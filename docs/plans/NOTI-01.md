# NOTI-01 토스 알림 동의

## 범위
- 구현 기능: NOTI-01
- 참고 기능: USER-01 (내 정보의 `CERT_REMINDER` 동의 여부), USER-03 (앱 안 알림 설정), NOTI-02 (발송 조건), AUTH-02 (삭제한 사용자 토큰 거절)

같은 이슈(#58)의 USER-03은 계획서 없이 시나리오 테스트로 진행합니다. NOTI-01은 동시 요청 규칙(6번)이 있어 계획서를 씁니다.

## 명세 점검
- 상태 전이: 동의는 `없음 → 있음` 한 방향뿐이다. 거부(`agreementRejected`)는 저장하지 않으므로 `있음 → 없음`은 없다. 동의 철회는 토스 앱 설정에서 하고 서버는 알 수 없다 (ERD `push_agreements` 설명).
- 동시성: 같은 사용자의 같은 동의 요청 2개가 동시에 와도 1건만 저장한다 (6번). `users` 행 락을 잡고 확인한 뒤 저장하고, ERD의 `UNIQUE (user_id, type)`도 둔다.
- 권한: 로그인한 사용자 본인만. 다른 사용자를 지정하는 입력은 없다.
- 외부 연동 실패: 없음. 토스 동의 화면은 클라이언트가 호출하고 결과만 서버로 보낸다.
- 제외 범위
  - NOTI-01-1의 "내 정보의 `CERT_REMINDER` 동의 여부는 true다"는 USER-01(내 정보)이 없어서 이번에는 저장 여부만 확인한다. 응답 확인은 USER-01 구현 때 한다. 그래서 구현 후에도 NOTI-01은 `미구현`으로 둔다.
  - `CERT_REMINDER` 외 알림의 동의는 받지 않는다 (1번, Q-14 미정).

## 명세 변경
- 없음
- 상태: NOTI-01-1의 내 정보 확인이 남아 `미구현`으로 둔다. `api.md` 목록의 `구현`은 체크한다 (호출 가능한 API).

## 설계

### API 계약
- `POST /api/users/me/push-agreements`
- 요청: `{ "type": "CERT_REMINDER", "result": "newAgreement" }`
- 응답: `200`, `Response<Void>` (저장했든 안 했든 같음. 출력 없음)
- 에러 코드: `COMMON_002` (400, `field: type` 또는 `result`), `COMMON_008` (401)

### 엔티티
- `PushAgreement` (`push_agreements`): `id`, `user`(FK, NOT NULL), `type`(`PushAgreementType`), `agreedAt`
  - `@Table(uniqueConstraints = UNIQUE (user_id, type))`
  - `type`은 `@Enumerated(EnumType.STRING)` + `columnDefinition = "varchar(30)"` (ROOM-01에서 정한 enum 매핑 방식)
- `PushAgreementType`: `CERT_REMINDER` 하나. ERD의 나머지 값(`POKE` 등)은 동의를 받기로 정해지면(Q-14) 추가한다.

### 주요 결정
- **요청 검증:** `type`, `result`를 enum이 아니라 문자열로 받고 `@Pattern`으로 검사한다.
  - enum으로 받으면 `"POKE"` 같은 값이 JSON 변환 단계에서 `COMMON_003`이 되는데, 명세(2번, NOTI-01-4)는 `COMMON_002`를 요구한다.
  - `type`: 서비스가 `PushAgreementType` 값으로 검사한다 (지금은 `CERT_REMINDER`). DTO 정규식과 enum이 따로 놀지 않도록 기준을 enum 하나로 뒀다 (코드리뷰 반영). `result`: DTO `@Pattern`으로 `newAgreement`, `alreadyAgreed`, `agreementRejected`만 허용 (앱인토스 값 그대로).
- **저장 판단:** `agreementRejected`면 저장하지 않고 성공 (삭제한 사용자 확인은 다른 결과와 똑같이 먼저 한다). 나머지 두 값은 같은 처리: 동의가 없으면 저장, 있으면 그대로 둔다 (처음 동의 시각 유지, 5번).
  - `alreadyAgreed`인데 서버에 기록이 없을 수도 있다 (다른 기기에서 동의). 이때도 저장한다 (3번).
- **동시 요청:** `users` 행을 `PESSIMISTIC_WRITE`로 잡은 뒤 `existsByUser_IdAndType`로 확인하고 저장한다. ROOM-01과 같은 락이다. UNIQUE는 락을 빼먹은 코드에 대한 마지막 방어선이다.
- **삭제한 사용자:** 락을 잡은 뒤 삭제 상태면 `COMMON_008` (ROOM-01과 같은 처리).
- **시각:** `agreedAt`은 주입받은 `Clock`으로 구한다. 테스트는 고정 `Clock`.
- **앱 안 설정과 분리:** 동의를 저장해도 `users`의 알림 토글은 건드리지 않는다 (7번).
- **위치:** `domain/user` 아래에 `PushAgreement` 엔티티·리포지토리, `UserController`·`UserService`(USER-03과 같이).
- **이미 동의했는데 거부가 온 경우:** 기존 동의를 지우지 않는다. 서버는 토스 앱 설정 변경을 알 방법이 없고(웹훅·조회 API 없음), 꺼진 사람에게 보낸 알림은 토스가 막는다. 사람 확인 (2026-11-12)

## 규칙 점검
- `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음

## 할 일
- [x] `PushAgreement` 엔티티, `PushAgreementType`, 리포지토리 (UNIQUE 포함)
- [x] 요청 DTO `PushAgreementCreateRequest` (`@Pattern` 검증)
- [x] 서비스: 사용자 락 → 결과 판단 → 없으면 저장
- [x] 컨트롤러 `POST /api/users/me/push-agreements`
- [x] 테스트: NOTI-01-1 (저장 여부까지), NOTI-01-2 ~ NOTI-01-5
- [x] 락을 빼면 NOTI-01-5가 실패하는지 확인
- [x] `api.md` 목록 구현 체크

## 열린 질문
- 없음

## 자기 점검

| 처리 규칙 | 시나리오 | 테스트 |
| --- | --- | --- |
| 1, 2 (`CERT_REMINDER`만, 나머지는 `COMMON_002`) | NOTI-01-4 | `PushAgreementCreateTest` |
| 3 (`newAgreement`, `alreadyAgreed`면 저장) | NOTI-01-1, 시나리오 없음(기록 없을 때 `alreadyAgreed`, 코드리뷰 봇 반영) | `PushAgreementCreateTest` |
| 4 (거부는 저장 안 함) | NOTI-01-3 | `PushAgreementCreateTest` |
| 5 (처음 동의 시각 유지) | NOTI-01-2 | `PushAgreementCreateTest` |
| 6 (동시 요청에도 1건) | NOTI-01-5 | `PushAgreementCreateTest` |
| 7 (앱 안 알림 설정은 그대로) | 없음 | 없음 |

- 테스트가 없는 시나리오: NOTI-01-1의 "내 정보의 `CERT_REMINDER` 동의 여부는 true"는 USER-01이 없어 저장 여부만 확인했다. 그래서 NOTI-01은 `미구현`으로 두고 `api.md` 목록만 구현 체크했다. USER-01을 구현할 때 이 부분을 테스트하고 `구현됨`으로 바꾼다.
- 시나리오가 없는 처리 규칙: 7번 (동의를 저장해도 앱 안 알림 설정은 그대로). 서비스가 `users`의 알림 토글을 건드리지 않아 지켜진다.
- 명세에 없는 규칙을 만든 곳: 없음. 거부가 와도 기존 동의를 지우지 않는 것은 4번("저장하지 않는다")을 그대로 읽은 것이고 사람 확인을 받았다.
- 계획에 없던 변경
  - `PushAgreementService`를 따로 두지 않고 USER-03과 같은 `UserService`에 넣었다. 둘 다 짧은 메서드 하나라 서비스를 나눌 이유가 없었다.
  - `alreadyAgreed`가 오면 응답 코드도 200이다. 저장 여부와 상관없이 항상 200 (사람 확인).
- 동시성 검증: 사용자 행 락을 빼면 NOTI-01-5가 3번 모두 실패하고(UNIQUE 위반), 되돌리면 통과하는 것을 확인했다.
- 같은 이슈의 USER-03은 계획서 없이 시나리오 테스트(`UserSettingsUpdateTest`, USER-03-1·2)로 진행했고 `구현됨`으로 바꿨다.
