# ROOM-09 목표 달성 뒤 선택

## 범위
- 구현 기능: ROOM-09, POT-01 (6~9번 다음 솥 자동 시작), ROOM-02 (21번 선택 대기 표시)
- 참고 기능: RICE-01 (8번 목표 달성 보너스), RICE-02 (2·3번 솥 회수), RICE-03 (1번 목표를 채운 방), ROOM-06 (본 완성 횟수), ROOM-07 (방장 위임, 11번 방 삭제), CLEAN-01 (나간 멤버 기록 정리), USER-01 (참여 방 목록)

## 명세 점검
- 상태 전이
  - 솥: `진행 중` → `가득 참`. 가득 차는 적립에서 다음 솥(`seq + 1`)을 `진행 중`으로 바로 만든다. 지금은 가득 찬 솥에 계속 쌓는데(Q-86) 이를 바꾼다.
  - 방: `평소` → `선택 대기`(솥이 가득 찰 때) → `평소`([더 수행하기]) 또는 `삭제`([방 닫기]). 선택 대기 중 솥이 또 차도 선택 대기 그대로다 (9번).
- 동시성
  - 솥 적립·완성은 지금처럼 방 행 락 안에서 한다. 다음 솥 생성과 선택 대기 표시도 같은 락 안이라 한 번만 일어난다. 다음 솥은 `UNIQUE (room_id, seq)`가 한 번 더 막는다.
  - [더 수행하기]·[방 닫기]는 방 행 락을 잡고 선택 대기를 확인한다. 동시에 와도 먼저 처리된 쪽이 선택 대기를 끝내서 다른 쪽은 `ROOM_011`이다 (ROOM-09-10).
  - [방 닫기]와 멤버 나가기·입장·인증은 모두 방 행 락이라 하나씩 처리된다. 닫기는 멤버의 참여 방 수를 줄이기만 해서 사용자 행 락은 잡지 않는다.
- 권한: 방장만 고른다 (`ROOM_007`). 선택 대기 여부는 방장의 오늘 탭에만 `true`로 나간다.
- 외부 연동 실패: [방 닫기]의 사진 저장소 삭제는 나가기와 같이 커밋 뒤에 한다(`deleteAfterCommit`). 실패해도 DB는 지움 표시라 닫기는 성공한다.
- 제외 범위: 목표 재설정, 팝업 화면, 팝업과 축하 화면의 순서(Q-107, 클라이언트가 `entry`와 함께 판단).

## 명세 변경
- 이번 브랜치에서 이미 반영: POT-01 6~9번·출력·시나리오 11~13, ROOM-02 21번·출력, ROOM-09 신규, api.md, Q-84·Q-85 결정, Q-89 수정, Q-106·Q-107 추가.
- erd.md `rooms`: `pot_decision_requested_at` 추가 (엔티티 참고).
- erd.md `room_pots`·`room_members`: "목표를 넘겨도 같은 솥에 계속 쌓임(Q-86)" 설명을 다음 솥 자동 시작으로 바꾸고, `leave_type`에 `ROOM_CLOSED` 추가.
- RICE-03 1번: "연장이 아직 없어" 문구를 "다음 솥이 자동으로 시작돼도"로 바꾼다. #168이 같은 줄을 고쳐 #168 머지 뒤에 고친다.

## 설계

### API 계약
- `POST /api/rooms/{roomId}/pot-decision/continue` ([더 수행하기])
  - 요청: 없음
  - 응답: 200, `data` 없음
  - 에러 코드: `ROOM_005` (404) → `ROOM_002` (403) → `ROOM_007` (403) → `ROOM_011` (409, 새 code)
- `DELETE /api/rooms/{roomId}` ([방 닫기])
  - 요청: 없음
  - 응답: 200, `data` 없음
  - 에러 코드: 위와 같은 순서
- `GET /api/rooms/{roomId}/today`: `me.isPotDecisionPending` 추가. 방장이고 선택 대기면 `true`.

### 엔티티
- `Room`: `potDecisionRequestedAt` (`LocalDateTime`, NULL이면 선택 대기 아님). 운영 DB에는 `ddl-auto: update`로 NULL 허용 컬럼이 생겨 기존 방은 선택 대기가 아니다.
  - `requestPotDecision(now)`: NULL일 때만 기록 (9번)
  - `continuePot()`: NULL로 되돌림
  - `isPotDecisionPending()`
