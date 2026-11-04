# Guião da demonstração — SCAD

Tudo corre **num só PC** (`deploy/local`): os 3 nós, os 6 PostgreSQL (3 primários + 3 réplicas), o HDFS
(1 NameNode + 3 DataNodes), o Naming Service, a Central de Auditoria, o fornecedor e o painel são containers separados.

Duração sugerida: **15 a 20 minutos**. Os comandos são para PowerShell, na pasta `scad` do repositório.

```powershell
$C = "docker compose -f deploy/local/docker-compose.yml"     # atalho usado abaixo: Invoke-Expression "$C stop no-centro"
```
(Também se pode escrever o comando completo: `docker compose -f deploy/local/docker-compose.yml stop no-centro`.)

---

## 0. Preparação (na noite anterior e 15 min antes)

1. **Docker Desktop** a correr (cerca de 3 GB de RAM livres).
2. Arrancar do zero, com os dados iniciais:
   ```powershell
   docker compose -f deploy/local/docker-compose.yml down -v
   docker compose -f deploy/local/docker-compose.yml up -d --build
   ```
   Esperar **~60 s**. Abrir **http://localhost:8080**. Na faixa do topo deve estar tudo verde.
3. Logo no arranque, o sistema já cria **uma ordem sozinho**: a FARINHA-25 do SUL começa abaixo do mínimo
   (70 < 80), por isso aparece a ordem `OC-SUL-…` (SUL 230 + CENTRO 170, enviada por REST). Isto serve de primeiro exemplo.
4. **Acesso a partir do telemóvel/portátil** (opcional):
   - PowerShell **como Administrador**: `powershell -ExecutionPolicy Bypass -File deploy\abrir-porta-painel.ps1`
     (abre só a porta 8080 e mostra o endereço, por exemplo `http://192.168.1.20:8080`).
   - O telemóvel tem de estar **na mesma rede Wi-Fi**, e essa rede não pode isolar os clientes.
   - ⚠️ **Neste PC o WSL está em modo `mirrored`** (`%USERPROFILE%\.wslconfig`). Testámos e, neste modo, as portas
     do Docker **só respondem em `localhost`**, não pelo IP do PC. Se o telemóvel não abrir o painel:
     1. editar `%USERPROFILE%\.wslconfig` e pôr `#` antes de `networkingMode=mirrored`;
     2. `wsl --shutdown`; reiniciar o Docker Desktop; repetir o passo 2.
     (Isto muda a rede do Ubuntu do WSL para NAT, o padrão. Para voltar ao modo mirrored depois, basta tirar o `#`.)
   - Testar **antes** da apresentação: abrir `http://<IP>:8080` no telemóvel.
5. Ter abertas duas janelas: o browser no painel e um PowerShell na pasta `scad`.

**Repor tudo entre ensaios:** `down -v` + `up -d --build` (passo 2). Depois de um failover é **obrigatório**,
porque o primário antigo não volta a ser réplica sozinho.

---

## 1. Visão geral (2 min) — painel, separador **Stock**

- **Faixa do topo**: 3 nós (com o relógio de Lamport), Central CORBA, HDFS, fornecedor e a BD de cada fragmento (primário).
- **Stock**: 3 armazéns × 6 produtos. Barra: cor = nível; tracejado azul = em encomenda; traço preto = mínimo.
  Regra: reposição quando `quantidade + em_encomenda < mínimo`.
- Explicar a **fragmentação**: cada armazém tem a sua BD (o fragmento); o catálogo está replicado em todas.
  O painel lê o stock **dos 3 fragmentos** através de qualquer nó.

## 2. Fluxo normal de uma compra (3 min) — critérios SBDD, CORBA, Serviços Web, DFS

1. Em **Stock**: armazém NORTE, **Óleo alimentar 5 L**, quantidade **45**, "Registar saída".
   → a mensagem diz "abaixo do mínimo: alerta CORBA + pedido de reposição".
2. **Monitor → Reposições (nó NORTE)**: pede a secção crítica → DENTRO → repartição do lote de 600 →
   `{NORTE=390, CENTRO=100, SUL=110}` → ordem criada (2PC COMMIT) → liberta.
   - Explicar a repartição: P1 NORTE fica com a sua necessidade (195); sem armazéns "perto do mínimo";
     P3 CENTRO e SUL proporcionais à necessidade (100 e 110); sobram 195 → **excesso para quem disparou**.
3. **Monitor → Two-Phase Commit**: `OC-NORTE-…` CRIAR_ORDEM, votos NORTE/CENTRO/SUL = SIM, COMMIT, fase 2 ✓.
   Passar o rato sobre a linha para ver a cronologia (PREPARE TRANSACTION → decisão gravada → COMMIT PREPARED).
4. **Ordens**: a ordem passa a **ENVIADA** por **SOAP** (o fornecedor de óleo é "legado"), com o número de confirmação.
   Clicar em **PDF**: a factura vem do HDFS.
