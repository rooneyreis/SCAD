import React from 'react';
import { deQualquerNo, urlFicheiro } from '../api.js';
import { usePolling } from '../usePolling.js';

const tamanho = (b) => (b < 1024 ? `${b} B` : `${(b / 1024).toFixed(1)} KB`);
const tipo = (c) => c.split('/')[2] || '';

export default function EcraFicheiros({ vivos }) {
  const [dados, erro] = usePolling(() => deQualquerNo('/ficheiros', vivos), 3000, [vivos.join()]);
  const no = vivos[0] || 'NORTE';
  const ficheiros = (dados && dados.ficheiros) || [];
  return (
    <div className="cartao">
      <h2>Ficheiros no HDFS (/scad)</h2>
      {erro && !dados && <div className="mensagem erro">{erro}</div>}
      {dados && (
        <p>
          HDFS: {dados.hdfsDisponivel ? <span className="etiqueta VIVO">DISPONÍVEL</span>
            : <span className="etiqueta MORTO">INDISPONÍVEL</span>}
          {' '}· fila local do nó {no}: <b>{dados.filaLocalPendentes}</b> ficheiro(s) à espera de envio
          {' '}<span className="suave">(NameNode = ponto único de falha; os DataNodes têm replicação 3)</span>
        </p>
      )}
      <table>
        <thead><tr><th>Tipo</th><th>Caminho</th><th className="num">Tamanho</th><th>Modificado</th>
          <th className="num">Réplicas</th><th /></tr></thead>
        <tbody>
          {ficheiros.map((f) => (
            <tr key={f.caminho}>
              <td><span className="etiqueta cinza">{tipo(f.caminho)}</span></td>
              <td className="mono">{f.caminho}</td>
              <td className="num">{tamanho(f.tamanho)}</td>
              <td>{new Date(f.modificadoEm).toLocaleString('pt-PT')}</td>
              <td className="num"><span className={`etiqueta ${f.replicasVivas >= f.replicacao ? 'SIM' : 'AVISO'}`}>
                {f.replicasVivas}/{f.replicacao}</span></td>
              <td><a href={urlFicheiro(no, f.caminho)} target="_blank" rel="noreferrer">abrir</a></td>
            </tr>))}
        </tbody>
      </table>
    </div>
  );
}
