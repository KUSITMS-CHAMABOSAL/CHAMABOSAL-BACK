# k8s/gateway

클러스터 진입점입니다. k3s 기본 Traefik이 Gateway API로 80·443을 받고, cert-manager가 Let's Encrypt 인증서를 발급합니다. Traefik 설정은 `infra/ansible`이 합니다.

| 파일 | 내용 |
|---|---|
| `cert-manager/helmchart.yaml` | cert-manager v1.21.3, Gateway API 지원 켬 (k3s Helm 컨트롤러로 설치) |
| `namespace.yaml` | `chamabosal` |
| `cluster-issuer.yaml` | `letsencrypt-staging`, `letsencrypt-prod` (HTTP-01, Gateway `http` 리스너) |
| `gateway.yaml` | `chamabosal-gateway`: `http`(80), `https`(443, `api-k8s.chamabosal.com`, Secret `chamabosal-tls`). http는 https로 301 |

리스너 포트가 8000·8443인 이유: Traefik Gateway provider는 리스너 포트를 Traefik entryPoint 포트와 맞춥니다. 밖에서는 80·443으로 들어옵니다.

## 적용 순서

```bash
kubectl apply -k k8s/gateway/cert-manager
# Helm 설치가 끝나 CRD가 생기고 webhook이 준비될 때까지 기다린다. 준비 전에 ClusterIssuer를 만들면 실패한다.
kubectl wait --for condition=established crd/clusterissuers.cert-manager.io --timeout=5m
kubectl -n cert-manager rollout status deploy/cert-manager-webhook --timeout=5m
kubectl apply -k k8s/gateway
kubectl apply -k k8s/mysql
kubectl apply -k k8s/app
```

## 인증서

- DNS `api-k8s.chamabosal.com`이 서버 공인 IP를 가리켜야 발급됩니다. 그 전에는 `certificate/chamabosal-tls`가 `READY False`로 기다리고, DNS가 생기면 알아서 다시 시도합니다.
- 처음엔 staging으로 발급을 확인하고, 성공하면 `gateway.yaml` annotation을 `letsencrypt-prod`로 바꾼 뒤 `chamabosal-tls` Secret을 지워 다시 발급받습니다.
- `api.chamabosal.com`은 DNS를 옮기는 날 `https` 리스너를 하나 더 추가합니다 (앱 HTTPRoute hostnames도 함께).

확인

```bash
kubectl -n chamabosal get gateway,certificate,challenges
curl -I -H 'Host: api-k8s.chamabosal.com' http://<서버 공인 IP>/   # 301 → https
```
