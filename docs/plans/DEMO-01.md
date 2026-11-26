# DEMO-01 전시 시연 모드

## 범위
- 구현 기능: DEMO-01, DEMO-02, DEMO-03
- 참고 기능: VOTE-01, VOTE-03, VOTE-04, RESULT-01, RESULT-03, NABAL-01, NABAL-02, RICE-01, ROOM-09

## 명세 점검
- 상태 전이: 리셋이 회차를 바로 만든다. 방 1은 `인증 중`, 방 2는 지난 회차 `발표(PICKED)` + 지금 회차 `인증 중`. 투표 시작·마감·발표는 기존 서비스가 그대로 처리하고, 시연 처리는 투표 시작(DEMO-02)과 톡톡 보내기(DEMO-03) 뒤에 붙기만 한다.
- 동시성: 리셋은 시연 계정 사용자 행 락으로 같은 시연 번호를 하나씩 처리한다. 방 삭제는 방 행 락. 자동 투표는 `startIfAllCertified`가 잡은 방 행 락 안, 나발 빠짐은 `TapService.send`의 방 행 락 안에서 실행한다. 가짜 팀원 생성은 `toss_anon_key` UNIQUE가 중복을 막는다.
- 권한: 리셋은 JWT 없이 시연 키(헤더)로만 보호. 테스트 토큰(`AuthTestTokenService`)과 같은 방식(꺼지면 `COMMON_001`, 키 비교는 `MessageDigest.isEqual`).
- 외부 연동 실패: 시연 처리 자체는 외부 호출 없음. 방 1 발표 때 기존 결과 알림이 나가지만 가짜 팀원은 토스 동의가 없어 발송 대상이 아님.
- 제외 범위: 리액션 단계, 방문객 기록, 프론트 홈 탭 나발 화면, 시연 계정 연결 화면.

## 명세 변경
- `docs/spec/demo.md` 새로 추가 (DEMO-01~03). 기획 컨펌(2026-11-26) 반영. 기존 기능 명세는 바꾸지 않음
- `docs/spec/api.md`: `POST /api/demo/reset` 추가

## 설계

### API 계약
- `POST /api/demo/reset?slot={N}`
- 요청: 헤더 `X-Demo-Key`, 쿼리 `slot`
- 응답: `201` `DemoResetResponse(Long mainRoomId, Long moktakRoomId)`
- 에러 코드: `COMMON_001`(설정 꺼짐), `COMMON_008`(키 틀림), `DEMO_001`(없는 시연 번호), `DEMO_002`(일반 방 참여 중)

### 엔티티
- 새 테이블·컬럼 없음 (prod `ddl-auto: validate`라 수동 DDL을 피함)
- `RiceReason.DEMO_RESET` 추가 (varchar 컬럼이라 DDL 불필요). 리셋 때 시연 계정 장부를 지우고 +200 한 줄
- 나간 유형은 새 값 없이 `LeaveType.ROOM_CLOSED`를 다시 씀 (방을 통째로 지우는 [방 닫기]와 같은 처리. 새 값을 넣으면 나간 유형별 분기를 다시 확인해야 해서 뺌)
- 가짜 팀원: `users` 행, `toss_anon_key = "demo-{slot}-{A|B|C}"`. 토스 키가 아니라 로그인 불가

### 주요 결정
- 설정 (`application.yml`, env)
  - `demo.enabled` (`DEMO_ENABLED`, 기본 false), `demo.key` (`DEMO_KEY`, 짧으면 기동 실패)
  - `demo.slots` (`DEMO_SLOTS`): `번호:userId:닉네임`을 쉼표로 이은 값. 예) `1:12:보살이,2:34:참는중`. 닉네임 1~8자가 아니거나 번호가 겹치면 기동 실패
