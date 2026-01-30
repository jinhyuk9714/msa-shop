# MSA Shop — 면접용 정리

> 이 프로젝트를 면접에서 말할 때 참고할 수 있도록 정리한 문서입니다.  
> 본인 말투로 바꿔서 사용하시면 됩니다.

---

## 1. 프로젝트 한 줄 / 30초 / 1분 요약

### 한 줄
쇼핑몰 도메인으로 MSA를 연습한 프로젝트입니다. 서비스 경계 설계, SAGA·Outbox 기반 주문·결제 흐름, API Gateway·이벤트·관측성까지 경험했습니다.

### 30초
쇼핑몰을 6개 마이크로서비스로 나누고, API Gateway를 단일 진입점으로 두었습니다. 주문 시 재고 예약 → 결제 → 주문 저장 순서로 가고, 실패 시 재고 복구 같은 보상 트랜잭션을 넣었고, 결제 완료는 RabbitMQ로 정산 서비스에 넘겨서 일·월 매출을 집계하게 했습니다. Docker Compose와 Kubernetes·Helm으로 배포하고, CI에서 단위 테스트와 E2E까지 돌리도록 했습니다.

### 1분
쇼핑몰을 User, Product, Order, Payment, Settlement 다섯 서비스와 API Gateway로 나눠서 구현했습니다. 각 서비스는 DB를 따로 쓰고, 클라이언트는 Gateway 한 곳으로만 요청합니다. 주문 플로우는 order-service가 product-service에 재고 예약을 하고, payment-service에 결제를 요청한 뒤, 성공하면 주문을 저장합니다. 재고 부족이나 결제 실패 시에는 이미 예약한 재고를 복구하는 식으로 SAGA 패턴을 적용했고, 결제는 성공했는데 주문 저장이 실패하는 경우를 대비해 Outbox 테이블에 보상 이벤트를 넣고 스케줄러가 결제 취소·재고 복구를 하도록 했습니다. 결제 완료는 RabbitMQ로 발행하고 settlement-service가 구독해서 일별·월별 매출을 집계합니다. Gateway에서는 JWT 검증, Rate Limit, 통합 Swagger를 두었고, Prometheus·Grafana·Zipkin으로 관측성을 넣었습니다. 로컬·Docker Compose·K8s·Helm으로 실행·배포하고, CI에서 테스트와 E2E까지 자동으로 돌리게 해뒀습니다.

---

## 2. 자주 나올 만한 질문 + 답변 포인트

### "이 프로젝트에서 본인이 한 역할은?"
전체 설계와 구현을 혼자 진행했습니다. 서비스 경계 정리, API 스펙, 주문·결제·정산 플로우, Gateway·이벤트·Outbox·장바구니, Docker/K8s/Helm·CI·E2E·문서화까지 모두 포함합니다.

### "왜 서비스를 이렇게 나눴나요?"
도메인 단위로 나눴습니다. User(회원·인증), Product(상품·재고), Order(주문·장바구니), Payment(결제), Settlement(정산·매출)는 각각 다른 팀이 담당할 수 있는 경계라고 보고, DB도 서비스별로 분리해서 독립 배포·확장을 가정했습니다.

### "주문이 실패하면 어떻게 되나요?"
재고 예약 실패면 그 시점에서 409로 끝내고, 결제 실패면 이미 예약한 재고를 product-service의 release API로 복구한 뒤 402로 응답합니다. 결제는 성공했는데 주문 저장이 실패하는 경우는 Outbox에 “이 주문은 결제 취소·재고 복구 필요”를 적어두고, 스케줄러가 주기적으로 읽어서 payment 취소·재고 복구를 호출하도록 했습니다.

### "SAGA랑 Outbox를 왜 쓰나요?"
서비스마다 DB가 달라서 한 트랜잭션으로 묶을 수 없어서요. 주문·결제·재고가 각각 다른 DB에 있으니까, 한 단계라도 실패하면 이전 단계를 되돌려야 합니다. 그래서 SAGA로 보상 단계를 정의했고, “결제 성공 후 주문 저장 실패”처럼 이미 완료된 작업을 나중에 되돌려야 하는 경우는 메시지를 바로 보내지 않고 Outbox에 먼저 적어두고, DB 커밋 후 스케줄러가 처리하게 해서 “DB는 저장됐는데 메시지는 안 나간” 불일치를 피하려고 했습니다.

### "API Gateway에서 뭘 하나요?"
단일 진입점으로 라우팅하고, `/orders/**`, `/cart/**`, `/users/me` 등은 JWT 검증 후 `X-User-Id`를 헤더에 넣어서 백엔드로 넘깁니다. Rate Limit(IP당 분당 횟수)과 통합 Swagger UI도 Gateway에서 처리합니다.

