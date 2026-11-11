package com.chamabosal.domain.room.service;

import com.chamabosal.domain.room.entity.BosalColor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 랜덤 선택을 테스트에서 바꿔 끼울 수 있게 서비스와 분리했다. */
@Component
public class BosalColorPicker {

    public BosalColor pick(Collection<BosalColor> usedColors) {
        List<BosalColor> candidates = Arrays.stream(BosalColor.values())
                .filter(color -> !usedColors.contains(color))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("남은 보살 색이 없습니다. 방 인원 확인이 먼저 필요합니다.");
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
