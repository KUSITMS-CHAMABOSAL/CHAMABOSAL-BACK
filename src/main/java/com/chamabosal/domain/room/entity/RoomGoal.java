package com.chamabosal.domain.room.entity;

public enum RoomGoal {
    DELIVERY("배달 끊기 방", "배달 대신 집밥 먹고\n공양미 한 솥까지 함께 가요"),
    SNACK("간식 참기 방", "달콤한 유혹은 잠시 내려놓고\n공양미 한 솥까지 함께 가요"),
    SHOPPING("쇼핑 참기 방", "오늘의 지름신을 물리치고\n공양미 한 솥까지 함께 가요"),
    ETC("자율 절약 방", "나만의 번뇌를 내려놓고\n공양미 한 솥까지 함께 가요");

    private final String title;
    private final String description;

    RoomGoal(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }
}
