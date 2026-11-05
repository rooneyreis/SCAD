# Loja — Fase B (interfaces)

Branch `loja`.

## Loja — `http://<IP>:8085` (app `loja/`, contentor `loja`)
- Escolha da cidade (Maputo = SUL, Beira = CENTRO, Nampula = NORTE; "Indisponível de momento" se o nó/BD estiver em baixo).
- Cartões de produto: preço, "N disponíveis em <cidade>" / "Últimas N" / "Esgotado", quantidade limitada ao disponível,
  variação em tempo real (−3 / +400).
- Carrinho por cidade, ajuste automático se o disponível descer, checkout com nome, telefone e pagamento simulado.
- "O meu pedido": PENDENTE → AUTORIZADO (recibo PDF) / RECUSADO (motivo), actualiza-se sozinho; "Os meus pedidos".
- O proxy só expõe `/api/<região>/loja/...`; porta 8085 porque a 8084 está ocupada (Plastic SCM).

## Painéis dos armazéns
- Separador **Pedidos de clientes**: contador na barra lateral, notificação de pedido novo, cartões com Autorizar / Recusar,
  histórico com recibo.
- Cartões: + "pedidos de clientes pendentes" e "vendas de hoje (MZN)". Tabela de stock com Reservado e Disponível.
- Ordens com preço unitário (custo) e valor; Ficheiros HDFS incluem os recibos do armazém.

## Outros
- `GET /api/loja/pedidos/{id}/recibo` (backend); `deploy/abrir-porta-painel.ps1` abre também a 8085 (`-Profile Any`);
  `deploy/demo-ultima-unidade.ps1`; guião (secção 2b, disposição, perguntas) e notas do relatório actualizados.
