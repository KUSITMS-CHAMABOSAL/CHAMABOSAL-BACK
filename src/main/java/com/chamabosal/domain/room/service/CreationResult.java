package com.chamabosal.domain.room.service;

/** 새로 만들었으면 201, 이미 있던 것을 돌려주면 200으로 응답하려고 생성 여부를 함께 넘긴다. */
public record CreationResult<T>(T response, boolean isCreated) {
}
