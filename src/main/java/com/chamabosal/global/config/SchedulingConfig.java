package com.chamabosal.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 서버가 1대라 배치가 여러 서버에서 겹쳐 도는 것은 막지 않는다. 같은 회차를 이벤트와 동시에 처리하는 것은 방 행 락으로 막는다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
