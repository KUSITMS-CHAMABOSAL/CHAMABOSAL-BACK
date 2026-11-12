package com.chamabosal.domain.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record InviteJoinRequest(
        @Schema(description = "이 방에서 쓸 닉네임. 한글·영문·숫자 1~8자", example = "지수")
        @NotNull(message = "닉네임을 입력해 주세요.")
        @Pattern(regexp = RoomNicknameUpdateRequest.NICKNAME_PATTERN, message = RoomNicknameUpdateRequest.NICKNAME_MESSAGE)
        String nickname
) {
}
