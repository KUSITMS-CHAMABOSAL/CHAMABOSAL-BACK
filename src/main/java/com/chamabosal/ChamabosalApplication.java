package com.chamabosal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// JWT로만 인증하므로 임시 비밀번호를 만드는 기본 사용자 구성을 뺀다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ChamabosalApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChamabosalApplication.class, args);
	}

}
