package com.chamabosal.domain.auth.dto.response;

public record AuthTestTokenResponse(String accessToken, long expiresIn) {
}
