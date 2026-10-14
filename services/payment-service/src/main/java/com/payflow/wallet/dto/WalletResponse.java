package com.payflow.wallet.dto;

import com.payflow.wallet.Wallet;
import com.payflow.wallet.WalletStatus;
import java.util.UUID;

public record WalletResponse(UUID id, String currency, long balanceMinor, String balance, WalletStatus status, long version) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getCurrency(), wallet.getBalanceMinor(),
                wallet.balance().toString(), wallet.getStatus(), wallet.getVersion());
    }
}
