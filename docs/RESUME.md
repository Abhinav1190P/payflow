# Resume bullets for PayFlow

These are written only from what was built and measured (numbers from the k6 run in README → "Measured results").

- Built **PayFlow**, an event-driven order and payments platform in **Java 21 and Spring Boot 3**. It has 5 **microservices** behind Spring Cloud Gateway, with Eureka discovery and a Config Server. Services call each other through **RESTful APIs** using OpenFeign, with Resilience4j circuit breakers, retries and rate limiting.
- Built order checkout as a **Kafka saga** with compensating refunds, using a transactional **outbox**, idempotent consumers and dead-letter topics, so no order is half-processed when payment or stock fails.
- Built a double-entry wallet ledger with **Spring Data JPA** and PostgreSQL. A 100-thread concurrency test proves there are no overdrafts and no lost updates, using pessimistic locking with deadlock-free lock ordering plus idempotent POSTs via an `Idempotency-Key` header.
- Applied **OOP** and design patterns (Strategy, Factory, Template Method, Builder, Observer) and modern Java (records, sealed interfaces, pattern matching, virtual threads, CompletableFuture). Implemented **data structures** used by real features: LRU cache, PriorityQueue retry scheduler, Trie autocomplete, a graph with BFS/DFS for fraud-cycle detection, and binary search over balance history.
- Set up **Git/GitHub** with feature branches, conventional commits and PR templates, plus CI in GitHub Actions and GitLab CI (tests with Testcontainers, JaCoCo coverage, Docker images). Added observability with Prometheus, Grafana, Zipkin tracing through Kafka, and structured JSON logs. A k6 load test of 50 concurrent users through the gateway completed 6,400+ requests with 0% errors, at p95 19.5 ms for order placement and 10.9 ms for product listing.

Keyword check: Java, Core Java, Spring Boot, Spring MVC (REST and Thymeleaf), REST API, RESTful, Microservices, OOP, Object-Oriented Design, Data Structures, Git, GitHub, GitLab CI.
