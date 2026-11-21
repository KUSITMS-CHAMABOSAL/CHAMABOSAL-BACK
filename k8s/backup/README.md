# k8s/backup

클러스터 안 MySQL을 매일 S3로 백업하는 CronJob입니다. (K8s 전환 Phase 3, 담당: 송현빈)

MySQL 데이터는 서버 디스크(local-path PVC)에만 있어서, 서버가 망가지면 데이터도 같이 사라집니다. 그래서 하루 한 번 덤프를 S3에 올립니다.

## 동작

| 항목 | 값 |
|---|---|
| 시각 | 매일 05:00 (KST). 03:00(투표 시작·나발), 04:00·04:10(정리) 일 배치와 겹치지 않음 (5분마다 도는 투표 마감은 가벼워서 같이 돌아도 됨) |
| 덤프 | `mysqldump --single-transaction --routines --events` (서비스를 멈추지 않음) → gzip. 임시 파일은 최대 2Gi |
| 저장 위치 | `s3://<BACKUP_S3_BUCKET>/backups/mysql/chamabosal-YYYYMMDD-HHMMSS.sql.gz` (올린 시각, KST) |
| 순서 | `dump`(initContainer)가 파일을 만든 뒤 `upload`가 올림. 덤프가 실패하면 올리지 않음 |
| 실패 | 최대 3번 시도, 30분 넘으면 중단. 앞 백업이 안 끝났으면 새로 시작하지 않음 (직접 만든 Job은 예외) |
| 기록 | 성공·실패 Job을 3개씩 남김 (`kubectl -n chamabosal get jobs`) |

DB 접속은 앱 Secret `chamabosal-app-env`(`k8s/app` README)의 `DB_USERNAME`, `DB_PASSWORD`만 꺼내 씁니다. S3 업로드는 백업 전용 Secret `mysql-backup-s3`를 씁니다.

## Secret 만들기

`mysql-backup-s3` (업로드 컨테이너에 이 Secret만 넣음)

| 키 | 설명 |
|---|---|
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | 백업 전용 IAM 키. `backups/mysql/`에 `s3:PutObject`만 허용 |
| `AWS_REGION` | 버킷 리전 |
| `BACKUP_S3_BUCKET` | 백업을 올릴 버킷 |

```bash
kubectl -n chamabosal create secret generic mysql-backup-s3 \
  --from-literal=AWS_ACCESS_KEY_ID=... \
  --from-literal=AWS_SECRET_ACCESS_KEY=... \
  --from-literal=AWS_REGION=... \
  --from-literal=BACKUP_S3_BUCKET=...
```

## 운영 전 확인할 것

- **백업 전용 키:** 앱 키는 사진을 지울 수 있어서(`DeleteObject`), 같은 키로 백업하면 앱이 털렸을 때 백업까지 지워질 수 있습니다. 업로드만 되는 백업 전용 IAM 키를 만듭니다. 당장 만들기 어려우면 앱 키 값을 넣어도 동작하지만, 그 키가 `backups/mysql/`에 올릴 권한이 있어야 합니다.
- **삭제 방지:** 버킷 버전 관리(또는 별도 백업 버킷 + Object Lock)를 켜 두면 덮어쓰거나 지워도 되살릴 수 있습니다.
- **보관 기간:** S3 버킷에 `backups/mysql/` 수명 주기 규칙(예: 30일 뒤 삭제)을 만듭니다. CronJob은 지우지 않습니다.
- **암호화:** S3는 새 객체를 기본으로 서버 측 암호화(SSE-S3)합니다. 버킷 설정에서 꺼져 있지 않은지 확인합니다.
- **DB 사용자 권한:** 앱 계정에 `chamabosal` DB의 SELECT, SHOW VIEW, TRIGGER, EVENT 권한이 있으면 됩니다 (`--no-tablespaces`로 PROCESS 권한은 쓰지 않음).
- **디스크:** 덤프 임시 파일은 MySQL과 같은 서버 디스크에 2Gi까지 씁니다. 압축한 덤프가 그보다 커지면 `sizeLimit`을 늘리거나 서버 디스크를 늘립니다.

## 적용과 수동 실행

```bash
kubectl apply -k k8s/backup

# 지금 바로 한 번 백업 (05:00 근처에는 하지 않음. 직접 만든 Job은 동시 실행 막기가 적용되지 않음)
kubectl -n chamabosal create job --from=cronjob/mysql-backup mysql-backup-manual-$(date +%s)
kubectl -n chamabosal get jobs
kubectl -n chamabosal logs job/<job 이름> -c dump
kubectl -n chamabosal logs job/<job 이름> -c upload
```

## 복원

aws CLI 자격 증명이 있는 곳에서 실행합니다. 지금 DB를 덮어쓰므로, 처음 해 보는 거라면 새 DB(`CREATE DATABASE restore_check`)에 먼저 복원해 건수를 확인합니다.

