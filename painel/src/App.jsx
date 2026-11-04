import React, { useState } from 'react';
import { REGIOES, deQualquerNo, deTodosOsNos, fornecedor } from './api.js';
import { usePolling } from './usePolling.js';
import FaixaEstado from './componentes/FaixaEstado.jsx';
import EcraStock from './ecras/EcraStock.jsx';
import EcraOrdens from './ecras/EcraOrdens.jsx';
import EcraMonitor from './ecras/EcraMonitor.jsx';
import EcraFicheiros from './ecras/EcraFicheiros.jsx';
import EcraFornecedor from './ecras/EcraFornecedor.jsx';
import EcraMovimentos from './ecras/EcraMovimentos.jsx';

const ECRAS = [
  ['stock', 'Stock'],
  ['ordens', 'Ordens de compra'],
  ['monitor', 'Monitor distribuído'],
  ['ficheiros', 'Ficheiros (HDFS)'],
  ['fornecedor', 'Fornecedor'],
  ['movimentos', 'Movimentos'],
];

export default function App() {
  // O separador activo fica no endereço (#monitor, #ordens...): pode abrir-se directamente
  const inicial = window.location.hash.slice(1);
  const [ecra, setEcraEstado] = useState(ECRAS.some(([id]) => id === inicial) ? inicial : 'stock');
  const setEcra = (id) => {
    window.location.hash = id;
    setEcraEstado(id);
  };

  // Estado dos 3 nós a cada segundo: quem responde está vivo
  const [estados] = usePolling(() => deTodosOsNos('/estado'), 1000);
  const vivos = REGIOES.filter((r) => estados && estados[r]);

  const [fragmentos] = usePolling(() => deQualquerNo('/fragmentos', vivos), 2000, [vivos.join()]);
  const [forn, erroForn] = usePolling(() => fornecedor.estado(), 2000);

  return (
    <>
      <FaixaEstado estados={estados} fragmentos={fragmentos} fornecedor={erroForn ? null : forn} />
      <nav className="separadores">
        {ECRAS.map(([id, nome]) => (
          <button key={id} className={ecra === id ? 'activo' : ''} onClick={() => setEcra(id)}>{nome}</button>
        ))}
      </nav>
      <main>
        {ecra === 'stock' && <EcraStock vivos={vivos} />}
        {ecra === 'ordens' && <EcraOrdens vivos={vivos} />}
        {ecra === 'monitor' && <EcraMonitor vivos={vivos} fragmentos={fragmentos} estados={estados} />}
        {ecra === 'ficheiros' && <EcraFicheiros vivos={vivos} />}
        {ecra === 'fornecedor' && <EcraFornecedor vivos={vivos} estado={forn} erro={erroForn} />}
        {ecra === 'movimentos' && <EcraMovimentos vivos={vivos} />}
      </main>
    </>
  );
}
