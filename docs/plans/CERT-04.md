# CERT-04 인증 사진

## 범위
- 구현 기능: CERT-04 (1~4·6·7번, 8번은 Q-30 임시 결정)
- 함께 고치는 기능: CERT-01 (사진 키로 등록), CERT-02 (9·10번 사진 바꾸기·유지·지우기, 삭제 시 사진 삭제)
- 참고 기능: VOTE-02 (후보 카드의 사진 URL), ROOM-02 (오늘 탭 사진 URL, 팀원 담당), USER-05 4번, CLEAN-01
- 다음 작업으로 나눔 (할 일이 10개를 넘어서): CERT-04 5번 7일 뒤 삭제 작업(CERT-04-2·3·5)과 USER-05 4번 내 데이터 삭제 시 사진 삭제

## 명세 점검
- 상태 전이: 사진은 `임시 업로드(tmp/)` → `정식(인증에 연결)` → `삭제`다. 인증 등록·수정 때 정식 경로로 옮기고, 수정으로 바꾸거나 지우거나 인증을 삭제하면 바로 지운다. 임시 사진은 S3 수명 주기 규칙이 1일 뒤 지운다.
- 동시성: 사진 키는 인증 행에만 있고 인증 등록·수정·삭제는 이미 방 행 락 안에서 처리된다. 같은 임시 키로 두 인증을 동시에 등록해도 정식 키는 요청마다 새로 만들어 서로 덮어쓰지 않는다.
- 권한: 업로드 URL은 로그인 사용자만 받는다. 임시 키에 사용자 ID를 넣어 다른 사람이 받은 키로는 등록할 수 없게 한다 (주요 결정 2). 저장소는 비공개이고 조회는 만료되는 URL로만 한다.
- 외부 연동 실패: S3 복사·확인·삭제는 네트워크 호출이라 트랜잭션 밖에서 한다 (CLAUDE.md).
  - 등록·수정: 트랜잭션 전에 임시 사진을 정식 경로로 복사하고, 등록·수정이 실패하면 복사본을 지운다. 복사가 실패하면 사진 키 오류로 거부하고 인증은 바뀌지 않는다.
  - 바꾸거나 지운 사진: 커밋 뒤에 지운다. 삭제가 실패하면 에러 로그를 남긴다 (Discord 에러 알림).
  - presigned URL 발급은 서명 계산뿐이라 네트워크를 쓰지 않는다.
- 제외 범위: 7일 뒤 삭제 작업과 내 데이터 삭제 시 사진 삭제 (다음 작업), 떠난 보살 6개월 정리(CLEAN-01), 오늘 탭 사진 표시(ROOM-02), 신고 숨김.

## 명세 변경
- CERT-04 8번: Q-30이 정해지기 전까지 허용 형식·크기 임시 결정 (열린 질문 1)
- CERT-04: 임시 키는 `tmp/{사용자 ID}/{UUID}`, 정식 키는 `certifications/{UUID}`. 다른 사용자의 임시 키는 `COMMON_002` [구현 결정]
- CERT-04: 조회 URL 유효 시간 30분, 업로드 URL 유효 시간 5분(api.md의 `expiresIn: 300`) [구현 결정]
- api.md 업로드 URL 발급: 에러에 `COMMON_008` 추가, 임시 키 형식 명시

## 설계

### API 계약
- `POST /api/certifications/upload-urls`
  - 요청: `{ "contentType": "image/jpeg" }`
  - 응답 201: `{ "uploadUrl": "https://...", "imageKey": "tmp/12/2f1c...", "expiresIn": 300 }`
  - 에러 코드: `COMMON_002` (400, 허용하지 않는 형식), `COMMON_008` (401)
- `POST /api/rooms/{roomId}/certifications`: 요청에 `imageKey` (선택). 없거나 내 것이 아니거나 만료된 키면 `COMMON_002` (field: `imageKey`)
- `PATCH /api/certifications/{certificationId}`: 요청에 `imageKey`, `isImageKept`(필수). 응답에 `imageUrl`
- `DELETE /api/certifications/{certificationId}`: 사진도 바로 삭제
- 투표 후보(`VOTE-02`) 응답의 `certification.imageUrl`: 지금은 항상 `null` → 사진이 있으면 조회 URL

### 엔티티
- `Certification`: 이미 있는 `image_key`, `image_deleted_at`을 쓴다. 새 컬럼 없음
- 새 테이블 없음

