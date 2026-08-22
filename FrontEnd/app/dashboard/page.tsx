"use client";

import { useState, useEffect, Suspense } from "react";
import Image from "next/image";
import { useSearchParams } from "next/navigation";
import {
  Home,
  Package,
  CreditCard,
  Users,
  BarChart3,
  Settings,
  DollarSign,
  Lock,
  Zap,
  Building2,
  CheckCircle2,
  X,
  ShoppingBag,
  FileText,
}from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  getUsuario,
  logout,
  removerTokenCookie,
  type Usuario,
} from "@/lib/api-v2";
import styles from "@/app/styles/dashboard.module.css";

import {
  EmpresaProvider,
  useEmpresa,
  lerCacheUsuario,
  type CaixaInfo,
  type EmpresaAtiva,
} from "./context/Empresacontext";

import DashboardHome from "./components/DashboardHome";
import Produtos from "./components/Produtos";
import Vendas from "./components/Vendas";
import Clientes from "./components/Clientes";
import Relatorios from "./components/Relatorios";
import Configuracoes from "./components/Configuracoes";
import GerenciarEmpresas from "./components/GerenciarEmpresas";
import ModalCaixa from "./components/Modalcaixa";
import SeletorEmpresa from "./components/Seletorempresa";
import Planos from "./components/Plano";
import NovoProduto from "./acoesRapidas/NovoProduto";
import NovoCliente from "./acoesRapidas/NovoCliente";
import AbrirCaixa from "./acoesRapidas/AbrirCaixa";
import PaginaAcaoRapida from "./acoesRapidas/PaginaAcaoRapida";
import MobileNav from "./components/MobileNav";
import Pedidos from "./components/Pedidos";
import NotasFiscais from "./components/NotaFiscal";
import { ThemeToggle } from "@/components/theme-toggle";

type Secao =
  | "dashboard"
  | "produtos"
  | "estoque"
  | "vendas"
  | "clientes"
  | "relatorios"
  | "configuracoes"
  | "empresas"
  | "planos"
  | "produto-rapido"
  | "cliente-rapido"
  | "caixa-rapido"
  | "pedidos"
  | "notafiscal";

const BASE =
  process.env.NEXT_PUBLIC_API_URL ??
  "https://gestpro-backend-production.up.railway.app";

async function fetchAuth<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    credentials: "include",
    headers: { "Content-Type": "application/json" },
  });
  if (!res.ok) throw new Error(`HTTP_${res.status}`);
  if (res.status === 204) return null as T;
  return res.json();
}

async function buscarCaixaAberto(empresaId: number): Promise<CaixaInfo | null> {
  try {
    const caixa = await fetchAuth<CaixaInfo>(
      `/api/v1/caixas/aberto?empresaId=${empresaId}`,
    );
    if (!caixa || !caixa.id) return null;
    if (caixa.status && caixa.status.toUpperCase() !== "ABERTO") return null;
    if (caixa.aberto === false) return null;
    return caixa;
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    if (msg.includes("HTTP_404") || msg.includes("HTTP_400")) return null;
    throw err;
  }
}

async function resolverEstadoInicial(
  empresas: EmpresaAtiva[],
  empresaCacheId?: number | null,
): Promise<{ empresa: EmpresaAtiva | null; caixa: CaixaInfo | null }> {
  if (empresas.length === 0) return { empresa: null, caixa: null };

  // Mantém a empresa escolhida mesmo sem caixa aberto.
  const empresaEmCache = empresas.find((e) => e.id === empresaCacheId);
  if (empresaEmCache) {
    try {
      const caixa = await buscarCaixaAberto(empresaEmCache.id);
      return {
        empresa: empresaEmCache,
        caixa: caixa
          ? { ...caixa, empresaNome: empresaEmCache.nomeFantasia }
          : null,
      };
    } catch (err) {
      console.warn(
        `[Gevyro] erro ao buscar caixa empresaId=${empresaEmCache.id}:`,
        err,
      );
      return { empresa: empresaEmCache, caixa: null };
    }
  }

  for (const empresa of empresas) {
    try {
      const caixa = await buscarCaixaAberto(empresa.id);
      if (caixa) {
        return {
          empresa,
          caixa: { ...caixa, empresaNome: empresa.nomeFantasia },
        };
      }
    } catch (err) {
      console.warn(
        `[Gevyro] erro ao buscar caixa empresaId=${empresa.id}:`,
        err,
      );
    }
  }

  return { empresa: empresas[0] ?? null, caixa: null };
}

