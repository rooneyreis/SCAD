import React, { useState } from 'react';
import { ListOrdered } from 'lucide-react';

export default function EcraMovimentos({ regiao, movimentos }) {
  const [todos, setTodos] = useState(!regiao);
  const lista = (movimentos || []).filter((m) => todos || m.armazemId === regiao).slice(0, 80);
  return (
    <div className="cartao">
      <h2><ListOrdered size={20} />Movimentos de stock {todos ? '(todos os armazéns)' : `— ${regiao}`}
        {regiao && (
          <span className="extra"><label style={{ flexDirection: 'row', alignItems: 'center' }}>
            <input type="checkbox" checked={todos} onChange={(e) => setTodos(e.target.checked)} /> todos os armazéns
          </label></span>)}
      </h2>
      <table>
        <thead><tr><th>Hora corrigida (Berkeley)</th><th className="num">Lamport</th><th>Armazém</th><th>Tipo</th>
          <th>Produto</th><th className="num">Quantidade</th><th>Motivo</th><th>Origem</th></tr></thead>
        <tbody>
          {lista.map((m) => {
            const d = new Date(m.tsCorrigido);
            return (
              <tr key={`${m.armazemId}-${m.id}`}>
                <td className="mono">{d.toLocaleString('pt-PT', { hour12: false })}.{String(d.getMilliseconds()).padStart(3, '0')}</td>
                <td className="num mono">{m.tsLamport}</td>
                <td><b>{m.armazemId}</b></td>
                <td><span className={`etiqueta ${m.tipo === 'SAIDA' ? 'AVISO' : 'SIM'}`}>{m.tipo === 'SAIDA' ? 'SAÍDA' : 'ENTRADA'}</span></td>
                <td>{m.produtoId}</td>
                <td className="num">{m.tipo === 'SAIDA' ? '−' : '+'}{m.quantidade}</td>
                <td>{m.motivo}</td>
                <td>{m.origem}</td>
              </tr>);
          })}
        </tbody>
      </table>
      {lista.length === 0 && <p className="suave">Sem movimentos.</p>}
    </div>
  );
}
