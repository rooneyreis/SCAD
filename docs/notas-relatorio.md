# Notas para o relatório técnico

Registo, por fase, das decisões de design, justificações, diagramas e evidência de testes.
Serve de base para escrever o relatório final (máx. 15 páginas).

---

## Fase 1 — Esqueleto e riscos técnicos

### Arquitectura geral

```mermaid
flowchart LR
    subgraph M1[M1 - NORTE]
        noN[no-NORTE]
        pgN[(pg-norte primário)]
        pgSr[(pg-sul réplica)]
        NN[HDFS NameNode]
        DN1[DataNode]
        CEN[Central de Auditoria<br/>+ Naming + mestre Berkeley]
    end
    subgraph M2[M2 - CENTRO]
        noC[no-CENTRO]
        pgC[(pg-centro primário)]
        pgNr[(pg-norte réplica)]
        DN2[DataNode]
    end
    subgraph M3[M3 - SUL]
        noS[no-SUL]
        pgS[(pg-sul primário)]
        pgCr[(pg-centro réplica)]
        DN3[DataNode]
        FORN[fornecedor-mock<br/>REST + SOAP]
    end
    pgN -- streaming --> pgNr
    pgC -- streaming --> pgCr
    pgS -- streaming --> pgSr
    noN & noC & noS -- CORBA/IIOP --> CEN
    noN <-- HTTP: Ricart-Agrawala / 2PC --> noC <--> noS
```

### Decisões

| Decisão | Justificação |
|---|---|
| Maven multi-módulo (`scad-common`, `scad-no`, `scad-central`) | O código partilhado (stubs CORBA, ORB, cliente HDFS) fica num único sítio. |
| Compilação dentro de Docker (multi-stage, `maven:3.9-eclipse-temurin-17`) | As 3 máquinas compilam da mesma forma, sem instalar JDK nem Maven. A cache `~/.m2` é partilhada entre builds. |
| Uma imagem por serviço (`--target no / central / naming`) a partir de um único Dockerfile | A fase de build é partilhada; as imagens finais só têm o JRE 17. |
| Stubs CORBA gerados no build a partir de `idl/SCAD.idl` (compilador IDL do JacORB) | O IDL é a fonte única da verdade; o código gerado não é versionado. O compilador corre numa JVM separada (`exec:exec`) porque termina com `System.exit()`. |
| `jacorb.ior_proxy_host/port` em vez de `OAIAddr` | Dentro do container não existe o IP da máquina Windows, por isso não é possível fazer *bind* a esse IP. O ORB escuta em todas as interfaces do container numa porta fixa e anuncia no IOR o endereço alcançável (IP da máquina em modo distribuído, nome do serviço em modo local). |
| Registo periódico e idempotente (`rebind` no Naming e `registarAgente` na Central a cada 10 s) | Recupera sozinho de reinícios da Central, do Naming ou do próprio nó, sem estado partilhado. |
| Timeouts de cliente no JacORB (ligação 3 s, resposta 5 s, 1 tentativa) | Uma chamada a um processo morto nunca bloqueia o sistema. |
| Cliente HDFS *shaded* (`hadoop-client-api` + `hadoop-client-runtime`) | As dependências internas do Hadoop ficam isoladas e não entram em conflito com as do Spring Boot. Não foi preciso recorrer ao WebHDFS. |
| `dfs.client.use.datanode.hostname=true` e `dfs.datanode.hostname` por DataNode | O cliente liga-se aos DataNodes por um nome ou IP alcançável, e não pelo IP interno do container. |
| `replace-datanode-on-failure.policy=NEVER` | Com só 3 DataNodes não há substituto no pipeline de escrita; se um cair, a escrita continua com os restantes. |
| Réplica Postgres: `pg_basebackup -R` + slot de replicação físico | `-R` configura `primary_conninfo` e `standby.signal`. O slot garante que o primário guarda o WAL enquanto a réplica estiver desligada. |
| `max_prepared_transactions=50` no primário **e** na réplica | É necessário para o 2PC. Uma réplica precisa de um valor igual ou superior ao do primário, e depois de promovida tem de aceitar `PREPARE TRANSACTION`. |

### Risco técnico: JacORB em Java 17

#### O problema

O **JEP 320 (Java 11)** retirou do JDK os módulos `java.corba` e `java.xml.ws`. Com eles saíram:

