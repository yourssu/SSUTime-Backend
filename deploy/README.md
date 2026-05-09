# ECS 배포 설정

이 배포 구성은 ECS on EC2 launch type 기준입니다.

- `main` 브랜치: 운영 ECS 서비스 배포
- `develop` 브랜치: 개발 ECS 서비스 배포
- GitHub Actions: Gradle 테스트/빌드 -> Docker 이미지 빌드 -> ECR push -> ECS service update

## GitHub Secrets

Repository secrets 또는 GitHub Environments(`production`, `development`)에 아래 값을 설정하세요.

```text
AWS_REGION=ap-northeast-2
AWS_ROLE_TO_ASSUME=arn:aws:iam::<account-id>:role/<github-actions-deploy-role>
ECR_REPOSITORY=ssutime-api
ECS_CLUSTER_PROD=<prod-cluster-name>
ECS_SERVICE_PROD=<prod-service-name>
ECS_CLUSTER_DEV=<dev-cluster-name>
ECS_SERVICE_DEV=<dev-service-name>
```

## Task definition 수정

`deploy/ecs-task-dev.json`, `deploy/ecs-task-prod.json` 안의 `ACCOUNT_ID`, IAM role ARN, region, SSM parameter ARN을 실제 값으로 바꾸세요.

## SSM Parameter Store

예시는 아래 경로를 사용합니다.

```text
/ssutime/dev/DATABASE_URL
/ssutime/dev/DATABASE_USERNAME
/ssutime/dev/DATABASE_PASSWORD
/ssutime/dev/JWT_SECRET
/ssutime/dev/ANTHROPIC_API_KEY
/ssutime/dev/GOOGLE_APPLICATION_CREDENTIALS_JSON

/ssutime/prod/DATABASE_URL
/ssutime/prod/DATABASE_USERNAME
/ssutime/prod/DATABASE_PASSWORD
/ssutime/prod/JWT_SECRET
/ssutime/prod/ANTHROPIC_API_KEY
/ssutime/prod/GOOGLE_APPLICATION_CREDENTIALS_JSON
```

`DATABASE_URL` 예시:

```text
jdbc:mysql://<rds-endpoint>:3306/ssutime?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
```

## Firebase Admin SDK

ECS에서는 `GOOGLE_APPLICATION_CREDENTIALS_JSON`에 Firebase service account JSON 전체를 SSM SecureString 또는 Secrets Manager secret으로 저장해 주입하세요.

로컬 실행에서는 기존처럼 `FCM_CREDENTIALS_PATH=/path/to/service-account.json`도 사용할 수 있습니다.

서비스 계정 JSON은 이미지에 굽거나 git에 커밋하지 마세요.
