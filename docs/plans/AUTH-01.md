# AUTH-01 로그인 · 토큰 인증

## 범위
- 구현 기능: AUTH-01, AUTH-02, AUTH-03
- 참고 기능: 없음

토큰 발급과 검증은 같은 형식과 키를 공유하고, 발급만 만들면 사용할 곳이 없어 함께 구현합니다. 테스트 토큰도 같은 발급 로직을 쓰고 프론트 연동에 필요해 포함합니다.

## 명세 점검
- 상태 전이: 사용자는 `활성 → 삭제 → 복구` 또는 `삭제 → 정리 → 신규`로 바뀐다. `deleted_at + 6개월` 시각까지는 복구, 그 뒤는 신규로 처리한다.
- 동시성: 같은 `anonKey` 첫 로그인은 `toss_anon_key` UNIQUE로 막는다. 복구와 정리가 겹치는 경우도 같은 제약으로 처리한다.
- 권한: 로그인, 헬스체크, Swagger, 에러 코드 문서만 토큰 없이 접근한다. 삭제한 사용자의 토큰은 거절한다. 테스트 토큰은 기능이 켜져 있고 비밀값이 맞을 때만 발급한다.
- 외부 연동 실패: 교환 API는 오류도 HTTP 200으로 응답할 수 있어 `resultType`을 먼저 확인한다. 호출은 트랜잭션 밖에서 한다.
- 제외 범위: 닉네임 설정, 방 입장, 약관 동의 기록, 리프레시 토큰, 내 데이터 삭제 API, 정리 배치, 테스트 사용자 삭제

## 명세 변경
- `docs/spec/auth.md`: 처리 규칙 5, 6번에 6개월 경계 명시 (`deleted_at + 6개월` 시각까지 복구)
- `docs/spec/auth.md`: AUTH-02 처리 규칙 1번의 토큰 없이 호출 가능한 API에 `POST /api/auth/test-token` 추가
- `docs/spec/auth.md`: AUTH-03 테스트 토큰 추가
  - AUTH-03-1 켜져 있고 비밀값이 맞으면 테스트 사용자를 만들고 토큰 발급
  - AUTH-03-2 같은 이름으로 다시 요청하면 같은 사용자의 토큰 발급
  - AUTH-03-3 비밀값이 틀리거나 없으면 `COMMON_008`
  - AUTH-03-4 기능이 꺼져 있으면 비밀값이 맞아도 `COMMON_001`
- `docs/spec/erd.md`: `users.toss_anon_key`를 NULL 허용으로 변경, 6개월 정리는 행을 남기고 키만 NULL로 비우도록 수정

## 설계

### API 계약
- `POST /api/auth/login`
- 요청: `{ "authorizationCode": "..." }`, `@NotBlank`
- 응답: `200`, `Response<AuthLoginResponse>`, `{ "accessToken": "...", "expiresIn": 3600, "isNewUser": true }`
- 에러 코드: `AUTH_001` (400), `AUTH_002` (502), `COMMON_002` (400)
- 토큰이 필요한 API의 공통 에러: `COMMON_008` (401)
- `POST /api/auth/test-token`
- 요청: 헤더 `X-Test-Token-Key`, 본문 `{ "testUser": "a" }`, `testUser`는 영문 소문자·숫자 1~8자
- 응답: `200`, `Response<AuthTestTokenResponse>`, `{ "accessToken": "...", "expiresIn": 86400 }`
- 에러 코드: `COMMON_001` (404, 기능 꺼짐), `COMMON_002` (400), `COMMON_008` (401, 비밀값 불일치)

### 엔티티
- `User` (`users`): `id`, `tossAnonKey`, `isPokePushEnabled`, `isActivityPushEnabled`, `deletedAt`
- `tossAnonKey`는 UNIQUE, NULL 허용. 정리된 사용자는 NULL로 남아 다른 테이블의 참조를 유지한다.
- `last_viewed_room_id`는 프론트 협의 6번과 `rooms` 구현 후 추가한다.

