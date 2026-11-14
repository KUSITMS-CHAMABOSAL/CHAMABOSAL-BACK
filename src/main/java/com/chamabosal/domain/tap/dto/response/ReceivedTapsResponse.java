package com.chamabosal.domain.tap.dto.response;

import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.tap.entity.Tap;
import java.util.List;

/** 아직 재생하지 않은 받은 톡톡. 건너뛴(0번) 톡톡은 빼고, 방어된 회차는 빈 목록이다. */
public record ReceivedTapsResponse(List<ReceivedTap> taps) {

    public static ReceivedTapsResponse from(List<Tap> taps) {
        return new ReceivedTapsResponse(taps.stream().map(ReceivedTap::from).toList());
    }

    public record ReceivedTap(String nickname, BosalColor color, int count) {

        public static ReceivedTap from(Tap tap) {
            return new ReceivedTap(tap.getFromMember().getNickname(), tap.getFromMember().getColor(), tap.getCount());
        }
    }
}
