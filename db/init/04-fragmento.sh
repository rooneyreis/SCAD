#!/usr/bin/env bash
# Especializa esta base de dados como o fragmento da região $SCAD_REGIAO:
#  1) impõe o predicado da fragmentação horizontal (armazem_id = região) com CHECK;
#  2) carrega o stock inicial da região.
set -euo pipefail
: "${SCAD_REGIAO:?defina SCAD_REGIAO (NORTE, CENTRO ou SUL)}"

regiao_min=$(echo "$SCAD_REGIAO" | tr '[:upper:]' '[:lower:]')

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
    ALTER TABLE stock           ADD CONSTRAINT fragmento_regiao CHECK (armazem_id = '${SCAD_REGIAO}');
    ALTER TABLE movimento_stock ADD CONSTRAINT fragmento_regiao CHECK (armazem_id = '${SCAD_REGIAO}');
    ALTER TABLE linha_ordem     ADD CONSTRAINT fragmento_regiao CHECK (armazem_id = '${SCAD_REGIAO}');
    ALTER TABLE alerta          ADD CONSTRAINT fragmento_regiao CHECK (armazem_id = '${SCAD_REGIAO}');
    COMMENT ON DATABASE ${POSTGRES_DB} IS 'SCAD - fragmento ${SCAD_REGIAO}';
SQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -f "/scad-dados/stock-${regiao_min}.sql"

echo "[scad] Fragmento ${SCAD_REGIAO} inicializado"