### 주요 결정

**앱인토스 교환 API**
- `POST https://apps-in-toss-api.toss.im/api-partner/v1/apps-in-toss/users/anon-key/exchange`
- 요청 본문은 `{ "code": "..." }`, 성공 응답은 `success.anonKey`를 쓴다.
- `resultType`이 `SUCCESS`가 아니면 실패로 처리한다.
- `errorCode 4011`은 `AUTH_001`, 나머지 실패와 HTTP 오류, 타임아웃은 `AUTH_002`로 변환한다.
- `TossAuthClient` 인터페이스로 분리해 테스트에서는 가짜 구현을 쓴다.

**mTLS**
- Spring SSL Bundle(PEM)을 `RestClient`에 연결한다. 발급 파일은 `.crt`, `.key`이고 PEM 형식을 확인했다.
- 인증서 CN은 `chamabosal`, 만료일은 2027-12-01이다. 만료 전에 재발급한다.
- 연결 3초, 응답 5초 타임아웃을 둔다. 재시도는 하지 않는다. 일회용 코드라 재사용이 실패할 수 있다.
- 인증서와 키는 서버 `/etc/chamabosal/toss/`에 두고 컨테이너에 읽기 전용으로 연결한다. 경로는 환경변수로 받는다.
- 컨테이너는 `spring` 사용자로 실행되므로, 이 사용자가 읽을 수 있게 파일 권한을 맞춘다.
- `.gitignore`, `.dockerignore`에 `*.pem`, `*.key`, `*.crt`를 추가한다.

**프로필별 설정**
- `prod`: 인증서 경로와 `JWT_SECRET`을 기본값 없이 필수로 두어, 누락되면 앱이 기동되지 않는다.
- 로컬과 테스트: 인증서 없이 기동한다. 실제 `TossAuthClient`는 `prod`에서만 등록하고, 그 외에는 호출 시 `AUTH_002`를 돌려주는 구현을 쓴다.
- 로컬 로그인은 테스트 토큰으로 대신한다. 테스트 코드는 가짜 `TossAuthClient`와 테스트 전용 서명 키를 쓴다.

**로그인 처리**
- `AuthService.login`은 트랜잭션 없이 앱인토스를 호출하고, 사용자 조회·생성·복구만 별도 트랜잭션에서 처리한다.
- 동시 생성으로 UNIQUE 위반이 나면 다시 조회해 기존 사용자로 처리한다.
- 6개월이 지난 사용자는 기존 행의 `tossAnonKey`를 NULL로 비운 뒤 같은 키로 새 사용자를 만든다.
- 시각 비교는 주입받은 `Clock`을 쓴다.

**토큰**
- JWT HS256, `sub`는 사용자 ID, 유효 시간 3600초
- 서명 키는 환경변수 `JWT_SECRET`으로 받는다.
- 라이브러리는 `jjwt`를 쓰고, Spring Boot 4 호환 버전을 구현 때 확인한다.

**테스트 토큰**
- 환경변수 `AUTH_TEST_TOKEN_ENABLED`(기본 `false`), `AUTH_TEST_TOKEN_KEY`로 제어한다.
- 켜져 있는데 비밀값이 비어 있으면 앱이 기동되지 않게 한다.
- 비밀값은 `MessageDigest.isEqual`로 비교한다.
- 테스트 사용자의 `tossAnonKey`는 `test-{testUser}`로 저장한다. 실제 `anonKey`와 겹치지 않게 접두사를 고정한다.
- 일반 로그인과 같은 토큰 발급 로직을 쓰고, 유효 시간만 24시간으로 둔다.
- 출시 전에 끄고, 이후 코드를 제거한다.