### 설정·배포
- `build.gradle`: AWS SDK for Java v2 `s3` (presigner 포함)
- 저장소 설정: `storage.s3.bucket`, `storage.s3.region`, 접근 키는 SDK 기본 환경 변수(`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
- `docker-compose.yml`: `AWS_S3_BUCKET`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`를 앱에 넘김
- `cd.yml`: 위 4개를 서버 `.env`로 옮기고 필수 Secrets 검사 목록에 추가 (2026-11-16 등록 확인)

### 주요 결정
1. 저장소는 `PhotoStorage` 인터페이스 하나로 감싼다. 버킷 설정이 있으면 S3 구현, 없으면 메모리 가짜 구현을 쓴다. 테스트는 가짜로 돌고, 로컬도 버킷 환경 변수를 넣으면 실제 S3를 쓸 수 있다. prod는 CD가 버킷 설정을 필수로 넣는다. [구현 결정]
2. 임시 키는 `tmp/{사용자 ID}/{UUID}`로 발급하고, 등록·수정 때 내 사용자 ID 접두사인지 먼저 본다. UUID만으로도 추측은 어렵지만 다른 사람 사진을 내 인증에 붙이는 일을 확실히 막는다. [구현 결정]
3. 등록·수정 때 임시 사진이 있는지는 복사 결과로 판단한다. 버킷 목록 권한이 없어 없는 키도 403으로 오므로 404·403 모두 "없거나 만료된 사진 키"다.
4. 정식 경로 복사는 트랜잭션 전, 기존 사진 삭제는 커밋 뒤에 한다. 이 흐름은 새 `CertificationPhotoService`가 맡고 `CertificationService`의 락·확인 순서는 그대로 둔다. [구현 결정]
5. 조회 URL은 응답할 때마다 만들고(30분), 삭제된 사진(`image_deleted_at` 있음)은 `null`이다. 오늘 탭(ROOM-02)이 쓸 수 있게 `PhotoUrlProvider`처럼 인증 하나로 URL을 돌려주는 메서드를 공개한다.
6. 사진 크기 상한은 presigned PUT으로는 서버가 강제할 수 없다. 임시 결정의 크기 상한은 프론트가 지키고, 서버 강제는 Q-30이 정해지면 다시 본다.

## 규칙 점검
- `CLAUDE.md`, `docs/CONVENTION.md` 위반: 없음. 외부 호출(S3 복사·삭제)은 트랜잭션 밖에서 한다.
- 새 의존성: AWS SDK v2 `s3` 하나. 표준 라이브러리로는 presigned URL 서명을 대신할 수 없다.

## 할 일
- [x] 명세: CERT-04 8번 임시 결정, 키 형식·URL 유효 시간, api.md
- [x] `build.gradle` AWS SDK, 저장소 설정, `docker-compose.yml`·`cd.yml` 환경 변수
- [x] `PhotoStorage` 인터페이스와 S3 구현·가짜 구현
- [x] `POST /api/certifications/upload-urls`
- [x] 인증 등록에 `imageKey` (복사 → 등록 → 실패 시 복사본 삭제)
- [x] 인증 수정에 `imageKey`·`isImageKept`, 바꾸거나 지운 사진은 커밋 뒤 삭제, 응답 `imageUrl`
- [x] 인증 삭제 시 사진 커밋 뒤 삭제
- [x] 조회 URL: 투표 후보 `imageUrl`, ROOM-02용 공개 메서드
- [x] openapi와 컨트롤러 설명
- [x] 테스트: CERT-04-4·6·7·8, CERT-02-10·11·12 (CERT-04-6~8은 이번에 명세에 추가한 시나리오. CERT-04-1은 버킷 설정이라 수동 확인)

## 열린 질문
- 없음
  - Q-30 임시 결정으로 진행: 형식은 `image/jpeg`, `image/png`, `image/heic`, `image/webp`만, 크기 상한 10MB는 프론트가 지킴, 서버 리사이징 없음 (사람 결정 2026-11-16, `[임시: 기획 확인 중, 2026-11-16] Q-30`)
  - 7일 뒤 삭제 작업과 내 데이터 삭제 시 사진 삭제는 다음 PR (사람 결정 2026-11-16)
  - 오늘 탭(ROOM-02) 사진: 팀원 브랜치 `feat/#106-room-today`의 `GET /api/rooms/{roomId}/today`가 인증 카드마다 `imageUrl`(지금은 null)을 돌려준다. 이 PR은 URL을 만드는 공개 메서드만 제공하고, 오늘 탭 연결은 두 작업이 dev에 들어간 뒤 한 줄로 붙인다.

## 자기 점검

### 처리 규칙 · 시나리오 · 테스트

| 기능 | 처리 규칙 | 시나리오 | 테스트 |
|---|---|---|---|
| CERT-04 | 1 (사진 없이도 인증) | CERT-01-7 | `CertificationCreateTest.CERT_01_7` (기존) |
| CERT-04 | 2 (암호화된 비공개 저장소) | CERT-04-1 | 없음 (버킷 설정: 퍼블릭 차단·SSE-S3, 배포 뒤 수동 확인) |
| CERT-04 | 3 (임시 경로 발급, 등록 때 정식 경로로) | CERT-04-4 | `CertificationPhotoTest.CERT_04_4` (키 접두사 `tmp/{사용자}`→`certifications/` 확인) |
| CERT-04 | 4 (조회 URL은 응답마다, 지운 사진은 null) | CERT-04-8, CERT-02-10·11 | `CertificationPhotoTest.CERT_04_8`, `CERT_02_10`, `CERT_02_11` |
| CERT-04 | 5 (7일 뒤 삭제) | CERT-04-2·3·5 | 없음 (다음 PR) |
| CERT-04 | 6 (인증 삭제 시 즉시 삭제, 내 데이터 삭제) | CERT-04-4, USER-05-6 | `CertificationPhotoTest.CERT_04_4`, USER-05-6은 다음 PR |
| CERT-04 | 7 (쓰이지 않은 업로드는 1일 뒤) | 없음 | 없음 (버킷 수명 주기 규칙) |
| CERT-04 | 8 (허용 형식, Q-30 임시) | CERT-04-7 | `CertificationPhotoTest.CERT_04_7` |
| CERT-04 | 9 (내 임시 키만) | CERT-04-6 | `CertificationPhotoTest.CERT_04_6` |
| CERT-04 | 10 (URL 유효 시간) | 없음 | `CERT_04_4`에서 업로드 `expiresIn` 300 확인. 조회 30분은 S3 서명으로 한 번 수동 확인(임시 테스트, 지움) |
| CERT-04 | 11 (복사·삭제는 등록·수정 밖) | CERT-04-6·8, CERT-02-12 | 위 테스트 (실패한 수정에서 기존 사진이 남는지 `CERT_02_12`) |
| CERT-02 | 9 (사진 바꾸기·유지·지우기) | CERT-02-10·11, CERT-04-8 | `CertificationPhotoTest.CERT_02_10`, `CERT_02_11`, `CERT_04_8` |
| CERT-02 | 10 (유지 여부 필수) | CERT-02-12 | `CertificationPhotoTest.CERT_02_12` |

### 테스트가 없는 시나리오
- CERT-04-1: 버킷의 퍼블릭 차단 설정이라 코드로 확인할 수 없다. 배포 뒤 `certifications/…` 객체 주소를 서명 없이 열어 403인지 확인한다.
- CERT-04-2·3·5: 7일 뒤 삭제 작업은 다음 PR (사람 결정 2026-11-16).

### 시나리오가 없는 처리 규칙
- CERT-04 7번 (버킷 수명 주기 규칙)
- CERT-04 10번 (조회 URL 30분): 테스트는 메모리 저장소라 유효 시간을 담지 않는다.
- CERT-02 10번 뒷부분 (새 사진과 유지를 함께 보내면 `COMMON_002`): 코드에는 있고 시나리오는 없다.
- 투표 후보 카드의 `imageUrl`(VOTE-02): 코드는 연결했고 시나리오는 없다.

### 명세에 없는 규칙을 만든 곳
- 없음. 키 형식, URL 유효 시간, 저장소 호출 시점, 사진 키 확인 순서는 [구현 결정]으로, 허용 형식은 Q-30 임시 결정으로 명세에 적었다.
- 시나리오 CERT-04-6·7·8을 명세에 추가했다 (새 규칙 8·9번과 CERT-02 9번 바꾸기).
- 저장소 삭제가 실패하면 에러 로그만 남기고 요청은 성공시킨다 (CERT-04 11번). 남은 사진은 다음 PR의 삭제 작업에서도 다시 지우지 않는다 (지운 것으로 기록됨).

### 상태
- CERT-04: 2·3·5 시나리오가 다음 PR이라 `미구현` 유지
- CERT-01·02는 이번 계획서의 구현 기능이 아니라 상태를 바꾸지 않았다. CERT-02는 시나리오가 모두 테스트된다.
