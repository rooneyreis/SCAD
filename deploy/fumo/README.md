# Teste de fumo com 2 máquinas (M1 + M2)

Objectivo: validar **cedo**, entre PCs reais, as três coisas que não se conseguem provar num só PC:

| # | O que se valida | Como |
|---|---|---|
| 1 | CORBA com `ior_proxy_host` = IP real | o agente da M2 regista-se na Central da M1, e a Central chama-o de volta pelo IP da M2 |
| 2 | HDFS com um DataNode noutra máquina | NameNode na M1 com 2 DataNodes vivos (M1 e M2); ficheiros com 2 réplicas |
| 3 | Replicação PostgreSQL entre PCs | primário NORTE (M1) em streaming para a réplica (M2) |

| Máquina | Serviços |
|---|---|
| **M1** (NORTE) | pg-norte (primário), NameNode + DataNode, Naming Service, Central de Auditoria, no-NORTE |
| **M2** (CENTRO) | pg-centro (primário), pg-norte-replica, DataNode, no-CENTRO |

---

## 0. Pré-requisitos (nas DUAS máquinas)

1. **Mesma rede.** Ligar as duas ao mesmo router ou hotspot e anotar o IP de cada uma com
   `ipconfig` (o "Endereço IPv4" do Wi-Fi ou da Ethernet). Confirmar que se vêem: `ping <IP da outra>`.
   Alguns hotspots isolam os clientes uns dos outros; se o ping falhar, usar outro router.
2. **Docker Desktop a correr**, com o motor Linux (WSL 2).
3. **WSL em modo NAT (o padrão)**. Abrir `%USERPROFILE%\.wslconfig`. Se existir a linha
   `networkingMode=mirrored`, comentá-la (`# networkingMode=mirrored`), executar `wsl --shutdown`
   e reiniciar o Docker Desktop.
   *Porquê:* em modo `mirrored`, o Docker Desktop **não publica as portas na placa de rede do PC**
   (ficam só acessíveis em `localhost`), e as outras máquinas não lhes conseguem chegar.
4. **Porta 5432 livre.** Se houver um PostgreSQL instalado no Windows (serviço `postgresql-x64-NN`),
   pará-lo durante os testes. Numa PowerShell **como Administrador**:
   ```powershell
   Get-Service postgresql* | Stop-Service
   ```
   Para voltar a ligá-lo depois: `Get-Service postgresql* | Start-Service`.
5. **Firewall.** Numa PowerShell **como Administrador**, na raiz do repositório:
   ```powershell
   powershell -ExecutionPolicy Bypass -File deploy\abrir-portas.ps1 -Maquina M1   # na M1
   powershell -ExecutionPolicy Bypass -File deploy\abrir-portas.ps1 -Maquina M2   # na M2
   ```
6. **Código igual nas duas:** `git clone https://github.com/rooneyreis/SCAD.git` (ou `git pull`).

## 1. Configurar o `.env` (nas DUAS máquinas)

Na M1:
```powershell
cd SCAD\deploy\fumo\m1
copy .env.exemplo .env
notepad .env        # HOST_IP = IP da M1, NORTE_IP = IP da M1, CENTRO_IP = IP da M2
```
Na M2:
```powershell
cd SCAD\deploy\fumo\m2
copy .env.exemplo .env
notepad .env        # HOST_IP = IP da M2, NORTE_IP = IP da M1, CENTRO_IP = IP da M2
```
`PG_PASSWORD` e `REPLICACAO_PASSWORD` têm de ser iguais nas duas.

## 2. Arrancar (primeiro a M1, depois a M2)

**M1** (na pasta `deploy\fumo\m1`). A primeira vez demora alguns minutos, porque compila o projecto e descarrega as imagens:
```powershell
docker compose up -d --build
docker compose ps          # esperar que tudo esteja "Up"
```
**M2** (na pasta `deploy\fumo\m2`), depois de a M1 estar a correr:
```powershell
docker compose up -d --build
docker compose logs -f pg-norte-replica     # deve terminar com "database system is ready to accept read-only connections"; Ctrl+C para sair
```

## 3. Verificar (na M1, ~1 minuto depois de a M2 arrancar)

```powershell
cd deploy\fumo\m1
powershell -ExecutionPolicy Bypass -File ..\verificar-fumo.ps1
```
O script testa HTTP entre as máquinas, CORBA nos dois sentidos, alerta CORBA da M2 para a M1,
escrita no HDFS a partir das duas máquinas com 2 réplicas, e replicação Postgres M1 → M2.
Deve terminar com **`TESTE DE FUMO: TUDO OK`**.

Verificações visuais (opcional):
- UI do HDFS: `http://<IP da M1>:9870` → *Datanodes*: 2 nós "In service".
- `http://<IP da M2>:8080/api/estado` aberto no browser da M1.

**Envia-me o resultado completo do script**, e também a linha `Name:` de cada DataNode, que o script mostra no ponto 4.
Essa linha diz-nos que IP o NameNode vê para o DataNode da M2. Se for um IP interno do Docker
(ex.: `192.168.65.x` ou `172.x`) em vez do IP da M2, então com 3 máquinas os DataNodes da M2 e da M3
iam ter o mesmo IP e colidir. Nesse caso, na Fase 8 daremos portas diferentes ao DataNode de cada máquina.

## 4. Se algo falhar

| Sintoma | Provável causa / o que fazer |
|---|---|
| `no-X responde` falha só para a outra máquina | firewall (passo 0.5), rede com isolamento de clientes, ou `.wslconfig` em `mirrored` |
| Falha até para o IP da própria máquina | `.wslconfig` em `mirrored` (passo 0.3) |
| `pg-norte` não arranca ("port is already allocated") | PostgreSQL do Windows na porta 5432 (passo 0.4) |
| Réplica fica em "À espera do primário" | M1 não alcançável na porta 5432: firewall da M1 |
| `Live datanodes (1)` | DataNode da M2 não chega a `NORTE_IP:8020`: firewall da M1; ver `docker compose logs datanode` na M2 |
| Central não faz callback ao CENTRO | firewall da M2 na porta 3701, ou `HOST_IP` errado no `.env` da M2 |

Logs úteis: `docker compose logs --tail 50 <serviço>`.

## 5. Desligar

```powershell
docker compose down        # mantém os dados
docker compose down -v     # apaga também os dados (volta ao estado inicial)
```
