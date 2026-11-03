#!/usr/bin/env bash
# Executado UMA vez, na inicialização do PostgreSQL primário (docker-entrypoint-initdb.d).
# Cria o utilizador usado pelas réplicas e autoriza ligações de replicação a partir de qualquer IP
# (a réplica está noutra máquina da rede local).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
    CREATE ROLE replicador WITH REPLICATION LOGIN PASSWORD '${REPLICACAO_PASSWORD}';
SQL

echo "host replication replicador all scram-sha-256" >> "$PGDATA/pg_hba.conf"
