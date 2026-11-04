# Painel v2 — um painel por armazém, acesso pelo IP, gráficos

## O que mudou
- **Um painel por armazém, cada um na sua porta** (o mesmo nginx, 4 portas):
  `:8081` NORTE (azul-petróleo) · `:8082` CENTRO (violeta) · `:8083` SUL (laranja) · `:8080` vista geral.
  O painel sabe qual é o "seu" armazém através de `/regiao.json`, servido por cada porta.
- As **APIs dos nós** passaram para `18081 / 18082 / 18083` (os comandos do guião e dos resumos foram actualizados).
- **UI nova** (React + Recharts + ícones lucide): cabeçalho com o nome e a cor do armazém, mais o estado de todo o sistema;
  cartões de resumo (abaixo do mínimo, pendentes, enviadas, nós activos); gráficos de nível de stock (com o mínimo),
  evolução do stock (dente de serra, reconstruída a partir dos movimentos) e relógios antes/depois do Berkeley;
  cronologia visual de cada 2PC; fila da exclusão mútua (quem está dentro, quem espera, OKs adiados);
  notificações (alerta de stock, ordem criada/enviada/entregue, nó caiu/voltou, réplica promovida).
- **Nó do painel em baixo**: faixa vermelha "Nó X em baixo"; os dados continuam a vir dos outros nós e do fragmento.
  O estado de cada nó é actualizado nó a nó, por isso um nó em baixo não atrasa os outros. O nginx falha em 1 s
  (`resolver_timeout`) para um contentor parado.
- **Rede**: o `%USERPROFILE%\.wslconfig` passou de `mirrored` para NAT (cópia em `.wslconfig.bak`), e com isso os
  painéis respondem pelo IP do PC. O script `deploy/abrir-porta-painel.ps1` abre as portas 8080–8083 (tem de ser corrido como Administrador).

## Testado (04/11, 10:00–10:15, IP 192.168.100.23)
- `down -v` + `up` limpo: os 17 containers de pé; `http://192.168.100.23:8081/8082/8083` respondem, cada um com o seu armazém.
- Pico simultâneo: o Monitor mostra o NORTE na secção crítica e o CENTRO à espera; **1 ordem**.
- `stop no-centro` + `stop pg-centro`: o painel :8082 mostra a faixa "Nó CENTRO em baixo", 2/3 nós, "BD CENTRO RÉPLICA",
  e o stock e os gráficos do CENTRO servidos pela réplica promovida.
- Fornecedor em baixo → ordem PENDENTE_ENVIO → Normal → ENVIADA.

## Reverter
- Painel antigo: `git checkout painel-v1` (tag).
- WSL: `Copy-Item "$env:USERPROFILE\.wslconfig.bak" "$env:USERPROFILE\.wslconfig" -Force; wsl --shutdown` e reiniciar o Docker Desktop.
