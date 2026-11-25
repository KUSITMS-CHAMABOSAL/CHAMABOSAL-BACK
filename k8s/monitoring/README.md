# k8s/monitoring

Grafana Cloud(무료 플랜)로 클러스터·앱 지표와 로그를 보내는 설정입니다.

Grafana 공식 차트 `grafana/k8s-monitoring`을 Helm으로 설치하고, 값은 `values.yaml`에 둡니다. 수집기는 Grafana Alloy입니다.

| 파일 | 내용 |
|---|---|
| `values.yaml` | 차트 값. 무엇을 모아 어디로 보낼지 정함. 비밀값은 없음 |
| `dashboard.json` | API·JVM 대시보드. Grafana에서 import |
| `discord-template.tmpl` | Discord 알림 메시지 템플릿 |

## 모으는 것

| 대상 | 수집 방법 | 대시보드·알림에 쓰는 곳 |
|---|---|---|
| 노드 CPU·메모리·디스크 | node-exporter | 노드 CPU·메모리, 노드 메모리·디스크 80% 알림 |
| Pod 상태·재시작, StatefulSet 준비 수, CronJob 마지막 성공 시각 | kube-state-metrics | Pod 재시작, MySQL 다운, 백업 알림 |
| 컨테이너 CPU·메모리 | kubelet, cAdvisor | Pod별 사용량 |
| 앱 지표 (요청 수·응답 시간·에러율, JVM 힙) | 앱 Pod 애너테이션 → `:8081/actuator/prometheus` | API·JVM 대시보드, 앱 다운 알림 |
| 로그 | `chamabosal` 네임스페이스 Pod의 컨테이너 로그 (앱, MySQL, 백업 Job) | 장애 때 로그 검색 |

- 앱 지표는 관리 포트 8081에서만 열리고 Service·HTTPRoute에는 없어 밖에서 닿지 않습니다 (`k8s/app` README).
- 수집 주기는 60초(차트 기본값)입니다.
- 클러스터에 뜨는 것: `alloy-metrics`(StatefulSet 1개), `alloy-logs`(DaemonSet), Alloy Operator, kube-state-metrics, node-exporter. 로컬에서 잰 메모리는 합쳐 약 290Mi입니다.

## Grafana Cloud 준비

1. Grafana Cloud 무료 계정과 스택을 만듭니다. 무료 플랜은 3명까지 쓸 수 있습니다.
2. 스택 상세 화면에서 아래 값을 확인합니다.
   - Prometheus: Remote Write 주소(`.../api/prom/push`), 사용자 번호(Username / Instance ID)
   - Loki: 주소(`https://logs-....grafana.net`) 뒤에 `/loki/api/v1/push`를 붙인 값, 사용자 번호
3. Access Policy를 `metrics:write`, `logs:write` 범위로 만들고 토큰을 발급합니다. 이 토큰은 수집기 전용으로만 씁니다.

## Secret 만들기

값은 커밋하지 않습니다. 차트가 `monitoring` 네임스페이스의 Secret `grafana-cloud`를 읽습니다.

| 키 | 설명 |
|---|---|
| `prometheus-url` | Prometheus Remote Write 주소 |
| `prometheus-username` | Prometheus 사용자 번호 |
| `loki-url` | Loki push 주소 (`.../loki/api/v1/push`) |
| `loki-username` | Loki 사용자 번호 |
| `token` | Access Policy 토큰 (지표·로그 공용) |

```bash
kubectl create namespace monitoring
kubectl -n monitoring create secret generic grafana-cloud \
  --from-literal=prometheus-url=... \
  --from-literal=prometheus-username=... \
  --from-literal=loki-url=... \
  --from-literal=loki-username=... \
  --from-literal=token=...
```

Secret 값을 바꾼 뒤에는 수집기가 다시 읽으므로 재설치하지 않아도 됩니다.

## 설치·업그레이드

레포 루트에서 실행합니다. 차트 버전은 고정합니다. 올릴 때는 차트의 `UPGRADING.md`를 먼저 봅니다.

```bash
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update grafana
helm upgrade --install grafana-k8s-monitoring grafana/k8s-monitoring \
  --version 4.6.1 -n monitoring -f k8s/monitoring/values.yaml --wait

kubectl -n monitoring get pods   # alloy-logs, alloy-metrics, alloy-operator, kube-state-metrics, node-exporter 모두 Running
```

지우기: `helm -n monitoring uninstall grafana-k8s-monitoring`

## 대시보드·알림

Grafana Cloud의 Kubernetes Monitoring 앱이 노드·Pod 화면을 기본으로 보여줍니다. 앱 지표 대시보드는 `dashboard.json`을 가져와 만듭니다.

1. Grafana → Dashboards → New → Import
2. `dashboard.json` 내용을 붙여넣고 Load
3. Prometheus 데이터 소스로 `grafanacloud-<스택 이름>-prom`을 고르고 Import

패널 쿼리는 아래와 같습니다. 쿼리를 바꾸면 `dashboard.json`도 함께 고칩니다.

| 항목 | 쿼리 |
|---|---|
| API 요청 수 (초당) | `sum by (uri) (rate(http_server_requests_seconds_count{job="chamabosal-app"}[5m]))` |
| API 평균 응답 시간 | `sum by (uri) (rate(http_server_requests_seconds_sum{job="chamabosal-app"}[5m])) / sum by (uri) (rate(http_server_requests_seconds_count{job="chamabosal-app"}[5m]))` |
| API 에러율 (5xx) | `(sum(rate(http_server_requests_seconds_count{job="chamabosal-app", status=~"5.."}[5m])) or vector(0)) / sum(rate(http_server_requests_seconds_count{job="chamabosal-app"}[5m]))` |
| JVM 힙 | `sum(jvm_memory_used_bytes{job="chamabosal-app", area="heap"})`, 최대 `sum(jvm_memory_max_bytes{job="chamabosal-app", area="heap"})` |

