package com.chamabosal.domain.nabal.service;

import com.chamabosal.domain.nabal.dto.request.NabalReplantRequest;
import com.chamabosal.domain.nabal.dto.request.NabalSeenUpdateRequest;
import com.chamabosal.domain.nabal.dto.response.NabalReplantResponse;
import com.chamabosal.domain.nabal.dto.response.NabalStatusResponse;
import com.chamabosal.domain.nabal.entity.NabalLoss;
import com.chamabosal.domain.nabal.repository.NabalLossRepository;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.service.RiceService;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 내 보살의 나발 상태 조회, 빠지는 연출 확인, 나발 심기 (NABAL-02, NABAL-03). */
@Service
@RequiredArgsConstructor
public class NabalService {

    public static final int REPLANT_PRICE = 15;
    private static final String DUPLICATE_MESSAGE = "같은 나발을 두 번 고를 수 없습니다.";

    private final UserRepository userRepository;
    private final NabalLossRepository nabalLossRepository;
    private final RiceService riceService;
    private final Clock clock;

    /**
     * 내 정보(USER-01)에서 부른다. 본 기록도 심은 기록도 없으면 처음 빠진 것이다 (NABAL-02 3번).
     * 연출을 보기 전에 심은 기록도 이미 한 번 빠졌던 것이라 처음으로 보지 않는다 (코드리뷰 반영).
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public NabalStatusResponse getStatus(Long userId) {
        List<NabalLoss> fallen = nabalLossRepository.findByUser_IdAndReplantedAtIsNullOrderByNabalNumber(userId);
        Long lastId = fallen.stream().map(NabalLoss::getId).max(Long::compare).orElse(null);
        return new NabalStatusResponse(
                fallen.stream().map(NabalLoss::getNabalNumber).toList(),
                fallen.stream().filter(loss -> loss.getSeenAt() == null).map(NabalLoss::getNabalNumber).toList(),
                lastId,
                !fallen.isEmpty() && !nabalLossRepository.existsSeenOrReplanted(userId));
    }

    /** 보낸 기록 ID 이하의 내 기록만 본 것으로 처리해, 화면을 그린 뒤 빠진 나발은 다음에 보여준다 (NABAL-02 5·6번). */
    @Transactional
    public void markSeen(Long userId, NabalSeenUpdateRequest request) {
        nabalLossRepository.markSeenUpTo(userId, request.nabalLossId(), LocalDateTime.now(clock));
    }

    /**
     * 고른 빠진 나발을 개당 15석으로 심는다 (NABAL-03). 판정 배치와 같은 사용자 행 락 안에서 빠진 나발인지 확인하고 잔액을 쓴다.
     * 하나라도 빠지지 않은 나발이면 아무것도 심지 않는다 (3번). 장부 원인은 나발 기록 ID라 같은 기록은 두 번 쓰지 않는다.
     */
    @Transactional
    public NabalReplantResponse replant(Long userId, NabalReplantRequest request) {
        List<Integer> numbers = request.nabalNumbers();
        if (new HashSet<>(numbers).size() != numbers.size()) {
            throw BaseException.withField(BaseErrorCode.INVALID_REQUEST, DUPLICATE_MESSAGE, "nabalNumbers");
        }
        User user = userRepository.findWithLockById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.UNAUTHENTICATED));
        Map<Integer, NabalLoss> fallen = nabalLossRepository.findByUser_IdAndReplantedAtIsNullOrderByNabalNumber(userId)
                .stream().collect(Collectors.toMap(NabalLoss::getNabalNumber, Function.identity(), (first, second) -> first));
        if (!fallen.keySet().containsAll(numbers)) {
            throw new BaseException(BaseErrorCode.NABAL_NOT_FALLEN);
        }
        List<NabalLoss> targets = numbers.stream().map(fallen::get).toList();
        riceService.spendEach(user, REPLANT_PRICE, RiceRefType.NABAL, targets.stream().map(NabalLoss::getId).toList());
        LocalDateTime now = LocalDateTime.now(clock);
        targets.forEach(loss -> loss.replant(now));
        return new NabalReplantResponse(riceService.getDisplayBalance(userId),
                fallen.keySet().stream().filter(number -> !numbers.contains(number)).sorted().toList());
    }
}
