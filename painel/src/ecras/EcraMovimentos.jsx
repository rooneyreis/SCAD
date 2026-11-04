import React from 'react';
import { deQualquerNo } from '../api.js';
import { usePolling } from '../usePolling.js';

export default function EcraMovimentos({ vivos }) {
  const [movs, erro] = usePolling(() => deQualquerNo('/global/movimentos?limite=60', vivos), 1500, [vivos.join()]);
  return (
    <div className="cartao">
      <h2>Movimentos de stock (todos os armazéns, tempo real)</h2>
      {erro && !movs && <div className="mensagem erro">{erro}</div>}
      <table>
        <thead><tr><th>Hora corrigida (Berkeley)</th><th className="num">Lamport</th><th>Armazém</th><th>Tipo</th>
          <th>Produto</th><th className="num">Quantidade</th><th>Motivo</th><th>Origem</th></tr></thead>
        <tbody>
          {(movs || []).map((m) => (
            <tr key={`${m.armazemId}-${m.id}`}>
              <td className="mono">{new Date(m.tsCorrigido).toLocaleString('pt-PT', { hour12: false })}.
                {String(new Date(m.tsCorrigido).getMilliseconds()).padStart(3, '0')}</td>
              <td className="num mono">{m.tsLamport}</td>
              <td><b>{m.armazemId}</b></td>
              <td><span className={`etiqueta ${m.tipo === 'SAIDA' ? 'AVISO' : 'SIM'}`}>{m.tipo}</span></td>
              <td>{m.produtoId}</td>
              <td className="num">{m.tipo === 'SAIDA' ? '−' : '+'}{m.quantidade}</td>
              <td>{m.motivo}</td>
              <td>{m.origem}</td>
            </tr>))}
        </tbody>
      </table>
    </div>
  );
}
