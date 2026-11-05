import React, { useEffect, useState } from 'react';
import { ReceiptText, ShoppingBasket } from 'lucide-react';
import { catalogo as lerCatalogo, meusPedidos } from './api.js';
import { usePolling } from './usePolling.js';
import Montra from './paginas/Montra.jsx';
import PaginaPedido from './paginas/PaginaPedido.jsx';
import MeusPedidos from './paginas/MeusPedidos.jsx';

/** Rota a partir do endereço: #/ (montra), #/pedido/<id>, #/pedidos. */
function useRota() {
  const ler = () => window.location.hash.replace(/^#\/?/, '');
  const [rota, setRota] = useState(ler());
  useEffect(() => {
    const mudar = () => setRota(ler());
    window.addEventListener('hashchange', mudar);
    return () => window.removeEventListener('hashchange', mudar);
  }, []);
  return rota;
}

export default function App() {
  const rota = useRota();
  const [regiao, setRegiao] = useState(localStorage.getItem('scad-loja-cidade') || 'SUL');
  const [carrinhos, setCarrinhos] = useState({});
  // Tempo real: o catálogo (disponível em cada cidade) é relido a cada 1,5 s
  const [catalogo, erroCatalogo] = usePolling(() => lerCatalogo(regiao), 1500, [regiao]);

  const mudarCidade = (r) => {
    setRegiao(r);
    localStorage.setItem('scad-loja-cidade', r);
  };
  const concluir = (pedido) => {
    meusPedidos.guardar(pedido.id);
    window.location.hash = `#/pedido/${pedido.id}`;
  };
  const noCarrinho = Object.values(carrinhos[regiao] || {}).reduce((t, q) => t + q, 0);

  return (
    <div className="loja">
      <header className="topo">
        <a className="marca" href="#/">
          <span className="logo"><ShoppingBasket size={24} /></span>
          <span><b>Mercado SCAD</b><small>Bens essenciais, directamente do armazém da sua cidade</small></span>
        </a>
        <nav>
          <a href="#/pedidos"><ReceiptText size={18} />Os meus pedidos</a>
          <a href="#/" className="botao-carrinho"><ShoppingBasket size={18} />Carrinho
            {noCarrinho > 0 && <span className="contador">{noCarrinho}</span>}</a>
        </nav>
      </header>
      <main>
        {rota.startsWith('pedido/') && <PaginaPedido id={decodeURIComponent(rota.slice('pedido/'.length))} />}
        {rota === 'pedidos' && <MeusPedidos />}
        {!rota.startsWith('pedido/') && rota !== 'pedidos' && (
          <Montra catalogo={catalogo} erroCatalogo={erroCatalogo} regiao={regiao} aoMudarCidade={mudarCidade}
                  carrinhos={carrinhos} setCarrinhos={setCarrinhos} aoConcluir={concluir} />
        )}
      </main>
      <footer>Mercado SCAD · projecto académico (ISUTC) · pagamentos simulados · stock actualizado em tempo real</footer>
    </div>
  );
}
