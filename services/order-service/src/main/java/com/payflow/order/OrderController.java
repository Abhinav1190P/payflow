package com.payflow.order;

import com.payflow.common.api.ApiResponse;
import com.payflow.common.api.PageResponse;
import com.payflow.common.error.NotFoundException;
import com.payflow.common.security.AuthenticatedUser;
import com.payflow.order.dto.OrderView;
import com.payflow.order.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CheckoutService checkout;
    private final OrderRepository orders;

    public OrderController(CheckoutService checkout, OrderRepository orders) {
        this.checkout = checkout;
        this.orders = orders;
    }

    // 202 Accepted: the order exists, but the payment and stock steps finish asynchronously (poll GET /{id}).
    @PostMapping
    public ResponseEntity<ApiResponse<OrderView>> place(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                        @Valid @RequestBody PlaceOrderRequest request) {
        var result = checkout.placeOrder(user, authorization, idempotencyKey, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .header("Idempotent-Replayed", Boolean.toString(result.replayed()))
                .body(ApiResponse.of(result.order()));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<OrderView>> mine(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.of(PageResponse.from(orders.findByUserId(user.id(), pageable), OrderView::summary));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<OrderView> one(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return ApiResponse.of(orders.findByIdAndUserId(id, user.id()).map(OrderView::from)
                .orElseThrow(() -> new NotFoundException("Order")));
    }
}
