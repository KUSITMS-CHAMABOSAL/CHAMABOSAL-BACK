package com.chamabosal.domain.auth.service;

import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthUserService {

    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public AuthUser findOrCreate(String tossAnonKey) {
        return userRepository.findByTossAnonKey(tossAnonKey)
                .map(user -> resolve(user, tossAnonKey))
                .orElseGet(() -> create(tossAnonKey));
    }

    private AuthUser resolve(User user, String tossAnonKey) {
        if (!user.isDeleted()) {
            return new AuthUser(user.getId(), false);
        }
        if (user.isRestorable(LocalDateTime.now(clock))) {
            user.restore();
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
