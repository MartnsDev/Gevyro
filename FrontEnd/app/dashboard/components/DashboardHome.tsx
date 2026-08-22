"use client";

import { useEffect, useState, ReactNode } from "react";
import { useEmpresa } from "../context/Empresacontext";
import {
  AlertCircle, DollarSign, FileText, BarChart3, Package, Users,
  CreditCard, TrendingUp, TrendingDown, Calendar, Store,
  Lock, ShoppingBag, ChevronRight, Receipt, Settings, ShoppingCart
} from "lucide-react";
import { StatsCard } from "@/components/dashboard/StatsCard";
import type { Usuario } from "@/lib/api-v2";

import { 
  dashboardService, 
  type VisaoGeral, 
  type MetodoPagamentoData, 
  type ProdutoVendasData, 
  type VendasDiariasData 
} from "@/lib/services/dashboard";

import { BarChart }  from "./graphs/BarChart";
import { PieChart }  from "./graphs/PieChart";
import { AreaTrendChart } from "./graphs/AreaTrendChart";

import AbrirCaixa from "../acoesRapidas/AbrirCaixa";
import NovaVenda from "../acoesRapidas/NovaVenda";
import NovoProduto from "../acoesRapidas/NovoProduto";
import NovoCliente from "../acoesRapidas/NovoCliente";
import ModalRelatorioRapido from "../acoesRapidas/ModalRelatorioRapido";

const fmt = (v?: number | null) => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(v ?? 0);

const API = process.env.NEXT_PUBLIC_API_URL ?? "https://gestpro-backend-production.up.railway.app";

function ClientOnly({ children }: { children: ReactNode }) {
  const [ok, setOk] = useState(false);
  useEffect(() => setOk(true), []);
  return ok ? <>{children}</> : null;
}

function ChartCard({ title, subtitle, children, fullWidth, accent }: { title: string; subtitle?: string; children: ReactNode; fullWidth?: boolean; accent?: string; }) {
  return (
    <div className="dashboard-chart-card" style={{ background: "var(--surface-elevated)", border: "1px solid var(--border)", borderRadius: 12, padding: "16px 17px", gridColumn: fullWidth ? "1 / -1" : undefined, position: "relative", overflow: "hidden", minWidth: 0 }}>
      {accent && <div style={{ position: "absolute", top: 0, left: 16, width: 32, height: 2, background: accent }} />}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <div>
          <p style={{ fontSize: 12, fontWeight: 700, color: "var(--foreground)", margin: 0 }}>{title}</p>
          {subtitle && <p style={{ fontSize: 9, color: "var(--foreground-subtle)", margin: "3px 0 0" }}>{subtitle}</p>}
        </div>
      </div>
      {children}
    </div>
  );
}

function EmptyChart() {
  return (
    <div className="dashboard-empty-chart" style={{ height: 200, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 8, color: "var(--foreground-subtle)" }}>
      <BarChart3 size={32} style={{ opacity: 0.25 }} />
      <p style={{ fontSize: 13, margin: 0, opacity: 0.5 }}>Sem dados disponíveis</p>
    </div>
  );
}

