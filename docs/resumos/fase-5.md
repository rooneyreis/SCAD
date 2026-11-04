# Fase 5 — Serviços Web: fornecedor-mock REST + SOAP e gateway

## O que foi feito
- **`fornecedor-contrato`**: interface JAX-WS `ServicoFornecedor` (operação `SubmeterOrdemCompra`), as mensagens `PedidoOrdem` e `ConfirmacaoOrdem` (as mesmas em JSON e em XML) e o `FalhaFornecedor` (SOAP Fault com detalhe `codigo/mensagem/temporaria`).
- **`fornecedor-mock`** (porta 8090):
  - REST: `POST /api/v1/ordens` com o cabeçalho `Idempotency-Key` → 201 (nova), 200 (duplicada), 400 (inválida), 503 (em baixo), 500 (erro).
  - SOAP (Apache CXF): `/ws/fornecedor`, com o WSDL em `/ws/fornecedor?wsdl`; os erros vão como SOAP Fault.
  - Idempotência pelo `ordemId` (um reenvio devolve a confirmação original); validação (campos obrigatórios, soma das linhas = quantidade).
  - Interruptor `GET/PUT /api/controlo` com `{"modo": "NORMAL" | "EM_BAIXO" | "LENTO" | "ERRO"}`, mais estatísticas e o registo dos pedidos.
- **Gateway** (`GatewayFornecedores`, padrão Adapter): a interface `ClienteFornecedor` tem as implementações `ClienteRest` e `ClienteSoap`, e o `fornecedor.protocolo` decide qual se usa. Timeouts de 2 s (ligação) e 4 s (resposta); 3 tentativas com espera exponencial; erros permanentes não se repetem; com sucesso, 2PC para `ENVIADA`; em caso de falha, a ordem fica `PENDENTE_ENVIO` e é reenviada a cada 15 s.
- Dados: GRAO-DOURADO e DOCE-VALE usam REST; OLEOS-LIMPOPO (óleo e sabão) usa SOAP.

## Como testar
```powershell
Invoke-RestMethod http://localhost:8090/ws/fornecedor?wsdl          # WSDL
Invoke-RestMethod http://localhost:8090/api/controlo -Method Put -Body '{"modo":"EM_BAIXO"}' -ContentType 'application/json'
# provocar uma ordem (ex.: saída de 10 de ACUCAR-10 no SUL) -> 3 tentativas falham, fica PENDENTE_ENVIO
Invoke-RestMethod http://localhost:8083/api/fornecedores/eventos
Invoke-RestMethod http://localhost:8090/api/controlo -Method Put -Body '{"modo":"NORMAL"}' -ContentType 'application/json'
# até ~15 s depois: ENVIADA
```
Verificado: REST (FARINHA) e SOAP (OLEO) ficam ENVIADA. Com o fornecedor em baixo, ficam 3 tentativas registadas e a ordem pendente, que é reenviada quando o fornecedor volta. No modo LENTO, o cliente desiste por timeout mas o fornecedor aceitou a ordem; o reenvio é reconhecido como **duplicado**, com a mesma confirmação, e há **uma só compra**.

Testes: `RecepcaoOrdensTest` (3): idempotência, validação, falhas temporárias. Total: **50 testes**.

## Problemas conhecidos
- O WSDL anuncia `http://localhost:8090/...` (o endereço por onde foi pedido); o cliente usa o URL configurado, por isso não afecta o sistema.
- Os pedidos aceites pelo mock ficam em memória (perdem-se se o container reiniciar).
