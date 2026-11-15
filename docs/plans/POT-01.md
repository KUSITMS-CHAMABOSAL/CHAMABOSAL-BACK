# POT-01 공양미 한 솥

## 범위
- 구현 기능: POT-01, RICE-02 (솥 회수 2·3번), RICE-03 (목표를 채운 뒤 나가기 1번)
- 참고 기능: RICE-01 (1·8번 적립 조건과 목표 달성 보너스), CERT-01·02, VOTE-03, RESULT-03, ROOM-01 (방 목표 석), ROOM-02 (홈의 솥 진행도·축하 재생), ROOM-06 (본 완성 횟수), ROOM-07 (나가기)

## 명세 점검
- 상태 전이: 솥은 `진행 중`(`completed_at` NULL) → `가득 참` 하나뿐이다. 목표 석 이상이 되는 적립에서 `가득 참`으로 바꾸고, 그 뒤 적립도 같은 솥에 쌓는다 (Q-86). 다음 솥은 연장할 때만 생기는데 연장 API는 Q-85가 정해질 때까지 만들지 않는다 (api.md 11장 담당자 확인).
- 동시성: 솥에 쌓는 곳(인증 등록·투표 제출·톡톡 보내기)과 빼는 곳(인증 삭제), 나가기가 모두 방 행 락을 잡고 있다. 솥 갱신·달성·보상·쌀가마 배지 +1을 그 락 안에서 하므로 달성은 한 번이다 (POT-01-2). 보상 중복은 `rice_histories`의 `UNIQUE (user_id, reason, ref_type, ref_id)`가 한 번 더 막는다. 첫 솥 생성도 방 행 락 안에서 하고 `UNIQUE (room_id, seq)`로 막는다.
- 권한: 축하 화면은 그 방의 현재 멤버만 조회한다 (`ROOM_002`). 솥 적립·회수는 각 기능의 권한 확인을 그대로 따른다.
- 외부 연동 실패: 없음.
- 제외 범위: 연장·종료 요청과 화면(Q-85), 리액션 적립(REACT-01에서 붙임), 홈의 솥 진행도와 축하 재생 표시(ROOM-02, 팀원 담당), 개인 공양미 장부.

## 명세 변경
- erd.md `pot_contributions`의 "넘쳐서 두 솥에 나뉘어 들어간 경우" 문장 삭제: Q-86으로 목표를 넘긴 석도 가득 찬 솥에 계속 쌓여 한 적립이 두 솥에 나뉘지 않는다.
- erd.md `room_pots`: 첫 솥은 그 방에 처음 적립될 때 만든다는 문장 추가 (주요 결정 1).
- RICE-03 1번: 연장이 없어 "목표를 채웠다"는 방의 완성 횟수가 1 이상인 것으로 판단한다는 [구현 결정] 추가.
- POT-01 14번, api.md 11장: 나간 멤버를 "떠난 보살"로 포함하던 문장을 빼고, 순위에는 그 솥에 넣은 기록이 있는 현재 멤버만 넣는다 (사람 결정 2026-11-16). 응답의 `isLeft`도 뺀다.

## 설계

### API 계약
- `GET /api/rooms/{roomId}/pots/{seq}`: api.md 11장 그대로
  - 응답: `seq`, `goalAmount`, `rewardAmount`, `contributions[]`(`rank`, `memberId`, `nickname`, `color`, `equippedItemCodes`, `amount`)
  - 에러 코드: `POT_001` (404, 새 code), `ROOM_002` (403), `ROOM_005` (404), `COMMON_008` (401)
- 기존 응답에 필드 추가 (api.md에 이미 있음)
  - `POST /api/rooms/{roomId}/certifications`: `potEarned`
  - `DELETE /api/certifications/{certificationId}`: `potRecalled`
  - `POST /api/rounds/{roundId}/votes`: `potEarned`
  - `POST /api/rounds/{roundId}/taps`: `potEarned`