function useIsMobile() {
  const [isMobile, setIsMobile] = useState(false);
  useEffect(() => {
    const mq = window.matchMedia("(max-width: 767px)");
    setIsMobile(mq.matches);
    const handler = (e: MediaQueryListEvent) => setIsMobile(e.matches);
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, []);
  return isMobile;
}

function ToastPagamento({ onClose }: { onClose: () => void }) {
  useEffect(() => {
    const t = setTimeout(onClose, 6000);
    return () => clearTimeout(t);
  }, [onClose]);

  return (
    <div
      style={{
        position: "fixed",
        top: 20,
        right: 20,
        zIndex: 9999,
        display: "flex",
        alignItems: "center",
        gap: 12,
        padding: "14px 18px",
        background: "var(--surface-elevated)",
        border: "1px solid rgba(16,185,129,0.35)",
        borderRadius: 14,
        boxShadow: "0 8px 32px rgba(0,0,0,0.25)",
        animation: "slideIn .35s cubic-bezier(.175,.885,.32,1.275)",
        maxWidth: 340,
        /* mobile: não estoura */
        left: "auto",
        width: "auto",
      }}
    >
      <div
        style={{
          width: 36,
          height: 36,
          borderRadius: 10,
          flexShrink: 0,
          background: "rgba(16,185,129,0.12)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <CheckCircle2 size={18} color="#10b981" />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <p
          style={{
            fontSize: 13,
            fontWeight: 600,
            color: "var(--foreground)",
            margin: 0,
          }}
        >
          Plano ativado com sucesso! 🎉
        </p>
        <p
          style={{
            fontSize: 12,
            color: "var(--foreground-muted)",
            margin: "2px 0 0",
          }}
        >
          Seu acesso foi atualizado. Aproveite!
        </p>
      </div>
      <button
        onClick={onClose}
        style={{
          background: "none",
          border: "none",
          padding: 4,
          cursor: "pointer",
          color: "var(--foreground-subtle)",
          display: "flex",
          alignItems: "center",
          flexShrink: 0,
          minHeight: 32,
          minWidth: 32,
          justifyContent: "center",
        }}
      >
        <X size={14} />
      </button>
      <style>{`
        @keyframes slideIn { from{transform:translateX(120%);opacity:0} to{transform:translateX(0);opacity:1} }
        @media (max-width: 767px) {
          /* Toast de pagamento não estoura no mobile */
          .toast-pagamento {
            left: 12px !important;
            right: 12px !important;
            max-width: calc(100vw - 24px) !important;
          }
        }
      `}</style>
    </div>
  );
}

function DashboardInner({
  usuario,
  mostrarToast,
  secaoInicial,
  cancelado,
}: {
  usuario: Usuario;
  mostrarToast: boolean;
  secaoInicial: Secao;
  cancelado: boolean;
}) {
  const {
    empresaAtiva,
    caixaAtivo,
    setCaixaAtivo,
    setEmpresaAtiva,
    resetarContexto,
  } = useEmpresa();
  const [secao, setSecao] = useState<Secao>(secaoInicial);

  useEffect(() => {
    setSecao(secaoInicial);
  }, [secaoInicial]);
  const [modalCaixa, setModalCaixa] = useState(false);
  const [toast, setToast] = useState(mostrarToast);
  const isMobile = useIsMobile();

  const resolverFoto = (url?: string | null) => {
    if (!url || !url.trim()) return null;
    if (
      url.startsWith("http") ||
      url.startsWith("blob:") ||
      url.startsWith("data:")
    )
      return url;
    return `${BASE}${url.startsWith("/") ? "" : "/"}${url}`;
  };

  const [fotoAtual, setFotoAtual] = useState<string | null>(
    resolverFoto(usuario.fotoUpload || usuario.foto || null),
  );
  const [nomeAtual, setNomeAtual] = useState(usuario.nome);

  const iniciais = nomeAtual
    .split(" ")
    .map((n) => n[0])
    .filter(Boolean)
    .join("")
    .toUpperCase()
    .slice(0, 2);

  const handleLogout = async () => {
    try {
      await logout();
      resetarContexto();
      globalThis.location.replace("/");
    } catch (error) {
      const mensagem = error instanceof Error ? error.message : "Não foi possível sair da conta.";
      globalThis.alert(mensagem);
    }
  };

  const tituloSecao: Record<Secao, string> = {
    dashboard: "Visão Geral",
    produtos: "Produtos",
    estoque: "Estoque",
    vendas: "Vendas",
    pedidos: "Pedidos",
    clientes: "Clientes",
    relatorios: "Relatórios",
    empresas: "Empresas",
    planos: "Planos",
    notafiscal: "Notas Fiscais",
    configuracoes: "Configurações",
    "produto-rapido": "Novo Produto",
    "cliente-rapido": "Novo Cliente",
    "caixa-rapido": "Abrir Caixa",
  
  };

  const renderSection = () => {
    switch (secao) {
      case "dashboard":
        return (
          <DashboardHome
            usuario={usuario}
            onNavegar={(s) => setSecao(s as Secao)}
          />
        );
      case "produtos":
        return <Produtos onNavegar={(s) => setSecao(s as Secao)} />;
      case "vendas":
        return <Vendas />;
      case "pedidos":
        return <Pedidos />;
      case "clientes":
        return <Clientes />;
      case "relatorios":
        return <Relatorios />;
      case "configuracoes":
        return (
          <Configuracoes
            usuario={usuario}
            onFotoAtualizada={(url) => setFotoAtual(resolverFoto(url))}
            onNomeAtualizado={setNomeAtual}
          />
        );
      case "empresas":
        return <GerenciarEmpresas />;
      case "notafiscal":
        return <NotasFiscais />;
      case "planos":
        return <Planos />;
      case "produto-rapido":
        return (
          <PaginaAcaoRapida onVoltar={() => setSecao("dashboard")}>
            <NovoProduto
              empresaId={empresaAtiva?.id ?? 0}
              onClose={() => setSecao("dashboard")}
              onConcluido={() => setSecao("dashboard")}
            />
          </PaginaAcaoRapida>
        );
      case "cliente-rapido":
        return (
          <PaginaAcaoRapida onVoltar={() => setSecao("dashboard")}>
            <NovoCliente
              empresaId={empresaAtiva?.id ?? 0}
              onClose={() => setSecao("dashboard")}
              onConcluido={() => setSecao("dashboard")}
            />
          </PaginaAcaoRapida>
        );
      case "caixa-rapido":
        return (
          <PaginaAcaoRapida onVoltar={() => setSecao("dashboard")}>
            <AbrirCaixa onConcluido={() => setSecao("dashboard")} />
          </PaginaAcaoRapida>
        );
      default:
        return (
          <DashboardHome
            usuario={usuario}
            onNavegar={(s) => setSecao(s as Secao)}
          />
        );
    }
  };

  const nomeEmpresaCaixa =
    empresaAtiva?.nomeFantasia ?? caixaAtivo?.empresaNome ?? "";

  return (
    <div className={`${styles.dashboardContainer} dashboard-mobile-scope`}>
      {toast && <ToastPagamento onClose={() => setToast(false)} />}

      {cancelado && secao === "planos" && (
        <div
          style={{
            position: "fixed",
            top: 20,
            left: "50%",
            transform: "translateX(-50%)",
            zIndex: 9999,
            display: "flex",
            alignItems: "center",
            gap: 10,
            padding: "12px 18px",
            background: "var(--surface-elevated)",
            border: "1px solid rgba(245,158,11,0.35)",
            borderRadius: 12,
            boxShadow: "0 8px 24px rgba(0,0,0,0.2)",
            fontSize: 13,
            color: "#d97706",
            /* mobile: não estoura */
            maxWidth: "calc(100vw - 24px)",
            whiteSpace: "normal",
            textAlign: "center",
          }}
        >
          Pagamento cancelado. Você pode assinar quando quiser.
        </div>
      )}

      {/* Header */}
      <header className={styles.dashboardHeader}>
        <div className={styles.headerBrand}>
          <div className={styles.headerLogo} role="img" aria-label="Gevyro">
            <Image
              src="/images/gevyro-logo.png"
              alt=""
              width={2083}
              height={755}
              priority
              className={styles.dashboardLogoLight}
            />
            <span className={styles.dashboardLogoDark} aria-hidden="true">
              <Image
                src="/images/gevyro-logo.png"
                alt=""
                width={2083}
                height={755}
                priority
                className={styles.dashboardLogoDarkWordmark}
              />
              <Image
                src="/images/gevyro-logo.png"
                alt=""
                width={2083}
                height={755}
                priority
                className={styles.dashboardLogoDarkSymbol}
              />
            </span>
          </div>
          <div
            className={styles.headerTitle}
            style={{
              marginLeft: 16,
              paddingLeft: 16,
              borderLeft: "1px solid var(--border)",
            }}
          >
            <p
              style={{
                fontSize: 10,
                color: "var(--foreground-subtle)",
                margin: 0,
                textTransform: "uppercase",
                letterSpacing: ".06em",
              }}
            >
              GEVYRO
            </p>
            <p
              style={{
                fontSize: 14,
                fontWeight: 600,
                color: "var(--foreground)",
                margin: 0,
              }}
            >
              {tituloSecao[secao]}
            </p>
          </div>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: isMobile ? 4 : 12 }}>
          <ThemeToggle compact={isMobile} />
          {/* SeletorEmpresa: oculto no mobile apenas se não houver caixa (simplifica header) */}
          <SeletorEmpresa
            empresaAtiva={empresaAtiva}
            onSelecionar={(empresa) => {
              if (empresaAtiva?.id === empresa.id) return;

              // A persistência do contexto é síncrona. Limpamos o caixa da
              // empresa anterior antes de recarregar para que o DashboardLoader
              // reconstrua toda a página somente com dados da nova empresa.
              setEmpresaAtiva(empresa);
              setCaixaAtivo(null);
              globalThis.location.reload();
            }}
            onNova={() => setSecao("empresas")}
          />
          <button
            onClick={() => setModalCaixa(true)}
            style={{
              display: "flex",
              alignItems: "center",
              gap: isMobile ? 5 : 7,
              padding: isMobile ? "4px 7px" : "7px 14px",
              background: caixaAtivo ? "rgba(16,185,129,0.12)" : "transparent",
              border: `1px solid ${caixaAtivo ? "rgba(16,185,129,0.4)" : "var(--border)"}`,
              borderRadius: 8,
              color: caixaAtivo ? "var(--primary)" : "var(--foreground-muted)",
              fontSize: isMobile ? 9 : 12,
              fontWeight: 500,
              cursor: "pointer",
              transition: "all .15s",
              whiteSpace: "nowrap",
              minHeight: isMobile ? 29 : 36,
            }}
          >
            {caixaAtivo ? <DollarSign size={14} /> : <Lock size={14} />}
            {/* No mobile, mostra só o ícone se não tiver caixa aberto */}
            {isMobile
              ? caixaAtivo
                ? `Caixa · ${nomeEmpresaCaixa.split(" ")[0]}`
                : "Caixa"
              : caixaAtivo
                ? `Caixa Aberto · ${nomeEmpresaCaixa}`
                : "Abrir Caixa"}
          </button>
          <div className={styles.headerUser}>
            <span className={styles.headerUserName}>
              {nomeAtual.split(" ")[0]}!
            </span>
            {fotoAtual ? (
              <img
                key={fotoAtual}
                src={fotoAtual}
                alt={nomeAtual}
                className={styles.headerUserAvatar}
                onError={() => setFotoAtual(null)}
                referrerPolicy="no-referrer"
              />
            ) : (
              <div className={styles.headerUserInitials}>{iniciais}</div>
            )}
            {!isMobile && (
              <Button
                onClick={handleLogout}
                variant="ghost"
                className="text-[var(--foreground)] hover:bg-[var(--surface-overlay)] hover:text-[var(--primary)]"
              >
                Sair
              </Button>
            )}
          </div>
        </div>
      </header>

      {/* Layout */}
      <div className={styles.dashboardLayout}>
        {/* Sidebar: apenas no desktop */}
        {!isMobile && (
          <aside className={styles.sidebar}>
            <nav className={styles.sidebarNav}>
              {(
                [
                  { id: "dashboard", label: "Dashboard", icon: <Home /> },
                  { id: "produtos", label: "Produtos", icon: <Package /> },
                  { id: "vendas", label: "Vendas", icon: <CreditCard /> },
                  { id: "pedidos", label: "Pedidos", icon: <ShoppingBag /> },
                  { id: "clientes", label: "Clientes", icon: <Users /> },
                  { id: "relatorios", label: "Relatórios", icon: <BarChart3 /> },
                  { id: "empresas", label: "Empresas", icon: <Building2 /> },
                  { id: "notafiscal", label: "Notas Fiscais", icon: <FileText /> },
                  {
                    id: "configuracoes",
                    label: "Configurações",
                    icon: <Settings />,
                  },
                  { id: "planos", label: "Planos", icon: <Zap /> },
                ] as { id: Secao; label: string; icon: React.ReactNode }[]
              ).map((item) => (
                <button
                  key={item.id}
                  onClick={() => setSecao(item.id)}
                  className={`${styles.sidebarNavItem} ${secao === item.id ? styles.sidebarNavItemActive : ""}`}
                >
                  {item.icon}
                  <span>{item.label}</span>
                </button>
              ))}
            </nav>
          </aside>
        )}

        <main className={styles.mainContent}>{renderSection()}</main>
      </div>

      {/* Bottom Nav — apenas mobile */}
      {isMobile && (
        <MobileNav
          secao={secao}
          onChange={setSecao}
          caixaAtivo={!!caixaAtivo}
          onLogout={handleLogout}
        />
      )}

      {modalCaixa && (
        <ModalCaixa
          onClose={() => setModalCaixa(false)}
          caixaAtivo={caixaAtivo}
          empresaAtiva={empresaAtiva}
          onCaixaAtualizado={(caixa, empresa) => {
            setCaixaAtivo(caixa);
            if (empresa) {
              setEmpresaAtiva(empresa);
            }
          }}
        />
      )}
    </div>
  );
}

function DashboardLoader() {
  const searchParams = useSearchParams();
  const { inicializarUsuario } = useEmpresa();

  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [loading, setLoading] = useState(true);

  const mostrarToast = searchParams.get("payment") === "success";
  const cancelado = searchParams.get("canceled") === "true";
  const secaoInicial = (searchParams.get("section") as Secao) ?? "dashboard";

  useEffect(() => {
    let desmontado = false;

    async function inicializar() {
      if (searchParams.has("token")) globalThis.history.replaceState({}, "", "/dashboard");

      let data: Usuario;
      try {
        const resultado = await getUsuario();
        if (!resultado) throw new Error("sem usuário");
        data = resultado;
      } catch {
        removerTokenCookie();
        globalThis.location.href = "/auth/login";
        return;
      }

      if (desmontado) return;

      // Versões antigas de /api/usuario não retornavam id. O e-mail
      // normalizado mantém o cache isolado até backend e frontend atualizarem.
      const uid = data.id > 0
        ? String(data.id)
        : data.email.trim().toLowerCase();
      const cache = lerCacheUsuario(uid);

      let empresaResolvida: EmpresaAtiva | null = null;
      let caixaResolvido: CaixaInfo | null = null;

      try {
        const empresas = (await fetchAuth<EmpresaAtiva[]>("/api/v1/empresas"))
          .filter((empresa) => empresa.ativo !== false);
        if (desmontado) return;

        if (empresas.length > 0) {
          const { empresa, caixa } = await resolverEstadoInicial(
            empresas,
            cache.empresaAtiva?.id,
          );
          if (desmontado) return;

          empresaResolvida = empresa;
          caixaResolvido = caixa;
        }
      } catch (err) {
        console.warn("[Gevyro] falha ao buscar empresas:", err);
      }

      if (desmontado) return;

      inicializarUsuario(uid, empresaResolvida, caixaResolvido);
      setUsuario(data);
      setLoading(false);
    }

    inicializar();
    return () => {
      desmontado = true;
    };
  }, [searchParams, inicializarUsuario]);

  if (loading)
    return (
      <div
        style={{
          height: "100vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "var(--background, #0a0a0a)",
        }}
      >
        <p style={{ color: "var(--foreground-muted, #888)", fontSize: 14 }}>
          Carregando Gevyro...
        </p>
      </div>
    );

  if (!usuario) return null;

  return (
    <DashboardInner
      usuario={usuario}
      mostrarToast={mostrarToast}
      secaoInicial={secaoInicial}
      cancelado={cancelado}
    />
  );
}

export default function DashboardPage() {
  return (
    <EmpresaProvider>
      <Suspense
        fallback={
          <div
            style={{
              height: "100vh",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              background: "var(--background, #0a0a0a)",
            }}
          >
            <p style={{ color: "var(--foreground-muted, #888)", fontSize: 14 }}>
              Carregando...
            </p>
          </div>
        }
      >
        <DashboardLoader />
      </Suspense>
    </EmpresaProvider>
  );
}
