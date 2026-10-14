# Git workflow

All stages are in the working tree now. To keep a readable history, commit them as small logical steps. Run these from `~/Documents/GitHub/payflow`.

```bash
git init -b main
git add .gitignore .dockerignore pom.xml
git commit -m "chore: maven multi-module parent with spring cloud bom"

git switch -c feature/platform
git add platform/payflow-common
git commit -m "feat(common): shared api envelope, errors, jwt security, data structures, event contracts"
git add platform/payflow-messaging
git commit -m "feat(messaging): transactional outbox, inbox, kafka dead-letter handling"

git switch -c feature/infra
git add infra
git commit -m "feat(infra): eureka, config server, gateway with redis rate limiting"

git switch -c feature/services
git add services/auth-service
git commit -m "feat(auth): registration, login, jwt issuing, user lookup"
git add services/payment-service
git commit -m "feat(payment): wallet ledger, payments, fraud graph, saga participant, retry scheduler"
git add services/inventory-service
git commit -m "feat(inventory): catalog, trie autocomplete, redis cache, stock reservations"
git add services/order-service
git commit -m "feat(order): checkout, order state machine, saga orchestrator, admin ui"
git add services/notification-service
git commit -m "feat(notification): order event consumer"

git switch -c feature/ops
git add Dockerfile docker-compose.yml deploy load-tests k8s
git commit -m "ops: docker compose stack, prometheus, grafana, zipkin, k6, kubernetes"
git add .github .gitlab-ci.yml
git commit -m "ci: github actions and gitlab ci"
git add ui
git commit -m "feat(ui): minimal react client"
git add README.md docs
git commit -m "docs: architecture, concept table, interview notes"
```

Each `git switch -c` above branches from the previous feature branch, so the history stays a single line. Push everything and open a pull request from the last branch into `main`:

```bash
git remote add origin https://github.com/<you>/payflow.git
git push -u origin main feature/platform feature/infra feature/services feature/ops
```

**GitHub Desktop:** add the folder as a local repository, then commit the paths in the groups above one at a time and publish.
