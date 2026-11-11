# ROOM-01 방 만들기 · 닉네임 수정

## 범위
- 구현 기능: ROOM-01, ROOM-04
- 참고 기능: CERT-01 (첫 회차 인증), INVITE-03 (입장 시 닉네임·색상 규칙), ROOM-07 (나간 멤버), CLEAN-03 (빈 방)

방과 멤버 테이블을 처음 만드는 작업입니다. 닉네임 규칙(형식, 6개월 중복, 대소문자 무시)과 방 행 락은 두 기능이 같이 쓰고, 초대·입장(#55)이 이 위에 붙습니다.

## 명세 점검
- 상태 전이: 멤버는 `현재 멤버(left_at NULL) → 나감(left_at 기록)`. 이번 범위에서는 현재 멤버를 만들기만 하고, 나간 멤버는 닉네임 중복 판단에만 읽는다.
- 동시성
  - ROOM-01: 같은 사용자의 동시 요청으로 참여 방이 2개를 넘으면 안 된다 (규칙 10). `users` 행 락을 잡고 참여 방 수를 센다.
  - ROOM-04: 같은 방에서 같은 닉네임으로 동시에 바꾸면 1명만 성공해야 한다 (규칙 7). `rooms` 행 락을 잡고 중복을 확인한다.
- 권한: 방 만들기는 로그인한 사용자 누구나. 닉네임 수정은 그 방의 현재 멤버만 (`ROOM_002`). 삭제된 방은 `ROOM_005`.
- 외부 연동 실패: 없음
- 제외 범위: 초대 토큰·입장 (#55), 오늘 탭, 나가기·내보내기, 빈 방 정리. ROOM-01-6(만든 날 첫 회차 인증)은 회차 행을 첫 인증 때 만드는 구조라(ROOM-02 4번) CERT-01 구현 때 테스트한다.

## 명세 변경
- `docs/spec/room-membership.md` ROOM-01 11번, `open-questions.md` Q-37: **방을 닉네임 제출 때 방과 멤버를 한 번에 만든다**로 결정 처리
  - 이유: 명세 입력(방 이름·목표·함께할 인원·닉네임), `api.md`, 프론트에 공개한 OpenAPI가 모두 이미 한 번에 만드는 방식이고, 최신 디자인도 방 정보(p23) → 닉네임(p21) → 보살 받기(p22) 순서로 마지막에 한 번 제출한다.
  - 결과: 빈 방이 생기지 않으므로 `erd.md`의 `rooms.owner_member_id` "생성 직후 NULL" 설명과 CLEAN-03(빈 방 정리)을 지운다.
  - 프론트 확인 완료 (2026-11-11). Q-37을 결정으로 바꾼다.
- 상태: 구현 후 ROOM-01, ROOM-04를 `구현됨`으로 바꾼다.

## 설계

### API 계약
- `POST /api/rooms`
- 요청: `{ "name": "배달 끊기 방", "goal": "DELIVERY", "memberLimit": 4, "nickname": "민지" }`
- 응답: `201`, `Response<RoomCreateResponse>`, `{ "roomId": 1, "memberId": 11, "nickname": "민지", "color": "PINK" }`
- 에러 코드: `ROOM_001` (409), `COMMON_002` (400), `COMMON_008` (401)

- `PATCH /api/rooms/{roomId}/members/me/nickname`
- 요청: `{ "nickname": "지수2" }`
- 응답: `200`, `Response<Void>`
- 에러 코드: `ROOM_002` (403), `ROOM_004` (409, `field: nickname`), `ROOM_005` (404), `COMMON_002` (400), `COMMON_008` (401)

### 엔티티
- `Room` (`rooms`): `id`, `name`, `goal`(`RoomGoal`), `memberLimit`, `ownerMemberId`, `potCompletedCount`, `deletedAt`
- `RoomMember` (`room_members`): `id`, `room`, `user`(NULL 허용), `nickname`, `color`(`BosalColor`), `joinedAt`, `leftAt`, `leaveType`(`LeaveType`), `inviteToken`, `seenResultRoundDate`, `seenPotCount`, `pickedStreak`, `restingRoundDate`
- `erd.md`의 컬럼을 이번에 모두 만든다. 마이그레이션 도구 없이 운영 DB에 직접 DDL을 실행해야 해서, 기능마다 컬럼을 나눠 추가하면 운영 반영이 그만큼 늘어난다.
- enum(`RoomGoal`, `BosalColor`, `LeaveType`)은 `@Enumerated(EnumType.STRING)`으로 저장한다.
- 방장은 `ownerMemberId`(Long)로 둔다. `rooms ↔ room_members`가 서로 FK를 가리키는 순환을 엔티티 연관관계로 만들지 않기 위해서다. 방을 저장하고 멤버를 저장한 뒤 같은 트랜잭션에서 채운다.

### 주요 결정
- **참여 방 2개 제한:** `users` 행을 `PESSIMISTIC_WRITE`로 잡고, 그 사용자의 현재 멤버 행 중 삭제되지 않은 방의 수를 센다. ERD의 락 방식을 그대로 따른다.
- **닉네임 중복:** `rooms` 행을 `PESSIMISTIC_WRITE`로 잡고, 같은 방에서 현재 멤버이거나 나간 지 6개월이 안 된 멤버(`left_at + 6개월 >= 지금`) 중 내 행을 뺀 닉네임과 `LOWER`로 비교한다. 같으면 `ROOM_004`(`field: nickname`).
  - ERD의 생성 컬럼 UNIQUE(`active_nickname`, `active_user_id`, `active_color`)는 넣지 않는다. 운영 DDL에만 넣으면 H2 테스트와 운영 스키마가 달라지고, 6개월 조건은 어차피 락으로만 지킬 수 있다. `erd.md`의 해당 설명도 락 방식으로 고친다.
- **같은 닉네임으로 수정:** 대소문자까지 같으면 저장하지 않고 성공 (ROOM-04 규칙 6). 대소문자만 다르면 내 행은 중복 검사에서 빠지므로 바뀐다 (ROOM-04-5).
- **방 이름 검증:** 앞뒤 공백을 지운 뒤 1~8자, 한글(완성형)·영문·숫자·띄어쓰기. 공백을 지운 뒤 검사해야 해서 서비스에서 확인하고 `COMMON_002`(`field: name`)로 던진다. 닉네임 형식과 함께할 인원(2~4)은 DTO 검증 어노테이션으로 처리한다.
- **색상:** 새 방은 멤버가 없으니 4종 중 랜덤. 랜덤은 주입 가능한 `RandomGenerator`(또는 색 선택기)로 분리해 테스트에서 고정한다.
- **시각:** `joinedAt`은 주입받은 `Clock`으로 구한다. 6개월 비교도 같은 `Clock` 기준.
- **에러 코드:** `BaseErrorCode`에 `// Room` 구역을 만들고 `ROOM_001`, `ROOM_002`, `ROOM_004`, `ROOM_005`를 추가한다. 스냅샷에도 추가한다.
- **운영 DB:** `rooms`, `room_members` 생성 SQL을 PR에 첨부하고, 머지 전에 서버에서 실행한다 (`ddl-auto: validate`라 테이블이 없으면 배포가 뜨지 않음).
- **OpenAPI 초안:** `servers` 맨 앞에 `url: /`(현재 서버)를 추가한다 (#53 리뷰 반영). 구현된 두 API는 `/v3/api-docs`에도 나타난다.

## 규칙 점검
- `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음

## 할 일
- [ ] 명세 변경: Q-37 결정, ROOM-01 11번, `erd.md` `owner_member_id`·생성 컬럼 UNIQUE 설명, CLEAN-03 삭제
- [ ] `BaseErrorCode` `// Room` 구역과 `error-codes.snapshot`
- [ ] 엔티티 `Room`, `RoomMember`와 enum `RoomGoal`, `BosalColor`, `LeaveType`, 리포지토리(락 조회 포함)
- [ ] ROOM-01 서비스·컨트롤러·DTO (`POST /api/rooms`)
- [ ] ROOM-04 서비스·컨트롤러·DTO (`PATCH /api/rooms/{roomId}/members/me/nickname`)
- [ ] 테스트: ROOM-01-1 ~ ROOM-01-5, ROOM-01-7, ROOM-01-8
- [ ] 테스트: ROOM-04-1 ~ ROOM-04-7
- [ ] 운영 DB 생성 SQL 작성 (PR 첨부)
- [ ] `openapi.yaml` `servers`에 현재 서버 추가

## 열린 질문
- 없음
  - Q-37 방 생성 시점: 프론트 확인으로 "닉네임 제출 때 한 번에"로 결정
  - 생성 컬럼 UNIQUE: 넣지 않고 락으로 지키기로 결정

## 자기 점검
`/spec-build`가 구현 후 작성합니다.
