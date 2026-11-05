import React from 'react';
import { consultarPedido, meusPedidos, mzn } from '../api.js';
import { usePolling } from '../usePolling.js';

const ROTULO = { PENDENTE: 'À espera de autorização', AUTORIZADO: 'Autorizado', RECUSADO: 'Recusado' };

/** Pedidos feitos neste navegador, com o estado actual de cada um. */
export default function MeusPedidos() {
  const ids = meusPedidos.ler();
  const [pedidos] = usePolling(() => Promise.all(ids.map((id) => consultarPedido(id).catch(() => null))), 2000);
  return (
    <div className="pagina-pedido">
      <h1>Os meus pedidos</h1>
      {ids.length === 0 && <p className="vazio">Ainda não fez nenhum pedido neste dispositivo.</p>}
      <div className="lista-pedidos">
        {(pedidos || []).filter(Boolean).map((p) => (
          <a key={p.id} className="item-pedido" href={`#/pedido/${p.id}`}>
            <span className="mono"><b>{p.id}</b></span>
            <span>{p.linhas.map((l) => `${l.quantidade} × ${l.produtoNome}`).join(', ')}</span>
            <b>{mzn(p.total)}</b>
            <span className={`estado ${p.estado}`}>{ROTULO[p.estado]}</span>
          </a>))}
      </div>
    </div>
  );
}
