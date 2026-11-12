# ROOM-06 결과 · 한 솥 확인

## 범위
- 구현 기능: ROOM-06 (이번에는 한 솥 확인 `seen-pot`만)
- 참고 기능: POT-01 (완성 횟수, 축하 1번만), RESULT-02 (결과 확인 기준), ROOM-02 (오늘 탭의 안 본 축하 여부), INVITE-03 (들어올 때 확인 상태 채움)

## 명세 점검
- 상태 전이: 멤버의 `seen_pot_count`는 늘어나기만 한다. 저장값보다 작거나 같은 값은 무시한다 (5번).
- 동시성: 같은 멤버의 확인 요청 2개가 동시에 오면 큰 값이 저장된 뒤 작은 값이 덮어쓸 수 있다. "저장값보다 클 때만 바꾸는" 조건부 update 한 문장으로 막는다. 완성 횟수는 늘기만 해서 락 없이 읽은 값으로 "완성 횟수보다 큰지" 비교해도 통과한 값은 계속 유효하다 (코드리뷰 반영, 처음 계획은 `rooms` 행 락).
- 권한: 그 방의 현재 멤버만 (`ROOM_002`, 1번). 삭제된 방은 `ROOM_005`.
- 외부 연동 실패: 없음
- 제외 범위
  - 결과 확인 `seen-result` (2~4번, ROOM-06-1~3): 회차·결과 테이블이 없어 RESULT-02 구현 때 한다.
  - ROOM-06-4, ROOM-06-5의 "오늘 탭의 안 본 축하 여부"는 ROOM-02가 없어서 저장값만 확인한다. 응답 확인은 ROOM-02 구현 때 한다.
  - 완성 횟수를 올리는 것은 POT-01(팀원)이라 테스트에서 값을 직접 넣는다.
  - 그래서 구현 후에도 ROOM-06은 `미구현`으로 둔다.

## 명세 변경
- `room-membership.md` ROOM-06 5번, `api.md`: 음수도 `COMMON_002`로 돌려준다 (`[구현 결정]`, 사람 확인 2026-11-12)
- `openapi.yaml` 초안: `seenPotCount`의 `minimum`을 1에서 0으로 (코드와 맞춤)
- 상태: 위 이유로 `미구현`. `api.md` 목록은 `seen-pot`만 구현 체크한다.

## 설계

### API 계약
- `PUT /api/rooms/{roomId}/members/me/seen-pot`
- 요청: `{ "seenPotCount": 2 }`
- 응답: `200`, `Response<Void>`
- 에러 코드: `ROOM_002` (403), `ROOM_005` (404), `COMMON_002` (400, `field: seenPotCount`, 현재 완성 횟수보다 큼·음수·값 없음), `COMMON_008` (401)

### 엔티티
- 새 테이블·컬럼 없음
- `RoomMemberRepository.raiseSeenPotCount`: `seen_pot_count < 요청 값`일 때만 바꾸는 조건부 update

### 주요 결정
- **처리 순서** (`@Transactional`): 방 조회 → 삭제된 방이면 `ROOM_005` → 내 현재 멤버 행, 없으면 `ROOM_002` → 요청 값이 방의 `pot_completed_count`보다 크면 `COMMON_002` → 조건부 update.
- **입력 검증:** DTO에서 `@NotNull`, `@Min(0)`으로 막아 `COMMON_002`. 완성 횟수는 0부터 늘기만 해서 음수는 잘못된 입력이다 (예외의 "입력값 오류"). 무시해도 데이터는 안전하지만 잘못된 요청을 400으로 드러낸다 (사람 확인, 2026-11-12).
- **락을 쓰지 않는 이유:** ERD의 `rooms` 행 락은 닉네임·인원처럼 방 안에서 겹치면 안 되는 값을 위한 것이다. 확인 상태는 내 행만 바뀌어 방 전체를 막을 필요가 없다. 방 행 락을 잡으면 축하를 볼 때마다 한 솥 완성·입장·나가기와 순서를 기다리게 된다.
- **응답:** 저장 여부와 상관없이 200. 출력이 없다.