- `RoomPot`: `next(previous)` 추가. `seq + 1`, 같은 `goalAmount`, `carriedOver = amount = previous.amount - previous.goalAmount`.
- `LeaveType`: `ROOM_CLOSED` 추가 (`@Enumerated(STRING)`, 컬럼이 `varchar(20)`이라 값 추가에 DDL이 필요 없음).
- 새 에러 code `ROOM_011` (409, "목표 달성 뒤 선택할 차례가 아니에요.") → `error-codes.snapshot`에 추가.

### 주요 결정
1. 솥을 넘기는 적립의 기여는 나누지 않는다. 295석에서 10석이 들어오면 1번째 솥에 10석 기여로 기록하고(진행도 305석), 넘친 5석은 2번째 솥의 `carriedOver`·시작 진행도가 된다. 2번째 솥의 이월 5석은 누구의 기여로도 세지 않는다. 기여 행이 한 인증에 하나라 회수 조회(`findByMember_IdAndReasonAndRefId`)가 그대로 동작하고, 그 인증을 지워도 1번째 솥은 가득 차서 회수하지 않는다 (RICE-02 3번). [구현 결정]
2. 다음 솥은 가득 차는 적립 안에서 만든다. 적립은 언제나 가장 최근 솥(진행 중)에 쌓인다.
3. 운영 DB에 이미 가득 찬 솥이 없어(2026-11-19 확인) 옛 Q-86 동작으로 쌓인 방을 이어주는 처리는 넣지 않는다.
4. 솥 완성(`PotService.complete`)에서 `room.requestPotDecision(now)`를 부른다. 보상 지급·완성 횟수 +1과 같은 락 안이다.
5. [방 닫기]는 현재 멤버 전원을 `leftAt = now`, `leaveType = ROOM_CLOSED`로 나가게 하고 방을 삭제한다. 나가기 차감은 하지 않는다 (ROOM-09 7번). 멤버를 현재 상태로 두면 삭제된 방의 행이 현재 멤버로 남아, 6개월 뒤 기록 정리(CLEAN-01)와 내 데이터 삭제(USER-05)의 "참여 중인 방" 처리에서 빠지기 때문이다. "마지막 멤버가 나가 방이 삭제될 때는 현재 멤버가 없다"는 기존 전제도 지켜진다. [구현 결정]
6. [방 닫기]는 멤버 전원이 나가는 것이라 나가기(ROOM-07 3번, Q-48)와 같이 멤버 전원의 이 방 인증 사진을 바로 지운다. 진행 중 회차는 마지막 멤버가 나가 방이 삭제될 때(ROOM-07 11번)와 같이 마감·판정 없이 둔다. 다른 멤버 알림은 Q-106 답 전까지 보내지 않는다 (예약 알림 NOTI-02도 아직 없음).
7. `ROOM_011`은 "선택 대기가 아님"이다. 동시 요청에서 진 쪽은 [더 수행하기]가 먼저였으면 `ROOM_011`, [방 닫기]가 먼저였으면 방이 삭제돼 `ROOM_005`다 (코드리뷰 반영).
8. 오늘 탭의 선택 대기 여부는 방장에게만 `true`다. 방장이 위임되면 방 상태라 새 방장에게 보인다 (10번).

