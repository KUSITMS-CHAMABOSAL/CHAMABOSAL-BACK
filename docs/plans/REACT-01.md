# REACT-01 인증 리액션 · 콕 찌르기

## 범위
- 구현 기능: REACT-01, POKE-01
- 참고 기능: RICE-01 (5번 리액션 보상), POT-01 (1번 팀 공양미, 시나리오 9), CERT-01·02 (인증·잠금), VOTE-01 (투표 시작), ROOM-02 (오늘 탭의 리액션 수·받은 콕 수 표시, 팀원 담당), POKE-02 (콕 푸시)

## 명세 점검
- 상태 전이: 리액션은 인증이 속한 회차가 `인증 중`일 때만 남기거나 바꾸거나 취소한다. 회차가 투표 중·발표·투표 없음으로 넘어가면 `REACT_002`다. 콕은 회차 상태와 상관없이 오늘 회차에 쌓고, 오늘 회차 행이 없으면 `인증 중`으로 만든다 (POKE-01 4번).
- 동시성: 둘 다 인증 등록과 같은 방 행 락을 잡는다.
  - 리액션: 투표 시작과 엇갈려 투표 시작 뒤에 남는 일을 막고, 같은 사람의 동시 요청은 `UNIQUE (certification_id, member_id)`로도 막는다. 보상은 RICE-01·POT-01 적립과 같이 락 안에서 한 번만 기록한다.
  - 콕: 같은 (회차, 보낸 사람, 받는 사람) 행의 횟수 더하기와 오늘 회차 행 생성이 동시에 일어나도 한 행에 더해진다. `UNIQUE (round_id, from_member_id, to_member_id)`
- 권한: 리액션은 인증이 있는 방의 현재 멤버만, 본인 인증은 불가. 콕은 같은 방 현재 멤버끼리만, 본인은 불가.
- 외부 연동 실패: 없음. 콕 푸시는 POKE-02.
- 제외 범위: 콕 푸시(POKE-02), 오늘 탭에서 리액션 수·받은 콕 수를 보여주는 조회(ROOM-02, 팀원 담당), 결과 판정(RESULT-01, Q-79로 배드 수를 안 씀).

## 명세 변경
- REACT-01 5번: Q-33이 정해지기 전까지 회차가 `인증 중`인 동안만 남기거나 바꿀 수 있다는 임시 결정 추가. `투표 없음` 회차도 확정 시각에 마감된다 (사람 결정 2026-11-16, `[임시: 기획 확인 중, 2026-11-16] Q-33`).
- POKE-01: 확인 순서 [구현 결정] 추가 (주요 결정 4). api.md 콕 에러 줄에도 같은 순서.

## 설계

### API 계약
- `PUT /api/certifications/{certificationId}/reactions/me`
  - 요청: `{ "type": "GOOD" | "BAD" }`
  - 응답 200: `{ "riceEarned": 0|1, "potEarned": 0|1 }`
  - 에러 코드: `COMMON_002` (400), `CERT_002` (404), `ROOM_002` (403), `REACT_001` (403, 새 code), `REACT_002` (409, 새 code), `COMMON_008` (401)
- `DELETE /api/certifications/{certificationId}/reactions/me`
  - 응답 200 (data 없음). 남긴 리액션이 없으면 성공
  - 에러 코드: `CERT_002`, `ROOM_002`, `REACT_001`, `REACT_002`, `COMMON_008`
- `POST /api/rooms/{roomId}/pokes`
  - 요청: `{ "toMemberId": 12, "count": 5 }` (`count` 1~30)
  - 응답 201 (data 없음)
  - 에러 코드: `COMMON_002` (400), `ROOM_005` (404), `ROOM_002` (403), `POKE_001` (400, 새 code), `ROOM_009` (404), `COMMON_008` (401)

### 엔티티
- `Reaction` (`reactions`): `certification`, `member`(누른 사람), `type`(`ReactionType` enum: `GOOD`/`BAD`). `UNIQUE (certification_id, member_id)`. 바꾸면 `type` 수정, 취소하면 행 삭제
- `Poke` (`pokes`): `round`, `fromMember`, `toMember`, `count`. `UNIQUE (round_id, from_member_id, to_member_id)`

### 주요 결정
1. 리액션 보상은 RICE-01 장부의 `REACTION` / `CERTIFICATION` / 인증 ID로 한 번만 기록하고, 기록됐을 때만 솥에 `REACTION`으로 같은 석을 쌓는다 (RICE-01 5번, POT-01 1번, Q-90). 취소한 뒤 다시 남겨도 장부 UNIQUE로 다시 주지 않는다.
2. "투표 시작 전"은 인증이 속한 회차의 상태가 `인증 중`인 동안이다. 투표 중·발표·투표 없음이면 `REACT_002`다 (Q-33 임시 결정, 사람 결정 2026-11-16).
3. 같은 값으로 다시 PUT하면 그대로 두고 성공한다. 같은 리액션을 다시 눌러 취소하는 동작은 프론트가 DELETE를 부른다 (api.md).
4. 콕 확인 순서: `COMMON_002` → `ROOM_005` → `ROOM_002` → `POKE_001` → `ROOM_009`. 본인인지는 받는 멤버 ID가 내 멤버 ID인지로 보고, 받는 사람이 이 방의 현재 멤버인지는 그 뒤에 본다. [구현 결정]
5. 콕은 받는 사람이 인증했는지와 상관없이 쌓고, 인증해도 지우지 않는다. 초기화는 Q-54가 정해지면 인증 등록에 붙인다.
6. 받은 콕 수와 리액션 수를 돌려주는 조회는 ROOM-02(팀원)가 만든다. 이번에는 저장만 하고, POKE-01-1의 누적은 저장된 행으로 확인한다. REACT-01-6(조회 결과에 누른 사람이 안 보임)은 조회 API가 없어 테스트하지 않는다.

