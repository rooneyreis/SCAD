// Identidade visual de cada painel: uma cor de destaque e o nome do armazém.
export const TEMA = {
  NORTE: { cor: '#3b82f6', claro: 'rgba(59,130,246,.16)', nome: 'Armazém NORTE', cidade: 'Nampula' },
  CENTRO: { cor: '#a855f7', claro: 'rgba(168,85,247,.16)', nome: 'Armazém CENTRO', cidade: 'Beira' },
  SUL: { cor: '#f59e0b', claro: 'rgba(245,158,11,.16)', nome: 'Armazém SUL', cidade: 'Maputo' },
  GERAL: { cor: '#22d3ee', claro: 'rgba(34,211,238,.14)', nome: 'SCAD', cidade: 'vista geral' },
};

// Verde/amarelo/vermelho só para os estados de stock
export const COR_NIVEL = { NORMAL: '#22c55e', AVISO: '#eab308', CRITICO: '#ef4444', RUPTURA: '#dc2626' };

// Estilo comum dos gráficos (tema escuro)
export const GRAFICO = {
  grelha: '#2a2f3a',
  eixo: '#a3abb8',
  tooltip: { background: '#1b1f27', border: '1px solid #343a46', borderRadius: 10, color: '#e5e7eb' },
};

export const hora = (iso) => (iso ? new Date(iso).toLocaleTimeString('pt-PT', { hour12: false }) : '');

/**
 * Nível da prateleira: só conta a quantidade física, ignorando o que já está encomendado.
 * (O nível do backend conta quantidade + em encomenda, porque serve para decidir se é preciso
 * encomendar; aqui é para mostrar o estado real do armazém.) Mesmos limiares: mínimo e 1,2 × mínimo.
 */
export function nivelPrateleira(l) {
  if (l.quantidade === 0) return 'RUPTURA';
  if (l.quantidade < l.minimo) return 'CRITICO';
  if (5 * l.quantidade <= 6 * l.minimo) return 'AVISO';
  return 'NORMAL';
}
export const abaixoDoMinimo = (l) => nivelPrateleira(l) === 'CRITICO' || nivelPrateleira(l) === 'RUPTURA';

/** Valor em meticais: 1 450,00 MZN. */
export const mzn = (v) => (v == null ? '—'
  : `${Number(v).toLocaleString('pt-PT', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} MZN`);
