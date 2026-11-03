#!/usr/bin/env bash
# Arranque de uma réplica PostgreSQL (streaming replication, mestre-escravo).
#
# Na primeira execução (volume vazio) copia o primário com pg_basebackup; o "-R" escreve o
# primary_conninfo e o standby.signal, por isso o servidor arranca já como réplica (hot standby).
# Corre como utilizador "postgres" (ver docker-compose).
set -euo pipefail

: "${PRIMARIO_HOST:?defina PRIMARIO_HOST}"
: "${PRIMARIO_PORTA:=5432}"
: "${SLOT_REPLICACAO:?defina SLOT_REPLICACAO}"
: "${REPLICACAO_PASSWORD:?defina REPLICACAO_PASSWORD}"
export PGPASSWORD="$REPLICACAO_PASSWORD"

if [ ! -s "$PGDATA/PG_VERSION" ]; then
    echo "[replica] À espera do primário ${PRIMARIO_HOST}:${PRIMARIO_PORTA}..."
    until pg_isready -q -h "$PRIMARIO_HOST" -p "$PRIMARIO_PORTA"; do sleep 2; done

    # O slot obriga o primário a guardar o WAL até esta réplica o receber (não se perde nada
    # se a réplica estiver desligada durante algum tempo).
    LIGACAO="host=$PRIMARIO_HOST port=$PRIMARIO_PORTA user=replicador dbname=postgres"
    if ! psql "$LIGACAO" -tAc "SELECT 1 FROM pg_replication_slots WHERE slot_name='$SLOT_REPLICACAO'" | grep -q 1; then
        psql "$LIGACAO" -c "SELECT pg_create_physical_replication_slot('$SLOT_REPLICACAO')"
    fi

    echo "[replica] A copiar o primário (pg_basebackup)..."
    pg_basebackup -h "$PRIMARIO_HOST" -p "$PRIMARIO_PORTA" -U replicador \
        -D "$PGDATA" -X stream -R -S "$SLOT_REPLICACAO" --progress
    chmod 0700 "$PGDATA"
fi

# Os mesmos parâmetros do primário: necessário para hot standby e para a réplica poder ser promovida.
exec postgres \
    -c hot_standby=on \
    -c wal_level=replica \
    -c max_wal_senders=10 \
    -c max_replication_slots=10 \
    -c max_prepared_transactions=50
