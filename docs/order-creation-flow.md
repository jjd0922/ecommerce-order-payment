# Order Creation Flow

## Scope

이 문서는 주문 생성 유스케이스의 현재 처리 범위를 설명한다.

현재 PR에서는 DB 영속화 구현체를 추가하지 않는다. 애플리케이션 레이어에서 필요한 포트와 유스케이스 흐름을 정의하고, 테스트용 fake adapter로 도메인 규칙이 올바르게 조합되는지 검증한다.

## Flow

```text
CreateOrderCommand
-> 상품 조회
-> 상품 판매 상태 검증
-> 재고 조회
-> 재고 차감
-> 주문 항목 생성
-> 주문 생성
-> 주문 저장
-> 차감된 재고 저장
-> CreateOrderResult 반환
```

## Application Ports

- `CreateOrderUseCase`: 주문 생성 유스케이스의 인바운드 인터페이스이다.
- `ProductQueryPort`: 상품 조회를 담당한다.
- `InventoryQueryPort`: 재고 조회를 담당한다.
- `InventoryCommandPort`: 재고 저장을 담당한다.
- `OrderCommandPort`: 주문 저장을 담당한다.
- `OrderIdGeneratorPort`: 주문 식별자 생성을 담당한다.

## Service and Facade Policy

애플리케이션 레이어의 외부 호출 진입점은 UseCase 인터페이스로 정의한다.

단일 유스케이스를 직접 수행하는 경우 `Service`가 UseCase를 구현한다. 여러 서비스 조합, 캐시 우선 조회, 외부 시스템 조합처럼 흐름을 묶는 책임이 커지는 경우 `Facade`가 UseCase를 구현한다.

## Consistency Rules

- 주문 생성 요청에는 최소 하나 이상의 주문 라인이 필요하다.
- 주문 라인의 상품 식별자는 필수이다.
- 주문 수량은 1 이상이어야 한다.
- 판매 중단 상품은 주문할 수 없다.
- 재고보다 큰 수량은 차감할 수 없다.
- 주문 총 금액은 주문 항목의 단가와 수량으로 계산한다.

## Transaction Boundary

`CreateOrderService#create` 메서드를 주문 생성 트랜잭션 경계로 정의한다.

인프라 구현체가 추가되면 같은 트랜잭션 안에서 주문 저장과 재고 차감 저장이 함께 처리되어야 한다.
