package com.chamabosal.domain.store.service;

import com.chamabosal.domain.defense.repository.DefenseTicketRepository;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.service.RiceService;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.store.dto.request.EquippedItemsUpdateRequest;
import com.chamabosal.domain.store.dto.response.ItemPurchaseResponse;
import com.chamabosal.domain.store.dto.response.StoreResponse;
import com.chamabosal.domain.store.dto.response.StoreResponse.DefenseTicketStatus;
import com.chamabosal.domain.store.dto.response.StoreResponse.StoreItem;
import com.chamabosal.domain.store.entity.Item;
import com.chamabosal.domain.store.entity.UserItem;
import com.chamabosal.domain.store.repository.ItemRepository;
import com.chamabosal.domain.store.repository.UserItemRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 곳간 조회·아이템 구매·착용 (STORE-01~03). 구매와 착용은 사용자 전체 잔액·보유 아이템을 다뤄 방어권 구매·나발 심기와 같은
 * 사용자 행 락을 먼저 잡는다. 락을 잡은 뒤의 조회가 커밋된 값을 보도록 READ COMMITTED로 읽는다.
 */
@Service
@RequiredArgsConstructor
public class StoreService {

    private static final String SAME_ITEM_MESSAGE = "같은 아이템은 한 번만 보내 주세요.";
    private static final String SAME_SLOT_MESSAGE = "슬롯마다 하나만 착용할 수 있어요.";

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final UserItemRepository userItemRepository;
    private final DefenseTicketRepository defenseTicketRepository;
    private final RiceService riceService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public StoreResponse getStore(Long userId) {
        Map<Long, UserItem> owned = ownedByItemId(userId);
        List<StoreItem> items = itemRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(item -> StoreItem.of(item, owned.get(item.getId())))
                .toList();
        // 이번 주는 지금 회차 날짜가 속한 월요일~일요일이다 (api.md isUsedThisWeek).
        LocalDate monday = Round.dateOf(LocalDateTime.now(clock)).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        DefenseTicketStatus defenseTicket = new DefenseTicketStatus(
                defenseTicketRepository.existsByUser_IdAndUsedAtIsNull(userId)
                        ? DefenseTicketStatus.Status.OWNED : DefenseTicketStatus.Status.NONE,
                defenseTicketRepository.existsByUser_IdAndUsedRound_RoundDateBetween(userId, monday, monday.plusDays(6)));
        return new StoreResponse(riceService.getDisplayBalance(userId), defenseTicket, items);
    }

    /**
     * 확인 순서: 없는 아이템 → 이미 보유 → 잔액 부족 (STORE-02 2번). 사면 바로 착용하고 같은 슬롯의 착용은 벗긴다 (6번).
     * 잔액 부족이면 롤백되어 보유도 남지 않는다. 같은 아이템 동시 구매는 사용자 행 락과 UNIQUE가 막는다 (7번).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ItemPurchaseResponse purchase(Long userId, Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.ITEM_NOT_FOUND));
        User user = lockUser(userId);
        if (userItemRepository.existsByUser_IdAndItem_Id(userId, itemId)) {
            throw new BaseException(BaseErrorCode.ITEM_ALREADY_OWNED);
        }
        riceService.spend(user, item.getPrice(), RiceRefType.ITEM, itemId);
        List<UserItem> owned = userItemRepository.findWithItemByUserId(userId);
        owned.stream().filter(userItem -> userItem.getItem().getSlot() == item.getSlot()).forEach(UserItem::unequip);
        userItemRepository.save(UserItem.acquire(user, item));
        List<Long> equippedItemIds = Stream.concat(
                        owned.stream().filter(UserItem::isEquipped).map(UserItem::getItem), Stream.of(item))
                .sorted(Comparator.comparingInt(Item::getDisplayOrder).thenComparing(Item::getId))
                .map(Item::getId)
                .toList();
        return new ItemPurchaseResponse(riceService.getDisplayBalance(userId), equippedItemIds);
    }

    /**
     * 보낸 목록으로 착용 상태를 통째로 바꾼다 (STORE-03 1번). 확인 순서: 같은 ID 중복 → 보유하지 않음(없는 아이템 포함, 2번) →
     * 같은 슬롯 둘 이상(3번) [구현 결정]. 동시 요청은 사용자 행 락으로 한 요청의 목록만 남는다 (7번).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void equip(Long userId, EquippedItemsUpdateRequest request) {
        List<Long> itemIds = request.itemIds();
        if (new HashSet<>(itemIds).size() != itemIds.size()) {
            throw BaseException.withField(BaseErrorCode.INVALID_REQUEST, SAME_ITEM_MESSAGE, "itemIds");
        }
        lockUser(userId);
        Map<Long, UserItem> owned = ownedByItemId(userId);
        if (!owned.keySet().containsAll(itemIds)) {
            throw new BaseException(BaseErrorCode.ITEM_NOT_OWNED);
        }
        long slots = itemIds.stream().map(itemId -> owned.get(itemId).getItem().getSlot()).distinct().count();
        if (slots != itemIds.size()) {
            throw BaseException.withField(BaseErrorCode.INVALID_REQUEST, SAME_SLOT_MESSAGE, "itemIds");
        }
        owned.forEach((itemId, userItem) -> {
            if (itemIds.contains(itemId)) {
                userItem.equip();
            } else {
                userItem.unequip();
            }
        });
    }

    /** 보살 캐릭터를 그리는 응답에 쓰는 착용 아이템 코드, 노출 순서. 착용이 없는 사용자는 빈 목록이다. */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> getEquippedItemCodes(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userItemRepository.findEquippedCodes(userIds).stream()
                .collect(Collectors.groupingBy(row -> (Long) row[0],
                        Collectors.mapping(row -> (String) row[1], Collectors.toList())));
    }

    private Map<Long, UserItem> ownedByItemId(Long userId) {
        return userItemRepository.findWithItemByUserId(userId).stream()
                .collect(Collectors.toMap(userItem -> userItem.getItem().getId(), Function.identity()));
    }

    private User lockUser(Long userId) {
        return userRepository.findWithLockById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.UNAUTHENTICATED));
    }
}