### 엔티티
- `RoomPot` (`room_pots`): `room`, `seq`, `goalAmount`, `amount`, `carriedOver`, `completedAt`. `UNIQUE (room_id, seq)`
- `PotContribution` (`pot_contributions`): `pot`, `member`, `delta`, `reason`(`PotReason` enum: `CERTIFICATION`/`VOTE`/`TAP`/`REACTION`/`RECALL`), `refId`. 인덱스 `(pot_id, member_id)`
- `Room`: `pot_completed_count`를 +1 하는 메서드 추가 (팀원 요청 4번)

### 주요 결정
1. 첫 솥은 방을 만들 때가 아니라 처음 적립할 때 만든다. 방 만들기(ROOM-01, 팀원 담당)를 건드리지 않고, 이미 있는 방도 그대로 동작한다. 솥 행이 없으면 진행도 0석, 목표는 방 목표 석이다. [구현 결정]
2. 솥 적립은 개인 적립(`RiceService.accrue`)이 실제로 기록됐을 때만 같은 석으로 한다. 1명 방, 이미 받은 보상은 솥에도 쌓지 않는다 (POT-01 1번). [구현 결정]
3. 솥 적립은 그 시점 진행 중인 솥, 없으면 가장 최근 솥에 쌓는다. 연장 전까지는 가득 찬 솥에 계속 쌓인다 (Q-86).
4. 인증 삭제 회수는 그 인증이 들어간 솥이 진행 중일 때만 그 솥에서 빼고 0석 아래로 내리지 않는다. 회수도 기여 행(`RECALL`)으로 남겨 넣은 석 계산에서 뺀다 (RICE-02 2·3번, POT-01 11·14번).
5. 목표 달성 보상은 그 순간의 현재 멤버 전원에게 `POT` / `ROOM_POT` / 솥 ID로 목표의 10%(정수 그대로)를 기록한다 (RICE-01 8번, Q-81).
6. 목표를 채운 뒤 나가면 차감하지 않는다. 연장이 아직 없어 방의 완성 횟수가 1 이상이면 목표를 채운 방이다. 연장한 방은 Q-89. [구현 결정]
7. 축하 화면 순위에는 그 솥에 넣은 기록이 있는 현재 멤버만 넣는다. 나간 멤버와 넣은 기록이 없는 멤버는 뺀다 (사람 결정 2026-11-16). 넣은 석은 그 솥의 기여 행 합계이고, 같은 석이면 그 솥의 첫 기여 행이 앞선 멤버가 앞이다 (POT-01 14·15번). `equippedItemCodes`는 결과 화면처럼 빈 목록으로 둔다 (곳간 착용 연결 전).
8. 축하 미확인 여부는 이미 있는 `RoomMember.hasUnseenPot()`·ROOM-06을 그대로 쓴다 (POT-01 12·13번). 이 PR은 완성 횟수를 올리기만 한다.

