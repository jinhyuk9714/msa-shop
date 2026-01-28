## MSA Shop

간단한 쇼핑몰 도메인을 기반으로 한 **마이크로서비스 아키텍처(MSA) 연습 프로젝트**입니다.  
회원, 상품, 주문, 결제, 정산까지의 흐름을 여러 개의 서비스로 나누어 구현하는 것을 목표로 합니다.

### 목표

- User / Product / Order / Payment / Settlement 로 서비스 경계 나누기
- 서비스 간 통신 (REST + Resilience4j)
- 주문/결제 흐름에서의 SAGA/보상 트랜잭션 개념 맛보기
- 결제 완료 이벤트 기반 정산/매출 집계 배치 설계

자세한 설계와 흐름은 `docs/ARCHITECTURE.md` 를 참고하세요.
