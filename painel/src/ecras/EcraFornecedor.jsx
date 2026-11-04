import React, { useState } from 'react';
import { REGIOES, deTodosOsNos, fornecedor } from '../api.js';
import { usePolling } from '../usePolling.js';

const MODOS = [
  ['NORMAL', 'Normal', ''],
  ['EM_BAIXO', 'Em baixo', 'responde 503 / SOAP Fault: as ordens ficam PENDENTE_ENVIO'],
  ['LENTO', 'Lento (8 s)', 'mais do que o timeout de 4 s: testa timeouts e idempotência'],
  ['ERRO', 'Com erros', 'erro interno 500 / SOAP Fault'],
];

export default function EcraFornecedor({ estado, erro }) {
  const [msg, setMsg] = useState(null);
  const [eventos] = usePolling(() => deTodosOsNos('/fornecedores/eventos'), 2000);

  const mudar = async (modo) => {
    try {
      await fornecedor.modo(modo);
      setMsg({ ok: true, texto: `Fornecedor em modo ${modo}.` });
    } catch (e) {
      setMsg({ ok: false, texto: e.message });
    }
  };

  const todos = REGIOES.flatMap((r) => ((eventos && eventos[r]) || []).map((e) => `[${r}] ${e}`))
    .sort((a, b) => b.slice(b.indexOf(']') + 2).localeCompare(a.slice(a.indexOf(']') + 2))).slice(0, 30);

  return (
    <>
      <div className="cartao">
        <h2>Fornecedor simulado (REST /api/v1/ordens · SOAP /ws/fornecedor)</h2>
        {erro && <div className="mensagem erro">Fornecedor inacessível: {erro}</div>}
        {estado && (
          <p>Modo actual: <span className={`etiqueta ${estado.modo}`}>{estado.modo}</span>
            {' '}· pedidos recebidos <b>{estado.pedidosRecebidos}</b> · ordens únicas <b>{estado.ordensUnicas}</b>
            {' '}· duplicados ignorados (idempotência) <b>{estado.duplicadosIgnorados}</b> · rejeitados <b>{estado.rejeitados}</b></p>
        )}
        <div className="linha">
          {MODOS.map(([m, nome, ajuda]) => (
            <button key={m} title={ajuda} className={`botao grande ${estado && estado.modo === m ? '' : 'secundario'}`}
                    onClick={() => mudar(m)}>{nome}</button>))}
        </div>
        {msg && <div className={`mensagem ${msg.ok ? 'sucesso' : 'erro'}`}>{msg.texto}</div>}
        <p className="suave">WSDL (no PC da demo):{' '}
          <a href={`http://${window.location.hostname}:8090/ws/fornecedor?wsdl`} target="_blank" rel="noreferrer">
            :8090/ws/fornecedor?wsdl</a></p>
      </div>
      <div className="grelha">
        <div className="cartao">
          <h2>Gateway dos nós (tentativas, timeouts, reenvios)</h2>
          <pre className="eventos" style={{ maxHeight: 420 }}>{todos.join('\n') || 'sem envios ainda'}</pre>
        </div>
        <div className="cartao">
          <h2>Registo do fornecedor</h2>
          <pre className="eventos" style={{ maxHeight: 420 }}>{estado ? estado.registo.join('\n') : ''}</pre>
        </div>
      </div>
    </>
  );
}
