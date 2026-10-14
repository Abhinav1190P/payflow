package com.payflow.wallet;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    @Query("select w.id from Wallet w where w.userId = :userId and w.currency = :currency")
    Optional<UUID> findIdByUserIdAndCurrency(@Param("userId") UUID userId, @Param("currency") String currency);

    @Query("select w from Wallet w where w.userId = :userId and w.currency = :currency")
    Optional<Wallet> findByUserIdAndCurrency(@Param("userId") UUID userId, @Param("currency") String currency);

    @Query("select w.id from Wallet w where w.code = :code")
    Optional<UUID> findIdByCode(@Param("code") String code);

    // Concept: pessimistic locking. Generates SELECT ... FOR UPDATE: the row stays locked until this
    // transaction commits, so concurrent transfers on the same wallet queue up instead of racing.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);

    @Query("select coalesce(sum(w.balanceMinor), 0) from Wallet w where w.currency = :currency")
    long sumBalances(@Param("currency") String currency);
}