- o **CORBA**: os pacotes `org.omg.CORBA`, `org.omg.PortableServer`, `org.omg.CosNaming`, …, e o ORB do próprio JDK;
- o **RMI-IIOP**: os pacotes `javax.rmi` e `javax.rmi.CORBA` e as suas implementações (`com.sun.corba.se.impl.javax.rmi.CORBA.Util`, `StubDelegateImpl`).

O JacORB 3.9 (2017) foi escrito para Java 6–8 e assume que o JDK fornece as duas coisas. Os obstáculos apareceram por camadas:

1. **`org.omg.*` em falta.** Resolve-se com o jar `jacorb-omgapi`, que o próprio JacORB publica.
2. **`ClassNotFoundException: javax.rmi.CORBA.Stub`** ao activar o primeiro servant (`POA.servant_to_reference`). A causa é que a classe base de *todas* as referências do JacORB, `org.jacorb.orb.Reference`, estende `javax.rmi.CORBA.Stub`. Mesmo sem usar RMI-IIOP, a classe tem de existir.
3. **`INITIALIZE: Can not create Util delegate`.** Ao adicionar a API `javax.rmi.CORBA`, o inicializador estático de `Stub` procura uma implementação de `javax.rmi.CORBA.UtilDelegate`, e cada construtor de `Stub` procura uma `StubDelegate`. No Java 8 essas implementações vinham do JDK; aqui não existem.

#### A solução

| Peça | Origem | Papel |
|---|---|---|
| `jacorb-omgapi` 3.9 | JacORB | API `org.omg.*` |
| `yoko-rmi-spec` 1.0, **excluindo** `yoko-spec-corba` | Apache Yoko | Só a API `javax.rmi` / `javax.rmi.CORBA`. A exclusão evita uma segunda cópia de `org.omg.*` no classpath, o que causaria conflitos de classes. |
| `UtilDelegateMinimo` | nosso (`common.corba.rmi`) | Implementa `javax.rmi.CORBA.UtilDelegate`: carrega classes e mapeia excepções. |
| `StubDelegateMinimo` | nosso (`common.corba.rmi`) | Implementa `javax.rmi.CORBA.StubDelegate`: `equals/hashCode/toString` delegam no `Delegate` CORBA do objecto, exactamente como em `ObjectImpl`. |
| `CompatibilidadeJava17.activar()` | nosso | Regista os delegates em `javax.rmi.CORBA.UtilClass` e `javax.rmi.CORBA.StubClass`. É chamado no bloco estático de `FabricaOrb`, antes de qualquer ORB existir. O Naming Service recebe as mesmas propriedades por `-D` no Dockerfile. |

Validação: um teste JUnit com uma chamada IIOP real entre dois ORBs (incluindo uma excepção do IDL), e a comunicação nos dois sentidos entre containers (ver evidência mais abaixo).

#### Porque não Java 8

- **Spring Boot 3 exige Java 17.** Com Java 8 nos módulos CORBA, o `scad-no` (que é servidor CORBA *e* serviço Spring) teria de descer para Spring Boot 2.7, que já não tem suporte, ou de se dividir em dois processos ligados por outro mecanismo. Seria mais complexidade e mais pontos de falha.
- **Duas JVMs no mesmo projecto** (8 e 17) obrigariam a duas toolchains e duas imagens base, e impediriam partilhar o `scad-common`.
- **Âmbito reduzido:** a solução são duas classes pequenas e explícitas, com um propósito bem documentado, enquanto a alternativa obrigaria a mudar toda a arquitectura.
- Os JDK 8 são versões antigas, sem as melhorias de segurança e de contentorização (limites de memória do container, etc.) das versões recentes.

#### Limitação

Os delegates são **mínimos**: suportam o CORBA definido por IDL (structs, sequences, enums, excepções, interfaces, `oneway`) e **não suportam RMI-IIOP** (interfaces `java.rmi.Remote` exportadas por IIOP, valuetypes, `PortableRemoteObject`, `Tie`, cópia de objectos). Essas operações falham explicitamente com `org.omg.CORBA.NO_IMPLEMENT("RMI-IIOP não é suportado no SCAD")`, em vez de terem um comportamento silencioso ou errado. Para o SCAD isto não tem impacto, porque o contrato é exclusivamente o `SCAD.idl`. Se no futuro o IDL passar a usar `valuetype`, será preciso uma implementação completa, por exemplo a `yoko-rmi-impl` ou o GlassFish ORB.