에러율 쿼리는 5xx가 한 번도 없을 때 `No data` 대신 0이 나오도록 분자에만 `or vector(0)`를 붙입니다. 분모에는 붙이지 않아 수집이 끊기면 `No data`로 보입니다.

알림은 쿼리에 비교 연산자를 넣지 않고 Grafana 알림 규칙의 조건(Threshold)에서 비교합니다. PromQL의 `< 1` 같은 비교는 값을 걸러낼 뿐이라, MySQL이 내려가면 결과가 0이 되고 Grafana 기본 조건(IS ABOVE 0)으로는 울리지 않습니다.

| 알림 | 쿼리 | 조건 | 지속 |
|---|---|---|---|
| 노드 메모리 80% 이상 | `1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes` | IS ABOVE 0.8 | 5분 |
| Pod 재시작 반복 | `increase(kube_pod_container_status_restarts_total{namespace="chamabosal"}[15m])` | IS ABOVE 2 | 없음 |
| MySQL 다운 | `kube_statefulset_status_replicas_ready{namespace="chamabosal", statefulset="mysql"}` | IS BELOW 1 | 2분 |
| 앱 다운 | `sum(up{job="chamabosal-app"})` | IS BELOW 1 | 2분 |
| 노드 디스크 80% 이상 | `1 - node_filesystem_avail_bytes{mountpoint="/"} / node_filesystem_size_bytes{mountpoint="/"}` | IS ABOVE 0.8 | 5분 |
| 백업 26시간 넘게 성공 없음 | `time() - kube_cronjob_status_last_successful_time{namespace="chamabosal", cronjob="mysql-backup"}` | IS ABOVE 93600 | 없음 |

- 앱 다운은 Pod가 응답하지 않아 앱의 5xx 알림도 못 보내는 경우를 잡습니다. 배포 중 Pod가 바뀔 때 줄이 둘로 갈려도 합계로 보도록 `sum`을 씁니다.
- 규칙은 폴더 `chamabosal`, 평가 그룹 `infra`(1분)에 둡니다.
- 백업 알림은 마지막 성공 뒤 지난 초로 봅니다. 매일 05:00 백업에 2시간 여유를 둔 26시간이라, 실패한 경우와 CronJob이 아예 돌지 않은 경우를 모두 잡습니다. 이 지표는 첫 백업이 성공해야 생기므로, 백업을 처음 적용할 때는 `k8s/backup` README대로 수동 Job을 한 번 돌린 뒤 규칙을 만듭니다.
- 데이터가 끊기면(수집기 다운 등) Grafana 기본 설정대로 `DatasourceNoData` 알림이 옵니다.

알림은 Grafana Alerting의 연락처(Contact point)에 Discord 웹훅을 넣어 보냅니다. 웹훅은 앱의 5xx 알림(`DISCORD_ERROR_WEBHOOK_URL`)과 같은 채널에 따로 만들어, 한쪽을 바꿔도 다른 쪽에 영향이 없게 합니다.

1. Notification configuration → Templates에 `discord-template.tmpl` 내용으로 템플릿 그룹 `chamabosal`을 만듭니다.
2. Contact points에 Discord 연락처 `discord-chamabosal`을 만들고 Optional Discord settings를 채웁니다.
   - Title: `{{ template "chamabosal.title" . }}`
   - Message Content: `{{ template "chamabosal.message" . }}`
   - Show Message in Embed: 체크 (본문을 카드 안에 넣음)
3. Test로 Discord 수신을 확인합니다.

## 무료 플랜 한도

2026-11-24에 Grafana 가격 페이지 기준으로 확인한 값입니다. 계정을 만든 뒤 스택의 사용량 화면에서 다시 확인합니다.

| 항목 | 한도 | 예상 사용량 |
|---|---|---|
| 지표 | 활성 시계열 10k | 로컬 k3d에서 약 1.4k. 서버는 Gateway·cert-manager Pod가 더 있고 API 경로가 늘수록 늘어남 |
| 로그 | 월 50GB | `chamabosal` 네임스페이스만 보냄 |
| 보관 | 14일 | |
| 사용자 | 3명 | |

한도에 가까워지면 `podLogsViaLoki.namespaces`를 줄이거나 차트의 지표 허용 목록(`metricsTuning`)으로 줄입니다.

## 로컬에서 확인 (k3d)

`k8s/app` README의 로컬 절차로 앱을 띄운 뒤, 가짜 Secret으로 설치합니다. 보내는 곳이 없어 전송은 실패하지만, 수집 대상이 잡히는지는 확인할 수 있습니다.

```bash
brew install helm
kubectl create namespace monitoring
kubectl -n monitoring create secret generic grafana-cloud \
  --from-literal=prometheus-url=http://127.0.0.1:9/api/prom/push --from-literal=prometheus-username=1 \
  --from-literal=loki-url=http://127.0.0.1:9/loki/api/v1/push --from-literal=loki-username=1 \
  --from-literal=token=dummy
helm upgrade --install grafana-k8s-monitoring grafana/k8s-monitoring \
  --version 4.6.1 -n monitoring -f k8s/monitoring/values.yaml --wait

# Alloy 화면에서 수집 대상 확인: annotation_autodiscovery에 chamabosal-app(:8081)이 up인지
kubectl -n monitoring port-forward pod/grafana-k8s-monitoring-alloy-metrics-0 12345:12345
# 브라우저로 http://localhost:12345 → annotation_autodiscovery.feature → prometheus.scrape.annotation_autodiscovery_http
```
