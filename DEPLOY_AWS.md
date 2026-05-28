# WeMeet AWS Deploy

이 프로젝트는 `AWS + Docker + RDS MariaDB + ElastiCache Redis + ALB HTTPS 종료` 기준으로 배포하도록 정리되어 있다.

## 1. 권장 아키텍처

- 애플리케이션: EC2 또는 ECS에 Docker 컨테이너로 배포
- RDBMS: Amazon RDS for MariaDB
- Redis: Amazon ElastiCache for Redis
- HTTPS: Application Load Balancer 에서 TLS 종료

## 2. 컨테이너 이미지 빌드

```bash
docker build -t wemeet:latest .
```

ECR 사용 시:

```bash
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin <account>.dkr.ecr.ap-northeast-2.amazonaws.com
docker tag wemeet:latest <account>.dkr.ecr.ap-northeast-2.amazonaws.com/wemeet:latest
docker push <account>.dkr.ecr.ap-northeast-2.amazonaws.com/wemeet:latest
```

## 3. EC2에 배포

1. `deploy/aws/.env.example` 을 `deploy/aws/.env` 로 복사하고 실제 값으로 수정한다.
2. `ECR_IMAGE` 값을 푸시한 이미지 URI로 바꾼다.
3. `APP_SERVER_PORT=11000`, `HOST_PORT=11000` 기준으로 애플리케이션 포트를 맞춘다.
4. `CANONICAL_HOST` 는 대표 도메인으로 설정한다. 예: `wemeet.ai.kr`
5. EC2에서 다음을 실행한다.

```bash
docker compose -f deploy/aws/compose.ec2.yaml --env-file deploy/aws/.env up -d
```

## 4. HTTPS

- ALB 리스너 `80 -> 443 redirect`
- ALB 리스너 `443 -> target group :11000`
- ACM 인증서를 443 리스너에 연결
- 애플리케이션은 `server.forward-headers-strategy=framework`, `app.security.require-https=true`, `app.security.canonical-host` 로 프록시 뒤에서 HTTPS와 대표 도메인을 강제한다.

## 5. 운영 프로필

- 운영 배포는 반드시 `SPRING_PROFILES_ACTIVE=aws`
- `src/main/resources/application-aws.properties` 에서 RDS, Redis, HTTPS 강제 옵션을 사용한다.

## 6. 과제 기준 대응

- Spring Boot: Docker 컨테이너로 AWS 배포 가능
- MariaDB: RDS MariaDB 연결
- Redis: 세션/추천 캐시 저장
- Open API: 서버측 Nominatim/OSRM REST 호출
- HTTPS: ALB + ACM + 앱 내부 HTTPS 강제 설정
