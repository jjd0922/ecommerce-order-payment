# Error Codes

API 오류 응답은 RFC 7807 `application/problem+json` 형식을 사용한다.

공통 확장 필드는 다음과 같다.

| 필드 | 설명 |
| --- | --- |
| `code` | 애플리케이션 오류 코드 |
| `requestId` | 요청 추적 ID. `X-Request-Id` 응답 헤더와 동일 |

## 코드 목록

| Code | HTTP Status | 의미 | 대표 상황 |
| --- | --- | --- | --- |
| `INVALID_REQUEST` | 400 | 요청 형식 또는 필수 값 오류 | JSON 파싱 실패, 검증 실패, 필수 헤더 누락 |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | 같은 멱등키가 다른 요청에 사용됨 | 같은 `Idempotency-Key`로 다른 주문 결제 준비 요청 |
| `IDEMPOTENCY_IN_FLIGHT` | 409 | 같은 멱등키의 최초 요청 처리 중 | `IN_FLIGHT` 멱등성 레코드가 존재 |
| `DOMAIN_RULE_VIOLATION` | 409 | 도메인 상태 전이 또는 규칙 위반 | 이미 완료된 결제 상태 전이 요청 |
| `UNPROCESSABLE_ENTITY` | 422 | 문법은 맞지만 비즈니스 처리가 불가능 | 재고 부족 |
| `INFRASTRUCTURE_ERROR` | 500 | 인프라 처리 실패 | 직렬화, 외부 시스템, 저장소 처리 실패 |

## 응답 예시

```json
{
  "type": "about:blank",
  "title": "idempotency key conflict",
  "status": 409,
  "detail": "idempotency key was already used with different payment request",
  "code": "IDEMPOTENCY_KEY_CONFLICT",
  "requestId": "request-123"
}
```
