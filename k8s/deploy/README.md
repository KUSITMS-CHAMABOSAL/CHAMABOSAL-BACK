# k8s/deploy

GitHub Actions가 k3s에 배포할 때 쓰는 계정입니다.

| 파일 | 내용 |
|---|---|
| `rbac.yaml` | ServiceAccount `github-deployer`, 장기 토큰 Secret, `chamabosal` 네임스페이스용 Role |

관리자 kubeconfig는 클러스터 전체 권한이라 GitHub에 올리지 않고, 이 계정으로 만든 kubeconfig만 올립니다.

권한 범위: `chamabosal` 네임스페이스 안에서만 ConfigMap·Service·Deployment·HTTPRoute를 만들고 바꿀 수 있고, 다른 네임스페이스와 클러스터 리소스는 못 건드립니다. Secret 조회 권한은 없지만 Deployment를 바꿀 수 있어 Pod에 네임스페이스의 Secret을 마운트할 수 있으므로, 이 kubeconfig가 새면 `chamabosal` 네임스페이스의 Secret도 샌 것으로 봅니다.

## 배포 흐름

`dev`에 머지하면 `cd.yml`이 아래 순서로 돕니다.

1. `test` → `build-and-push`: 이미지를 `<커밋 SHA>`와 `latest` 태그로 GHCR에 올림
2. `deploy-k8s`: Tailscale로 클러스터에 붙음 → `kustomize edit set image`로 태그를 커밋 SHA로 바꿈 → `kubectl apply -k k8s/app` → `rollout status` (5분)
3. 실패하면 Pod 목록과 앱 로그 100줄을 남기고 job이 실패함. 앱은 `Recreate`라 그동안 끊겨 있으니 아래 롤백을 함

옛 서버 `deploy` job은 DNS 전환(Phase 5) 날까지 같이 돕니다. 그날 `deploy` job을 지웁니다.

## 처음 한 번 준비

### 1. 계정 만들기 (관리자 kubeconfig)

```bash
export KUBECONFIG=~/.kube/chamabosal-k3s.yaml
kubectl apply -k k8s/deploy
kubectl auth can-i patch deployments -n chamabosal --as=system:serviceaccount:chamabosal:github-deployer  # yes
kubectl auth can-i get secrets -n chamabosal --as=system:serviceaccount:chamabosal:github-deployer        # no
```

### 2. 배포용 kubeconfig 만들기

서버 주소와 CA는 관리자 kubeconfig에서 가져옵니다. 파일에 토큰이 들어가니 커밋하지 않습니다.

```bash
export KUBECONFIG=~/.kube/chamabosal-k3s.yaml
SERVER=$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
CA=$(kubectl config view --raw --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}')
TOKEN=$(kubectl -n chamabosal get secret github-deployer-token -o jsonpath='{.data.token}' | base64 -d)
[ -n "$TOKEN" ] || echo "토큰이 아직 비어 있습니다. 몇 초 뒤 TOKEN 줄부터 다시 실행하세요."
umask 077
# 토큰이 비어 있으면 파일을 만들지 않는다.
[ -n "$TOKEN" ] && cat > ~/.kube/chamabosal-deployer.yaml <<EOF
apiVersion: v1
kind: Config
clusters:
  - name: chamabosal
    cluster:
      server: $SERVER
      certificate-authority-data: $CA
users:
  - name: github-deployer
    user:
      token: $TOKEN
contexts:
  - name: github-deployer
    context:
      cluster: chamabosal
      user: github-deployer
      namespace: chamabosal
current-context: github-deployer
EOF
KUBECONFIG=~/.kube/chamabosal-deployer.yaml kubectl get deploy  # chamabosal-app이 보이면 됨
```

### 3. Tailscale OAuth 클라이언트

Tailscale 관리자 화면에서 만듭니다.

1. Access controls에 태그 주인을 추가합니다: `"tagOwners": { "tag:ci": ["autogroup:admin"] }`
2. Settings → OAuth clients → Generate: 범위 `Auth Keys`(Write), 태그 `tag:ci`
3. 만든 기기는 임시(ephemeral)라 job이 끝나면 tailnet에서 사라집니다

### 4. GitHub Secrets

| 이름 | 값 |
|---|---|
| `TS_OAUTH_CLIENT_ID`, `TS_OAUTH_SECRET` | 3번에서 만든 OAuth 클라이언트 |
| `KUBECONFIG` | 2번 파일 내용 (`gh secret set KUBECONFIG < ~/.kube/chamabosal-deployer.yaml`) |

## 수동 재배포

Actions → CD → Run workflow (`dev`). `dev`의 **최신 커밋**을 옛 서버와 k3s 둘 다에 배포합니다. 롤백한 뒤에 실행하면 문제 커밋이 다시 올라가니 주의합니다. 특정 커밋을 다시 배포하려면 아래 롤백의 방법을 씁니다.

노트북에서 `kubectl apply -k k8s/app`을 그대로 실행하면 레포의 태그(`latest`)로 덮어써서 배포한 버전이 바뀝니다. 매니페스트만 고쳐 바로 적용해야 하면 지금 이미지 태그를 넣고 적용합니다.

```bash
export KUBECONFIG=~/.kube/chamabosal-k3s.yaml
TAG=$(kubectl -n chamabosal get deploy chamabosal-app -o jsonpath='{.spec.template.spec.containers[0].image}' | cut -d: -f2)
kubectl kustomize k8s/app | sed "s|chamabosal-back:latest|chamabosal-back:$TAG|" | kubectl apply -f -
```

## 롤백

이전 버전 3개를 보관합니다 (`revisionHistoryLimit: 3`).

```bash
export KUBECONFIG=~/.kube/chamabosal-k3s.yaml
kubectl -n chamabosal rollout history deployment/chamabosal-app
kubectl -n chamabosal rollout undo deployment/chamabosal-app                  # 바로 전 버전
kubectl -n chamabosal rollout undo deployment/chamabosal-app --to-revision=N  # 특정 버전
kubectl -n chamabosal rollout status deployment/chamabosal-app
```

더 오래된 버전은 Actions에서 그 커밋의 CD 실행을 열고 **`deploy-k8s` job만** Re-run 합니다. "Re-run all jobs"를 누르면 옛 서버 `deploy`도 그 버전으로 돌아가니 DNS 전환 전에는 누르지 않습니다.

Actions 실행은 30일이 지나면 Re-run 할 수 없습니다. 그때는 GHCR에 남은 이미지(최근 10개)의 커밋 SHA를 직접 넣어 적용합니다.

```bash
export KUBECONFIG=~/.kube/chamabosal-k3s.yaml
TAG=<되돌릴 커밋 SHA 40자>
kubectl kustomize k8s/app | sed "s|chamabosal-back:latest|chamabosal-back:$TAG|" | kubectl apply -f -
kubectl -n chamabosal rollout status deployment/chamabosal-app
```

롤백은 다음 `dev` 머지 때 덮어써지므로, 원인을 고친 커밋을 머지해야 끝납니다.

## 토큰 교체

토큰이 새면 Secret을 지우고 다시 만든 뒤 2번과 4번을 다시 합니다.

```bash
kubectl -n chamabosal delete secret github-deployer-token
kubectl apply -k k8s/deploy
```
