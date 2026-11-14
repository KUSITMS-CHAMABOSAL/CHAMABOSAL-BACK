# RESULT-02 결과 확인

## 범위
- 구현 기능: RESULT-02, RESULT-03, RICE-01(4번 톡톡 완료 적립만)
- 참고 기능: RESULT-01, VOTE-04, VOTE-05, ROOM-06, ROOM-02, SHIELD-01, SHIELD-02, POT-01
- Sprint 4 첫 PR. 결과 화면 응답의 톡톡 필드(`canTap`, `tappedMemberCount`)가 톡톡 기록에 기대서 결과 화면과 톡톡을 함께 만든다
- 연속 지목 휴식(RESULT-04)은 최종 디자인에 없어 지금 없는 기능으로 본다 (사람 결정, 2026-11-15)

## 명세 점검
- 상태 전이
  - 회차: 지목된 사람이 `PICKED` 결과를 처음 조회하면 `rounds.picked_seen_at`을 기록한다. 한 번 기록하면 바꾸지 않는다 (방어 마감 기준)
  - 톡톡: 행이 생기면 그 사람의 그 회차 세트는 끝난다 (`UNIQUE (round_id, from_member_id)`). 재생 완료는 `played_at`을 채우는 한 방향
- 동시성
  - 처음 조회 시각: 지목된 사람이 결과를 동시에 두 번 열 수 있다. `picked_seen_at IS NULL`일 때만 채우는 조건부 UPDATE로 먼저 기록한 값을 남긴다. 회차의 `@Version`을 올리지 않아 다른 회차 갱신과 부딪히지 않는다
  - 톡톡 전송: 같은 사람의 중복 전송은 UNIQUE가 막는다 (`TAP_003`). "다음 투표가 열리기 전까지만"(RESULT-03 4번)은 다음 회차 투표 시작과 겹칠 수 있어, 투표 시작과 같은 방 행 락을 잡고 확인한다. `roundId`만 받으므로 VOTE-03처럼 방 ID만 먼저 읽고 락을 잡은 뒤 `READ_COMMITTED`로 읽는다
  - 재생 완료: 그 시점까지 받은 미재생 톡톡을 한 번에 재생됨으로 바꾼다 (RESULT-03 6번 "그때까지 받은"). 재시도해도 바뀌는 게 없다
- 권한
  - 결과 조회: 그 방의 현재 멤버 (`ROOM_002`)
  - 톡톡 전송: 그 방의 현재 멤버이고 지목된 본인이 아님. 투표 시작 뒤 들어온 멤버도 보낼 수 있다
  - 받은 톡톡 조회·재생 완료: 지목된 본인만 (`TAP_005`)
- 외부 연동 실패: 없음
- 금액: 톡톡을 1번 이상 보내면 보낸 사람에게 개인 +5석 (방 인원 2명 이상). 0번(건너뛰기)은 적립하지 않는다
- 제외 범위
  - 방어권(SHIELD-01·02): 방어권이 아직 없어 결과 응답의 `defenseTicketCount`는 지목된 본인에게 `0`, `canUseDefense`는 `false`. 처음 조회 시각 기록과 `defenseUsableUntil`(처음 조회 + 2분)은 이번에 만든다
  - 방어되면 톡톡 보상을 유지할지(Q-93): 방어권 PR에서
  - 번뇌 완료 보상(RESULT-03 9번, Q-92)
  - 한 솥 적립(`potEarned`): 한 솥(POT-01)과 함께
  - 앱 진입 때 보여줄 미확인 결과·미재생 톡톡(ROOM-02 오늘 탭, 팀원 담당)과 진입 순서(Q-78)
  - 꾸미기 아이템(`equippedItemCodes`): 항상 빈 배열
  - 결과 확인 저장(ROOM-06 `seen-result`, RESULT-02 8번): 팀원이 만든다

## 명세 변경
- 없음

## 설계

### API 계약
- `GET /api/rounds/{roundId}/result` (RESULT-02): `api.md` 그대로
  - `picked`: `PICKED`·`DEFENDED`일 때만. 지목된 사람의 닉네임·색상·나갔는지, 그 회차 인증 결과와 쓴 금액
  - `voteCounts`: 표를 받은 멤버만, 득표 수 많은 순 (아래 "정한 것"). `allBosalCount`: `오늘은 모두 보살` 표 수. 누가 누구에게 투표했는지는 보내지 않는다
  - `canTap`: `PICKED`이고, 내가 지목된 사람이 아니고, 아직 톡톡하지 않았고, 톡톡 시간이 지나지 않음
  - `isTapExpired`: `PICKED`인데 그 뒤 회차의 투표가 열림(그 방에 더 뒤 회차 날짜로 `VOTING`·`ANNOUNCED`인 회차가 있음)
  - `tappedMemberCount`: 나를 뺀, 그 회차에 1번 이상 톡톡한 멤버 수
  - 방어권 필드: 위 "제외 범위"
  - 에러: `ROUND_001`(404), `ROOM_002`(403), `RESULT_001`(409, 아직 발표 전)
