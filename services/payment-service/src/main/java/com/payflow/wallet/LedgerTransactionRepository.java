package com.payflow.wallet;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    // Concept: pagination + sorting. Step 1 of the N+1 fix: page over ids only. Paging a query that
    // fetch-joins a collection would make Hibernate paginate in memory, so ids are paged first.
    @Query(value = """
            select t.id from LedgerTransaction t
            where exists (select 1 from LedgerEntry e where e.transaction = t and e.wallet.id = :walletId)
            """,
            countQuery = """
            select count(t) from LedgerTransaction t
            where exists (select 1 from LedgerEntry e where e.transaction = t and e.wallet.id = :walletId)
            """)
    Page<UUID> findIdsForWallet(@Param("walletId") UUID walletId, Pageable pageable);

    // Concept: N+1 fix with @EntityGraph. Step 2: load those transactions and their entries in ONE query
    // (a LEFT JOIN FETCH) instead of 1 query for transactions + 1 query per transaction for entries.
    @EntityGraph(attributePaths = "entries")
    @Query("select distinct t from LedgerTransaction t where t.id in :ids")
    List<LedgerTransaction> findWithEntriesByIdIn(@Param("ids") Collection<UUID> ids);

    @Query("""
            select distinct t from LedgerTransaction t join fetch t.entries e
            where t.type = com.payflow.wallet.TransactionType.TRANSFER and t.createdAt >= :since
            """)
    List<LedgerTransaction> findTransfersSince(@Param("since") Instant since);
}
