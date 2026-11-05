import React from 'react';
import { ArrowLeft, CircleCheck, CircleX, Clock, FileDown, Warehouse } from 'lucide-react';
import { CIDADES, consultarPedido, mzn, urlRecibo } from '../api.js';
import { usePolling } from '../usePolling.js';

const hora = (iso) => (iso ? new Date(iso).toLocaleString('pt-PT', { hour12: false }) : '');

/** "O meu pedido": o estado actualiza-se sozinho (PENDENTE → AUTORIZADO / RECUSADO). */
export default function PaginaPedido({ id }) {
  const [p, erro] = usePolling(() => consultarPedido(id), 1500, [id]);
  if (!p) {
    return <div className="pagina-pedido">{erro ? <div className="banner erro">{erro.message}</div> : <p className="vazio">A carregar…</p>}</div>;
  }
  const cidade = CIDADES.find((c) => c.regiao === p.armazemId).cidade;
  const decidido = p.estado !== 'PENDENTE';

  return (
    <div className="pagina-pedido">
      <a className="voltar" href="#/"><ArrowLeft size={16} />Continuar a comprar</a>
      <div className={`estado-pedido ${p.estado}`}>
        {p.estado === 'PENDENTE' && <Clock size={40} />}
        {p.estado === 'AUTORIZADO' && <CircleCheck size={40} />}
        {p.estado === 'RECUSADO' && <CircleX size={40} />}
        <div>
          <h1>{p.estado === 'PENDENTE' ? 'À espera de autorização do armazém'
            : p.estado === 'AUTORIZADO' ? 'Pedido autorizado' : 'Pedido recusado'}</h1>
          <p>
            {p.estado === 'PENDENTE' && `O stock já está reservado para si no armazém de ${cidade}. Esta página actualiza-se sozinha.`}
            {p.estado === 'AUTORIZADO' && `O armazém de ${cidade} autorizou a entrega. Obrigado pela compra!`}
            {p.estado === 'RECUSADO' && <>Motivo: <b>{p.motivoRecusa}</b>. A reserva foi libertada; nada foi cobrado.</>}
          </p>
        </div>
      </div>

      <ol className="passos">
        <li className="feito"><span>1</span>Pedido recebido<small>{hora(p.criadoEm)}</small></li>
        <li className={decidido ? 'feito' : 'actual'}><span>2</span>Autorização do armazém<small>{decidido ? hora(p.decididoEm) : 'em curso…'}</small></li>
        <li className={p.estado === 'AUTORIZADO' ? 'feito' : p.estado === 'RECUSADO' ? 'falhou' : ''}><span>3</span>
          {p.estado === 'RECUSADO' ? 'Recusado' : 'Autorizado'}</li>
      </ol>

      <div className="resumo-pedido">
        <div className="cab"><b className="mono">{p.id}</b><span><Warehouse size={16} /> Armazém {p.armazemId} · {cidade}</span></div>
        <div className="cliente">{p.clienteNome} · {p.clienteTelefone} · pagamento simulado {p.pagamentoRef}</div>
        <table>
          <tbody>
            {p.linhas.map((l) => (
              <tr key={l.produtoId}><td>{l.produtoNome}</td><td className="num">{l.quantidade} × {mzn(l.precoUnitario)}</td>
                <td className="num"><b>{mzn(l.subtotal)}</b></td></tr>))}
          </tbody>
          <tfoot><tr><td colSpan={2}>Total</td><td className="num">{mzn(p.total)}</td></tr></tfoot>
        </table>
        {p.estado === 'AUTORIZADO' && (
          <a className="recibo" href={urlRecibo(p.id)} target="_blank" rel="noreferrer"><FileDown size={18} />Descarregar recibo (PDF)</a>
        )}
      </div>
    </div>
  );
}
