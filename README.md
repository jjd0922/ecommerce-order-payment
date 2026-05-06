# ecommerce-order-payment

Spring Boot, JPA, MySQL 기반의 주문-결제(Mock)-재고예약 포트폴리오 프로젝트이다.

단순 CRUD 구현보다 실제 커머스 주문/결제 흐름에서 자주 문제가 되는 정합성, 동시성, 멱등성, 복구성, 추적성을 중심으로 설계한다.

## 프로젝트 목표

- 주문 생성, 결제 준비, 결제 승인/실패, 재고 예약 만료 복구 흐름을 구성한다.
- 결제 준비 단계에서 재고를 선예약하여 초과 판매를 방지한다.
- 결제 이탈 또는 실패 시 재고 예약을 해제하고 재고를 복구한다.
- 실제 PG 연동 대신 Mock 결제를 사용하되, PG 교체가 가능하도록 Port 기반으로 분리한다.
- 도메인 이벤트와 Outbox 패턴으로 상태 변경을 추적하고 장애 복구 가능성을 확보한다.
- API 요청 단위 추적을 위해 `X-Request-Id`와 MDC를 연계한다.

## 기술 스택

- Java 17
- Spring Boot 3.4
- Spring Web
- Spring Data JPA
- MySQL 8.0
- Gradle Multi-Module
- Docker Compose
- JUnit 5

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

| 모듈 | 책임 |
| --- | --- |
| `order-api` | HTTP API, 요청/응답 DTO, 공통 오류 응답, 요청 추적 필터 |
| `order-application` | UseCase, 트랜잭션 경계, Port 정의, 애플리케이션 서비스 |
| `order-domain` | 주문, 결제, 상품, 재고, 재고예약 도메인 모델과 상태 전이 규칙 |
| `order-infrastructure` | JPA Adapter, MySQL Repository, Mock 결제, Outbox 저장/릴레이 |

## 핵심 흐름

```text
1. 주문 생성
   -> 상품 조회
   -> 판매 가능 여부 검증
   -> 주문 CREATED 생성

2. 결제 준비
   -> 주문 조회
   -> Idempotency-Key 기준 기존 결제 조회
   -> 주문 상품별 재고 선예약
   -> 주문 PAYMENT_PENDING 전이
   -> 결제 READY 생성
   -> 도메인 이벤트 Outbox 저장

3. 결제 승인
   -> 결제 조회
   -> Idempotency-Key 검증
   -> Mock 결제 승인 요청
   -> 결제 APPROVED 전이
   -> 주문 PAID 전이
   -> 재고 예약 CONFIRMED 전이
   -> 도메인 이벤트 Outbox 저장

4. 결제 실패
   -> 결제 FAILED 전이
   -> 주문 FAILED 전이
   -> 재고 예약 RELEASED 전이
   -> held 재고를 available 재고로 복구

5. 예약 만료 복구
   -> 만료된 HELD 예약 조회
   -> 재고 복구
   -> 예약 EXPIRED 전이
   -> 만료 이벤트 Outbox 저장
```

## 핵심 설계 포인트

| 항목 | 구현 근거 |
| --- | --- |
| 정합성 | 주문, 결제, 재고예약 상태 변경을 UseCase 트랜잭션 경계에서 처리 |
| 동시성 | `available_quantity >= quantity` 조건부 update로 재고 선점 |
| 멱등성 | 결제 준비/승인 API에서 `Idempotency-Key` 사용 |
| 복구성 | 재고 예약 만료 복구 스케줄러와 Outbox 이벤트 릴레이 구성 |
| 추적성 | 도메인 이벤트, Outbox, `X-Request-Id`, MDC 연계 |

## 주요 API

### 주문 생성

```http
POST /orders
Content-Type: application/json
```

```json
{
  "orderLines": [
    {
      "productId": "11111111-1111-1111-1111-111111111111",
      "quantity": 2
    }
  ]
}
```

### 결제 준비

```http
POST /payments/prepare
Content-Type: application/json
Idempotency-Key: payment-request-1
```

```json
{
  "orderId": "22222222-2222-2222-2222-222222222222"
}
```

### 결제 승인

```http
POST /payments/{paymentId}/confirm
Idempotency-Key: payment-request-1
```

### 재고 예약 만료 수동 실행

```http
POST /admin/inventory-reservations/expire
```

## 요청 추적

모든 API 응답에는 `X-Request-Id` 헤더가 포함된다.

- 클라이언트가 `X-Request-Id`를 전달하면 해당 값을 그대로 응답한다.
- 전달하지 않으면 서버에서 UUID를 생성한다.
- 요청 처리 중 MDC `requestId`에 저장하여 로그와 요청을 연결한다.
- 오류 응답에도 `requestId`를 포함한다.

## 로컬 실행

MySQL을 실행한다.

```bash
docker compose up -d
```

테스트를 실행한다.

```bash
./gradlew test
```

Windows 환경에서는 다음 명령을 사용한다.

```bash
.\gradlew.bat test
```

애플리케이션을 실행한다.

```bash
./gradlew :order-api:bootRun
```

Windows 환경에서는 다음 명령을 사용한다.

```bash
.\gradlew.bat :order-api:bootRun
```

## 테스트

- 주문 생성 UseCase 테스트
- 결제 준비 UseCase 테스트
- 결제 승인/실패 UseCase 테스트
- 재고 예약 만료 복구 테스트
- API Controller 테스트
- 요청 추적 필터 테스트
- 재고 예약 동시성 통합 테스트

재고 예약 동시성 통합 테스트는 로컬 MySQL이 실행 중이면 실제 DB 조건부 update를 검증한다. MySQL에 연결할 수 없으면 JUnit assumption으로 skip된다.

## 상세 문서

- [Notion 상세 문서 초안](docs/notion-project-document.md)
