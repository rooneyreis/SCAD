import React from 'react';
import {
  Bar, BarChart, CartesianGrid, Cell, ComposedChart, Legend, Line, LineChart, ReferenceLine,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { COR_NIVEL, hora } from '../tema.js';

/** Nível de stock por produto: barras (cor do nível) + em encomenda (empilhada) + traço do mínimo. */
export function GraficoNivelStock({ linhas }) {
  const dados = linhas.map((l) => ({
    produto: l.produtoId, quantidade: l.quantidade, encomenda: l.emEncomenda, minimo: l.minimo, nivel: l.nivel,
  }));
  return (
    <ResponsiveContainer width="100%" height={300}>
      <ComposedChart data={dados} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
        <XAxis dataKey="produto" tick={{ fontSize: 12 }} interval={0} />
        <YAxis tick={{ fontSize: 12 }} />
        <Tooltip />
        <Legend wrapperStyle={{ fontSize: 13 }} />
        <Bar dataKey="quantidade" name="Quantidade" stackId="s" radius={[0, 0, 0, 0]}>
          {dados.map((d) => <Cell key={d.produto} fill={COR_NIVEL[d.nivel]} />)}
        </Bar>
        <Bar dataKey="encomenda" name="Em encomenda" stackId="s" fill="#93c5fd" radius={[6, 6, 0, 0]} />
        {/* Mínimo de cada produto: um traço tracejado sobre a barra (sem linha a unir produtos) */}
        <Line dataKey="minimo" name="Mínimo" stroke="#0f172a" strokeWidth={0} legendType="plainline"
              activeDot={false} isAnimationActive={false}
              dot={(p) => (
                <line key={p.index} x1={p.cx - 26} x2={p.cx + 26} y1={p.cy} y2={p.cy}
                      stroke="#0f172a" strokeWidth={3} strokeDasharray="5 3" />
              )} />
      </ComposedChart>
    </ResponsiveContainer>
  );
}

/**
 * Evolução do stock de um produto ao longo do tempo ("dente de serra"), reconstruída a partir dos
 * movimentos: partindo da quantidade actual e andando para trás em cada movimento.
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
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={pontos} margin={{ top: 10, right: 20, left: -10, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
        <XAxis dataKey="t" type="number" scale="time" domain={['dataMin', 'dataMax']}
               tickFormatter={(t) => hora(new Date(t).toISOString())} tick={{ fontSize: 12 }} />
        <YAxis tick={{ fontSize: 12 }} domain={[0, (dataMax) => Math.max(dataMax, maximo || 0)]} />
        <Tooltip labelFormatter={(t) => hora(new Date(t).toISOString())}
                 formatter={(v, n, p) => [v, `quantidade (${p.payload.motivo})`]} />
        <ReferenceLine y={minimo} stroke="#dc2626" strokeDasharray="6 4" strokeWidth={2}
                       label={{ value: `mínimo ${minimo}`, position: 'insideTopRight', fill: '#dc2626', fontSize: 12 }} />
        {maximo && <ReferenceLine y={maximo} stroke="#94a3b8" strokeDasharray="2 4"
                                  label={{ value: `máximo ${maximo}`, position: 'insideTopRight', fill: '#64748b', fontSize: 12 }} />}
        <Line type="stepAfter" dataKey="quantidade" stroke="var(--destaque)" strokeWidth={3} dot={{ r: 3 }} />
      </LineChart>
    </ResponsiveContainer>
  );
}

/** Desvio de cada relógio antes e depois da última ronda de Berkeley. */
export function GraficoRelogios({ dados }) {
  return (
    <ResponsiveContainer width="100%" height={260}>
      <BarChart data={dados} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
        <XAxis dataKey="no" tick={{ fontSize: 13 }} />
        <YAxis tick={{ fontSize: 12 }} unit=" ms" width={70} />
        <Tooltip formatter={(v) => `${v} ms`} />
        <Legend wrapperStyle={{ fontSize: 13 }} />
        <ReferenceLine y={0} stroke="#0f172a" />
        <Bar dataKey="antes" name="Antes da ronda" fill="#f59e0b" radius={[6, 6, 0, 0]} />
        <Bar dataKey="depois" name="Depois da ronda" fill="#16a34a" radius={[6, 6, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}
