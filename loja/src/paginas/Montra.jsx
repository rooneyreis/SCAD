import React, { useEffect, useState } from 'react';
import { MapPin, WifiOff } from 'lucide-react';
import { CIDADES } from '../api.js';
import CartaoProduto from '../componentes/CartaoProduto.jsx';
import Carrinho from '../componentes/Carrinho.jsx';

/**
 * Montra: escolha da cidade, produtos com o disponível em tempo real e carrinho.
 * Há um carrinho por cidade (cada compra é feita a um só armazém). Se o disponível descer abaixo do
 * que está no carrinho (outro cliente comprou), a quantidade é ajustada e o cliente é avisado.
 */
export default function Montra({ catalogo, erroCatalogo, regiao, aoMudarCidade, carrinhos, setCarrinhos, aoConcluir }) {
  const [ajustes, setAjustes] = useState([]);
  const cidade = catalogo && catalogo.find((c) => c.armazemId === regiao);
  const carrinho = carrinhos[regiao] || {};

  useEffect(() => {
    if (!cidade || !cidade.aberta) return;
    const novos = [];
    const ajustado = { ...carrinho };
    cidade.produtos.forEach((p) => {
      if (ajustado[p.id] > p.disponivel) {
        novos.push(p.disponivel === 0
          ? `${p.nome} esgotou entretanto e foi retirado do carrinho.`
          : `Só restam ${p.disponivel} de ${p.nome}: a quantidade no carrinho foi ajustada.`);
        if (p.disponivel === 0) delete ajustado[p.id]; else ajustado[p.id] = p.disponivel;
      }
    });
    if (novos.length) {
      setCarrinhos((c) => ({ ...c, [regiao]: ajustado }));
      setAjustes(novos);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [catalogo]);
  useEffect(() => setAjustes([]), [regiao]);

  const alterar = (id, q) => setCarrinhos((c) => {
    const novo = { ...(c[regiao] || {}) };
    if (q <= 0) delete novo[id]; else novo[id] = q;
    return { ...c, [regiao]: novo };
  });
  const linhas = cidade ? cidade.produtos.filter((p) => carrinho[p.id]).map((p) => ({ produto: p, quantidade: carrinho[p.id] })) : [];

  return (
    <>
      <section className="cidades" aria-label="Escolha a cidade">
        {CIDADES.map(({ regiao: r, cidade: nome }) => {
          const c = catalogo && catalogo.find((x) => x.armazemId === r);
          const fechada = c && !c.aberta;
          return (
            <button key={r} className={`cidade ${r === regiao ? 'activa' : ''} ${fechada ? 'fechada' : ''}`} onClick={() => aoMudarCidade(r)}>
              {fechada ? <WifiOff size={20} /> : <MapPin size={20} />}
              <span><b>{nome}</b><small>{fechada ? 'Indisponível de momento' : `Armazém ${r}`}</small></span>
            </button>);
        })}
      </section>

      {erroCatalogo && !catalogo && <div className="banner erro">Não foi possível contactar os armazéns. A tentar de novo…</div>}
      {cidade && !cidade.aberta && (
        <div className="banner erro"><WifiOff size={20} />
          O armazém de {CIDADES.find((c) => c.regiao === regiao).cidade} está indisponível de momento ({cidade.motivo.toLowerCase()}).
          As outras cidades continuam a funcionar.</div>
      )}

      <div className="montra">
        <section className="produtos">
          {!catalogo && !erroCatalogo && <p className="vazio">A carregar o catálogo…</p>}
          {cidade && cidade.produtos.map((p) => (
            <CartaoProduto key={p.id} produto={p} cidade={cidade.cidade} noCarrinho={carrinho[p.id] || 0}
                           fechada={!cidade.aberta} aoAdicionar={(id, q) => alterar(id, (carrinho[id] || 0) + q)} />
          ))}
        </section>
        {cidade && (
          <Carrinho cidade={cidade} linhas={linhas} ajustes={ajustes} aoAlterar={alterar}
                    aoRemover={(id) => alterar(id, 0)}
                    aoConcluir={(p) => { setCarrinhos((c) => ({ ...c, [regiao]: {} })); aoConcluir(p); }} />
        )}
      </div>
    </>
  );
}
