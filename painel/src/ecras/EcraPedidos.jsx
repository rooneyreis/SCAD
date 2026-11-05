import React, { useState } from 'react';
import { Check, FileDown, History, ShoppingBag, X } from 'lucide-react';
import { paraNo, urlFicheiro } from '../api.js';
import { hora, mzn } from '../tema.js';

const MOTIVOS_RAPIDOS = ['Cliente não atendeu o telefone', 'Produto danificado no armazém', 'Fora da zona de entrega'];

/** Região de um pedido a partir do id: PED-SUL-000001 → SUL. */
const regiaoDoPedido = (id) => id.split('-')[1];

/** Um pedido pendente: cliente, produtos, total e as decisões Autorizar / Recusar. */
function CartaoPedido({ p, vivos, aoDecidir, mostrarArmazem }) {
  const [aRecusar, setARecusar] = useState(false);
  const [motivo, setMotivo] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const [erro, setErro] = useState(null);
  const no = regiaoDoPedido(p.id);   // só o nó do armazém escreve no seu fragmento

  const decidir = async (accao, corpo) => {
    setOcupado(true);
    setErro(null);
    try {
      const r = await paraNo(no, `/pedidos/${p.id}/${accao}`, corpo);
      aoDecidir(r);
    } catch (e) {
      setErro(!vivos.includes(no) ? `O nó ${no} está em baixo: não é possível decidir agora.` : e.message);
      setOcupado(false);
    }
  };

  return (
    <div className="pedido">
      <div className="pedido-cab">
        <div>
          <div className="mono"><b>{p.id}</b>{mostrarArmazem && <span className="suave"> · {p.armazemId}</span>}</div>
          <div className="suave">recebido às {hora(p.criadoEm)} · pagamento simulado {p.pagamentoRef}</div>
        </div>
        <div className="pedido-total">{mzn(p.total)}</div>
      </div>
      <div className="pedido-cliente"><b>{p.clienteNome}</b> <span className="suave">· {p.clienteTelefone}</span></div>
      <table className="compacta">
        <tbody>
          {p.linhas.map((l) => (
            <tr key={l.produtoId}>
              <td>{l.produtoNome}</td>
              <td className="num">{l.quantidade} × {mzn(l.precoUnitario)}</td>
              <td className="num"><b>{mzn(l.subtotal)}</b></td>
            </tr>))}
        </tbody>
      </table>
      {!aRecusar && (
        <div className="linha" style={{ marginTop: 12 }}>
          <button className="botao autorizar" disabled={ocupado} onClick={() => decidir('autorizar')}>
            <Check size={18} />{ocupado ? 'A autorizar…' : 'Autorizar'}
          </button>
          <button className="botao secundario recusar" disabled={ocupado} onClick={() => setARecusar(true)}>
            <X size={18} />Recusar
          </button>
        </div>
      )}
      {aRecusar && (
        <div className="recusa">
          <div className="linha">
            {MOTIVOS_RAPIDOS.map((m) => (
              <button key={m} type="button" className={`chip ${motivo === m ? 'activo' : ''}`} onClick={() => setMotivo(m)}>{m}</button>))}
          </div>
          <div className="linha" style={{ marginTop: 10 }}>
            <input style={{ flex: 1, minWidth: 220 }} placeholder="Motivo da recusa" value={motivo}
                   onChange={(e) => setMotivo(e.target.value)} />
            <button className="botao perigo" disabled={ocupado || !motivo.trim()}
                    onClick={() => decidir('recusar', { motivo: motivo.trim() })}>
              <X size={18} />{ocupado ? 'A recusar…' : 'Confirmar recusa'}
            </button>
            <button className="botao secundario" disabled={ocupado} onClick={() => setARecusar(false)}>Voltar</button>
          </div>
        </div>
      )}
      {erro && <div className="mensagem erro">{erro}</div>}
    </div>
  );
}

/**
 * Pedidos de clientes da loja para este armazém: os pendentes (com Autorizar / Recusar) e o histórico.
 * Autorizar = saída de stock (VENDA, origem LOJA) + recibo PDF no HDFS; recusar liberta a reserva.
 */
export default function EcraPedidos({ regiao, vivos, pedidos, recarregar }) {
  const [msg, setMsg] = useState(null);
  const lista = pedidos || [];
  const pendentes = lista.filter((p) => p.estado === 'PENDENTE').sort((a, b) => a.tsLamport - b.tsLamport);
  const decididos = lista.filter((p) => p.estado !== 'PENDENTE');
  const noFicheiros = vivos[0] || 'NORTE';

  const aoDecidir = (p) => {
    setMsg(p.estado === 'AUTORIZADO'
      ? { ok: true, texto: `${p.id} autorizado: saída de stock registada (VENDA, origem LOJA) e recibo enviado para o HDFS.` }
      : { ok: false, texto: `${p.id} recusado (${p.motivoRecusa}): a reserva foi libertada.` });
    recarregar();
  };

  return (
    <>
      <div className="cartao">
        <h2><ShoppingBag size={20} />Pedidos pendentes {regiao ? `— ${regiao}` : '(todos os armazéns)'}
          <span className="extra">o stock destes pedidos está reservado até serem autorizados ou recusados</span></h2>
        {msg && <div className={`mensagem ${msg.ok ? 'sucesso' : 'erro'}`} style={{ marginBottom: 14 }}>{msg.texto}</div>}
        {pedidos === null && <p className="suave">A carregar…</p>}
        {pedidos !== null && pendentes.length === 0 && <p className="suave">Não há pedidos à espera. Os novos aparecem aqui sozinhos.</p>}
        <div className="pedidos">
          {pendentes.map((p) => (
            <CartaoPedido key={p.id} p={p} vivos={vivos} aoDecidir={aoDecidir} mostrarArmazem={!regiao} />))}
        </div>
      </div>

      <div className="cartao">
        <h2><History size={20} />Histórico</h2>
        {decididos.length === 0 && <p className="suave">Ainda não há pedidos decididos.</p>}
        {decididos.length > 0 && (
          <table>
            <thead><tr><th>Pedido</th><th>Cliente</th><th>Produtos</th><th className="num">Total</th><th>Estado</th>
              <th>Decidido</th><th>Recibo / motivo</th></tr></thead>
            <tbody>
              {decididos.map((p) => (
                <tr key={p.id}>
                  <td className="mono"><b>{p.id}</b></td>
                  <td>{p.clienteNome}<div className="suave">{p.clienteTelefone}</div></td>
                  <td>{p.linhas.map((l) => `${l.quantidade} × ${l.produtoNome}`).join(', ')}</td>
                  <td className="num"><b>{mzn(p.total)}</b></td>
                  <td><span className={`etiqueta ${p.estado}`}>{p.estado}</span></td>
                  <td>{hora(p.decididoEm)}</td>
                  <td>{p.estado === 'AUTORIZADO'
                    ? <a href={urlFicheiro(noFicheiros, p.recibo)} target="_blank" rel="noreferrer"><FileDown size={16} /> Recibo PDF</a>
                    : <span className="suave">{p.motivoRecusa}</span>}</td>
                </tr>))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
