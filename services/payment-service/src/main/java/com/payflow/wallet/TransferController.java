package com.payflow.wallet;

import com.payflow.common.api.ApiResponse;
import com.payflow.common.security.AuthenticatedUser;
import com.payflow.directory.RecipientResolver;
import com.payflow.wallet.dto.TransferRequest;
import com.payflow.wallet.dto.TransferResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final WalletService walletService;
    private final RecipientResolver recipients;

    public TransferController(WalletService walletService, RecipientResolver recipients) {
        this.walletService = walletService;
        this.recipients = recipients;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(@AuthenticationPrincipal AuthenticatedUser user,
                                                                  @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                                  @RequestHeader(WalletController.IDEMPOTENCY_HEADER) String idempotencyKey,
                                                                  @Valid @RequestBody TransferRequest request) {
        UUID recipientId = recipients.resolve(request.toEmail(), authorization);
        return WalletController.respond(walletService.transfer(user.id(), idempotencyKey, recipientId, request));
    }
}
