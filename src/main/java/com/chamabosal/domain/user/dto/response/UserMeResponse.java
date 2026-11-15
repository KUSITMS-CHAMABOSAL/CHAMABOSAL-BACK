package com.chamabosal.domain.user.dto.response;

import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.user.entity.PushAgreementType;
import com.chamabosal.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;

/** 내 정보 (USER-01). 사용자 ID는 내보내지 않는다. 꾸미기 아이템은 곳간(STORE-03) 전이라 항상 빈 배열이다. */
public record UserMeResponse(
        @Schema(description = "설정 화면 콕 알림 토글 (POKE 알림). 토스 동의와 별개") boolean isPokePushEnabled,
        @Schema(description = "설정 화면 인증·투표 알림 토글 (RESULT·VOTE_OPENED·CERT_REMINDER 알림). 토스 동의와 별개") boolean isActivityPushEnabled,
        @Schema(description = "콕 찌르기 알림 토스 동의 (POKE). 동의 카드를 띄울지 판단") boolean isPokeAgreed,
        @Schema(description = "투표 결과 알림 토스 동의 (RESULT)") boolean isResultAgreed,
        @Schema(description = "새 투표 알림 토스 동의 (VOTE_OPENED)") boolean isVoteOpenedAgreed,
        @Schema(description = "인증 마감 알림 토스 동의 (CERT_REMINDER)") boolean isCertReminderAgreed,
        @Schema(description = "착용 아이템 코드. 착용은 사용자 단위라 모든 방에 같음") List<String> equippedItemCodes,
        @Schema(description = "현재 멤버인 방, 들어온 순서. 비어 있으면 첫 화면") List<MyRoom> rooms,
        @Schema(description = "참여 중인 방이 2개면 false") boolean canCreateRoom
) {

    public record MyRoom(
            Long roomId,
            String name,
            @Schema(description = "그 방의 내 닉네임") String nickname,
            @Schema(description = "그 방의 내 보살 색") BosalColor color,
            @Schema(description = "안 본 결과나 한 솥 축하가 있음") boolean hasUnseen
    ) {

        public static MyRoom of(RoomMember member, boolean hasUnseen) {
            return new MyRoom(member.getRoom().getId(), member.getRoom().getName(), member.getNickname(),
                    member.getColor(), hasUnseen);
        }
    }

    public static UserMeResponse of(User user, Set<PushAgreementType> agreed, List<MyRoom> rooms, boolean canCreateRoom) {
        return new UserMeResponse(user.isPokePushEnabled(), user.isActivityPushEnabled(),
                agreed.contains(PushAgreementType.POKE), agreed.contains(PushAgreementType.RESULT),
                agreed.contains(PushAgreementType.VOTE_OPENED), agreed.contains(PushAgreementType.CERT_REMINDER),
                List.of(), rooms, canCreateRoom);
    }
}
