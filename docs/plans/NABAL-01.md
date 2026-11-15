# NABAL-01 나발

## 범위
- 구현 기능: NABAL-01 나발 빠짐, NABAL-02 나발 상태와 확인, NABAL-03 나발 심기
- 참고 기능: USER-01(내 정보에 나발 상태), CERT-01(회차 날짜), CERT-05(그날 들어온 멤버), RICE-01·02(장부, 표시 잔액), SHIELD-01(사용자 행 락 안에서 잔액 확인 후 사용), USER-05(삭제한 사용자)
- 새 명세 `docs/spec/nabal.md`. 나발 정책서와 공양미 정책서(나발 심기 15석, 나발 38개)를 옮겼다

## 명세 점검
- 상태 전이
  - 나발 한 개: `있음` → `빠짐`(나발 기록 생성) → `있음`(심기, 기록에 심은 시각). 같은 번호가 다시 빠지면 새 기록
  - 보살/중생: 빠진 나발이 1개 이상이면 중생, 0개면 보살. 저장하지 않고 계산한다
  - 확인: 기록마다 `본 시각`. 보낸 기록 ID 이하를 한 번에 본 것으로 처리
- 동시성
  - 판정 배치와 나발 심기가 같은 사용자의 빠진 나발 목록을 읽고 바꾼다. 둘 다 사용자 행 락 안에서 한다. 심기는 잔액을 읽고 쓰므로 원래 사용자 행 락이 필요하다 (SHIELD-01과 같음)
  - 같은 사용자·방·회차 날짜는 `UNIQUE (user_id, room_id, round_date)`로 한 번만 기록. 배치가 두 번 돌거나 겹쳐도 안전
  - 같은 나발 동시 심기(NABAL-03-6): 사용자 행 락 안에서 "빠진 나발인지" 확인 → 두 번째 요청은 `NABAL_001`
  - 03:00 뒤에는 그 회차 날짜로 인증할 수 없어(CERT-01) 판정과 인증은 겹치지 않는다
- 권한: 로그인한 본인만. 방과 무관한 사용자 단위 API
- 외부 연동 실패: 없음
- 금액
  - 심기: 잔액이 개수 × 15석 이상이면 나발마다 -15석. 장부 `PURCHASE`, 원인 `NABAL`(나발 기록 ID). 음수 잔액이면 `STORE_001`
  - 나발이 빠져도 공양미 변화 없음
- 제외 범위: 빠지는 연출·회귀 스토리·필수 문구(클라이언트), 곳간 화면 표시(STORE-01), 오늘 탭의 다른 멤버 나발(ROOM-02 구현 때), 투표·결과 화면의 나발(팀원 기능)

## 명세 변경
- 새 파일 `docs/spec/nabal.md` (NABAL-01~03)
- `open-questions.md`: Q-100(방마다 1개, A), Q-101(다른 멤버에게도 보임) 결정, Q-103(빠질 번호 서버 랜덤, 심을 번호 사용자 지정) 추가
- `user.md` USER-01 출력: 나발 상태 추가
- `api.md`: 내 정보 응답의 `nabal`, 확인 저장·심기 API, 에러 `NABAL_001`
- `erd.md`: `nabal_losses` 테이블, 장부 원인 `NABAL`

## 설계

### API 계약
- `GET /api/users/me` (USER-01) 응답에 추가:
  `"nabal": { "fallenNumbers": [3, 7], "unseenFallenNumbers": [7], "lastNabalLossId": 12, "isFirstLoss": false }`
  - 번호는 오름차순. 기록이 없으면 `lastNabalLossId`는 `null`
- `PUT /api/users/me/seen-nabal-loss` (NABAL-02): 요청 `{ "nabalLossId": 12 }`. 응답 200 데이터 없음. 값 없으면 `COMMON_002`
- `POST /api/users/me/nabal-replants` (NABAL-03): 요청 `{ "nabalNumbers": [3, 7] }`. 응답 201 `{ "riceBalance": 10, "fallenNumbers": [] }`
  - 확인 순서: 입력(빈 목록·범위·중복) `COMMON_002` → 빠지지 않은 나발 `NABAL_001`(409) → 잔액 부족 `STORE_001`(409)
- 새 에러 code: `NABAL_001` (409) 빠지지 않은 나발

### 엔티티
- `NabalLoss` (`nabal_losses`): user, room, roundDate, nabalNumber(0~37), seenAt(NULL), replantedAt(NULL)
  - `UNIQUE (user_id, room_id, round_date)`, 인덱스 `(user_id, replanted_at)`
  - 빠진 나발 = `replanted_at IS NULL`인 기록
