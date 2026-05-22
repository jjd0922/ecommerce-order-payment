# ecommerce-order-payment

[![CI](https://github.com/jjd0922/ecommerce-order-payment/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jjd0922/ecommerce-order-payment/actions/workflows/ci.yml)
[![Coverage](https://codecov.io/gh/jjd0922/ecommerce-order-payment/branch/main/graph/badge.svg)](https://codecov.io/gh/jjd0922/ecommerce-order-payment)
[![Java](https://img.shields.io/badge/Java-17-orange)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-green)]()
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)]()

주문-결제(Mock)-재고예약 흐름에서 정합성, 동시성, 멱등성, 복구성을 다루는 Spring Boot 기반 백엔드 포트폴리오 프로젝트입니다.

상세 흐름, 아키텍처 다이어그램, 오류 코드, 보안 범위, ADR은 [Documents](#documents)를 참고하세요.

## Highlights

- 결제 준비 단계에서 재고를 선예약하고, MySQL 조건부 update로 초과 판매를 방지
- 주문에 여러 상품이 포함되어도 `productId` 정렬로 재고 row 락 획득 순서 고정
- 결제 준비/승인 API에 공용 `idempotency_record` 기반 멱등성 처리 적용
- 결제 승인 외부 호출은 DB 트랜잭션 밖에서 수행하고, 결과 반영은 짧은 트랜잭션으로 분리
- payment row 비관적 락으로 같은 결제에 대한 중복 승인 요청 방어
- Outbox 저장, polling relay, `FAILED` 이벤트 재시도로 이벤트 발행 복구성 확보
- 재고 예약 만료 스케줄러와 수동 관리자 API로 결제 이탈 재고 복구
- Testcontainers 기반 MySQL 동시성 통합 테스트와 ArchUnit 계층 의존성 검증
- `X-Request-Id`, MDC, Actuator/Prometheus endpoint로 요청 추적과 기본 운영 관측성 구성

## Quick Start

```bash
docker compose up -d
./gradlew :order-api:bootRun
```

Windows:

```bash
docker compose up -d
.\gradlew.bat :order-api:bootRun
```

```text
API                  http://localhost:8080
Health               http://localhost:8080/actuator/health
Prometheus metrics   http://localhost:8080/actuator/prometheus
```

## Test

```bash
./gradlew test
```

Windows:

```bash
.\gradlew.bat test
```

테스트는 도메인 규칙, 유스케이스 흐름, API 계약, 요청 추적, 계층 의존성, MySQL 동시성 제어를 검증합니다.

## Tech Stack

| 영역 | 사용 기술 |
| --- | --- |
| 언어/런타임 | Java 17 |
| 프레임워크 | Spring Boot 3.4.5, Spring Web, Spring Data JPA, Validation, Actuator |
| DB | MySQL 8.0 |
| 아키텍처 | Gradle Multi-Module, Port 기반 계층 분리 |
| 관측성 | Micrometer, Prometheus registry, MDC request tracing |
| 테스트 | JUnit 5, AssertJ, Mockito, Testcontainers, ArchUnit |
| 로컬 실행 | Docker Compose, Gradle |

## Module

```text
ecommerce-order-payment
├── order-api              # HTTP API, 요청/응답 DTO, 오류 응답, 요청 추적
├── order-application      # UseCase, 트랜잭션 경계, Port 정의
├── order-domain           # 주문, 결제, 상품, 재고, 재고예약 도메인 규칙
├── order-infrastructure   # JPA adapter, Mock 결제, Outbox, 스케줄러
├── docker                 # MySQL schema/seed
├── docs                   # 설계 문서와 ADR
└── docker-compose.yml
```

## Main API

```http
POST /v1/orders
POST /v1/payments/prepare
POST /v1/payments/{paymentId}/confirm
POST /v1/admin/inventory-reservations/expire
```

결제 준비와 결제 승인은 각각 다른 `Idempotency-Key`를 사용합니다. 멱등키는 공용 레코드의 기본 키로 저장되고, 같은 키에 다른 요청 hash가 들어오면 충돌로 거부합니다.

## Documents

- [아키텍처 다이어그램](docs/architecture-diagrams.md)
- [주문 생성 흐름](docs/order-creation-flow.md)
- [주문-결제-재고예약 흐름](docs/order-payment-reservation-flow.md)
- [오류 코드](docs/error-codes.md)
- [결제 Webhook과 대사 설계](docs/payment-webhook-reconciliation.md)
- [보안 범위와 결제 신뢰 경계](docs/security-scope.md)
- [ADR 0001: 조건부 재고 업데이트](docs/adr/0001-conditional-inventory-update.md)
- [ADR 0002: Outbox 폴링 릴레이](docs/adr/0002-outbox-polling-relay.md)
- [ADR 0003: Mock 결제 포트](docs/adr/0003-mock-payment-port.md)
- [ADR 0004: 멱등성 레코드](docs/adr/0004-idempotency-record.md)
