# VOTE-03 투표 제출

## 범위
- 구현 기능: VOTE-03, VOTE-02, VOTE-05
- 참고 기능: CERT-05, VOTE-04, RESULT-01, RICE-01, RESULT-02

## 명세 점검
- 상태 전이: 제출로 전원 투표가 되면 VOTE-04(마감)까지 같은 트랜잭션에서 이어짐
- 동시성: 마지막 두 표가 동시에 들어와도 마감·보상이 한 번만 되어야 함 (방 행 락 + `votes` UNIQUE 제약)
- 권한: 투표 명단에 있고 투표 시작 시점까지 인증한 사용자만 제출 가능. 본인·휴식 중 후보는 서버가 차단
- 외부 연동 실패: 없음
- 제외 범위:
  - 공양미 적립(RICE-01)·한 솥 적립(POT-01) 자체 — 아직 그 도메인이 없어 이번 PR에서 보상 지급·응답 필드를 모두 뺀다 (사람 확인 완료)
  - 결과 화면(RESULT-02), 콕(POKE-01), 방어권(SHIELD-01·02)
  - 투표 중 나간 명단 멤버가 후보로 남는지·지목될 수 있는지 (Q-27, 미정) — 이번 구현은 나간 멤버도 그대로 후보로 노출한다 (스냅샷 그대로, 현재 멤버 여부로 거르지 않음). Q-27이 다르게 정해지면 다시 손본다
  - 꾸미기 아이템(`equippedItemCodes`) — 아이템 도메인이 없어 항상 빈 배열
  - 투표 중 3명 미만이 되는 경우의 상태(Q-34, 미정) — 이번 구현은 방 인원만으로 `UNDER_THREE`를 판단해 회차 상태보다 먼저 확인한다 (완전히 해결하진 않음)
  - 첫 미인증 멤버 선정 순서(Q-25, 미정) — 이번 구현은 입장 순서(`joinedAt` 오름차순)로 임시 정렬

## 명세 변경
- 없음 (VOTE-04·RESULT-01 명세 `상태`를 `구현됨`으로 바꾸는 건 별도 docs 커밋, 이 계획과는 무관)

## 설계

### API 계약

**POST /api/rounds/{roundId}/votes** (VOTE-03)
- 요청: `{ "targetType": "MEMBER" | "ALL_BOSAL", "targetMemberId": Long? }`
- 응답 201: `{ "isClosed": boolean }` (`riceEarned`·`potEarned`는 RICE-01 PR에서 추가)
- 에러: `ROUND_001`(404), `ROOM_002`(403), `VOTE_001`(409, 투표 중 아님), `VOTE_002`(403, 명단 밖: 투표 시작 뒤 들어왔거나 나갔다가 다시 들어옴), `VOTE_003`(403, 투표권 없음), `VOTE_004`(409, 이미 투표함), `VOTE_005`(400, 본인 선택), `VOTE_006`(400, 선택할 수 없는 후보), `COMMON_002`(400)

**GET /api/rooms/{roomId}/vote** (VOTE-05, 후보 목록은 VOTE-02)
- 응답: `status` + 상태별 필드 (`common.md`/`api.md`의 8개 상태표 그대로)
- 에러: `ROOM_002`(403), `ROOM_005`(404)

### 엔티티
- 새 엔티티 없음. 기존 `Vote`, `RoundParticipant`, `Round`, `RoomMember`, `Certification` 사용
- 리포지토리 추가
  - `RoundRepository`: `findRoomIdByRoundId`(락 전 roomId만), `findFirstByRoom_IdOrderByRoundDateDesc`(가장 최근 회차 행, 상태 무관)
  - `RoundParticipantRepository`: `findByRound_IdAndMember_Id`, `countByRound_IdAndCanVoteTrue`
  - `VoteRepository`: `existsByRound_IdAndVoterMember_Id`, `countByRound_Id`
  - `CertificationRepository`: `findByRound_IdAndDeletedAtIsNull`(후보 카드용 인증 내용을 배치로, N+1 방지)

