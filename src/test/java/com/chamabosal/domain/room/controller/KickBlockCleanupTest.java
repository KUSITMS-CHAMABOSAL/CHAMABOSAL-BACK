package com.chamabosal.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.chamabosal.domain.room.entity.KickBlock;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.repository.KickBlockRepository;
import com.chamabosal.domain.room.service.KickBlockCleanupScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class KickBlockCleanupTest extends RoomTestSupport {

    @Autowired
    KickBlockRepository kickBlockRepository;

    @Autowired
    KickBlockCleanupScheduler kickBlockCleanupScheduler;

    @AfterEach
    void cleanUp() {
        kickBlockRepository.deleteAll();
    }

    @Test
    void CLEAN_02_1_만료_시각이_지난_기록은_지운다() {
        Room room = createRoom(4);
        kickBlockRepository.save(KickBlock.create("anon-key-a", room, NOW.minusMonths(6).minusMinutes(1)));

        kickBlockCleanupScheduler.deleteExpired();

        assertThat(kickBlockRepository.count()).isZero();
    }

    @Test
    void CLEAN_02_2_만료_전_기록은_그대로다() {
        Room room = createRoom(4);
        kickBlockRepository.save(KickBlock.create("anon-key-a", room, NOW.minusMonths(5)));

        kickBlockCleanupScheduler.deleteExpired();

        assertThat(kickBlockRepository.count()).isEqualTo(1);
    }
}
