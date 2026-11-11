# API 명세

경로, 요청·응답 필드, 에러 code를 적은 문서입니다. 동작 규칙은 각 기능 명세를 따르고, 엔드포인트마다 기능 ID를 붙였습니다. 구현된 API는 Swagger가 기준입니다.

- `[미정] Q-37`처럼 적힌 항목은 [`open-questions.md`](open-questions.md)의 답에 따라 바뀔 수 있습니다.

## 0. 공통

### 인증

- `POST /api/auth/login`, `POST /api/auth/test-token`을 뺀 모든 API는 `Authorization: Bearer {accessToken}` 헤더가 필요합니다. (AUTH-02)
- 토큰이 없거나 만료되면 `COMMON_008` (401)을 돌려줍니다. 클라이언트는 인증 코드를 다시 받아 로그인합니다.

### 응답 형식

```json
{ "success": true, "code": "200", "message": "요청이 성공했습니다.", "data": { } }
```

```json
{ "success": false, "code": "ROOM_004", "message": "이미 쓰는 닉네임이에요.", "field": "nickname" }
```

- 생성은 201, 조회·수정·삭제는 200입니다.
- 돌려줄 값이 없으면 `data`를 빼고 보냅니다. `field`는 입력값 오류일 때만 보냅니다.

### 값 형식

- 시각은 `2026-11-05T19:24:00`, 날짜는 `2026-11-05` 형식이고 모두 KST입니다.
- `memberId`는 방 안의 멤버 ID입니다. 사용자 ID는 응답에 내보내지 않습니다.
- `riceBalance`는 화면 표시 잔액입니다. 실제 잔액이 음수면 0입니다. (RICE-01)
- 사진 URL은 응답할 때마다 만드는 만료 URL입니다. 삭제된 사진은 `null`입니다. (CERT-04)
- 인증 마감은 항상 회차 날짜 다음 날 05:00(KST)라 응답에 넣지 않습니다. 05:00 전에 등록한 인증은 전날 회차입니다. 프론트가 기기 시간대가 아니라 KST 기준으로 계산합니다.

### 필드 이름

| 종류 | 규칙 | 예 |
|---|---|---|
| ID | `{대상}Id` | `roomId`, `memberId` |
| 불린 | `is`·`has`·`can`으로 시작 | `isOwner`, `hasUnseen`, `canCreateRoom` |
| 개수 | `{대상}Count` | `memberCount`, `certifiedCount` |
| 시각 | `{동작}At`, 기한은 `{상태}Until` | `certifiedAt`, `closesAt`, `editableUntil` |
| 날짜 | `{대상}Date` | `roundDate` |
| enum 값 | 대문자 스네이크 | `DELIVERY`, `NO_VOTE` |

### enum

| 이름 | 값 |
|---|---|
| RoomGoal | `DELIVERY` `CAFE` `SHOPPING` `ETC` |
| BosalColor | `PINK` `PURPLE` `MINT` `YELLOW` |
| CertificationResult | `ENDURED` (참았어요) `FAILED` (못 참았어요) |
| LockReason | `TIME_OVER` (10분 지남) `VOTE_STARTED` (투표 시작) |
| RoundStatus | `CERTIFYING` (인증 중) `VOTING` (투표 중) `ANNOUNCED` (발표) `NO_VOTE` (투표 없음) |
| ReactionType | `RICE` `MOKTAK` |
| ResultType | `PICKED` `ALL_BOSAL` `ALL_SAFE` `DEFENDED` |
| InviteStatus | `JOINABLE` `ALREADY_MEMBER` `ROOM_LIMIT` `ROOM_FULL` `UNAVAILABLE` |
| DefenseTicketStatus | `NONE` `OWNED` `IN_USE` |
| PushAgreementType | `CERT_REMINDER` |
| PushAgreementResult | `newAgreement` `alreadyAgreed` `agreementRejected` (앱인토스 값 그대로) |

### 에러 code

- 각 API의 에러는 기능 명세의 `예외`를 따릅니다.
- 구현된 code 전체는 서버의 `/docs/error-codes` 페이지에서 볼 수 있습니다.
- 이미 정해진 code는 바꾸지 않습니다. (`src/test/resources/error-codes.snapshot`)

## 1. API 목록