## 규칙 점검
- `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음

## 할 일
- [x] 명세: REACT-01 5번 임시 결정, POKE-01 확인 순서
- [x] `Reaction`·`ReactionType` 엔티티와 레포지토리
- [x] `ReactionService` 남기기·바꾸기(보상 포함)·취소, `ReactionController`
- [x] `Poke` 엔티티와 레포지토리
- [x] `PokeService` 찌르기(오늘 회차 행 생성 포함), `PokeController`
- [x] `REACT_001`·`REACT_002`·`POKE_001`과 에러 코드 스냅샷
- [x] openapi 확인·보완
- [x] 테스트: REACT-01-1~5·8~11, POKE-01-1~7, POT-01-9 (REACT-01-6은 조회 API가 없어 뺌)

## 열린 질문
- 없음 (Q-33은 임시 결정으로 진행, 사람 결정 2026-11-16. 기획 답이 오면 open-questions.md를 결정으로 바꾼다)
- 구현을 막지 않는 질문 (ROOM-02 표시와 관련): Q-52 리액션 수를 보여주는지, Q-54 인증하면 받은 콕을 초기화하는지, Q-55 찌른 사람에게도 누적 횟수를 보여주는지

## 자기 점검

### 처리 규칙 · 시나리오 · 테스트

| 기능 | 처리 규칙 | 시나리오 | 테스트 |
|---|---|---|---|
| REACT-01 | 1·2 (하나만, 바꾸기) | REACT-01-1, 2 | `ReactionTest.REACT_01_1`, `REACT_01_2` |
| REACT-01 | 3 (취소) | REACT-01-3 | `ReactionTest.REACT_01_3` |
| REACT-01 | 4 (본인 인증 불가) | REACT-01-4, 8 | `ReactionTest.REACT_01_4`, `REACT_01_8` |
| REACT-01 | 5 (인증 중일 때만, Q-33 임시) | REACT-01-5 | `ReactionTest.REACT_01_5` |
| REACT-01 | 6 (누른 사람 비공개) | REACT-01-6 | 없음 (조회는 ROOM-02) |
| REACT-01 | 7 (처음 1번 보상, 회수 없음) | REACT-01-10, 11, POT-01-9 | `ReactionTest.REACT_01_10`, `REACT_01_11`, `DefenseMoktakTest.POT_01_9` |
| REACT-01 | 8 (배드 수는 판정에 안 씀) | 없음 | 없음 (RESULT-01은 리액션을 읽지 않음) |
| REACT-01 | 9 (남기기 확인 순서) | REACT-01-4, 5, 8 | 위와 같음 |
| REACT-01 | 10 (취소 확인 순서) | REACT-01-9 | `ReactionTest.REACT_01_9` |
| POKE-01 | 1 (같은 방 현재 멤버, 본인 불가) | POKE-01-3, 4 | `PokeTest.POKE_01_3`, `POKE_01_4` |
| POKE-01 | 2 (인증 여부 상관없음) | POKE-01-2 | `PokeTest.POKE_01_2` |
| POKE-01 | 3 (회차 상태 상관없음) | POKE-01-5 | `PokeTest.POKE_01_5` |
| POKE-01 | 4 (오늘 회차, 없으면 생성) | POKE-01-6 | `PokeTest.POKE_01_6` |
| POKE-01 | 5·7 (제한 없이 누적, 받는 사람 기준) | POKE-01-1 | `PokeTest.POKE_01_1` |
| POKE-01 | 6 (요청 한 번 1~30) | POKE-01-7 | `PokeTest.POKE_01_7` |
| POKE-01 | 8 (인증하면 초기화) | 없음 | 없음 ([미정] Q-54) |
| POKE-01 | 9 (누적 표시) | 없음 | 없음 ([미정] Q-55, 표시는 ROOM-02) |
| POKE-01 | 10 (확인 순서) | POKE-01-3, 4, 7 | 위와 같음 |
| POT-01 | 1 (톡톡 완료·리액션도 팀 공양미) | POT-01-9 | `DefenseMoktakTest.POT_01_9` |

### 테스트가 없는 시나리오
- REACT-01-6: 리액션 수를 돌려주는 조회가 ROOM-02(팀원 담당)라 아직 없다. 보여줄지도 [미정] Q-52.

### 시나리오가 없는 처리 규칙
- REACT-01 8번: 결과 판정 코드는 리액션을 읽지 않아 지킨다.
- POKE-01 8·9번: [미정] Q-54, Q-55.
- 두 기능의 동시성(방 행 락): 시나리오가 없어 동시 요청 테스트는 만들지 않았다.

### 명세에 없는 규칙을 만든 곳
- 없음. REACT-01 5번의 Q-33 임시 결정과 POKE-01 10번 확인 순서는 명세에 적었다.
- 리액션 취소에서 인증이 없거나 삭제됐으면 `CERT_002`, 같은 값 PUT은 그대로 성공 (api.md 그대로).

### 상태
- REACT-01: REACT-01-6 테스트가 없어 `미구현` 유지
- POKE-01: 8·9번이 [미정]이라 `미구현` 유지
