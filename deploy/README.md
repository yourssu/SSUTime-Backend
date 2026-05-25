# EC2 Docker 배포 설정

이 배포 구성은 GitHub Actions에서 Docker 이미지를 ECR Public Registry에 push한 뒤,
브랜치별 EC2 서버에 SSH로 접속해 `docker pull` 후 컨테이너를 재기동하는 방식입니다.

- `main` 브랜치: 운영 EC2 서버 배포
- `develop` 브랜치: 개발 EC2 서버 배포
- GitHub Actions: Gradle 테스트/빌드 -> Docker 이미지 빌드 -> ECR Public push -> EC2 docker pull/run

## GitHub Secrets

Repository secrets 또는 GitHub Environments(`production`, `development`)에 아래 값을 설정하세요.

```text
AWS_ROLE_TO_ASSUME=arn:aws:iam::<account-id>:role/<github-actions-deploy-role>
ECR_PUBLIC_ALIAS=<ecr-public-alias>
ECR_PUBLIC_REPOSITORY=ssutime-api

EC2_HOST_PROD=<prod-ec2-public-ip-or-domain>
EC2_USER_PROD=ubuntu
EC2_SSH_KEY_PROD=<prod-private-key-pem>

EC2_HOST_DEV=<dev-ec2-public-ip-or-domain>
EC2_USER_DEV=ubuntu
EC2_SSH_KEY_DEV=<dev-private-key-pem>
```

`AWS_ROLE_TO_ASSUME`는 ECR Public에 push할 수 있어야 합니다.

## ECR Public Registry

ECR Public repository를 먼저 생성하세요.

```text
public.ecr.aws/<ecr-public-alias>/ssutime-api
```

workflow는 아래 두 태그를 push합니다.

```text
public.ecr.aws/<alias>/ssutime-api:<branch>-<commit-sha>
public.ecr.aws/<alias>/ssutime-api:<branch>-latest
```

예:

```text
public.ecr.aws/abc1def2/ssutime-api:main-latest
public.ecr.aws/abc1def2/ssutime-api:develop-latest
```

## EC2 서버 준비

EC2에는 Docker가 설치되어 있어야 하고, GitHub Actions에서 접속할 SSH key가 등록되어 있어야 합니다.

앱 환경변수 파일은 서버에 직접 만들어 둡니다.

```bash
sudo mkdir -p /opt/ssutime
sudo vi /opt/ssutime/.env
sudo chmod 600 /opt/ssutime/.env
```

`/opt/ssutime/.env` 예시:

```text
DATABASE_URL=jdbc:mysql://<rds-endpoint>:3306/ssutime?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DATABASE_USERNAME=<mysql-user>
DATABASE_PASSWORD=<mysql-password>
DATABASE_DRIVER=com.mysql.cj.jdbc.Driver
JWT_SECRET=<minimum-32-char-secret>
JWT_EXPIRY_MINUTES=60
ANTHROPIC_API_KEY=<anthropic-api-key>
GOOGLE_APPLICATION_CREDENTIALS_JSON=<firebase-service-account-json>
# LMS 수집 silent push 주기(분). 운영 기본값은 15, 개발서버에서 1로 설정하면 매분 전체 대상에게 전송됩니다.
CRAWL_TRIGGER_INTERVAL_MINUTES=15
```

Spring Boot는 컨테이너 내부에서 기본 포트 `8080`으로 실행됩니다. 외부 노출 포트는 Docker와 Nginx에서만 조정합니다.

보안 그룹에서 필요한 포트를 열어야 합니다.

```text
22: GitHub Actions runner에서 SSH 접속
80: 외부 HTTP 요청을 받는 Nginx
```

기본 Docker 포트 매핑은 아래와 같습니다.

```text
127.0.0.1:9090 -> container 8080
```

즉 외부 요청은 `80 -> Nginx -> 127.0.0.1:9090 -> Docker container 8080` 흐름으로 들어갑니다.

Nginx location 예시:

```nginx
location / {
    proxy_pass http://127.0.0.1:9090;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

## Firebase Admin SDK

EC2에서는 `GOOGLE_APPLICATION_CREDENTIALS_JSON`에 Firebase service account JSON 전체를 환경변수로 주입합니다.

로컬 실행에서는 기존처럼 `FCM_CREDENTIALS_PATH=/path/to/service-account.json`도 사용할 수 있습니다.

서비스 계정 JSON은 이미지에 굽거나 git에 커밋하지 마세요.

## LMS 수집 트리거 주기

앱은 매분 스케줄러를 실행하고, `CRAWL_TRIGGER_INTERVAL_MINUTES` 값으로 사용자 버킷을 나눠 silent push를 보냅니다.

- 기본값: `15` — 사용자별 약 15분마다 1회 전송
- 개발서버: `1` — 매분 전체 대상에게 전송

개발서버에서 1분 단위로 테스트하려면 EC2의 `/opt/ssutime/.env`에 아래 값을 넣고 컨테이너를 재기동하세요.

```text
CRAWL_TRIGGER_INTERVAL_MINUTES=1
```

## 수동 배포 확인

EC2에서 직접 확인할 때는 아래 형태로 실행할 수 있습니다.

```bash
docker pull public.ecr.aws/<alias>/ssutime-api:main-latest
docker run -d \
  --name ssutime-api \
  --restart unless-stopped \
  --env-file /opt/ssutime/.env \
  -p 127.0.0.1:9090:8080 \
  public.ecr.aws/<alias>/ssutime-api:main-latest
```