| 도메인 | 메서드 | 경로 | 기능 ID |
|---|---|---|---|
| 인증 | POST | `/api/auth/login` | AUTH-01 |
| 인증 | POST | `/api/auth/test-token` | AUTH-03 |
| 사용자 | GET | `/api/users/me` | USER-01 |
| 사용자 | GET | `/api/users/me/records` | USER-02 |
| 사용자 | PATCH | `/api/users/me/settings` | USER-03 |
| 사용자 | DELETE | `/api/users/me` | USER-05 |
| 사용자 | POST | `/api/users/me/push-agreements` | NOTI-01 |
| 방 | POST | `/api/rooms` | ROOM-01 |
| 방 | GET | `/api/rooms/{roomId}/today` | ROOM-02 |
| 방 | PATCH | `/api/rooms/{roomId}/members/me/nickname` | ROOM-04 |
| 방 | PUT | `/api/rooms/{roomId}/members/me/seen-result` | ROOM-06 |
| 방 | PUT | `/api/rooms/{roomId}/members/me/seen-pot` | ROOM-06 |
| 방 | DELETE | `/api/rooms/{roomId}/members/me` | ROOM-07 |
| 방 | DELETE | `/api/rooms/{roomId}/members/{memberId}` | ROOM-08 |
| 초대 | POST | `/api/rooms/{roomId}/members/me/invite-token` | INVITE-01 |
| 초대 | GET | `/api/invites/{token}` | INVITE-02 |
| 초대 | POST | `/api/invites/{token}/join` | INVITE-03 |
| 곳간 | GET | `/api/store` | STORE-01 |
| 곳간 | POST | `/api/items/{itemId}/purchases` | STORE-02 |
| 곳간 | PUT | `/api/users/me/equipped-items` | STORE-03 |
| 인증 사진 | POST | `/api/certifications/upload-urls` | CERT-04 |
| 인증 사진 | POST | `/api/rooms/{roomId}/certifications` | CERT-01 |
| 인증 사진 | PATCH | `/api/certifications/{certificationId}` | CERT-02 |
| 인증 사진 | DELETE | `/api/certifications/{certificationId}` | CERT-02 |
| 리액션 | PUT | `/api/certifications/{certificationId}/reactions/me` | REACT-01 |
| 리액션 | DELETE | `/api/certifications/{certificationId}/reactions/me` | REACT-01 |
| 콕 | POST | `/api/rooms/{roomId}/pokes` | POKE-01 |
| 투표 | GET | `/api/rooms/{roomId}/vote` | VOTE-05 |
| 투표 | POST | `/api/rounds/{roundId}/votes` | VOTE-03 |
| 결과 | GET | `/api/rounds/{roundId}/result` | RESULT-02 |
| 톡톡 | POST | `/api/rounds/{roundId}/taps` | RESULT-03 |
| 톡톡 | GET | `/api/rounds/{roundId}/taps/received` | RESULT-03 |
| 톡톡 | PUT | `/api/rounds/{roundId}/taps/received/played` | RESULT-03 |
| 한 솥 | GET | `/api/rooms/{roomId}/pots/{seq}` | POT-01 |
| 방어권 | POST | `/api/defense-tickets` | SHIELD-01 |
| 방어권 | GET | `/api/defense-tickets/rooms` | SHIELD-02 |
| 방어권 | POST | `/api/rooms/{roomId}/defenses` | SHIELD-02 |

## 2. 인증

### POST /api/auth/login

AUTH-01. 앱인토스 인증 코드로 로그인합니다. 처음 온 사용자는 새로 만듭니다.

```json
// request
{ "authorizationCode": "..." }

// response 200
{ "accessToken": "...", "expiresIn": 3600, "isNewUser": true }
```

- `isNewUser`: 6개월 안에 돌아와 복구한 사용자는 `false`
- 리프레시 토큰 없이 액세스 토큰만 쓸지는 [미정] Q-16
- 에러: `AUTH_001` (400), `AUTH_002` (502), `COMMON_002` (400)

### POST /api/auth/test-token

AUTH-03. 개발 환경 전용입니다. 출시 전에 끄고 이후 제거합니다.

```json
// request (헤더 X-Test-Token-Key: 서버 비밀값)
{ "testUser": "tester1" }

// response 200
{ "accessToken": "...", "expiresIn": 86400 }
```

- `testUser`: 영문 소문자·숫자 1~8자
- 에러: `COMMON_001` (404, 기능 꺼짐), `COMMON_002` (400), `COMMON_003` (400), `COMMON_008` (401, 비밀값 불일치)

## 3. 사용자

### GET /api/users/me

USER-01. 앱을 열 때 호출합니다. 어느 화면으로 갈지 판단합니다.

```json
{
  "isPokePushEnabled": true,
  "isActivityPushEnabled": true,
  "isCertReminderAgreed": false,
  "equippedItemCodes": ["SANTA_HAT"],
  "rooms": [
    { "roomId": 1, "name": "배달 끊기 방", "nickname": "민지", "color": "PINK", "hasUnseen": false }
  ],
  "canCreateRoom": true
}
```

- `equippedItemCodes`: 내 보살 캐릭터에 씀. 착용은 사용자 단위라 모든 방에 같음
- `rooms`: 현재 멤버인 방만. 비어 있으면 첫 화면. `nickname`, `color`는 그 방의 내 보살
- `hasUnseen`: 안 본 결과나 한 솥 축하가 있음
- `canCreateRoom`: 참여 중인 방이 2개면 `false`

### GET /api/users/me/records

USER-02. 내 보살 탭의 이번 달 기록입니다. 나간 방을 포함한 모든 방을 합산합니다.

```json
{
  "enduredCount": 9,
  "streakDays": 4,
  "pickedCount": 2,
  "calendar": [
    { "date": "2026-11-01", "result": "ENDURED" },
    { "date": "2026-11-02", "result": "FAILED" }
  ]
}
```

- `calendar`: 인증이 없는 날은 빠짐. 그날 하나라도 `FAILED`면 `FAILED`

### PATCH /api/users/me/settings

USER-03. 앱 안의 알림 설정을 바꿉니다. 보낸 값만 바꿉니다.

