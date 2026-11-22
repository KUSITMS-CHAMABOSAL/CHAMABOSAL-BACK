# k8s/mysql

클러스터 안 MySQL입니다. `docker-compose.yml`의 `db`를 옮겼습니다.

| 파일 | 내용 |
|---|---|
| `statefulset.yaml` | `mysql:8.4` Pod 1개, 데이터 20Gi, 메모리 1Gi 제한, `mysqladmin ping` probe |
| `service.yaml` | `mysql:3306` (headless) |
| `storageclass.yaml` | `local-path-retain`: PVC를 지워도 서버 디스크의 데이터를 지우지 않음 |

데이터는 서버 디스크(`/var/lib/rancher/k3s/storage/`)에만 있습니다. 매일 S3 백업은 `k8s/backup`이 합니다.

## Secret 만들기

`mysql-auth`. 값은 커밋하지 않습니다.

| 키 | 설명 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | root 비밀번호 (지금 서버의 `DB_ROOT_PASSWORD`) |
| `MYSQL_USER`, `MYSQL_PASSWORD` | 앱 계정. `chamabosal-app-env`의 `DB_USERNAME`, `DB_PASSWORD`와 같은 값 |

```bash
kubectl -n chamabosal create secret generic mysql-auth \
  --from-literal=MYSQL_ROOT_PASSWORD=... \
  --from-literal=MYSQL_USER=... \
  --from-literal=MYSQL_PASSWORD=...
```

계정은 데이터 폴더가 비어 있는 첫 기동 때만 만들어집니다. 그 뒤에 Secret 값을 바꾸면 MySQL 안의 비밀번호도 직접 바꿔야 합니다.

## 적용과 확인

```bash
kubectl apply -k k8s/gateway   # 네임스페이스
kubectl apply -k k8s/mysql
kubectl -n chamabosal rollout status statefulset/mysql
kubectl -n chamabosal exec statefulset/mysql -- sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" chamabosal -e "SELECT 1"'
```
