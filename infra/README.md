# infra

쿠버네티스(k3s) 서버를 만드는 방법입니다. 가비아는 Terraform provider가 없어서 서버·보안그룹은 콘솔에서 만들고 여기에 기록합니다. 서버 안 설정은 `ansible/`이 합니다.

## 가비아 콘솔에서 만든 것

| 항목 | 값 |
|---|---|
| 서버 | `chamabosal-k3s`, Standard 2vCore / 8GB, Ubuntu 24.04 LTS, 루트 50GB |
| 네트워크 | `chamabosal-vpc` / `chamabosal-app-subnet`(퍼블릭), 공인 IP `1.201.110.116` |
| 보안그룹 | `chamabosal-k3s-sg` 하나만 연결 (`Default-SecurityGroup`은 22·3389가 전체에 열려 있어 쓰지 않음) |
| 인바운드 | TCP 80·443 전체. TCP 22는 Ansible 첫 실행 동안만 작업자 IP `/32`로 열고 닫음. 6443은 열지 않음 |
| 아웃바운드 | 전체 허용 |
| DNS (가비아) | `api-k8s.chamabosal.com` A → 서버 공인 IP, TTL 300 |

방화벽은 보안그룹이 맡고 서버에서 ufw를 켜지 않습니다. ufw는 k3s의 Pod·Service 트래픽(FORWARD)을 막는 경우가 있습니다.

관리 접속(SSH, `kubectl`)은 Tailscale로만 합니다.

## Ansible 실행

준비

1. 로컬에 Ansible 설치: `brew install ansible`
2. Tailscale 관리 화면 Settings → Keys에서 auth key 발급 (Reusable 끔, Ephemeral 끔)
3. 보안그룹 22를 내 IP `/32`로 열기

실행 (레포 루트에서)

```bash
TS_AUTHKEY=tskey-auth-... ansible-playbook -i infra/ansible/inventory.ini infra/ansible/playbook.yml --private-key ~/.ssh/<서버 키>.pem
```

하는 일

- Tailscale 설치 후 `chamabosal-k3s`라는 이름으로 tailnet에 붙임
- k3s 설치 (`v1.36` 채널). API 서버 인증서에 공인 IP·Tailscale 이름·Tailscale IP를 넣음
- k3s 기본 Traefik에서 Gateway API provider를 켬. Gateway API CRD는 k3s에 들어 있어 따로 설치하지 않음
- kubeconfig를 `~/.kube/chamabosal-k3s.yaml`로 내려받고 주소를 Tailscale IP로 바꿈

끝난 뒤

1. 확인: `KUBECONFIG=~/.kube/chamabosal-k3s.yaml kubectl get nodes`
2. `inventory.ini`의 `ansible_host`를 Tailscale IP로 바꿈
3. 보안그룹에서 22 규칙 삭제

kubeconfig는 클러스터 관리자 권한이라 커밋하거나 공유하지 않습니다.