- `RiceRefType.NABAL` 추가 (원인 ID = 나발 기록 ID)
- `NabalPicker`: 남은 번호 중 랜덤 선택. `BosalColorPicker`처럼 테스트에서 바꿔 끼울 수 있게 분리

### 주요 결정
1. **판정 배치**: 매일 03:00에 바로 전 회차 날짜(`Round.dateOf(now)` 하루 전)를 판정한다. 대상은 현재 방이 있는 삭제되지 않은 사용자. 사용자마다 트랜잭션을 따로 열어 한 명이 실패해도 계속한다 (VoteStartScheduler와 같은 방식)
2. **미인증 판단**: 사용자의 현재 방마다, 그 회차 날짜 이전에 들어온 방(`Round.dateOf(joinedAt) < 회차 날짜`)에서 그 회차의 삭제하지 않은 내 인증이 없으면 그 방 하나를 센다. 회차 행이 없는 방(아무도 인증 안 함)도 센다
3. **빠질 번호**: 빠진 번호를 뺀 나머지에서 방마다 하나씩 랜덤. 남은 나발이 미인증 방 수보다 적으면 남은 만큼만 뺀다
4. **빠진 날 몰아서 판정 안 함**: 서버가 03:00에 내려가 있었으면 그날 판정은 건너뛴다. 출시 전 서버 1대라 드문 경우이고, 몰아서 빼면 한 번에 여러 개가 빠져 체감이 나쁘다
5. **확인 저장은 기록 ID 기준**: 화면을 그린 뒤 새로 빠진 나발이 본 것으로 처리되지 않게 `id <= nabalLossId`만 바꾼다. 조건부 update 한 문장 (seen-pot과 같은 방식)
6. **처음 빠짐**: 본 기록이 하나도 없으면 처음. 따로 컬럼을 두지 않는다
7. **나발 상태는 내 정보(USER-01)에 담는다**: 앱을 열 때 이미 부르는 API라 요청이 늘지 않는다
8. **내 데이터 삭제(USER-05)**: PR #104가 먼저 머지되면 이 PR에서 나발 기록도 즉시 삭제 목록에 넣는다. 늦게 머지되면 그 PR에 넣는다

## 규칙 점검
- 명세에 없던 기능을 새로 만든다. 정책서와 기획 답변(Q-100, Q-101, Q-103)을 옮겼고, 정책서에 없는 부분은 `[구현 결정]`으로 표시했다 (사람 확인 필요)
- 현재 시각은 `Clock`, enum은 `EnumType.STRING`, DTO는 record, 조회는 `readOnly`
- 그 외 `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음

## 할 일
- [x] 명세·`api.md`·`erd.md`·OpenAPI 초안 반영
- [x] `NabalLoss` 엔티티·레포지토리, `RiceRefType.NABAL`, 에러 code `NABAL_001`(스냅샷 추가)
- [x] 판정 서비스·03:00 스케줄러 (NABAL-01)
- [x] 내 정보에 나발 상태, 확인 저장 (NABAL-02)
- [x] 나발 심기 (NABAL-03)
- [x] 테스트: NABAL-01-1~9, NABAL-02-1~5, NABAL-03-1~6

## 열린 질문
- 없음 (Q-100, Q-101, Q-103 결정)

## 자기 점검
- 시나리오 NABAL-01-1~9, NABAL-02-1~5, NABAL-03-1~6 모두 테스트. 시나리오 밖: 빠진 나발이 없을 때 상태, 빈 목록·범위 밖·중복 번호 `COMMON_002`
- 계획에서 바뀐 것: 여러 개 심기를 한 번에 확인·기록하려고 `RiceService.spendEach` 추가(`spend`는 이걸 부름)
- 코드리뷰 반영: 판정이 인증 등록과 같은 방 행 락을 잡고 READ COMMITTED로 읽어 02:59에 시작한 인증을 놓치지 않음. 처음 빠짐은 본 기록·심은 기록이 모두 없을 때. 다른 멤버 나발 표시(NABAL-02 7번)는 ROOM-02 12번으로 옮김
- 남은 것: 내 데이터 삭제(PR #104)가 머지되면 나발 기록 즉시 삭제를 붙임 (주요 결정 8), 오늘 탭의 다른 멤버 나발은 ROOM-02 구현 때
- 전체 테스트 통과
