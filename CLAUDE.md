# CLAUDE.md

참아보살 백엔드 레포의 AI 작업 규칙입니다. 코드·깃 규칙은 [`docs/CONVENTION.md`](docs/CONVENTION.md)를 따릅니다.

## 기준 문서

- [`docs/spec/`](docs/spec/README.md): 맞는 동작의 유일한 기준
- `docs/plans/`: 작업별 계획서
- [`docs/spec/open-questions.md`](docs/spec/open-questions.md): 기획에 확인할 질문

## 프로젝트 정보

- Java 21, Spring Boot 4, JPA, MySQL (테스트는 H2)
- 테스트: `./gradlew test`
- 인증은 Spring Security와 JWT를 쓴다. 공개 경로는 `SecurityConfig`에서 관리한다.
- 앱인토스 로그인은 `prod`에서만 실제 호출한다. 로컬은 테스트 토큰을 쓴다.
- 스키마 마이그레이션 도구는 쓰지 않는다.

## 작업 규칙

- 기능 구현은 `/spec-plan <기능 ID>`로 계획서를 확정한 뒤 `/spec-build <기능 ID>`로 진행한다.
- 명세에 없는 규칙을 만들지 않는다. 필요해 보이면 구현하지 말고 사람에게 알린다.
- `[미정]` 정책은 임의로 정하지 않고 `open-questions.md`와 계획서의 열린 질문에 올린다.
- 정책이 바뀌면 코드보다 명세를 먼저 고친다.
- 명세 변경은 관련 코드와 같은 PR에 넣는다.
- 시나리오마다 테스트를 하나씩 만든다.
- 사람이 요청하지 않으면 커밋·푸시·PR 생성을 하지 않는다.

## 구현 규칙

`.coderabbit.yaml`의 리뷰 기준을 구현 단계부터 지킨다.

아래 규칙은 `ArchitectureTest`와 `ErrorCodeSnapshotTest`가 검사한다. 위반하면 테스트 실패 메시지를 보고 고친다.

- 현재 시각은 주입받은 `Clock`으로 구한다.
- 엔티티는 `BaseEntity`를 상속하고, enum 필드는 `@Enumerated(EnumType.STRING)`으로 저장한다.
- DTO는 record로, API 컨트롤러는 `ResponseEntity<Response<T>>`를 반환하고 요청 바디는 Spring의 `@RequestBody`로 받는다.
- 이미 정해진 에러 `code`는 바꾸지 않는다. 새 `code`는 `src/test/resources/error-codes.snapshot`에 추가한다.

아래 규칙은 자동 검사가 없으므로 직접 지킨다.

- 시간 시나리오 테스트는 고정된 `Clock`으로 작성한다.
- 조회 전용 서비스 메서드는 `@Transactional(readOnly = true)`를 쓴다.
- 인원 수·중복 확인처럼 "확인 후 저장"하는 코드는 락이나 UNIQUE 제약으로 동시 요청을 막는다.
- 외부 API(앱인토스, Discord)는 트랜잭션 안에서 호출하지 않는다.

## 하네스

- 작업을 끝내려 하면 Stop 훅(`.claude/hooks/run-tests.sh`)이 `./gradlew test`를 돌린다. 실패하면 결과를 보고 고친다.
- 테스트만 먼저 작성하는 단계처럼 실패가 의도된 경우에는 구현하지 말고 그 사실을 보고하고 끝낸다.
- 검사를 통과시키려고 `ArchitectureTest`, `ErrorCodeSnapshotTest`, 스냅샷의 기존 줄을 고치거나 지우지 않는다. 규칙을 바꿔야 하면 사람에게 알린다.
- 같은 실수가 반복되면 이 문서에 문장을 더하기 전에, 그 실수를 잡는 테스트나 규칙을 추가할 수 있는지 먼저 검토한다.
