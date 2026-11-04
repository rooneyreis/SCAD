import React, { useState } from 'react';
import { REGIOES, deTodosOsNos, paraNo } from '../api.js';
import { usePolling } from '../usePolling.js';

const hora = (iso) => (iso ? new Date(iso).toLocaleTimeString('pt-PT', { hour12: false }) : '');
// "2026-11-04T00:16:34.123Z  texto" -> "02:16:34  texto" (hora local, como no resto do painel)
const curto = (linha) => {
  const m = linha.match(/^(\S+Z)\s+(.*)$/s);
  return m ? `${hora(m[1])}  ${m[2]}` : linha;
};

function ExclusaoMutua({ dados }) {
  return (
    <div className="cartao">
      <h2>Exclusão mútua — Ricart-Agrawala (por recurso fornecedor/produto)</h2>
      <div className="grelha">
        {REGIOES.map((r) => {
          const d = dados && dados[r];
          if (!d) return <div key={r}><h3>Nó {r} <span className="etiqueta MORTO">EM BAIXO</span></h3></div>;
          const activos = d.recursos.filter((x) => x.estado !== 'LIVRE' || x.okAdiadosPara.length);
          return (
            <div key={r}>
              <h3>Nó {r}</h3>
              <div className="linha" style={{ marginBottom: 6 }}>
                {Object.entries(d.membros).map(([m, e]) => (
                  <span key={m} className={`etiqueta ${e}`}>{m}: {e}</span>))}
              </div>
              <table>
                <thead><tr><th>Recurso</th><th>Estado</th><th>À espera de OK</th><th>OK adiado a</th></tr></thead>
                <tbody>
                  {(activos.length ? activos : d.recursos.slice(0, 3)).map((x) => (
                    <tr key={x.recurso} className={x.estado === 'DENTRO' ? 'destaque' : ''}>
                      <td className="mono">{x.recurso}</td>
                      <td><span className={`etiqueta ${x.estado}`}>{x.estado}</span>
                        {x.estado !== 'LIVRE' && <span className="suave"> ts={x.meuTs}</span>}</td>
                      <td>{x.aguardaOkDe.join(', ') || '—'}</td>
                      <td>{x.okAdiadosPara.join(', ') || '—'}</td>
                    </tr>))}
                  {d.recursos.length === 0 && <tr><td colSpan="4" className="suave">sem pedidos ainda</td></tr>}
                </tbody>
              </table>
              <pre className="eventos" style={{ maxHeight: 170 }}>{d.eventos.slice(0, 10).map(curto).join('\n')}</pre>
            </div>);
        })}
      </div>
    </div>
  );
}

function DoisFases({ dados }) {
  const todas = REGIOES.flatMap((r) => (dados && dados[r]) || [])
    .sort((a, b) => (b.inicio || '').localeCompare(a.inicio || '')).slice(0, 8);
  return (
    <div className="cartao">
      <h2>Two-Phase Commit — cronologia (participantes, votos, decisão)</h2>
      {todas.length === 0 && <p className="suave">Ainda não houve transacções distribuídas.</p>}
      <table>
        <thead><tr><th>Hora</th><th>Transacção</th><th>Tipo</th><th>Coord.</th><th>Votos (fase 1)</th>
          <th>Decisão</th><th>Fase 2</th></tr></thead>
        <tbody>
          {todas.map((t) => (
            <tr key={t.transacaoId + t.inicio} title={t.eventos.join('\n')}>
              <td className="mono">{hora(t.inicio)}</td>
              <td className="mono"><b>{t.transacaoId}</b></td>
              <td>{t.tipo}</td>
              <td>{t.coordenador}</td>
              <td>{Object.entries(t.votos).map(([p, v]) => (
                <span key={p} className={`etiqueta ${v}`} style={{ marginRight: 4 }}
                      title={t.motivosNao[p] || ''}>{p}: {v}</span>))}</td>
              <td>{t.decisao ? <span className={`etiqueta ${t.decisao}`}>{t.decisao}</span> : '…'}</td>
              <td>{Object.entries(t.fase2Concluida).map(([p, ok]) => (
                <span key={p} className={`etiqueta ${ok ? 'SIM' : 'AVISO'}`} style={{ marginRight: 4 }}>
                  {p} {ok ? '✓' : 'pendente'}</span>))}</td>
            </tr>))}
        </tbody>
      </table>
      <p className="suave">Passe o rato sobre uma linha para ver a cronologia completa.</p>
    </div>
  );
}

