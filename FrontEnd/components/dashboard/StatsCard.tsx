"use client";

import type { ReactNode } from "react";
import { Area, AreaChart, ResponsiveContainer } from "recharts";

interface StatsCardProps {
  title: string;
  value: string | number;
  icon: ReactNode;
  trend?: { value: string; positive: boolean };
  accent?: "primary" | "secondary" | "warning" | "destructive";
  loading?: boolean;
  series?: number[];
  hint?: string;
}

const ACCENT_COLORS = {
  primary: { bg: "var(--primary-muted)", icon: "var(--primary)" },
  secondary: { bg: "var(--secondary-muted)", icon: "var(--secondary)" },
  warning: { bg: "var(--warning-muted)", icon: "var(--warning)" },
  destructive: { bg: "var(--destructive-muted)", icon: "var(--destructive)" },
};

export function StatsCard({
  title,
  value,
  icon,
  trend,
  accent = "primary",
  loading = false,
  series = [],
  hint,
}: StatsCardProps) {
  const colors = ACCENT_COLORS[accent];

  if (loading) {
    return (
      <div
        className="card-glow dashboard-stat-card"
        style={{
          background: "var(--surface-elevated)",
          border: "1px solid var(--border)",
          borderRadius: 12,
          padding: "14px",
        }}
      >
        <div className="skeleton" style={{ height: 12, width: "60%", marginBottom: 16 }} />
        <div className="skeleton" style={{ height: 28, width: "40%", marginBottom: 8 }} />
        <div className="skeleton" style={{ height: 10, width: "30%" }} />
      </div>
    );
  }

  return (
    <div
      className="card-glow dashboard-stat-card animate-fade-in"
      style={{
        background: "var(--surface-elevated)",
        border: "1px solid var(--border)",
        borderRadius: 12,
        padding: "14px 15px 10px",
        display: "flex",
        flexDirection: "column",
        gap: 8,
        minHeight: 128,
        overflow: "hidden",
      }}
    >
      <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
        <span style={{ fontSize: 10, color: "var(--foreground-muted)", fontWeight: 650 }}>
          {title}
        </span>
        <div
          style={{
            width: 27,
            height: 27,
            borderRadius: 8,
            background: "transparent",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: colors.icon,
            flexShrink: 0,
          }}
        >
          {icon}
        </div>
      </div>

      <div>
        <div style={{ fontSize: 22, fontWeight: 750, color: accent === "primary" ? "var(--primary)" : "var(--foreground)", lineHeight: 1, letterSpacing: "-.035em" }}>
          {value}
        </div>
        {hint && <div style={{ marginTop: 5, fontSize: 9, color: "var(--foreground-subtle)" }}>{hint}</div>}
        {trend && (
          <div
            style={{
              marginTop: 6,
              fontSize: 12,
              color: trend.positive ? "var(--success)" : "var(--destructive)",
              fontWeight: 500,
            }}
          >
            {trend.positive ? "+" : ""}{trend.value}
          </div>
        )}
      </div>
      {series.length > 1 && (
        <div style={{ height: 34, margin: "0 -7px -2px" }}>
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={series.map((value, index) => ({ index, value }))}>
              <defs><linearGradient id={`stat-${accent}`} x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor={colors.icon} stopOpacity={.3} /><stop offset="100%" stopColor={colors.icon} stopOpacity={0} /></linearGradient></defs>
              <Area type="monotone" dataKey="value" stroke={colors.icon} strokeWidth={2} fill={`url(#stat-${accent})`} isAnimationActive animationDuration={650} />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
}
