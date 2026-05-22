# Order Payment Reservation Flow

## Purpose

이 문서는 현재 구현 기준의 주문-결제(Mock)-재고예약 흐름을 설명한다.

실제 PG 연동은 사용하지 않는다. 대신 운영에서 중요한 정합성, 동시성, 멱등성, 장애 복구, 추적성을 Port 기반 설계로 분리한다.

## Flow

```text
1. 주문 생성
   -> 상품 조회
   -> 상품 판매 상태 검증
   -> 주문 CREATED 생성

2. 결제 준비
   -> Idempotency-Key로 멱등성 레코드 생성 또는 기존 결과 재현
   -> 주문 조회
   -> 주문 항목별 재고 선예약
   -> 주문 상태 PAYMENT_PENDING 전이
   -> Mock 결제 READY 생성
   -> InventoryReserved, PaymentPrepared 이벤트를 Outbox에 저장

3. 결제 승인 성공
   -> Idempotency-Key로 멱등성 레코드 생성 또는 기존 결과 재현
   -> payment row를 비관적 락으로 조회
   -> 결제 PROCESSING 전이
   -> DB 트랜잭션 밖에서 Mock 결제 승인 호출
   -> payment row를 다시 비관적 락으로 조회
   -> 결제 APPROVED 전이
   -> 주문 PAID 전이
   -> 재고 예약 CONFIRMED 전이
   -> PaymentApproved, InventoryReservationConfirmed 이벤트를 Outbox에 저장

4. 결제 승인 실패
   -> 결제 FAILED 전이
   -> 주문 FAILED 전이
   -> 재고 예약 RELEASED 전이
   -> held 재고를 available 재고로 복구
   -> PaymentFailed, InventoryReservationReleased 이벤트를 Outbox에 저장

5. 결제 이탈 또는 예약 만료
   -> 스케줄러 또는 관리자 API가 만료 처리 UseCase 호출
   -> FOR UPDATE SKIP LOCKED로 만료된 HELD 예약 선점
   -> held 재고를 available 재고로 복구
   -> 예약 EXPIRED 전이
   -> InventoryReservationExpired 이벤트를 Outbox에 저장
```

## Consistency

- 주문 생성은 재고를 변경하지 않는다.
- 결제 준비 단계에서만 재고를 선예약한다.
- 재고 예약 성공 후 주문 상태를 `PAYMENT_PENDING`으로 전이한다.
- 결제 준비 결과는 Mock 결제 `READY` 상태로 생성한다.
- 결제 승인 요청은 `PROCESSING`으로 먼저 전이한 뒤 DB 트랜잭션 밖에서 Mock PG를 호출한다.
- PG 결과 반영은 payment row를 다시 잠근 뒤 짧은 트랜잭션 안에서 처리한다.
- 같은 멱등성 키의 같은 요청은 저장된 완료 응답을 반환한다.
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

여러 상품을 포함한 주문은 `productId` 오름차순으로 재고를 예약한다. 이렇게 하면 여러 요청이 같은 상품 집합을 다른 순서로 잠그면서 발생하는 데드락 가능성을 낮출 수 있다.

결제 승인은 `PaymentQueryPort#getPaymentForUpdate`를 통해 payment row에 비관적 락을 건다. 같은 결제에 대한 중복 승인 요청은 하나만 `READY -> PROCESSING` 전이를 수행하고, 이후 요청은 `PROCESSING` 또는 완료 상태를 기준으로 처리된다.

## Idempotency

결제 준비와 결제 승인은 `Idempotency-Key`를 필수로 받는다.

공용 `idempotency_record` 테이블은 key, request hash, response body, status, createdAt, expiresAt을 저장한다.

- 기존 레코드가 없으면 `IN_FLIGHT` 레코드를 생성하고 비즈니스 로직을 실행한다.
- 같은 key와 같은 request hash의 완료 레코드가 있으면 저장된 응답을 재현한다.
- 같은 key와 다른 request hash가 있으면 충돌로 거부한다.
- 같은 key가 `IN_FLIGHT`이면 중복 처리 중으로 보고 `IDEMPOTENCY_IN_FLIGHT` 오류를 반환한다.

결제 준비와 결제 승인은 요청 hash가 다르므로 같은 `Idempotency-Key`를 재사용하지 않는다.

## Recoverability

재고 예약은 만료 시간을 가진다.

`InventoryReservationExpirationScheduler`는 주기적으로 `ExpireInventoryReservationsUseCase`를 호출한다. 관리자 API `POST /v1/admin/inventory-reservations/expire`로도 같은 UseCase를 수동 실행할 수 있다.

승인 요청 후 `PROCESSING`에 머문 결제는 `RecoverProcessingPaymentsUseCase`에서 복구할 수 있다. 현재 Mock PG 어댑터는 `PaymentApprovalQueryPort`를 제공하므로, 운영 PG 연동 시에는 PG 거래 조회 결과를 같은 결제 결과 반영 서비스에 전달하면 된다.

## Observability

비즈니스 상태 변경 이벤트는 `DomainEventPublisherPort`를 통해 Outbox에 저장된다.

현재 구현 이벤트:

- `InventoryReserved`
- `PaymentPrepared`
- `PaymentApproved`
- `PaymentFailed`
- `InventoryReservationConfirmed`
- `InventoryReservationReleased`
- `InventoryReservationExpired`

Outbox 릴레이는 `FOR UPDATE SKIP LOCKED`로 발행 대상 이벤트를 선점하고, 현재 프로젝트에서는 로그 발행 어댑터로 이벤트 발행을 대체한다.