### 주요 결정
1. **락 순서**: `roundId`만 받으므로 ① `findRoomIdByRoundId`로 roomId만 읽고 ② `roomRepository.findWithLockById`로 락을 잡은 뒤 ③ 그제서야 `roundRepository.findById`로 회차를 처음 영속성 컨텍스트에 올린다. 락 걸기 전에 회차를 먼저 읽으면 락을 기다리는 동안 다른 트랜잭션이 바꾼 내용을 못 보는 문제가 CERT-01에서 코드리뷰로 걸린 적이 있어, 같은 실수를 피한다.
2. **제출 검증 순서**: 명단 소속(`RoundParticipant` 존재) → 다시 들어왔는지(`member.isCurrent() && !joinedAt.isAfter(voteStartedAt)`, 아니면 `VOTE_002`) → 투표권(`canVote`, 아니면 `VOTE_003`) → 중복 제출(`VOTE_004`) → 본인 선택(`VOTE_005`) → 후보 유효성(대상이 명단의 `isCandidate`이고 휴식 중이 아님, 아니면 `VOTE_006`)
3. **마감 연결**: `Vote` 저장 후 같은 트랜잭션에서 `voteCloseService.closeIfAllVoted(round, now)`(`MANDATORY`)를 불러 전원 투표 시 즉시 마감한다. 응답의 `isClosed`는 이 호출의 반환값
4. **VOTE-05 상태 판정 순서** (Q-34 관련 임시 결정 포함): `memberCount < 3` → `UNDER_THREE` / 그 방에 `VOTING` 회차가 있으면 그 회차 기준으로 `NOT_IN_LIST`·`NO_VOTING_RIGHT`·`VOTED`·`VOTING` 중 하나 / 없으면 가장 최근 `ANNOUNCED` 회차가 있는지로 `RESULT_UNSEEN`·`RESULT_SEEN`(없으면 `BEFORE_FIRST_VOTE`) 판정. `NO_VOTE` 회차는 "지난 결과"로 치지 않는다(보여줄 결과가 없음)
5. **후보 목록(VOTE-02)**: 그 회차 `RoundParticipant` 중 `isCandidate=true`인 멤버(본인 제외) + 항상 `오늘은 모두 보살`. `isResting`이면 선택 불가 사유 `휴식 중`. 인증 내용은 배치 조회로 N+1 없이 붙인다