5. **Ficheiros (HDFS)**: factura, JSON do 2PC e auditoria, com **réplicas 3/3**.
6. Alerta CORBA na Central: `docker compose -f deploy/local/docker-compose.yml logs --tail 20 central`
   → `ALERTA CRITICO de NORTE: produto=OLEO-5 …` (e as rondas do Berkeley).
7. **Receber entrega**: em **Ordens**, botão "Receber entrega" → 2PC (RECEBER_ENTREGA) → a ordem fica **ENTREGUE**
   e o stock de cada armazém sobe (ver **Stock** e **Movimentos**, com as ENTRADAs).

## 3. Pico simultâneo — exclusão mútua (2 min) — critério SOD

1. **Stock** → botão vermelho **⚡ Pico simultâneo (NORTE + CENTRO)**
   (10 de ARROZ-25 em cada armazém, ao mesmo tempo; ambos ficam abaixo do mínimo de 100).
2. Ir logo ao **Monitor → Exclusão mútua**: um nó está **DENTRO** (linha amarela); o outro ficou com
   **"OK ADIADO"**, à espera. A secção crítica tem 2 s de propósito, para se conseguir ver.
3. Resultado esperado:
   - **Ordens**: só **UMA** ordem de ARROZ-25 (ex.: CENTRO 306 + NORTE 194, porque o NORTE está "perto do mínimo" = P2).
   - **Monitor → Reposições** do nó que entrou em segundo lugar: *"Re-verificação: ARROZ-25 já não precisa de reposição
     (… em encomenda=194) → não cria ordem"*.
   - Explicar: Ricart-Agrawala **por recurso** (fornecedor/produto), carimbos de Lamport, desempate pelo id do nó;
     quem tem o carimbo menor entra primeiro; a re-verificação dentro da secção crítica evita a ordem duplicada.

## 4. Relógios — Berkeley (1 min) — critério SOD

- **Monitor → Relógios**: os 3 nós com o **mesmo desvio** (~+350 ms). Começaram em +2500, −1800 e +700 ms
  e o mestre (Central) em 0: a média é 350. Berkeley põe todos de acordo entre si, não com a hora "real".
- Botão **"Desregular +3 s"** num nó → o desvio salta → em **≤ 20 s** a próxima ronda corrige
  (coluna "Última ronda Berkeley: antes → depois").
- **Movimentos**: cada registo tem a hora corrigida **e** o carimbo de Lamport.

## 5. Falhas simuladas (6–8 min) — critério "comportamento perante falhas"

Fazer pela ordem abaixo (do menos para o mais destrutivo). Entre cenários não é preciso repor, excepto onde indicado.

### 5.1 Fornecedor em baixo → ordem pendente e reenviada
| Passo | Acção | O que deve aparecer |
|---|---|---|
| 1 | **Fornecedor** → botão **Em baixo** | Faixa: Fornecedor `EM_BAIXO` (amarelo) |
| 2 | **Stock**: SUL, **Açúcar castanho**, quantidade **10** | — |
| 3 | **Fornecedor → Gateway dos nós** | 3 tentativas (0,5 s, 1 s de espera) com HTTP 503 → "fica PENDENTE_ENVIO" |
| 4 | **Ordens** | a ordem `OC-SUL-…` está **PENDENTE_ENVIO** |
| 5 | **Fornecedor** → **Normal** | em ≤ 15 s a ordem passa a **ENVIADA** (reenvio automático) |

Variante **Lento** (idempotência): modo **Lento** + nova ordem → o cliente desiste ao fim de 4 s, mas o fornecedor
aceita a ordem 8 s depois. Voltar a **Normal** → o reenvio é reconhecido como **duplicado**: "duplicados ignorados"
sobe e as **ordens únicas não aumentam**. Um reenvio nunca duplica a compra.

### 5.2 Desligar um nó: `no-centro`
```powershell
docker compose -f deploy/local/docker-compose.yml stop no-centro
```
| O que deve aparecer | Quando |
|---|---|
| Faixa: **Nó CENTRO EM BAIXO** (vermelho); o painel continua a funcionar | imediato |
| **Monitor → Exclusão mútua**: os outros nós vêem `CENTRO: MORTO` | ~3 s (detector de falhas por heartbeats) |
| **Stock**: o stock do CENTRO **continua visível** (o fragmento pg-centro está vivo) | — |
| Saída no CENTRO dá erro (a "loja" CENTRO está em baixo) | — |
| Uma compra no NORTE continua a funcionar e **inclui o armazém CENTRO** (o 2PC fala directamente com o fragmento) | ex.: Feijão, NORTE, 100 |

Voltar: `docker compose -f deploy/local/docker-compose.yml start no-centro` → `CENTRO: VIVO` (reintegrado).