### Evidência de testes (Fase 1)

- **JUnit** `ServidorCorbaTest`: chamada IIOP real entre dois ORBs, com propagação da excepção `ProdutoInexistente`.
- **CORBA entre containers:** os 3 agentes registaram-se no Naming e na Central; a Central fez *callback* (`obterRelogio`, `consultarTodos`) a cada agente; `POST /api/prova/alerta` foi entregue (oneway).
- **Resiliência CORBA:** com a Central parada, os nós passam a `centralDisponivel=false`. Com a Central reiniciada, todos voltam a registar-se em ≤ 10 s. Com um nó reiniciado, regista-se de novo sozinho.
- **HDFS:** 3 DataNodes vivos; os ficheiros escritos por cada nó têm `Live_repl=3` (`hdfs fsck`).
- **Postgres:** `pg_stat_replication` mostra `streaming` nos 3 primários; as réplicas têm `pg_is_in_recovery()=t` e vêem os dados escritos no primário.
- **Memória (modo local, tudo num PC):** cerca de 2,1 GB no total (NameNode 345 MB, cada DataNode 250 MB, cada nó 200 MB, cada Postgres 25–50 MB).

---

## Fase 2 — Dados e fluxo base (saída de stock → alerta CORBA)

### Modelo da base de dados distribuída

```mermaid
erDiagram
    ARMAZEM ||--o{ STOCK : tem
    PRODUTO ||--o{ STOCK : "é guardado em"
    FORNECEDOR ||--o{ PRODUTO : fornece
    STOCK ||--o{ MOVIMENTO_STOCK : regista
    STOCK ||--o{ ALERTA : gera
    ORDEM_COMPRA ||--|{ LINHA_ORDEM : "é repartida em"
    ARMAZEM ||--o{ LINHA_ORDEM : recebe
    FORNECEDOR ||--o{ ORDEM_COMPRA : recebe
    PRODUTO ||--o{ ORDEM_COMPRA : "é encomendado em"
```

| Tabela | Distribuição | Justificação |
|---|---|---|
| `armazem`, `fornecedor`, `produto` | **replicada** (igual em todos os fragmentos) | catálogo pequeno e quase estático; lido em todas as operações locais |
| `stock`, `movimento_stock`, `alerta` | **fragmentação horizontal** por região | cada armazém só altera o seu stock: escritas locais, sem coordenação |
| `linha_ordem` | **fragmentação horizontal** por região | cada linha incrementa o `em_encomenda` *desse* armazém: tem de estar no mesmo fragmento que o seu `stock` |
| `ordem_compra` | cabeçalho em **todos os fragmentos que participam** na ordem | o painel vê a ordem mesmo com um nó em baixo; dá ao 2PC um motivo real para tocar vários fragmentos |

**O predicado de fragmentação é imposto pela própria BD.** `db/init/04-fragmento.sh` adiciona `CHECK (armazem_id = '<REGIÃO>')` a cada tabela fragmentada (teste: `fragmentoRecusaLinhasDeOutraRegiao`). O esquema (`02-esquema.sql`) e o catálogo (`03-catalogo.sql`) são os mesmos em todos os fragmentos; só o script de fragmento e o stock inicial (`db/dados/stock-<região>.sql`) dependem da região (`SCAD_REGIAO`).

Restrições de integridade: `CHECK (quantidade >= 0)`, `em_encomenda >= 0`, `maximo > minimo`, `lote > 0`, e os enumerados (`tipo`, `estado`, `nivel`, `protocolo`) com `CHECK ... IN (...)`.

### Fluxo (passos 1 e 2)

```mermaid
sequenceDiagram
    participant P as Painel
    participant N as Nó (região)
    participant BD as Fragmento local (PostgreSQL)
    participant C as Central de Auditoria (CORBA)
    P->>N: POST /api/movimentos/saida
    N->>BD: BEGIN
    N->>BD: UPDATE stock ... WHERE quantidade >= q RETURNING *
    N->>BD: INSERT movimento_stock (ts_lamport, ts_corrigido)
    N->>BD: INSERT alerta (se o nível o exigir) — outbox
    N->>BD: COMMIT
    N-->>P: 201 (stock após, nível, alerta)
    N->>C: _non_existent() (bidireccional: a Central está viva?)
    N-)C: dispararAlerta (oneway)
    N->>BD: UPDATE alerta SET enviado_em = ...
```