```bash
# 1. 백업 목록
aws s3 ls s3://<버킷>/backups/mysql/

# 2. 복원 중에 앱과 스케줄러가 쓰지 않게 앱을 내린다
kubectl -n chamabosal scale deployment/chamabosal-app --replicas=0

# 3. 받아서 MySQL에 넣기. 중간에 받기·압축 풀기가 실패하면 멈추게 pipefail을 켠다
#    (MySQL Pod 이름·계정 환경 변수는 k8s/mysql에 맞춤)
set -o pipefail
aws s3 cp s3://<버킷>/backups/mysql/<파일 이름> - | gunzip \
  | kubectl -n chamabosal exec -i statefulset/mysql -- \
      sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" chamabosal' \
  && echo "복원 완료" || echo "복원 실패: 앱을 올리기 전에 원인을 확인"

# 4. 건수를 확인한 뒤 앱을 다시 올린다
kubectl -n chamabosal scale deployment/chamabosal-app --replicas=1
```

## 로컬 k3d에서 확인하기

S3 대신 [S3Mock](https://github.com/adobe/S3Mock)을 띄우고, aws CLI가 `AWS_ENDPOINT_URL`로 그쪽에 올리게 합니다. Docker Desktop과 k3d(`brew install k3d`)가 필요합니다.

```bash
# 1. 클러스터, 임시 MySQL, S3Mock(버킷 local)
k3d cluster create chamabosal
kubectl create namespace chamabosal
kubectl -n chamabosal run mysql --image=mysql:8.4 --port=3306 \
  --env=MYSQL_DATABASE=chamabosal --env=MYSQL_USER=chamabosal \
  --env=MYSQL_PASSWORD=local --env=MYSQL_ROOT_PASSWORD=local
kubectl -n chamabosal expose pod mysql --port=3306
kubectl -n chamabosal run s3mock --image=adobe/s3mock:5.2.3 --port=9090 \
  --env=COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS=local
kubectl -n chamabosal expose pod s3mock --port=9090
kubectl -n chamabosal wait --for=condition=Ready pod/mysql pod/s3mock --timeout=240s

# 2. Secret (AWS_ENDPOINT_URL은 로컬에서만 넣음)
kubectl -n chamabosal create secret generic chamabosal-app-env \
  --from-literal=DB_USERNAME=chamabosal --from-literal=DB_PASSWORD=local
kubectl -n chamabosal create secret generic mysql-backup-s3 \
  --from-literal=AWS_ACCESS_KEY_ID=local --from-literal=AWS_SECRET_ACCESS_KEY=local \
  --from-literal=AWS_REGION=ap-northeast-2 --from-literal=BACKUP_S3_BUCKET=local \
  --from-literal=AWS_ENDPOINT_URL=http://s3mock:9090

# 3. 테스트 데이터 (MySQL이 준비되기까지 10초쯤 걸릴 수 있음)
kubectl -n chamabosal exec mysql -- sh -c 'MYSQL_PWD=local mysql -u chamabosal chamabosal \
  -e "CREATE TABLE rooms (id BIGINT PRIMARY KEY, name VARCHAR(50)); INSERT INTO rooms VALUES (1, \"배달 끊기 방\");"'

# 4. 백업 실행
kubectl apply -k k8s/backup
kubectl -n chamabosal create job --from=cronjob/mysql-backup manual-1
kubectl -n chamabosal wait --for=condition=complete job/manual-1 --timeout=300s
kubectl -n chamabosal logs job/manual-1 -c upload   # uploaded s3://local/backups/mysql/...

# 5. 새 DB에 복원해 확인
KEY=$(kubectl -n chamabosal run s3ls --rm -i --restart=Never --quiet --image=amazon/aws-cli:2.41.2 \
  --env=AWS_ACCESS_KEY_ID=local --env=AWS_SECRET_ACCESS_KEY=local \
  --env=AWS_REGION=ap-northeast-2 --env=AWS_ENDPOINT_URL=http://s3mock:9090 \
  -- s3 ls s3://local/backups/mysql/ | awk '{print $4}' | tail -1)
kubectl -n chamabosal exec mysql -- sh -c 'MYSQL_PWD=local mysql -u root -e "CREATE DATABASE restore_check"'
kubectl -n chamabosal run s3get --rm -i --restart=Never --quiet --image=amazon/aws-cli:2.41.2 \
  --env=AWS_ACCESS_KEY_ID=local --env=AWS_SECRET_ACCESS_KEY=local \
  --env=AWS_REGION=ap-northeast-2 --env=AWS_ENDPOINT_URL=http://s3mock:9090 \
  -- s3 cp "s3://local/backups/mysql/$KEY" - \
  | gunzip | kubectl -n chamabosal exec -i mysql -- sh -c 'MYSQL_PWD=local mysql -u root restore_check'
kubectl -n chamabosal exec mysql -- sh -c 'MYSQL_PWD=local mysql -u root restore_check -e "SELECT * FROM rooms"'

# 정리
k3d cluster delete chamabosal
```
