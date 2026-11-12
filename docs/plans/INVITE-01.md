# INVITE-01 초대 토큰 · 미리보기 · 입장

## 범위
- 구현 기능: INVITE-01, INVITE-02, INVITE-03
- 참고 기능: ROOM-01·ROOM-04 (닉네임 규칙, 방·사용자 락), ROOM-07·ROOM-08 (나간 멤버, 내보내진 멤버), USER-05 (`DATA_DELETED`), AUTH-01 (6개월 기준), RESULT-02·POT-01 (확인 상태), VOTE-01 (투표 명단)

#54의 `Room`, `RoomMember` 위에 붙입니다. 새 테이블은 없고 `room_members.invite_token`과 나간 멤버 행을 씁니다.

## 명세 점검
- 상태 전이
  - 멤버 행: `현재 멤버 → 나감(LEFT / KICKED / DATA_DELETED)`. 이번 범위에서 `나감(LEFT·DATA_DELETED, 6개월 안) → 현재 멤버`로 되돌리는 전이를 처음 만든다 (INVITE-03 6번). `KICKED`는 되돌리지 않는다.
  - 초대 토큰: `NULL → 값`. 한 번 생기면 바뀌지 않는다. 6개월이 지나 새 행으로 들어오면 새 행의 토큰은 다시 `NULL`부터 시작한다.
  - 나가기·내보내기 API(ROOM-07, ROOM-08)는 아직 없다. 테스트는 `RoomMember.leave()`로 나간 상태를 만든다.
- 동시성
  - INVITE-01-4: 같은 멤버의 토큰 요청이 동시에 와도 토큰은 하나. `rooms` 행 락 안에서 토큰을 확인하고 만든다.
  - INVITE-03-15: 마지막 자리 동시 입장. `rooms` 행 락 안에서 인원을 센다.
  - INVITE-03-16: 같은 사용자가 두 방에 동시 입장. `users` 행 락 안에서 참여 방 수를 센다.
  - 닉네임·색상 중복도 `rooms` 행 락 안에서 확인한다. 락 순서는 ERD대로 `users` → `rooms`.
- 권한
  - INVITE-01: 그 방의 현재 멤버만 (`ROOM_002`), 삭제된 방은 `ROOM_005`.
  - INVITE-02·03: 로그인한 사용자 누구나. 막힘은 상태 값(02) 또는 에러 코드(03)로 돌려준다.
  - 세 API 모두 인증 필요 (`COMMON_008`). `SecurityConfig` 공개 경로에 넣지 않는다.
- 외부 연동 실패: 없음
- 제외 범위
  - 회차·결과·인증 테이블이 아직 없다. 아래 시나리오는 해당 기능을 구현할 때 테스트한다 (ROOM-01-6과 같은 처리).
    - INVITE-03-12의 결과 확인 상태 (`seen_result_round_date`): RESULT-02. 한 솥 확인 상태(`seen_pot_count`)는 이번에 채우고 테스트한다.
    - INVITE-03-13, INVITE-03-14 (회차 참여 시점, 투표 명단 유지): CERT-01, VOTE-01
    - INVITE-03-17 (지난 기록이 내 닉네임으로 보임): 같은 멤버 행을 다시 쓰는 것(INVITE-03-7)까지 이번에 확인하고, 기록 표시는 각 조회 기능에서 확인
  - 그래서 구현 후 INVITE-01, INVITE-02는 `구현됨`, INVITE-03은 `미구현`으로 둔다.

## 명세 변경
- `open-questions.md` Q-38, `invite.md` INVITE-03 17번, `api.md` "입장 시점은 [미정] Q-38": **닉네임 제출(WF 03) 때 입장까지 한 번에 처리**로 결정 처리
  - 프론트 확인 완료 (2026-11-11). Q-37(방 만들기)과 같은 흐름이고 명세 입력·`api.md`가 이미 이 방식이다.
- 상태: 구현 후 INVITE-01, INVITE-02를 `구현됨`으로 바꾼다. INVITE-03은 위 시나리오가 남아 `미구현`으로 둔다.

## 설계

