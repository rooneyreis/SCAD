# Fase 6 — DFS: facturas PDF, auditoria e logs no HDFS, com fila local

## O que foi feito
- **Fila local** (`FilaHdfs`, em `scad-common`): todos os ficheiros são primeiro gravados no disco do nó (volume `/dados/fila-hdfs`) e depois enviados ao HDFS, por ordem. Se o HDFS estiver em baixo, ficam à espera, mesmo que o nó reinicie. A fila é esvaziada a cada 5 s e logo a seguir a cada documento.
- **Factura em PDF** (OpenPDF, `GeradorFactura`): gerada quando a ordem fica ENVIADA → `/scad/facturas/AAAA/MM/OC-xxx.pdf`. Contém o fornecedor, o canal (REST/SOAP), a confirmação, o prazo, a repartição por armazém, o carimbo de Lamport e a hora corrigida.
- **2PC** → `/scad/2pc/<transacao>-<nó>.json`: a cronologia completa (participantes, votos, decisão, fase 2).
- **Auditoria** (Central, a cada 60 s): `consultarTodos` por CORBA a cada agente, mais os alertas recebidos no período → `/scad/auditoria/AAAA-MM-DD/auditoria-HHmmss.txt`.
- **Logs de erros** → `/scad/logs/erros/AAAA-MM-DD/<nó>-<hora>.log`: 2PC abortados, envios ao fornecedor falhados e eventos de failover.
- API: `GET /api/ficheiros?dir=/scad` (listagem recursiva com factor de replicação e réplicas vivas, mais o tamanho da fila local) e `GET /api/ficheiros/conteudo?caminho=...` (download; o PDF abre no browser).

## Como testar
```powershell
Invoke-RestMethod http://localhost:8081/api/ficheiros            # lista
Start-Process "http://localhost:8081/api/ficheiros/conteudo?caminho=/scad/facturas/2026/11/OC-SUL-000004.pdf"
# HDFS em baixo
docker compose -f deploy/local/docker-compose.yml stop namenode
# ... provocar uma ordem ... -> filaLocalPendentes > 0
docker compose -f deploy/local/docker-compose.yml start namenode   # ~30 s depois a fila esvazia
```
Verificado: com o NameNode parado ficaram 3 ficheiros na fila, enviados quando o HDFS voltou; com um DataNode parado, a factura continua a descarregar-se (replicação 3). Testes: `FilaHdfsTest` (fila com HDFS em baixo e reinício). Total: **51 testes**.

## Problemas conhecidos
- **O NameNode é um ponto único de falha** nesta configuração (sem HA). Com ele em baixo, os ficheiros ficam na fila local e a listagem do painel mostra "HDFS indisponível".
- Os ficheiros na fila local só se vêem no painel depois de chegarem ao HDFS.
