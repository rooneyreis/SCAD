// Identidade visual de cada painel: cor de destaque e nome do armazém.
export const TEMA = {
  NORTE: { cor: '#0e7490', claro: '#e0f2f7', nome: 'Armazém NORTE', cidade: 'Nampula' },
  CENTRO: { cor: '#7c3aed', claro: '#efe7fd', nome: 'Armazém CENTRO', cidade: 'Beira' },
  SUL: { cor: '#ea580c', claro: '#fdeee4', nome: 'Armazém SUL', cidade: 'Maputo' },
  GERAL: { cor: '#334155', claro: '#e9edf2', nome: 'SCAD — vista geral', cidade: 'todos os armazéns' },
};

export const COR_NIVEL = { NORMAL: '#16a34a', AVISO: '#d97706', CRITICO: '#dc2626', RUPTURA: '#991b1b' };

export const hora = (iso) => (iso ? new Date(iso).toLocaleTimeString('pt-PT', { hour12: false }) : '');
