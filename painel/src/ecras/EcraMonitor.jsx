import React, { useState } from 'react';
import { ArrowRight, CheckCircle2, Clock, Database, GitCommitHorizontal, Hourglass, KeyRound, Lock,
  ServerCrash, Timer, XCircle } from 'lucide-react';
import { REGIOES, deTodosOsNos, paraNo } from '../api.js';
import { usePolling } from '../usePolling.js';
import { hora } from '../tema.js';
import { GraficoRelogios } from '../componentes/Graficos.jsx';

// "2026-11-04T00:16:34.123Z  texto" -> "02:16:34  texto" (hora local)
const curto = (linha) => {
  const m = linha.match(/^(\S+Z)\s+(.*)$/s);
  return m ? `${hora(m[1])}  ${m[2]}` : linha;
};

/** Fila da exclusão mútua: para cada recurso, quem está DENTRO, quem está À ESPERA e os OKs adiados. */
function FilaExclusao({ dados }) {
  const recursos = new Set();
  REGIOES.forEach((r) => dados && dados[r] && dados[r].recursos.forEach((x) => recursos.add(x.recurso)));
  const estadoDe = (r, recurso) => dados && dados[r] && dados[r].recursos.find((x) => x.recurso === recurso);
  const activos = [...recursos].filter((rec) => REGIOES.some((r) => {
    const e = estadoDe(r, rec);
    return e && (e.estado !== 'LIVRE' || e.okAdiadosPara.length);
  }));
  const eventos = REGIOES.flatMap((r) => ((dados && dados[r] && dados[r].eventos) || []).map((e) => ({ r, e })))
    .sort((a, b) => b.e.localeCompare(a.e)).slice(0, 14);
  const membros = REGIOES.map((r) => dados && dados[r] && dados[r].membros).find(Boolean) || {};

  return (
    <div className="cartao">
      <h2><Lock size={20} />Exclusão mútua — Ricart-Agrawala
        <span className="extra">um recurso por fornecedor/produto · detector de falhas:{' '}
          {REGIOES.map((r) => <span key={r} className={`etiqueta ${dados && dados[r] ? 'VIVO' : 'MORTO'}`} style={{ marginLeft: 4 }}>
            {r}</span>)}</span>
      </h2>
      {activos.length === 0 && <p className="suave">Nenhum nó dentro ou à espera da secção crítica. (Experimente o ⚡ Pico simultâneo.)</p>}
      {activos.map((rec) => (
        <div key={rec} className="recurso">
          <div className="nome">{rec}</div>
          <div className="nos-ra">
            {REGIOES.map((r) => {
              const e = estadoDe(r, rec);
              if (!dados || !dados[r]) {
                return <div key={r} className="no-ra MORTO"><div className="cab"><ServerCrash size={18} />{r}</div><div className="det">nó em baixo</div></div>;
              }
              const est = e ? e.estado : 'LIVRE';
              return (
                <div key={r} className={`no-ra ${est}`}>
                  <div className="cab">
                    {est === 'DENTRO' ? <KeyRound size={18} /> : est === 'PEDINDO' ? <Hourglass size={18} /> : <CheckCircle2 size={18} />}
                    {r} <span className={`etiqueta ${est}`}>{est === 'DENTRO' ? 'NA SECÇÃO CRÍTICA' : est === 'PEDINDO' ? 'À ESPERA' : 'LIVRE'}</span>
                  </div>
                  {e && est !== 'LIVRE' && <div className="det">carimbo Lamport ts={e.meuTs}</div>}
                  {e && e.aguardaOkDe.length > 0 && <div className="det">aguarda OK de <b>{e.aguardaOkDe.join(', ')}</b></div>}
                  {e && e.okAdiadosPara.length > 0 && <div className="det">OK adiado a <b>{e.okAdiadosPara.join(', ')}</b></div>}
                </div>);
            })}
          </div>
        </div>
      ))}
      <h3>Mensagens recentes {Object.keys(membros).length > 0 && <span className="suave">(L = relógio de Lamport)</span>}</h3>
      <pre className="eventos" style={{ maxHeight: 200 }}>{eventos.map(({ r, e }) => `[${r}] ${curto(e)}`).join('\n') || 'sem mensagens'}</pre>
    </div>
  );
}

const ICONE_VOTO = { SIM: CheckCircle2, NAO: XCircle, SEM_RESPOSTA: Timer };

