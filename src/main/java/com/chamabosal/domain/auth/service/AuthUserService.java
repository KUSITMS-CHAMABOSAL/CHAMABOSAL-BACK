package com.chamabosal.domain.auth.service;

import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class AuthUserService {

    private final UserRepository userRepository;

    @Transactional
    public AuthUser findOrCreate(String tossAnonKey) {
        return userRepository.findByTossAnonKey(tossAnonKey)
                .map(user -> resolve(user, tossAnonKey))
                .orElseGet(() -> create(tossAnonKey));
    }

    /** 내 데이터를 삭제하면 키가 비워져 찾히지 않는다. 키가 남은 삭제 사용자(예전 데이터)도 복구 없이 새 사용자로 만든다 (AUTH-01 5·6번). */
    private AuthUser resolve(User user, String tossAnonKey) {
        if (!user.isDeleted()) {
            return new AuthUser(user.getId(), false);
        }
        // UNIQUE 제약에 걸리지 않도록 기존 행의 키를 먼저 비운다.
        user.releaseTossAnonKey();
        userRepository.flush();
        return create(tossAnonKey);
    }

    private AuthUser create(String tossAnonKey) {
        User user = userRepository.saveAndFlush(User.create(tossAnonKey));
        return new AuthUser(user.getId(), true);
    }

    public record AuthUser(Long id, boolean isNew) {
    }
}