### 5.3 Parar o primário de um fragmento: `pg-centro` → failover
```powershell
docker compose -f deploy/local/docker-compose.yml stop pg-centro
```
| O que deve aparecer | Quando |
|---|---|
| **Monitor → Base de dados distribuída**: CENTRO = **RÉPLICA PROMOVIDA** (`pg-centro-replica`) | ~6–10 s |
| Faixa: **BD CENTRO — RÉPLICA promovida** (amarelo) | idem |
| Eventos de failover: "Primário pg-centro caiu: réplica PROMOVIDA a primário por este nó" (nó SUL, o responsável no anel) e "já é primária (promovida por outro nó)" nos outros | idem |
| Saída no CENTRO **funciona** (escreve na réplica promovida); stock e ordens do CENTRO continuam visíveis | — |

⚠️ Não há regresso automático ao primário antigo (para evitar split-brain). Depois deste cenário, **repor tudo** (secção 0).

### 5.4 Matar o coordenador entre as fases do 2PC → recuperação
```powershell
Invoke-RestMethod "http://localhost:8081/api/demo/2pc-pausa?ms=25000" -Method Post   # pausa de 25 s antes da fase 2, só no NORTE
```
1. **Stock**: NORTE, **Arroz 25 kg**, quantidade **10** (o NORTE coordena a ordem).
2. ~5 s depois, em **Monitor → Two-Phase Commit**: a decisão **COMMIT** já está gravada, mas a fase 2 está vazia (pendente).
   Opcional, transacções preparadas na BD:
   `docker compose -f deploy/local/docker-compose.yml exec pg-norte psql -U scad -d scad -c "select gid from pg_prepared_xacts"`
3. **Matar o coordenador**: `docker compose -f deploy/local/docker-compose.yml stop no-norte`
4. Voltar a arrancá-lo: `docker compose -f deploy/local/docker-compose.yml start no-norte`
5. ~20–30 s depois: **Monitor → 2PC** mostra `OC-NORTE-… CRIAR_ORDEM (recuperação) COMMIT` com a fase 2 ✓;
   a ordem aparece em **Ordens** e é enviada ao fornecedor. As transacções preparadas voltam a 0.
   Explicar: a decisão foi gravada no log do coordenador **antes** da fase 2; ao reiniciar, ele lê o log e conclui.
   (A pausa é perdida no reinício, por isso as compras seguintes voltam ao normal.)

### 5.5 Parar um DataNode → as facturas continuam acessíveis
```powershell
docker compose -f deploy/local/docker-compose.yml stop datanode-centro
```
**Ficheiros (HDFS)** → abrir uma factura: abre normalmente (há réplicas noutros DataNodes). Depois: `start datanode-centro`.

Extra, **HDFS (NameNode) em baixo**: `stop namenode` → faixa HDFS vermelha → provocar uma ordem → **Ficheiros**
mostra "fila local: N ficheiros à espera" → `start namenode` → em ~30 s a fila esvazia e os ficheiros aparecem.
Dizer que **o NameNode é o ponto único de falha** do HDFS nesta configuração.

### 5.6 Central de Auditoria em baixo
`stop central` → faixa "Central CORBA EM BAIXO"; as compras **continuam**; os alertas ficam na fila local (outbox).
`start central` → os alertas pendentes chegam (ver `logs central`). Os IORs são persistentes, por isso a referência antiga
continua válida.

---

## 6. Perguntas prováveis (respostas curtas)

- **Porque 2PC e não Paxos/Raft?** As compras tocam vários fragmentos *diferentes* (atomicidade entre dados distintos),
  não réplicas do mesmo dado. O PostgreSQL suporta-o nativamente (`PREPARE TRANSACTION`).
  O bloqueio do 2PC foi mitigado com o log do coordenador, a recuperação e o "presumed abort".
- **E se o coordenador morrer antes de decidir?** As transacções preparadas sem decisão no log são anuladas pela recuperação ao fim de 30 s.
- **Como evitam duas ordens para o mesmo lote?** Ricart-Agrawala por (fornecedor, produto) **e** re-verificação dentro da secção crítica.
- **E se um nó morrer dentro da secção crítica?** O detector de falhas (3 s sem heartbeat) tira-o dos participantes, e os outros deixam de esperar pelo seu OK.
- **JacORB em Java 17?** O JDK 11 removeu o CORBA e o RMI-IIOP; usamos `jacorb-omgapi` + `yoko-rmi-spec` + 2 delegates mínimos (ver `docs/notas-relatorio.md`).
- **Alertas oneway perdidos?** POA persistente, `_non_existent()` antes do envio e deduplicação na Central.
- **Idempotência no fornecedor?** O id da ordem é a chave (`Idempotency-Key` em REST, `ordemId` em SOAP).

## 7. Se algo correr mal durante a demo

| Problema | Solução rápida |
|---|---|
| Faixa toda vermelha / painel não abre | `docker compose -f deploy/local/docker-compose.yml ps`; se faltar algo: `up -d` |
| Uma ordem não aparece | esperar ~15 s (reenvio, verificação a cada 30 s); ver **Monitor → Reposições** |
| Ficou num estado estranho | repor: `down -v` + `up -d --build` (~1 min) |
| Telemóvel não abre o painel | mostrar no PC; ver o ponto 0.4 (WSL mirrored, firewall, mesma rede) |
