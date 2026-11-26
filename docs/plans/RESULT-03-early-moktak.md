# RESULT-03 이마 톡톡 전원 완료 시 목탁 수행 조기 오픈

## 범위
- 구현 기능: RESULT-03 (5번 전달 시점, 6번 전달 시각, 9번 목탁 수행 시작), VOTE-05 (`목탁 수행` 상태 조건)
- 함께 고치는 문서: RICE-01 6번 (목탁 수행 시작 시점 문장), `common.md` 이마 톡톡·목탁 행, `api.md` 받은 톡톡 조회 `deliveredAt`
- 참고 기능: SHIELD-02 (방어 = 지목된 사람이 결과를 처음 연 순간부터 2분, `picked_seen_at`), RESULT-02 (`isTapExpired`), ROOM-07 나가기·ROOM-08 내보내기·USER-05 내 데이터 삭제 (멤버가 빠지는 경로), VOTE-04 (`closeIfAllVoted` 패턴), Q-92·Q-93·Q-27
- 앞 작업: `docs/plans/RESULT-03.md` (톡톡 보내기·전달, PR #82)

## 명세 점검
- 상태 전이
  - 지금은 전달을 저장하지 않고 `투표 마감 + 5시간`이 지났는지로만 판단한다 (RESULT-03 5번).
  - 새 상태: 회차에 `전원 완료 시각`을 한 번만 남긴다. 비어 있음 → 기록됨으로만 바뀌고 되돌아가지 않는다.
  - 방어 마감 = 지목된 사람이 결과를 처음 연 시각 + 2분 (SHIELD-02 3번, `Round.getDefenseClosesAt`). 아직 열지 않았으면 없다(톡톡 마감 전까지 방어 기회가 남아 있다).
  - 전달(목탁 열림) = `지금 ≥ 투표 마감 + 5시간` 또는 `(전원 완료 시각 있음 그리고 방어 마감 있음 그리고 지금 > 방어 마감)`
  - 전달 시각 = 조기 오픈이면 `max(전원 완료 시각, 방어 마감)`, 아니면 `투표 마감 + 5시간`
  - 방어 마감이 지나기 전에는 방어로 톡톡이 무효가 될 수 있어 전달하지 않는다. 방어는 방어 마감 시각까지 허용(`isAfter`)되므로 조기 전달은 그 시각을 넘긴 뒤부터다. 방어되면 결과가 `DEFENDED`라 전원 완료 기록이 있어도 톡톡·목탁이 없다 (RESULT-03 7번).
  - 지목된 사람이 결과를 열지 않으면 전원이 보냈어도 조기 전달하지 않고, 열면 그때부터 2분 뒤에 전달한다. 끝까지 열지 않으면 `투표 마감 + 5시간`에 전달한다.
  - 한 번 전달되면 받은 톡톡 합계는 바뀌지 않는다. 전원이 이미 보냈고, 투표 시작 뒤 들어온 사람과 다시 들어온 사람은 보낼 수 없다.
- 동시성
  - 전원 완료 확인과 기록은 방 행 락 안에서, 상태가 바뀌는 순간에만 한다. `VoteCloseService.closeIfAllVoted`와 같은 패턴이다.
  - 톡톡 보내기는 이미 방 행 락 안이다. 마지막 두 명이 동시에 보내도 락 순서대로 확인해 한 번만 기록한다. 기록은 비어 있을 때만 채운다.
  - 나가기·내보내기·내 데이터 삭제는 `RoomService.removeMember`(방 행 락 안)에서 `startIfAllCertified`, `closeIfAllVoted` 바로 옆에서 확인한다.
  - 방어(`DefenseService.use`)도 같은 방 행 락이라 방어와 전원 완료 기록이 엇갈리지 않는다.
  - 처음 연 시각은 결과 조회의 조건부 UPDATE(`markPickedSeen`)로 한 번만 남는다. 전달은 전원 완료 시각과 처음 연 시각으로 읽을 때 계산하고, 두 값 모두 비어 있음 → 기록됨으로만 바뀌어 전달이 되돌아가지 않는다.
- 권한: 새 API 없음. 받은 톡톡 조회(지목된 본인, `TAP_005`), 목탁 완료(`TAP_005`) 권한은 그대로다.
- 외부 연동 실패: 없음 (알림 없음).
- 제외 범위
  - 조기 오픈을 알리는 푸시 알림
  - 방어할 수 없는 지목자(미인증, 방어권 0개)라도 결과를 연 뒤 2분은 기다린다. 2분 안에 방어권을 살 수 있고 규칙을 단순하게 둔다 [구현 결정]
  - 톡톡 보내기 마감(`TAP_004`)과 결과 화면 `isTapExpired`는 그대로 `투표 마감 + 5시간` 기준이다. 전원이 보냈으면 더 보낼 사람이 없다.

## 명세 변경
- RESULT-03 5번: "톡톡 마감 시각에 한 번에 전달"에 "지목된 사람을 뺀 회차 멤버 전원이 보냈으면(건너뛰기 포함) 지목된 사람의 방어 시간(결과를 처음 연 순간부터 2분)이 지난 뒤 바로 전달"을 더한다. 전원 완료 시각을 회차에 남긴다. [기획 결정 2026-11-27] [구현 결정]
  - 전원 = 톡톡 보낼 자격이 있는 사람: 지목된 사람을 뺀 지금 방 멤버 중 투표 시작 전부터 있던 사람 (RESULT-02 4번, `TAP_006` 기준). 나간 사람은 세지 않는다.
  - 지목된 사람이 방을 나간 결과는 톡톡이 없어 기록하지 않는다 (Q-27).
- RESULT-03 6번: 전달 시각을 위 규칙으로 돌려준다.
- RESULT-03 9번, RICE-01 6번: 목탁 수행은 "톡톡이 전달된 뒤부터"를 유지하고, 전달 시점은 5번을 따른다고 적는다.
- VOTE-05 `목탁 수행` 상태: "톡톡 전달됨"의 뜻을 5번 기준으로 적는다.
- `common.md` 이마 톡톡 행, `api.md` 받은 톡톡 조회 `isDelivered`·`deliveredAt` 설명.
- 시나리오 추가: RESULT-03-22 ~ 30, VOTE-05 1개.
- `open-questions.md`: Q-92 결정 칸에 "2026-11-27 추가: 전원이 보내면(건너뛰기 포함) 지목된 사람의 방어 시간이 끝난 뒤 바로 전달" 덧붙임 (사람 확인 2026-11-27).
- 시나리오 시각 기준: 15:00 투표 마감, 톡톡 마감 20:00.

## 설계

### API 계약
- 새 API 없음. 응답 필드 추가 없음.
- `GET /api/rounds/{roundId}/taps/received`: `isDelivered`·`deliveredAt`이 새 규칙을 따른다. 조기 오픈이면 `deliveredAt`이 `투표 마감 + 5시간`보다 이르다.
- `POST /api/rounds/{roundId}/moktak`: `MOKTAK_005`(전달 전) 판단이 새 규칙을 따른다.
- `GET /api/rooms/{roomId}/vote`: `MOKTAK` 상태가 새 규칙을 따른다.
- 에러 코드: 추가 없음.

### 엔티티
- `Round`
  - `tapsCompletedAt` (DATETIME, NULL) 추가. 운영 DB는 ddl update로 칼럼이 자동 추가된다.
  - `completeTaps(now)`: 비어 있을 때만 남긴다.
  - `getTapDeliveredAt()`: 위 전달 시각 규칙 (조기 오픈 조건이 안 되면 `투표 마감 + 5시간`)
  - `isTapDelivered(now)`: `!isTapOpen(now)` 또는 `(전원 완료 시각 있음 그리고 방어 마감 있음 그리고 now.isAfter(방어 마감) 그리고 !now.isBefore(전원 완료 시각))`
- `erd.md` `rounds`에 칼럼 추가.

### 주요 결정
- 전원 완료 확인 서비스: `TapService.completeTapsIfAll(Round, now)` (`Propagation.MANDATORY`, 방 행 락 안)과, 나가기 경로용 `completeTapsIfAll(roomId, now)`.
  - 대상 회차: 그 방의 가장 최근 `ANNOUNCED` 회차.
  - `PICKED`가 아니거나, 지목된 사람이 방에 없거나, 이미 기록됐거나, 톡톡 마감(`투표 마감 + 5시간`)이 지났으면 아무것도 하지 않는다.
  - 자격 있는 멤버 ID 집합이 그 회차에 톡톡을 보낸 멤버 ID 집합에 모두 들어 있으면 기록한다. 0명이면 기록한다 (받은 톡톡이 없어 목탁은 `MOKTAK_002`로 그대로 막힌다).
- 읽는 쪽 교체: `round.isTapOpen(now)`를 "전달 전" 뜻으로 쓰던 곳만 `!round.isTapDelivered(now)`로 바꾼다.
  - `TapService.getReceived` (`isDelivered`, `deliveredAt`)
  - `MoktakService.complete` (`MOKTAK_005`)
  - `MoktakService.isMoktakDue` (투표 탭 `MOKTAK`)
  - `TapService.findTapBlock`의 `TAP_004`, `RoundResultService`의 `isTapExpired`는 그대로 둔다.
- 다음 투표가 먼저 열리면 목탁 기한이 지나는 규칙(Q-92, `MOKTAK_003`)은 그대로다.

## 규칙 점검
- `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음
  - 현재 시각은 주입받은 `Clock`, 시나리오 테스트는 고정 `Clock`(`MutableClock`)으로 쓴다.
  - "확인 후 저장"은 방 행 락으로 막는다.
  - 조회 전용 메서드(`getReceived`)는 쓰기가 없어 그대로 `readOnly`다.

## 할 일
- [ ] 명세: RESULT-03 5·6·9번, RICE-01 6번, VOTE-05 목탁 수행 상태, common, api, erd, 시나리오 RESULT-03-22~30·VOTE-05 1개 (Q-92는 계획서 확정 때 덧붙임)
- [ ] 테스트 먼저 작성하고 실패 확인
- [ ] `Round`: `tapsCompletedAt`, `completeTaps`, `getTapDeliveredAt`, `isTapDelivered`
- [ ] `TapService.completeTapsIfAll` (회차·방 두 가지)과 `send`에서 호출
- [ ] `RoomService.removeMember`에서 호출 (나가기·내보내기·내 데이터 삭제)
- [ ] 읽는 쪽 교체: `getReceived`, `MoktakService.complete`, `isMoktakDue`
- [ ] Swagger 설명, `openapi.yaml` 초안 (`deliveredAt` 설명)
- [ ] 테스트: RESULT-03-22 ~ 30, VOTE-05 추가 시나리오, 기존 RESULT-03·VOTE-05·RICE-01 회귀
- [ ] 프론트 전달 목록: "전원이 톡톡하면 목탁이 5시간보다 일찍 열릴 수 있음, `deliveredAt`이 이를 수 있음, 프론트 수정 없음"

### 추가할 시나리오 (번호만, 문장은 명세에 씀)
- RESULT-03-22 A가 결과를 연 뒤 2분 안에 전원이 보냄 → 연 시각 + 2분을 넘기면 전달 (그 전에는 빈 목록, `MOKTAK_005`)
- RESULT-03-23 A의 방어 시간이 지난 뒤 마지막 사람이 보냄 → 그 순간 전달, `deliveredAt` = 그 시각
- RESULT-03-24 전원이 보냈지만 A가 결과를 열지 않음 → 전달 안 됨, A가 열면 그때 + 2분을 넘긴 뒤 전달
- RESULT-03-25 한 명이 안 보냄 → 투표 마감 + 5시간까지 전달 안 됨
- RESULT-03-26 안 보낸 사람이 나가거나 내보내짐 → 그 순간 기록, 방어 시간이 지났으면 바로 전달
- RESULT-03-27 건너뛰기(0번)도 보낸 것으로 셈
- RESULT-03-28 방어하면 전원 완료여도 받은 톡톡·목탁 없음
- RESULT-03-29 마지막 두 명이 동시에 보냄 → 기록 한 번, 시각 하나
- RESULT-03-30 지목된 사람이 나간 결과는 기록하지 않음
- VOTE-05-?? 전원 톡톡 뒤 A의 방어 시간이 지나면 A의 투표 탭은 `목탁 수행`

## 열린 질문
- 없음. 정책 두 가지(전원이 보내면 지목된 사람의 방어 시간이 끝난 뒤 바로 전달, 건너뛰기도 보낸 것으로 셈)는 기획 확인 완료 (2026-11-27). Q-92 결정 칸에 덧붙임 (사람 확인 2026-11-27).

## 자기 점검
`/spec-build`가 구현 후 작성합니다.
