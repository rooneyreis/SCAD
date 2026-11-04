import React from 'react';
import { AlertTriangle, Clock, Send, Server } from 'lucide-react';

function Kpi({ icone: Icone, cor, valor, rotulo }) {
  return (
    <div className="kpi">
      <div className="icone" style={{ background: `${cor}1a`, color: cor }}><Icone size={24} /></div>
      <div><div className="valor">{valor}</div><div className="rotulo">{rotulo}</div></div>
    </div>
  );
}

/** Cartões de resumo no topo de todos os ecrãs. */
export default function Resumo({ regiao, vivos, stock, ordens }) {
  const linhas = (stock && stock.stock) || [];
  const abaixo = (l) => l.quantidade + l.emEncomenda < l.minimo || l.quantidade === 0;
  const meus = regiao ? linhas.filter((l) => l.armazemId === regiao) : linhas;
  const os = ordens || [];
  const minhasOrdens = regiao ? os.filter((o) => o.linhas[regiao]) : os;
  return (
    <div className="resumo">
      <Kpi icone={AlertTriangle} cor="#dc2626" valor={meus.filter(abaixo).length}
           rotulo={regiao ? `produtos abaixo do mínimo (${regiao})` : 'produtos abaixo do mínimo'} />
      <Kpi icone={Clock} cor="#d97706" valor={minhasOrdens.filter((o) => o.estado === 'PENDENTE_ENVIO').length}
           rotulo="ordens pendentes de envio" />
      <Kpi icone={Send} cor="#2563eb" valor={minhasOrdens.filter((o) => o.estado === 'ENVIADA').length}
           rotulo="ordens enviadas (a caminho)" />
      <Kpi icone={Server} cor={vivos.length === 3 ? '#16a34a' : '#dc2626'} valor={`${vivos.length}/3`}
           rotulo="nós activos" />
    </div>
  );
}