```json
{ "isPokePushEnabled": false, "isActivityPushEnabled": true }
```

- 에러: `COMMON_002` (400)

### DELETE /api/users/me

USER-05. 내 데이터를 삭제합니다. 모든 방에서 나가고 사진은 바로 삭제합니다. 6개월 안에 돌아오면 복구됩니다.

### POST /api/users/me/push-agreements

NOTI-01. 클라이언트가 받은 토스 알림 동의 결과를 저장합니다.

```json
{ "type": "CERT_REMINDER", "result": "newAgreement" }
```

- `agreementRejected`면 저장하지 않음
- 에러: `COMMON_002` (400)

## 4. 방

### POST /api/rooms

ROOM-01. 방을 만들고 만든 사람을 방장 멤버로 등록합니다. 색은 서버가 배정합니다.

```json
// request
{ "name": "배달 끊기 방", "goal": "DELIVERY", "memberLimit": 4, "nickname": "민지" }

// response 201
{ "roomId": 1, "memberId": 11, "nickname": "민지", "color": "PINK" }
```

- `name`: 1~12자, `memberLimit`: 2~4, `nickname`: 1~8자 (형식은 `common.md`)
- 방을 만드는 시점은 [미정] Q-37
- 에러: `ROOM_001` (409), `COMMON_002` (400)

### GET /api/rooms/{roomId}/today

ROOM-02. 오늘 탭입니다. 회차 날짜가 오늘인 회차를 기준으로 돌려줍니다.

```json
{
  "room": {
    "roomId": 1, "name": "배달 끊기 방", "goal": "DELIVERY",
    "memberCount": 4, "memberLimit": 4, "isOwner": true, "potCompletedCount": 1, "hasVotingRound": true
  },
  "pot": { "amount": 186, "goalAmount": 300 },
  "round": {
    "roundId": 101, "status": "CERTIFYING",
    "certifiedCount": 3, "isAllCertified": false
  },
  "members": [
    {
      "memberId": 11, "nickname": "민지", "color": "PINK", "equippedItemCodes": ["SANTA_HAT"],
      "isMe": true, "monthlyEnduredCount": 9, "monthlyPickedCount": 3,
      "certification": {
        "certificationId": 501, "result": "ENDURED", "memo": "집밥 해먹었어요", "spentAmount": null,
        "imageUrl": "https://...", "certifiedAt": "2026-11-05T19:24:00",
        "myReaction": null,
        "editableUntil": "2026-11-05T19:34:00", "lockReason": null
      }
    }
  ],
  "me": { "isLastCertifier": false, "receivedPokeCount": 2 },
  "entry": {
    "unplayedTapRoundId": null,
    "unseenResultRoundId": 100,
    "hasUnseenPotCelebration": false
  }
}
```

- `room.potCompletedCount`: 이 방에서 한 솥을 다 채운 횟수 (쌀가마 배지). 몇 번째 솥인지 표시하고, 안 본 축하를 판단하는 기준(`seen-pot`의 `seenPotCount`)으로도 씀
- `room.hasVotingRound`: 이 방에 투표 중인 회차가 있음 (어제 회차 포함). 이때 내보내기는 `ROOM_008`로 막힘. `round.status`는 오늘 회차만의 상태라 다를 수 있음
- `round.roundId`: 오늘 회차 행이 아직 없으면 `null`. 행은 첫 인증이나 첫 콕 때 만듦
- `round.status`: 회차 행이 없으면 `CERTIFYING`
- `round.certifiedCount`: 현재 멤버 중 오늘 인증한 사람 수. 인증하고 나간 멤버는 세지 않음
- `round.isAllCertified`: 현재 멤버 전원이 인증함
- `certification`: 인증 전이면 `null`
- `editableUntil`: 수정·삭제 마감 시각 (인증 후 10분). `lockReason`이 `null`일 때 남은 시간 표시에 씀
- `lockReason`: 수정·삭제할 수 있으면 `null`. 10분이 지나면 `TIME_OVER`, 10분 안이어도 투표가 시작되면 `VOTE_STARTED`
- `equippedItemCodes`: 착용 중인 아이템 코드 목록. 없으면 빈 배열
- `members[]`: 홈 카드, 방 정보, 멤버 프로필 시트에 같이 씀. 멤버 프로필 API는 따로 없음
- `monthlyEnduredCount`, `monthlyPickedCount`: 이 방의 이번 달 참은 횟수와 지목 횟수. 지목은 방어 성공을 세지 않음
- 리액션 받은 수는 보내지 않음 ([미정] Q-52)
- `myReaction`: 남기지 않았으면 `null`. 누가 눌렀는지는 보내지 않음
- `me.isLastCertifier`: 내가 인증하면 전원 인증이 됨. 등록 전 확인 시트에 씀. 어제 회차가 투표 중이어도 등록하면 어제 투표가 닫히고 오늘 투표가 시작됨
- `me.receivedPokeCount`: 이번 회차에 받은 콕 수. 인증하면 0으로 돌아갈지는 [미정] Q-54
- `entry`: 앱을 열거나 방에 들어올 때 홈보다 먼저 띄울 화면. 톡톡 → 결과 → 한 솥 축하 → 방 홈 순서
  - `unplayedTapRoundId`가 있으면 `GET /api/rounds/{roundId}/taps/received` → 재생 후 `PUT …/played`
  - `unseenResultRoundId`가 있으면 `GET /api/rounds/{roundId}/result` → 본 뒤 `PUT …/seen-result`
  - `hasUnseenPotCelebration`이 `true`면 `GET /api/rooms/{roomId}/pots/{room.potCompletedCount}` → 본 뒤 `PUT …/seen-pot`
