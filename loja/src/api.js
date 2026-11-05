// API pública da loja, através do proxy (/api/<região>/loja/...). Sem contas nem pagamento real.

export const CIDADES = [
  { regiao: 'SUL', cidade: 'Maputo' },
  { regiao: 'CENTRO', cidade: 'Beira' },
  { regiao: 'NORTE', cidade: 'Nampula' },
];

const base = (regiao) => `/api/${regiao.toLowerCase()}/loja`;

async function pedido(url, opcoes = {}, timeoutMs = 5000) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const r = await fetch(url, { ...opcoes, signal: ctrl.signal });
    const texto = await r.text();
    let corpo = null;
    try { corpo = texto ? JSON.parse(texto) : null; } catch (e) { /* resposta do proxy (ex.: 502) */ }
    if (!r.ok) {
      const erro = new Error((corpo && (corpo.detail || corpo.title)) || `O serviço não respondeu (HTTP ${r.status})`);
      erro.estado = r.status;
      erro.corpo = corpo;
      throw erro;
    }
    return corpo;
  } catch (e) {
    if (e.name === 'AbortError') throw new Error('O armazém não respondeu a tempo. Tente de novo.');
    throw e;
  } finally {
    clearTimeout(t);
  }
}

/** Lê de qualquer armazém que responda (o catálogo e os pedidos estão nos fragmentos de cada região). */
async function deQualquer(caminho, preferida) {
  const ordem = [...CIDADES.map((c) => c.regiao)].sort((a, b) => (a === preferida ? -1 : b === preferida ? 1 : 0));
  let ultimo;
  for (const r of ordem) {
    try {
      return await pedido(`${base(r)}${caminho}`, {}, 2500);
    } catch (e) {
      if (e.estado === 404) throw e;
      ultimo = e;
    }
  }
  throw ultimo;
}

/** Cidades (aberta / indisponível de momento) com produtos, preço e disponível. */
export const catalogo = (preferida) => deQualquer('/catalogo', preferida);

/** A compra vai sempre ao nó da cidade escolhida (é lá que o stock é reservado). */
export const comprar = (regiao, encomenda) => pedido(`${base(regiao)}/pedidos`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(encomenda),
}, 15000);

const regiaoDoPedido = (id) => id.split('-')[1];

export const consultarPedido = (id) => deQualquer(`/pedidos/${encodeURIComponent(id)}`, regiaoDoPedido(id));

export const urlRecibo = (id) => `${base(regiaoDoPedido(id))}/pedidos/${encodeURIComponent(id)}/recibo`;

export const mzn = (v) => `${Number(v).toLocaleString('pt-PT', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} MZN`;

/** Pedidos feitos neste navegador (não há contas: guardam-se localmente). */
export const meusPedidos = {
  ler: () => JSON.parse(localStorage.getItem('scad-loja-pedidos') || '[]'),
  guardar: (id) => localStorage.setItem('scad-loja-pedidos',
    JSON.stringify([id, ...meusPedidos.ler().filter((x) => x !== id)].slice(0, 30))),
};