## 규칙 점검
- `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음

## 할 일
- [x] 명세: erd.md 두 문장, RICE-03 1번 [구현 결정], POT-01 14번·api.md 나간 멤버 제외
- [x] `RoomPot`·`PotContribution` 엔티티와 레포지토리, `Room` 완성 횟수 +1
- [x] `PotService.accrue`: 진행 중인 솥(없으면 생성)에 쌓기, 목표 달성 처리와 보상
- [x] 인증 등록·투표 제출·톡톡 보내기에 솥 적립 연결, 응답에 `potEarned`
- [x] 인증 삭제에 솥 회수 연결, 응답에 `potRecalled`
- [x] `RiceService.deductOnLeave`: 목표를 채운 방이면 차감 안 함
- [x] `GET /api/rooms/{roomId}/pots/{seq}` 축하 화면, `POT_001`과 에러 코드 스냅샷
- [x] openapi와 각 컨트롤러 설명
- [x] 테스트: POT-01-1~8·10, RICE-02-3·4, RICE-03-6

## 열린 질문
- 없음
- 참고: 테스트할 수 없는 시나리오는 POT-01-9(리액션, REACT-01 전), POT-01-11·12(연장, Q-85 전). POT-01은 `미구현`으로 남는다.

## 자기 점검

### 처리 규칙 · 시나리오 · 테스트

| 기능 | 처리 규칙 | 시나리오 | 테스트 |
|---|---|---|---|
| POT-01 | 1 (2명 이상, 적립 조건) | POT-01-1, 9 | `PotTest.POT_01_1`, 9는 없음 |
| POT-01 | 2 (번뇌 완료·보너스는 안 쌓음) | POT-01-10 | `DefenseMoktakTest.POT_01_10` |
| POT-01 | 3 (개인 잔액과 별도) | 없음 | 없음 |
| POT-01 | 4·5 (목표 달성과 전원 보상) | POT-01-1, 2, 8 | `PotTest.POT_01_1`, `POT_01_2`, `POT_01_8` |
| POT-01 | 6 (방장 선택 전에도 계속, Q-86) | 없음 | 없음 |
| POT-01 | 7·8 (연장) | POT-01-11, 12 | 없음 (연장 API 없음) |
| POT-01 | 9 (종료한 방) | 없음 | 없음 (종료 API 없음) |
| POT-01 | 10 (쌀가마 배지 +1) | POT-01-1 | `PotTest.POT_01_1` |
| POT-01 | 11 (0 아래로 안 내려감) | POT-01-3 | `PotTest.POT_01_3` |
| POT-01 | 12·13 (축하는 한 번, 들어올 때 채움) | POT-01-4, 5 | `PotTest.POT_01_4`, `POT_01_5` |
| POT-01 | 14·15 (넣은 석, 순위, 나간 멤버 제외) | POT-01-6, 7 | `PotTest.POT_01_6`, `POT_01_7` |
| RICE-02 | 1 (개인 회수) | RICE-02-1, 2 | `CertificationEditTest.RICE_02_1`, `RICE_02_2` |
| RICE-02 | 2 (진행 중인 솥 회수) | RICE-02-3 | `CertificationEditTest.RICE_02_3` |
| RICE-02 | 3 (채운 솥은 회수 안 함) | RICE-02-4 | `CertificationEditTest.RICE_02_4` |
| RICE-02 | 4 (끝난 투표 결과 유지) | 없음 | 없음 |
| RICE-02 | 5 (음수 허용) | RICE-02-5 | `CertificationEditTest.RICE_02_5` |
| RICE-02 | 6 (회수 기록에 방) | 없음 | 없음 |
| RICE-02 | 예외 (한 번만 기록) | RICE-02-6 | `CertificationEditTest.RICE_02_6` |
| RICE-03 | 1 (목표를 채운 뒤 나가면 차감 없음) | RICE-03-6 | `RiceLeaveDeductTest.RICE_03_6` |

### 테스트가 없는 시나리오
- POT-01-9: 리액션 적립(REACT-01)이 아직 없다. 톡톡 적립은 같은 경로라 POT-01-10 테스트의 톡톡 단계에서 함께 지난다.
- POT-01-11·12: 연장 API가 없다 (Q-85).
- RICE-03-5: 아이템 유지는 곳간(팀원 담당) 구매가 있어야 확인할 수 있다. 이번 범위 밖.

### 시나리오가 없는 처리 규칙
- POT-01 3·6·9번
- POT-01 14번의 나간 멤버 제외: 시나리오는 없고 `POT_01_6`에서 나간 멤버 D가 빠지는지 함께 확인한다.
- RICE-02 4·6번: 4번은 투표 시작 뒤 인증 삭제가 잠겨(CERT-02) 생기지 않고, 6번은 회수 기록이 지급 기록의 방을 그대로 쓴다.

### 명세에 없는 규칙을 만든 곳
- 없음. 주요 결정 1은 erd.md에, 6·7은 명세에 [구현 결정]으로 적었고, 2·3은 POT-01 1번과 Q-86을 그대로 따른다.
- 회수할 때 솥이 회수할 석보다 적으면 실제로 뺀 석만 `potRecalled`와 회수 기여 행에 남긴다 (POT-01 11번을 따른 결과).
- POT-01-7은 연장이 없어 2번째 솥을 만들 수 없어, 진행 중인 1번째 솥과 없는 2번째 솥을 조회해 둘 다 `POT_001`인지 확인한다.
