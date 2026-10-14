package com.payflow.wallet;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// Users are created in auth-service, so payment-service opens a wallet lazily the first time it sees a user id.
// The insert runs in its own REQUIRES_NEW transaction so a race between two first requests (unique constraint)
// only fails that tiny transaction; the loser simply re-reads the wallet the winner created.
// TransactionTemplate is used instead of @Transactional because @Transactional only works through the Spring
// proxy: a call from another method of this same class (self-invocation) would silently skip it.
@Component
public class WalletOpener {

    private final WalletRepository wallets;
    private final WalletProperties properties;
    private final TransactionTemplate requiresNew;

    public WalletOpener(WalletRepository wallets, WalletProperties properties, PlatformTransactionManager transactionManager) {
        this.wallets = wallets;
        this.properties = properties;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public UUID existingOrOpen(UUID userId) {
        return wallets.findIdByUserIdAndCurrency(userId, properties.currency()).orElseGet(() -> ensureOrReread(userId));
    }

    public UUID ensureOrReread(UUID userId) {
        try {
            return requiresNew.execute(status -> wallets.findIdByUserIdAndCurrency(userId, properties.currency())
                    .orElseGet(() -> wallets.saveAndFlush(Wallet.openFor(userId, properties.currency())).getId()));
        } catch (DataIntegrityViolationException raceLost) {
            return wallets.findIdByUserIdAndCurrency(userId, properties.currency()).orElseThrow(() -> raceLost);
        }
    }
}
