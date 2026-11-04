# Fase 4 — SOD: Ricart-Agrawala com detector de falhas; Berkeley

## O que foi feito
- **Ricart-Agrawala** (`RicartAgrawala`): exclusão mútua **por recurso** `fornecedor/produto`, com carimbos de Lamport e desempate pelo id do nó. Os OKs são adiados enquanto o nó está DENTRO, ou a PEDIR com prioridade maior. Os pedidos sem resposta são reenviados a cada 2 s e há um timeout de 30 s. As mensagens vão por HTTP (`/interno/ra/pedido`, `/interno/ra/ok`).
- **Detector de falhas** (`DetectorFalhas`): heartbeats a cada 1 s; um nó sem resposta há mais de 3 s é declarado MORTO, e o RA deixa de esperar pelo seu OK. Quando volta, é reintegrado.
- **Berkeley** (`MestreBerkeley`, na Central, a cada 20 s): `obterRelogio` por CORBA com compensação de RTT; média sem valores absurdos (a mais de 10 s da mediana); `ajustarRelogio` com o ajuste de cada nó. Cada nó guarda o desvio antes e depois de cada ajuste (`/api/relogio`). Também é possível desregular um relógio para a demo (`POST /api/relogio/perturbar?ms=3000`).
- Desvios iniciais simulados: NORTE +2500 ms, CENTRO −1800 ms, SUL +700 ms. Depois da primeira ronda, todos ficam em +350 ms (a média, incluindo o mestre a 0).
- Painel: `/api/exclusao` (membros, estado por recurso, OKs adiados, eventos).
- Para a fila do RA ser visível, a secção crítica tem uma demora artificial de 2 s (`SCAD_DEMORA_SC_MS`).

## Como testar
Pico simultâneo, sem painel:
```powershell
$a = @{ armazemId='NORTE';  produtoId='ARROZ-25'; quantidade=10; motivo='Pico' } | ConvertTo-Json
$b = @{ armazemId='CENTRO'; produtoId='ARROZ-25'; quantidade=10; motivo='Pico' } | ConvertTo-Json
Start-Job { Invoke-RestMethod http://localhost:18081/api/movimentos/saida -Method Post -Body $using:a -ContentType 'application/json' } | Out-Null
Invoke-RestMethod http://localhost:18082/api/movimentos/saida -Method Post -Body $b -ContentType 'application/json' | Out-Null
Start-Sleep 8
(Invoke-RestMethod http://localhost:18081/api/ordens) | Where-Object produtoId -eq 'ARROZ-25'   # UMA ordem
(Invoke-RestMethod http://localhost:18081/api/exclusao).eventos                                 # OK ADIADO, re-verificação
```
Resultado verificado: o CENTRO (ts=7) entrou primeiro; o NORTE (ts=8) ficou com o OK adiado; houve 1 ordem (CENTRO 306 + NORTE 194); o NORTE, na re-verificação, não criou nenhuma ordem.

Testes: `RicartAgrawalaTest` (5): 60 entradas concorrentes de 3 nós sem violação; recursos diferentes não se bloqueiam; OK adiado e ordem de entrada; nó que morre dentro da SC não bloqueia os outros; timeout. `MestreBerkeleyTest`: mediana. Total: **47 testes**.

## Problemas conhecidos
- Um OK atrasado de uma ronda anterior poderia, em teoria, ser contado na ronda seguinte (os OKs não levam o carimbo do pedido). É raro e não afecta a demo.
- O detector de falhas não distingue um nó lento de um nó morto (assume um sistema síncrono com limite de 3 s).
