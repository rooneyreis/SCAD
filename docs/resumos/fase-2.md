# Fase 2 — Dados e fluxo base (saída de stock → alerta CORBA)

## O que foi feito
- Esquema SQL (`db/init`): o catálogo (`armazem`, `fornecedor`, `produto`) está **replicado** em todos os fragmentos; `stock`, `movimento_stock`, `linha_ordem` e `alerta` estão **fragmentados por região**. O predicado de fragmentação é imposto com `CHECK (armazem_id = '<REGIÃO>')`.
- Dados iniciais (`db/dados`) com cenários de demonstração (ver a tabela em `docs/notas-relatorio.md`).
- Saída de stock atómica (`UPDATE ... WHERE quantidade >= ?`), com o movimento gravado com carimbo de Lamport e hora corrigida (desvio simulado por nó).
- Agente de stock: classifica cada stock (NORMAL/AVISO/CRITICO/RUPTURA) e grava os alertas numa fila local (tabela `alerta`, padrão *outbox*), que é enviada à Central por CORBA (`dispararAlerta`) e reenviada se a Central estiver em baixo.
- CORBA robusto: POA persistente (IORs fixos), `_non_existent()` antes do `oneway` e deduplicação na Central.

## Como testar
```powershell
docker compose -f deploy/local/docker-compose.yml up -d --build
$s = @{ armazemId='NORTE'; produtoId='ARROZ-25'; quantidade=10; motivo='Venda' } | ConvertTo-Json
Invoke-RestMethod http://localhost:18081/api/movimentos/saida -Method Post -Body $s -ContentType 'application/json'
docker compose -f deploy/local/docker-compose.yml logs central | Select-String ALERTA
```
Testes automáticos: ver o README (`mvn verify`; 27 testes).

## Problemas conhecidos
- O teste de fumo com 2 máquinas (`deploy/fumo`) ficou preparado mas não foi executado (adiado por decisão tua).
- Neste PC, o `.wslconfig` tem `networkingMode=mirrored` e há um PostgreSQL do Windows na porta 5432. Isto afecta o acesso por IP (ver o guião da demo).
