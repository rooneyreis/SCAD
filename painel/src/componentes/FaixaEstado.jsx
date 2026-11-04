import React from 'react';
import { REGIOES } from '../api.js';

function Pastilha({ estado, texto, detalhe }) {
  return (
    <span className={`pastilha ${estado}`} title={detalhe || ''}>
      <span className="ponto" />
      {texto}
      {detalhe && <small>{detalhe}</small>}
    </span>
  );
}

/** Faixa fixa no topo: estado de cada componente do sistema. */
export default function FaixaEstado({ estados, fragmentos, fornecedor }) {
  const nos = estados || {};
  const vivos = REGIOES.filter((r) => nos[r]);
  const centralOk = vivos.some((r) => nos[r].centralDisponivel);
  const hdfsOk = vivos.some((r) => nos[r].hdfsDisponivel);
  const frag = (fragmentos && fragmentos.fragmentos) || [];

  return (
    <header className="faixa">
      <h1>SCAD</h1>
      {REGIOES.map((r) => (
        <Pastilha key={r} estado={nos[r] ? 'ok' : 'falha'} texto={`Nó ${r}`}
                  detalhe={nos[r] ? `L=${nos[r].lamport}` : 'EM BAIXO'} />
      ))}
      <span className="separador" />
      <Pastilha estado={vivos.length === 0 ? 'aviso' : centralOk ? 'ok' : 'falha'} texto="Central CORBA"
                detalhe={vivos.length > 0 && !centralOk ? 'EM BAIXO' : ''} />
      <Pastilha estado={vivos.length === 0 ? 'aviso' : hdfsOk ? 'ok' : 'falha'} texto="HDFS"
                detalhe={vivos.length > 0 && !hdfsOk ? 'EM BAIXO' : ''} />
      <Pastilha estado={!fornecedor ? 'falha' : fornecedor.modo === 'NORMAL' ? 'ok' : 'aviso'} texto="Fornecedor"
                detalhe={fornecedor ? fornecedor.modo : 'EM BAIXO'} />
      <span className="separador" />
      {frag.map((f) => (
        <Pastilha key={f.regiao} estado={f.emUso === 'PRIMARIO' ? 'ok' : 'aviso'} texto={`BD ${f.regiao}`}
                  detalhe={f.emUso === 'PRIMARIO' ? 'primário' : 'RÉPLICA promovida'} />
      ))}
    </header>
  );
}
