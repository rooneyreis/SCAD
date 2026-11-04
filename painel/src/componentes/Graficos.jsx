import React from 'react';
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, ComposedChart, Legend, Line, ReferenceLine,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { COR_NIVEL, GRAFICO, hora } from '../tema.js';

const eixo = { tick: { fontSize: 13, fill: GRAFICO.eixo }, stroke: GRAFICO.grelha };
const dica = { contentStyle: GRAFICO.tooltip, labelStyle: { color: '#e5e7eb' }, itemStyle: { color: '#e5e7eb' },
  cursor: { fill: 'rgba(255,255,255,.04)' } };
const legenda = { wrapperStyle: { fontSize: 14, color: GRAFICO.eixo } };

/** Nível de stock por produto: barras arredondadas (cor do nível) + em encomenda + traço do mínimo. */
export function GraficoNivelStock({ linhas }) {
  const dados = linhas.map((l) => ({
    produto: l.produtoId, quantidade: l.quantidade, encomenda: l.emEncomenda, minimo: l.minimo, nivel: l.nivel,
  }));
  return (
    <ResponsiveContainer width="100%" height={310}>
      <ComposedChart data={dados} margin={{ top: 10, right: 10, left: -6, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={GRAFICO.grelha} />
        <XAxis dataKey="produto" interval={0} {...eixo} />
        <YAxis {...eixo} />
        <Tooltip {...dica} />
        <Legend {...legenda} />
        <Bar dataKey="quantidade" name="Quantidade" stackId="s" radius={[0, 0, 6, 6]}>
          {dados.map((d) => <Cell key={d.produto} fill={COR_NIVEL[d.nivel]} />)}
        </Bar>
        <Bar dataKey="encomenda" name="Em encomenda" stackId="s" fill="var(--destaque)" fillOpacity={0.55} radius={[8, 8, 0, 0]} />
        {/* Mínimo de cada produto: um traço tracejado sobre a barra */}
        <Line dataKey="minimo" name="Mínimo" stroke="#e8eaee" strokeWidth={0} legendType="plainline"
              activeDot={false} isAnimationActive={false}
              dot={(p) => (
                <line key={p.index} x1={p.cx - 26} x2={p.cx + 26} y1={p.cy} y2={p.cy}
                      stroke="#e8eaee" strokeWidth={3} strokeDasharray="5 3" />
              )} />
      </ComposedChart>
    </ResponsiveContainer>
  );
}

/**
 * Evolução do stock de um produto ("dente de serra"), reconstruída a partir dos movimentos:
 * partindo da quantidade actual e andando para trás em cada movimento. Área com gradiente.
 */
export function GraficoEvolucao({ movimentos, actual, minimo, maximo }) {
  const ordenados = [...movimentos].sort((a, b) => a.tsCorrigido.localeCompare(b.tsCorrigido));
  const pontos = [];
  let q = actual;
  for (let i = ordenados.length - 1; i >= 0; i--) {
    const m = ordenados[i];
    pontos.unshift({ t: new Date(m.tsCorrigido).getTime(), quantidade: q, motivo: `${m.tipo} ${m.quantidade}` });
    q = m.tipo === 'SAIDA' ? q + m.quantidade : q - m.quantidade;
  }
  const inicio = pontos.length ? pontos[0].t - 30_000 : Date.now() - 60_000;
  pontos.unshift({ t: inicio, quantidade: q, motivo: 'início' });
  pontos.push({ t: Date.now(), quantidade: actual, motivo: 'agora' });

  return (
    <ResponsiveContainer width="100%" height={310}>
      <AreaChart data={pontos} margin={{ top: 10, right: 20, left: -6, bottom: 0 }}>
        <defs>
          <linearGradient id="gradStock" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--destaque)" stopOpacity={0.45} />
            <stop offset="100%" stopColor="var(--destaque)" stopOpacity={0.02} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke={GRAFICO.grelha} />
        <XAxis dataKey="t" type="number" scale="time" domain={['dataMin', 'dataMax']}
               tickFormatter={(t) => hora(new Date(t).toISOString())} {...eixo} />
        <YAxis domain={[0, (dataMax) => Math.max(dataMax, maximo || 0)]} {...eixo} />
        <Tooltip {...dica} labelFormatter={(t) => hora(new Date(t).toISOString())}
                 formatter={(v, n, p) => [v, `quantidade (${p.payload.motivo})`]} />
        <ReferenceLine y={minimo} stroke="#ef4444" strokeDasharray="6 4" strokeWidth={2}
                       label={{ value: `mínimo ${minimo}`, position: 'insideTopRight', fill: '#fca5a5', fontSize: 13 }} />
        {maximo && <ReferenceLine y={maximo} stroke="#6b7280" strokeDasharray="2 4"
                                  label={{ value: `máximo ${maximo}`, position: 'insideTopRight', fill: GRAFICO.eixo, fontSize: 13 }} />}
        <Area type="stepAfter" dataKey="quantidade" stroke="var(--destaque)" strokeWidth={3} fill="url(#gradStock)"
              dot={{ r: 4, fill: 'var(--destaque)', stroke: '#0d0f13', strokeWidth: 2 }} />
      </AreaChart>
    </ResponsiveContainer>
  );
}

/** Desvio de cada relógio antes e depois da última ronda de Berkeley. */
export function GraficoRelogios({ dados }) {
  return (
    <ResponsiveContainer width="100%" height={270}>
      <BarChart data={dados} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={GRAFICO.grelha} />
        <XAxis dataKey="no" {...eixo} />
        <YAxis unit=" ms" width={76} {...eixo} />
        <Tooltip {...dica} formatter={(v) => `${v} ms`} />
        <Legend {...legenda} />
        <ReferenceLine y={0} stroke="#6b7280" />
        <Bar dataKey="antes" name="Antes da ronda" fill="#6b7280" radius={[8, 8, 8, 8]} />
        <Bar dataKey="depois" name="Depois da ronda" fill="var(--destaque)" radius={[8, 8, 8, 8]} />
      </BarChart>
    </ResponsiveContainer>
  );
}
