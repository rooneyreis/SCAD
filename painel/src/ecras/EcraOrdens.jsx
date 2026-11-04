import React, { useState } from 'react';
import { FileDown, PackageCheck, Receipt } from 'lucide-react';
import { paraNo, urlFicheiro } from '../api.js';

/** Caminho da factura no HDFS: /scad/facturas/AAAA/MM/OC-xxx.pdf (data UTC da ordem). */
function caminhoFactura(o) {
  const d = new Date(o.tsCorrigido);
  return `/scad/facturas/${d.getUTCFullYear()}/${String(d.getUTCMonth() + 1).padStart(2, '0')}/${o.id}.pdf`;
}

export default function EcraOrdens({ regiao, vivos, ordens, recarregar }) {
  const [todas, setTodas] = useState(!regiao);
  const [msg, setMsg] = useState(null);
  const [aReceber, setAReceber] = useState(null);
  // A entrega é coordenada pelo nó deste painel (se estiver vivo) ou por outro nó vivo
  const no = regiao && vivos.includes(regiao) ? regiao : vivos[0] || 'NORTE';
  const lista = (ordens || []).filter((o) => todas || o.linhas[regiao]);

  const receber = async (o) => {
    setAReceber(o.id);
    try {
      const r = await paraNo(no, `/ordens/${o.id}/entrega`);
      setMsg({ ok: r.decisao2pc === 'COMMIT', texto: `Entrega de ${o.id} recebida: 2PC ${r.decisao2pc} coordenado pelo nó ${no}.` });
      recarregar();
    } catch (e) {
      setMsg({ ok: false, texto: `Erro ao receber ${o.id}: ${e.message}` });
    } finally {
      setAReceber(null);
    }
  };

  return (
    <div className="cartao">
      <h2><Receipt size={20} />Ordens de compra consolidadas {todas ? '(todas)' : `com entrega no ${regiao}`}
        {regiao && (
          <span className="extra"><label style={{ flexDirection: 'row', alignItems: 'center' }}>
            <input type="checkbox" checked={todas} onChange={(e) => setTodas(e.target.checked)} /> mostrar todas
          </label></span>)}
      </h2>
      {msg && <div className={`mensagem ${msg.ok ? 'sucesso' : 'erro'}`} style={{ marginBottom: 12 }}>{msg.texto}</div>}
      {lista.length === 0 && <p className="suave">Ainda não há ordens.</p>}
      {lista.length > 0 && (
        <table>
          <thead>
            <tr><th>Ordem</th><th>Estado</th><th>Produto / fornecedor</th><th>Canal</th><th className="num">Total</th>
              <th>Repartição por armazém</th><th>Confirmação</th><th>Factura</th><th /></tr>
          </thead>
          <tbody>
            {lista.map((o) => (
              <tr key={o.id}>
                <td className="mono"><b>{o.id}</b><div className="suave">disparou {o.armazemOrigem} · L={o.tsLamport}</div></td>
                <td><span className={`etiqueta ${o.estado}`}>{o.estado.replace('_', ' ')}</span></td>
                <td>{o.produtoNome}<div className="suave">{o.fornecedorNome}</div></td>
                <td><span className="etiqueta cinza">{o.protocolo}</span></td>
                <td className="num"><b>{o.quantidadeTotal}</b></td>
                <td className="mono">{Object.entries(o.linhas).map(([a, q]) => (
                  <span key={a} style={{ fontWeight: a === regiao ? 800 : 400, marginRight: 10 }}>{a} {q}</span>))}</td>
                <td className="mono">{o.confirmacao || '—'}{o.prazoEntrega && <div className="suave">entrega {o.prazoEntrega}</div>}</td>
                <td>{o.estado !== 'PENDENTE_ENVIO'
                  ? <a href={urlFicheiro(no, caminhoFactura(o))} target="_blank" rel="noreferrer"><FileDown size={16} /> PDF</a> : '—'}</td>
                <td>{o.estado === 'ENVIADA' && (
                  <button className="botao" disabled={aReceber === o.id} onClick={() => receber(o)}>
                    <PackageCheck size={17} />{aReceber === o.id ? 'A receber…' : 'Receber entrega'}
                  </button>)}</td>
              </tr>))}
          </tbody>
        </table>
      )}
    </div>
  );
}
