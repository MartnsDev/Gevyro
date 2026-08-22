"use client";

import { Bar, BarChart as RechartsBarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

interface BarChartProps {
  labels: string[];
  data: number[];
  label: string;
  color?: "blue" | "green" | "purple" | "orange" | "rose" | "cyan";
  formatValue?: (value: number) => string;
  height?: number;
  horizontal?: boolean;
}

const COLORS = { blue: "#3b82f6", green: "#10b981", purple: "#8b5cf6", orange: "#f59e0b", rose: "#f43f5e", cyan: "#06b6d4" };
const compact = (value: number) => new Intl.NumberFormat("pt-BR", { notation: "compact", maximumFractionDigits: 1 }).format(value);

export function BarChart({ labels, data, label, color = "blue", formatValue = compact, height = 260, horizontal = false }: BarChartProps) {
  const chartData = labels.map((name, index) => ({ name, value: data[index] ?? 0 }));
  const total = data.reduce((sum, value) => sum + value, 0);
  const accent = COLORS[color];

  return (
    <div style={{ width: "100%", minWidth: 0 }}>
      <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: 16, marginBottom: 20 }}>
        <div><p style={{ margin: 0, color: "var(--foreground-subtle)", fontSize: 10, fontWeight: 700, letterSpacing: ".1em", textTransform: "uppercase" }}>{label}</p><p style={{ margin: "5px 0 0", color: "var(--foreground)", fontSize: 24, lineHeight: 1, fontWeight: 750, letterSpacing: "-.035em" }}>{formatValue(total)}</p></div>
        <span style={{ color: "var(--foreground-subtle)", fontSize: 11 }}>Total acumulado</span>
      </div>
      <div className="dashboard-chart-canvas" style={{ width: "100%", height }}>
        <ResponsiveContainer width="100%" height="100%">
          <RechartsBarChart data={chartData} layout={horizontal ? "vertical" : "horizontal"} margin={horizontal ? { top: 4, right: 24, bottom: 0, left: 8 } : { top: 8, right: 8, bottom: 0, left: -14 }} barCategoryGap={horizontal ? "28%" : "34%"}>
            <defs><linearGradient id={`bar-${color}`} x1="0" y1="0" x2={horizontal ? "1" : "0"} y2={horizontal ? "0" : "1"}><stop offset="0%" stopColor={accent} /><stop offset="100%" stopColor={accent} stopOpacity={0.45} /></linearGradient></defs>
            <CartesianGrid stroke="var(--border-subtle)" strokeDasharray="3 5" vertical={horizontal} horizontal={!horizontal} />
            {horizontal ? <><XAxis type="number" tickFormatter={compact} axisLine={false} tickLine={false} tick={{ fill: "var(--foreground-subtle)", fontSize: 10 }} /><YAxis type="category" dataKey="name" width={92} axisLine={false} tickLine={false} tick={{ fill: "var(--foreground-muted)", fontSize: 11 }} tickFormatter={(value) => value.length > 14 ? `${value.slice(0, 13)}…` : value} /></> : <><XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: "var(--foreground-muted)", fontSize: 11 }} /><YAxis tickFormatter={compact} axisLine={false} tickLine={false} tick={{ fill: "var(--foreground-subtle)", fontSize: 10 }} /></>}
            <Tooltip cursor={{ fill: "rgba(255,255,255,.035)" }} content={({ active, payload, label: tooltipLabel }) => active && payload?.length ? <div style={{ border: "1px solid var(--border)", borderRadius: 10, background: "var(--surface-overlay)", padding: "10px 12px", boxShadow: "0 14px 36px rgba(0,0,0,.35)" }}><p style={{ margin: 0, fontSize: 11, color: "var(--foreground-muted)" }}>{tooltipLabel}</p><p style={{ margin: "4px 0 0", fontSize: 14, fontWeight: 700, color: accent }}>{formatValue(Number(payload[0].value ?? 0))}</p></div> : null} />
            <Bar dataKey="value" fill={`url(#bar-${color})`} radius={horizontal ? [0, 7, 7, 0] : [7, 7, 2, 2]} maxBarSize={horizontal ? 30 : 48} animationDuration={700}>{chartData.map((item) => <Cell key={item.name} />)}</Bar>
          </RechartsBarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
