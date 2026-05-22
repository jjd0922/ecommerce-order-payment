package com.orderpayment.api.order;

import com.orderpayment.application.order.dto.CreateOrderCommand;
import com.orderpayment.application.order.dto.CreateOrderResult;
import com.orderpayment.application.order.port.in.CreateOrderUseCase;
import com.orderpayment.domain.product.ProductId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;

    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        CreateOrderResult result = createOrderUseCase.create(request.toCommand());
        return ResponseEntity.created(URI.create("/v1/orders/" + result.orderId().value()))
                .body(CreateOrderResponse.from(result));
    }

    public record CreateOrderRequest(@NotEmpty List<@Valid OrderLineRequest> orderLines) {

        private CreateOrderCommand toCommand() {
            return new CreateOrderCommand(orderLines.stream()
                    .map(OrderLineRequest::toCommand)
                    .toList());
        }
    }

    public record OrderLineRequest(
            @NotNull UUID productId,
            @Positive int quantity
    ) {

        private CreateOrderCommand.OrderLine toCommand() {
            return new CreateOrderCommand.OrderLine(new ProductId(productId), quantity);
        }
    }

    public record CreateOrderResponse(
            UUID orderId,
            BigDecimal totalAmount,
            String status
    ) {

        private static CreateOrderResponse from(CreateOrderResult result) {
            return new CreateOrderResponse(
                    result.orderId().value(),
                    result.totalAmount().amount(),
                    result.status().name()
            );
        }
    }
}
