package com.chamabosal.domain.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RoomNicknameUpdateRequest(
        @Schema(description = "새 닉네임. 한글·영문·숫자 1~8자", example = "지수2")
        @NotNull(message = "닉네임을 입력해 주세요.")
        @Pattern(regexp = NICKNAME_PATTERN, message = NICKNAME_MESSAGE)
        String nickname
) {

    /** 한글은 완성형만 허용한다. 자모 단독, 띄어쓰기, 특수문자, 이모지는 쓸 수 없다. */
    public static final String NICKNAME_PATTERN = "[가-힣a-zA-Z0-9]{1,8}";
    public static final String NICKNAME_MESSAGE = "닉네임은 한글, 영문, 숫자 1~8자입니다.";
}