- 에러: `ROOM_002` (403), `ROOM_005` (404)

### PATCH /api/rooms/{roomId}/members/me/nickname

ROOM-04. 이 방에서 쓰는 내 닉네임을 바꿉니다.

```json
{ "nickname": "지수2" }
```

- 1~8자, 한글(완성형)·영문·숫자
- 현재 멤버와 나간 지 6개월이 안 된 멤버의 닉네임은 쓸 수 없음. 영문 대소문자는 구분하지 않음
- 지금 닉네임과 같으면 저장하지 않고 성공

- 에러: `ROOM_002` (403), `ROOM_004` (409, `field: nickname`), `ROOM_005` (404), `COMMON_002` (400)

### PUT /api/rooms/{roomId}/members/me/seen-result

ROOM-06. 결과를 어디까지 봤는지 저장합니다. 그 회차와 이전 회차를 모두 본 것으로 처리합니다.

```json
{ "roundId": 100 }
```

- 이미 저장한 회차보다 이전 회차를 보내면 바꾸지 않음
- 에러: `ROOM_002` (403), `ROOM_005` (404), `COMMON_002` (400, 이 방의 발표된 회차가 아님)

### PUT /api/rooms/{roomId}/members/me/seen-pot

ROOM-06. 한 솥 축하를 몇 번째 솥까지 봤는지 저장합니다.

```json
{ "seenPotCount": 2 }
```

- 저장값보다 작거나 같으면 바꾸지 않음
- 에러: `ROOM_002` (403), `ROOM_005` (404), `COMMON_002` (400, 현재 완성 횟수보다 큼)

### DELETE /api/rooms/{roomId}/members/me

ROOM-07. 방을 나갑니다. 방장이면 위임하고, 마지막 멤버면 방을 삭제합니다.

- 사진은 바로 지우지 않고 회차 종료 7일 뒤 삭제
- 에러: `ROOM_002` (403), `ROOM_005` (404)

### DELETE /api/rooms/{roomId}/members/{memberId}

ROOM-08. 방장이 멤버를 내보냅니다. 내보낸 사람은 같은 방에 다시 들어올 수 없습니다.

- 투표 중인 회차가 있으면 `ROOM_008`. 어제 회차가 투표 중이어도 같음 (오늘 탭 `room.hasVotingRound`)

- 에러: `ROOM_002` (403), `ROOM_005` (404), `ROOM_007` (403), `ROOM_008` (409), `ROOM_009` (404), `ROOM_010` (400)

## 5. 초대

### POST /api/rooms/{roomId}/members/me/invite-token

INVITE-01. 내 초대 토큰을 돌려줍니다. 없으면 만듭니다. 클라이언트는 이 토큰으로 공유 링크를 만듭니다.

```json
// response 200
{ "inviteToken": "..." }
```

- 에러: `ROOM_002` (403), `ROOM_005` (404)

### GET /api/invites/{token}

INVITE-02. 초대 미리보기와 입장 판단입니다. 막힌 경우도 200으로 보내고 `status`로 화면을 나눕니다.

```json
{
  "status": "JOINABLE",
  "room": { "roomId": 1, "name": "배달 끊기 방", "goal": "DELIVERY", "memberCount": 3, "memberLimit": 4 },
  "inviterNickname": "민지",
  "previousNickname": null
}
```

| status | 판단 순서 |
|---|---|
| `UNAVAILABLE` | 없는 토큰, 방 삭제, 초대한 사람이 내보내짐 |
| `ALREADY_MEMBER` | 이미 이 방 멤버 |
| `UNAVAILABLE` | 내가 이 방에서 내보내짐 (사유 표시 안 함) |
| `ROOM_LIMIT` | 참여 중인 방이 2개 |
| `ROOM_FULL` | 방 인원이 함께할 인원만큼 참 |
| `JOINABLE` | 모두 통과 |

- `room`: `UNAVAILABLE`이면 `null`
- `inviterNickname`: 초대한 사람이 나갔으면 `null`
- `previousNickname`: 나간 지 6개월 안 된 방이면 예전 닉네임

### POST /api/invites/{token}/join

INVITE-03. 방에 들어옵니다. 나간 지 6개월 안이면 예전 멤버 행을 다시 씁니다.

```json
// request
{ "nickname": "지수" }

// response 201
{ "roomId": 1, "memberId": 12, "nickname": "지수", "color": "MINT" }
```

