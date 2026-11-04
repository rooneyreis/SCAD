import React, { useState } from 'react';
import { deQualquerNo, paraNo, urlFicheiro } from '../api.js';
import { usePolling } from '../usePolling.js';

/** Caminho da factura no HDFS: /scad/facturas/AAAA/MM/OC-xxx.pdf (data UTC da ordem). */
function caminhoFactura(o) {
  const d = new Date(o.tsCorrigido);
  const mes = String(d.getUTCMonth() + 1).padStart(2, '0');
  return `/scad/facturas/${d.getUTCFullYear()}/${mes}/${o.id}.pdf`;
}

export default function EcraOrdens({ vivos }) {
  const [ordens, erro, recarregar] = usePolling(() => deQualquerNo('/ordens', vivos), 2000, [vivos.join()]);
  const [msg, setMsg] = useState(null);
  const [aReceber, setAReceber] = useState(null);
  const no = vivos[0] || 'NORTE';

  const receber = async (o) => {
    setAReceber(o.id);
    try {
      const r = await paraNo(no, `/ordens/${o.id}/entrega`);
      setMsg({ ok: r.decisao2pc === 'COMMIT', texto: `Entrega de ${o.id} recebida (2PC ${r.decisao2pc} coordenado pelo nó ${no}).` });
      recarregar();
    } catch (e) {
      setMsg({ ok: false, texto: `Erro ao receber ${o.id}: ${e.message}` });
    } finally {
      setAReceber(null);
    }
  };

  return (
    <div className="cartao">
      <h2>Ordens de compra consolidadas</h2>
      {msg && <div className={`mensagem ${msg.ok ? 'sucesso' : 'erro'}`}>{msg.texto}</div>}
      {erro && !ordens && <div className="mensagem erro">{erro}</div>}
      {ordens && ordens.length === 0 && <p className="suave">Ainda não há ordens.</p>}
      {ordens && ordens.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Ordem</th><th>Estado</th><th>Produto</th><th>Fornecedor</th><th>Protocolo</th>
              <th className="num">Total</th><th>Repartição por armazém</th><th>Confirmação</th><th>Factura</th><th />
            </tr>
          </thead>
          <tbody>
            {ordens.map((o) => (
              <tr key={o.id}>
                <td className="mono"><b>{o.id}</b>
                  <div className="suave">disparou {o.armazemOrigem} · L={o.tsLamport}</div></td>
                <td><span className={`etiqueta ${o.estado}`}>{o.estado}</span></td>
                <td>{o.produtoNome}</td>
                <td>{o.fornecedorNome}</td>
                <td><span className="etiqueta cinza">{o.protocolo}</span></td>
                <td className="num"><b>{o.quantidadeTotal}</b></td>
                <td className="mono">{Object.entries(o.linhas).map(([a, q]) => `${a} ${q}`).join(' · ')}</td>
                <td className="mono">{o.confirmacao || '—'}
                  {o.prazoEntrega && <div className="suave">entrega {o.prazoEntrega}</div>}</td>
                <td>{o.estado !== 'PENDENTE_ENVIO'
                  ? <a href={urlFicheiro(no, caminhoFactura(o))} target="_blank" rel="noreferrer">PDF</a> : '—'}</td>
                <td>{o.estado === 'ENVIADA' && (
                  <button className="botao" disabled={aReceber === o.id} onClick={() => receber(o)}>
                    {aReceber === o.id ? 'A receber…' : 'Receber entrega'}
                  </button>)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