### Decisões

| Decisão | Justificação |
|---|---|
| JDBC explícito (`JdbcTemplate`), sem JPA | O SQL fica visível e controlado. O 2PC (Fase 3) precisa de `PREPARE TRANSACTION` em ligações concretas. |
| `FragmentosBd`: um pool Hikari por fragmento, com `initializationFailTimeout=-1` | O nó arranca mesmo com um fragmento em baixo; cada fragmento tem o seu `TransactionTemplate`. Os fragmentos sem endereço configurado são ignorados. |
| Saída atómica: `UPDATE ... WHERE quantidade >= ? RETURNING *` | A verificação e a actualização são uma só instrução, por isso saídas concorrentes nunca deixam o stock negativo (teste: 20 pedidos concorrentes de 10 sobre 105 → exactamente 10 com sucesso). |
| Níveis: RUPTURA (`q = 0`), CRITICO (`q + enc < min`), AVISO (`q + enc ≤ 1,2 × min`), NORMAL | O AVISO coincide com o "perto do mínimo" da regra de repartição; só o CRITICO activa a reposição (Fase 3). Usa aritmética inteira (`5·disp ≤ 6·min`) para evitar erros de vírgula flutuante. |
| Alertas em **outbox** (tabela `alerta`, gravada na mesma transacção do movimento) | O alerta nunca se perde: se o nó cair depois do commit, o alerta continua na BD e é enviado quando o nó voltar. É também a "fila local" pedida para quando a Central cai. |
| Um estado só gera um alerta (`ultimoAlertaDescreve`) | A verificação periódica (30 s) apanha estados que já existiam no arranque (ex.: FARINHA-25 no SUL), sem repetir alertas. |
| Relógio de Lamport retomado a partir do maior `ts_lamport` gravado | Depois de um reinício, os carimbos do nó nunca recuam. |
| Relógio corrigido = sistema + `CLOCK_SKEW_MS` + ajustes Berkeley | Simula desvios (NORTE +2500 ms, CENTRO −1800 ms, SUL +700 ms) sem mexer no relógio do sistema operativo. |
| Erros HTTP em `problem+json` (RFC 9457) | 404 produto inexistente, 409 stock insuficiente, 422 armazém de outro fragmento, 400 validação, 503 BD indisponível. |
| Estado do HDFS verificado em segundo plano (`MonitorHdfs`) | Com o NameNode inacessível, a verificação demora ~6 s, e o painel faz polling a cada segundo. |

### Problema encontrado: `oneway` + IOR transitório = alertas perdidos em silêncio

Num teste em que a Central foi parada e reiniciada, os alertas ficaram marcados como enviados mas **nunca chegaram**. Causa:
1. O RootPOA é *transient*: o *object key* da Central muda a cada arranque.
2. O nó ainda tinha a referência antiga e enviou o alerta antes de o registo ser refeito.
3. `dispararAlerta` é `oneway`: o servidor não pode responder `OBJECT_NOT_EXIST`, por isso o cliente não recebe erro.

Solução em três camadas:
1. **POA persistente** (`LifespanPolicy PERSISTENT` + `IdAssignmentPolicy USER_ID`), com porta e `jacorb.implname` fixos: os IORs da Central e dos agentes **não mudam entre reinícios**.
2. **`_non_existent()` antes do oneway**: uma operação CORBA padrão com resposta, que confirma que o processo está vivo e tem o objecto activo. Só depois se marca o alerta como enviado.
3. **Deduplicação na Central** por `alertaId`: um reenvio nunca é contado duas vezes (entrega *at-least-once* + idempotência ⇒ efeito *exactly-once*).

Evidência: com a Central parada, ficaram 2 alertas pendentes na fila local. A Central foi reiniciada às 22:55:51.6, e os 2 alertas chegaram às 22:55:52, **antes** de o agente se ter voltado a registar (22:56:01). A referência antiga continuou válida.

### Cenários de demonstração nos dados iniciais

