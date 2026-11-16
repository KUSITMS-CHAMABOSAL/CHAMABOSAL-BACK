package com.chamabosal.domain.store.repository;

import com.chamabosal.domain.store.entity.Item;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByCode(String code);

    /** 상점 노출 순서 (STORE-01 2번). */
    List<Item> findAllByOrderByDisplayOrderAscIdAsc();
}