- 닉네임 규칙은 닉네임 수정과 같음 (1~8자, 6개월 안에 나간 멤버의 닉네임도 불가, 대소문자 구분 없음)
- 다시 들어오는 사람은 자기 예전 닉네임을 쓸 수 있음 (중복 검사에서 내 예전 행은 제외)
- 나간 지 6개월 안이면 예전 기록이 이어지고, 예전 색을 다른 멤버가 쓰면 남은 색 중 랜덤
- 이미 멤버면 새로 만들지 않고 기존 정보를 돌려줌
- 투표가 시작된 뒤 들어오면 다음 회차부터 참여
- 입장 시점은 [미정] Q-38
- 에러: `ROOM_001` (409), `ROOM_003` (409), `ROOM_004` (409, `field: nickname`), `ROOM_006` (403), `COMMON_002` (400)

## 6. 곳간

### GET /api/store

STORE-01. 공양미 잔액, 방어권 상태, 꾸미기 아이템 목록입니다.

```json
{
  "riceBalance": 120,
  "defenseTicket": { "status": "IN_USE", "usingRoomName": "배달 끊기 방", "isUsedThisWeek": true },
  "items": [
    { "itemId": 1, "code": "SANTA_HAT", "name": "산타 모자", "price": 100, "slot": "HEAD", "isOwned": true, "isEquipped": true }
  ]
}
```

- `usingRoomName`: `IN_USE`일 때만. 결과 발표 전에 방이 삭제되면 `IN_USE`가 끝남
- `IN_USE`일 때 방어권을 더 살 수 있는지는 [미정] Q-20

### POST /api/items/{itemId}/purchases

STORE-02. 꾸미기 아이템을 사고 바로 착용합니다.

```json
// response 201
{ "riceBalance": 20, "equippedItemIds": [1] }
```

- `equippedItemIds`: 구매 뒤 착용 중인 아이템 전체. 산 아이템과 같은 슬롯에 있던 아이템은 빠짐

- 에러: `STORE_001` (409), `STORE_002` (404), `STORE_003` (409)

### PUT /api/users/me/equipped-items

STORE-03. 착용할 아이템 목록을 보내면 착용 상태를 통째로 바꿉니다. 목록에 없는 아이템은 벗깁니다.

```json
{ "itemIds": [1] }
```

- 지금은 슬롯이 1칸이라 0~1개. 슬롯이 늘면 칸마다 하나씩 보냄

- 곳간에서 끼워 보고 벗어 보는 동안에는 보내지 않고, 마지막 착용 목록을 한 번에 보냄 (구현할 때 Swagger 설명에도 적음)
- 빈 배열이면 모두 벗음
- 슬롯마다 하나. 지금 슬롯은 `HEAD` 1칸이고 칸 수는 [미정] Q-53
- 에러: `STORE_004` (409, 보유하지 않은 아이템), `COMMON_002` (400, 같은 슬롯 중복)

## 7. 인증 사진

> 담당자 확인
> - `roundDate`를 화면에서 쓰는지 (오늘 탭에서는 뺌)
> - 리액션 이름이 "잘 참았어요/아쉬워요"로 바뀌었는지, 받은 수를 보여주는지 ([미정] Q-52, 오늘 탭에서는 뺌)

### POST /api/certifications/upload-urls

CERT-04. 사진 업로드 URL을 발급합니다. 임시 경로에 올리고 인증 등록 때 정식 경로로 옮깁니다.

```json
// request
{ "contentType": "image/jpeg" }

// response 201
{ "uploadUrl": "https://...", "imageKey": "tmp/...", "expiresIn": 300 }
```

- 허용 파일 형식·크기는 [미정] Q-30
- 업로드에 실패하면 `imageKey` 없이 인증을 등록함
- 에러: `COMMON_002` (400, 허용하지 않는 형식)

### POST /api/rooms/{roomId}/certifications

CERT-01. 인증을 등록합니다. 회차는 서버가 요청을 받은 시각으로 정합니다.

```json
// request
{ "result": "FAILED", "memo": "치킨 시켰어요", "spentAmount": 23000, "imageKey": "tmp/..." }

// response 201
{
  "certificationId": 501, "roundDate": "2026-11-05",
  "certifiedAt": "2026-11-05T19:24:00", "editableUntil": "2026-11-05T19:34:00",
  "riceEarned": 10, "potEarned": 10,
  "canUseDefense": true,
  "isVoteStarted": false
}
```

- `memo`: 1~40자 (글자 수 기준은 [미정] Q-29)
- `spentAmount`: `FAILED`면 필수 1~999,999, `ENDURED`면 보내지 않음
- `imageKey`: 선택. 업로드 URL로 받은 키만 쓸 수 있음
- `riceEarned`, `potEarned`: 방 인원이 1명이면 0 (RICE-01)
- `isVoteStarted`: 이 등록으로 전원 인증이 되어 투표가 시작됨. 인증 완료 화면(디자인 p109)의 "모두가 인증해서 투표가 열렸어요"에 씀. 어제 회차가 투표 중이었으면 어제 투표는 이때 닫힘
- 에러: `CERT_001` (409, 이미 인증함), `ROOM_002` (403), `ROOM_005` (404), `COMMON_002` (400, 입력값 오류·없거나 만료된 `imageKey`)

### PATCH /api/certifications/{certificationId}

CERT-02. 등록 후 10분 안에 수정합니다. 등록처럼 모든 값을 보내고, 보낸 값으로 통째로 바꿉니다. 공양미를 다시 주지 않습니다.