| Produto (lote) | NORTE | CENTRO | SUL | Para que serve |
|---|---|---|---|---|
| ARROZ-25 (500) | 105 / mín 100 | 104 / mín 100 | 300 | **Pico simultâneo**: uma saída de 10 no NORTE e no CENTRO deixa os dois abaixo do mínimo |
| FARINHA-25 (400) | 200 | 90 / mín 80 (perto) | **70 / mín 80** | já abaixo do mínimo no arranque; repartição com prioridade 2 (CENTRO perto do mínimo) |
| ACUCAR-10 (400) | 180 | 160 | 85 / mín 80 (perto) | alerta AVISO no arranque |
| OLEO-5 (600) | 100 / mín 60 | 150 | 140 | saída de 45 no NORTE → prioridade 3 e excesso para o armazém que disparou |
| FEIJAO-10, SABAO-BARRA | normais | | | movimentos sem alerta |

### Evidência de testes (Fase 2)

- `mvn verify`: 27 testes, todos verdes.
  - `RelogioLamportTest` (5), `RelogioCorrigidoTest` (2) e `StockTest` (11 casos de nível, condição de reposição e necessidade).
  - `ServicoMovimentosIT` (8), com **PostgreSQL real via Testcontainers, inicializado com os scripts de produção**: carimbos de Lamport e hora corrigida; alerta CRITICO entregue; fila local com a Central em baixo (reenvio pela ordem de emissão); stock insuficiente sem efeitos; produto inexistente e armazém de outro fragmento rejeitados; predicado de fragmentação imposto pela BD; 20 saídas concorrentes; verificação periódica sem alertas repetidos.
- Stack local: os alertas iniciais esperados chegam à Central (FARINHA-25 CRITICO no SUL e 4 AVISO). A API devolve 201/409/422/400 correctamente, e os acentos (UTF-8) são gravados sem problemas.

### Teste de fumo com 2 máquinas (preparado)

Fica em `deploy/fumo/` (ver o README dessa pasta). Ao validar a parte da M1 num só PC, descobriram-se dois requisitos de ambiente, agora documentados:
- Com o **WSL em modo `mirrored`** (`.wslconfig`), o Docker Desktop não publica as portas na placa de rede: elas só ficam acessíveis em `localhost`. É preciso o modo NAT (o padrão).
- Um **PostgreSQL instalado no Windows** ocupa a porta 5432 em `0.0.0.0` e fica com as ligações ao IP da máquina. Tem de ser parado durante a demonstração.

> Por decisão do grupo (apresentação no dia seguinte), a demonstração corre **num só PC** (`deploy/local`),
> com cada componente num container separado. O deploy em 3 máquinas fica preparado (teste de fumo) mas não foi executado.

---

## Fase 3 — SBDD: ordem consolidada, 2PC, recuperação e failover

### Fluxo completo de uma compra

```mermaid
sequenceDiagram
    participant N as Nó que dispara (ex.: NORTE)
    participant P as Outros nós (RA)
    participant F as Fragmentos (NORTE, CENTRO, SUL)
    participant FO as Fornecedor
    participant H as HDFS
    N->>N: 1-2. saída → nível CRITICO → alerta CORBA (outbox)
    N->>P: 3. PEDIDO(recurso=fornecedor/produto, ts Lamport)
    P-->>N: OK (ou OK adiado)
    N->>F: 4. re-verificar q + enc < min (dentro da SC)
    N->>F: 5. 2PC: PREPARE em cada fragmento → log da decisão → COMMIT PREPARED
    N->>P: 6. sai da SC (envia OKs adiados)
    N->>FO: 7. REST ou SOAP (timeouts, 3 tentativas, Idempotency-Key)
    N->>F: 2PC: estado ENVIADA
    N->>H: 8. factura PDF + JSON do 2PC (via fila local)
```

### Decisões

