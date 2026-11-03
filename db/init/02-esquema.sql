-- =====================================================================================
-- Esquema SCAD — igual em todos os fragmentos (NORTE, CENTRO, SUL).
--
-- Catálogo REPLICADO em todos os fragmentos: armazem, fornecedor, produto.
-- Tabelas FRAGMENTADAS horizontalmente por região (só têm linhas do armazém da região;
-- o predicado de fragmentação é imposto por CHECK em 04-fragmento.sh):
--   stock, movimento_stock, linha_ordem, alerta.
-- ordem_compra: o cabeçalho existe em todos os fragmentos que participam na ordem (2PC).
-- =====================================================================================

CREATE TABLE armazem (
    id      VARCHAR(10) PRIMARY KEY,
    nome    TEXT        NOT NULL,
    cidade  TEXT        NOT NULL
);

CREATE TABLE fornecedor (
    id         VARCHAR(20) PRIMARY KEY,
    nome       TEXT        NOT NULL,
    protocolo  VARCHAR(4)  NOT NULL CHECK (protocolo IN ('REST', 'SOAP')),
    endpoint   TEXT        NOT NULL   -- caminho no serviço do fornecedor; o host vem da configuração
);

CREATE TABLE produto (
    id             VARCHAR(20) PRIMARY KEY,
    nome           TEXT        NOT NULL,
    unidade        TEXT        NOT NULL,
    fornecedor_id  VARCHAR(20) NOT NULL REFERENCES fornecedor (id),
    lote           INTEGER     NOT NULL CHECK (lote > 0)   -- quantidade fixa de cada ordem de compra
);

CREATE TABLE stock (
    armazem_id      VARCHAR(10) NOT NULL REFERENCES armazem (id),
    produto_id      VARCHAR(20) NOT NULL REFERENCES produto (id),
    quantidade      INTEGER     NOT NULL CHECK (quantidade >= 0),
    minimo          INTEGER     NOT NULL CHECK (minimo >= 0),
    maximo          INTEGER     NOT NULL,
    em_encomenda    INTEGER     NOT NULL DEFAULT 0 CHECK (em_encomenda >= 0),
    actualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (armazem_id, produto_id),
    CHECK (maximo > minimo)
);

CREATE TABLE movimento_stock (
    id            BIGSERIAL   PRIMARY KEY,
    armazem_id    VARCHAR(10) NOT NULL,
    produto_id    VARCHAR(20) NOT NULL,
    tipo          VARCHAR(10) NOT NULL CHECK (tipo IN ('SAIDA', 'ENTRADA')),
    motivo        TEXT        NOT NULL,
    quantidade    INTEGER     NOT NULL CHECK (quantidade > 0),
    ts_lamport    BIGINT      NOT NULL,
    ts_corrigido  TIMESTAMPTZ NOT NULL,   -- hora do relógio corrigido (Berkeley)
    origem        VARCHAR(10) NOT NULL,   -- nó que registou o movimento
    FOREIGN KEY (armazem_id, produto_id) REFERENCES stock (armazem_id, produto_id)
);
CREATE INDEX idx_movimento_recentes ON movimento_stock (id DESC);

CREATE TABLE ordem_compra (
    id                VARCHAR(40) PRIMARY KEY,          -- também é a chave de idempotência no fornecedor
    fornecedor_id     VARCHAR(20) NOT NULL REFERENCES fornecedor (id),
    produto_id        VARCHAR(20) NOT NULL REFERENCES produto (id),
    quantidade_total  INTEGER     NOT NULL CHECK (quantidade_total > 0),
    estado            VARCHAR(20) NOT NULL CHECK (estado IN ('PENDENTE_ENVIO', 'ENVIADA', 'ENTREGUE')),
    armazem_origem    VARCHAR(10) NOT NULL REFERENCES armazem (id),   -- armazém que disparou
    coordenador       VARCHAR(10) NOT NULL,                           -- nó coordenador do 2PC
    confirmacao       TEXT,                                           -- nº de confirmação do fornecedor
    prazo_entrega     DATE,
    ts_lamport        BIGINT      NOT NULL,
    ts_corrigido      TIMESTAMPTZ NOT NULL,
    actualizado_em    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE linha_ordem (
    ordem_id    VARCHAR(40) NOT NULL REFERENCES ordem_compra (id),
    armazem_id  VARCHAR(10) NOT NULL REFERENCES armazem (id),
    quantidade  INTEGER     NOT NULL CHECK (quantidade > 0),
    PRIMARY KEY (ordem_id, armazem_id)
);

-- Alertas de stock emitidos pelo agente deste armazém. Funciona como "outbox":
-- é escrito na mesma transacção do movimento e enviado à Central (CORBA) depois;
-- enquanto enviado_em for NULL, o alerta continua na fila local para reenvio.
CREATE TABLE alerta (
    id                UUID        PRIMARY KEY,
    armazem_id        VARCHAR(10) NOT NULL,
    produto_id        VARCHAR(20) NOT NULL,
    quantidade_actual INTEGER     NOT NULL,
    em_encomenda      INTEGER     NOT NULL,
    minimo            INTEGER     NOT NULL,
    nivel             VARCHAR(10) NOT NULL CHECK (nivel IN ('AVISO', 'CRITICO', 'RUPTURA')),
    ts_lamport        BIGINT      NOT NULL,
    ts_corrigido      TIMESTAMPTZ NOT NULL,
    enviado_em        TIMESTAMPTZ,
    FOREIGN KEY (armazem_id, produto_id) REFERENCES stock (armazem_id, produto_id)
);
CREATE INDEX idx_alerta_pendente ON alerta (ts_lamport) WHERE enviado_em IS NULL;

-- Log do coordenador do 2PC (não é fragmentado: fica no fragmento do nó coordenador).
-- A decisão é gravada ANTES de ser comunicada aos participantes; se o coordenador morrer
-- entre as fases, ao reiniciar lê este log e conclui as transacções preparadas.
CREATE TABLE log_2pc (
    transacao_id  VARCHAR(60) PRIMARY KEY,
    tipo          VARCHAR(20) NOT NULL,
    coordenador   VARCHAR(10) NOT NULL,
    participantes TEXT        NOT NULL,          -- ex.: "NORTE,CENTRO,SUL"
    decisao       VARCHAR(10) NOT NULL CHECK (decisao IN ('COMMIT', 'ABORT')),
    concluida     BOOLEAN     NOT NULL DEFAULT false,
    ts_lamport    BIGINT      NOT NULL,
    ts_corrigido  TIMESTAMPTZ NOT NULL
);
