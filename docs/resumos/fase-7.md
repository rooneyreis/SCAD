# Fase 7 — Painel React

## O que foi feito
- Painel React (Vite) servido por um container **nginx** (`painel`, porta **8080 publicada em 0.0.0.0**). O nginx também faz de proxy:
  `/api/norte/*`, `/api/centro/*`, `/api/sul/*` → nós; `/api/fornecedor/*` → fornecedor-mock.
  - Basta **uma porta** para abrir o painel a partir de outro dispositivo: `http://<IP do PC>:8080`.
  - Se um nó cair, só os pedidos a esse nó falham; o painel continua a funcionar (os nomes são resolvidos a cada pedido).
  - Os endpoints `/interno/*` (heartbeats, Ricart-Agrawala) **não** são expostos.
- **Faixa de estado** (a cada 1 s): nós NORTE/CENTRO/SUL (com o relógio de Lamport), Central CORBA, HDFS, fornecedor (modo) e qual servidor está em uso em cada fragmento (primário ou réplica promovida).
- Ecrãs (o separador fica no endereço, por exemplo `http://localhost:8080/#monitor`):
  1. **Stock**: tabela produtos × armazéns com uma barra de nível (cor = nível, tracejado = em encomenda, traço = mínimo); formulário "Registar saída"; botão **⚡ Pico simultâneo** (10 de ARROZ-25 no NORTE e no CENTRO ao mesmo tempo).
  2. **Ordens**: estado, fornecedor, protocolo, repartição, confirmação, link para o PDF e botão **Receber entrega** (2PC).
  3. **Monitor**: Ricart-Agrawala por nó (membros vivos/mortos, estado por recurso, à espera de OK, OKs adiados, eventos); cronologia dos 2PC (votos, decisão, fase 2); reposições; relógios (desvio, Lamport, última ronda Berkeley antes → depois, botão "Desregular +3 s"); primário/réplica de cada fragmento e eventos de failover.
  4. **Ficheiros (HDFS)**: listagem com réplicas vivas/pedidas, abrir/descarregar, tamanho da fila local.
  5. **Fornecedor**: interruptor Normal / Em baixo / Lento / Com erros, estatísticas (duplicados ignorados = idempotência), eventos do gateway e registo do fornecedor.
  6. **Movimentos**: todos os armazéns, com hora corrigida e carimbo de Lamport.
- `deploy/abrir-porta-painel.ps1`: abre **só** a porta 8080 na firewall (e na firewall Hyper-V, se o WSL estiver em modo mirrored) e mostra o endereço a usar.

## Como testar
```powershell
docker compose -f deploy/local/docker-compose.yml up -d --build
Start-Process http://localhost:8080
# Acesso a partir do telemóvel (PowerShell como Administrador):
powershell -ExecutionPolicy Bypass -File deploy\abrir-porta-painel.ps1
```

## Problemas conhecidos
- **Neste PC, o `.wslconfig` tem `networkingMode=mirrored`.** Neste modo o Docker Desktop pode não publicar as portas na placa de rede, e o painel abre em `localhost` mas não pelo IP. O script avisa. Se o telemóvel não abrir o painel, comentar essa linha, executar `wsl --shutdown` e reiniciar o Docker Desktop (ver o guião da demo).
- A rede Wi-Fi não pode isolar os clientes (alguns hotspots e redes de convidados fazem-no).