| Decisão | Justificação |
|---|---|
| Repartição como função pura (`Reparticao.calcular`) | Regra de negócio isolada da infra-estrutura e testada exaustivamente (10 casos JUnit, incluindo arredondamentos e excesso). |
| Ordem = exactamente 1 lote; prioridades P1 (quem disparou), P2 (perto do mínimo, do mais vazio para o menos vazio), P3 (proporcional à necessidade, sem passar do máximo); resto e excesso para quem disparou | Regra acordada com o grupo. "Perto do mínimo" = `q + enc ≤ 1,2 × min`. |
| Coordenador = nó que ganhou a exclusão mútua | Já tem a secção crítica do recurso; não há dois coordenadores para o mesmo lote. |
| 2PC sobre `PREPARE TRANSACTION` do PostgreSQL | O PostgreSQL é o gestor de recursos (participante); o estado preparado sobrevive a reinícios **e é replicado para a réplica** (sobrevive ao failover). |
| Votos em paralelo, com timeout de 5 s; sem resposta = NÃO | O coordenador nunca fica à espera de um participante morto na fase 1. |
| Decisão gravada em `log_2pc` (autocommit) **antes** da fase 2 | É o que permite a recuperação: depois de gravada, a decisão é irrevogável. |
| gid = `coordenador|transacção|participante` | A recuperação sabe de que coordenador é cada transacção preparada. |
| Recuperação a cada 10 s e no arranque: (a) aplica as decisões do log ainda não concluídas; (b) "presumed abort" das preparadas sem decisão com mais de 30 s | Cobre o coordenador que morre entre as fases e o que morre durante a votação. Também cobre participantes temporariamente em baixo na fase 2. |
| Fase 2 idempotente (gid inexistente = já concluída) | A recuperação pode repetir sem efeitos laterais. |
| O mesmo coordenador genérico (`TrabalhoParticipante`) para criar, marcar ENVIADA e receber entrega | As três operações tocam vários fragmentos, e todas são atómicas. |
| Cabeçalho da ordem só nos fragmentos participantes; leitura distribuída junta cabeçalhos e linhas | Acordado na Fase 0; o painel vê a ordem mesmo com um nó em baixo. |
| Leituras globais (stock, movimentos, ordens) feitas directamente aos fragmentos | Funcionam com o **nó** de uma região em baixo, desde que o seu **fragmento** esteja acessível. |
| Failover: o nó da região seguinte no anel (onde estaria a réplica) promove ao fim de 3 falhas (~6 s); os outros esperam mais 2 ciclos e, se a réplica já não estiver em recuperação, adoptam-na | Evita promoções concorrentes (embora `pg_promote` seja idempotente) sem criar um ponto único de falha. |
| Uma réplica promovida é sempre a autoridade; não há regresso automático ao primário antigo | Evita *split-brain* (dois primários com dados divergentes). O primário antigo tem de ser reintegrado manualmente como réplica. |
| Lamport retomado a partir de `max(ts_lamport)` de movimentos, alertas, ordens e log 2PC | Os ids das ordens usam o carimbo: um reinício nunca gera ids repetidos. |

### Evidência
- `DoisFasesIT` (3 PostgreSQL reais): commit cria a ordem em 2 fragmentos e não toca no 3.º; um voto NÃO anula o trabalho já preparado; a recuperação conclui transacções preparadas a partir do log; ciclo criar → ENVIADA (idempotente) → ENTREGUE (e não deixa receber duas vezes).
- Stack: FARINHA-25 do SUL gerou sozinha `OC-SUL-…` com SUL 230 + CENTRO 170 (P2).
- **Coordenador morto entre as fases**: com a decisão COMMIT gravada e transacções preparadas em `pg-norte` e `pg-centro`, parou-se o `no-norte`. Ao reiniciar, a recuperação aplicou o COMMIT (0 preparadas), e a ordem foi enviada pelo reenvio automático.
- **Failover**: `stop pg-centro` → em ~6–10 s o SUL promoveu `pg-centro-replica`, os outros adoptaram-na, e as saídas no CENTRO continuaram a funcionar.

---

## Fase 4 — SOD: Ricart-Agrawala e Berkeley

