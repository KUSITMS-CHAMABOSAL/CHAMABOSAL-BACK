package com.chamabosal.domain.store.service;

import com.chamabosal.domain.store.entity.Item;
import com.chamabosal.domain.store.repository.ItemRepository;
import com.chamabosal.domain.store.repository.UserItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이그레이션 도구가 없어서 앱이 시작할 때 카탈로그를 DB에 맞춘다. 없으면 넣고 있으면 고친다. 서버가 1대라 겹쳐 돌지 않는다.
 * 슬롯이 바뀐 아이템은 착용을 모두 벗겨, 바뀐 슬롯에 둘이 착용된 채로 남지 않게 한다 (STORE-03 3번, 코드리뷰 반영).
 */
@Component
@RequiredArgsConstructor
public class ItemCatalogInitializer implements ApplicationRunner {

    private final ItemRepository itemRepository;
    private final UserItemRepository userItemRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        sync();
    }

    @Transactional
    public void sync() {
        for (ItemCatalog.Entry entry : ItemCatalog.ENTRIES) {
            itemRepository.findByCode(entry.code()).ifPresentOrElse(
                    item -> {
                        boolean slotChanged = item.getSlot() != entry.slot();
                        item.update(entry.name(), entry.price(), entry.slot(), entry.displayOrder());
                        // 일괄 update가 바뀐 아이템을 먼저 반영하고 영속성 컨텍스트를 비우므로 고친 뒤에 부른다.
                        if (slotChanged) {
                            userItemRepository.unequipItem(item.getId());
                        }
                    },
                    () -> itemRepository.save(
                            Item.create(entry.code(), entry.name(), entry.price(), entry.slot(), entry.displayOrder())));
        }
    }
}