## 규칙 점검
- `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음

## 할 일
- [x] `RoomMemberRepository.raiseSeenPotCount` (조건부 update)
- [x] 요청 DTO `RoomSeenPotUpdateRequest`, 서비스, 컨트롤러 `PUT /api/rooms/{roomId}/members/me/seen-pot`
- [x] 테스트: ROOM-06-4, ROOM-06-5 (저장값까지), 음수 요청 `COMMON_002` (시나리오 없음)
- [x] 동시 요청에서 작은 값이 덮어쓰지 않는지 확인하는 테스트와 검증
- [x] `api.md` 목록 `seen-pot` 구현 체크

## 열린 질문
- 없음

## 자기 점검

| 처리 규칙 | 시나리오 | 테스트 |
| --- | --- | --- |
| 1 (현재 멤버만), 삭제된 방 | 없음 | `RoomSeenPotUpdateTest` (시나리오 없는 테스트, 코드리뷰 반영) |
| 5 (저장값보다 클 때만 저장) | ROOM-06-4, ROOM-06-5 | `RoomSeenPotUpdateTest` |
| 5 (완성 횟수보다 크면 `COMMON_002`) | 없음 | `RoomSeenPotUpdateTest` (시나리오 없는 테스트) |
| 입력 검증 (음수) | 없음 | `RoomSeenPotUpdateTest` (시나리오 없는 테스트) |
| 동시 요청 | 없음 | `RoomSeenPotUpdateTest` (시나리오 없는 테스트) |
| 2, 3, 4 (결과 확인) | ROOM-06-1 ~ 06-3 | 없음 (RESULT-02 이후) |

- 테스트가 없는 시나리오: ROOM-06-1 ~ 06-3 (결과 확인, 회차·결과 테이블 없음). ROOM-06-4·5는 "오늘 탭의 안 본 축하 여부" 대신 저장값을 확인했다. ROOM-02 구현 때 응답을 확인한다. 그래서 ROOM-06은 `미구현`으로 두고 `api.md` 목록은 `seen-pot`만 구현 체크했다.
- 시나리오가 없는 처리 규칙: 1번(현재 멤버만, `ROOM_002`), 삭제된 방(`ROOM_005`), 5번의 `COMMON_002`. 시나리오는 없지만 모두 테스트를 추가했다.
- 명세에 없는 규칙을 만든 곳: 음수 `COMMON_002` (사람 확인). 코드리뷰 지적대로 명세 ROOM-06 5번과 `api.md`, OpenAPI 초안에도 같은 PR에서 반영했다.
- 계획에 없던 변경 (코드리뷰 반영)
  - 방 행 락 대신 조건부 update로 동시 요청을 막는다.
  - OpenAPI 초안의 `seenPotCount`가 `minimum: 1`이어서 코드와 같은 `minimum: 0`으로 고쳤다.
  - 조건부 update에 `left_at IS NULL`을 넣어, 확인 요청과 나가기가 겹쳐도 나간 행은 바꾸지 않는다. 마지막 멤버가 나가 방이 삭제되는 경우도 같이 걸린다.
  - bulk update는 auditing을 거치지 않아 `updated_at`을 쿼리에서 직접 넣는다.
  - `RoomMember`에 `@DynamicUpdate`를 붙였다 (PR 코드리뷰 반영). 락을 쓰지 않으면서, 닉네임 수정·나가기처럼 멤버를 읽고 저장하는 다른 트랜잭션이 모든 컬럼을 다시 써서 올라간 확인 횟수를 옛 값으로 되돌릴 수 있었다. 이 순서를 그대로 만든 테스트가 `@DynamicUpdate` 없이 3번 모두 실패하고, 붙이면 통과한다.
- 동시성 검증: 조건부 update 대신 "읽고 비교한 뒤 저장"하게 바꾸면 동시 요청 테스트가 5번 모두 실패하고, 되돌리면 통과한다. 요청 수는 커넥션 풀(10개)에 맞췄다.
