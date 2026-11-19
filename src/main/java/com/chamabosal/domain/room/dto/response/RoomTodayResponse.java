package com.chamabosal.domain.room.dto.response;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationLock;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.reaction.entity.ReactionType;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.round.entity.RoundStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 오늘 탭 (ROOM-02).
 */
public record RoomTodayResponse(Room room, Pot pot, RoundSummary round, List<Member> members, Me me, Entry entry) {

    public record Room(
            Long roomId, String name, RoomGoal goal, int memberCount, int memberLimit, boolean isOwner,
            @Schema(description = "한 솥을 다 채운 횟수. 안 본 축하 판단에 씀") int potCompletedCount,
            @Schema(description = "이 방에 투표 중인 회차가 있음 (어제 회차 포함). 내보내기 버튼 상태") boolean hasVotingRound,
            @Schema(description = "현재 멤버가 1명에서 2명이 된 회차 날짜가 1일째. 혼자인 동안은 0이고, 다시 2명이 되면 새로 셈") int daysTogether
    ) {
    }

    public record Pot(
            @Schema(description = "지금 쌓고 있는 솥의 진행도. 첫 적립 전에는 0. 목표를 채우면 넘친 석으로 다음 솥이 바로 시작됨") int amount,
            @Schema(description = "목표 석") int goalAmount
    ) {
    }

    public record RoundSummary(
            @Schema(description = "회차 행이 아직 없으면 null") Long roundId,
            @Schema(description = "회차 행이 없으면 CERTIFYING") RoundStatus status,
            @Schema(description = "현재 멤버 중 이 회차에 인증한 사람 수") int certifiedCount,
            boolean isAllCertified,
            @Schema(description = "지금 인증할 수 있음. 이전 회차 투표가 끝나야 열림 (Q-17·Q-82)") boolean isCertificationOpen
    ) {
    }

    public record Member(
            Long memberId, String nickname, BosalColor color,
            @Schema(description = "착용 아이템 코드, 상점 노출 순서. 착용은 사용자 단위라 모든 방에 같음") List<String> equippedItemCodes,
            @Schema(description = "빠진 나발 번호(0~37). 보살은 사용자 단위라 모든 방에 같음") List<Integer> fallenNabalNumbers,
            boolean isMe,
            @Schema(description = "이 방의 이번 달 참았어요 수") int monthlyEnduredCount,
            @Schema(description = "이 방의 이번 달 지목 수. 방어 성공은 세지 않음") int monthlyPickedCount,
            @Schema(description = "인증 전이면 null") CertificationInfo certification
    ) {
    }

    public record CertificationInfo(
            Long certificationId, CertificationResult result, String memo,
            @Schema(description = "FAILED일 때만") Integer spentAmount,
            @Schema(description = "사진이 없거나 지웠으면 null") String imageUrl,
            LocalDateTime certifiedAt,
            @Schema(description = "받은 잘 참았어요 수. 누가 눌렀는지는 돌려주지 않음") int goodCount,
            @Schema(description = "받은 아쉬워요 수. 누가 눌렀는지는 돌려주지 않음") int badCount,
            @Schema(description = "내가 남긴 리액션. 없으면 null") ReactionType myReaction,
            @Schema(description = "수정·삭제 마감 시각 (인증 후 10분)") LocalDateTime editableUntil,
            @Schema(description = "수정·삭제할 수 있으면 null, 아니면 TIME_OVER 또는 VOTE_STARTED") String lockReason
    ) {

        /** 인증 전이면 null. 잠금 사유는 CERT-03 잠금을 화면 값으로 바꾼다 (6번). */
        public static CertificationInfo from(Certification certification, String imageUrl, ReactionCounts counts,
                                             ReactionType myReaction, LocalDateTime now) {
            if (certification == null) {
                return null;
            }
            CertificationLock lock = certification.lockAt(now);
            String lockReason = lock == null ? null : switch (lock) {
                case EDIT_TIME_OVER -> "TIME_OVER";
                case VOTE_STARTED -> "VOTE_STARTED";
            };
            return new CertificationInfo(certification.getId(), certification.getResult(), certification.getMemo(),
                    certification.getSpentAmount(), imageUrl, certification.getCertifiedAt(), counts.good(), counts.bad(), myReaction,
                    certification.getEditableUntil(), lockReason);
        }
    }

    /** 인증 하나가 받은 리액션 수 (ROOM-02 7번). */
    public record ReactionCounts(int good, int bad) {

        public static final ReactionCounts NONE = new ReactionCounts(0, 0);
    }

    public record Me(
            @Schema(description = "내가 등록하면 바로 투표가 시작됨. 등록 전 마지막 인증 확인 시트를 띄울 때 씀. "
                    + "인증이 열려 있고, 나만 인증 전이고, 현재 멤버 3명 이상이고, 회차가 인증 중일 때만 true. "
                    + "3명 미만 방은 전원 인증해도 투표가 열리지 않아 false") boolean isLastCertifier,
            @Schema(description = "이번 회차에 받은 콕 수. 누가 찔렀는지는 돌려주지 않음") int receivedPokeCount,
            @Schema(description = "솥을 채운 뒤 방장이 아직 [더 수행하기]·[방 닫기]를 고르지 않음. 방장에게만 true. "
                    + "true면 방에 들어올 때마다 선택 팝업 (ROOM-09)") boolean isPotDecisionPending
    ) {
    }

    public record Entry(
            @Schema(description = "안 본 최근 결과의 회차") Long unseenResultRoundId,
            @Schema(description = "안 본 한 솥 축하가 있음") boolean hasUnseenPotCelebration
    ) {
    }
}