**Spring Security**
- `SecurityConfig`: 세션 없음, CSRF·폼 로그인·HTTP Basic 비활성화
- 공개 경로: `POST /api/auth/login`, `POST /api/auth/test-token`, `/actuator/health`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/docs/error-codes`
- `JwtAuthenticationFilter`: 토큰 검증 후 사용자를 조회하고, 없거나 `deletedAt`이 있으면 거절한다.
- 인증 실패는 `COMMON_008`, 권한 실패는 `COMMON_009`를 `ErrorResponse` 형식으로 돌려준다.
- 기존 `CorsConfig`는 `CorsConfigurationSource`로 바꾸고 `http.cors()`에 연결한다.
- 토큰 없이 없는 경로를 호출하면 `COMMON_001` 대신 `COMMON_008`이 된다. 기존 예외 처리 테스트가 영향을 받는지 확인한다.

**배포**
- `docker-compose.yml`: 환경변수와 인증서 볼륨 추가
- `.github/workflows/cd.yml`: `JWT_SECRET`을 배포 환경변수와 필수값 검증에 추가. `AUTH_TEST_TOKEN_ENABLED`, `AUTH_TEST_TOKEN_KEY`도 배포 환경변수에 추가
- 서버 인증서 배치와 GitHub Secret 등록은 사람이 직접 진행한다.

## 규칙 점검
- `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음

## 할 일
- [x] 의존성, 환경 설정, 인증서 파일 제외 규칙
- [x] `User` 엔티티와 Repository
- [x] `AuthErrorCode`, `TossAuthClient`, mTLS 연결
- [x] JWT 발급·검증
- [x] 로그인 서비스: 생성, 복구, 정리, 동시 요청 처리
- [x] `POST /api/auth/login`
- [x] Spring Security, 인증 필터, 에러 응답, CORS 이전, Swagger Bearer 설정
- [x] `POST /api/auth/test-token`
- [x] `docker-compose.yml`, `cd.yml` 배포 설정
- [x] 테스트: AUTH-01-1 ~ AUTH-01-7, AUTH-02-1 ~ AUTH-02-4, AUTH-03-1 ~ AUTH-03-4, 기존 CORS 테스트

## 열린 질문
- Q-16 액세스 토큰만 사용: 프론트 답변 대기. 구현을 막지는 않지만, 재로그인 때 동의 화면이 뜨면 재검토
- Q-11 약관 고지: 답변에 따라 `users`에 저장 항목이 늘 수 있음. 구현을 막지는 않음
- 프론트 로컬 환경의 인증 코드 발급 가능 여부: 답변 대기. 테스트 토큰은 백엔드 테스트에도 필요해 답변과 관계없이 구현

## 자기 점검

| 시나리오 | 테스트 |
| --- | --- |
| AUTH-01-1 ~ AUTH-01-7 | `AuthLoginTest` |
| AUTH-02-1 ~ AUTH-02-4 | `AuthTokenTest` |
| AUTH-03-1 ~ AUTH-03-3 | `AuthTestTokenEnabledTest` |
| AUTH-03-4 | `AuthTestTokenDisabledTest` |

- 테스트가 없는 시나리오: 없음
- 시나리오가 없는 처리 규칙: AUTH-01 규칙 2, 4는 받는 값이 없다는 규칙이라 DTO 구조로 지킴. AUTH-03 규칙 4는 `AuthTestTokenServiceTest`로 확인
- 명세에 없는 규칙: 없음. 6개월 경계와 `users` 정리는 명세에 반영
- 재시도 없이는 AUTH-01-7과 `AuthServiceTest`가 실패하는 것을 확인
- 공양미, 아이템, 방어권 유지: 아직 테이블이 없어 같은 사용자 행 복구까지만 확인. 해당 기능 구현 때 다시 확인
- 앱인토스 실제 호출: 일회용 인증서로 `prod` 기동만 확인. 실제 교환은 배포 후 확인 필요
- 코드리뷰 반영: preflight 캐시 복구, 필터의 DB 장애 처리, 배포 전 인증서 읽기 검사, 테스트 토큰 재시도 공유, JWT 파서 재사용, 공개 경로의 405 응답, 검증 메시지
- 본문 형식 검사가 테스트 토큰 확인보다 먼저 실행되는 동작은 AUTH-03 규칙 7로 명세에 반영
