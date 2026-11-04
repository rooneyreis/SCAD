import React, { useState } from 'react';
import { BarChart3, LineChart as IconeLinha, PackageMinus, Table2 } from 'lucide-react';
import { REGIOES, paraNo } from '../api.js';
import { COR_NIVEL } from '../tema.js';
import { GraficoEvolucao, GraficoNivelStock } from '../componentes/Graficos.jsx';

/** Barra horizontal compacta: quantidade + em encomenda + traço do mínimo. */
function Barra({ s }) {
  const escala = Math.max(s.maximo, s.quantidade + s.emEncomenda);
  const pct = (v) => `${Math.min(100, (v / escala) * 100)}%`;
  return (
    <div style={{ position: 'relative', height: 12, background: '#2a2f3a', borderRadius: 6, overflow: 'hidden', minWidth: 160 }}>
      <div style={{ position: 'absolute', inset: 0, width: pct(s.quantidade), background: COR_NIVEL[s.nivel], borderRadius: 6 }} />
      <div style={{ position: 'absolute', top: 0, bottom: 0, left: pct(s.quantidade), width: pct(s.emEncomenda), background: 'var(--destaque)', opacity: .55 }} />
      <div style={{ position: 'absolute', top: -2, bottom: -2, left: pct(s.minimo), width: 2, background: '#e8eaee' }} />
    </div>
  );
}

export default function EcraArmazem({ regiao, vivos, stock, movimentos }) {
  // Painel de um armazém: mostra o seu; vista geral: escolhe-se o armazém
  const [escolhido, setEscolhido] = useState('NORTE');
  const armazem = regiao || escolhido;
  const linhas = ((stock && stock.stock) || []).filter((l) => l.armazemId === armazem);
  const indisponivel = stock && stock.fragmentosIndisponiveis.includes(armazem);

  const [produto, setProduto] = useState('ARROZ-25');
  const linhaProduto = linhas.find((l) => l.produtoId === produto);
  const movsProduto = (movimentos || []).filter((m) => m.armazemId === armazem && m.produtoId === produto);

  const [form, setForm] = useState({ produtoId: 'ARROZ-25', quantidade: 10, motivo: 'Venda' });
  const [msg, setMsg] = useState(null);
  const [ocupado, setOcupado] = useState(false);

  const registar = async (e) => {
    e.preventDefault();
    setOcupado(true);
    try {
      const r = await paraNo(armazem, '/movimentos/saida',
        { ...form, armazemId: armazem, quantidade: Number(form.quantidade) });
      setMsg({ ok: true, texto: `Saída registada: ${form.produtoId} fica com ${r.stockApos.quantidade} (${r.nivel})`
        + (r.precisaReposicao ? ' → abaixo do mínimo: alerta CORBA à Central + pedido de reposição' : '') });
    } catch (err) {
      setMsg({ ok: false, texto: !vivos.includes(armazem)
        ? `O nó ${armazem} está em baixo: não é possível registar saídas neste armazém.`
        : `Erro no nó ${armazem}: ${err.message}` });
    } finally {
      setOcupado(false);
    }
  };

  return (
    <>
      <div className="cartao">
        <h2><PackageMinus size={20} />Registar saída de stock — {armazem}
          {!regiao && (
            <span className="extra">
              <select value={escolhido} onChange={(e) => setEscolhido(e.target.value)}>
                {REGIOES.map((r) => <option key={r}>{r}</option>)}
              </select>
            </span>)}
        </h2>
        <form className="linha" onSubmit={registar}>
          <label>Produto
            <select value={form.produtoId} onChange={(e) => setForm({ ...form, produtoId: e.target.value })}>
              {linhas.map((p) => <option key={p.produtoId} value={p.produtoId}>{p.produtoNome}</option>)}
            </select>
          </label>
          <label>Quantidade
            <input type="number" min="1" style={{ width: 110 }} value={form.quantidade}
                   onChange={(e) => setForm({ ...form, quantidade: e.target.value })} />
          </label>
          <label>Motivo
            <input value={form.motivo} onChange={(e) => setForm({ ...form, motivo: e.target.value })} />
          </label>
          <button className="botao grande" disabled={ocupado}><PackageMinus size={18} />Registar saída</button>
        </form>
        {msg && <div className={`mensagem ${msg.ok ? 'sucesso' : 'erro'}`}>{msg.texto}</div>}
      </div>

      {indisponivel && <div className="mensagem erro">O fragmento {armazem} da base de dados está inacessível.</div>}

      <div className="grelha-2">
        <div className="cartao">
          <h2><BarChart3 size={20} />Nível de stock por produto — {armazem}
            <span className="extra">verde normal · amarelo perto do mínimo · vermelho abaixo</span></h2>
          <GraficoNivelStock linhas={linhas} />
        </div>
        <div className="cartao">
          <h2><IconeLinha size={20} />Evolução do stock
            <span className="extra">
              <select value={produto} onChange={(e) => setProduto(e.target.value)}>
                {linhas.map((p) => <option key={p.produtoId} value={p.produtoId}>{p.produtoNome}</option>)}
              </select>
            </span>
          </h2>
          {linhaProduto && <GraficoEvolucao movimentos={movsProduto} actual={linhaProduto.quantidade}
                                            minimo={linhaProduto.minimo} maximo={linhaProduto.maximo} />}
          <p className="suave">Saídas descem, entregas sobem: o "dente de serra" em torno do mínimo (tracejado vermelho).</p>
        </div>
      </div>

      <div className="cartao">
        <h2><Table2 size={20} />Stock do armazém {armazem}</h2>
        <table>
          <thead><tr><th>Produto</th><th>Fornecedor</th><th className="num">Quantidade</th><th className="num">Mínimo</th>
            <th className="num">Máximo</th><th className="num">Em encomenda</th><th>Nível</th><th /></tr></thead>
          <tbody>
            {linhas.map((s) => (
              <tr key={s.produtoId}>
                <td><b>{s.produtoNome}</b><div className="suave mono">{s.produtoId}</div></td>
                <td>{s.fornecedorNome}<div className="suave">{s.protocolo} · lote {s.lote}</div></td>
                <td className="num"><b>{s.quantidade}</b></td>
                <td className="num">{s.minimo}</td>
                <td className="num">{s.maximo}</td>
                <td className="num">{s.emEncomenda || '—'}</td>
                <td style={{ width: 200 }}><Barra s={s} /></td>
                <td><span className={`etiqueta ${s.nivel}`}>{s.nivel}</span></td>
              </tr>))}
          </tbody>
        </table>
      </div>
    </>
  );
}
