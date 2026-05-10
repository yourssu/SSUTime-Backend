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
```

보안 그룹에서 필요한 포트를 열어야 합니다.

```text
22: GitHub Actions runner에서 SSH 접속
9090: 애플리케이션 직접 접근 또는 ALB/Nginx에서 내부 접근
```

기본 Docker 포트 매핑은 아래와 같습니다.

```text
0.0.0.0:9090 -> container 8080
```

외부에 직접 열지 않고 EC2 내부에서만 프록시하려면 workflow의 `HOST_BIND_ADDRESS`를 `127.0.0.1`로 바꾸세요.

## Firebase Admin SDK

EC2에서는 `GOOGLE_APPLICATION_CREDENTIALS_JSON`에 Firebase service account JSON 전체를 환경변수로 주입합니다.

로컬 실행에서는 기존처럼 `FCM_CREDENTIALS_PATH=/path/to/service-account.json`도 사용할 수 있습니다.

서비스 계정 JSON은 이미지에 굽거나 git에 커밋하지 마세요.

## 수동 배포 확인

EC2에서 직접 확인할 때는 아래 형태로 실행할 수 있습니다.

```bash
docker pull public.ecr.aws/<alias>/ssutime-api:main-latest
docker run -d \
  --name ssutime-api \
  --restart unless-stopped \
  --env-file /opt/ssutime/.env \
  -p 0.0.0.0:9090:8080 \
  public.ecr.aws/<alias>/ssutime-api:main-latest
```
