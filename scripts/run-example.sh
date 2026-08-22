#!/usr/bin/env sh
set -eu

curl --request POST "http://localhost:${PORT:-8080}/orders/checkout" \
  --header "Content-Type: application/json" \
  --data '{"tenantId":"academy-algebra","orderId":"order-1042","customerId":"learner-73","totalCents":4900}'