## 규칙 점검
- `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음

## 할 일
- [ ] `RoundRepository`·`RoundParticipantRepository`·`VoteRepository`·`CertificationRepository`에 조회 메서드 추가
- [ ] `BaseErrorCode`에 `ROUND_001`, `VOTE_001`~`VOTE_006` 추가, `error-codes.snapshot` 갱신
- [ ] `VoteSubmitService` 구현 (락 순서·검증 순서·마감 연결)
- [ ] `VoteCandidateService`(VOTE-02 후보 계산, VOTE-05에서 재사용) 구현
- [ ] `VoteTabStatusService`(VOTE-05 상태 판정) 구현
- [ ] `VoteController`: `POST /api/rounds/{roundId}/votes`, `GET /api/rooms/{roomId}/vote`
- [ ] 요청·응답 DTO (record), `VoteSubmitRequest` 검증(`targetType`별 `targetMemberId` 유무)
- [ ] 테스트: VOTE-02-1~8, VOTE-03-1~9, VOTE-05-1~10 (보상 검증 제외)

## 열린 질문
- 없음 (Q-25, Q-27, Q-34는 이미 `open-questions.md`에 있고 이번 구현은 위 "제외 범위"의 임시 결정으로 우회)

## 자기 점검

### 처리 규칙·시나리오·테스트 연결표

| 기능 | 규칙 | 시나리오 | 테스트 |
|---|---|---|---|
| VOTE-02 | 1~2 (명단 기본 후보, 본인 제외) | VOTE-02-1 | `VOTE_02_1_본인_제외` |
| VOTE-02 | 3 (휴식 중 표시, 선택 불가) | VOTE-02-3 | `VOTE_02_3_휴식_중_표시` |
| VOTE-02 | 2 (본인 선택 서버 차단) | VOTE-02-2 | `VOTE_03_본인_선택_거부` (VOTE-03 제출 쪽 테스트로 겸함) |
| VOTE-02 | 3 (휴식 중 투표 서버 차단) | VOTE-02-4 | `VOTE_03_없는_회차_거부`류와 같은 `INVALID_VOTE_TARGET` 경로로 커버 — 별도 시나리오 테스트는 없음 |
| VOTE-02 | 4 (기존 멤버 미인증은 후보) | VOTE-02-5 | `VOTE_02_5_기존_멤버_미인증은_후보` |
| VOTE-02 | 7 (오늘 들어온 미인증 제외) | VOTE-02-6 | `VOTE_02_6_오늘_들어온_미인증_멤버_제외` |
| VOTE-02 | 5 (시작 후 참여자 제외) | VOTE-02-7 | `VOTE_02_7_시작_후_참여자_제외` |
| VOTE-02 | 6 (후보 1명뿐일 때) | VOTE-02-8 | 없음 (아래 "테스트가 없는 시나리오") |
| VOTE-03 | 1 (명단·투표권자만 제출) | VOTE-03-1, VOTE-03-9 | `VOTE_03_1_정상_제출`, `VOTE_03_9_미인증_멤버는_투표_불가` |
| VOTE-03 | 3 (1인 1표, 중복 불가) | VOTE-03-4 | `VOTE_03_4_중복_제출` |
| VOTE-03 | 4 (본인 선택 불가) | 없음(명세에 번호 없음) | `VOTE_03_본인_선택_거부` |
| VOTE-03 | 6 (모두 보살 선택도 같은 보상) | VOTE-03-2 | `VOTE_03_2_모두_보살_선택도_같은_보상` (보상 자체는 제외 범위) |
| VOTE-03 | 7 (마지막 미투표자 제출 시 즉시 마감) | VOTE-03-7 | `VOTE_03_7_마지막_두_표_동시_제출` |
| VOTE-03 | 8 (투표 중 득표 비공개) | VOTE-03-6 | `VOTE_03_6_투표_중_득표_비공개` |
| VOTE-03 | 9 (재입장자 투표 불가) | VOTE-03-8 | `VOTE_03_8_다시_들어온_멤버는_투표_불가` |
| VOTE-03 | 명단 밖 거부 | VOTE-03-3 | `VOTE_03_3_명단_밖_사용자_거부` |
| VOTE-03 | 마감 후 제출 거부 | VOTE-03-5 | `VOTE_03_5_마감_후_제출` |
| VOTE-05 | 상태 8개 분기 | VOTE-05-1~8 | `VOTE_05_1`~`VOTE_05_8` |
| VOTE-05 | 상태별 화면 데이터 | VOTE-05-9, VOTE-05-10 | `VOTE_05_9_투표_완료_화면_데이터`, `VOTE_05_10_투표권_없음_화면_데이터` |

### 테스트가 없는 시나리오
- VOTE-02-8 (후보가 한 명뿐일 때도 `오늘은 모두 보살` 선택 가능) — "오늘은 모두 보살"은 응답 데이터가 아니라 클라이언트가 항상 보여주는 고정 선택지로 구현해서(api.md의 candidates 응답에 별도 필드 없음), 서버 쪽에서 검증할 대상이 없다.
- VOTE-02-4 (휴식 중인 후보에게 투표하면 서버가 거부) — 별도 시나리오 테스트는 안 만들었지만 `VoteSubmitService.resolveTarget`이 `targetParticipant.isResting()`이면 `VOTE_006`을 던지는 경로로 구현은 돼 있다.

### 시나리오가 없는 처리 규칙
- VOTE-03 5번 (투표 보상 적립) — 이번 PR 범위에서 제외 (RICE-01 없음).

### 명세에 없는 규칙을 만든 곳
1. **VOTE-05-10에서 본인을 후보 목록에 포함**: VOTE-02 규칙 2번(본인 제외)은 "투표자"를 전제로 해서, 투표권이 없는 내 화면(`NO_VOTING_RIGHT`)에서는 본인도 빼지 않기로 했다 (VOTE-05-10 예시 문구 그대로 구현).
2. **휴식 중 멤버를 후보 목록에 포함하는 방법**: `RoundParticipant.isCandidate`는 이미 머지된 VOTE-01·CERT-05 스냅샷에서 "선택 가능 여부"로 쓰여서 휴식 중이면 항상 false인데, VOTE-02 3번은 "목록에는 넣고 선택만 막으라"고 해서 서로 안 맞았다. 엔티티는 바꾸지 않고, `VoteCandidateService`에서 기존 `isResting` 필드와 합쳐 `isCandidate() || isResting()`으로 목록 포함 여부를 판단했다 (사람 확인 완료).
3. **제출 검증 순서**: 명단 소속 → 재입장 → 투표권 → 중복 제출 → 본인 선택 → 후보 유효성. 명세에 순서가 없어서 에러 코드 목록 순서를 따랐다.
4. **락 전 조회 순서**: `roundId`로 `roomId`만 먼저 읽고 방 락을 잡은 뒤에야 회차를 읽는다 (CERT-01 코드리뷰로 걸렸던 실수를 피하려는 구현 결정).

### 코드리뷰 반영
- 아직 PR을 올리지 않아 해당 없음.

### 테스트 결과
`./gradlew test` 전체 232개 통과 (기존 206개 + 이번 PR 26개).