- 시연 방 판별: 현재 멤버 중 `toss_anon_key`가 `demo-`로 시작하는 사용자가 있는 방 (`RoomMemberRepository.existsBy...TossAnonKeyStartingWith`)
- 방 1 / 방 2 구분: 방 2는 리셋 때 지난 회차가 이미 발표돼 투표가 다시 시작되지 않으므로(03:00 배치는 인증 0명 → 투표 없음) 별도 구분 없이 "시연 방" 하나로 처리
- 패키지 `domain/demo` (controller, dto, service). `VoteStartService.start` 끝과 `TapService.send` 끝에서 `DemoPlayService`를 부름 (꺼져 있으면 바로 반환)
- DEMO-02 표: A→C, B→C, C→`ALL_BOSAL`. 방문객이 누구를 골라도 C 2표로 단독 최다라 리액션(배드) 동점 판정에 기대지 않음. `Vote`만 저장하고 `RiceService`는 부르지 않음
- DEMO-03 나발: `NabalPicker`로 번호를 고르고 `NabalLoss(user, 방 1, 회차 날짜)` 저장. UNIQUE(user, room, date)라 두 번 빠지지 않음
- 리셋 순서 (한 트랜잭션)
  1. 시연 계정 사용자 행 락 → 현재 방 조회 → 일반 방이 있으면 `DEMO_002`
  2. 시연 방마다 방 행 락, 현재 멤버 전원 `leave(DEMO_RESET)`, `room.delete` (ROOM-09 `close`와 같은 방식, 차감 없음)
  3. 시연 계정 `rice_histories` 삭제 후 `DEMO_RESET +200`, `nabal_losses`·`user_items`·`defense_tickets` 삭제
  4. 가짜 팀원 조회·없으면 생성
  5. 방 1: `Room.create("자율 절약 방", ETC, 4, 300)`, 멤버 4명(시연 계정 방장), 오늘 회차 `CERTIFYING`, A·B·C 인증(사진 없음, 보상 없음)
  6. 방 2: 같은 방 생성. 지난 회차 날짜 = 오늘 회차 − 1일. 인증 4명, 명단 스냅샷, 표 A·B·C → 시연 계정, `announce(PICKED, 시연 계정)`, `voteClosedAt = now − 5시간 − 1분`, 톡톡 A 3 · B 3 · C 4 (합계 10), 멤버 `seenResultRoundDate` 채움. `pickedSeenAt`은 방어 마감 기준이라 톡톡 마감이 지난 이 회차에는 영향이 없어 비워 둠. 오늘 회차는 행 없음(= 인증 중)
- 방 2 멤버 joinedAt은 지난 회차 시작 1분 전으로 둬서 후보·명단 규칙(그날 들어옴)에 걸리지 않게 함
- 리셋은 전시 시간(낮) 실행을 전제. 03:00 근처 리셋은 회차 날짜가 바뀔 수 있어 다루지 않음
- 시연 계정은 리셋 전 팀원이 시연 닉네임으로 아무 방에 들어와 `room_members`에서 userId를 확인해 설정에 넣음 (운영 절차, 코드 없음)

