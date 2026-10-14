# Kubernetes (optional stage)

The manifests cover the PayFlow services: Deployments with readiness and liveness probes on the Actuator health groups, Services, HPAs for the gateway, payment-service and order-service, and an Ingress.

Postgres, Kafka, Redis and Zipkin are expected to exist in the `payflow` namespace under the names `postgres`, `kafka`, `redis` and `zipkin`. For a local cluster, install them with their Helm charts, or point the ConfigMap at managed services.

```bash
# build images into your cluster's registry (kind example)
for m in infra/discovery-server infra/config-server infra/api-gateway services/auth-service services/payment-service \
         services/inventory-service services/order-service services/notification-service; do
  docker build --build-arg MODULE=$m -t payflow/$(basename $m):latest .
  kind load docker-image payflow/$(basename $m):latest
done
kubectl apply -f k8s/
kubectl -n payflow get pods
```

With several replicas, the design choices from earlier stages matter:

- `FOR UPDATE SKIP LOCKED` in the outbox relay stops two pods from sending the same message.
- The idempotency primary key and the inbox table protect against duplicates across pods.
- The gateway rate limit lives in Redis, so it is shared by every gateway pod.
