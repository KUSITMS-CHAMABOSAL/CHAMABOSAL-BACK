# STORE-02 아이템 구매 (STORE-01 곳간 조회, STORE-03 착용 · 벗기)

## 범위
- 구현 기능: STORE-01, STORE-02, STORE-03
- 참고 기능: RICE-01·02 (장부, 표시 잔액), SHIELD-01·02 (사용자 행 락 안에서 잔액 확인 후 사용, 방어권 상태), USER-01 (내 정보 착용 목록), USER-05 3번 (내 데이터 삭제 때 아이템 삭제), ROOM-02 (오늘 탭 착용 목록, PR #116)
- 이번 PR에서 빼는 것 (다음 PR, 할 일이 10개를 넘어서, 사람 확인 2026-11-16): 오늘 탭·투표 후보·결과·한 솥 축하 응답의 `equippedItemCodes` 채우기와 STORE-03-5 (두 방의 오늘 탭에서 착용 확인). STORE-03은 그 PR에서 `구현됨`

## 명세 점검
- 상태 전이: 아이템은 미보유 → 보유(착용) → 보유(벗음) ↔ 보유(착용). 보유는 되돌릴 수 없음 (STORE-02 3번). 구매는 바로 착용으로 이어짐 (6번)
- 동시성
  - 구매: 사용자 전체 잔액을 읽고 쓰므로 방어권 구매·나발 심기와 같은 사용자 행 락 안에서 처리 (STORE-02 7번, 02-8·9). `UNIQUE (user_id, item_id)`로 같은 아이템 두 번 보유도 막음
  - 착용: 보낸 목록으로 통째로 바꾸므로 사용자 행 락 안에서 처리해 한 요청의 목록만 남게 함 (STORE-03 7번, 03-6)
  - 구매와 착용이 동시에 와도 같은 사용자 행 락으로 하나씩 처리됨
- 권한: 로그인한 본인 것만 다룸. 다른 사용자 지정 입력 없음
- 외부 연동 실패: 없음
- 제외 범위: 인앱 결제, 슬롯·아이템 추가(P1), 방마다 다른 착용, 미리 착용해 보기(클라이언트)

## 명세 변경
- `erd.md` items: "6종으로 시작"을 "디자인 데이터 기준, 지금은 산타 모자·루돌프 코 2종(모두 100석)"으로 고침. STORE-02 1번(Q-53 결정)과 어긋나서 명세 쪽에 맞춤. 시나리오(01-1의 6종, 02-2의 50석)는 테스트가 아이템을 직접 넣어서 그대로 둠
- `api.md`: 곳간 3개 API 구현 체크, 착용 API 확인 순서 추가

## 설계

### API 계약
- `GET /api/store` (STORE-01)
  - 응답: `riceBalance`, `defenseTicket { status: NONE|OWNED, isUsedThisWeek }`, `items[] { itemId, code, name, price, slot, isOwned, isEquipped }` (노출 순서)
  - `isUsedThisWeek`: 쓴 회차의 회차 날짜가 지금 회차 날짜가 속한 주(월~일)에 있음 (api.md)
  - 에러: `COMMON_008`
- `POST /api/items/{itemId}/purchases` (STORE-02)
  - 응답 201: `riceBalance`, `equippedItemIds` (구매 뒤 착용 중인 전체)
  - 확인 순서: `STORE_002`(없는 아이템, 404) → `STORE_003`(이미 보유, 409) → `STORE_001`(잔액 부족, 409)
- `PUT /api/users/me/equipped-items` (STORE-03)
  - 요청: `{ itemIds: [..] }` 0~3개
  - 응답: 없음 (`Response<Void>`)
  - 확인 순서: `COMMON_002`(목록 없음·3개 초과·같은 ID 중복) → `STORE_004`(없거나 보유하지 않은 아이템, 409) → `COMMON_002`(같은 슬롯 둘 이상) [구현 결정]

### 엔티티
- `Item` (`items`): code(UK), name, price, slot(`ItemSlot` enum: HAT/FACE/EXPRESSION, STRING 저장), displayOrder
- `UserItem` (`user_items`): user, item, isEquipped. `UNIQUE (user_id, item_id)`
- 장부: `RiceService.spend(user, price, RiceRefType.ITEM, itemId)` 재사용 (`PURCHASE`, 원인 `ITEM`)

### 주요 결정
- 아이템 데이터 넣기: 마이그레이션 도구가 없어서, 코드에 둔 카탈로그(code, name, price, slot, displayOrder)를 앱 시작 때 code 기준으로 없으면 넣고 있으면 고침. 테스트에서는 돌리지 않고 테스트가 아이템을 직접 넣음. 지금 카탈로그는 STORE-02 1번의 2종. 카탈로그에서 빼도 행은 지우지 않음 (보유 기록이 참조) [구현 결정, 사람 확인 2026-11-16]
- 구매: 아이템 확인 → 사용자 행 락 → 이미 보유 확인 → `UserItem` 저장(착용) → 같은 슬롯 착용 벗김 → 장부 차감. 잔액 부족 예외면 롤백되어 보유도 남지 않음
- 착용: 사용자 행 락 → 보유 목록을 읽어 검증 → 보낸 목록만 착용, 나머지 벗김
- 이번 주 사용 여부: 회차 날짜 기준 월요일~일요일, 쓴 회차(`used_round_id`)의 회차 날짜로 판단
- 내 데이터 삭제(USER-05 3번): 사용자 아이템 행을 바로 삭제. 아이템 카탈로그는 남음
- 내 정보(USER-01) `equippedItemCodes`: 이번 PR에서 채움 (내 코드, 착용 목록 조회 하나)
- 착용 코드 조회를 사용자 ID 목록으로 한 번에 읽는 메서드로 만들어, 다음 PR에서 화면 4곳이 같이 씀

## 규칙 점검
- `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음
  - 확인 후 저장(이미 보유·잔액)은 사용자 행 락과 UNIQUE로 막음
  - 새 에러 code `STORE_002`·`STORE_003`·`STORE_004`는 스냅샷에 추가
  - 조회(`GET /api/store`)는 `@Transactional(readOnly = true)`

## 할 일
- [ ] `Item`·`UserItem` 엔티티·리포지토리, `ItemSlot`
- [ ] 아이템 카탈로그와 시작 때 넣기
- [ ] 에러 코드 `STORE_002`·`STORE_003`·`STORE_004`, 스냅샷 추가
- [ ] `GET /api/store` 곳간 조회
- [ ] `POST /api/items/{itemId}/purchases` 구매
- [ ] `PUT /api/users/me/equipped-items` 착용
- [ ] 내 정보 `equippedItemCodes`, 내 데이터 삭제 때 사용자 아이템 삭제
- [ ] 명세: `erd.md` items, `api.md`, OpenAPI, STORE-01·02 `구현됨`
- [ ] 테스트: STORE-01-1~4, STORE-02-1~9, STORE-03-1~4·6·7, USER-05 아이템 삭제 확인

## 열린 질문
- 디자인의 전체 아이템 데이터(이름·코드·슬롯·가격·순서). Q-53으로 "디자인 데이터 기준"은 정해졌고, 지금 명세에는 2종만 있음. 새 질문은 만들지 않고 받는 대로 카탈로그에 추가 (구현을 막지 않음)

## 자기 점검
`/spec-build`가 구현 후 작성합니다.
