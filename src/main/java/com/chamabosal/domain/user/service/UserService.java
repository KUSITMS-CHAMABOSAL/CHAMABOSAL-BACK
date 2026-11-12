package com.chamabosal.domain.user.service;

import com.chamabosal.domain.user.dto.request.PushAgreementCreateRequest;
import com.chamabosal.domain.user.dto.request.UserSettingsUpdateRequest;
import com.chamabosal.domain.user.entity.PushAgreement;
import com.chamabosal.domain.user.entity.PushAgreementType;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.PushAgreementRepository;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String UNSUPPORTED_TYPE_MESSAGE = "동의를 받지 않는 알림 종류입니다.";

    private final UserRepository userRepository;
    private final PushAgreementRepository pushAgreementRepository;
    private final Clock clock;

    @Transactional
    public void updateSettings(Long userId, UserSettingsUpdateRequest request) {
        User user = userRepository.findById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.UNAUTHENTICATED));
        user.updatePushSettings(request.isPokePushEnabled(), request.isActivityPushEnabled());
    }

    /** 같은 요청이 동시에 와도 1건만 저장되도록 사용자 행 락 안에서 확인하고 저장한다. */
    @Transactional
    public void agreePush(Long userId, PushAgreementCreateRequest request) {
        PushAgreementType type = PushAgreementType.find(request.type())
                .orElseThrow(() -> BaseException.withField(BaseErrorCode.INVALID_REQUEST, UNSUPPORTED_TYPE_MESSAGE, "type"));
        User user = userRepository.findWithLockById(userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.UNAUTHENTICATED));
        if (request.isRejected() || pushAgreementRepository.existsByUser_IdAndType(userId, type)) {
            return;
        }
        pushAgreementRepository.save(PushAgreement.create(user, type, LocalDateTime.now(clock)));
    }
}