### API 계약
- `POST /api/rooms/{roomId}/members/me/invite-token`
- 요청: 없음
- 응답: 새로 만들면 `201`, 이미 있으면 `200`. `Response<InviteTokenResponse>`, `{ "inviteToken": "..." }`
- 에러 코드: `ROOM_002` (403), `ROOM_005` (404), `COMMON_008` (401)

- `GET /api/invites/{token}`
- 요청: 없음
- 응답: `200`, `Response<InvitePreviewResponse>`, `{ "status": "JOINABLE", "room": { "roomId", "name", "goal", "memberCount", "memberLimit" }, "inviterNickname", "previousNickname" }`
- 에러 코드: `COMMON_008` (401)

- `POST /api/invites/{token}/join`
- 요청: `{ "nickname": "지수" }`
- 응답: 들어오면 `201`, 이미 멤버면 `200`. `Response<InviteJoinResponse>`, `{ "roomId", "memberId", "nickname", "color" }`
- 에러 코드: `ROOM_001` (409), `ROOM_003` (409), `ROOM_004` (409, `field: nickname`), `ROOM_006` (403), `COMMON_002` (400), `COMMON_008` (401)

### 엔티티
- 새 엔티티·컬럼 없음. `RoomMember`에 메서드만 추가한다.
  - `issueInviteToken(String token)`: 토큰이 없을 때만 채운다.
  - `rejoin(nickname, color, joinedAt, seenPotCount)`: `left_at`·`leave_type`을 비우고 닉네임·색상·들어온 시각·한 솥 확인 상태를 새로 쓴다. 지목·휴식·토큰은 그대로 둔다 (INVITE-03 7번).
  - `canRejoin(now, months)`: `LEFT`·`DATA_DELETED`이고 `left_at + 6개월 >= now`. 기존 `holdsNickname`과 같은 기준.
  - `isKicked()`, `isCurrent()`
- 새 멤버를 만들 때 `seen_pot_count`를 방의 `pot_completed_count`로 채우도록 `RoomMember.create`에 값을 넘긴다. 방 만들기(ROOM-01)는 0을 넘긴다.
- 리포지토리 추가
  - `findByInviteToken(token)`: 방까지 함께 읽는다.
  - `countByRoom_IdAndLeftAtIsNull(roomId)`: 방 인원 수
  - `findByRoom_IdAndLeftAtIsNull(roomId)`: 현재 멤버 색상
  - `existsByRoom_IdAndUser_IdAndLeaveType(roomId, userId, KICKED)`: 내보내진 사람
  - `findFirstByRoom_IdAndUser_IdAndLeftAtIsNotNullOrderByLeftAtDesc(roomId, userId)`: 가장 최근에 나간 내 행 (예전 닉네임, 다시 쓰기 대상)

### 주요 결정
- **토큰 생성:** `SecureRandom` 32바이트를 URL-safe Base64(패딩 없음, 43자)로 만든다. 컬럼 길이 64 안이고 링크 경로에 그대로 쓸 수 있다. 랜덤 생성은 `BosalColorPicker`처럼 컴포넌트(`InviteTokenGenerator`)로 분리해 테스트에서 고정할 수 있게 한다. 충돌은 확률상 무시하고 `invite_token` UNIQUE가 마지막으로 막는다.
- **토큰 동시 요청:** `rooms` 행을 `PESSIMISTIC_WRITE`로 잡은 뒤 내 멤버 행을 읽고, 토큰이 없을 때만 만든다. ERD의 "방 멤버를 바꾸는 코드는 이 락을 먼저 잡는다"를 따른다.
- **판단 순서 공통화:** INVITE-02와 INVITE-03이 같은 순서(INVITE-02 1번)로 판단하도록 판단 메서드 하나(`InviteStatus` 반환)를 두고 두 서비스가 쓴다. 미리보기는 락 없이 `@Transactional(readOnly = true)`, 입장은 락을 잡은 뒤 같은 메서드로 다시 판단한다.
  - 없는 토큰, 방 삭제, 초대한 사람 `KICKED` → `UNAVAILABLE`
  - 내 현재 멤버 행이 있음 → `ALREADY_MEMBER`
  - 내 `KICKED` 행이 있음 → `UNAVAILABLE`
  - 내 참여 방(삭제된 방 제외)이 2개 → `ROOM_LIMIT`
  - 방 인원 ≥ `member_limit` → `ROOM_FULL`
  - 그 외 → `JOINABLE`