| Decisão | Justificação |
|---|---|
| Ricart-Agrawala (e não o algoritmo do anel) | Não precisa de token nem de reconstruir o anel quando um nó cai; 2(N−1) mensagens por entrada; usa os carimbos de Lamport que já temos. |
| Exclusão **por recurso** `fornecedor/produto` | Compras de produtos diferentes não se bloqueiam (teste `recursosDiferentesNaoSeBloqueiam`). |
| Prioridade `(ts Lamport, id do nó)` | Ordem total: o desempate pelo id torna o algoritmo determinístico. |
| Resposta ao PEDIDO no próprio pedido HTTP (OK imediato) ou mais tarde (`/interno/ra/ok`) | Metade das mensagens no caso comum. |
| Pedidos sem resposta reenviados a cada 2 s; recepção idempotente; timeout de 30 s | Tolera mensagens perdidas; nunca bloqueia para sempre. |
| Detector de falhas por heartbeats (1 s; morto ao fim de 3 s); um nó morto sai das listas "à espera de OK" e "OK adiado"; um nó que volta entra nos pedidos seguintes | Requisito: "nunca bloquear à espera de um nó morto". Teste: nó que morre **dentro** da secção crítica. |
| Pedidos do mesmo nó para o mesmo recurso serializados localmente antes do RA | O RA trata da concorrência entre nós; um trinco local trata das threads do mesmo nó. |
| Demora de 2 s dentro da secção crítica (configurável) | Só para a demonstração: torna visível a fila e o OK adiado. |
| **Berkeley**: mestre = Central; `obterRelogio` com compensação de RTT/2; média excluindo diferenças a mais de 10 s da mediana; `ajustarRelogio(media − d_i)`; o mestre também se ajusta | Algoritmo clássico; a mediana torna a exclusão de valores absurdos robusta. |
| Relógio do nó = sistema + desvio simulado + ajustes; o relógio do SO nunca é alterado | Os relógios reais quase não se desviam; o desvio simulado (NORTE +2500, CENTRO −1800, SUL +700 ms) torna o algoritmo visível. Se a Central cair, mantém-se o último ajuste. |

Evidência: `RicartAgrawalaTest` (5): 60 entradas concorrentes de 3 nós × 2 threads com no máximo 1 dentro; OK adiado e ordem de entrada; nó morto dentro da SC; timeout. Pico simultâneo na stack: o CENTRO (ts=7) entrou antes do NORTE (ts=8, OK adiado), houve **uma ordem**, e o NORTE na re-verificação não criou nenhuma. Berkeley: os 4 relógios convergiram para +350 ms (a média), e as rondas seguintes corrigem ±1 ms.

---

## Fase 5 — Serviços Web

| Decisão | Justificação |
|---|---|
| Módulo `fornecedor-contrato` com a interface JAX-WS e as mensagens | O mesmo contrato gera o WSDL no servidor e o proxy no cliente, e as mesmas classes servem para JSON (Jackson) e XML (JAXB). |
| Apache CXF (servidor e cliente SOAP) | Publica o WSDL automaticamente e converte `FalhaFornecedor` em SOAP Fault com detalhe estruturado (`codigo`, `temporaria`). |
| Padrão **Adapter**: `ClienteFornecedor` com `ClienteRest` e `ClienteSoap`; escolha pelo campo `fornecedor.protocolo` | O gateway não conhece o protocolo; acrescentar um protocolo é acrescentar uma classe. |
| Timeouts de 2 s (ligação) e 4 s (resposta); 3 tentativas com espera exponencial (0,5 s, 1 s); erros 4xx ou faults não temporários não se repetem | Robustez sem martelar um fornecedor que rejeita os dados. |
| **Idempotência**: `ordemId` como chave (`Idempotency-Key` em REST); o fornecedor guarda a confirmação e devolve-a em reenvios | Um timeout do cliente **não** significa que o fornecedor não recebeu: o reenvio nunca duplica a compra. |
| Envio fora da secção crítica; falha → `PENDENTE_ENVIO`; reenvio a cada 15 s (ordens do próprio coordenador ao fim de 10 s, de qualquer coordenador ao fim de 60 s) | A secção crítica não fica presa à latência do fornecedor; se o coordenador morrer, outro nó envia (a idempotência torna isto seguro). |
| Não enviar se faltarem linhas (fragmento inacessível) | O fornecedor rejeitaria a ordem como permanentemente inválida. |

Evidência: REST (FARINHA) e SOAP (OLEO) chegam a ENVIADA. Com o fornecedor EM_BAIXO: 3 tentativas, a ordem fica PENDENTE e é reenviada quando ele volta. No modo LENTO (8 s): o cliente desistiu por timeout mas o fornecedor aceitou; o reenvio foi devolvido como **duplicado**, com a mesma confirmação → 1 só compra. *Bug encontrado e corrigido*: um timeout de leitura a meio da resposta chegava como `RestClientException` genérica e não era repetido; passou a ser tratado como falha temporária.

---

## Fase 6 — DFS (HDFS)

