package com.chamabosal.domain.room.dto.request;

import com.chamabosal.domain.room.entity.RoomGoal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RoomCreateRequest(
        @Schema(description = "방 이름. 앞뒤 띄어쓰기를 지운 뒤 1~8자, 한글·영문·숫자·띄어쓰기", example = "배달 끊기 방")
        @NotNull(message = "방 이름을 입력해 주세요.")
        String name,

        @Schema(description = "절약 목표")
        @NotNull(message = "절약 목표를 골라 주세요.")
        RoomGoal goal,

        @Schema(description = "함께할 인원. 만든 뒤 바꿀 수 없음", example = "4")
        @NotNull(message = "함께할 인원을 입력해 주세요.")
        @Min(value = 2, message = "함께할 인원은 2~4명이에요.")
        @Max(value = 4, message = "함께할 인원은 2~4명이에요.")
        Integer memberLimit,

        @Schema(description = "공양미 한 솥 목표 석. 300~700 중 50 단위", example = "350", multipleOf = POT_GOAL_UNIT)
        @NotNull(message = "공양미 목표를 정해 주세요.")
        @Min(value = POT_GOAL_MIN, message = POT_GOAL_MESSAGE)
        @Max(value = POT_GOAL_MAX, message = POT_GOAL_MESSAGE)
        Integer potGoalAmount,

        @Schema(description = "이 방에서 쓸 닉네임. 한글·영문·숫자 1~8자", example = "민지")
        @NotNull(message = "닉네임을 입력해 주세요.")
        @Pattern(regexp = RoomNicknameUpdateRequest.NICKNAME_PATTERN, message = RoomNicknameUpdateRequest.NICKNAME_MESSAGE)
        String nickname
) {

    public static final int POT_GOAL_MIN = 300;
    public static final int POT_GOAL_MAX = 700;
    /** 50석 단위는 어노테이션이 없어 서비스에서 확인한다. */
    public static final int POT_GOAL_UNIT = 50;
    public static final String POT_GOAL_MESSAGE =
            "공양미 목표는 " + POT_GOAL_MIN + "~" + POT_GOAL_MAX + "석 중 " + POT_GOAL_UNIT + "석 단위예요.";
}
