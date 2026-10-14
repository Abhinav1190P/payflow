package com.payflow.payment;

import com.payflow.common.security.AuthenticatedUser;
import com.payflow.common.api.ApiResponse;
import com.payflow.idempotency.Replayable;
import com.payflow.payment.dto.PaymentRequest;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.wallet.WalletController;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(@AuthenticationPrincipal AuthenticatedUser user,
                                                            @RequestHeader(WalletController.IDEMPOTENCY_HEADER) String idempotencyKey,
                                                            @Valid @RequestBody PaymentRequest request) {
        Replayable<PaymentResponse> result = paymentService.pay(user.id(), idempotencyKey, request);
        return ResponseEntity.status(result.status())
                .header(WalletController.REPLAYED_HEADER, Boolean.toString(result.replayed()))
                .body(ApiResponse.of(result.body()));
    }
}
