# k8s/app

참아보살 API 앱 매니페스트입니다. (K8s 전환 Phase 3, 담당: 송현빈)

| 파일 | 내용 |
|---|---|
| `deployment.yaml` | 앱 Pod 1개, `Recreate` 배포, probe, 리소스, 보안 설정, 토스 인증서 볼륨 |
| `service.yaml` | `chamabosal-app:8080` (ClusterIP) |
| `httproute.yaml` | `api-k8s.chamabosal.com` → 앱. Gateway `chamabosal-gateway`(네임스페이스 `chamabosal`)의 `https` 리스너에 붙음 |
| `config.env` | 비밀이 아닌 고정값. `configMapGenerator`가 ConfigMap `chamabosal-app-config-<해시>`로 만듦 |

네임스페이스 `chamabosal`, Gateway, MySQL(`mysql:3306`)은 `k8s/gateway/`, `k8s/mysql/`에서 만듭니다.

## 운영 전 알아둘 것

- 스케줄러(`@Scheduled`)가 Pod마다 돌지 않게 replicas 1 + `Recreate`로 둡니다. 배포할 때 옛 Pod가 내려간 뒤 새 Pod가 떠서 잠깐 끊깁니다 (지금 docker compose 배포와 같음). replicas를 늘리려면 ShedLock부터 넣어야 합니다.
- 종료 순서: preStop 5초 → Spring graceful shutdown 최대 30초 → `terminationGracePeriodSeconds` 45초. Spring 쪽 값은 `KubernetesProbeConfigTest`가 지킵니다 (#185).
- `SPRING_JPA_HIBERNATE_DDL_AUTO=update`는 출시 전까지만 둡니다. 출시 때 `config.env`에서 지워 prod 기본값 `validate`로 돌립니다. `config.env`를 바꾸면 ConfigMap 이름이 바뀌어 Pod가 새 값으로 다시 뜹니다.
- `api.chamabosal.com`은 DNS를 옮기는 날(Phase 5) `httproute.yaml`에 추가합니다. 그 전에는 지금 서버를 가리켜서 인증서 발급(HTTP-01)이 실패하고, 넣어 두면 두 서버로 트래픽이 섞일 수 있습니다.
- 이미지 태그는 `kustomization.yaml`의 `images.newTag`에서 정합니다. 지금은 `latest`라서 다시 apply해도 새 이미지로 뜨지 않고, `kubectl rollout undo`도 이전 빌드로 돌아가지 못합니다. 배포할 때 `kustomize edit set image ghcr.io/kusitms-chamabosal/chamabosal-back:<커밋 SHA>`로 바꿔야 새로 뜨고 롤백도 됩니다 (Phase 4 CI/CD, 이전 버전 3개 보관).

## Secret 만들기

값은 커밋하지 않습니다. 모든 값을 지금 GitHub Secrets에 있는 값 그대로 넣습니다.

`chamabosal-app-env` (앱 환경 변수)

| 키 | 설명 |
|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | MySQL 앱 계정 |
| `JWT_SECRET` | JWT 서명 키 |
| `DISCORD_ERROR_WEBHOOK_URL` | 5xx 알림 웹훅 |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | 인증 사진 S3 접근 키 |
| `AWS_S3_BUCKET`, `AWS_REGION` | 비밀은 아니지만 지금 GitHub Secrets로 관리해서 함께 둠 |
| `AUTH_TEST_TOKEN_ENABLED`, `AUTH_TEST_TOKEN_KEY` | 프론트 연동용 테스트 토큰. 출시 전에 끔 |

```bash
kubectl -n chamabosal create secret generic chamabosal-app-env \
  --from-literal=DB_USERNAME=... \
  --from-literal=DB_PASSWORD=... \
  --from-literal=JWT_SECRET=... \
  --from-literal=DISCORD_ERROR_WEBHOOK_URL=... \
  --from-literal=AWS_ACCESS_KEY_ID=... \
  --from-literal=AWS_SECRET_ACCESS_KEY=... \
  --from-literal=AWS_S3_BUCKET=... \
  --from-literal=AWS_REGION=... \
  --from-literal=AUTH_TEST_TOKEN_ENABLED=... \
  --from-literal=AUTH_TEST_TOKEN_KEY=...
```

`chamabosal-toss-mtls` (토스 mTLS 인증서, `/app/toss`에 읽기 전용으로 마운트)

```bash
kubectl -n chamabosal create secret generic chamabosal-toss-mtls \
  --from-file=chamabosal.crt=/etc/chamabosal/toss/chamabosal.crt \
  --from-file=chamabosal.key=/etc/chamabosal/toss/chamabosal.key
```

## 적용

```bash
kubectl apply -k k8s/app
kubectl -n chamabosal rollout status deployment/chamabosal-app
```

## 로컬 k3d에서 확인하기

Docker Desktop과 k3d(`brew install k3d`)가 필요합니다. GHCR 이미지는 amd64만 있어서 Apple Silicon에서는 로컬에서 빌드한 이미지를 씁니다. MySQL은 임시 Pod로 띄웁니다.

```bash
# 1. 클러스터와 네임스페이스
k3d cluster create chamabosal
kubectl create namespace chamabosal

# 2. 앱 이미지를 빌드해 클러스터에 넣기
docker build -t chamabosal-back:local .
k3d image import chamabosal-back:local -c chamabosal

# 3. 임시 MySQL
kubectl -n chamabosal run mysql --image=mysql:8.4 --port=3306 \
  --env=MYSQL_DATABASE=chamabosal --env=MYSQL_USER=chamabosal \
  --env=MYSQL_PASSWORD=local --env=MYSQL_ROOT_PASSWORD=local
kubectl -n chamabosal expose pod mysql --port=3306

# 4. 가짜 값으로 Secret 만들기 (토스 인증서는 형식만 맞춘 자체 서명 인증서)
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=local" \
  -keyout /tmp/chamabosal.key -out /tmp/chamabosal.crt
kubectl -n chamabosal create secret generic chamabosal-toss-mtls \
  --from-file=chamabosal.crt=/tmp/chamabosal.crt --from-file=chamabosal.key=/tmp/chamabosal.key
kubectl -n chamabosal create secret generic chamabosal-app-env \
  --from-literal=DB_USERNAME=chamabosal --from-literal=DB_PASSWORD=local \
  --from-literal=JWT_SECRET=local-k3d-only-secret-key-at-least-32-bytes \
  --from-literal=DISCORD_ERROR_WEBHOOK_URL= \
  --from-literal=AWS_ACCESS_KEY_ID=local --from-literal=AWS_SECRET_ACCESS_KEY=local \
  --from-literal=AWS_S3_BUCKET=local --from-literal=AWS_REGION=ap-northeast-2 \
  --from-literal=AUTH_TEST_TOKEN_ENABLED=false --from-literal=AUTH_TEST_TOKEN_KEY=

# 5. 적용. 이미지만 로컬 빌드로 바꿔 넣는다. 매니페스트를 고친 뒤에도 이 명령으로 다시 적용한다.
#    (k3s에 Gateway API CRD가 들어 있어 따로 설치하지 않음. Gateway가 없으면 라우트는 붙지 않고 남음)
kubectl kustomize k8s/app \
  | sed -e 's|ghcr.io/kusitms-chamabosal/chamabosal-back:latest|chamabosal-back:local|' \
        -e 's|imagePullPolicy: Always|imagePullPolicy: IfNotPresent|' \
  | kubectl apply -f -
kubectl -n chamabosal rollout status deployment/chamabosal-app

# 6. probe 확인
kubectl -n chamabosal port-forward svc/chamabosal-app 8080:8080 &
curl -s localhost:8080/actuator/health/liveness   # {"status":"UP"}
curl -s localhost:8080/actuator/health/readiness  # {"status":"UP"}

# 정리
k3d cluster delete chamabosal
```
