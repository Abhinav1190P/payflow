# 20 interview questions about PayFlow

**1. Why store money as `long` minor units?**
Binary floating point can't represent 0.1 exactly, so sums drift. Integer paise are exact. `Math.addExact` turns overflow into an exception instead of a silent wrap.

**2. What is a double-entry ledger and why use it?**
Every movement is one transaction with a debit and a credit of the same amount (`LedgerTransaction.assertBalanced`). Money is only moved, never created. Top-ups come from a `CASH_IN` system wallet, so the sum of all balances is always 0, and that is a check you can run in production.

**3. How do you stop 100 concurrent transfers from overdrawing a wallet?**
`SELECT ... FOR UPDATE` on both wallets (`WalletRepository.findByIdForUpdate`), so transfers on the same wallet queue up. The domain check in `Wallet.debit` and a database CHECK constraint back it up. `WalletConcurrencyTest` fires 100 transfers at a wallet that can afford 50 and asserts exactly 50 succeed, the balance is 0, and the ledger matches.

**4. How do you avoid deadlocks?**
Always lock rows in a fixed order (sorted ids), in both `WalletService.lockInOrder` and `ProductRepository.findAllForUpdate`. Two transactions can then never each hold a lock the other is waiting for.

**5. Optimistic vs pessimistic locking: when do you use which?**
- **Pessimistic** for hot, contended money rows: blocking is cheaper than failing and retrying.
- **Optimistic** (`@Version` + ETag/If-Match) for rare admin edits: no lock is held, and a conflict gives 412/409.

**6. How does the idempotent POST work?**
The client sends an `Idempotency-Key` header. The response is stored in the same transaction as the money movement, keyed by (user, key). A retry gets the stored response back, and a different body with the same key gets 422. A `ConcurrentHashMap` rejects simultaneous duplicates quickly on one instance; the primary key protects across instances.

**7. Why does `tryPayMerchant` return a sealed result instead of throwing?**
An exception that passes through a `@Transactional` proxy marks the transaction rollback-only even if the caller catches it, and the commit then fails with `UnexpectedRollbackException`. A declined payment must still commit (idempotency record, saga reply), so it is a value, not an exception.

**8. What is the N+1 problem and how did you fix it?**
Loading N transactions and then touching their lazy entries runs 1 + N queries. The fix pages over ids first, then loads them with `@EntityGraph` (one join query). A join fetch with paging would paginate in memory. `LedgerRepositoryTest` asserts 11 vs 1 statements.

**9. Why a saga and not a distributed transaction?**
Each service owns its database, and 2PC across services and Kafka is fragile and blocks. A saga is a series of local transactions, each with a compensating action: if stock fails after payment, `RefundPayment` runs.

**10. Orchestration or choreography: what did you choose?**
Orchestration. `OrderSagaOrchestrator` holds the whole flow and the `OrderStatus` state machine in one place, which is easier to reason about and debug than events bouncing between services.

**11. What is the transactional outbox?**
The next message is inserted into `outbox_messages` in the same database transaction as the business change. `OutboxRelay` publishes it afterwards. This gives at-least-once delivery with no dual-write inconsistency.

**12. If delivery is at-least-once, how do you avoid double processing?**
Idempotent consumers. `Inbox.firstTime` inserts the message id with `ON CONFLICT DO NOTHING` in the same transaction, so a duplicate does nothing. Business keys add a second guard: `order_payments.order_id` and stock reservations per order.

**13. What happens to a poison message?**
`DefaultErrorHandler` retries twice, one second apart, then `DeadLetterPublishingRecoverer` moves it to `<topic>.DLT`. The partition keeps flowing, and the message can be inspected and replayed.

**14. How do several relay instances avoid sending the same outbox row?**
`SELECT ... FOR UPDATE SKIP LOCKED`: each instance locks a different batch and skips rows another instance holds.

**15. What does the PriorityQueue retry scheduler do?**
Technical failures (`TransientDataAccessException`) during a charge are parked in a min-heap ordered by next-attempt time, with exponential backoff. The scheduler only peeks at the head (O(1)); offer and poll are O(log n). After 5 attempts the saga gets `PaymentFailed`. It lives in memory, so a restart loses the queue, and the saga timeout covers that.

**16. What does Resilience4j add to the Feign calls?**
- **Retry** with backoff for blips.
- **Circuit breaker**, which fails fast when a dependency is mostly failing.
- **Rate limiter** to protect the callee.
- **Fallbacks.** Missing prices fail checkout with 503. A missing balance just skips the early check, because payment re-checks during the saga.

**17. Why are there two caches in inventory-service?**
- **Redis `@Cacheable`** for products: shared by all instances, with a TTL, and evicted on stock changes.
- **A hand-written LRU** for autocomplete results: per instance and cheap to rebuild.

**18. How do you trace one request across services?**
- The gateway adds `X-Correlation-Id`.
- Micrometer Tracing propagates `traceId` over HTTP (Feign) and Kafka (observation enabled) to Zipkin.
- JSON logs include `traceId`, `spanId` and `correlationId`.

**19. How is security handled across services?**
auth-service issues an HS256 JWT. Every service verifies it locally with the shared secret, so there is no call to auth per request. Feign calls forward the user's own token. The admin UI uses a separate filter chain with HTTP Basic because a browser page can't attach the JWT header easily.

**20. What would you change for production?**
- RS256 with a JWKS endpoint instead of a shared HMAC secret.
- A Git backend for the config server.
- Kafka with replication factor 3.
- One Postgres per service, or managed databases.
- A persistent retry queue.
- Sharded system wallets so top-ups don't serialize.
- Alerting on DLT size and saga timeouts.
