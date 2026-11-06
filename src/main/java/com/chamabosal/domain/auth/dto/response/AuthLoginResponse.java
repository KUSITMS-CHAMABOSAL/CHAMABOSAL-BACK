package com.chamabosal.domain.auth.dto.response;

public record AuthLoginResponse(String accessToken, long expiresIn, boolean isNewUser) {
}