| Decisão | Justificação |
|---|---|
| HDFS: 1 NameNode + 3 DataNodes, replicação 3 | Um ficheiro sobrevive à perda de 2 DataNodes. **O NameNode é um ponto único de falha** (não há HA nesta configuração). |
| Estrutura: `/scad/facturas/AAAA/MM/OC-xxx.pdf`, `/scad/auditoria/AAAA-MM-DD/`, `/scad/2pc/`, `/scad/logs/erros/AAAA-MM-DD/` | Pedida no enunciado; particionada por data. |
| **Fila local "store and forward"** (`FilaHdfs`): grava primeiro no disco do nó (volume) e depois envia por ordem; o `.caminho` é escrito por último com rename atómico | Nada se perde com o HDFS em baixo, nem se o nó reiniciar; nunca se envia uma entrada incompleta. |
| Factura gerada pelo nó que confirmou o envio (OpenPDF) | Um único emissor por envio; reenvios reescrevem o mesmo caminho (idempotente). |
| Auditoria periódica na Central (`consultarTodos` CORBA a cada 60 s) com os alertas do período | Usa a interface IDL para auditar, como pedido. |
| Cliente HDFS *shaded* com `dfs.client.use.datanode.hostname` e `replace-datanode-on-failure=NEVER` | Ver Fase 1. |

Evidência: factura, JSONs do 2PC e auditoria com réplicas 3/3. Com o NameNode parado, 3 ficheiros ficaram na fila e foram enviados quando ele voltou. Com um DataNode parado, a factura continuou a descarregar-se. Teste `FilaHdfsTest`.

---

## Fase 7 — Painel

| Decisão | Justificação |
|---|---|
| React (Vite) servido por **nginx** com proxy para os 3 nós e o fornecedor, em vez de cada nó servir o painel | Uma só porta (8080) para aceder a partir de outros dispositivos; o painel não depende de nenhum nó em particular. Desvio em relação ao plano inicial, documentado. |
| `resolver` do Docker com variáveis no `proxy_pass` | O nginx resolve os nomes a cada pedido: um nó parado só faz falhar os pedidos a esse nó. |
| Leituras globais "do primeiro nó vivo"; estado por nó pedido a cada nó | Com um nó em baixo, o painel mostra-o a vermelho e continua a mostrar todo o stock e as ordens. |
| Polling a 1–2 s | Suficiente para a demonstração; mais simples do que WebSockets. |
| `/interno/*` não exposto pelo proxy | As mensagens do RA e os heartbeats só circulam entre nós. |
| **v2:** um painel por armazém (portas 8081/8082/8083, região via `/regiao.json`), com cor própria; o Monitor, os Ficheiros e o Fornecedor mostram o sistema inteiro | Cada portátil da demonstração "é" um nó/armazém, mas todos vêem o estado global. As APIs dos nós passaram para 18081–18083. |
| **v2:** gráficos (Recharts): nível de stock com mínimo; evolução do stock reconstruída a partir dos movimentos; relógios antes/depois de Berkeley; 2PC e fila do RA visuais; notificações por diferença entre leituras | Tornam visíveis os algoritmos distribuídos durante a defesa. |
| **v2:** estado actualizado nó a nó; `resolver_timeout 1s` no nginx | Um nó parado (nome que deixa de existir no DNS do Docker) fazia cada pedido demorar ~4 s e atrasava a informação dos nós vivos. |

---

## Testes automáticos (resumo final)

| Módulo | Testes | O que cobrem |
|---|---|---|
| scad-common | 9 | CORBA real em Java 17; Lamport; relógio corrigido; fila HDFS |
| scad-no (unitários) | 26 | níveis de stock; repartição (10 casos); Ricart-Agrawala (5, com falhas) |
| scad-no (integração, Testcontainers) | 12 | fluxo base com PostgreSQL real; 2PC com 3 PostgreSQL (commit, abort, recuperação, ciclo completo) |
| scad-central | 1 | mediana do Berkeley |
| fornecedor-mock | 3 | idempotência, validação, modos de falha |
| **Total** | **51** | `mvn verify` (ver README) |

## Limitações conhecidas (para a secção de discussão)
- Teste com 3 máquinas físicas não executado (ver `deploy/fumo`); em Docker Desktop o NameNode vê os DataNodes remotos pelo IP do gateway do Docker, o que pode exigir portas de DataNode diferentes por máquina.
- O NameNode é um ponto único de falha; o primário antigo não é reintegrado automaticamente depois de um failover.
- O detector de falhas assume um limite de 3 s (sistema síncrono): um nó muito lento é tratado como morto.
- O fornecedor-mock guarda as ordens em memória.
