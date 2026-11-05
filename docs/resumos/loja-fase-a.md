# Loja — Fase A (backend)

Branch `loja` (tag `antes-loja` = estado anterior).

## Esquema (`db/init/05-loja.sh`, idempotente; `deploy/migrar-loja.ps1` para instalações existentes)
- `produto.preco_venda`, `produto.preco_custo` (MZN); `stock.reservado` (CHECK 0 ≤ reservado ≤ quantidade);
  `ordem_compra.preco_unitario`; `pedido_cliente` + `linha_pedido_cliente` (fragmentados por região).

## API
| Método | Caminho | Nó | Para quê |
|---|---|---|---|
| GET | `/api/loja/catalogo` | qualquer | cidades (aberta / indisponível + motivo), produtos, preço, disponível |
| POST | `/api/loja/pedidos` | o da cidade | compra: cria PENDENTE e reserva (409 se não houver disponível) |
| GET | `/api/loja/pedidos/{id}` | qualquer | "o meu pedido" |
| GET | `/api/pedidos?estado=PENDENTE` | o do armazém | pedidos do armazém (painel) |
| GET | `/api/pedidos/resumo` | o do armazém | pendentes e vendas de hoje (MZN) |
| POST | `/api/pedidos/{id}/autorizar` | o do armazém | saída VENDA/LOJA + recibo no HDFS |
| POST | `/api/pedidos/{id}/recusar` `{"motivo": "..."}` | o do armazém | liberta a reserva |

`/api/stock` e `/api/global/stock` passaram a incluir `reservado` e `disponivel`; `/api/ordens` inclui
`precoUnitario` e `total`.

## Testes
63/63 (11 novos em `ServicoLojaIT`, contra PostgreSQL real: reserva, compra acima do disponível sem
reservas parciais, saída manual não toca no reservado, concorrência de 8 clientes na última unidade,
autorizar, recusar, decidir duas vezes, resumo, reposição na compra e coberta por encomenda;
mais 1 em `StockTest` para a nova condição).

Na stack real: compra → disponível desce; 6 clientes pela última unidade → 1 × 201 + 5 × 409;
autorizar → movimento VENDA/LOJA + `/scad/recibos/2026/11/PED-SUL-000001.pdf`; recusar → reserva libertada;
compra que deixa o disponível abaixo do mínimo → ordem em ~4 s com preço e total; factura no HDFS;
`docker stop no-centro` → Beira "indisponível de momento" em ~4 s, as outras cidades continuam.
Migração testada sobre uma BD com o esquema antigo (dados preservados, idempotente).
