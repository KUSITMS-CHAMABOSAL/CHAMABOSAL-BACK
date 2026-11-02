# Convention

## 깃허브 컨벤션

### Prefix

| Prefix | 설명 |
| --- | --- |
| `feat` | 새로운 기능을 추가할 경우 |
| `fix` | 버그를 고친 경우 |
| `docs` | 문서 수정 |
| `refactor` | 코드 리팩토링 |
| `test` | 테스트 코드, 리팩토링 테스트 코드 추가 |
| `chore` | 주석 등 기타 작업 |
| `ci` | 빌드, CI 배포 작업 |
| `perf` | 성능 개선 |

### 브랜치 전략

```
main - 실제 배포되는 브랜치
dev  - 개발 통합 브랜치
```

- `dev` → `main`으로 머지 진행
- 작업 브랜치는 `dev`에서 생성하고 `dev`로 PR
- PR 생성·업데이트 시 CI(빌드·테스트) 실행
- 출시 전: `dev` 머지 → 서버 배포 (서버 1대 운영)
- 출시 후: CD 트리거를 `main`으로 전환해 `main` 머지 → 운영 서버 배포

### 브랜치 이름

```
prefix/#00-description-with-dash-english
```

### 커밋 메시지

```
prefix: 한국어 설명 (#issue)
```

- `prefix: 한국어 설명 (#이슈번호)` 형태이며, `:` 뒤에만 space가 있음
- subject는 어떤 작업을 했는지 명확하게 드러나야 하고, 너무 길지 않게 작성
- 본문은 한 줄당 72자 내로 작성
- 본문은 필요할 때만 1~3줄로 간단히 작성하고, 자세한 설명은 PR 본문에 작성
- 본문은 어떻게 변경했는지보다 무엇을, 왜 변경했는지를 설명

### 이슈 · PR

- 제목은 `prefix: 한국어 설명` 형태이며, `:` 뒤에만 space가 있음
- 템플릿
  - 이슈: [`.github/ISSUE_TEMPLATE/`](../.github/ISSUE_TEMPLATE)
  - PR: [`.github/PULL_REQUEST_TEMPLATE.md`](../.github/PULL_REQUEST_TEMPLATE.md)

### PR 리뷰 · 머지 기준

- 작업 브랜치 → PR → 코드리뷰 봇 피드백 반영 → 머지
- PR 올린 사람이 AI 봇 피드백을 반영한 뒤 직접 머지 (팀원 리뷰는 필수 아님)
- `dev` / `main`에 직접 push하지 않고 모든 변경은 PR로 머지 (브랜치 보호 규칙은 운영 배포 전 설정)

---

## 코드 컨벤션

### 패키지 구조 (도메인형)

```
src/main/java/{basePackage}/
├── domain/
│   └── {도메인명}/
│       ├── controller/
│       ├── dto/
│       │   ├── request/
│       │   └── response/
│       ├── entity/
│       ├── repository/
│       └── service/
└── global/
    ├── common/
    │   ├── entity/        # BaseEntity (생성·수정 시각)
    │   ├── exception/     # BaseErrorCode, BaseException, ErrorCode, GlobalExceptionHandler
    │   ├── response/      # Response, ErrorResponse
    │   ├── util/
    │   └── ErrorCodeViewController   # 에러코드 확인 페이지
    ├── config/            # SwaggerConfig, JpaAuditingConfig, ClockConfig 등
    ├── security/          # 인증 도입 시 (config, filter, handler 등)
    └── discord/           # Discord 에러 알림 (webhook)
```

- 도메인별로 폴더를 나누고, 그 안에 controller / dto / entity / repository / service를 둠
- 여러 도메인이 같이 쓰는 것은 `global`에 둠

### 네이밍 규칙

