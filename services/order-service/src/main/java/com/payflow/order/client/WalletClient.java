package com.payflow.order.client;

import com.payflow.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

// The user's own JWT is forwarded, so payment-service authorises the call exactly as if the user made it.
@FeignClient(name = "payment-service", path = "/api/v1/wallets")
public interface WalletClient {

    @GetMapping("/me")
    ApiResponse<WalletView> myWallet(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    record WalletView(UUID id, String currency, long balanceMinor, String status) {
    }
}
