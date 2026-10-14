package com.payflow.messaging.inbox;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface InboxRepository extends Repository<ProcessedMessage, ProcessedMessage.Key> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO processed_messages (consumer, message_id, processed_at)
            VALUES (:consumer, :messageId, now())
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(@Param("consumer") String consumer, @Param("messageId") String messageId);
}