```json
// request
{ "result": "FAILED", "memo": "치킨 반 마리만 시켰어요", "spentAmount": 23000, "imageKey": null, "isImageKept": true }

// response 200
{
  "certificationId": 501, "result": "FAILED", "memo": "치킨 반 마리만 시켰어요", "spentAmount": 23000,
  "imageUrl": "https://...", "editableUntil": "2026-11-05T19:34:00"
}
```

- `result`, `memo`, `spentAmount`: 등록과 같은 규칙. `ENDURED`로 바꾸면 `spentAmount`를 보내지 않고, 저장된 금액은 지워짐
- 사진은 `imageKey`와 `isImageKept`로 정함

  | `imageKey` | `isImageKept` | 결과 |
  |---|---|---|
  | 있음 | `false` | 새 사진으로 바꾸고 기존 사진은 바로 삭제 |
  | 없음 | `true` | 기존 사진 유지 (사진이 없었으면 그대로 없음) |
  | 없음 | `false` | 사진 삭제 |
  | 있음 | `true` | `COMMON_002` |

- `editableUntil`: 수정해도 바뀌지 않음 (등록 후 10분)
- 에러: `CERT_002` (404), `CERT_003` (403, 본인 인증 아님), `CERT_004` (409, 수정 시간 지남), `CERT_005` (409, 투표 시작), `COMMON_002` (400)

### DELETE /api/certifications/{certificationId}

CERT-02. 등록 후 10분 안에 삭제합니다. 지급한 공양미와 한 솥 적립을 회수하고 사진을 바로 지웁니다.

```json
// response 200
{ "riceRecalled": 10, "potRecalled": 10 }
```

- `potRecalled`: 그 인증이 들어간 솥이 이미 가득 찼으면 0 (RICE-02)
- 이미 삭제한 내 인증을 다시 삭제하면 회수 없이 `0`, `0`으로 성공 (재시도해도 안전). 이 확인은 10분·투표 시작 잠금 검사보다 먼저 해서, 10분이 지났거나 투표가 시작된 뒤의 재시도도 `CERT_004`·`CERT_005` 대신 성공
- 확인 순서: 없는 인증(`CERT_002`) → 본인 인증 아님(`CERT_003`) → 이미 삭제됨(성공) → 수정 시간 지남(`CERT_004`) → 투표 시작(`CERT_005`)
- 삭제한 뒤 같은 회차에 다시 인증할 수 있음
- 에러: `CERT_002` (404, 없는 인증), `CERT_003` (403), `CERT_004` (409), `CERT_005` (409)

### PUT /api/certifications/{certificationId}/reactions/me

REACT-01. 리액션을 남기거나 바꿉니다.

```json
{ "type": "RICE" }
```

- 보낸 리액션으로 설정만 함. 같은 값으로 다시 보내도 그대로 (재시도해도 안전)
- 같은 리액션을 다시 눌러 취소하는 동작은 프론트가 DELETE를 부름
- 에러: `CERT_002` (404), `ROOM_002` (403, 인증이 있는 방의 멤버 아님), `REACT_001` (403, 본인 인증), `REACT_002` (409, 투표 시작 후), `COMMON_002` (400, 없는 `type`)

### DELETE /api/certifications/{certificationId}/reactions/me

REACT-01. 리액션을 취소합니다.

- 남긴 리액션이 없어도 성공 (재시도해도 안전)
- 확인 순서: 없는 인증(`CERT_002`) → 방 멤버 아님(`ROOM_002`) → 남긴 리액션 없음(성공) → 본인 인증(`REACT_001`) → 투표 시작 후(`REACT_002`). 그래서 투표 시작 뒤라도 남긴 리액션이 없으면 성공
- 에러: `CERT_002` (404), `ROOM_002` (403), `REACT_001` (403), `REACT_002` (409)

## 8. 콕

### POST /api/rooms/{roomId}/pokes

POKE-01. 같은 방 멤버를 콕 찌릅니다. 인증 여부와 회차 상태에 상관없이 언제나 보낼 수 있습니다. 연타는 화면에서 1~2초 모아 횟수로 보내고, 서버는 받은 횟수를 더합니다.

```json
// request
{ "toMemberId": 12, "count": 5 }

// response 201 (data 없음)
```

- `count`: 요청 한 번에 1~30. 앱 안 누적 횟수에는 제한이 없음
- 받은 콕 수는 받는 사람의 오늘 탭 `me.receivedPokeCount`로 보임. 누가 찔렀는지는 보내지 않음. 찌른 사람에게도 보일지는 [미정] Q-55
- 받는 사람·방·회차 기준 처음 콕만 푸시를 보냄 (POKE-02). 이미 보낸 푸시의 숫자는 바꿀 수 없어서 늘어나는 횟수는 앱 안에서만 보임
- 에러: `POKE_001` (400, 본인), `ROOM_009` (404, 받는 사람이 이 방 현재 멤버 아님), `ROOM_002` (403), `ROOM_005` (404), `COMMON_002` (400, `count` 범위)

## 9. 투표 · 결과 · 톡톡

