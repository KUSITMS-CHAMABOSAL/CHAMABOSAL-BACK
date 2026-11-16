package com.chamabosal.domain.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RoomNicknameUpdateRequest(
        @Schema(description = "새 닉네임. 앞뒤 띄어쓰기를 지운 뒤 한글·영문·숫자 1~8자", example = "지수2")
        @NotNull(message = "닉네임을 입력해 주세요.")
        @Pattern(regexp = NICKNAME_PATTERN, message = NICKNAME_MESSAGE)
        String nickname
) {

    public RoomNicknameUpdateRequest {
        nickname = normalizeNickname(nickname);
    }

    /** 한글은 완성형만 허용한다. 자모 단독, 띄어쓰기, 특수문자, 이모지는 쓸 수 없다. */
    public static final String NICKNAME_PATTERN = "[가-힣a-zA-Z0-9]{1,8}";
    /** 방 만들기·입장·닉네임 수정이 같이 쓴다. 앞뒤 띄어쓰기는 지우고 형식을 검사한다 (정책서). 가운데 띄어쓰기는 형식 오류다. */
    public static String normalizeNickname(String nickname) {
        return nickname == null ? null : nickname.strip();
    }

    public static final String NICKNAME_MESSAGE = "닉네임은 한글, 영문, 숫자 1~8자예요.";
}
