# Payment Webhook and Reconciliation

현재 프로젝트는 클라이언트가 호출하는 동기 결제 승인 API를 중심으로 구현한다.

```http
POST /v1/payments/{paymentId}/confirm
```

실제 PG 연동에서는 클라이언트 redirect 흐름만으로 결제 결과를 확정하지 않는다. 네트워크 단절, 브라우저 종료, redirect 누락이 발생할 수 있기 때문이다. 운영 환경에서는 PG webhook과 정기 대사를 함께 둔다.

## Webhook API

후속 구현 대상 API는 다음과 같다.

```http
POST /v1/payments/webhooks/pg
```

처리 원칙:

| 항목 | 정책 |
| --- | --- |
| 인증 | PG 서명 헤더 검증 |
| 멱등성 | PG event id 또는 transaction id 기준 중복 처리 방지 |
| 상태 전이 | payment row를 `FOR UPDATE`로 잠근 뒤 결과 반영 |
| 응답 | 검증 성공 후 내부 처리 결과와 무관하게 PG 재시도 정책에 맞는 2xx/4xx 반환 |
| 이벤트 | 결제 상태 변경과 Outbox 저장을 같은 트랜잭션에서 처리 |

## Reconciliation Batch

정기 대사 배치는 “PG는 승인됐지만 우리 DB는 모르는 상태”를 복구한다.

대상:

| 대상 | 설명 |
| --- | --- |
| `PROCESSING` 결제 | 승인 요청 후 일정 시간 이상 결과가 없는 결제 |
| `READY` 결제 | 준비 후 만료 전까지 승인 여부가 불명확한 결제 |
| PG 거래 내역 | PG transaction id 또는 order/payment id로 조회 |

처리 순서:

1. 대사 대상 결제를 batch size만큼 조회한다.
2. payment row를 `FOR UPDATE SKIP LOCKED`로 선점한다.
3. PG 거래 상태를 조회한다.
4. 승인/실패 결과를 내부 payment/order/inventory reservation 상태에 반영한다.
5. 상태 변경 이벤트를 Outbox에 저장한다.

Webhook과 대사 배치는 같은 상태 전이 서비스를 사용해야 한다. 이렇게 해야 동기 confirm, webhook, reconciliation이 서로 다른 규칙으로 결제 상태를 변경하지 않는다.