> 담당자 확인 (최신 디자인과 다른 부분)
> - "오늘은 모두 보살" 선택지가 디자인에 없음 (VOTE-02)
> - 동점이면 "뽑힌 보살이 없어요" (p97). 명세는 목탁 수로 지목 (RESULT-01)
> - 받은 톡톡이 "0/3번", "해린 보살의 톡톡!"으로 보낸 사람 단위 표시 (p110~117). 받은 톡톡을 직접 수행하는 이마받기 변경(기획 회의)은 아직 반영 전
> - 방어권을 결과 화면에서 쓰는 흐름 (p96)
> - 다음 회차 투표가 열려 닫힌 투표의 결과 화면 ([미정] Q-56)
> - `roundDate`를 화면에서 쓰는지

### GET /api/rooms/{roomId}/vote

VOTE-05. 투표 탭 상태와 화면 데이터입니다.

```json
{
  "state": "VOTING",
  "roundId": 100, "roundDate": "2026-11-04",
  "closesAt": "2026-11-06T05:00:00",
  "votedCount": 2, "voterCount": 4,
  "candidates": [
    {
      "memberId": 12, "nickname": "지수", "color": "MINT", "equippedItemCodes": [],
      "certification": { "result": "FAILED", "memo": "치킨 시켰어요", "spentAmount": 23000, "imageUrl": null },
      "moktakCount": 2, "isSelectable": true, "isResting": false
    }
  ]
}
```

| state | 조건 |
|---|---|
| `UNDER_THREE` | 방 인원 3명 미만 |
| `BEFORE_FIRST_VOTE` | 지난 결과가 없고 투표 전 |
| `NOT_IN_LIST` | 투표 시작 후 들어와 명단에 없음 |
| `NO_VOTING_RIGHT` | 명단에 있지만 인증하지 않아 투표권이 없음 |
| `VOTING` | 투표 중, 아직 투표 안 함 |
| `VOTED` | 투표한 뒤 마감 전 |
| `RESULT_UNSEEN` | 마감 후 결과를 아직 안 봄 |
| `RESULT_SEEN` | 결과를 본 뒤 다음 투표 전 |

- `BEFORE_FIRST_VOTE`: 나를 뺀 미인증 멤버 수와 첫 번째 미인증 멤버를 함께 보냄 (순서는 [미정] Q-25)
- `closesAt`: 늦어도 닫히는 시각 (회차 날짜 이틀 뒤 05:00). 다음 회차가 먼저 전원 인증하면 그때 닫힘
- `voterCount`: 투표권 있는 인원 (명단 중 인증한 멤버)
- `candidates`: `VOTING`일 때만. `certification`이 `null`이면 인증 안 함
- 투표 중 득표 수는 보내지 않음
- 투표 중 3명 미만이 될 때 상태는 [미정] Q-34
- 에러: `ROOM_002` (403), `ROOM_005` (404)

### POST /api/rounds/{roundId}/votes

VOTE-03. 후보 또는 "오늘은 모두 보살"에 투표합니다. `targetMemberId`가 `null`이면 모두 보살입니다.

```json
// request
{ "targetMemberId": 12 }

// response 201
{ "riceEarned": 5, "potEarned": 5, "isClosed": false }
```

- `isClosed`: 이 표로 투표권 있는 명단 전원이 투표해 마감됨
- 에러: `ROUND_001` (404), `ROOM_002` (403), `VOTE_001` (409, 투표 중 아님), `VOTE_002` (403, 명단 밖), `VOTE_003` (403, 투표권 없음), `VOTE_004` (409, 이미 투표함), `VOTE_005` (400, 본인 선택), `VOTE_006` (400, 선택할 수 없는 후보), `COMMON_002` (400)

### GET /api/rounds/{roundId}/result

RESULT-02. 결과 화면입니다.

```json
{
  "roundId": 100, "roundDate": "2026-11-04", "resultType": "PICKED",
  "picked": {
    "memberId": 12, "nickname": "지수", "color": "MINT", "equippedItemCodes": [], "isLeft": false,
    "certificationResult": "FAILED", "spentAmount": 23000
  },
  "voteCounts": [ { "memberId": 12, "nickname": "지수", "count": 3, "isLeft": false } ],
  "allBosalCount": 1,
  "isPickedMe": false,
  "isMyDefenseUsed": false,
  "canTap": true,
  "isTapExpired": false,
  "tappedMemberCount": 2
}
```

- 누가 누구에게 투표했는지는 보내지 않음
- `canTap`: `PICKED`이고 내가 지목된 사람이 아니고 아직 톡톡하지 않음
- `tappedMemberCount`: 나를 뺀, 그 회차 톡톡을 끝낸 멤버 수
- `picked`: `PICKED`, `DEFENDED`일 때만. 나머지는 `null`
- `picked.certificationResult`, `picked.spentAmount`: 결과 화면(디자인 p91)에 씀. 인증 안 했으면 둘 다 `null`, `ENDURED`면 `spentAmount`가 `null`
- 에러: `ROUND_001` (404), `ROOM_002` (403), `RESULT_001` (409, 아직 발표 전)
- "모두 보살"이 과반이 아닐 때 지목은 [미정] Q-22

### POST /api/rounds/{roundId}/taps

