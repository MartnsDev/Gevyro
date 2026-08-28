"use client";

import { useEffect, useState, Suspense, ReactNode } from "react";
import { useSearchParams } from "next/navigation";
import {
  Building2, CheckCircle2, Clock, Crown, Star, Rocket,
  FlaskConical, ArrowRight, AlertCircle, Loader2,
  Zap, Check
} from "lucide-react";
import { useEmpresa } from "../context/Empresacontext";
import { abrirPortalCobranca, criarCheckout, type PlanoPagoId } from "@/lib/billing";

// 1. CONFIGURAÇÕES E API
const getApiBase = () => {
  const envUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  const cleanUrl = envUrl.replace(/\/api\/v1\/?$/, '').replace(/\/v1\/?$/, '').replace(/\/$/, '');
  return `${cleanUrl}/api/nota-fiscal`;
};
const API_BASE = getApiBase();

const API_GLOBAL = process.env.NEXT_PUBLIC_API_URL ?? "https://api.gevyro.com.br";

async function fetchAuth<T>(path: string): Promise<T> {
  const res = await fetch(`${API_GLOBAL}${path}`, {
    credentials: "include",
    headers: { "Content-Type": "application/json" },
  });
  if (!res.ok) throw new Error(`Erro ${res.status}`);
  return res.json();
}

// 2. DEFINIÇÃO DOS PLANOS
const PLANOS = [
  {
    id: "EXPERIMENTAL", nome: "Experimental", descricao: "Explore todo o potencial da plataforma.",
    preco: "Grátis", duracao: "por 30 dias", icon: FlaskConical,
    corBase: "var(--primary)", corBg: "var(--primary-muted)",
    features: ["1 empresa gerenciada", "1 frente de caixa (PDV)", "Até 300 produtos", "Relatórios e Dashboards", "Emissão de Nota Fiscal", "Histórico de 2 meses"],
    destaque: false, pagavel: false, cta: "Iniciar Teste"
  },
  {
    id: "BASICO", nome: "Básico", descricao: "A fundação sólida para pequenos negócios.",
    preco: "R$ 79,90", duracao: "por mês", icon: Star,
    corBase: "var(--primary)", corBg: "var(--primary-muted)",
    features: ["1 empresa gerenciada", "1 frente de caixa (PDV)", "Até 500 produtos", "Relatórios completos", "Histórico de 6 meses", "Controle de dívidas", "Suporte via e-mail"],
    destaque: false, pagavel: true, cta: "Assinar Básico"
  },
  {
    id: "PRO", nome: "Pro", descricao: "Ferramentas avançadas para quem quer crescer.",
    preco: "R$ 149,90", duracao: "por mês", icon: Rocket,
    corBase: "var(--primary)", corBg: "var(--primary-muted)",
    features: ["Até 5 empresas", "Até 5 frentes de caixa", "Produtos ilimitados", "Histórico de 1 ano", "Exportação PDF/CSV", "Backup automático", "Suporte prioritário"],
    destaque: true, pagavel: true, cta: "Escolher o Pro"
  },
  {
    id: "PREMIUM", nome: "Premium", descricao: "Poder absoluto para franquias e redes.",
    preco: "R$ 299,90", duracao: "por mês", icon: Crown,
    corBase: "var(--primary)", corBg: "var(--primary-muted)",
    features: ["Empresas ilimitadas", "Caixas ilimitados", "Histórico ilimitado", "Relatórios avançados", "Shopee e Mercado Livre", "Backup automático", "Suporte dedicado 24h"],
    destaque: false, pagavel: true, cta: "Assinar Premium"
  }
] as const;

const ORDEM = ["EXPERIMENTAL", "BASICO", "PRO", "PREMIUM"] as const;
const DURACAO_TRIAL_DIAS = 30;

function barWidth(dias: number, total: number) { return total === 0 ? 0 : Math.min(100, Math.max(0, (dias / total) * 100)); }
function barColor(pct: number) { return pct > 50 ? "#10b981" : pct > 20 ? "#f59e0b" : "#ef4444"; }

