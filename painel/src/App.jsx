import React, { useEffect, useRef, useState } from 'react';
import { Activity, FileText, Package, Receipt, Truck, ListOrdered } from 'lucide-react';
import { REGIOES, deQualquerNo, doNo, fornecedor, regiaoDoPainel } from './api.js';
import { usePolling } from './usePolling.js';
import { TEMA } from './tema.js';
import Cabecalho from './componentes/Cabecalho.jsx';
import Resumo from './componentes/Resumo.jsx';
import Notificacoes, { useDetectorEventos } from './componentes/Notificacoes.jsx';
import EcraArmazem from './ecras/EcraArmazem.jsx';
import EcraOrdens from './ecras/EcraOrdens.jsx';
import EcraMonitor from './ecras/EcraMonitor.jsx';
import EcraFicheiros from './ecras/EcraFicheiros.jsx';
import EcraFornecedor from './ecras/EcraFornecedor.jsx';
import EcraMovimentos from './ecras/EcraMovimentos.jsx';

const ECRAS = [
  ['armazem', 'Armazém', Package],
  ['ordens', 'Ordens de compra', Receipt],
  ['monitor', 'Monitor distribuído', Activity],
  ['movimentos', 'Movimentos', ListOrdered],
  ['ficheiros', 'Ficheiros HDFS', FileText],
  ['fornecedor', 'Fornecedor', Truck],
];

/**
 * Estado de cada nó, actualizado nó a nó assim que cada um responde (a cada 1 s):
 * um nó em baixo nunca atrasa a informação dos que estão vivos.
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

export default function App() {
  // Região deste painel: 8081 NORTE, 8082 CENTRO, 8083 SUL; 8080 = vista geral (null)
  const [regiao, setRegiao] = useState(undefined);
  useEffect(() => { regiaoDoPainel().then(setRegiao); }, []);

  const inicial = window.location.hash.slice(1);
  const [ecra, setEcraEstado] = useState(ECRAS.some(([id]) => id === inicial) ? inicial : 'armazem');
  const setEcra = (id) => { window.location.hash = id; setEcraEstado(id); };

  // Dados partilhados por todos os ecrãs (lidos do primeiro nó vivo: o painel sobrevive à queda de nós)
  const estados = useEstadosDosNos();
  const vivos = REGIOES.filter((r) => estados && estados[r]);
  const chave = [vivos.join()];
  const [stock] = usePolling(() => deQualquerNo('/global/stock', vivos), 1500, chave);
  const [ordens, , recarregarOrdens] = usePolling(() => deQualquerNo('/ordens', vivos), 2000, chave);
  const [movimentos] = usePolling(() => deQualquerNo('/global/movimentos?limite=300', vivos), 2000, chave);
  const [fragmentos] = usePolling(() => deQualquerNo('/fragmentos', vivos), 2000, chave);
  const [forn, erroForn] = usePolling(() => fornecedor.estado(), 2000);

  const notificacoes = useDetectorEventos({ estados, stock, ordens, fragmentos, regiao });

  // Cor de destaque da região em todo o painel
  const tema = TEMA[regiao || 'GERAL'];
  const raiz = useRef(document.documentElement);
  useEffect(() => {
    raiz.current.style.setProperty('--destaque', tema.cor);
    raiz.current.style.setProperty('--destaque-claro', tema.claro);
    document.title = `SCAD — ${tema.nome}`;
  }, [tema]);

  if (regiao === undefined) return null;
  const meuNoEmBaixo = regiao && estados && !estados[regiao];

  return (
    <>
      <Cabecalho regiao={regiao} tema={tema} estados={estados} fragmentos={fragmentos}
                 fornecedor={erroForn ? null : forn} />
      {meuNoEmBaixo && (
        <div className="faixa-erro">⚠ Nó {regiao} em baixo — este armazém não pode registar saídas;
          os dados abaixo vêm dos outros nós e do fragmento {regiao} da base de dados.</div>
      )}
      <nav className="separadores" style={{ background: '#fff', borderBottom: '1px solid var(--linha)' }}>
        {ECRAS.map(([id, nome, Icone]) => (
          <button key={id} className={ecra === id ? 'activo' : ''} onClick={() => setEcra(id)}>
            <Icone size={17} />{nome}
          </button>
        ))}
      </nav>
      <main>
        <Resumo regiao={regiao} vivos={vivos} stock={stock} ordens={ordens} />
        {ecra === 'armazem' && <EcraArmazem regiao={regiao} vivos={vivos} stock={stock} movimentos={movimentos} />}
        {ecra === 'ordens' && <EcraOrdens regiao={regiao} vivos={vivos} ordens={ordens} recarregar={recarregarOrdens} />}
        {ecra === 'monitor' && <EcraMonitor estados={estados} fragmentos={fragmentos} />}
        {ecra === 'movimentos' && <EcraMovimentos regiao={regiao} movimentos={movimentos} />}
        {ecra === 'ficheiros' && <EcraFicheiros vivos={vivos} />}
        {ecra === 'fornecedor' && <EcraFornecedor vivos={vivos} estado={forn} erro={erroForn} />}
      </main>
      <Notificacoes lista={notificacoes} />
    </>
  );
}
