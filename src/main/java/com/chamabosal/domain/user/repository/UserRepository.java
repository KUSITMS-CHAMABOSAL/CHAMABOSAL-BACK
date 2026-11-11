package com.chamabosal.domain.user.repository;

import com.chamabosal.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTossAnonKey(String tossAnonKey);

    boolean existsByIdAndDeletedAtIsNull(Long id);

    /** 참여 방 개수 확인을 같은 사용자의 동시 요청에서 하나씩 처리하려고 사용자 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithLockById(Long id);
}
