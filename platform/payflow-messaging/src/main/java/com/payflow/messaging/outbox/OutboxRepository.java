package com.payflow.messaging.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    // FOR UPDATE SKIP LOCKED: several app instances can run the relay at once; each one locks a different
    // batch of rows and skips rows another instance is already sending, so no message is sent twice by two relays.
    @Query(nativeQuery = true, value = """
            SELECT * FROM outbox_messages
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """)
    List<OutboxMessage> lockNextBatch(@Param("limit") int limit);

    long countByPublishedAtIsNull();
}
