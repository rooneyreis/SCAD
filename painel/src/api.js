// Acesso à API dos nós, através do proxy do painel (/api/norte, /api/centro, /api/sul).

export const REGIOES = ['NORTE', 'CENTRO', 'SUL'];

const base = (regiao) => `/api/${regiao.toLowerCase()}`;

async function pedido(url, opcoes = {}, timeoutMs = 4000) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const r = await fetch(url, { ...opcoes, signal: ctrl.signal });
    const texto = await r.text();
    const corpo = texto ? JSON.parse(texto) : null;
    if (!r.ok) {
      throw new Error((corpo && (corpo.detail || corpo.title)) || `HTTP ${r.status}`);
    }
    return corpo;
  } catch (e) {
    throw new Error(e.name === 'AbortError' ? 'sem resposta (timeout)' : e.message);
  } finally {
    clearTimeout(t);
  }
}

/** GET a um nó concreto. */
export const doNo = (regiao, caminho, timeoutMs) => pedido(`${base(regiao)}${caminho}`, {}, timeoutMs);

/** POST a um nó concreto. */
export const paraNo = (regiao, caminho, corpo, timeoutMs = 15000) =>
  pedido(`${base(regiao)}${caminho}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
  }, timeoutMs);

/**
 * GET ao primeiro nó que responder (os nós vivos primeiro). As vistas globais (stock, ordens...)
 * são lidas dos fragmentos, por isso qualquer nó serve: o painel funciona com nós em baixo.
 */
export async function deQualquerNo(caminho, vivos = REGIOES) {
  const ordem = [...vivos, ...REGIOES.filter((r) => !vivos.includes(r))];
  let ultimoErro;
  for (const r of ordem) {
    try {
      return await doNo(r, caminho, 3000);
    } catch (e) {
      ultimoErro = e;
    }
  }
  throw ultimoErro || new Error('nenhum nó respondeu');
}

/** GET a todos os nós; devolve { REGIAO: dados | null }. */
export async function deTodosOsNos(caminho) {
  const res = await Promise.allSettled(REGIOES.map((r) => doNo(r, caminho, 2500)));
  return Object.fromEntries(REGIOES.map((r, i) => [r, res[i].status === 'fulfilled' ? res[i].value : null]));
}

export const fornecedor = {
  estado: () => pedido('/api/fornecedor/controlo', {}, 2500),
  modo: (modo) => pedido('/api/fornecedor/controlo', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ modo }),
  }),
};

/** URL de download de um ficheiro do HDFS (através de um nó vivo). */
export const urlFicheiro = (regiao, caminho) =>
  `${base(regiao)}/ficheiros/conteudo?caminho=${encodeURIComponent(caminho)}`;