- **미리보기 빈 값:** `UNAVAILABLE`이면 `room`, `inviterNickname`, `previousNickname`을 모두 `null`로 돌려준다. INVITE-02 3번의 "사유를 표시하지 않는다"에 맞춘다.
- **이미 멤버:** 미리보기는 `ALREADY_MEMBER`와 방 정보(`roomId` 포함)를 돌려준다. 프론트는 이 값으로 닉네임 화면 없이 바로 방으로 보낸다. 입장 API의 이미 멤버 처리(INVITE-03-5)는 두 번 누르기·재시도 대비다.
- **초대한 사람 닉네임:** 초대한 사람 행이 현재 멤버면 닉네임, `LEFT`·`DATA_DELETED`면 `null` (INVITE-02 4·5번).
- **예전 닉네임:** 내가 이 방에서 가장 최근에 나간 행이 6개월 안이면 그 닉네임, 아니면 `null`. `ALREADY_MEMBER`면 `null`.
- **입장 처리 순서** (`@Transactional`)
  1. 토큰으로 초대한 사람 행과 방을 찾는다. 없으면 `ROOM_006`.
  2. `users` 행 락 → `rooms` 행 락. 삭제된 사용자는 `COMMON_008` (ROOM-01과 같음).
  3. 판단 메서드로 다시 판단. `UNAVAILABLE` → `ROOM_006`, `ROOM_LIMIT` → `ROOM_001`, `ROOM_FULL` → `ROOM_003`, `ALREADY_MEMBER` → 기존 멤버 정보로 `200`.
  4. 다시 쓸 행 = 가장 최근에 나간 내 행이 `canRejoin`이면 그 행, 아니면 없음.
  5. 닉네임 중복: `findSameNicknameMembers(roomId, nickname, 다시 쓸 행 ID 또는 -1)` 중 `holdsNickname`이 있으면 `ROOM_004` (`field: nickname`). ROOM-04 코드와 같은 쿼리·기준을 쓴다.
  6. 색상: 현재 멤버가 쓰는 색 목록을 구한다. 다시 쓸 행이면 예전 색이 목록에 없을 때 그대로, 있으면 `BosalColorPicker.pick(사용 중인 색)`. 새 행이면 `pick(사용 중인 색)`.
  7. 다시 쓸 행이 있으면 `rejoin`, 없으면 새 행 저장. 들어온 시각은 주입받은 `Clock`, `seen_pot_count`는 방의 `pot_completed_count`.
- **이미 멤버인데 닉네임이 다름:** 기존 멤버 정보를 그대로 돌려주고 닉네임은 바꾸지 않는다 (INVITE-03 3번 "새로 만들지 않고 기존 멤버 정보를 돌려준다"). 요청 닉네임 형식 검증은 DTO에서 먼저 한다.
- **`DATA_DELETED`로 나간 행:** INVITE-03 6번에 나간 유형 구분이 없어 `LEFT`와 같이 6개월 안이면 다시 쓴다. AUTH-01이 6개월 안에 돌아온 사용자의 공양미·아이템·방어권을 이어주는 것과 맞고, 본인이 직접 다시 들어올 때만 이어진다. 새 행으로 만들면 예전 행이 닉네임을 6개월 동안 잡고 있어 본인이 예전 닉네임을 못 쓴다. 이미 지운 사진은 돌아오지 않는다. 초대한 사람으로서는 INVITE-02 5번대로 스스로 나간 것과 같다.
- **6개월 기준:** `left_at + 6개월` 시각까지 포함 (INVITE-03 11번). 닉네임 보유 기준과 같은 상수를 쓰도록 `RoomService`의 `NICKNAME_HOLD_MONTHS`를 공용 위치(`RoomMember` 상수 등)로 옮긴다.
- **닉네임 DTO 검증:** `RoomNicknameUpdateRequest`와 같은 `@Pattern`·`@Size`를 `InviteJoinRequest`에 쓴다.
- **응답 상태 코드:** INVITE-01, INVITE-03은 새로 만들었는지에 따라 `201`/`200`을 나눈다. 서비스가 결과와 생성 여부를 함께 돌려주고 컨트롤러가 `Response.created` / `Response.ok`를 고른다.
- **에러 코드:** `BaseErrorCode` `// Room` 구역에 `ROOM_FULL`(`ROOM_003`, 409, "방이 가득 찼습니다."), `ROOM_UNAVAILABLE`(`ROOM_006`, 403, "들어갈 수 없는 방입니다.")을 추가하고 스냅샷에 두 줄을 추가한다.
- **도메인 위치:** 초대는 방 멤버를 다루므로 `domain/room` 아래에 `InviteController`, `InviteService`, DTO를 둔다. Swagger 태그는 `[INVITE] 초대 API`.

