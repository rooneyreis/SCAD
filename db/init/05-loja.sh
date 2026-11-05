#!/usr/bin/env bash
# Loja (pedidos de clientes) — migração do esquema do fragmento $SCAD_REGIAO.
#
# É idempotente: corre na inicialização de uma BD nova (docker-entrypoint-initdb.d) e também
# serve para migrar um fragmento já existente (deploy/migrar-loja.ps1). Só se aplica ao
# primário: as réplicas recebem as alterações pela streaming replication.
#
#  - catálogo (replicado): preço de venda e de custo de cada produto (MZN);
#  - stock (fragmentado): coluna "reservado" (disponível = quantidade − reservado);
#  - ordem_compra: preço de custo unitário no momento da ordem;
#  - pedido_cliente + linha_pedido_cliente (fragmentados por região).
set -euo pipefail
: "${SCAD_REGIAO:?defina SCAD_REGIAO (NORTE, CENTRO ou SUL)}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
    BEGIN;

    -- Preços (MZN). Referência: retalho em Moçambique, 2026.
    ALTER TABLE produto ADD COLUMN IF NOT EXISTS preco_venda NUMERIC(12, 2);
    ALTER TABLE produto ADD COLUMN IF NOT EXISTS preco_custo NUMERIC(12, 2);
    UPDATE produto p SET preco_venda = v.venda, preco_custo = v.custo
      FROM (VALUES ('ARROZ-25',    1850.00, 1450.00),
                   ('FARINHA-25',  1350.00, 1050.00),
                   ('FEIJAO-10',   1200.00,  950.00),
                   ('ACUCAR-10',    850.00,  650.00),
                   ('OLEO-5',       750.00,  580.00),
                   ('SABAO-BARRA',   60.00,   42.00)) AS v (id, venda, custo)
     WHERE p.id = v.id AND p.preco_venda IS NULL;
    ALTER TABLE produto ALTER COLUMN preco_venda SET NOT NULL;
    ALTER TABLE produto ALTER COLUMN preco_custo SET NOT NULL;

    -- Reserva de stock pelos pedidos da loja ainda não autorizados
    ALTER TABLE stock ADD COLUMN IF NOT EXISTS reservado INTEGER NOT NULL DEFAULT 0;
    ALTER TABLE stock DROP CONSTRAINT IF EXISTS stock_reservado;
    ALTER TABLE stock ADD CONSTRAINT stock_reservado CHECK (reservado >= 0 AND reservado <= quantidade);

    -- Preço de custo fixado no momento da ordem (o catálogo pode mudar depois)
    ALTER TABLE ordem_compra ADD COLUMN IF NOT EXISTS preco_unitario NUMERIC(12, 2);

    CREATE SEQUENCE IF NOT EXISTS seq_pedido_cliente;

    CREATE TABLE IF NOT EXISTS pedido_cliente (
        id                VARCHAR(30) PRIMARY KEY,          -- PED-<REGIAO>-000001
        armazem_id        VARCHAR(10) NOT NULL REFERENCES armazem (id),
        cliente_nome      TEXT        NOT NULL,
        cliente_telefone  VARCHAR(20) NOT NULL,
        estado            VARCHAR(12) NOT NULL CHECK (estado IN ('PENDENTE', 'AUTORIZADO', 'RECUSADO')),
        total             NUMERIC(14, 2) NOT NULL CHECK (total >= 0),
        pagamento_ref     VARCHAR(40) NOT NULL,             -- pagamento simulado
        motivo_recusa     TEXT,
        recibo            TEXT,                             -- caminho do recibo PDF no HDFS
        ts_lamport        BIGINT      NOT NULL,
        criado_em         TIMESTAMPTZ NOT NULL,             -- hora corrigida (Berkeley)
        decidido_em       TIMESTAMPTZ,
        CONSTRAINT fragmento_regiao CHECK (armazem_id = '${SCAD_REGIAO}')
    );
    CREATE INDEX IF NOT EXISTS idx_pedido_estado ON pedido_cliente (estado, criado_em);

    CREATE TABLE IF NOT EXISTS linha_pedido_cliente (
        pedido_id       VARCHAR(30) NOT NULL REFERENCES pedido_cliente (id),
        armazem_id      VARCHAR(10) NOT NULL,
        produto_id      VARCHAR(20) NOT NULL,
        quantidade      INTEGER     NOT NULL CHECK (quantidade > 0),
        preco_unitario  NUMERIC(12, 2) NOT NULL,            -- preço de venda no momento da compra
        PRIMARY KEY (pedido_id, produto_id),
        FOREIGN KEY (armazem_id, produto_id) REFERENCES stock (armazem_id, produto_id),
        CONSTRAINT fragmento_regiao CHECK (armazem_id = '${SCAD_REGIAO}')
    );

    COMMIT;
SQL

echo "[scad] Esquema da loja aplicado ao fragmento ${SCAD_REGIAO}"