- 클래스: `PascalCase`, 메서드·변수: `camelCase`, 상수·Enum 값: `UPPER_SNAKE_CASE`
- DTO: 요청은 `{도메인}{동작}Request`, 응답은 `{도메인}{동작}Response` (예: `TestCreateRequest`, `TestCreateResponse`)
- 메서드는 동사로 시작: `createTest`, `getTest`, `getTests`, `updateTest`, `deleteTest`
- Repository는 Spring Data 규칙을 따름: `findBy...`, `existsBy...`
- boolean은 `is` / `has` / `can`으로 시작: `isDeleted`, `hasPermission`
- URL: `/api`로 시작, 소문자 + 복수형 명사, 단어 구분은 `-` (예: `/api/meetups/{meetupId}/participants`)
- DB 테이블·컬럼: `snake_case`

### DTO

- 요청·응답 DTO는 `record`로 작성 (Entity는 JPA 때문에 class)
- 검증 어노테이션은 record 필드에 바로 작성
- Entity → 응답 DTO 변환은 응답 DTO의 정적 팩토리 메서드 `from()` 사용

```java
public record TestCreateRequest(
        @NotBlank String title,
        @NotNull Long categoryId
) {}

public record TestCreateResponse(Long id, String title) {
    public static TestCreateResponse from(Test test) {
        return new TestCreateResponse(test.getId(), test.getTitle());
    }
}
```

### Swagger UI 포맷

- 로컬 접속: `http://localhost:8080/swagger-ui/index.html` (API 스펙 JSON: `/v3/api-docs`)

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "[AUTH] 인증 API", description = "이메일 로그인 및 토큰 재발급 관련 API")
public class AuthController {

    ...

    @GetMapping("/me")
    @Operation(
            summary = "Access 토큰 검증",
            description = "헤더의 Access Token이 유효한지 검증합니다."
    )
    public ResponseEntity<Response<MeResponse>> getMe() {
        Member member = authenticationGuard.currentMember();
        return ResponseEntity.ok(Response.ok(MeResponse.from(member)));
    }

    ...

}
```

### Controller

반환 타입 래퍼 (성공 시 HTTP 상태코드)

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tests")
@Tag(name = "[TEST] 테스트 API", description = "테스트 생성·조회 API")
public class TestController {

    private final TestService testService;

    @PostMapping
    @Operation(summary = "테스트 생성", description = "새 테스트를 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 값 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<Response<TestCreateResponse>> createTest(
            @RequestBody @Valid TestCreateRequest request
    ) {
        TestCreateResponse response = testService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.created("테스트가 등록되었습니다.", response));
    }
}
```

```java
// 응답 성공 HTTP 상태코드 분리하기
// 1. 생성 (201)
return ResponseEntity.status(HttpStatus.CREATED)
        .body(Response.created("주관식 질문이 등록되었습니다.", response));

// 2. 조회·수정 등 (200)
return ResponseEntity.ok(Response.ok("테스트지 템플릿 import 성공", response));
return ResponseEntity.ok(Response.ok(response));   // 메시지 없이
```

- 성공 응답은 `ResponseEntity<Response<T>>`로 반환하고 HTTP 상태코드를 명시
- `Response` 정적 메서드는 `ok(data)`, `ok(message, data)`, `created(message, data)`만 사용
- 실패 응답은 컨트롤러에서 만들지 않고 예외를 던지면 `GlobalExceptionHandler`가 `ErrorResponse`로 변환

### 에러코드

- 공통 에러는 `BaseErrorCode`, 도메인 에러는 도메인별 `{도메인}ErrorCode` enum을 만들어 `ErrorCode`를 구현
- 상수 이름은 의미가 드러나게, `code`는 `{DOMAIN}_{nnn}` 형식으로 작성 (예: `MEETUP_NOT_FOUND` / `MEETUP_001`)
- `code`는 프론트와의 약속이므로 한 번 정하면 바꾸지 않고, 중복되면 서버가 기동되지 않음
- 실패 시 `throw new BaseException(MeetupErrorCode.MEETUP_NOT_FOUND)`처럼 예외를 던짐
- 정의된 에러코드는 `/docs/error-codes` 페이지에서 자동으로 확인 가능 (별도 문서 수정 불필요)

```java
@Getter
@RequiredArgsConstructor
public enum MeetupErrorCode implements ErrorCode {

    MEETUP_NOT_FOUND(HttpStatus.NOT_FOUND, "MEETUP_001", "존재하지 않는 모임입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
```