/** Cronologia visual de cada 2PC: início → votos de cada participante → decisão → fase 2. */
function Cronologia2PC({ dados }) {
  const todas = REGIOES.flatMap((r) => (dados && dados[r]) || [])
    .sort((a, b) => (b.inicio || '').localeCompare(a.inicio || '')).slice(0, 7);
  return (
    <div className="cartao">
      <h2><GitCommitHorizontal size={20} />Two-Phase Commit — cronologia<span className="extra">fase 1: votos · decisão (gravada no log) · fase 2</span></h2>
      {todas.length === 0 && <p className="suave">Ainda não houve transacções distribuídas.</p>}
      {todas.map((t) => (
        <div key={t.transacaoId + t.inicio} className="tx" title={t.eventos.map(curto).join('\n')}>
          <div><div className="mono"><b>{hora(t.inicio)}</b></div><div className="quando">coord. {t.coordenador}</div></div>
          <div>
            <div className="mono" style={{ marginBottom: 6 }}><b>{t.transacaoId}</b> <span className="suave">{t.tipo}</span></div>
            <div className="fluxo">
              <span className="passo"><Database size={15} />PREPARE</span>
              {Object.entries(t.votos).map(([p, v]) => {
                const I = ICONE_VOTO[v] || Clock;
                return <span key={p} className={`passo ${v === 'SIM' ? 'sim' : 'nao'}`} title={t.motivosNao[p] || ''}><I size={15} />{p}: {v === 'SEM_RESPOSTA' ? 'sem resposta' : v}</span>;
              })}
              <ArrowRight size={18} className="seta" />
              {t.decisao ? <span className={`passo ${t.decisao === 'COMMIT' ? 'commit' : 'abort'}`}>{t.decisao}</span>
                : <span className="passo pendente"><Hourglass size={15} />a votar…</span>}
              <ArrowRight size={18} className="seta" />
              {t.participantes.map((p) => {
                const ok = t.fase2Concluida[p];
                return <span key={p} className={`passo ${ok ? 'sim' : 'pendente'}`}>{ok ? <CheckCircle2 size={15} /> : <Clock size={15} />}{p}</span>;
              })}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

function Relogios({ dados, estados }) {
  const [msg, setMsg] = useState(null);
  const perturbar = async (r) => {
    try {
      const a = await paraNo(r, '/relogio/perturbar?ms=3000');
      setMsg(`Relógio do ${r}: ${a.antesMs} → ${a.depoisMs} ms. A próxima ronda de Berkeley (≤ 20 s) corrige.`);
    } catch (e) {
      setMsg(`Erro: ${e.message}`);
    }
  };
  // Última ronda "visível" de cada nó (ignora ajustes de ±5 ms depois de convergir)
  const grafico = REGIOES.filter((r) => dados && dados[r]).map((r) => {
    const h = dados[r].historico.filter((a) => a.origem === 'Berkeley');
    const a = h.find((x) => Math.abs(x.deltaMs) > 5) || h[0];
    return { no: r, antes: a ? a.antesMs : dados[r].desvioEfectivoMs, depois: a ? a.depoisMs : dados[r].desvioEfectivoMs };
  });
  return (
    <div className="cartao">
      <h2><Clock size={20} />Relógios — Berkeley<span className="extra">mestre: Central CORBA · ronda a cada 20 s</span></h2>
      <GraficoRelogios dados={grafico} />
      <table>
        <thead><tr><th>Nó</th><th>Hora corrigida</th><th className="num">Desvio actual</th><th className="num">Lamport</th><th /></tr></thead>
        <tbody>{REGIOES.map((r) => {
          const d = dados && dados[r];
          if (!d) return <tr key={r}><td><b>{r}</b></td><td colSpan="4"><span className="etiqueta MORTO">EM BAIXO</span></td></tr>;
          return (
            <tr key={r}>
              <td><b>{r}</b></td>
              <td className="mono">{estados && estados[r] ? hora(estados[r].horaCorrigida) : ''}</td>
              <td className="num mono">{d.desvioEfectivoMs > 0 ? '+' : ''}{d.desvioEfectivoMs} ms</td>
              <td className="num mono">{d.lamport}</td>
              <td><button className="botao secundario" onClick={() => perturbar(r)}>Desregular +3 s</button></td>
            </tr>);
        })}</tbody>
      </table>
      {msg && <div className="mensagem sucesso">{msg}</div>}
      <p className="suave">Desvios simulados iniciais: NORTE +2500, CENTRO −1800, SUL +700 ms (o relógio do sistema nunca é alterado).</p>
    </div>
  );
}

function Fragmentos({ fragmentos }) {
  const f = (fragmentos && fragmentos.fragmentos) || [];
  const eventos = (fragmentos && fragmentos.eventos) || [];
  return (
    <div className="cartao">
      <h2><Database size={20} />Base de dados distribuída<span className="extra">primário / réplica de cada fragmento</span></h2>
      <table>
        <thead><tr><th>Fragmento</th><th>Em uso</th><th>Endereço activo</th><th>Réplica</th></tr></thead>
        <tbody>{f.map((x) => (
          <tr key={x.regiao} className={x.emUso === 'REPLICA' ? 'destaque' : ''}>
            <td><b>{x.regiao}</b></td>
            <td><span className={`etiqueta ${x.emUso}`}>{x.emUso === 'REPLICA' ? 'RÉPLICA PROMOVIDA' : 'PRIMÁRIO'}</span></td>
            <td className="mono">{x.enderecoActivo}</td><td className="mono">{x.replica}</td>
          </tr>))}</tbody>
      </table>
      <h3>Eventos de failover</h3>
      <pre className="eventos">{eventos.length ? eventos.map((e) => `${hora(e.hora)}  [${e.regiao}] ${e.descricao}`).join('\n') : 'nenhum'}</pre>
    </div>
  );
}

export default function EcraMonitor({ fragmentos, estados }) {
  const [exclusao] = usePolling(() => deTodosOsNos('/exclusao'), 1000);
  const [transaccoes] = usePolling(() => deTodosOsNos('/2pc'), 1500);
  const [relogios] = usePolling(() => deTodosOsNos('/relogio'), 2000);
  const [reposicoes] = usePolling(() => deTodosOsNos('/reposicao/eventos'), 1500);
  return (
    <>
      <FilaExclusao dados={exclusao} />
      <Cronologia2PC dados={transaccoes} />
      <div className="grelha">
        <Relogios dados={relogios} estados={estados} />
        <Fragmentos fragmentos={fragmentos} />
      </div>
      <div className="cartao">
        <h2>Reposições (fluxo de compra em cada nó)</h2>
        <div className="grelha">
          {REGIOES.map((r) => (
            <div key={r}><h3>Nó {r}</h3>
              <pre className="eventos" style={{ maxHeight: 200 }}>
                {reposicoes && reposicoes[r] ? reposicoes[r].slice(0, 12).map(curto).join('\n') || 'sem reposições' : 'nó em baixo'}
              </pre>
            </div>))}
        </div>
      </div>
    </>
  );
}
