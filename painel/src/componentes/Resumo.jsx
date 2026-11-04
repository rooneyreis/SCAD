import React from 'react';
import { AlertTriangle, Clock, PackagePlus, Send, Server } from 'lucide-react';
import { abaixoDoMinimo } from '../tema.js';

function Kpi({ icone: Icone, cor, valor, rotulo, nota }) {
  return (
    <div className="kpi">
      <div className="icone" style={{ background: 'var(--cartao2)', color: cor }}><Icone size={24} /></div>
      <div><div className="valor">{valor}</div><div className="rotulo">{rotulo}</div>{nota && <div className="nota">{nota}</div>}</div>
    </div>
  );
}

/** Cartões de resumo no topo de todos os ecrãs. */
export default function Resumo({ regiao, vivos, stock, ordens }) {
  const linhas = (stock && stock.stock) || [];
  const meus = regiao ? linhas.filter((l) => l.armazemId === regiao) : linhas;
  const os = ordens || [];
  const abaixo = meus.filter(abaixoDoMinimo);
  const comEncomenda = abaixo.filter((l) => l.emEncomenda > 0).length;
  const minhasOrdens = regiao ? os.filter((o) => o.linhas[regiao]) : os;
  return (
    <div className="resumo">
      <Kpi icone={AlertTriangle} cor="#ef4444" valor={abaixo.length}
           rotulo={regiao ? `produtos abaixo do mínimo (${regiao})` : 'produtos abaixo do mínimo'}
           nota={abaixo.length > 0 && `${comEncomenda} já com encomenda a caminho`} />
      <Kpi icone={Clock} cor="#eab308" valor={minhasOrdens.filter((o) => o.estado === 'PENDENTE_ENVIO').length}
           rotulo="ordens pendentes de envio" />
      <Kpi icone={Send} cor="var(--destaque)" valor={minhasOrdens.filter((o) => o.estado === 'ENVIADA').length}
           rotulo="ordens enviadas (a caminho)" />
      {regiao
        ? <Kpi icone={PackagePlus} cor="var(--destaque)" valor={meus.reduce((t, l) => t + l.emEncomenda, 0)}
               rotulo={`unidades em encomenda para o ${regiao}`} />
        : <Kpi icone={Server} cor={vivos.length === 3 ? '#22c55e' : '#ef4444'} valor={`${vivos.length}/3`}
               rotulo="nós activos" />}
    </div>
  );
}
