import React, { useState } from 'react';
import { REGIOES, deQualquerNo, paraNo } from '../api.js';
import { usePolling } from '../usePolling.js';

const COR = { NORMAL: 'verde', AVISO: 'amarelo', CRITICO: 'vermelho', RUPTURA: 'vermelho' };

/** Barra de nível: quantidade (cor do nível) + em encomenda (tracejado) + marca do mínimo. */
function Barra({ s }) {
  const escala = Math.max(s.maximo, s.quantidade + s.emEncomenda);
  const pct = (v) => `${Math.min(100, (v / escala) * 100)}%`;
  return (
    <div className="barra" title={`quantidade ${s.quantidade} · em encomenda ${s.emEncomenda} · mínimo ${s.minimo} · máximo ${s.maximo}`}>
      <div className={`cheio ${COR[s.nivel]}`} style={{ width: pct(s.quantidade) }} />
      <div className="encomenda" style={{ left: pct(s.quantidade), width: pct(s.emEncomenda) }} />
      <div className="minimo" style={{ left: pct(s.minimo) }} />
    </div>
  );
}

function Celula({ s }) {
  if (!s) return <td className="suave">fragmento indisponível</td>;
  return (
    <td>
      <Barra s={s} />
      <div className="linha" style={{ justifyContent: 'space-between', marginTop: 3 }}>
        <span className="mono">
          <b>{s.quantidade}</b> / mín {s.minimo}
          {s.emEncomenda > 0 && <span style={{ color: '#1f4e79' }}> (+{s.emEncomenda} enc.)</span>}
        </span>
        <span className={`etiqueta ${s.nivel}`}>{s.nivel}</span>
      </div>
    </td>
  );
}

export default function EcraStock({ vivos }) {
  const [dados, erro] = usePolling(() => deQualquerNo('/global/stock', vivos), 1500, [vivos.join()]);
  const [form, setForm] = useState({ armazemId: 'NORTE', produtoId: 'ARROZ-25', quantidade: 10, motivo: 'Venda' });
  const [msg, setMsg] = useState(null);
  const [ocupado, setOcupado] = useState(false);

  const stock = (dados && dados.stock) || [];
  const produtos = [...new Map(stock.map((s) => [s.produtoId, s])).values()];
  const celula = (produto, armazem) => stock.find((s) => s.produtoId === produto && s.armazemId === armazem);

  const registar = async (e) => {
    e.preventDefault();
    setOcupado(true);
    try {
      const r = await paraNo(form.armazemId, '/movimentos/saida', { ...form, quantidade: Number(form.quantidade) });
      setMsg({ ok: true, texto: `Saída registada no ${form.armazemId}: ${form.produtoId} fica com ${r.stockApos.quantidade} `
        + `(${r.nivel})${r.precisaReposicao ? ' → abaixo do mínimo: alerta CORBA + pedido de reposição' : ''}` });
    } catch (err) {
      setMsg({ ok: false, texto: `Erro no nó ${form.armazemId}: ${err.message}` });
    } finally {
      setOcupado(false);
    }
  };

  const pico = async () => {
    setOcupado(true);
    const corpo = (armazemId) => ({ armazemId, produtoId: 'ARROZ-25', quantidade: 10, motivo: 'Pico simultâneo' });
    const res = await Promise.allSettled([paraNo('NORTE', '/movimentos/saida', corpo('NORTE')),
                                          paraNo('CENTRO', '/movimentos/saida', corpo('CENTRO'))]);
    const txt = res.map((r, i) => `${['NORTE', 'CENTRO'][i]}: ${r.status === 'fulfilled'
      ? `${r.value.stockApos.quantidade} (${r.value.nivel})` : r.reason.message}`).join(' · ');
    setMsg({ ok: res.every((r) => r.status === 'fulfilled'),
      texto: `Pico simultâneo de ARROZ-25 (10 no NORTE e 10 no CENTRO ao mesmo tempo) → ${txt}. `
        + 'Veja no Monitor a exclusão mútua (Ricart-Agrawala): só deve sair UMA ordem.' });
    setOcupado(false);
  };

  return (
    <>
      <div className="cartao">
        <h2>Registar saída de stock</h2>
        <form className="linha" onSubmit={registar}>
          <label>Armazém
            <select value={form.armazemId} onChange={(e) => setForm({ ...form, armazemId: e.target.value })}>
              {REGIOES.map((r) => <option key={r}>{r}</option>)}
            </select>
          </label>
          <label>Produto
            <select value={form.produtoId} onChange={(e) => setForm({ ...form, produtoId: e.target.value })}>
              {produtos.map((p) => <option key={p.produtoId} value={p.produtoId}>{p.produtoNome}</option>)}
            </select>
          </label>
          <label>Quantidade
            <input type="number" min="1" style={{ width: 100 }} value={form.quantidade}
                   onChange={(e) => setForm({ ...form, quantidade: e.target.value })} />
          </label>
          <label>Motivo
            <input value={form.motivo} onChange={(e) => setForm({ ...form, motivo: e.target.value })} />
          </label>
          <button className="botao grande" disabled={ocupado}>Registar saída</button>
          <button type="button" className="botao grande perigo" disabled={ocupado} onClick={pico}>
            ⚡ Pico simultâneo (NORTE + CENTRO)
          </button>
        </form>
        {msg && <div className={`mensagem ${msg.ok ? 'sucesso' : 'erro'}`}>{msg.texto}</div>}
      </div>

      <div className="cartao">
        <h2>Stock por armazém</h2>
        {erro && !dados && <div className="mensagem erro">{erro}</div>}
        {dados && dados.fragmentosIndisponiveis.length > 0 && (
          <div className="mensagem erro">Fragmentos inacessíveis: {dados.fragmentosIndisponiveis.join(', ')}</div>
        )}
        <table>
          <thead>
            <tr>
              <th>Produto</th><th>Fornecedor</th><th className="num">Lote</th>
              {REGIOES.map((r) => <th key={r}>{r}</th>)}
            </tr>
          </thead>
          <tbody>
            {produtos.map((p) => (
              <tr key={p.produtoId}>
                <td><b>{p.produtoNome}</b><div className="suave mono">{p.produtoId}</div></td>
                <td>{p.fornecedorNome}<div className="suave">{p.protocolo}</div></td>
                <td className="num">{p.lote}</td>
                {REGIOES.map((r) => <Celula key={r} s={celula(p.produtoId, r)} />)}
              </tr>
            ))}
          </tbody>
        </table>
        <p className="suave">Barra: cor = nível actual · tracejado azul = em encomenda · traço preto = mínimo.
          Reposição quando quantidade + em encomenda &lt; mínimo.</p>
      </div>
    </>
  );
}
