import React, { useEffect, useRef, useState } from 'react';
import { Activity, FileText, LayoutDashboard, ListOrdered, Package, Receipt, ShoppingBag, Truck, Warehouse } from 'lucide-react';
import { REGIOES, deQualquerNo, deTodosOsNos, doNo, fornecedor, regiaoDoPainel } from './api.js';
import { usePolling } from './usePolling.js';
import { TEMA } from './tema.js';
import Resumo from './componentes/Resumo.jsx';
import Notificacoes, { useDetectorEventos } from './componentes/Notificacoes.jsx';
import EcraArmazem from './ecras/EcraArmazem.jsx';
import EcraOrdens from './ecras/EcraOrdens.jsx';
import EcraMonitor from './ecras/EcraMonitor.jsx';
import EcraFicheiros from './ecras/EcraFicheiros.jsx';
import EcraFornecedor from './ecras/EcraFornecedor.jsx';
import EcraMovimentos from './ecras/EcraMovimentos.jsx';
import EcraPedidos from './ecras/EcraPedidos.jsx';

const ECRAS = [
  ['armazem', 'Stock', Package],
  ['pedidos', 'Pedidos de clientes', ShoppingBag],
  ['ordens', 'Ordens de compra', Receipt],
  ['movimentos', 'Movimentos', ListOrdered],
  ['ficheiros', 'Ficheiros HDFS', FileText],
  ['fornecedor', 'Fornecedor', Truck],
  ['monitor', 'Monitor distribuído', Activity],
];

/**
 * Estado de cada nó, actualizado nó a nó assim que cada um responde (a cada 1 s):
 * um nó em baixo nunca atrasa a informação dos que estão vivos, e volta sozinho quando reaparece.
 */
function useEstadosDosNos() {
  const [estados, setEstados] = useState(null);
  useEffect(() => {
    let vivo = true;
    const ler = () => REGIOES.forEach((r) => {
      doNo(r, '/estado', 1500)
        .then((e) => vivo && setEstados((a) => ({ ...(a || {}), [r]: e })))
        .catch(() => vivo && setEstados((a) => ({ ...(a || {}), [r]: null })));
    });
    ler();
    const id = setInterval(ler, 1000);
    return () => { vivo = false; clearInterval(id); };
  }, []);
  return estados;
}

/**
 * Pedidos da loja e resumo (pendentes, vendas de hoje): do nó deste armazém, ou de todos os nós
 * na vista geral. Lidos a cada 1,5 s: um pedido novo aparece sozinho no painel.
 */
function lerPedidos(regiao) {
  if (regiao) return doNo(regiao, '/pedidos?limite=100', 2500);
  return deTodosOsNos('/pedidos?limite=100').then((porNo) => Object.values(porNo).flatMap((l) => l || [])
    .sort((a, b) => b.criadoEm.localeCompare(a.criadoEm)));
}

function lerResumoLoja(regiao) {
  if (regiao) return doNo(regiao, '/pedidos/resumo', 2500);
  return deTodosOsNos('/pedidos/resumo').then((porNo) => Object.values(porNo).filter(Boolean).reduce(
    (t, r) => ({ pendentes: t.pendentes + r.pendentes, vendasHoje: t.vendasHoje + r.vendasHoje }),
    { pendentes: 0, vendasHoje: 0 }));
}

