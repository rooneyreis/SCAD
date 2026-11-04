import React from 'react';
import { Warehouse, LayoutDashboard } from 'lucide-react';
import { REGIOES } from '../api.js';

function Pastilha({ estado, texto, detalhe }) {
  return (
    <span className={`pastilha ${estado}`}>
      <span className="ponto" />{texto}{detalhe && <small>{detalhe}</small>}
    </span>
  );
}

/** Cabeçalho: nome do armazém deste painel (com a cor da região) e estado de todo o sistema. */
export default function Cabecalho({ regiao, tema, estados, fragmentos, fornecedor }) {
  const nos = estados || {};
  const vivos = REGIOES.filter((r) => nos[r]);
  const centralOk = vivos.some((r) => nos[r].centralDisponivel);
  const hdfsOk = vivos.some((r) => nos[r].hdfsDisponivel);
  const frag = (fragmentos && fragmentos.fragmentos) || [];
  const Icone = regiao ? Warehouse : LayoutDashboard;

  return (
    <header className="cabecalho">
      <div className="topo">
        <div className="titulo">
          <div className="icone"><Icone size={26} /></div>
          <div>
            <h1>{tema.nome}</h1>
            <div className="subtitulo">{tema.cidade} · SCAD — Sistema de Compras Automáticas Distribuídas</div>
          </div>
        </div>
        <div className="estado">
          {REGIOES.map((r) => (
            <Pastilha key={r} estado={nos[r] ? '' : 'falha'} texto={`Nó ${r}`}
                      detalhe={nos[r] ? `L=${nos[r].lamport}` : 'EM BAIXO'} />
          ))}
          <Pastilha estado={vivos.length === 0 ? 'aviso' : centralOk ? '' : 'falha'} texto="Central CORBA"
                    detalhe={vivos.length > 0 && !centralOk ? 'EM BAIXO' : ''} />
          <Pastilha estado={vivos.length === 0 ? 'aviso' : hdfsOk ? '' : 'falha'} texto="HDFS"
                    detalhe={vivos.length > 0 && !hdfsOk ? 'EM BAIXO' : ''} />
          <Pastilha estado={!fornecedor ? 'falha' : fornecedor.modo === 'NORMAL' ? '' : 'aviso'} texto="Fornecedor"
                    detalhe={fornecedor ? (fornecedor.modo === 'NORMAL' ? '' : fornecedor.modo) : 'EM BAIXO'} />
          {frag.map((f) => (
            <Pastilha key={f.regiao} estado={f.emUso === 'PRIMARIO' ? '' : 'aviso'} texto={`BD ${f.regiao}`}
                      detalhe={f.emUso === 'PRIMARIO' ? 'primário' : 'RÉPLICA'} />
          ))}
        </div>
      </div>
    </header>
  );
}