export default function DashboardHome({ usuario, onNavegar }: { usuario?: Usuario; onNavegar?: (secao: string) => void; }) {
  const { empresaAtiva, caixaAtivo } = useEmpresa();

  const [visao, setVisao] = useState<VisaoGeral | null>(null);
  const [vendasMetodo, setVendasMetodo] = useState<MetodoPagamentoData[]>([]);
  const [vendasProduto, setVendasProduto] = useState<ProdutoVendasData[]>([]);
  const [vendasDiarias, setVendasDiarias] = useState<VendasDiariasData[]>([]);
  const [loading, setLoading] = useState(true);
  
  // Estado das Preferências de Notificação
  const [prefs, setPrefs] = useState({
    alertaEstoqueZerado: true,
    alertaVencimentoPlano: true,
  });
  
  // Estado para controlar qual Modal de Ação Rápida está aberto
  const [modalAtivo, setModalAtivo] = useState<"venda" | "produto" | "caixa" | "cliente" | "relatorio" | null>(null);
  const [alertasExpandido, setAlertasExpandido] = useState(false);

  // Helper para navegação lateral
  const nav = (s: string) => onNavegar?.(s);

  // Busca de Preferências de Alerta do Usuário
  const fetchPreferencias = async () => {
    try {
      const res = await fetch(`${API}/api/v1/configuracoes/notificacoes`, {
        credentials: "include",
      });
      if (res.ok) {
        const data = await res.json();
        setPrefs(data);
      }
    } catch (e) {
      console.warn("Aviso: Usando preferências de alerta padrão.");
    }
  };

  // Busca de dados utilizando o dashboardService centralizado
  const fetchDados = async (id: number) => {
    setLoading(true);
    try {
      // Dispara a busca de preferências em paralelo com os dados do dashboard
      fetchPreferencias();

      const [v, metodo, produto, diarias] = await Promise.allSettled([
        dashboardService.visaoGeral(id),
        dashboardService.vendasPorMetodo(id),
        dashboardService.vendasPorProduto(id),
        dashboardService.vendasDiarias(id),
      ]);
      
      if (v.status === "fulfilled") setVisao(v.value);
      if (metodo.status === "fulfilled") setVendasMetodo(metodo.value ?? []);
      if (produto.status === "fulfilled") setVendasProduto(produto.value ?? []);
      if (diarias.status === "fulfilled") setVendasDiarias(diarias.value ?? []);
    } catch (err) { 
      console.error("Erro ao buscar dados do dashboard:", err); 
    } finally { 
      setLoading(false); 
    }
  };

  useEffect(() => {
    if (empresaAtiva?.id) fetchDados(empresaAtiva.id);
    return () => setLoading(false);
  }, [empresaAtiva?.id]);

  const primeiroNome = usuario?.nome?.split(" ")[0] ?? "usuário";
  const today = new Date().toLocaleDateString("pt-BR", { weekday: "long", day: "numeric", month: "long", year: "numeric" });

  const trendSeries = vendasDiarias.map(item => item.total);
  const spark = trendSeries.length > 1 ? trendSeries : [3, 5, 4, 7, 6, 9, 8];
  const primaryStats = [
    { title: "Faturamento hoje", value: loading ? "—" : fmt(visao?.vendasHoje), icon: <DollarSign size={15} />, accent: "primary" as const, series: spark, hint: "Movimento do período" },
    { title: "Lucro hoje", value: loading ? "—" : fmt(visao?.lucroDia), icon: <TrendingUp size={15} />, accent: "primary" as const, series: spark.map((v, i) => v * (1 + i * .04)), hint: "Resultado do dia" },
    { title: "Ticket estimado", value: loading ? "—" : fmt((visao?.vendasHoje ?? 0) / Math.max(vendasMetodo.reduce((sum, item) => sum + item.total, 0), 1)), icon: <Receipt size={15} />, accent: "primary" as const, series: spark.map((v, i) => v * (.8 + i * .03)), hint: "Média das vendas" },
    { title: "Lucro do mês", value: loading ? "—" : fmt(visao?.lucroMes), icon: <TrendingUp size={15} />, accent: "primary" as const, series: spark.map(v => v * .72), hint: "Resultado acumulado" },
  ];

  const alertasDoBackend = visao?.alertas ?? [];
  
  // Filtra os alertas de estoque APENAS se a preferência permitir
  const alertasProduto = prefs.alertaEstoqueZerado 
    ? alertasDoBackend.filter(a => a.startsWith("Estoque esgotado:")) 
    : [];

  // Outros alertas genéricos que o backend possa enviar
  const alertasOutrosBackend = alertasDoBackend.filter(a => !a.startsWith("Estoque esgotado:"));

  // Filtra alertas de plano APENAS se a preferência permitir
  const alertasPlano = (prefs.alertaVencimentoPlano && visao?.planoUsuario && visao.planoUsuario.diasRestantes < 7) 
    ? [`Plano ${visao.planoUsuario.tipoPlano}: ${visao.planoUsuario.diasRestantes} dia(s) restante(s)`] 
    : [];

  const alertasOutros = [...alertasOutrosBackend, ...alertasPlano];
  const todosAlertas = [...alertasProduto, ...alertasOutros];

  const acoes = [
    {
      label: caixaAtivo ? "Ver Caixa" : "Abrir Caixa",
      desc: caixaAtivo ? `${fmt(caixaAtivo.totalVendas)} em vendas` : "Nenhum caixa aberto",
      icon: caixaAtivo ? <DollarSign size={20} /> : <Lock size={20} />,
      cor: caixaAtivo ? "#34d399" : "var(--foreground-muted)",
      bg: caixaAtivo ? "rgba(52,211,153,0.08)" : "var(--surface-overlay)",
      borda: caixaAtivo ? "rgba(52,211,153,0.3)" : "var(--border)",
      acao: () => caixaAtivo ? nav("caixa-rapido") : setModalAtivo("caixa"),
    },
    {
      label: "Nova Venda",
      desc: caixaAtivo ? `Caixa #${caixaAtivo.id} aberto` : "Abra o caixa primeiro",
      icon: <ShoppingCart size={20} />,
      cor: caixaAtivo ? "var(--foreground)" : "var(--foreground-subtle)",
      bg: "var(--surface-overlay)", borda: "var(--border)",
      acao: () => caixaAtivo ? setModalAtivo("venda") : setModalAtivo("caixa"),
    },
    {
      label: "Registrar Pedido",
      desc: "Registrar um pedido de venda",
      icon: <ShoppingBag size={20} />,
      cor: "var(--foreground)", bg: "var(--surface-overlay)", borda: "var(--border)",
      acao: () => nav("pedidos"),
    },
    {
      label: "Novo Produto",
      desc: `${visao?.produtosComEstoque ?? 0} com estoque`,
      icon: <Package size={20} />,
      cor: "var(--foreground)", bg: "var(--surface-overlay)", borda: "var(--border)",
      acao: () => setModalAtivo("produto"),
    },
    {
      label: "Novo Cliente",
      desc: `${visao?.clientesAtivos ?? 0} ativos`,
      icon: <Users size={20} />,
      cor: "var(--foreground)", bg: "var(--surface-overlay)", borda: "var(--border)",
      acao: () => setModalAtivo("cliente"),
    },
    {
      label: "Resumo do Dia",
      desc: "Ver métricas rápidas",
      icon: <BarChart3 size={20} />,
      cor: "var(--foreground)", bg: "var(--surface-overlay)", borda: "var(--border)",
      acao: () => setModalAtivo("relatorio"),
    },
    {
      label: "Relatórios",
      desc: "Exportar dados completos",
      icon: <FileText size={20} />,
      cor: "var(--foreground)", bg: "var(--surface-overlay)", borda: "var(--border)",
      acao: () => nav("relatorios"),
    },
    {
      label: "Emitir nota fiscal",
      desc: "Emitir NF-e / NFC-e",
      icon: <Receipt size={20} />,
      cor: "var(--foreground)", bg: "var(--surface-overlay)", borda: "var(--border)",
      acao: () => nav("notafiscal"),
    },
    {
      label: "Configurações",
      desc: "Alterar configurações",
      icon: <Settings size={20} />,
      cor: "var(--foreground)", bg: "var(--surface-overlay)", borda: "var(--border)",
      acao: () => nav("configuracoes"),
    },
  ];
  const primaryActions = acoes.slice(0, 5);
  const secondaryActions = acoes.slice(5);

  if (!empresaAtiva)
    return (
      <ClientOnly>
        <div style={{ padding: 48, textAlign: "center", color: "var(--foreground-muted)", display: "flex", flexDirection: "column", alignItems: "center", gap: 12 }}>
          <Store size={48} color="var(--foreground-subtle)" />
          <h2 style={{ fontSize: 16, fontWeight: 600, color: "var(--foreground)", margin: 0 }}>Nenhuma empresa selecionada</h2>
          <p style={{ fontSize: 14 }}>Selecione ou cadastre uma empresa no menu superior.</p>
        </div>
      </ClientOnly>
    );

  return (
    <ClientOnly>
      {modalAtivo === "caixa" && (
        <AbrirCaixa
          onConcluido={() => {
            setModalAtivo(null);
            void fetchDados(empresaAtiva.id);
          }}
        />
      )}
      
      {modalAtivo === "venda" && caixaAtivo && <NovaVenda empresaId={empresaAtiva.id} caixaId={caixaAtivo.id} onClose={() => setModalAtivo(null)} onConcluido={() => fetchDados(empresaAtiva.id)} />}
      
      {modalAtivo === "produto" && <NovoProduto empresaId={empresaAtiva.id} onClose={() => setModalAtivo(null)} onConcluido={() => fetchDados(empresaAtiva.id)} />}
      
      {modalAtivo === "cliente" && <NovoCliente empresaId={empresaAtiva.id} onClose={() => setModalAtivo(null)} onConcluido={() => fetchDados(empresaAtiva.id)} />}
      
      {modalAtivo === "relatorio" && <ModalRelatorioRapido empresaId={empresaAtiva.id} onClose={() => setModalAtivo(null)} onIrRelatorios={() => nav("relatorios")} />}

      <div className="dashboard-home" style={{ padding: "20px 22px 42px", display: "flex", flexDirection: "column", gap: 12 }}>
        <div className="dashboard-heading" style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14, flexWrap: "wrap" }}>
          <div>
            <h1 style={{ fontSize: 22, fontWeight: 760, color: "var(--foreground)", margin: 0, letterSpacing: "-.035em" }}>Visão geral</h1>
            <p style={{ fontSize: 10, color: "var(--foreground-subtle)", textTransform: "capitalize", margin: "4px 0 0" }}>{today} · Bem-vindo, {primeiroNome}</p>
          </div>
          <div style={{ display: "flex", gap: 7, alignItems: "center", flexWrap: "wrap" }}>
            {primaryActions.slice(0, 3).map(action => <button key={action.label} onClick={action.acao} className="dashboard-top-action" style={{ border: "1px solid var(--border)", borderRadius: 8, background: "var(--surface-elevated)", color: "var(--foreground-muted)", padding: "8px 10px", fontSize: 10, fontWeight: 650, display: "inline-flex", alignItems: "center", gap: 6, cursor: "pointer" }}>{action.icon}{action.label}</button>)}
            <button onClick={() => setModalAtivo("produto")} className="dashboard-top-action" style={{ border: "1px solid var(--border)", borderRadius: 8, background: "var(--surface-elevated)", color: "var(--foreground-muted)", padding: "8px 10px", fontSize: 10, fontWeight: 650, display: "inline-flex", alignItems: "center", gap: 6, cursor: "pointer" }}><Package size={14} />Novo produto</button>
          </div>
        </div>

        {/* Indicadores principais da visão geral */}
        <div className="dashboard-primary-stats" style={{ display: "grid", gridTemplateColumns: "repeat(4, minmax(0,1fr))", gap: 8 }}>
          {primaryStats.map(card => <StatsCard key={card.title} {...card} loading={loading} />)}
        </div>

        <div className="dashboard-main-grid" style={{ display: "grid", gridTemplateColumns: "minmax(0,1.75fr) minmax(265px,.75fr)", gap: 8 }}>
          <ChartCard title="Faturamento por dia" subtitle="Movimentação dos últimos dias" accent="#22c55e">
            <div style={{ display: "flex", alignItems: "baseline", gap: 8, marginBottom: 2 }}><strong style={{ fontSize: 20, color: "#4ade80", letterSpacing: "-.03em" }}>{fmt(visao?.vendasSemanais)}</strong><span style={{ fontSize: 9, color: "var(--foreground-subtle)" }}>acumulado semanal</span></div>
            {vendasDiarias.length > 0 ? <AreaTrendChart labels={vendasDiarias.map(item => item.dia)} data={vendasDiarias.map(item => item.total)} formatValue={fmt} height={225} /> : <EmptyChart />}
          </ChartCard>
          <ChartCard title="Vendas por canal" subtitle="Participação por pagamento" accent="#22c55e">
            {vendasMetodo.length > 0 ? <PieChart labels={vendasMetodo.map(item => item.metodo)} data={vendasMetodo.map(item => item.total)} formatValue={value => `${value} venda${value !== 1 ? "s" : ""}`} /> : <EmptyChart />}
          </ChartCard>
        </div>

        <div className="dashboard-bottom-grid" style={{ display: "grid", gridTemplateColumns: "minmax(0,1.15fr) minmax(240px,.85fr) minmax(240px,.85fr)", gap: 8 }}>
          <ChartCard title="Top produtos vendidos" subtitle="Ranking por quantidade" accent="#22c55e">
            {vendasProduto.length > 0 ? <BarChart labels={vendasProduto.slice(0, 6).map(item => item.nome)} data={vendasProduto.slice(0, 6).map(item => item.quantidade)} label="Unidades" color="green" formatValue={value => `${value} un.`} height={220} horizontal /> : <EmptyChart />}
          </ChartCard>

          <ChartCard title="Resumo da operação" subtitle={empresaAtiva.nomeFantasia} accent="#22c55e">
            <div className="dashboard-metric-list">
              {[
                ["Vendas na semana", fmt(visao?.vendasSemanais), BarChart3, "#4ade80", "vendas"],
                ["Produtos em estoque", visao?.produtosComEstoque ?? 0, Package, "#4ade80", "produtos"],
                ["Produtos zerados", visao?.produtosSemEstoque ?? 0, TrendingDown, "#fb7185", "produtos"],
                ["Clientes ativos", visao?.clientesAtivos ?? 0, Users, "#60a5fa", "clientes"],
                ["Custo em estoque", fmt(visao?.custos), Receipt, "#fbbf24", "produtos"],
              ].map(([label, value, Icon, color, section]) => {
                const MetricIcon = Icon as typeof Package;
                return <button key={String(label)} onClick={() => nav(String(section))} style={{ width: "100%", border: 0, borderBottom: "1px solid var(--border-subtle)", background: "transparent", padding: "12px 2px", display: "grid", gridTemplateColumns: "28px 1fr auto 14px", alignItems: "center", gap: 8, color: "inherit", cursor: "pointer", textAlign: "left" }}><span style={{ width: 27, height: 27, display: "grid", placeItems: "center", borderRadius: 7, background: `color-mix(in srgb, ${color} 10%, transparent)`, color: String(color) }}><MetricIcon size={14} /></span><span style={{ fontSize: 10, color: "var(--foreground-muted)" }}>{String(label)}</span><strong style={{ fontSize: 11, color: "var(--foreground)" }}>{String(value)}</strong><ChevronRight size={12} color="var(--foreground-subtle)" /></button>;
              })}
            </div>
          </ChartCard>

          <ChartCard title="Ações e alertas" subtitle="O que precisa de atenção" accent="#22c55e">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 6, marginBottom: 10 }}>
              {[...primaryActions.slice(3), ...secondaryActions.slice(0, 2)].map(action => <button key={action.label} onClick={action.acao} style={{ minWidth: 0, border: "1px solid var(--border)", borderRadius: 8, background: "var(--surface-overlay)", color: "var(--foreground-muted)", padding: "9px 8px", display: "flex", alignItems: "center", gap: 6, fontSize: 9, fontWeight: 650, cursor: "pointer" }}><span style={{ color: "var(--primary)" }}>{action.icon}</span><span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{action.label}</span></button>)}
            </div>
            {todosAlertas.length === 0 ? <div style={{ border: "1px solid rgba(34,197,94,.16)", borderRadius: 8, background: "rgba(34,197,94,.055)", padding: 12, display: "flex", gap: 8, alignItems: "center", color: "#4ade80", fontSize: 10 }}><span style={{ width: 7, height: 7, borderRadius: 99, background: "#22c55e" }} />Operação sem alertas</div> : <button onClick={() => setAlertasExpandido(value => !value)} style={{ width: "100%", border: "1px solid rgba(245,158,11,.2)", borderRadius: 8, background: "rgba(245,158,11,.07)", padding: 11, display: "flex", gap: 8, alignItems: "center", color: "#fbbf24", fontSize: 10, cursor: "pointer", textAlign: "left" }}><AlertCircle size={14} /><span style={{ flex: 1 }}>{todosAlertas.length} alerta{todosAlertas.length > 1 ? "s" : ""} pendente{todosAlertas.length > 1 ? "s" : ""}</span><ChevronRight size={12} style={{ transform: alertasExpandido ? "rotate(90deg)" : "none" }} /></button>}
            {alertasExpandido && todosAlertas.slice(0, 4).map(alerta => <div key={alerta} style={{ padding: "7px 3px", borderBottom: "1px solid var(--border-subtle)", fontSize: 9, color: "var(--foreground-muted)" }}>{alerta.replace("Estoque esgotado: ", "")}</div>)}
          </ChartCard>
        </div>
        <style>{`
          .dashboard-home svg { flex-shrink: 0; }
          @media (max-width: 1160px) {
            .dashboard-primary-stats { grid-template-columns: repeat(2, minmax(0,1fr)) !important; }
            .dashboard-bottom-grid { grid-template-columns: 1fr 1fr !important; }
            .dashboard-bottom-grid > :first-child { grid-column: 1 / -1; }
          }
          @media (max-width: 900px) {
            .dashboard-main-grid, .dashboard-bottom-grid { grid-template-columns: 1fr !important; }
            .dashboard-bottom-grid > :first-child { grid-column: auto; }
          }
          @media (max-width: 640px) {
            .dashboard-home { padding: 16px 12px 92px !important; gap: 9px !important; }
            .dashboard-heading { align-items: flex-start !important; }
            .dashboard-primary-stats { grid-template-columns: 1fr 1fr !important; }
            .dashboard-top-action { display: none !important; }
          }
        `}</style>
      </div>
    </ClientOnly>
  );
}
