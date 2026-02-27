#!/bin/bash
# concurrent-test.sh

ENDPOINT=$1
PRODUCT_ID=1
QUANTITY=10

if [ -z "$ENDPOINT" ]; then
  echo "Usage: $0 <pessimistic|optimistic>"
  exit 1
fi

URL="http://localhost:8080/api/orders/$ENDPOINT"

echo "Resetting product stock..."
curl -s -X POST http://localhost:8080/api/products/reset | jq . || curl -s -X POST http://localhost:8080/api/products/reset
echo ""

echo "Starting concurrent test on $URL..."

for i in {1..20}
do
  # Each request tries to buy 10 units. Initial stock is 100.
  curl -s -X POST -H "Content-Type: application/json" -d "{\"productId\": $PRODUCT_ID, \"quantity\": $QUANTITY, \"userId\": \"user$i\"}" $URL &
  sleep 0.05 # Stagger requests slightly to mimic real traffic
done

wait
echo -e "\n\nTest finished. Check stats endpoint for results:"
curl -s http://localhost:8080/api/orders/stats | jq . || curl -s http://localhost:8080/api/orders/stats
echo ""
