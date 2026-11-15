package com.chamabosal.domain.nabal.service;

import com.chamabosal.domain.nabal.entity.NabalLoss;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/** 빠질 나발을 남은 번호 중 랜덤으로 고른다 (NABAL-01 8번, Q-103). 테스트에서 바꿔 끼울 수 있게 서비스와 분리했다. */
@Component
public class NabalPicker {

    public OptionalInt pick(Collection<Integer> fallenNumbers) {
        List<Integer> remaining = IntStream.range(0, NabalLoss.NABAL_COUNT)
                .filter(number -> !fallenNumbers.contains(number))
                .boxed()
                .toList();
        if (remaining.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(remaining.get(ThreadLocalRandom.current().nextInt(remaining.size())));
    }
}
