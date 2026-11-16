package com.chamabosal.domain.store.repository;

import com.chamabosal.domain.store.entity.UserItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserItemRepository extends JpaRepository<UserItem, Long> {

    boolean existsByUser_IdAndItem_Id(Long userId, Long itemId);

    /** 보유 아이템을 아이템과 함께 읽는다. 슬롯 비교와 노출 순서에 아이템이 필요하다. */
    @Query("select ui from UserItem ui join fetch ui.item i where ui.user.id = :userId order by i.displayOrder, i.id")
    List<UserItem> findWithItemByUserId(Long userId);

    /** 착용 아이템 코드를 여러 사용자에 대해 한 번에 읽는다. 결과는 [사용자 ID, 코드], 노출 순서. */
    @Query("""
            select ui.user.id, i.code from UserItem ui join ui.item i
            where ui.user.id in :userIds and ui.equipped = true
            order by i.displayOrder, i.id
            """)
    List<Object[]> findEquippedCodes(Collection<Long> userIds);

    /** 내 데이터 삭제 (USER-05 3번). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserItem ui where ui.user.id = :userId")
    int deleteByUserId(Long userId);
}
