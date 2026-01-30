# 프로파일·시크릿 관리

프로파일 분리와 비밀값(시크릿) 관리 방식을 정리합니다.

---

## 1. Spring 프로파일

### default (로컬 bootRun)

- **활성**: 프로파일 미지정 또는 `--spring.profiles.active=default`
- **용도**: 로컬에서 `./gradlew :user-service:bootRun` 등으로 기동할 때
- **DB**: H2 메모리 (`application.yml` 기본값)
- **JWT·RabbitMQ 등**: `application.yml` 기본값 또는 `${VAR:default}` 형태로 env 미설정 시 로컬용 값 사용
- **H2 콘솔**: 사용 가능
- **로그**: Spring 기본 (root INFO 등)

### prod (Docker / K8s)

- **활성**: `SPRING_PROFILES_ACTIVE=prod` (Docker Compose·K8s에서 설정)
- **용도**: Docker Compose, Kubernetes 등 컨테이너 환경
- **DB·RabbitMQ**: `SPRING_DATASOURCE_*`, `SPRING_RABBITMQ_*` 등 **환경 변수 필수** (기본값 없음)
- **JWT**: `APP_JWT_SECRET` 환경 변수 필수. `application-prod.yml`에서 기본값 없이 참조.
- **H2 콘솔**: 비활성
- **로그**: `logging.level.root=WARN`, `com.msa=INFO`로 축소

---

## 2. 시크릿(비밀값) 관리

### 저장소에 넣지 말 것

- **JWT 시크릿** (`app.jwt.secret`): 32자 이상. user-service, order-service, api-gateway가 동일 값 사용.
- **DB 비밀번호** (`SPRING_DATASOURCE_PASSWORD` 등)
- **RabbitMQ 비밀번호** (`SPRING_RABBITMQ_PASSWORD` 등)

`application.yml`·`application-prod.yml`에는 **변수 참조**(`${APP_JWT_SECRET}` 등)만 두고, 실제 값은 환경 변수 또는 시크릿 저장소에서 주입합니다.

### 로컬 (bootRun)

- `APP_JWT_SECRET` 등 미설정 시 `application.yml`의 `${APP_JWT_SECRET:...}` **기본값** 사용 (로컬 전용).
- `.env` 파일 사용 시 `export APP_JWT_SECRET=...` 후 실행. `.env`는 `.gitignore`에 두는 것을 권장.

### Docker Compose

- `docker-compose.yml`의 `environment`에서 `APP_JWT_SECRET`, `SPRING_DATASOURCE_PASSWORD` 등 설정.
- **실제 운영** 시에는 `env_file:`로 `.env` 참조하거나, Docker Secrets 등으로 넘기는 방식을 권장. `.env`는 커밋하지 않습니다.

### Kubernetes

- **Secret** 리소스 (`k8s/00-secret.yaml`)에 `mysql-root-password`, `jwt-secret`, `rabbitmq-password` 등 저장.
- Deployment에서 `env.valueFrom.secretKeyRef`로 참조. 매니페스트에 평문 비밀을 적지 않습니다.
- 운영 시 Sealed Secrets, External Secrets 등으로 Secret을 관리하는 구성 권장.

---

## 3. prod에서 필요한 환경 변수 요약

| 변수                         | 사용 서비스                               | 비고               |
| ---------------------------- | ----------------------------------------- | ------------------ |
| `APP_JWT_SECRET`             | user, order, api-gateway                  | 32자 이상, 동일 값 |
| `SPRING_DATASOURCE_URL`      | user, product, order, payment, settlement | JDBC URL           |
| `SPRING_DATASOURCE_USERNAME` | 위 동일                                   |                    |
| `SPRING_DATASOURCE_PASSWORD` | 위 동일                                   |                    |
| `SPRING_RABBITMQ_HOST`       | payment, settlement                       |                    |
| `SPRING_RABBITMQ_PORT`       | payment, settlement                       |                    |
| `SPRING_RABBITMQ_USERNAME`   | payment, settlement                       |                    |
| `SPRING_RABBITMQ_PASSWORD`   | payment, settlement                       |                    |
| `PRODUCT_SERVICE_BASE_URL`   | order                                     | prod에서 필수      |
| `PAYMENT_SERVICE_BASE_URL`   | order                                     | prod에서 필수      |

---

## 4. 프로파일 실행 예시

```bash
# 로컬 (default)
./gradlew :user-service:bootRun

# 로컬에서 prod 프로파일 사용 (env 필수)
APP_JWT_SECRET=your-secret-32chars-minimum!! ./gradlew :user-service:bootRun --args='--spring.profiles.active=prod'
```

Docker Compose·K8s에서는 `SPRING_PROFILES_ACTIVE=prod`를 설정해 두었으므로, 위 환경 변수들만 채우면 prod 설정이 적용됩니다.