## 규칙 점검
- `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음

## 할 일
- [x] `BaseErrorCode`에 `ROOM_003`, `ROOM_006` 추가, `error-codes.snapshot` 두 줄 추가
- [x] 명세 변경: Q-38 결정, INVITE-03 17번, `api.md` 입장 시점
- [x] `RoomMember` 메서드(`issueInviteToken`, `rejoin`, `canRejoin`, `isKicked`), 6개월 상수 공용화, `create`에 `seenPotCount`
- [x] `RoomMemberRepository` 조회 메서드, `InviteTokenGenerator`
- [x] INVITE-01 서비스·컨트롤러·DTO (`POST /api/rooms/{roomId}/members/me/invite-token`)
- [x] INVITE-02 판단 메서드, 서비스·컨트롤러·DTO (`GET /api/invites/{token}`)
- [x] INVITE-03 서비스·컨트롤러·DTO (`POST /api/invites/{token}/join`)
- [x] 테스트: INVITE-01-1 ~ INVITE-01-4
- [x] 테스트: INVITE-02-1 ~ INVITE-02-13
- [x] 테스트: INVITE-03-1 ~ INVITE-03-11, INVITE-03-12(한 솥 부분), INVITE-03-15, INVITE-03-16

## 열린 질문
- 없음
  - Q-38 입장 처리 시점: 프론트 확인으로 "닉네임 제출 때 한 번에"로 결정
  - `DATA_DELETED`로 나간 행: 명세대로 6개월 안이면 다시 쓰기로 결정 (2026-11-11)

## 자기 점검

| 처리 규칙 | 시나리오 | 테스트 |
| --- | --- | --- |
| INVITE-01 1 (현재 멤버만) | INVITE-01-3 | `InviteTokenTest` |
| INVITE-01 2, 3 (없으면 임의 값으로 생성, 있으면 재사용) | INVITE-01-1, INVITE-01-2 | `InviteTokenTest` |
| INVITE-01 6 (동시 요청에도 하나) | INVITE-01-4 | `InviteTokenTest` |
| INVITE-02 1-1 (방 삭제, 초대한 사람 내보내짐) | INVITE-02-2, INVITE-02-4 | `InvitePreviewTest` |
| INVITE-02 1-2 (이미 멤버) | INVITE-02-6 | `InvitePreviewTest` |
| INVITE-02 1-3 (내가 내보내짐) | INVITE-02-7 | `InvitePreviewTest` |
| INVITE-02 1-4 (참여 방 2개, 가득 찬 방보다 먼저) | INVITE-02-8 | `InvitePreviewTest` |
| INVITE-02 1-5 (함께할 인원만큼 참) | INVITE-02-9, INVITE-02-13 | `InvitePreviewTest` |
| INVITE-02 1-6, 4 (입장 가능, 초대한 사람 닉네임) | INVITE-02-1 | `InvitePreviewTest` |
| INVITE-02 2 (없는 토큰) | INVITE-02-3 | `InvitePreviewTest` |
| INVITE-02 3 (UNAVAILABLE이면 정보 비움) | INVITE-02-2, INVITE-02-7 | `InvitePreviewTest` |
| INVITE-02 5 (스스로 나간 초대한 사람) | INVITE-02-5 | `InvitePreviewTest` |
| INVITE-02 6 (자리가 나면 같은 링크) | INVITE-02-10 | `InvitePreviewTest` |
| INVITE-02 7 (예전 닉네임, 6개월 기준) | INVITE-02-11, INVITE-02-12 | `InvitePreviewTest` |
| INVITE-03 1, 2 (다시 판단, 에러 코드) | INVITE-03-2 ~ INVITE-03-4 | `InviteJoinTest` |
| INVITE-03 3 (이미 멤버) | INVITE-03-5 | `InviteJoinTest` |
| INVITE-03 5 (닉네임 중복, 대소문자 무시) | INVITE-03-6 | `InviteJoinTest` |
| INVITE-03 6, 7 (예전 행 재사용, 기록 이어짐) | INVITE-03-7 | `InviteJoinTest` |
| INVITE-03 8 (예전 행 닉네임 변경) | INVITE-03-8 | `InviteJoinTest` |
| INVITE-03 9 (예전 색이 겹치면 랜덤) | INVITE-03-9, INVITE-03-10 | `InviteJoinTest` |
| INVITE-03 10, 11 (6개월 지나면 새 행) | INVITE-03-11 | `InviteJoinTest` |
| INVITE-03 12 (새 멤버 색) | INVITE-03-1 | `InviteJoinTest` |
| INVITE-03 13 (확인 상태 채움) | INVITE-03-12 (한 솥만) | `InviteJoinTest` |
| INVITE-03 16 (동시 입장) | INVITE-03-15, INVITE-03-16 | `InviteJoinTest` |

- 테스트가 없는 시나리오: INVITE-03-12의 결과 확인 부분(RESULT-02), INVITE-03-13·14(CERT-01, VOTE-01), INVITE-03-17(각 조회 기능). 회차·결과·인증 테이블이 아직 없어서 범위에서 미리 뺐다. 그래서 INVITE-03은 `미구현`으로 두고 INVITE-01, INVITE-02만 `구현됨`으로 바꿨다.
- 시나리오가 없는 처리 규칙
  - INVITE-01 4, 5 (방 삭제 시 사용 불가, 6개월 안 재입장은 같은 토큰): INVITE-02-2와 INVITE-03-7(같은 행 재사용)로 간접 확인
  - INVITE-03 4 (닉네임 형식): `RoomNicknameUpdateRequest`와 같은 DTO 검증이라 ROOM-01-5로 확인
  - INVITE-03 14, 15 (회차 참여 시점, 투표 명단 유지): INVITE-03-13·14와 같은 이유로 뒤로 미룸
  - INVITE-01의 삭제된 방(`ROOM_005`): 코드에는 있으나 시나리오가 없다
- 명세에 없는 규칙을 만든 곳: 없음. `UNAVAILABLE`이면 초대한 사람·예전 닉네임도 비우는 것은 INVITE-02 3번(사유 표시 안 함)을 따른 구현 결정
- 계획에 없던 변경
  - 입장 트랜잭션을 `READ COMMITTED`로 읽는다. 토큰으로 방 ID를 찾는 조회가 락보다 먼저라, MySQL 기본(REPEATABLE READ)이면 락을 잡은 뒤에도 그 전 스냅샷을 읽어 동시 입장에서 인원을 넘길 수 있다. 테스트는 H2라 MySQL에서의 차이는 확인하지 못했다.
  - 한 솥 확인 상태는 `RoomMember.create`에 값을 넘기지 않고 `create` 안에서 방의 현재 완성 횟수로 채운다. 호출하는 쪽을 고칠 필요가 없다.
  - `findSameNicknameMembers`의 제외할 행 ID를 NULL 허용으로 바꿨다. 새로 들어오는 사람은 제외할 행이 없다.
  - 201/200을 나누려고 서비스가 `CreationResult`(응답과 생성 여부)를 돌려준다.