## 규칙 점검
- `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음
  - 현재 시각은 `Clock`, 엔티티 enum은 `STRING`, 응답은 `ResponseEntity<Response<T>>`
  - 선택 확인 후 저장은 방 행 락으로 막는다
  - 외부 API 호출 없음

## 할 일
- [x] `RoomPot.next`, `PotService.accrue`에서 가득 차면 다음 솥 생성 (주요 결정 1·2·3)
- [x] `Room` 선택 대기 필드·메서드, `PotService.complete`에서 선택 대기 표시
- [x] `LeaveType.ROOM_CLOSED`, `ROOM_011` 에러 code와 스냅샷
- [x] `RoomService.continuePot`, `RoomService.close`(멤버 전원 나감·사진 삭제·방 삭제) + 컨트롤러 2개 (Swagger 포함)
- [x] 오늘 탭 `me.isPotDecisionPending`
- [x] erd.md, openapi.yaml 갱신
- [x] 테스트: POT-01-11~13, ROOM-09-1~11
- [x] 상태 갱신: ROOM-09 `구현됨`, ROOM-02 `구현됨`

## 열린 질문
1. Q-106: [방 닫기] 때 다른 멤버에게 알림을 보낼지. 답 전까지 보내지 않는다.
2. Q-107: 선택 팝업과 한 솥 축하 화면의 순서. 서버는 플래그만 주므로 구현에는 영향 없음.
3. Q-89: 다음 솥이 시작된 방에서 나갈 때 차감. 지금 동작(완성 횟수 1 이상이면 차감 없음)을 유지한다.

## 자기 점검

### 처리 규칙 · 시나리오 · 테스트

| 처리 규칙 | 시나리오 | 테스트 |
|---|---|---|
| POT-01 6번 (다음 솥 자동 시작) | POT-01-11 | `PotTest.POT_01_11_목표를_채우면_넘친_석으로_다음_솥이_시작된다` |
| POT-01 7번 (이월, 다시 보너스) | POT-01-11, POT-01-13 | `PotTest.POT_01_11_…`, `PotTest.POT_01_13_다음_솥을_채우면_다시_보너스를_준다` |
| POT-01 8번 (공양미·아이템 유지) | POT-01-12 | `PotTest.POT_01_12_다음_솥이_시작돼도_잔액은_보너스만_더해진다` |
| POT-01 9번 ([방 닫기] = 방 삭제) | ROOM-09-3 | `RoomPotDecisionTest.ROOM_09_3_방_닫기면_방이_삭제된다` |
| ROOM-02 21번, ROOM-09 1·3번 | ROOM-09-1 | `RoomPotDecisionTest.ROOM_09_1_솥을_채우면_방장에게만_선택_대기다` |
| ROOM-09 2번 (선택 대기 중 계속) | ROOM-09-6 | `RoomPotDecisionTest.ROOM_09_6_선택_대기_중에도_인증하면_다음_솥에_쌓인다` |
| ROOM-09 4번 (방장만) | ROOM-09-4 | `RoomPotDecisionTest.ROOM_09_4_방장이_아니면_ROOM_007이다` |
| ROOM-09 5번 (더 수행하기) | ROOM-09-2 | `RoomPotDecisionTest.ROOM_09_2_더_수행하기면_선택_대기가_끝나고_방은_그대로다` |
| ROOM-09 6번 (방 닫기, 사진) | ROOM-09-3, ROOM-09-11 | `RoomPotDecisionTest.ROOM_09_3_…`, `CertificationPhotoTest.ROOM_09_11_방을_닫으면_멤버_전원의_사진이_바로_삭제된다` |
| ROOM-09 7번 (차감 없음) | ROOM-09-9 | `RoomPotDecisionTest.ROOM_09_9_방_닫기로는_공양미를_차감하지_않는다` |
| ROOM-09 8번 (`ROOM_011`) | ROOM-09-5 | `RoomPotDecisionTest.ROOM_09_5_선택_대기가_아니면_ROOM_011이다` |
| ROOM-09 9번 (한 번만 고름) | ROOM-09-8 | `RoomPotDecisionTest.ROOM_09_8_선택_대기_중_솥을_또_채워도_한_번만_고른다` |
| ROOM-09 10번 (위임 뒤 새 방장) | ROOM-09-7 | `RoomPotDecisionTest.ROOM_09_7_방장이_나가면_위임된_방장이_고른다` |
| ROOM-09 12번 (동시 선택) | ROOM-09-10 | `RoomPotDecisionTest.ROOM_09_10_동시에_골라도_하나만_처리된다` |

- 기존 테스트 수정: `PotTest.POT_01_1`, `POT_01_2`는 옛 Q-86 동작(넘친 석이 가득 찬 솥에 남음)을 확인하던 부분을 다음 솥 기준으로 바꿨다.

### 테스트가 없는 시나리오
- 없음

### 시나리오가 없는 처리 규칙
- ROOM-09 11번 (팝업과 축하 화면 순서): [미정] Q-107이고 서버 동작이 없다.
- ROOM-09 6번 중 다른 멤버 알림: [미정] Q-106, 보내지 않는다.
- ROOM-09 6번 중 진행 중 회차를 그대로 두는 부분: 따로 시나리오가 없다. 방 삭제 뒤 투표 시작·마감 배치가 삭제된 방을 건너뛰는 기존 동작(ROOM-07-9)에 기댄다.

### 명세에 없는 규칙을 만든 곳
- 넘기는 적립의 기여를 나누지 않음 (주요 결정 1). erd.md `room_pots`에 적음.
- [방 닫기] 때 멤버를 `ROOM_CLOSED`로 나가게 함 (주요 결정 5). ROOM-09 6번 `[구현 결정]`, erd.md `leave_type`에 적음.
- 위 두 가지 외에는 명세의 `[구현 결정]`(ROOM-09 7·8·9·10·12번)을 따랐다.
