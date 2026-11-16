package com.chamabosal.domain.tap.dto.response;

import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.tap.entity.Tap;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 받은 톡톡 (RESULT-03 6번). 전달 전이거나 방어된 회차는 빈 목록이고 합계는 0이다. 건너뛴(0번) 톡톡은 뺀다.
 * totalCount는 목탁 수행에서 두드려야 하는 횟수다.
 */
public record ReceivedTapsResponse(
        boolean isDelivered,
        LocalDateTime deliveredAt,
        int totalCount,
        boolean isMoktakDone,
        List<ReceivedTap> taps
) {

    public static ReceivedTapsResponse of(boolean isDelivered, LocalDateTime deliveredAt, boolean isMoktakDone,
                                          List<Tap> taps) {
        return new ReceivedTapsResponse(isDelivered, deliveredAt, taps.stream().mapToInt(Tap::getCount).sum(),
                isMoktakDone, taps.stream().map(ReceivedTap::from).toList());
    }

    public record ReceivedTap(String nickname, BosalColor color, int count) {

        public static ReceivedTap from(Tap tap) {
            return new ReceivedTap(tap.getFromMember().getNickname(), tap.getFromMember().getColor(), tap.getCount());
        }
    }
}
