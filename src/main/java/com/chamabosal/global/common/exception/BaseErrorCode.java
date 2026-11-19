package com.chamabosal.global.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BaseErrorCode implements ErrorCode {

    // Common
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_001", "요청하신 리소스를 찾을 수 없어요."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_002", "요청 값이 올바르지 않아요."),
    INVALID_JSON_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_003", "요청 본문을 읽을 수 없어요."),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON_004", "필수 요청 파라미터가 빠졌어요."),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "COMMON_005", "요청 값의 타입이 올바르지 않아요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_006", "지원하지 않는 HTTP 메서드예요."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_007", "지원하지 않는 Content-Type이에요."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "COMMON_008", "인증이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_009", "접근 권한이 없어요."),
    UNPROCESSABLE_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_010", "처리할 수 없는 요청이에요."),

    UNHANDLED_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 내부 오류가 발생했어요."),

    // Auth
    INVALID_AUTH_CODE(HttpStatus.BAD_REQUEST, "AUTH_001", "인증 코드가 틀렸거나 만료됐어요."),
    TOSS_API_FAILED(HttpStatus.BAD_GATEWAY, "AUTH_002", "앱인토스 호출에 실패했어요."),

    // Room
    ROOM_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "ROOM_001", "참여 중인 방이 2개예요."),
    NOT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "ROOM_002", "이 방의 멤버가 아니에요."),
    ROOM_FULL(HttpStatus.CONFLICT, "ROOM_003", "방이 가득 찼어요."),
    // 닉네임 중복 허용으로 쓰지 않음 (#161). 정해진 code라 남겨 둠
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "ROOM_004", "이미 쓰는 닉네임이에요."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "ROOM_005", "없거나 삭제된 방이에요."),
    ROOM_UNAVAILABLE(HttpStatus.FORBIDDEN, "ROOM_006", "들어갈 수 없는 방이에요."),
    NOT_ROOM_OWNER(HttpStatus.FORBIDDEN, "ROOM_007", "방장만 할 수 있어요."),
    KICK_DURING_VOTE(HttpStatus.CONFLICT, "ROOM_008", "투표 중에는 내보낼 수 없어요."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "ROOM_009", "이 방에 없는 멤버예요."),
    CANNOT_KICK_OWNER(HttpStatus.BAD_REQUEST, "ROOM_010", "방장 본인은 내보낼 수 없어요."),
    POT_DECISION_NOT_PENDING(HttpStatus.CONFLICT, "ROOM_011", "목표 달성 뒤 선택할 차례가 아니에요."),

    // Certification
    ALREADY_CERTIFIED(HttpStatus.CONFLICT, "CERT_001", "이미 인증했어요."),
    CERTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CERT_002", "없거나 삭제된 인증이에요."),
    NOT_MY_CERTIFICATION(HttpStatus.FORBIDDEN, "CERT_003", "본인 인증이 아니에요."),
    CERTIFICATION_EDIT_TIME_OVER(HttpStatus.CONFLICT, "CERT_004", "수정 시간이 지나서 인증을 바꿀 수 없어요."),
    CERTIFICATION_VOTE_STARTED(HttpStatus.CONFLICT, "CERT_005", "투표가 시작돼서 인증을 바꿀 수 없어요."),
    CERTIFICATION_NOT_OPEN(HttpStatus.CONFLICT, "CERT_006", "아직 인증 시간이 아니에요."),

    // Round
    ROUND_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUND_001", "없는 회차예요."),

    // Vote
    VOTE_NOT_OPEN(HttpStatus.CONFLICT, "VOTE_001", "투표 중이 아니에요."),
    NOT_IN_VOTE_LIST(HttpStatus.FORBIDDEN, "VOTE_002", "투표 명단에 없어요."),
    NO_VOTING_RIGHT(HttpStatus.FORBIDDEN, "VOTE_003", "투표권이 없어요."),
    ALREADY_VOTED(HttpStatus.CONFLICT, "VOTE_004", "이미 투표했어요."),
    SELF_VOTE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "VOTE_005", "본인에게 투표할 수 없어요."),
    INVALID_VOTE_TARGET(HttpStatus.BAD_REQUEST, "VOTE_006", "선택할 수 없는 후보예요."),

    // Result
    RESULT_NOT_ANNOUNCED(HttpStatus.CONFLICT, "RESULT_001", "아직 발표 전인 회차예요."),

    // Tap
    TAP_NOT_AVAILABLE(HttpStatus.CONFLICT, "TAP_001", "이마 톡톡이 없는 결과예요."),
    TAP_SELF(HttpStatus.FORBIDDEN, "TAP_002", "지목된 본인은 톡톡할 수 없어요."),
    ALREADY_TAPPED(HttpStatus.CONFLICT, "TAP_003", "이미 톡톡했어요."),
    TAP_EXPIRED(HttpStatus.CONFLICT, "TAP_004", "톡톡 시간이 지났어요."),
    NOT_PICKED_MEMBER(HttpStatus.FORBIDDEN, "TAP_005", "지목된 사람만 받은 톡톡을 볼 수 있어요."),
    TAP_NOT_VOTED(HttpStatus.FORBIDDEN, "TAP_006", "투표한 보살만 톡톡할 수 있어요."),

    // Moktak
    MOKTAK_NOT_AVAILABLE(HttpStatus.CONFLICT, "MOKTAK_001", "번뇌 완료 보상이 없는 결과예요."),
    MOKTAK_NO_TAPS(HttpStatus.CONFLICT, "MOKTAK_002", "받은 톡톡이 없어요."),
    MOKTAK_EXPIRED(HttpStatus.CONFLICT, "MOKTAK_003", "수행 시간이 지났어요."),
    /** 더 이상 쓰지 않는다. 덜 두드리면 부분 보상 없이 끝난다 (Q-108). 정해진 code는 바꾸지 않아 남겨 둔다. */
    MOKTAK_NOT_FINISHED(HttpStatus.CONFLICT, "MOKTAK_004", "받은 톡톡만큼 두드려야 해요."),
    MOKTAK_NOT_DELIVERED(HttpStatus.CONFLICT, "MOKTAK_005", "아직 톡톡이 전달되지 않았어요."),

    // Store
    RICE_NOT_ENOUGH(HttpStatus.CONFLICT, "STORE_001", "공양미가 부족해요."),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE_002", "없는 아이템이에요."),
    ITEM_ALREADY_OWNED(HttpStatus.CONFLICT, "STORE_003", "이미 가진 아이템이에요."),
    ITEM_NOT_OWNED(HttpStatus.CONFLICT, "STORE_004", "가지고 있지 않은 아이템이에요."),

    // Reaction
    REACTION_ON_MINE(HttpStatus.FORBIDDEN, "REACT_001", "내 인증에는 리액션을 남길 수 없어요."),
    REACTION_CLOSED(HttpStatus.CONFLICT, "REACT_002", "투표가 시작되어 리액션을 바꿀 수 없어요."),

    // Poke
    POKE_SELF(HttpStatus.BAD_REQUEST, "POKE_001", "나를 찌를 수 없어요."),

    // Pot
    POT_NOT_FOUND(HttpStatus.NOT_FOUND, "POT_001", "다 채운 솥이 없어요."),

    // Nabal
    NABAL_NOT_FALLEN(HttpStatus.CONFLICT, "NABAL_001", "빠지지 않은 나발은 심을 수 없어요."),

    // Defense
    NO_DEFENSE_TICKET(HttpStatus.CONFLICT, "DEFENSE_002", "보유한 방어권이 없어요."),
    NOT_PICKED_FOR_DEFENSE(HttpStatus.FORBIDDEN, "DEFENSE_008", "지목된 사람만 방어권을 쓸 수 있어요."),
    DEFENSE_EXPIRED(HttpStatus.CONFLICT, "DEFENSE_009", "방어 시간이 지났어요."),
    DEFENSE_AFTER_MOKTAK(HttpStatus.CONFLICT, "DEFENSE_010", "목탁 수행을 마친 결과는 방어할 수 없어요."),
    DEFENSE_NOT_CERTIFIED(HttpStatus.FORBIDDEN, "DEFENSE_011", "인증하지 않은 방에서는 방어권을 쓸 수 없어요."),

    // 새 도메인 구역은 이 줄 위에 추가한다. 마지막 상수도 쉼표로 끝내 추가할 때 다른 줄을 건드리지 않는다.
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