export default function App() {
  // Região deste painel: 8081 NORTE, 8082 CENTRO, 8083 SUL; 8080 = vista geral (null)
  const [regiao, setRegiao] = useState(undefined);
  useEffect(() => { regiaoDoPainel().then(setRegiao); }, []);

  const inicial = window.location.hash.slice(1);
  const [ecra, setEcraEstado] = useState(ECRAS.some(([id]) => id === inicial) ? inicial : 'armazem');
  const setEcra = (id) => { window.location.hash = id; setEcraEstado(id); };

  // Dados lidos do primeiro nó vivo: o painel sobrevive à queda do seu próprio nó
  const estados = useEstadosDosNos();
  const vivos = REGIOES.filter((r) => estados && estados[r]);
  const chave = [vivos.join()];
  const [stock] = usePolling(() => deQualquerNo('/global/stock', vivos), 1500, chave);
  const [ordens, , recarregarOrdens] = usePolling(() => deQualquerNo('/ordens', vivos), 2000, chave);
  const [movimentos] = usePolling(() => deQualquerNo('/global/movimentos?limite=300', vivos), 2000, chave);
  const [fragmentos] = usePolling(() => deQualquerNo('/fragmentos', vivos), 2000, chave);
  const [forn, erroForn] = usePolling(() => fornecedor.estado(), 2000);
  const [pedidos, , recarregarPedidos] = usePolling(() => (regiao === undefined ? null : lerPedidos(regiao)), 1500, [regiao]);
  const [resumoLoja, , recarregarResumo] = usePolling(() => (regiao === undefined ? null : lerResumoLoja(regiao)), 1500, [regiao]);

  const notificacoes = useDetectorEventos({ estados, stock, ordens, fragmentos, pedidos, regiao });

  const tema = TEMA[regiao || 'GERAL'];
  const raiz = useRef(document.documentElement);
  useEffect(() => {
    raiz.current.style.setProperty('--destaque', tema.cor);
    raiz.current.style.setProperty('--destaque-claro', tema.claro);
    document.title = `SCAD — ${tema.nome}`;
  }, [tema]);

  if (regiao === undefined) return null;
  const meuNoEmBaixo = regiao && estados && !estados[regiao];
  const meuFragmento = regiao && fragmentos && fragmentos.fragmentos.find((f) => f.regiao === regiao);
  const nomeEcra = ECRAS.find(([id]) => id === ecra)[1];
  const Icone = regiao ? Warehouse : LayoutDashboard;
  const pendentes = (pedidos || []).filter((p) => p.estado === 'PENDENTE').length;

  return (
    <div className="app">
      <aside className="lateral">
        <div className="marca">
          <div className="icone"><Icone size={26} /></div>
          <div><h1>{tema.nome}</h1><div className="cidade">{tema.cidade}</div></div>
        </div>
        {regiao && (
          <div className="estado-no">
            <span className={`pastilha ${!estados ? 'aviso' : meuNoEmBaixo ? 'falha' : ''}`}>
              <span className="ponto" />Nó {regiao}: {!estados ? 'a ligar…' : meuNoEmBaixo ? 'EM BAIXO' : 'activo'}
            </span>
            <span className={`pastilha ${!meuFragmento || meuFragmento.emUso !== 'PRIMARIO' ? 'aviso' : ''}`}>
              <span className="ponto" />BD: {!meuFragmento ? '…' : meuFragmento.emUso === 'PRIMARIO' ? 'primário' : 'réplica promovida'}
            </span>
          </div>
        )}
        <nav>
          {ECRAS.map(([id, nome, I]) => (
            <button key={id} className={ecra === id ? 'activo' : ''} onClick={() => setEcra(id)}>
              <I size={20} />{nome}
              {id === 'pedidos' && pendentes > 0 && <span className="contador">{pendentes}</span>}
            </button>
          ))}
        </nav>
        <div className="rodape">SCAD — Sistema de Compras Automáticas Distribuídas</div>
      </aside>

      <div className="conteudo">
        <div className="topo">{tema.nome} <span>/</span> <b>{nomeEcra}</b></div>
        {meuNoEmBaixo && (
          <div className="faixa-erro">⚠ Nó {regiao} em baixo — a tentar reconectar… (os dados abaixo vêm dos outros
            nós e do fragmento {regiao} da base de dados; não é possível registar saídas)</div>
        )}
        <main>
          {ecra !== 'monitor' && <Resumo regiao={regiao} vivos={vivos} stock={stock} ordens={ordens} loja={resumoLoja} />}
          {ecra === 'armazem' && <EcraArmazem regiao={regiao} vivos={vivos} stock={stock} movimentos={movimentos} />}
          {ecra === 'pedidos' && <EcraPedidos regiao={regiao} vivos={vivos} pedidos={pedidos}
                                              recarregar={() => { recarregarPedidos(); recarregarResumo(); }} />}
          {ecra === 'ordens' && <EcraOrdens regiao={regiao} vivos={vivos} ordens={ordens} recarregar={recarregarOrdens} />}
          {ecra === 'movimentos' && <EcraMovimentos regiao={regiao} movimentos={movimentos} />}
          {ecra === 'ficheiros' && <EcraFicheiros regiao={regiao} vivos={vivos} ordens={ordens} />}
          {ecra === 'fornecedor' && <EcraFornecedor vivos={vivos} estado={forn} erro={erroForn} />}
          {ecra === 'monitor' && <EcraMonitor estados={estados} fragmentos={fragmentos} fornecedor={erroForn ? null : forn} />}
        </main>
      </div>
      <Notificacoes lista={notificacoes} />
    </div>
  );
}
