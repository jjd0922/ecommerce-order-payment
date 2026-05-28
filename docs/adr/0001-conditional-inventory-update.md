# ADR 0001: 조건부 재고 업데이트

## 배경

여러 주문이 같은 상품 재고를 동시에 예약해도 초과 판매가 발생하면 안 된다. 또한 한 주문이 여러 상품을 포함하면 여러 재고 행을 함께 갱신하므로 락 획득 순서도 중요하다.

## 결정

상품별 재고 예약은 다음 조건부 업데이트로 처리한다.

```sql
UPDATE inventory
SET available_quantity = available_quantity - :quantity,
    held_quantity = held_quantity + :quantity
WHERE product_id = :productId
  AND available_quantity >= :quantity
```

예약 명령은 재고 행을 갱신하기 전에 `productId` 오름차순으로 정렬한다. 이렇게 하면 여러 상품을 포함한 주문에서도 락 획득 순서가 일정해져 데드락 가능성을 낮출 수 있다.

## 결과

- 재고 불변식은 별도 분산락 없이 데이터베이스가 보장한다.
- 영향받은 행 수가 `0`이면 재고 부족 또는 재고 행 없음으로 해석한다.
- 드문 데이터베이스 데드락은 애플리케이션에서 재시도 또는 오류로 처리해야 한다.
- Redis 락은 사용하지 않는다. 불변식은 MySQL에 있고, 별도 락 저장소는 TTL과 네트워크 분리 상황에서 새로운 실패 지점을 만든다.