- `POST /api/rounds/{roundId}/taps` (RESULT-03): 요청 `{ "count": 0~10 }`, 응답 201 `{ "riceEarned" }` (`potEarned`는 한 솥에서)
  - 확인 순서: `COMMON_002`(횟수) → `ROUND_001` → `ROOM_002` → `TAP_001`(`PICKED` 아님, 방어된 결과 포함) → `TAP_002`(지목된 본인) → `TAP_003`(이미 톡톡함) → `TAP_004`(톡톡 시간 지남)
- `GET /api/rounds/{roundId}/taps/received` (RESULT-03): 미재생이고 1번 이상인 톡톡만. `DEFENDED`면 빈 목록. 에러 `ROUND_001`, `ROOM_002`, `TAP_005`
- `PUT /api/rounds/{roundId}/taps/received/played` (RESULT-03): 재시도해도 성공. 에러 `ROUND_001`, `ROOM_002`, `TAP_005`
- 새 에러 코드: `RESULT_001`, `TAP_001`~`TAP_005`

### 엔티티
- `Tap` (`taps`): round, fromMember, count, playedAt. `UNIQUE (round_id, from_member_id)`
- `Round`: 처음 조회 시각 기록은 조건부 UPDATE 쿼리로 (`RoundRepository`)
- `RiceReason.TAP`, `RiceRefType.TAP`은 이미 있음

### 주요 결정
1. **결과 조회는 쓰기를 한다**: 지목된 사람의 첫 조회 시각을 남겨서 `@Transactional`(읽기 전용 아님). 조건부 UPDATE라 동시 조회에도 첫 시각 하나만 남는다
2. **"다음 투표가 열림"**: 같은 방에서 회차 날짜가 더 뒤이고 상태가 `VOTING`·`ANNOUNCED`인 회차가 있으면. `NO_VOTE` 회차도 `vote_started_at`에 확정 시각을 남기므로 그 컬럼이 아니라 상태로 판단한다
3. **톡톡 전송의 락**: 다음 투표 시작과 같은 방 행 락 안에서 "톡톡 시간 지남"을 확인한다. 그 뒤 저장과 적립까지 같은 트랜잭션
4. **톡톡 적립**: `RiceService.accrue`(사유 `TAP`, 원인 톡톡 ID, +5석). 0번이면 부르지 않는다
5. **재생 완료**: 그 회차의 미재생 톡톡을 한 번의 UPDATE로 `played_at = now`. 0번 행은 받은 톡톡에 나오지 않으므로 같이 채워도 상관없다

## 규칙 점검
- 기능 ID 상한: RICE-01은 4번 규칙(톡톡 적립) 하나만 붙이는 것이라 세 번째로 셌다
- 그 외 `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음

## 할 일
- [ ] 에러 코드 `RESULT_001`, `TAP_001`~`TAP_005`
- [ ] `Tap` 엔티티·리포지토리, 회차 첫 조회 시각 조건부 UPDATE
- [ ] 결과 조회 서비스·응답 DTO (RESULT-02)
- [ ] 톡톡 전송 서비스 (방 행 락, 적립 연결) (RESULT-03, RICE-01 4번)
- [ ] 받은 톡톡 조회·재생 완료 서비스
- [ ] 컨트롤러 4개 API
- [ ] 테스트: RESULT-02-1(최근 하나만, 확인 처리 제외)~5, RESULT-03-1~14, RICE-01-7·8

## 열린 질문
- 없음

### 정한 것 (2026-11-15, 사람 확인)
- 결과 확인 저장(`PUT /api/rooms/{roomId}/members/me/seen-result`, ROOM-06)은 팀원이 만든다. ROOM-06은 팀원 담당 명세이고 같은 ROOM-06의 한 솥 확인도 팀원이 만들었다. 그래서 RESULT-02 8번과 RESULT-02-1의 뒷부분("다음 진입 때 다시 뜨지 않는다")은 이번에 테스트하지 않는다. 이 두 질문은 `open-questions.md`에 올리지 않는다
- `voteCounts`는 표를 받은 멤버만, 득표 수가 많은 순으로 보낸다. 디자인 투표-12(동점 모두 무사)가 "민지 보살 2표 · 하린 보살 2표"처럼 표를 받은 사람만 보여주고, 투표-10·11(지목·방어)은 득표 수를 보여주지 않는다

## 자기 점검
`/spec-build`가 구현 후 작성합니다.
