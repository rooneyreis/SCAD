import React, { useEffect, useRef, useState } from 'react';
import { Bean, Candy, Droplets, Minus, Package, Plus, ShoppingCart, Sparkles, Wheat } from 'lucide-react';
import { mzn } from '../api.js';

/** Ícone e cor de cada produto (não há fotografias). */
const VISUAL = {
  'ARROZ-25': [Wheat, '#b45309', '#fef3c7'],
  'FARINHA-25': [Wheat, '#a16207', '#fefce8'],
  'FEIJAO-10': [Bean, '#9a3412', '#ffedd5'],
  'ACUCAR-10': [Candy, '#be185d', '#fce7f3'],
  'OLEO-5': [Droplets, '#ca8a04', '#fef9c3'],
  'SABAO-BARRA': [Sparkles, '#0369a1', '#e0f2fe'],
};
const POUCAS = 10;

/** Assinala durante uns segundos quando o disponível muda (outro cliente comprou, ou o armazém repôs). */
function useVariacao(valor) {
  const anterior = useRef(valor);
  const [variacao, setVariacao] = useState(null);
  useEffect(() => {
    if (anterior.current !== valor) {
      setVariacao({ delta: valor - anterior.current, chave: Date.now() });
      anterior.current = valor;
      const t = setTimeout(() => setVariacao(null), 4000);
      return () => clearTimeout(t);
    }
    return undefined;
  }, [valor]);
  return variacao;
}

export default function CartaoProduto({ produto, cidade, noCarrinho, aoAdicionar, fechada }) {
  const [Icone, cor, fundo] = VISUAL[produto.id] || [Package, '#475569', '#f1f5f9'];
  const livre = Math.max(0, produto.disponivel - noCarrinho);   // o que ainda se pode juntar ao carrinho
  const [quantidade, setQuantidade] = useState(1);
  const variacao = useVariacao(produto.disponivel);
  const esgotado = produto.disponivel === 0;
  const q = Math.min(quantidade, Math.max(livre, 1));

  return (
    <article className={`produto ${esgotado ? 'esgotado' : ''}`}>
      <div className="produto-imagem" style={{ background: fundo, color: cor }}>
        <Icone size={54} strokeWidth={1.6} />
        {esgotado && <span className="selo">Esgotado</span>}
      </div>
      <div className="produto-corpo">
        <h3>{produto.nome}</h3>
        <div className="unidade">por {produto.unidade}</div>
        <div className="preco">{mzn(produto.precoVenda)}</div>
        <div className={`stock ${esgotado ? 'zero' : produto.disponivel <= POUCAS ? 'poucas' : ''} ${variacao ? 'mudou' : ''}`}>
          {esgotado ? `Esgotado em ${cidade}`
            : produto.disponivel <= POUCAS ? `Últimas ${produto.disponivel} unidades em ${cidade}`
              : `${produto.disponivel} disponíveis em ${cidade}`}
          {variacao && <span key={variacao.chave} className={`delta ${variacao.delta < 0 ? 'desce' : 'sobe'}`}>
            {variacao.delta > 0 ? '+' : ''}{variacao.delta}</span>}
        </div>
        <div className="comprar">
          <div className="seletor">
            <button aria-label="menos" disabled={q <= 1} onClick={() => setQuantidade(q - 1)}><Minus size={16} /></button>
            <input aria-label="quantidade" inputMode="numeric" value={q}
                   onChange={(e) => setQuantidade(Math.max(1, Math.min(Number(e.target.value.replace(/\D/g, '')) || 1, livre)))} />
            <button aria-label="mais" disabled={q >= livre} onClick={() => setQuantidade(q + 1)}><Plus size={16} /></button>
          </div>
          <button className="adicionar" disabled={fechada || livre === 0}
                  onClick={() => { aoAdicionar(produto.id, q); setQuantidade(1); }}>
            <ShoppingCart size={17} />{livre === 0 && !esgotado ? 'Tudo no carrinho' : 'Adicionar'}
          </button>
        </div>
        {noCarrinho > 0 && <div className="no-carrinho">{noCarrinho} no carrinho · máximo {produto.disponivel}</div>}
      </div>
    </article>
  );
}
