package com.payflow.wallet;

import com.payflow.common.api.ApiResponse;
import com.payflow.common.error.BusinessRuleException;
import com.payflow.wallet.dto.WalletResponse;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Concept: method security. @PreAuthorize on the class applies to every endpoint here: only ADMIN tokens pass.
@RestController
@RequestMapping("/api/v1/admin/wallets")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWalletController {

    private final WalletService walletService;
    private final WalletQueryService queries;

    public AdminWalletController(WalletService walletService, WalletQueryService queries) {
        this.walletService = walletService;
        this.queries = queries;
    }

    @GetMapping("/{walletId}")
    public ResponseEntity<ApiResponse<WalletResponse>> get(@PathVariable UUID walletId) {
        WalletResponse wallet = queries.wallet(walletId);
        return ResponseEntity.ok().eTag(Long.toString(wallet.version())).body(ApiResponse.of(wallet));
    }

    @PostMapping("/{walletId}/freeze")
    public ResponseEntity<ApiResponse<WalletResponse>> freeze(@PathVariable UUID walletId, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        return withEtag(walletService.changeStatus(walletId, WalletStatus.FROZEN, version(ifMatch)));
    }

    @PostMapping("/{walletId}/unfreeze")
    public ResponseEntity<ApiResponse<WalletResponse>> unfreeze(@PathVariable UUID walletId, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        return withEtag(walletService.changeStatus(walletId, WalletStatus.ACTIVE, version(ifMatch)));
    }

    private static ResponseEntity<ApiResponse<WalletResponse>> withEtag(WalletResponse wallet) {
        return ResponseEntity.ok().eTag(Long.toString(wallet.version())).body(ApiResponse.of(wallet));
    }

    private static long version(String ifMatch) {
        String raw = ifMatch.replace("W/", "").replace("\"", "").trim();
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("INVALID_IF_MATCH", "If-Match must be the ETag returned by GET");
        }
    }
}
