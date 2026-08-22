"use client";

import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

interface AreaTrendChartProps {
  labels: string[];
  data: number[];
  formatValue: (value: number) => string;
  height?: number;
}

const compact = (value: number) => new Intl.NumberFormat("pt-BR", { notation: "compact", maximumFractionDigits: 1 }).format(value);

export function AreaTrendChart({ labels, data, formatValue, height = 245 }: AreaTrendChartProps) {
  const chartData = labels.map((name, index) => ({ name, value: data[index] ?? 0 }));

  return (
    <div className="dashboard-chart-canvas" style={{ width: "100%", height }}>
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={chartData} margin={{ top: 12, right: 8, left: -12, bottom: 0 }}>
          <defs>
            <linearGradient id="revenue-area" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#22c55e" stopOpacity={.42} />
              <stop offset="72%" stopColor="#22c55e" stopOpacity={.08} />
              <stop offset="100%" stopColor="#22c55e" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke="var(--border-subtle)" strokeDasharray="3 5" vertical={false} />
          <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: "var(--foreground-subtle)", fontSize: 9 }} />
          <YAxis tickFormatter={compact} axisLine={false} tickLine={false} tick={{ fill: "var(--foreground-subtle)", fontSize: 9 }} />
          <Tooltip content={({ active, payload, label }) => active && payload?.length ? <div style={{ border: "1px solid rgba(34,197,94,.28)", borderRadius: 8, background: "var(--surface-overlay)", padding: "9px 11px", boxShadow: "0 14px 34px rgba(0,0,0,.35)" }}><p style={{ margin: 0, fontSize: 9, color: "var(--foreground-muted)" }}>{label}</p><strong style={{ display: "block", marginTop: 3, color: "#4ade80", fontSize: 13 }}>{formatValue(Number(payload[0].value ?? 0))}</strong></div> : null} />
          <Area type="monotone" dataKey="value" stroke="#22c55e" strokeWidth={2.4} fill="url(#revenue-area)" activeDot={{ r: 4, fill: "#22c55e", stroke: "var(--surface-elevated)", strokeWidth: 2 }} animationDuration={850} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
