"use client";

import { useState, useEffect, useRef } from "react";
import { Store, ChevronDown, CheckCircle, Plus } from "lucide-react";

interface Empresa {
  id: number;
  nomeFantasia: string;
  cnpj?: string | null;
  razaoSocial?: string | null;
  planoNome?: string | null;
  ativo?: boolean; // Importante: deve estar presente para o filtro
}

interface CaixaInfo {
  id: number;
  status: "ABERTO" | "FECHADO";
  totalVendas: number;
}

interface Props {
  empresaAtiva: Empresa | null;
  onSelecionar: (empresa: Empresa) => void;
  onNova: () => void;
}

async function fetchAuth<T>(path: string, opts?: RequestInit): Promise<T> {
  const res = await fetch(
    `${process.env.NEXT_PUBLIC_API_URL ?? "https://api.gevyro.com.br"}${path}`,
    {
      credentials: "include",
      cache: "no-store",
      headers: { "Content-Type": "application/json" },
      ...opts,
    },
  );
  if (!res.ok) throw new Error(`Erro ${res.status}`);
  if (res.status === 204) return null as T;
  return res.json();
}

export default function SeletorEmpresa({
  empresaAtiva,
  onSelecionar,
  onNova,
}: Props) {
  const [aberto, setAberto] = useState(false);
  const [empresas, setEmpresas] = useState<Empresa[]>([]);
  const [caixas, setCaixas] = useState<Record<number, CaixaInfo | null>>({});
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fetchAuth<Empresa[]>("/api/v1/empresas")
      .then((data) => {
        // Filtramos para manter apenas as empresas que não estão marcadas como inativas
        const apenasAtivas = data.filter(emp => emp.ativo !== false);
        setEmpresas(apenasAtivas);
      })
      .catch(() => {});
  }, []);

  // Verifica caixa aberto por empresa ao abrir dropdown
  useEffect(() => {
    if (!aberto) return;
    empresas.forEach(async (emp) => {
      try {
        const c = await fetchAuth<CaixaInfo | null>(
          `/api/v1/caixas/aberto?empresaId=${emp.id}`,
        );
        setCaixas((prev) => ({ ...prev, [emp.id]: c }));
      } catch {
        setCaixas((prev) => ({ ...prev, [emp.id]: null }));
      }
    });
  }, [aberto, empresas]);

  // Fecha ao clicar fora
  useEffect(() => {
    const fn = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node))
        setAberto(false);
    };
    document.addEventListener("mousedown", fn);
    return () => document.removeEventListener("mousedown", fn);
  }, []);

  return (
    <div ref={ref} className="empresa-selector" style={{ position: "relative" }}>
      <button
        className="empresa-selector-trigger"
        onClick={() => setAberto((a) => !a)}
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          padding: "7px 12px",
          background: "var(--surface-elevated)",
          border: "1px solid var(--border)",
          borderRadius: 8,
          color: "var(--foreground)",
          fontSize: 13,
          fontWeight: 500,
          cursor: "pointer",
          transition: "all .15s",
        }}
        onMouseEnter={(e) =>
          ((e.currentTarget as HTMLButtonElement).style.borderColor =
            "var(--primary)")
        }
        onMouseLeave={(e) =>
          ((e.currentTarget as HTMLButtonElement).style.borderColor =
            "var(--border)")
        }
      >
        <Store size={14} color="var(--primary)" />
        <span
          style={{
            maxWidth: 140,
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {empresaAtiva?.nomeFantasia ?? "Selecionar empresa"}
        </span>
        <ChevronDown
          size={13}
          color="var(--foreground-muted)"
          style={{
            transform: aberto ? "rotate(180deg)" : "none",
            transition: "transform .2s",
          }}
        />
      </button>

      {aberto && (
        <div
          className="empresa-selector-menu"
          style={{
            position: "absolute",
            top: "calc(100% + 8px)",
            left: 0,
            minWidth: 260,
            zIndex: 1000,
            background: "var(--surface-elevated)",
            border: "1px solid var(--border)",
            borderRadius: 12,
            overflow: "hidden",
            boxShadow: "0 8px 32px rgba(0,0,0,0.4)",
            animation: "fadeIn .15s ease",
          }}
        >
          <div
            style={{
              padding: "10px 12px 6px",
              borderBottom: "1px solid var(--border)",
            }}
          >
            <p
              style={{
                fontSize: 10,
                fontWeight: 600,
                color: "var(--foreground-muted)",
                textTransform: "uppercase",
                letterSpacing: ".07em",
                margin: 0,
              }}
            >
              Suas Empresas Ativas
            </p>
          </div>

          {empresas.length === 0 ? (
            <div
              style={{
                padding: "16px 14px",
                color: "var(--foreground-subtle)",
                fontSize: 13,
                textAlign: "center",
              }}
            >
              Nenhuma empresa ativa cadastrada
            </div>
          ) : (
            empresas.map((emp) => {
              const caixa = caixas[emp.id];
              const ativa = empresaAtiva?.id === emp.id;
              return (
                <div
                  key={emp.id}
                  onClick={() => {
                    onSelecionar(emp);
                    setAberto(false);
                  }}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    padding: "10px 14px",
                    background: ativa ? "var(--primary-muted)" : "transparent",
                    cursor: "pointer",
                    transition: "background .15s",
                  }}
                  onMouseEnter={(e) =>
                    !ativa &&
                    ((e.currentTarget as HTMLDivElement).style.background =
                      "var(--surface-overlay)")
                  }
                  onMouseLeave={(e) =>
                    !ativa &&
                    ((e.currentTarget as HTMLDivElement).style.background =
                      "transparent")
                  }
                >
                  <div
                    style={{ display: "flex", alignItems: "center", gap: 10 }}
                  >
                    <div
                      style={{
                        width: 32,
                        height: 32,
                        borderRadius: 8,
                        background: ativa
                          ? "var(--primary-muted)"
                          : "var(--surface-overlay)",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                      }}
                    >
                      <Store
                        size={15}
                        color={
                          ativa ? "var(--primary)" : "var(--foreground-muted)"
                        }
                      />
                    </div>
                    <div>
                      <p
                        style={{
                          fontSize: 13,
                          fontWeight: 500,
                          color: ativa ? "var(--primary)" : "var(--foreground)",
                          margin: 0,
                        }}
                      >
                        {emp.nomeFantasia}
                      </p>
                      <p
                        style={{
                          fontSize: 11,
                          color: "var(--foreground-subtle)",
                          margin: "2px 0 0",
                        }}
                      >
                        {caixa === undefined ? (
                          "Verificando..."
                        ) : caixa ? (
                          <span style={{ color: "var(--primary)" }}>
                            ● Caixa aberto
                          </span>
                        ) : (
                          "Caixa fechado"
                        )}
                      </p>
                    </div>
                  </div>
                  {ativa && <CheckCircle size={15} color="var(--primary)" />}
                </div>
              );
            })
          )}

          <div
            style={{
              padding: "8px 12px",
              borderTop: "1px solid var(--border)",
            }}
          >
            <button
              onClick={() => {
                onNova();
                setAberto(false);
              }}
              style={{
                width: "100%",
                display: "flex",
                alignItems: "center",
                gap: 8,
                padding: "8px 10px",
                background: "transparent",
                border: "none",
                borderRadius: 7,
                color: "var(--foreground-muted)",
                fontSize: 13,
                cursor: "pointer",
                textAlign: "left",
                transition: "all .15s",
              }}
              onMouseEnter={(e) => {
                (e.currentTarget as HTMLButtonElement).style.background =
                  "var(--surface-overlay)";
                (e.currentTarget as HTMLButtonElement).style.color =
                  "var(--foreground)";
              }}
              onMouseLeave={(e) => {
                (e.currentTarget as HTMLButtonElement).style.background =
                  "transparent";
                (e.currentTarget as HTMLButtonElement).style.color =
                  "var(--foreground-muted)";
              }}
            >
              <Plus size={14} /> Nova empresa
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
