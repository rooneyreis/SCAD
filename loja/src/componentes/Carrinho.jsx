import React, { useState } from 'react';
import { CreditCard, Loader2, Minus, Plus, ShoppingBasket, Smartphone, Trash2 } from 'lucide-react';
import { comprar, mzn } from '../api.js';

const PAGAMENTOS = [
  ['mpesa', 'M-Pesa', Smartphone],
  ['emola', 'e-Mola', Smartphone],
  ['cartao', 'Cartão', CreditCard],
];

/**
 * Carrinho e checkout. O pagamento é simulado: só se pede nome e telefone. A compra vai ao nó da
 * cidade escolhida, que reserva o stock; se outro cliente tiver levado as últimas unidades entretanto,
 * o servidor responde 409 e mostra-se a mensagem dele.
 */
export default function Carrinho({ cidade, linhas, ajustes, aoAlterar, aoRemover, aoConcluir }) {
  const [cliente, setCliente] = useState({ nome: '', telefone: '' });
  const [pagamento, setPagamento] = useState('mpesa');
  const [estado, setEstado] = useState({ ocupado: false, erro: null });
  const total = linhas.reduce((t, l) => t + l.quantidade * l.produto.precoVenda, 0);
  const vazio = linhas.length === 0;
  const podePagar = !vazio && cidade.aberta && cliente.nome.trim() && cliente.telefone.trim() && !estado.ocupado;

  const pagar = async (e) => {
    e.preventDefault();
    setEstado({ ocupado: true, erro: null });
    try {
      const p = await comprar(cidade.armazemId, {
        armazemId: cidade.armazemId,
        clienteNome: cliente.nome.trim(),
        clienteTelefone: cliente.telefone.trim(),
        itens: linhas.map((l) => ({ produtoId: l.produto.id, quantidade: l.quantidade })),
      });
      aoConcluir(p);
    } catch (err) {
      setEstado({ ocupado: false, erro: err.estado === 502 || err.estado === 503
        ? `O armazém de ${cidade.cidade} está indisponível de momento. Tente mais tarde ou escolha outra cidade.`
        : err.message });
      return;
    }
    setEstado({ ocupado: false, erro: null });
  };

  return (
    <aside className="carrinho">
      <h2><ShoppingBasket size={22} />O seu carrinho <span className="cidade-carrinho">{cidade.cidade}</span></h2>
      {ajustes.length > 0 && (
        <div className="aviso">{ajustes.map((a) => <div key={a}>{a}</div>)}</div>
      )}
      {vazio && <p className="vazio">Ainda não escolheu produtos. Os preços e o stock são os do armazém de {cidade.cidade}.</p>}
      {linhas.map((l) => (
        <div key={l.produto.id} className="linha-carrinho">
          <div className="nome">{l.produto.nome}<small>{mzn(l.produto.precoVenda)}</small></div>
          <div className="seletor pequeno">
            <button aria-label="menos" onClick={() => aoAlterar(l.produto.id, l.quantidade - 1)}><Minus size={14} /></button>
            <span>{l.quantidade}</span>
            <button aria-label="mais" disabled={l.quantidade >= l.produto.disponivel}
                    onClick={() => aoAlterar(l.produto.id, l.quantidade + 1)}><Plus size={14} /></button>
          </div>
          <div className="subtotal">{mzn(l.quantidade * l.produto.precoVenda)}</div>
          <button className="remover" aria-label="remover" onClick={() => aoRemover(l.produto.id)}><Trash2 size={16} /></button>
        </div>
      ))}
      {!vazio && (
        <>
          <div className="total"><span>Total</span><b>{mzn(total)}</b></div>
          <form className="checkout" onSubmit={pagar}>
            <label>Nome<input value={cliente.nome} maxLength={80} placeholder="ex.: Maria Cossa"
                              onChange={(e) => setCliente({ ...cliente, nome: e.target.value })} /></label>
            <label>Telefone<input value={cliente.telefone} inputMode="tel" placeholder="ex.: 84 123 4567"
                                  onChange={(e) => setCliente({ ...cliente, telefone: e.target.value })} /></label>
            <div className="pagamentos">
              {PAGAMENTOS.map(([id, nome, I]) => (
                <button key={id} type="button" className={pagamento === id ? 'activo' : ''} onClick={() => setPagamento(id)}>
                  <I size={16} />{nome}
                </button>))}
            </div>
            <div className="simulado">Pagamento simulado: nenhum valor é cobrado.</div>
            {estado.erro && <div className="erro">{estado.erro}</div>}
            <button className="pagar" disabled={!podePagar}>
              {estado.ocupado ? <><Loader2 size={18} className="roda" />A reservar o stock…</> : <>Pagar e encomendar · {mzn(total)}</>}
            </button>
          </form>
        </>
      )}
    </aside>
  );
}
