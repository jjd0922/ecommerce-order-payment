# Order Payment Reservation Flow

## Purpose

이 프로젝트는 Spring Boot, JPA, MySQL 기반 주문-결제(Mock)-재고예약 프로세스를 Port 기반 설계로 구성한다.

실제 PG 연동은 사용하지 않는다. 대신 운영에서 중요한 정합성, 동시성, 멱등성, 장애 복구, 추적성을 중심으로 구조를 설명한다.

## Flow

```text
1. 주문 생성
   -> 상품 조회
   -> 상품 판매 상태 검증
   -> 주문 생성

2. 결제 준비
   -> 주문 조회
   -> 멱등성 키로 기존 결제 조회
   -> 주문 항목별 재고 선예약
   -> 주문 상태 PAYMENT_PENDING 전이
   -> Mock 결제 READY 생성
   -> 도메인 이벤트 발행

3. 결제 완료 또는 실패
   -> 다음 PR 범위

4. 결제 이탈 또는 만료
   -> 만료된 재고 예약 자동 해제
   -> 다음 PR에서 scheduler 또는 worker로 연결
```

## Consistency

- 주문 생성은 재고를 변경하지 않는다.
- 결제 준비 단계에서만 재고를 선예약한다.
- 재고 예약 성공 후 주문 상태를 `PAYMENT_PENDING`으로 전이한다.
- 결제 준비 결과는 Mock 결제 `READY` 상태로 생성한다.
- 같은 멱등성 키의 같은 결제 준비 요청은 기존 결과를 반환한다.
- 같은 멱등성 키의 다른 요청은 충돌로 거부한다.

## Concurrency

재고 선예약은 `InventoryReservationCommandPort#hold`가 담당한다.

MySQL 구현에서는 다음 조건부 update를 사용한다.

```sql
UPDATE inventory
SET available_quantity = available_quantity - :quantity,
    held_quantity = held_quantity + :quantity
WHERE product_id = :productId
  AND available_quantity >= :quantity
```

영향받은 row 수가 1이면 예약 성공이다. 영향받은 row 수가 0이면 재고 부족으로 처리한다.

## Idempotency

결제 준비 요청은 `IdempotencyKey`를 필수로 받는다.

- 기존 결제가 없으면 재고 예약과 Mock 결제를 생성한다.
- 기존 결제가 있고 주문 ID와 금액이 같으면 기존 결과를 반환한다.
- 기존 결제가 있고 주문 ID 또는 금액이 다르면 충돌로 거부한다.

인프라 구현 단계에서는 결제 테이블의 `idempotency_key`에 unique constraint가 필요하다.

## Recoverability

재고 예약은 만료 시간을 가진다.

현재 인프라 어댑터는 `InventoryReservationRecoveryPort#releaseExpiredReservations`를 제공한다. 이 포트는 만료된 `HELD` 예약을 조회하고, 예약 수량을 다시 가용 재고로 되돌린 뒤 예약 상태를 `EXPIRED`로 변경한다.

자동 실행은 다음 PR에서 scheduler 또는 worker로 연결한다.

## Observability

결제 준비 과정에서는 다음 도메인 이벤트를 발행한다.

- `InventoryReserved`
- `PaymentPrepared`

현재 인프라 구현은 이벤트를 로그로 남긴다. 이후 Outbox 어댑터로 교체하면 같은 Port를 유지하면서 DB outbox 테이블에 이벤트를 저장할 수 있다.

## Outbox Extension

`DomainEventPublisherPort`는 현재 로그 기반 어댑터를 사용한다.

Outbox 적용 시에는 다음 방식으로 확장한다.

```text
DomainEventPublisherPort
-> OutboxDomainEventPublisherAdapter
-> outbox_event table insert
-> relay worker publish
```

비즈니스 상태 변경과 outbox 저장은 같은 트랜잭션 안에서 처리되어야 한다.
