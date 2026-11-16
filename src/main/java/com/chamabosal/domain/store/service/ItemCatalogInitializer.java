package com.chamabosal.domain.store.service;

import com.chamabosal.domain.store.entity.Item;
import com.chamabosal.domain.store.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 마이그레이션 도구가 없어서 앱이 시작할 때 카탈로그를 DB에 맞춘다. 없으면 넣고 있으면 고친다. 서버가 1대라 겹쳐 돌지 않는다. */
@Component
@RequiredArgsConstructor
public class ItemCatalogInitializer implements ApplicationRunner {

    private final ItemRepository itemRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        sync();
    }

    @Transactional
    public void sync() {
        for (ItemCatalog.Entry entry : ItemCatalog.ENTRIES) {
            itemRepository.findByCode(entry.code()).ifPresentOrElse(
                    item -> item.update(entry.name(), entry.price(), entry.slot(), entry.displayOrder()),
                    () -> itemRepository.save(
                            Item.create(entry.code(), entry.name(), entry.price(), entry.slot(), entry.displayOrder())));
        }
    }
}
