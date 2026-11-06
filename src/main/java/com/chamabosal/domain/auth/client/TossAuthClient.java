package com.chamabosal.domain.auth.client;

public interface TossAuthClient {

    String exchangeAnonKey(String authorizationCode);
}
