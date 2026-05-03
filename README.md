# ecommerce-order-payment

커머스 주문 및 결제 도메인을 다루는 백엔드 서비스이다.

본 프로젝트는 주문 생성, 재고 차감, 결제 승인, 결제 결과 반영 과정에서 발생할 수 있는 정합성, 동시성, 멱등성, 복구성, 추적성 문제를 학습하고 검증하기 위한 목적으로 구성한다.

## 목표

이 서비스는 단순한 주문 CRUD 구현을 목표로 하지 않는다. 실제 커머스 시스템에서 문제가 되기 쉬운 주문 및 결제 처리 흐름을 중심으로, 다음 품질 속성을 코드와 테스트로 검증하는 것을 목표로 한다.

- 정합성: 주문, 재고, 결제 상태가 서로 모순되지 않도록 상태 전이와 데이터 제약을 관리한다.
- 동시성: 동일 상품에 대한 동시 주문 상황에서도 초과 판매가 발생하지 않도록 제어한다.
- 멱등성: 동일한 결제 요청이 반복되어도 중복 승인 또는 중복 처리가 발생하지 않도록 보장한다.
- 복구성: 처리 중 실패한 작업을 추적하고 재처리할 수 있는 구조를 제공한다.
- 추적성: 주문 및 결제 처리 흐름을 식별자, 로그, 상태 이력을 통해 추적할 수 있도록 구성한다.

## 기술 스택

- Java 17
- Spring Boot 3
- Gradle Multi-Module
- MySQL
- Docker Compose

## 모듈 구조

```text
ecommerce-order-payment
├── order-api
├── order-application
├── order-domain
├── order-infrastructure
├── docker
├── docs
└── docker-compose.yml
```

### 모듈 역할

| 모듈 | 역할 |
| --- | --- |
| `order-api` | HTTP API, 요청/응답 모델, 애플리케이션 부트스트랩을 담당한다. |
| `order-application` | 주문 및 결제 유스케이스, 트랜잭션 경계, 애플리케이션 서비스를 담당한다. |
| `order-domain` | 도메인 모델, 정책, 상태 전이 규칙, 불변식을 담당한다. |
| `order-infrastructure` | DB 접근, 외부 결제 연동, 메시징 및 기술 어댑터를 담당한다. |

## 로컬 실행

Docker Desktop 환경에서 전체 스택을 실행할 수 있다.

```bash
docker compose up -d
```

테스트를 실행한다.

```bash
./gradlew test
```

애플리케이션을 실행한다.

```bash
./gradlew :order-api:bootRun
```

Windows 환경에서는 다음 명령을 사용할 수 있다.

```bash
.\gradlew.bat test
.\gradlew.bat :order-api:bootRun
```

## 상태 확인 API

```http
GET /health
```

응답 예시는 다음과 같다.

```json
{
  "status": "UP"
}
```