## 규칙 점검
- `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음
  - 리셋 응답 `ResponseEntity<Response<DemoResetResponse>>`, 현재 시각은 `Clock`, 시나리오 테스트는 고정 `Clock`
  - 새 에러 코드 `DEMO_001`, `DEMO_002`는 스냅샷에 줄 추가만
  - 리셋은 쿼리·헤더만 받아 `@RequestBody` 없음

## 할 일
- [x] `docs/spec/api.md`에 `POST /api/demo/reset` 추가, `erd.md`에 `DEMO_RESET` 사유 추가
- [x] 설정(`DemoSettings`, `application.yml`의 `DEMO_ENABLED`·`DEMO_KEY`·`DEMO_SLOTS`), `SecurityConfig` 공개 경로
- [x] `BaseErrorCode` `DEMO_001`·`DEMO_002`, 스냅샷 추가. `RiceReason.DEMO_RESET`
- [x] `DemoPlayService` (시연 방 판별, 자동 투표, 나발 빠짐) + `VoteStartService`·`TapService` 연결
- [x] `DemoResetService`, `DemoController`
- [x] 테스트: DEMO-01-1 ~ DEMO-01-9
- [x] 테스트: DEMO-02-1 ~ DEMO-02-4
- [x] 테스트: DEMO-03-1 ~ DEMO-03-3
- [ ] 배포 전 (코드 밖): prod env `DEMO_ENABLED`, `DEMO_KEY`, 시연 번호별 userId·닉네임 (전시 끝나면 `DEMO_ENABLED=false`)

## 코드리뷰 반영 (2026-11-26)
- `DEMO_SLOTS`에 같은 userId가 두 번호에 있으면 기동 실패
- `DEMO_KEY` 앞뒤 공백을 지운 값으로 길이 확인과 비교
- 방 2 멤버 joinedAt을 회차 시작 1분 전으로 (정각이면 그날 들어온 멤버로 보임)
- 시연 계정에게 투표권이 있을 때만 가짜 팀원 표 입력 (DEMO-02 6번 추가)
- 투표 시작 때 시연 방 판별을 가짜 팀원 조회 한 번으로
- 명세에 없던 값(보살 색, 톡톡 3·3·4, 방 2 시연 계정 인증·표)은 승인받아 명세에 [구현 결정]으로 기록 (11/26)
- 리셋 때 이마 방어권도 삭제 (DEMO-01 7번 수정, 11/26)
- 2차 리뷰 (11/26)
  - 리셋의 방 삭제를 `RoomService.closeLocked`로 [방 닫기]와 공유해 방문객 사진·한 줄·쓴 금액을 바로 지움 (DEMO-01 5번 수정)
  - 톡톡 뒤 나발 빠짐에서 사용자 행 락을 잡고 빠진 나발을 읽음 (나발 심기·판정과 같은 락)
  - `DEMO_KEY` 길이를 앱·cd.yml 모두 앞뒤 공백을 지운 바이트 수로 셈
  - 방 2 지난 회차 마감 시각이 그 회차 투표 마감(15:00)을 넘지 않게 함
  - 투표 시작 때 시연 계정 확인을 먼저 해서 일반 방은 추가 조회 없이 끝냄
  - `DEMO_SLOTS` 숫자 형식 오류도 형식 안내 메시지로, 안 쓰는 `getSlots` 제거
  - 넘어감: 이벤트 방식 분리 (전시 후 호출 두 줄만 지우면 됨)

## 열린 질문
- 시연 번호 1~8(시연폰 8대)별 시연 계정과 닉네임. 폰마다 다른 토스 계정이 필요. 기획 정책이 아니라 운영 입력값이라 `open-questions.md`에는 올리지 않음
- 시연 방 2 이름도 "자율 절약 방"으로 둠 (기획 답변의 방 이름을 두 방 공통으로 해석)

## 자기 점검

테스트: `DemoTest` 16개, `DemoSettingsTest` 2개 통과, 전체 `./gradlew test` 통과.

### 처리 규칙 · 시나리오 · 테스트 연결표

| 처리 규칙 | 시나리오 | 테스트 |
|---|---|---|
| DEMO-01 1 설정 꺼짐 | DEMO-01-1 | `DEMO_01_1_시연_설정이_꺼져_있으면_없는_API로_거부한다` |
| DEMO-01 2 시연 키 | DEMO-01-2 | `DEMO_01_2_시연_키가_틀리면_거부한다` |
| DEMO-01 3 없는 번호·계정 | DEMO-01-3 | `DEMO_01_3_설정에_없는_시연_번호는_거부한다` |
| DEMO-01 4 일반 방 참여 중 | DEMO-01-7 | `DEMO_01_7_시연_계정이_일반_방에_있으면_거부하고_아무것도_바꾸지_않는다` |
| DEMO-01 5 시연 방 삭제 | DEMO-01-6 | `DEMO_01_6_다시_리셋하면_이전_방을_지우고_시연_계정을_처음_상태로_되돌린다` |
| DEMO-01 6 공양미 200석 | DEMO-01-4, DEMO-01-6 | `DEMO_01_4_…`, `DEMO_01_6_…` |
| DEMO-01 7 나발·아이템·방어권 초기화 | DEMO-01-6 | `DEMO_01_6_…` |
| DEMO-01 8 가짜 팀원 재사용 | DEMO-01-4, DEMO-01-6 | `DEMO_01_4_…`, `DEMO_01_6_…` |
| DEMO-01 9 방 이름·목표·멤버 | DEMO-01-4 | `DEMO_01_4_첫_리셋이면_가짜_팀원과_시연_방_2개를_만든다` |
| DEMO-01 10 시연 방 1 | DEMO-01-4 | `DEMO_01_4_…` |
| DEMO-01 11 시연 방 2 | DEMO-01-5 | `DEMO_01_5_시연_방_2는_받은_톡톡_10회가_전달돼_목탁을_할_수_있다` |
| DEMO-01 12 다른 번호 그대로 | DEMO-01-8 | `DEMO_01_8_다른_시연_번호의_방과_공양미는_그대로다` |
| DEMO-01 13 동시 리셋 | DEMO-01-9 | `DEMO_01_9_같은_시연_번호를_동시에_리셋해도_방은_2개다` |
| DEMO-02 1 시연 방만 | DEMO-02-3 | `DEMO_02_3_일반_방은_투표가_시작돼도_자동_표가_없다` |
| DEMO-02 2 가짜 팀원 표 | DEMO-02-1 | `DEMO_02_1_방문객이_인증하면_투표가_시작되고_가짜_팀원_표가_들어간다` |
| DEMO-02 3 C 지목 | DEMO-02-2 | `DEMO_02_2_방문객이_누구에게_투표해도_C가_지목된다` |
| DEMO-02 4 전원 투표 마감 | DEMO-02-2 | `DEMO_02_2_…` |
| DEMO-02 5 투표 보상 없음 | DEMO-02-1 | `DEMO_02_1_…` |
| DEMO-02 6 시연 계정 투표권 있을 때만 | DEMO-02-4 | `DEMO_02_4_방문객이_인증하지_않은_채_03시가_되면_자동_표가_없다` |
| DEMO-03 1 시연 방·시연 계정만 | DEMO-03-3 | `DEMO_03_3_일반_방에서_톡톡을_보내면_나발은_그대로다` |
| DEMO-03 2 건너뛰기 포함 1개 | DEMO-03-1, DEMO-03-2 | `DEMO_03_1_…`, `DEMO_03_2_건너뛰기여도_나발이_빠진다` |
| DEMO-03 3 안 본 나발로 노출 | DEMO-03-1 | `DEMO_03_1_시연_계정이_톡톡을_보내면_나발이_1개_빠진다` |

### 테스트가 없는 시나리오
- 없음

### 시나리오가 없는 처리 규칙
- 없음. 다만 "시연 설정이 꺼져 있으면 자동 처리 안 함"(DEMO-02 1번, DEMO-03 1번의 설정 부분)은 리셋 API 꺼짐(DEMO-01-1)만 시나리오가 있고, 자동 처리 쪽은 `isEnabled()` 확인 코드로만 보장

### 명세에 없는 규칙을 만든 곳
- 시연 방 2의 시연 계정 지난 회차 인증 내용(참았어요, "오늘도 잘 참았어요")과 표(시연 계정 → C). 기획 답변에 없어 화면에 거의 드러나지 않는 값으로 정함
- 시연 계정 보살 색 `YELLOW`, 가짜 팀원 색 A `PINK` · B `PURPLE` · C `MINT`
- 받은 톡톡 10회 배분 A 3 · B 3 · C 4
