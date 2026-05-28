# ADR 0003: Mock 결제 포트

## 배경

이 프로젝트는 실제 PG 계정에 의존하지 않고 주문, 결제, 재고 예약, 복구 흐름을 보여주는 것이 목적이다.

## 결정

결제 게이트웨이는 application port로 모델링하고 infrastructure 모듈에서 Mock adapter를 제공한다. 결제 승인 UseCase는 게이트웨이 호출을 데이터베이스 트랜잭션 밖에서 수행하고, 결과만 짧은 트랜잭션 안에서 반영한다.

## 결과

- domain/application 계층이 특정 PG SDK에 결합되지 않는다.
- 실제 PG adapter로 교체하기 전에 contract test로 port 동작을 고정할 수 있다.
- Mock adapter만으로는 실제 webhook, redirect, 서명 검증, PG 대사 흐름을 검증할 수 없다.
- 운영 연동 시 PG별 인증, 재시도, timeout, 대사 정책을 추가해야 한다.
