"use client";

import { useState } from "react";
import { Cell, Pie, PieChart as RechartsPieChart, ResponsiveContainer, Tooltip } from "recharts";

interface PieChartProps { labels: string[]; data: number[]; formatValue?: (value: number) => string; }
const COLORS = ["#10b981", "#3b82f6", "#8b5cf6", "#f59e0b", "#f43f5e", "#06b6d4"];

export function PieChart({ labels, data, formatValue = (value) => value.toLocaleString("pt-BR") }: PieChartProps) {
  const [active, setActive] = useState(-1);
  const chartData = labels.map((name, index) => ({ name, value: data[index] ?? 0 }));
  const total = data.reduce((sum, value) => sum + value, 0) || 1;
  const selected = active >= 0 ? chartData[active] : null;

  return (
    <div className="dashboard-pie-layout" style={{ display: "grid", gridTemplateColumns: "minmax(180px, .9fr) minmax(150px, 1.1fr)", alignItems: "center", gap: 16, minHeight: 285 }}>
      <div className="dashboard-pie-canvas" style={{ position: "relative", width: "100%", height: 230 }}>
        <ResponsiveContainer width="100%" height="100%">
          <RechartsPieChart>
            <Pie data={chartData} dataKey="value" nameKey="name" cx="50%" cy="50%" innerRadius="58%" outerRadius="82%" paddingAngle={3} cornerRadius={5} stroke="var(--surface-elevated)" strokeWidth={3} animationDuration={750} onMouseLeave={() => setActive(-1)}>
              {chartData.map((item, index) => <Cell key={item.name} fill={COLORS[index % COLORS.length]} opacity={active < 0 || active === index ? 1 : 0.35} onMouseEnter={() => setActive(index)} />)}
            </Pie>
            <Tooltip content={({ active: tooltipActive, payload }) => tooltipActive && payload?.length ? <div style={{ border: "1px solid var(--border)", borderRadius: 10, background: "var(--surface-overlay)", padding: "10px 12px", boxShadow: "0 14px 36px rgba(0,0,0,.35)" }}><p style={{ margin: 0, color: "var(--foreground-muted)", fontSize: 11 }}>{payload[0].name}</p><p style={{ margin: "4px 0 0", color: "var(--foreground)", fontSize: 14, fontWeight: 700 }}>{formatValue(Number(payload[0].value ?? 0))}</p></div> : null} />
          </RechartsPieChart>
        </ResponsiveContainer>
        <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", pointerEvents: "none", textAlign: "center" }}><div><p style={{ margin: 0, color: "var(--foreground)", fontSize: 20, fontWeight: 750, letterSpacing: "-.03em" }}>{selected ? `${((selected.value / total) * 100).toFixed(1)}%` : formatValue(total)}</p><p style={{ margin: "3px 0 0", maxWidth: 84, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", color: "var(--foreground-subtle)", fontSize: 10 }}>{selected?.name ?? "Total"}</p></div></div>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        {chartData.map((item, index) => <button key={item.name} type="button" onMouseEnter={() => setActive(index)} onMouseLeave={() => setActive(-1)} style={{ display: "grid", gridTemplateColumns: "10px minmax(0,1fr) auto", alignItems: "center", gap: 8, width: "100%", padding: "7px 8px", border: "none", borderRadius: 8, background: active === index ? "var(--surface-overlay)" : "transparent", color: "inherit", cursor: "default", textAlign: "left", opacity: active < 0 || active === index ? 1 : .42 }}><span style={{ width: 8, height: 8, borderRadius: 3, background: COLORS[index % COLORS.length] }} /><span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", color: "var(--foreground-muted)", fontSize: 11 }}>{item.name}</span><strong style={{ color: "var(--foreground)", fontSize: 11 }}>{((item.value / total) * 100).toFixed(1)}%</strong></button>)}
      </div>
      <style>{`@media(max-width:560px){.dashboard-pie-layout{grid-template-columns:1fr!important}}`}</style>
    </div>
  );
}
