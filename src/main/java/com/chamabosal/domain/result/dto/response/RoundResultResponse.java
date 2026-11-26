package com.chamabosal.domain.result.dto.response;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.round.entity.RoundResultType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 결과 화면 (RESULT-02). 누가 누구에게 투표했는지는 담지 않는다. */
public record RoundResultResponse(
        Long roundId,
        LocalDate roundDate,
        RoundResultType resultType,
        @Schema(description = "PICKED·DEFENDED일 때만. 나머지는 null") Picked picked,
        @Schema(description = "지목이 없을 때(ALL_BOSAL·ALL_SAFE)만. 표를 받은 멤버만, 득표 수가 많은 순. 지목이 있으면 빈 배열")
        List<VoteCount> voteCounts,
        @Schema(description = "지목이 없을 때만 \"오늘은 모두 보살\" 득표 수. 지목이 있으면 null") Integer allBosalCount,
        boolean isMePicked,
        @Schema(description = "내가 지목된 PICKED 결과이고 처음 연 시각이 톡톡 마감 전일 때만, 처음 연 시각 + 2분") LocalDateTime defenseUsableUntil,
        @Schema(description = "내가 지목된 PICKED·DEFENDED 결과일 때만, 보유한 방어권 개수") Integer defenseTicketCount,
        boolean canUseDefense,
        boolean canTap,
        @Schema(description = "PICKED인데 그 뒤 다음 투표가 열려 톡톡 시간이 지남") boolean isTapExpired,
        @Schema(description = "나를 뺀, 그 회차에 1번 이상 톡톡한 멤버 수") int tappedMemberCount,
        @Schema(description = "지목된 사람이 아직 방어권을 쓸 수 있는지 (방어권 고민 중 표시). 표시용이라 canTap과 상관없음") boolean isDefensePending
) {

    public record Picked(
            Long memberId, String nickname, BosalColor color,
            @Schema(description = "착용 아이템 코드, 상점 노출 순서. 떠난 보살도 그 사용자의 착용, 내 데이터를 삭제했으면 빈 배열") List<String> equippedItemCodes,
            boolean isLeft,
            @Schema(description = "인증하지 않았으면 null") CertificationResult certificationResult,
            @Schema(description = "인증하지 않았거나 ENDURED면 null") Integer spentAmount
    ) {

        public static Picked from(RoomMember member, Certification certification, List<String> equippedItemCodes) {
            return new Picked(member.getId(), member.getNickname(), member.getColor(), equippedItemCodes, !member.isCurrent(),
                    certification == null ? null : certification.getResult(),
                    certification == null ? null : certification.getSpentAmount());
        }
    }

    public record VoteCount(Long memberId, String nickname, int count, boolean isLeft) {

        public static VoteCount from(RoomMember member, long count) {
            return new VoteCount(member.getId(), member.getNickname(), (int) count, !member.isCurrent());
        }
    }
}
