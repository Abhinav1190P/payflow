package com.payflow.wallet;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    // Concept: text blocks. Multi-line queries stay readable without string concatenation.
    @Query("""
            select new com.payflow.wallet.EntryView(t.id, t.type, t.reference, e.direction,
                                                    e.amountMinor, e.balanceAfterMinor, e.createdAt)
            from LedgerEntry e join e.transaction t
            where e.wallet.id = :walletId and e.createdAt >= :from and e.createdAt < :to
            order by e.seq
            """)
    List<EntryView> findViewsForWallet(@Param("walletId") UUID walletId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select new com.payflow.wallet.BalancePoint(e.createdAt, e.balanceAfterMinor)
            from LedgerEntry e
            where e.wallet.id = :walletId
            order by e.createdAt, e.seq
            """)
    List<BalancePoint> findBalanceHistory(@Param("walletId") UUID walletId);

    @Query("""
            select coalesce(sum(case when e.direction = com.payflow.wallet.Direction.CREDIT then e.amountMinor
                                     else -e.amountMinor end), 0)
            from LedgerEntry e where e.wallet.id = :walletId
            """)
    long netForWallet(@Param("walletId") UUID walletId);
}
