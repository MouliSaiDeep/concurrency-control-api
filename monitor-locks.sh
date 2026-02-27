#!/bin/bash
# monitor-locks.sh

source .env 2>/dev/null || true

PG_USER=${POSTGRES_USER:-user}
PG_DB=${POSTGRES_DB:-inventory_db}

echo "Monitoring active locks for database $PG_DB with user $PG_USER..."
echo "Press Ctrl+C to stop."

while true; do
  echo "--- Active Locks at $(date) ---"
  
  # Connect via docker exec to the database container
  docker exec -t concurrency-control-api-db-1 psql -U $PG_USER -d $PG_DB -c "
    SELECT relation::regclass, locktype, mode, granted 
    FROM pg_locks 
    WHERE pid IN (SELECT pid FROM pg_stat_activity WHERE datname = '$PG_DB');
  " || docker exec -t $(docker ps -qf "ancestor=postgres:15") psql -U $PG_USER -d $PG_DB -c "
    SELECT relation::regclass, locktype, mode, granted 
    FROM pg_locks 
    WHERE pid IN (SELECT pid FROM pg_stat_activity WHERE datname = '$PG_DB');
  "
  
  sleep 2
  clear
done