### "이벤트는 어떻게 쓰나요?"
결제 완료 시 payment-service가 RabbitMQ Topic에 이벤트를 발행하고, settlement-service가 구독해서 일별·월별 매출 집계를 갱신합니다. 주문·결제는 동기 REST로 하고, 정산만 이벤트로 비동기 처리했습니다.

### "테스트는 어떻게 하나요?"
단위 테스트와 Testcontainers로 하는 통합 테스트를 Gradle로 돌리고, CI에서 그다음에 Docker Compose로 전체를 띄운 뒤 E2E 스크립트(회원가입·로그인·주문·장바구니 시나리오 등)를 실행해서 흐름이 끝까지 되는지 확인합니다.

### "배포는 어떻게 하나요?"
로컬에서는 각 서비스를 bootRun으로, 전체는 Docker Compose로 띄웁니다. Kubernetes는 k8s 매니페스트와 Helm 차트를 두었고, Helm으로 한 번에 설치·업그레이드할 수 있게 해뒀습니다. main 브랜치 푸시 시 GitHub Actions에서 6개 서비스 이미지를 빌드해 ghcr.io에 푸시하는 워크플로도 있습니다.

---

## 3. 기술 선택·역할 한 줄 정리

| 기술 | 역할 |
|------|------|
| Spring Boot 3.5, Java 21 | 공통 런타임 |
| Spring Cloud Gateway | 단일 진입점, 라우팅·JWT·Rate Limit |
| Spring Data JPA | 서비스별 DB 접근 |
| Resilience4j | order-service에서 product/payment 호출 시 Retry·CircuitBreaker |
| RabbitMQ | 결제 완료 이벤트 → 정산 구독 |
| JWT (JJWT, HS256) | 로그인 후 토큰 발급, Gateway에서 검증·X-User-Id 전달 |
| Redis | product-service 상품 목록·단건 캐시(선택) |
| MySQL 8 | Docker/K8s에서 서비스별 DB |
| Prometheus·Grafana·Zipkin | 메트릭·대시보드·분산 추적 |
| Docker Compose·K8s·Helm | 로컬 전체 실행·클러스터 배포 |
| GitHub Actions | CI(테스트·E2E), 이미지 빌드·푸시 |

---

## 4. 아키텍처·플로우 요약 (말로 설명할 때)

- **서비스**: user, product, order, payment, settlement + api-gateway. 각자 DB 하나씩.
- **주문 성공 흐름**: 클라이언트 → Gateway → order-service → (재고 예약 → 결제 요청 → 성공 시 주문 저장) → 응답.
- **실패 시**: 재고 실패 → 409. 결제 실패 → 재고 복구 후 402. 주문 저장 실패(결제는 성공) → Outbox에 보상 이벤트 → 스케줄러가 결제 취소·재고 복구.
- **정산**: payment-service가 결제 완료 시 RabbitMQ 발행 → settlement-service가 구독해 일·월 매출 집계.

---

## 5. 어려웠던 점 / 트러블슈팅 (말할 거리)

- **POST /orders/from-cart가 500으로 떨어졌을 때**: Spring에서 `GET /orders/{id}`에 "from-cart"가 path variable로 매칭되면서 POST가 지원되지 않는다는 응답이 나왔습니다. `@GetMapping("/{id:\\d+}")`처럼 id를 숫자만 허용하도록 바꿔서 해결했습니다.
- **Docker Compose 빌드 시간**: 각 서비스 Dockerfile에서 매번 Gradle을 돌리다 보니 첫 빌드가 오래 걸렸습니다. BuildKit 캐시 마운트(`--mount=type=cache,target=/root/.gradle/caches` 등)를 넣어서 이후 빌드는 빨라지게 했습니다.
- **서비스 간 일관성**: 주문·결제·재고가 서로 다른 DB라서, “결제만 성공하고 주문 저장은 실패” 같은 경우를 Outbox로 보상 처리하도록 설계하는 부분을 신경 썼습니다.

(본인이 더 기억나는 트러블슈팅이 있으면 여기에 한두 개 추가해 두면 좋습니다.)

---

## 6. 이 프로젝트로 보여주고 싶은 것

- MSA 서비스 경계를 도메인 단위로 나누고, 각 서비스가 독립 DB를 가지는 구조를 설계·구현한 경험
- 분산 환경에서 주문·결제 일관성을 SAGA·Outbox로 처리한 경험
- API Gateway를 통한 인증·라우팅·Rate Limit 적용
- 이벤트 기반 정산( RabbitMQ ) 경험
- Docker·K8s·Helm·CI·E2E까지 포함한 배포·품질 파이프라인

---

## 7. 참고 문서 (면접 전에 다시 볼 것)

- [ARCHITECTURE.md](ARCHITECTURE.md) — 서비스 구성·주문 플로우
- [IMPLEMENTED-SUMMARY.md](IMPLEMENTED-SUMMARY.md) — API·기능 전체 요약
- [FAILURE-SCENARIOS.md](FAILURE-SCENARIOS.md) — 장애·실패 시나리오