RESULT-03. 톡톡을 끝낼 때 한 번 보냅니다. 0번(건너뛰기)도 보냅니다.

```json
{ "count": 7 }
```

- `count`: 0~10
- 에러: `ROUND_001` (404), `ROOM_002` (403), `TAP_001` (409, 톡톡할 수 없는 결과), `TAP_002` (403, 지목된 본인), `TAP_003` (409, 이미 톡톡함), `TAP_004` (409, 톡톡 시간 지남), `COMMON_002` (400, `count` 범위)

### GET /api/rounds/{roundId}/taps/received

RESULT-03. 지목된 사람이 아직 재생하지 않은 받은 톡톡입니다.

```json
{
  "taps": [
    { "nickname": "민지", "color": "PINK", "count": 10 },
    { "nickname": "도윤", "color": "YELLOW", "count": 4 }
  ]
}
```

- `taps`: `count`가 0인 행(건너뛰기)은 빼고 보냄
- 에러: `ROUND_001` (404), `ROOM_002` (403), `TAP_005` (403, 지목된 사람이 아님)

### PUT /api/rounds/{roundId}/taps/received/played

RESULT-03. 받은 톡톡 재생 완료를 저장합니다.

- 이미 재생한 톡톡이면 바꾸지 않고 성공 (재시도해도 안전)
- 에러: `ROUND_001` (404), `ROOM_002` (403), `TAP_005` (403)

## 10. 한 솥

> 담당자 확인
> - 방 만들 때 목표 300·450·700석 선택 (p23), 완성 후 방장이 연장·종료 (p41~43). 11/8 회의의 방 유효기간(목표 기간이 끝나도 연장·폭파)과 같이 디자인 보완 중이라 아직 반영 전

### GET /api/rooms/{roomId}/pots/{seq}

POT-01. 다 채운 솥의 축하 화면입니다. `seq`는 몇 번째 솥인지이고, 보통 `room.potCompletedCount`를 넣습니다.

```json
{
  "seq": 1,
  "goalAmount": 300,
  "rewardAmount": 30,
  "contributions": [
    { "rank": 1, "memberId": 11, "nickname": "민지", "color": "PINK", "equippedItemCodes": [], "amount": 84, "isLeft": false },
    { "rank": 2, "memberId": 12, "nickname": "지수", "color": "MINT", "equippedItemCodes": [], "amount": 77, "isLeft": false }
  ]
}
```

- `rewardAmount`: 멤버 전원이 받은 개인 공양미
- `contributions`: 그 솥에 넣은 석이 많은 순. 인증 삭제로 회수한 석은 뺌
- 같은 석이면 같은 순위(예: 1, 1, 3). 같은 순위 안에서는 그 솥에 먼저 넣은 멤버가 앞
- 나간 멤버는 `isLeft: true`로 "떠난 보살" 표시
- 에러: `POT_001` (404, 없거나 아직 다 채우지 않은 솥), `ROOM_002` (403), `ROOM_005` (404)

## 11. 방어권

> 담당자 확인
> - 디자인은 지목된 뒤 결과 화면에서 사용 (p92, p96). 이대로면 방 선택 API가 없어지고 사용 API·조건이 바뀜
> - 곳간 조회의 `usingRoomName`, `isUsedThisWeek`는 이 흐름이 정해질 때 같이 정리
> - [미정] Q-20, Q-21, Q-28

### POST /api/defense-tickets

SHIELD-01. 공양미 100석으로 방어권을 삽니다.

```json
// response 201
{ "riceBalance": 20 }
```

- 에러: `STORE_001` (409, 공양미 부족), `DEFENSE_001` (409, 이미 보유)

### GET /api/defense-tickets/rooms

SHIELD-02. 방어권을 쓸 방을 고르는 화면입니다.

```json
{
  "rooms": [
    { "roomId": 1, "name": "배달 끊기 방", "status": "AVAILABLE" },
    { "roomId": 2, "name": "쇼핑 참기 방", "status": "NOT_CERTIFIED" }
  ]
}
```

- `status`: `AVAILABLE` `NOT_CERTIFIED` `VOTING` `USED_THIS_WEEK` `RESTING` `NO_VOTE_ROOM`
- 방어권이 없어도 방 목록은 보내고, 쓰는 순간 `DEFENSE_002`로 막음

### POST /api/rooms/{roomId}/defenses

SHIELD-02. 그 방의 현재 회차에 방어권을 씁니다. 쓰는 순간 소모됩니다.

- 마지막 인증자의 유예 시간은 [미정] Q-28
- 막힌 사유마다 code가 다르고, 방 선택 화면의 `status`와 짝을 이룸
- 에러: `DEFENSE_002` (409, 방어권 없음), `DEFENSE_003` (409, 인증 전 = `NOT_CERTIFIED`), `DEFENSE_004` (409, 투표 중 = `VOTING`), `DEFENSE_005` (409, 이번 주 사용 완료 = `USED_THIS_WEEK`), `DEFENSE_006` (409, 휴식 중 = `RESTING`), `DEFENSE_007` (409, 3명 미만 방 = `NO_VOTE_ROOM`), `ROOM_002` (403), `ROOM_005` (404)