// 3. COMPONENTE INTERNO
function PlanosInner() {
  const searchParams = useSearchParams();
  const [plano, setPlano] = useState<any>(null);
  const [emailUsuario, setEmailUsuario] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingPlano, setLoadingPlano] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [cancelado, setCancelado] = useState(false);
  
  // Controle de Efeitos Visuais no React
  const [hoverCard, setHoverCard] = useState<string | null>(null);

  useEffect(() => {
    if (searchParams.get("canceled") === "true") {
      setCancelado(true);
      globalThis.window.history.replaceState({}, "", "/dashboard/planos");
    }
  }, [searchParams]);

  useEffect(() => {
    const carregarDados = async () => {
      const headers: HeadersInit = { "Content-Type": "application/json" };

      fetch(`${API_GLOBAL}/api/usuario`, { credentials: "include", headers })
        .then((r) => (r.ok ? r.json() : Promise.reject()))
        .then((data) => {
          setEmailUsuario(data.email ?? null);
        }).catch(() => {});

      fetchAuth<any>("/api/v1/dashboard/vendas/plano-usuario")
        .then(setPlano).catch(() => {}).finally(() => setLoading(false));
    };
    carregarDados();
  }, []);

  async function handleAssinar(planoId: string) {
    setErro(null); setLoadingPlano(planoId);

    try {
      let emailCheckout = emailUsuario;
      if (!emailCheckout) {
        const usuario = await fetchAuth<{ email?: string }>("/api/usuario");
        emailCheckout = usuario.email?.trim() || null;
        if (!emailCheckout) throw new Error("Não foi possível identificar sua conta. Entre novamente.");
        setEmailUsuario(emailCheckout);
      }

      const possuiAssinaturaPaga = plano?.tipoPlano && plano.tipoPlano !== "EXPERIMENTAL";
      const portalUrl = possuiAssinaturaPaga
        ? await abrirPortalCobranca()
        : null;
      const url = portalUrl ?? await criarCheckout(planoId as PlanoPagoId);
      globalThis.window.location.assign(url);
    } catch (e: unknown) {
      setErro(e instanceof Error ? e.message : "Não foi possível iniciar o checkout.");
      setLoadingPlano(null);
    }
  }

  const planoAtualIdx = plano ? ORDEM.indexOf(plano.tipoPlano as (typeof ORDEM)[number]) : -1;
  const pct = plano ? barWidth(plano.diasRestantes, DURACAO_TRIAL_DIAS) : 100;
  const planoAtual = PLANOS.find((p) => p.id === plano?.tipoPlano);
  const estaAtivo = plano?.statusAcesso === "ATIVO";

  // RENDER (Estilos Nativos Blindados)
  return (
    <div className="planos-page" style={{ padding: "20px 22px 48px", width: "100%", display: "flex", flexDirection: "column", gap: 14, color: "var(--foreground)" }}>
      
      {/* 1. HERO SECTION */}
      <section style={{ display: "flex", flexDirection: "column", alignItems: "flex-start" }}>
        <div style={{ display: "inline-flex", alignItems: "center", gap: 5, color: "var(--primary)", fontSize: 10, fontWeight: 700, marginBottom: 6, textTransform: "uppercase", letterSpacing: ".08em" }}>
          <Zap size={13} /> Planos Gevyro
        </div>
        <h1 style={{ fontSize: 22, fontWeight: 760, lineHeight: 1.2, color: "var(--foreground)", margin: "0 0 4px", letterSpacing: "-.03em" }}>
          Planos
        </h1>
        <p style={{ fontSize: 13, color: "var(--foreground-muted)", lineHeight: 1.5, margin: 0 }}>
          Escolha os limites adequados para o momento do seu negócio.
        </p>

        {erro && (
          <div style={{ marginTop: 24, padding: "14px 20px", background: "rgba(239,68,68,0.1)", border: "1px solid rgba(239,68,68,0.3)", borderRadius: 12, color: "#ef4444", fontSize: 14, display: "flex", alignItems: "center", gap: 8, width: "100%" }}>
            <AlertCircle size={18} /> {erro}
          </div>
        )}
        {cancelado && (
          <div style={{ marginTop: 24, padding: "14px 20px", background: "rgba(245,158,11,0.1)", border: "1px solid rgba(245,158,11,0.3)", borderRadius: 12, color: "#f59e0b", fontSize: 14, display: "flex", alignItems: "center", gap: 8, width: "100%" }}>
            <AlertCircle size={18} /> Checkout cancelado. Fique à vontade para assinar quando estiver pronto.
          </div>
        )}
      </section>

      {/* 2. PLANO ATUAL BANNER */}
      {!loading && plano && planoAtual && (
        <section style={{ background: "var(--surface-elevated)", border: "1px solid var(--border)", borderRadius: 11, padding: 14, display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: 14 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 11 }}>
            <div style={{ width: 40, height: 40, borderRadius: 10, background: planoAtual.corBg, display: "flex", alignItems: "center", justifyContent: "center" }}>
              <planoAtual.icon size={20} color={planoAtual.corBase} />
            </div>
            <div>
              <p style={{ fontSize: 9, color: "var(--foreground-subtle)", margin: "0 0 2px", fontWeight: 650, textTransform: "uppercase", letterSpacing: ".07em" }}>Plano atual</p>
              <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <h2 style={{ fontSize: 16, fontWeight: 750, margin: 0, color: "var(--foreground)" }}>{planoAtual.nome}</h2>
                <span style={{ fontSize: 8, fontWeight: 750, padding: "3px 7px", borderRadius: 99, background: estaAtivo ? "rgba(16,185,129,0.12)" : "rgba(239,68,68,0.12)", color: estaAtivo ? "#10b981" : "#ef4444" }}>
                  {plano.statusAcesso === "ATIVO" ? "ATIVO" : "BLOQUEADO"}
                </span>
              </div>
            </div>
          </div>

          <div style={{ flex: 1, minWidth: 220, maxWidth: 360 }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6, fontSize: 10, fontWeight: 600 }}>
              <span style={{ display: "flex", alignItems: "center", gap: 6, color: "var(--foreground-muted)" }}><Clock size={14} /> Ciclo de Faturamento</span>
              <span style={{ color: pct < 20 ? "var(--destructive)" : "var(--foreground)" }}>{plano.diasRestantes} dias restantes</span>
            </div>
            <div style={{ height: 8, background: "var(--surface-overlay)", borderRadius: 99, overflow: "hidden", border: "1px solid var(--border)" }}>
              <div style={{ width: `${pct}%`, height: "100%", background: barColor(pct), transition: "width 1s cubic-bezier(0.4, 0, 0.2, 1)", borderRadius: 99 }} />
            </div>
          </div>
          
          <div style={{ display: "flex", alignItems: "center", gap: 7, fontSize: 10, fontWeight: 600, color: "var(--foreground-muted)", background: "var(--surface-overlay)", border: "1px solid var(--border)", padding: "8px 11px", borderRadius: 8 }}>
            <Building2 size={14} color={planoAtual.corBase} />
            <span>Lojas ativas: <strong style={{ color: "var(--foreground)" }}>{plano.empresasCriadas}</strong> / {plano.limiteEmpresas >= 99 ? "Ilimitado" : plano.limiteEmpresas}</span>
          </div>
        </section>
      )}

      {/* 3. GRID DE PLANOS */}
      <section className="pricing-grid-container">
        <style>{`
          .pricing-grid-container { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; align-items: stretch; }
          @media (max-width: 1024px) { .pricing-grid-container { grid-template-columns: repeat(2, 1fr); } }
          @media (max-width: 640px) { .pricing-grid-container { grid-template-columns: 1fr; } }
        `}</style>
        
        {PLANOS.map((p, idx) => {
          const isAtual = p.id === plano?.tipoPlano;
          const isUpgrade = idx > planoAtualIdx && planoAtualIdx >= 0;
          const isLoading = loadingPlano === p.id;
          const isHovered = hoverCard === p.id;

          // Dinâmica de estilos baseada no Hover do React
          let cardStyle: React.CSSProperties = {
            background: "var(--surface-elevated)",
            border: `1px solid ${isAtual || isHovered ? p.corBase : "var(--border)"}`,
            borderRadius: 11, padding: "16px 15px", position: "relative",
            display: "flex", flexDirection: "column",
            transition: "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
            transform: isHovered && !isAtual ? "translateY(-3px)" : "translateY(0)",
            boxShadow: isHovered && !isAtual ? `0 12px 24px ${p.corBg}` : "none",
          };

          if (p.destaque) {
            cardStyle = {
              ...cardStyle,
              border: `2px solid ${p.corBase}`,
              background: "var(--surface-elevated)",
              transform: isHovered ? "translateY(-3px)" : "translateY(0)",
              boxShadow: `0 8px 22px ${p.corBg}`,
              zIndex: 10
            };
          }

          return (
            <div 
              key={p.id} 
              style={cardStyle}
              onMouseEnter={() => setHoverCard(p.id)}
              onMouseLeave={() => setHoverCard(null)}
            >
              {p.destaque && (
                <div style={{ position: "absolute", top: -9, right: 10, background: p.corBase, color: "#fff", padding: "3px 8px", borderRadius: 99, fontSize: 8, fontWeight: 800, whiteSpace: "nowrap" }}>
                  RECOMENDADO
                </div>
              )}
              
              <div style={{ width: 34, height: 34, borderRadius: 9, background: p.corBg, display: "flex", alignItems: "center", justifyContent: "center", marginBottom: 11 }}>
                <p.icon size={18} color={p.corBase} />
              </div>
              
              <h3 style={{ fontSize: 16, fontWeight: 750, margin: "0 0 4px", color: "var(--foreground)" }}>{p.nome}</h3>
              <p style={{ fontSize: 10, color: "var(--foreground-muted)", margin: 0, minHeight: 30, lineHeight: 1.4 }}>{p.descricao}</p>
              
              <div style={{ fontSize: 25, fontWeight: 780, color: p.corBase, margin: "14px 0 3px", display: "flex", alignItems: "flex-end", gap: 3 }}>
                {p.preco}
                {p.preco !== "Grátis" && <span style={{ fontSize: 14, fontWeight: 600, color: "#94a3b8", paddingBottom: 8 }}>/mês</span>}
              </div>

              <button
                disabled={isAtual || isLoading || !p.pagavel}
                onClick={() => { if (!isAtual && !isLoading && p.pagavel) handleAssinar(p.id); }}
                style={{
                  width: "100%", padding: 10, marginTop: 10, marginBottom: 16, borderRadius: 8, fontSize: 10, fontWeight: 750,
                  display: "flex", alignItems: "center", justifyContent: "center", gap: 8, cursor: isAtual || !p.pagavel || isLoading ? "default" : "pointer",
                  transition: "all 0.2s",
                  background: isAtual ? "var(--surface-overlay)" : p.destaque ? p.corBase : "transparent",
                  color: isAtual ? "var(--foreground-muted)" : p.destaque ? "#fff" : "var(--foreground)",
                  border: isAtual ? "none" : p.destaque ? "none" : `1px solid ${isHovered ? p.corBase : "var(--border)"}`,
                  opacity: isLoading ? 0.7 : 1
                }}
              >
                {isLoading ? <><Loader2 size={18} className="animate-spin" /> Aguarde...</> 
                 : isAtual ? <><Check size={18} /> Plano Atual</> 
                 : !p.pagavel ? p.cta 
                 : <>{plano?.tipoPlano !== "EXPERIMENTAL" ? "Gerenciar na Stripe" : p.cta} {isUpgrade && <ArrowRight size={18} />}</>}
              </button>

              <div style={{ flex: 1 }}>
                <p style={{ fontSize: 9, fontWeight: 700, color: "var(--foreground)", textTransform: "uppercase", letterSpacing: ".07em", margin: "0 0 9px" }}>Inclui</p>
                <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "flex", flexDirection: "column", gap: 7 }}>
                  {p.features.slice(0, 5).map((f, i) => (
                    <li key={i} style={{ display: "flex", alignItems: "flex-start", gap: 7, fontSize: 10, color: "var(--foreground-muted)", lineHeight: 1.35 }}>
                      <CheckCircle2 size={12} color={p.corBase} style={{ flexShrink: 0, marginTop: 1 }} />
                      <span>{f}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          );
        })}
      </section>

      {/* 4. COMPARAÇÃO ESSENCIAL */}
      <section style={{ marginTop: 4 }}>
        <div style={{ marginBottom: 10 }}>
          <h3 style={{ fontSize: 14, fontWeight: 700, color: "var(--foreground)", margin: "0 0 3px" }}>Comparação rápida</h3>
          <p style={{ color: "var(--foreground-muted)", fontSize: 10, margin: 0 }}>Os principais limites de cada assinatura.</p>
        </div>
        <div style={{ overflowX: "auto", background: "var(--surface-elevated)", border: "1px solid var(--border)", borderRadius: 14, padding: "8px 0" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 700, textAlign: "left" }}>
            <thead>
              <tr>
                <th style={{ padding: "13px 16px", color: "var(--foreground-muted)", fontSize: 10, fontWeight: 700, borderBottom: "1px solid var(--border)" }}>Recurso</th>
                {PLANOS.map((p) => (
                  <th key={p.id} style={{ padding: "13px 12px", textAlign: "center", color: p.id === plano?.tipoPlano ? p.corBase : "var(--foreground)", fontSize: 10, fontWeight: 750, borderBottom: "1px solid var(--border)" }}>
                    {p.nome}
                    {p.id === plano?.tipoPlano && <div style={{ fontSize: 10, color: p.corBase, marginTop: 4, letterSpacing: "1px" }}>ATUAL</div>}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {[
                { label: "Lojas Gerenciadas", values: ["1", "1", "5", "Ilimitado"] },
                { label: "Frentes de Caixa (PDV)", values: ["1", "1", "5", "Ilimitado"] },
                { label: "Limite de Produtos", values: ["300", "500", "Ilimitado", "Ilimitado"] },
                { label: "Histórico de Dados", values: ["2 Meses", "6 Meses", "1 Ano", "Vitalício"] },
                { label: "Nota Fiscal", values: [true, true, true, true] },
                { label: "Integrações", values: [false, false, false, "Shopee e Mercado Livre"] },
              ].map((row, i) => (
                <tr key={i} style={{ borderBottom: "1px solid var(--border)" }}>
                  <td style={{ padding: "11px 16px", color: "var(--foreground-muted)", fontSize: 10, fontWeight: 500 }}>{row.label}</td>
                  {row.values.map((v, j) => (
                    <td key={j} style={{ padding: "11px 12px", textAlign: "center", fontSize: 10 }}>
                      {typeof v === "boolean" ? (
                        v ? <Check size={20} color="#10b981" style={{ margin: "0 auto" }} /> : <span style={{ color: "#475569" }}>—</span>
                      ) : (
                        <span style={{ fontWeight: PLANOS[j].id === plano?.tipoPlano ? 700 : 500, color: PLANOS[j].id === plano?.tipoPlano ? "#fff" : "#94a3b8" }}>{v}</span>
                      )}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <style dangerouslySetInnerHTML={{ __html: `
        @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
        .animate-spin { animation: spin 1s linear infinite; }
        @media(max-width:640px){.planos-page{padding:16px 12px 90px!important}}
      `}} />
    </div>
  );
}

export default function Planos() {
  return (
    <Suspense fallback={<div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", height: "60vh", color: "#94a3b8", gap: 16 }}><Loader2 size={32} className="animate-spin" /><p>Carregando as melhores opções para o seu negócio...</p></div>}>
      <PlanosInner />
    </Suspense>
  );
}