function Relogios({ dados, estados }) {
  const [msg, setMsg] = useState(null);
  const perturbar = async (r, ms) => {
    try {
      const a = await paraNo(r, `/relogio/perturbar?ms=${ms}`);
      setMsg(`Relógio do ${r}: ${a.antesMs} → ${a.depoisMs} ms. A próxima ronda de Berkeley (≤ 20 s) corrige.`);
    } catch (e) {
      setMsg(`Erro: ${e.message}`);
    }
  };
  return (
    <div className="cartao">
      <h2>Relógios — Berkeley (mestre: Central) e Lamport</h2>
      <table>
        <thead><tr><th>Nó</th><th>Hora corrigida</th><th className="num">Desvio actual</th><th className="num">Lamport</th>
          <th>Última ronda Berkeley (antes → depois)</th><th /></tr></thead>
        <tbody>
          {REGIOES.map((r) => {
            const d = dados && dados[r];
            if (!d) return <tr key={r}><td>{r}</td><td colSpan="5"><span className="etiqueta MORTO">EM BAIXO</span></td></tr>;
            const ultimo = d.historico.find((a) => a.origem === 'Berkeley');
            return (
              <tr key={r}>
                <td><b>{r}</b></td>
                <td className="mono">{estados && estados[r] ? hora(estados[r].horaCorrigida) : ''}</td>
                <td className="num mono">{d.desvioEfectivoMs > 0 ? '+' : ''}{d.desvioEfectivoMs} ms</td>
                <td className="num mono">{d.lamport}</td>
                <td className="mono">{ultimo
                  ? `${hora(ultimo.hora)}: ${ultimo.antesMs} → ${ultimo.depoisMs} ms (ajuste ${ultimo.deltaMs > 0 ? '+' : ''}${ultimo.deltaMs})`
                  : 'à espera da 1.ª ronda'}</td>
                <td><button className="botao secundario" onClick={() => perturbar(r, 3000)}>Desregular +3 s</button></td>
              </tr>);
          })}
        </tbody>
      </table>
      {msg && <div className="mensagem sucesso">{msg}</div>}
      <p className="suave">Desvio simulado inicial: NORTE +2500 ms, CENTRO −1800 ms, SUL +700 ms (o relógio do sistema nunca é alterado).</p>
    </div>
  );
}

function Fragmentos({ fragmentos }) {
  const f = (fragmentos && fragmentos.fragmentos) || [];
  const eventos = (fragmentos && fragmentos.eventos) || [];
  return (
    <div className="cartao">
      <h2>Base de dados distribuída — primário / réplica de cada fragmento</h2>
      <table>
        <thead><tr><th>Fragmento</th><th>Em uso</th><th>Endereço activo</th><th>Primário</th><th>Réplica</th></tr></thead>
        <tbody>{f.map((x) => (
          <tr key={x.regiao} className={x.emUso === 'REPLICA' ? 'destaque' : ''}>
            <td><b>{x.regiao}</b></td>
            <td><span className={`etiqueta ${x.emUso}`}>{x.emUso === 'REPLICA' ? 'RÉPLICA PROMOVIDA' : 'PRIMÁRIO'}</span></td>
            <td className="mono">{x.enderecoActivo}</td><td className="mono">{x.primario}</td><td className="mono">{x.replica}</td>
          </tr>))}</tbody>
      </table>
      <h3>Eventos de failover</h3>
      <pre className="eventos">{eventos.length ? eventos.map((e) => `${hora(e.hora)}  [${e.regiao}] ${e.descricao}`).join('\n') : 'nenhum'}</pre>
    </div>
  );
}

function Reposicoes({ dados }) {
  return (
    <div className="cartao">
      <h2>Reposições (fluxo de compra em cada nó)</h2>
      <div className="grelha">
        {REGIOES.map((r) => (
          <div key={r}><h3>Nó {r}</h3>
            <pre className="eventos" style={{ maxHeight: 200 }}>
              {dados && dados[r] ? dados[r].slice(0, 12).map(curto).join('\n') || 'sem reposições' : 'EM BAIXO'}
            </pre>
          </div>))}
      </div>
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
      <ExclusaoMutua dados={exclusao} />
      <DoisFases dados={transaccoes} />
      <Reposicoes dados={reposicoes} />
      <div className="grelha">
        <Relogios dados={relogios} estados={estados} />
        <Fragmentos fragmentos={fragmentos} />
      </div>
    </>
  );
}
