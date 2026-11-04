import React, { useEffect, useRef, useState } from 'react';
import { AlertTriangle, CheckCircle2, Info, ServerCrash } from 'lucide-react';
import { REGIOES } from '../api.js';
import { abaixoDoMinimo } from '../tema.js';

const DURACAO_MS = 7000;
const ICONE = { alerta: AlertTriangle, sucesso: CheckCircle2, aviso: ServerCrash, info: Info };

/**
 * Compara cada leitura com a anterior e gera notificações para os acontecimentos importantes:
 * alerta de stock, ordem criada/enviada/entregue, nó caiu/voltou, réplica promovida.
 * A primeira leitura serve só de referência (não gera notificações).
 */
export function useDetectorEventos({ estados, stock, ordens, fragmentos, regiao }) {
  const [lista, setLista] = useState([]);
  const anterior = useRef({});

  const notificar = (tipo, texto, detalhe) => {
    const id = `${Date.now()}-${Math.random()}`;
    setLista((l) => [...l.slice(-3), { id, tipo, texto, detalhe }]);
    setTimeout(() => setLista((l) => l.filter((n) => n.id !== id)), DURACAO_MS);
  };

  useEffect(() => {
    if (!estados) return;
    const antes = anterior.current.estados;
    if (antes) {
      REGIOES.forEach((r) => {
        if (antes[r] && !estados[r]) notificar('aviso', `Nó ${r} caiu`, 'detector de falhas: sem resposta');
        if (!antes[r] && estados[r]) notificar('sucesso', `Nó ${r} voltou`, 'reintegrado no grupo');
      });
    }
    anterior.current.estados = estados;
  }, [estados]);

  useEffect(() => {
    if (!stock) return;
    const chave = (l) => `${l.armazemId}/${l.produtoId}`;
    const antes = anterior.current.stock;
    if (antes) {
      stock.stock.forEach((l) => {
        const a = antes.get(chave(l));
        if (a && !abaixoDoMinimo(a) && abaixoDoMinimo(l) && (!regiao || l.armazemId === regiao)) {
          notificar('alerta', `Alerta de stock: ${l.produtoNome} no ${l.armazemId}`,
            `${l.quantidade} < mínimo ${l.minimo} → alerta CORBA + reposição`);
        }
      });
    }
    anterior.current.stock = new Map(stock.stock.map((l) => [chave(l), l]));
  }, [stock]);

  useEffect(() => {
    if (!ordens) return;
    const antes = anterior.current.ordens;
    if (antes) {
      ordens.forEach((o) => {
        const a = antes.get(o.id);
        const repart = Object.entries(o.linhas).map(([k, v]) => `${k} ${v}`).join(' · ');
        if (!a) notificar('info', `Ordem ${o.id} criada (2PC)`, `${o.quantidadeTotal} × ${o.produtoNome}: ${repart}`);
        else if (a.estado !== o.estado && o.estado === 'ENVIADA') notificar('sucesso', `Ordem ${o.id} enviada`, `${o.protocolo} · ${o.confirmacao}`);
        else if (a.estado !== o.estado && o.estado === 'ENTREGUE') notificar('sucesso', `Ordem ${o.id} entregue`, repart);
      });
    }
    anterior.current.ordens = new Map(ordens.map((o) => [o.id, o]));
  }, [ordens]);

  useEffect(() => {
    if (!fragmentos) return;
    const antes = anterior.current.fragmentos;
    if (antes) {
      fragmentos.fragmentos.forEach((f) => {
        if (antes.get(f.regiao) === 'PRIMARIO' && f.emUso === 'REPLICA') {
          notificar('aviso', `Réplica promovida: fragmento ${f.regiao}`, `failover para ${f.enderecoActivo}`);
        }
      });
    }
    anterior.current.fragmentos = new Map(fragmentos.fragmentos.map((f) => [f.regiao, f.emUso]));
  }, [fragmentos]);

  return lista;
}

export default function Notificacoes({ lista }) {
  return (
    <div className="notificacoes">
      {lista.map((n) => {
        const Icone = ICONE[n.tipo] || Info;
        return (
          <div key={n.id} className={`notificacao ${n.tipo}`}>
            <Icone size={20} style={{ flexShrink: 0, marginTop: 1 }} />
            <div><b>{n.texto}</b>{n.detalhe && <small>{n.detalhe}</small>}</div>
          </div>
        );
      })}
    </div>
  );
}
