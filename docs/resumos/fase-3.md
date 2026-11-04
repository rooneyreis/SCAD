# Fase 3 — SBDD: ordem consolidada com 2PC, recuperação e failover

## O que foi feito
- **Repartição do lote** (`Reparticao`): as 4 regras que definiste (P1 quem disparou, P2 perto do mínimo do mais vazio para o menos vazio, P3 proporcional sem passar do máximo, e o resto ou excesso para quem disparou). 10 testes JUnit, incluindo os cenários dos dados iniciais.
- **Reposição** (`ServicoReposicao`): secção crítica por recurso `fornecedor/produto` → re-verificação dentro da SC → repartição com o stock de todos os fragmentos acessíveis → 2PC. É disparada depois de cada saída abaixo do mínimo e pela verificação periódica (30 s).
- **2PC** (`Coordenador2PC`): `PREPARE TRANSACTION` em paralelo com timeout de 5 s para os votos; a decisão é gravada em `log_2pc` **antes** da fase 2; depois `COMMIT/ROLLBACK PREPARED`. O mesmo coordenador serve para 3 operações: criar ordem, marcar como enviada e receber entrega.
- **Recuperação do coordenador** (`Recuperacao2PC`, a cada 10 s e no arranque): conclui as decisões do log com a fase 2 incompleta e anula as transacções preparadas sem decisão ("presumed abort").
- **Failover** (`MonitorFailover`, a cada 2 s): ao fim de 3 falhas do primário, o nó responsável (o da região seguinte no anel) faz `pg_promote()` na réplica; os outros nós detectam que a réplica já é primária e passam a usá-la. Não há regresso automático ao primário antigo.
- API: `/api/ordens`, `/api/ordens/{id}/entrega`, `/api/global/stock`, `/api/global/movimentos`, `/api/2pc`, `/api/fragmentos`, `/api/reposicao/eventos`, `/api/demo/2pc-pausa?ms=`.

## Como testar
```powershell
docker compose -f deploy/local/docker-compose.yml down -v
docker compose -f deploy/local/docker-compose.yml up -d --build
# ~20 s depois: a FARINHA-25 do SUL gera sozinha a ordem OC-SUL-... (SUL 230 + CENTRO 170)
Invoke-RestMethod http://localhost:18083/api/ordens
Invoke-RestMethod http://localhost:18083/api/2pc            # votos, decisão, fase 2
# Failover
docker compose -f deploy/local/docker-compose.yml stop pg-centro
Start-Sleep 15; (Invoke-RestMethod http://localhost:18081/api/fragmentos).eventos
```
Testes: `mvn verify` → 41 testes (incluindo `DoisFasesIT` com 3 PostgreSQL reais: commit, abort por voto NÃO, recuperação a partir do log e o ciclo criar → enviada → entregue).

## Problemas conhecidos
- Depois de um failover, o primário antigo não é reintegrado automaticamente como réplica (é preciso recriar o volume). Para a demo: `docker compose down -v` repõe tudo.
- A anulação "presumed abort" só actua sobre transacções preparadas há mais de 30 s.
- Nesta fase a exclusão mútua ainda era local; o Ricart-Agrawala entra na Fase 4.
