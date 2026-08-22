"use client";

import { useEffect, useState, useMemo } from "react";
import { useEmpresa } from "../context/Empresacontext";
import {
  Plus,
  X,
  Check,
  Search,
  Edit2,
  Trash2,
  Users,
  Store,
  Phone,
  Mail,
  CreditCard,
  Truck,
  ChevronUp,
  ChevronDown,
  Building2,
  FileText,
  User,
  DollarSign,
} from "lucide-react";
import { toast } from "sonner";
import { fetchAuthJson } from "@/lib/api-v2";

type Tipo = "CLIENTE" | "FORNECEDOR";

interface Contato {
  id: number;
  nome: string;
  email?: string;
  telefone?: string;
  cpf?: string;
  cnpj?: string;
  contato?: string;
  observacoes?: string;
  tipo: Tipo;
  ativo: boolean;
  empresaId: number;
  deveAlgo?: boolean;
}

interface ContatoForm {
  nome: string;
  email: string;
  telefone: string;
  cpf: string;
  cnpj: string;
  contato: string;
  observacoes: string;
  tipo: Tipo;
}

const FORM_VAZIO: ContatoForm = {
  nome: "",
  email: "",
  telefone: "",
  cpf: "",
  cnpj: "",
  contato: "",
  observacoes: "",
  tipo: "CLIENTE",
};

interface Divida {
  id: number;
  descricao: string;
  valor: number;
  valorPago: number;
  saldoRestante: number;
  status: "ABERTA" | "PARCIAL" | "QUITADA";
  vencimento?: string;
  criadoEm: string;
  quitadoEm?: string;
}

async function fetchAuth<T>(path: string, opts?: RequestInit): Promise<T> {
  return fetchAuthJson<T>(path, opts);
}

const fmtCpf = (v: string) =>
  v.replace(/\D/g, "").replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, "$1.$2.$3-$4");

const fmtCnpj = (v: string) =>
  v
    .replace(/\D/g, "")
    .replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");

const inp: React.CSSProperties = {
  width: "100%",
  padding: "9px 12px",
  background: "var(--surface-overlay)",
  border: "1px solid var(--border)",
  borderRadius: 8,
  color: "var(--foreground)",
  fontSize: 13,
  outline: "none",
};
const btnP: React.CSSProperties = {
  display: "flex",
  alignItems: "center",
  gap: 6,
  padding: "9px 16px",
  background: "var(--primary)",
  border: "none",
  borderRadius: 8,
  color: "#fff",
  fontSize: 13,
  fontWeight: 600,
  cursor: "pointer",
};
const btnG: React.CSSProperties = {
  display: "flex",
  alignItems: "center",
  gap: 6,
  padding: "8px 12px",
  background: "transparent",
  border: "1px solid var(--border)",
  borderRadius: 8,
  color: "var(--foreground-muted)",
  fontSize: 13,
  cursor: "pointer",
};

function ModalContato({
  item,
  tipoInicial,
  onSave,
  onClose,
  saving,
  error,
}: {
  item?: Contato;
  tipoInicial: Tipo;
  onSave: (f: ContatoForm) => Promise<void>;
  onClose: () => void;
  saving: boolean;
  error?: string;
}) {
  const [form, setForm] = useState<ContatoForm>(
    item
      ? {
          nome: item.nome ?? "",
          email: item.email ?? "",
          telefone: item.telefone ?? "",
          cpf: item.cpf ?? "",
          cnpj: item.cnpj ?? "",
          contato: item.contato ?? "",
          observacoes: item.observacoes ?? "",
          tipo: item.tipo,
        }
      : { ...FORM_VAZIO, tipo: tipoInicial },
  );
  const [validationError, setValidationError] = useState("");

  const set = (k: keyof ContatoForm, v: string) => {
    setValidationError("");
    setForm((f) => ({ ...f, [k]: v }));
  };
  const isForn = form.tipo === "FORNECEDOR";

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.75)",
        backdropFilter: "blur(4px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 50,
        padding: 16,
      }}
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div
        className="animate-fade-in"
        style={{
          background: "var(--surface-elevated)",
          border: "1px solid var(--border)",
          borderRadius: 14,
          padding: 26,
          width: "100%",
          maxWidth: 520,
          maxHeight: "90vh",
          overflowY: "auto",
        }}
      >
        {/* Header */}
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: 20,
          }}
        >
          <h2
            style={{
              fontSize: 15,
              fontWeight: 700,
              color: "var(--foreground)",
              margin: 0,
            }}
          >
            {item ? "Editar" : "Novo"}{" "}
            {form.tipo === "CLIENTE" ? "Cliente" : "Fornecedor"}
          </h2>
          <button
            onClick={onClose}
            style={{ ...btnG, padding: 6, border: "none" }}
          >
            <X size={16} />
          </button>
        </div>

        {/* Tipo (só para criação) */}
        {!item && (
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "1fr 1fr",
              gap: 8,
              marginBottom: 18,
            }}
          >
            {(["CLIENTE", "FORNECEDOR"] as Tipo[]).map((t) => (
              <button
                key={t}
                onClick={() => set("tipo", t)}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 8,
                  padding: "10px 14px",
                  background:
                    form.tipo === t
                      ? "var(--primary-muted)"
                      : "var(--surface-overlay)",
                  border: `1px solid ${form.tipo === t ? "var(--primary)" : "var(--border)"}`,
                  borderRadius: 9,
                  cursor: "pointer",
                  color:
                    form.tipo === t
                      ? "var(--primary)"
                      : "var(--foreground-muted)",
                  fontSize: 13,
                  fontWeight: form.tipo === t ? 600 : 400,
                }}
              >
                {t === "CLIENTE" ? <User size={15} /> : <Truck size={15} />}
                {t === "CLIENTE" ? "Cliente" : "Fornecedor"}
              </button>
            ))}
          </div>
        )}

        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {(validationError || error) && (
            <div role="alert" style={{ padding: "10px 12px", borderRadius: 9, border: "1px solid rgba(239,68,68,.35)", background: "var(--destructive-muted)", color: "var(--destructive)", fontSize: 12, lineHeight: 1.5 }}>
              {validationError || error}
            </div>
          )}
          <div>
            <label
              style={{
                fontSize: 12,
                color: "var(--foreground-muted)",
                display: "block",
                marginBottom: 5,
                fontWeight: 500,
              }}
            >
              Nome *
            </label>
            <input
              style={inp}
              value={form.nome}
              onChange={(e) => set("nome", e.target.value)}
              placeholder={
                isForn ? "Razão social ou nome fantasia" : "Nome completo"
              }
              required
            />
          </div>

          <div
            style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}
          >
            <div>
              <label
                style={{
                  fontSize: 12,
                  color: "var(--foreground-muted)",
                  display: "block",
                  marginBottom: 5,
                  fontWeight: 500,
                }}
              >
                <Phone size={11} style={{ marginRight: 4 }} />
                Telefone
              </label>
              <input
                style={inp}
                value={form.telefone}
                onChange={(e) => set("telefone", e.target.value)}
                placeholder="(00) 00000-0000"
              />
            </div>
            <div>
              <label
                style={{
                  fontSize: 12,
                  color: "var(--foreground-muted)",
                  display: "block",
                  marginBottom: 5,
                  fontWeight: 500,
                }}
              >
                <Mail size={11} style={{ marginRight: 4 }} />
                E-mail
              </label>
              <input
                style={inp}
                type="email"
                value={form.email}
                onChange={(e) => set("email", e.target.value)}
                placeholder="email@exemplo.com"
              />
            </div>
          </div>

          {/* Campos por tipo */}
          {!isForn && (
            <div>
              <label
                style={{
                  fontSize: 12,
                  color: "var(--foreground-muted)",
                  display: "block",
                  marginBottom: 5,
                  fontWeight: 500,
                }}
              >
                <CreditCard size={11} style={{ marginRight: 4 }} />
                CPF
              </label>
              <input
                style={inp}
                value={form.cpf}
                onChange={(e) => set("cpf", e.target.value)}
                placeholder="000.000.000-00"
                maxLength={14}
              />
            </div>
          )}

          {isForn && (
            <>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: 12,
                }}
              >
                <div>
                  <label
                    style={{
                      fontSize: 12,
                      color: "var(--foreground-muted)",
                      display: "block",
                      marginBottom: 5,
                      fontWeight: 500,
                    }}
                  >
                    <Building2 size={11} style={{ marginRight: 4 }} />
                    CNPJ
                  </label>
                  <input
                    style={inp}
                    value={form.cnpj}
                    onChange={(e) => set("cnpj", e.target.value)}
                    placeholder="00.000.000/0000-00"
                    maxLength={18}
                  />
                </div>
                <div>
                  <label
                    style={{
                      fontSize: 12,
                      color: "var(--foreground-muted)",
                      display: "block",
                      marginBottom: 5,
                      fontWeight: 500,
                    }}
                  >
                    <User size={11} style={{ marginRight: 4 }} />
                    Contato
                  </label>
                  <input
                    style={inp}
                    value={form.contato}
                    onChange={(e) => set("contato", e.target.value)}
                    placeholder="Nome do contato"
                  />
                </div>
              </div>
              <div>
                <label
                  style={{
                    fontSize: 12,
                    color: "var(--foreground-muted)",
                    display: "block",
                    marginBottom: 5,
                    fontWeight: 500,
                  }}
                >
                  <FileText size={11} style={{ marginRight: 4 }} />
                  Observações
                </label>
                <textarea
                  style={{ ...inp, resize: "vertical", minHeight: 60 }}
                  value={form.observacoes}
                  onChange={(e) => set("observacoes", e.target.value)}
                  placeholder="Produtos fornecidos, condições, etc..."
                />
              </div>
            </>
          )}

          <div style={{ display: "flex", gap: 10, marginTop: 4 }}>
            <button
              type="button"
              onClick={onClose}
              style={{ ...btnG, flex: 1, justifyContent: "center" }}
            >
              Cancelar
            </button>
            <button
              onClick={() => {
                if (!form.nome.trim()) {
                  setValidationError("Preencha o campo Nome.");
                  return;
                }
                if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
                  setValidationError("Informe um e-mail válido, como nome@empresa.com.br.");
                  return;
                }
                if (!isForn && form.cpf && form.cpf.replace(/\D/g, "").length !== 11) {
                  setValidationError("O CPF deve conter 11 números.");
                  return;
                }
                if (isForn && form.cnpj && form.cnpj.replace(/\D/g, "").length !== 14) {
                  setValidationError("O CNPJ deve conter 14 números.");
                  return;
                }
                onSave(form);
              }}
              disabled={saving}
              style={{
                ...btnP,
                flex: 2,
                justifyContent: "center",
                opacity: saving ? 0.7 : 1,
              }}
            >
              {saving ? (
                "Salvando..."
              ) : (
                <>
                  <Check size={14} />
                  {item ? "Salvar" : "Cadastrar"}
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function ModalDividas({
  cliente,
  empresaId,
  onClose,
}: {
  cliente: Contato;
  empresaId: number;
  onClose: () => void;
}) {
  const [dividas, setDividas] = useState<Divida[]>([]);
  const [loading, setLoading] = useState(true);
  const [novaDesc, setNovaDesc] = useState("");
  const [novoValor, setNovoValor] = useState("");
  const [novoVenc, setNovoVenc] = useState("");
  const [salvando, setSalvando] = useState(false);
  const [pagandoId, setPagandoId] = useState<number | null>(null);
  const [valorPag, setValorPag] = useState("");

  const fmt = (v?: number | null) =>
    new Intl.NumberFormat("pt-BR", {
      style: "currency",
      currency: "BRL",
    }).format(v ?? 0);

  const carregar = async () => {
    setLoading(true);
    try {
      const data = await fetchAuth<Divida[]>(
        `/api/v1/dividas/cliente/${cliente.id}`,
      );
      setDividas(data);
    } catch {
      toast.error("Erro ao carregar dívidas");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    carregar();
  }, [cliente.id]);

  const abertas = dividas.filter((d) => d.status !== "QUITADA");
  const quitadas = dividas.filter((d) => d.status === "QUITADA");
  const totalDevido = abertas.reduce((s, d) => s + d.saldoRestante, 0);

  const criarDivida = async () => {
    if (!novaDesc.trim() || !novoValor) {
      toast.error("Preencha descrição e valor");
      return;
    }
    setSalvando(true);
    try {
      const nova = await fetchAuth<Divida>("/api/v1/dividas", {
        method: "POST",
        body: JSON.stringify({
          clienteId: cliente.id,
          empresaId,
          descricao: novaDesc,
          valor: parseFloat(novoValor),
          vencimento: novoVenc || null,
        }),
      });
      setDividas((p) => [nova, ...p]);
      setNovaDesc("");
      setNovoValor("");
      setNovoVenc("");
      toast.success("Dívida registrada!");
    } catch (e: any) {
      toast.error(e.message);
    } finally {
      setSalvando(false);
    }
  };

  const registrarPagamento = async (id: number) => {
    const v = parseFloat(valorPag);
    if (!v || v <= 0) {
      toast.error("Valor inválido");
      return;
    }
    try {
      const atualizada = await fetchAuth<Divida>(
        `/api/v1/dividas/${id}/pagamento?valor=${v}`,
        { method: "PATCH" },
      );
      setDividas((p) => p.map((d) => (d.id === id ? atualizada : d)));
      setPagandoId(null);
      setValorPag("");
      toast.success(
        atualizada.status === "QUITADA"
          ? "Dívida quitada! ✅"
          : "Pagamento registrado!",
      );
    } catch (e: any) {
      toast.error(e.message);
    }
  };

  const STATUS_COLOR = {
    ABERTA: {
      bg: "rgba(239,68,68,.1)",
      color: "#ef4444",
      label: "Em aberto",
    },
    PARCIAL: {
      bg: "rgba(245,158,11,.1)",
      color: "#f59e0b",
      label: "Parcial",
    },
    QUITADA: {
      bg: "rgba(16,185,129,.1)",
      color: "#10b981",
      label: "Quitada",
    },
  };

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.78)",
        backdropFilter: "blur(5px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 100,
        padding: 16,
      }}
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div
        style={{
          background: "var(--surface-elevated)",
          border: "1px solid var(--border)",
          borderRadius: 14,
          width: "100%",
          maxWidth: 560,
          maxHeight: "90vh",
          overflowY: "auto",
        }}
      >
        {/* Header */}
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            padding: "16px 20px",
            borderBottom: "1px solid var(--border)",
          }}
        >
          <div>
            <h2
              style={{
                fontSize: 15,
                fontWeight: 700,
                color: "var(--foreground)",
                margin: 0,
              }}
            >
              Dívidas — {cliente.nome}
            </h2>
            <p
              style={{
                fontSize: 12,
                color: "var(--foreground-muted)",
                margin: "3px 0 0",
              }}
            >
              {abertas.length} pendente(s) · Total:{" "}
              <span
                style={{
                  color: totalDevido > 0 ? "#ef4444" : "#10b981",
                  fontWeight: 700,
                }}
              >
                {fmt(totalDevido)}
              </span>
            </p>
          </div>
          <button
            onClick={onClose}
            style={{
              background: "none",
              border: "none",
              cursor: "pointer",
              color: "var(--foreground-muted)",
            }}
          >
            <X size={18} />
          </button>
        </div>

        <div
          style={{
            padding: 20,
            display: "flex",
            flexDirection: "column",
            gap: 16,
          }}
        >
          {/* Nova dívida */}
          <div
            style={{
              background: "var(--surface-overlay)",
              borderRadius: 10,
              padding: "14px 16px",
              border: "1px solid var(--border)",
            }}
          >
            <p
              style={{
                fontSize: 11,
                fontWeight: 700,
                color: "var(--foreground-muted)",
                textTransform: "uppercase",
                letterSpacing: ".07em",
                margin: "0 0 12px",
              }}
            >
              Registrar Nova Dívida
            </p>
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              <input
                style={inp}
                placeholder="Descrição (ex: compra fiado, empréstimo...)"
                value={novaDesc}
                onChange={(e) => setNovaDesc(e.target.value)}
              />
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: 8,
                }}
              >
                <input
                  style={inp}
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="Valor R$"
                  value={novoValor}
                  onChange={(e) => setNovoValor(e.target.value)}
                />
                <input
                  style={inp}
                  type="date"
                  value={novoVenc}
                  onChange={(e) => setNovoVenc(e.target.value)}
                />
              </div>
              <button
                onClick={criarDivida}
                disabled={salvando}
                style={{ ...btnP, opacity: salvando ? 0.7 : 1 }}
              >
                <Plus size={14} />{" "}
                {salvando ? "Salvando..." : "Adicionar Dívida"}
              </button>
            </div>
          </div>

          {/* Dívidas abertas */}
          {abertas.length > 0 && (
            <div>
              <p
                style={{
                  fontSize: 11,
                  fontWeight: 700,
                  color: "var(--foreground-muted)",
                  textTransform: "uppercase",
                  letterSpacing: ".07em",
                  marginBottom: 8,
                }}
              >
                Pendentes ({abertas.length})
              </p>
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {abertas.map((d) => {
                  const sc = STATUS_COLOR[d.status];
                  return (
                    <div
                      key={d.id}
                      style={{
                        background: "var(--surface-overlay)",
                        borderRadius: 10,
                        padding: "12px 14px",
                        border: `1px solid ${sc.bg}`,
                      }}
                    >
                      <div
                        style={{
                          display: "flex",
                          justifyContent: "space-between",
                          marginBottom: 6,
                        }}
                      >
                        <span
                          style={{
                            fontSize: 13,
                            fontWeight: 600,
                            color: "var(--foreground)",
                          }}
                        >
                          {d.descricao}
                        </span>
                        <span
                          style={{
                            fontSize: 11,
                            padding: "2px 8px",
                            borderRadius: 99,
                            background: sc.bg,
                            color: sc.color,
                            fontWeight: 600,
                          }}
                        >
                          {sc.label}
                        </span>
                      </div>
                      <div
                        style={{
                          display: "flex",
                          justifyContent: "space-between",
                          fontSize: 12,
                          color: "var(--foreground-muted)",
                          marginBottom: 6,
                        }}
                      >
                        <span>Total: {fmt(d.valor)}</span>
                        {d.valorPago > 0 && (
                          <span>Pago: {fmt(d.valorPago)}</span>
                        )}
                        <span style={{ color: sc.color, fontWeight: 700 }}>
                          Restante: {fmt(d.saldoRestante)}
                        </span>
                      </div>
                      {d.vencimento && (
                        <p
                          style={{
                            fontSize: 11,
                            color: "var(--foreground-subtle)",
                            margin: "0 0 8px",
                          }}
                        >
                          Vence:{" "}
                          {new Date(d.vencimento).toLocaleDateString("pt-BR")}
                        </p>
                      )}
                      {pagandoId === d.id ? (
                        <div style={{ display: "flex", gap: 6 }}>
                          <input
                            style={{ ...inp, flex: 1 }}
                            type="number"
                            min="0"
                            step="0.01"
                            placeholder="Valor pago R$"
                            value={valorPag}
                            onChange={(e) => setValorPag(e.target.value)}
                            autoFocus
                          />
                          <button
                            onClick={() => registrarPagamento(d.id)}
                            style={{ ...btnP, padding: "9px 14px" }}
                          >
                            <Check size={13} />
                          </button>
                          <button
                            onClick={() => {
                              setPagandoId(null);
                              setValorPag("");
                            }}
                            style={{ ...btnG, padding: "9px 10px" }}
                          >
                            <X size={13} />
                          </button>
                        </div>
                      ) : (
                        <button
                          onClick={() => {
                            setPagandoId(d.id);
                            setValorPag(String(d.saldoRestante));
                          }}
                          style={{
                            ...btnP,
                            fontSize: 12,
                            padding: "7px 12px",
                            background: "var(--primary)",
                          }}
                        >
                          <DollarSign size={13} /> Registrar Pagamento
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Histórico quitadas */}
          {quitadas.length > 0 && (
            <div>
              <p
                style={{
                  fontSize: 11,
                  fontWeight: 700,
                  color: "var(--foreground-muted)",
                  textTransform: "uppercase",
                  letterSpacing: ".07em",
                  marginBottom: 8,
                }}
              >
                Histórico Quitado ({quitadas.length})
              </p>
              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                {quitadas.map((d) => (
                  <div
                    key={d.id}
                    style={{
                      background: "rgba(16,185,129,.04)",
                      borderRadius: 9,
                      padding: "10px 14px",
                      border: "1px solid rgba(16,185,129,.15)",
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                    }}
                  >
                    <div>
                      <p
                        style={{
                          fontSize: 13,
                          fontWeight: 500,
                          color: "var(--foreground)",
                          margin: 0,
                        }}
                      >
                        {d.descricao}
                      </p>
                      {d.quitadoEm && (
                        <p
                          style={{
                            fontSize: 11,
                            color: "var(--foreground-muted)",
                            margin: "2px 0 0",
                          }}
                        >
                          Quitado em{" "}
                          {new Date(d.quitadoEm).toLocaleDateString("pt-BR")}
                        </p>
                      )}
                    </div>
                    <span
                      style={{
                        fontSize: 14,
                        fontWeight: 700,
                        color: "#10b981",
                      }}
                    >
                      {fmt(d.valor)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {!loading && dividas.length === 0 && (
            <div
              style={{
                textAlign: "center",
                padding: "24px 0",
                color: "var(--foreground-muted)",
              }}
            >
              <p style={{ fontSize: 14 }}>
                Nenhuma dívida registrada para este cliente.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function DetalheContato({
  item,
  onEditar,
  onClose,
}: {
  item: Contato;
  onEditar: () => void;
  onClose: () => void;
}) {
  const isForn = item.tipo === "FORNECEDOR";
  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.75)",
        backdropFilter: "blur(4px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 50,
        padding: 16,
      }}
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div
        className="animate-fade-in"
        style={{
          background: "var(--surface-elevated)",
          border: "1px solid var(--border)",
          borderRadius: 14,
          padding: 24,
          width: "100%",
          maxWidth: 380,
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            marginBottom: 18,
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <div
              style={{
                width: 44,
                height: 44,
                borderRadius: "50%",
                background: isForn
                  ? "rgba(59,130,246,0.12)"
                  : "var(--primary-muted)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              {isForn ? (
                <Truck size={18} color="#3b82f6" />
              ) : (
                <User size={18} color="var(--primary)" />
              )}
            </div>
            <div>
              <p
                style={{
                  fontSize: 15,
                  fontWeight: 700,
                  color: "var(--foreground)",
                  margin: 0,
                }}
              >
                {item.nome}
              </p>
              <span
                style={{
                  fontSize: 11,
                  padding: "2px 8px",
                  borderRadius: 99,
                  fontWeight: 600,
                  background: isForn
                    ? "rgba(59,130,246,0.1)"
                    : "var(--primary-muted)",
                  color: isForn ? "#3b82f6" : "var(--primary)",
                }}
              >
                {isForn ? "Fornecedor" : "Cliente"}
              </span>
            </div>
          </div>
          <button
            onClick={onClose}
            style={{ ...btnG, padding: 6, border: "none" }}
          >
            <X size={16} />
          </button>
        </div>

        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: 9,
            marginBottom: 18,
          }}
        >
          {item.telefone && (
            <div style={{ display: "flex", gap: 10, fontSize: 13 }}>
              <Phone size={14} color="var(--foreground-muted)" />
              <span>{item.telefone}</span>
            </div>
          )}
          {item.email && (
            <div style={{ display: "flex", gap: 10, fontSize: 13 }}>
              <Mail size={14} color="var(--foreground-muted)" />
              <span>{item.email}</span>
            </div>
          )}
          {item.cpf && (
            <div style={{ display: "flex", gap: 10, fontSize: 13 }}>
              <CreditCard size={14} color="var(--foreground-muted)" />
              <span>CPF: {fmtCpf(item.cpf.replace(/\D/g, ""))}</span>
            </div>
          )}
          {item.cnpj && (
            <div style={{ display: "flex", gap: 10, fontSize: 13 }}>
              <Building2 size={14} color="var(--foreground-muted)" />
              <span>CNPJ: {fmtCnpj(item.cnpj.replace(/\D/g, ""))}</span>
            </div>
          )}
          {item.contato && (
            <div style={{ display: "flex", gap: 10, fontSize: 13 }}>
              <User size={14} color="var(--foreground-muted)" />
              <span>Contato: {item.contato}</span>
            </div>
          )}
          {item.observacoes && (
            <div
              style={{
                padding: "8px 12px",
                background: "var(--surface-overlay)",
                borderRadius: 8,
                fontSize: 12,
                color: "var(--foreground-muted)",
                marginTop: 4,
              }}
            >
              {item.observacoes}
            </div>
          )}
        </div>
        <button
          onClick={onEditar}
          style={{ ...btnP, width: "100%", justifyContent: "center" }}
        >
          <Edit2 size={14} /> Editar
        </button>
      </div>
    </div>
  );
}

type SortKey = "nome" | "email" | "telefone";

export default function Clientes() {
  const { empresaAtiva } = useEmpresa();
  const [todos, setTodos] = useState<Contato[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [aba, setAba] = useState<Tipo>("CLIENTE");
  const [filtro, setFiltro] = useState("");
  const [sortKey, setSortKey] = useState<SortKey>("nome");
  const [sortAsc, setSortAsc] = useState(true);
  const [modal, setModal] = useState<{
    tipo: "novo" | "editar" | "detalhe";
    item?: Contato;
  } | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [modalDividas, setModalDividas] = useState<Contato | null>(null);

  const carregar = async () => {
    if (!empresaAtiva) return;
    setLoading(true);
    try {
      setTodos(
        await fetchAuth<Contato[]>(
          `/api/v1/clientes?empresaId=${empresaAtiva.id}`,
        ),
      );
    } catch {
      toast.error("Erro ao carregar contatos");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    carregar();
  }, [empresaAtiva?.id]);

  const clientes = useMemo(
    () => todos.filter((c) => c.tipo === "CLIENTE"),
    [todos],
  );
  const fornecedores = useMemo(
    () => todos.filter((c) => c.tipo === "FORNECEDOR"),
    [todos],
  );
  const abaLista = aba === "CLIENTE" ? clientes : fornecedores;

  const lista = useMemo(
    () =>
      abaLista
        .filter(
          (c) =>
            c.nome?.toLowerCase().includes(filtro.toLowerCase()) ||
            c.email?.toLowerCase().includes(filtro.toLowerCase()) ||
            c.telefone?.includes(filtro) ||
            c.cnpj?.includes(filtro) ||
            c.cpf?.includes(filtro),
        )
        .sort((a, b) => {
          const va = (a[sortKey] ?? "").toLowerCase();
          const vb = (b[sortKey] ?? "").toLowerCase();
          return sortAsc ? va.localeCompare(vb) : vb.localeCompare(va);
        }),
    [abaLista, filtro, sortKey, sortAsc],
  );

  const handleSort = (k: SortKey) => {
    if (sortKey === k) setSortAsc((v) => !v);
    else {
      setSortKey(k);
      setSortAsc(true);
    }
  };

  const handleSalvar = async (form: ContatoForm) => {
    if (!empresaAtiva) return;
    setFormError("");
    setSaving(true);
    try {
      const body = {
        nome: form.nome,
        email: form.email || null,
        telefone: form.telefone || null,
        cpf: form.cpf || null,
        cnpj: form.cnpj || null,
        contato: form.contato || null,
        observacoes: form.observacoes || null,
        tipo: form.tipo,
        empresaId: empresaAtiva.id,
      };

      if (modal?.tipo === "editar" && modal.item) {
        const updated = await fetchAuth<Contato>(
          `/api/v1/clientes/${modal.item.id}`,
          { method: "PUT", body: JSON.stringify(body) },
        );
        setTodos((prev) =>
          prev.map((c) => (c.id === updated.id ? updated : c)),
        );
        toast.success(
          `${form.tipo === "CLIENTE" ? "Cliente" : "Fornecedor"} atualizado!`,
        );
      } else {
        const created = await fetchAuth<Contato>("/api/v1/clientes", {
          method: "POST",
          body: JSON.stringify(body),
        });
        setTodos((prev) => [created, ...prev]);
        toast.success(
          `${form.tipo === "CLIENTE" ? "Cliente" : "Fornecedor"} cadastrado!`,
        );
        setAba(form.tipo);
      }
      setModal(null);
    } catch (e: any) {
      const mensagem = e instanceof Error ? e.message : "Não foi possível salvar o cadastro.";
      setFormError(mensagem);
      toast.error(mensagem);
    } finally {
      setSaving(false);
    }
  };

  const handleExcluir = async (id: number) => {
    if (!confirm("Remover este registro?")) return;
    setDeletingId(id);
    try {
      await fetchAuth(`/api/v1/clientes/${id}`, { method: "DELETE" });
      setTodos((prev) => prev.filter((c) => c.id !== id));
      toast.success("Removido!");
    } catch (e: any) {
      toast.error(e.message);
    } finally {
      setDeletingId(null);
    }
  };

  const Th = ({ k, label }: { k: SortKey; label: string }) => (
    <th
      onClick={() => handleSort(k)}
      style={{
        padding: "10px 14px",
        fontSize: 11,
        fontWeight: 600,
        color: "var(--foreground-muted)",
        textTransform: "uppercase",
        letterSpacing: ".06em",
        cursor: "pointer",
        textAlign: "left",
        background: "var(--surface)",
        whiteSpace: "nowrap",
        userSelect: "none",
      }}
    >
      <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
        {label}
        {sortKey === k &&
          (sortAsc ? <ChevronUp size={12} /> : <ChevronDown size={12} />)}
      </span>
    </th>
  );

  if (!empresaAtiva)
    return (
      <div
        style={{
          padding: 48,
          textAlign: "center",
          color: "var(--foreground-muted)",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          gap: 12,
        }}
      >
        <Store size={40} color="var(--foreground-subtle)" />
        <p style={{ fontSize: 14 }}>
          Selecione uma empresa para ver os contatos.
        </p>
      </div>
    );

  return (
    <div
      className="clientes-page"
      style={{ padding: 28, display: "flex", flexDirection: "column", gap: 20 }}
    >
      {/* Header */}
      <div
        style={{
          display: "flex",
          alignItems: "flex-start",
          justifyContent: "space-between",
          flexWrap: "wrap",
          gap: 12,
        }}
      >
        <div>
          <h2
            style={{
              fontSize: 18,
              fontWeight: 700,
              color: "var(--foreground)",
              margin: 0,
            }}
          >
            Clientes & Fornecedores
          </h2>
          <p
            style={{
              fontSize: 13,
              color: "var(--foreground-muted)",
              marginTop: 3,
            }}
          >
            {empresaAtiva.nomeFantasia} · {clientes.length} cliente(s) ·{" "}
            {fornecedores.length} fornecedor(es)
          </p>
        </div>
        <button style={btnP} onClick={() => setModal({ tipo: "novo" })}>
          <Plus size={15} /> Novo
        </button>
      </div>

      {/* Abas */}
      <div
        className="clientes-tabs"
        style={{
          display: "flex",
          gap: 4,
          background: "var(--surface-elevated)",
          border: "1px solid var(--border)",
          borderRadius: 10,
          padding: 4,
          width: "fit-content",
        }}
      >
        {(
          [
            ["CLIENTE", "Clientes", <Users size={14} />, clientes.length],
            [
              "FORNECEDOR",
              "Fornecedores",
              <Truck size={14} />,
              fornecedores.length,
            ],
          ] as [Tipo, string, React.ReactNode, number][]
        ).map(([val, label, icon, count]) => (
          <button
            key={val}
            onClick={() => setAba(val)}
            style={{
              display: "flex",
              alignItems: "center",
              gap: 7,
              padding: "7px 14px",
              background: aba === val ? "var(--primary)" : "transparent",
              border: "none",
              borderRadius: 7,
              cursor: "pointer",
              color: aba === val ? "#fff" : "var(--foreground-muted)",
              fontSize: 13,
              fontWeight: aba === val ? 600 : 400,
              transition: "all .15s",
            }}
          >
            {icon}
            {label}
            <span
              style={{
                fontSize: 11,
                padding: "1px 6px",
                borderRadius: 99,
                background:
                  aba === val
                    ? "rgba(255,255,255,0.2)"
                    : "var(--surface-overlay)",
                fontWeight: 600,
              }}
            >
              {count}
            </span>
          </button>
        ))}
      </div>

      {/* Filtro */}
      <div className="clientes-filtro" style={{ position: "relative", maxWidth: 360 }}>
        <Search
          size={13}
          style={{
            position: "absolute",
            left: 10,
            top: "50%",
            transform: "translateY(-50%)",
            color: "var(--foreground-subtle)",
          }}
        />
        <input
          style={{ ...inp, paddingLeft: 32 }}
          placeholder={`Buscar ${aba === "CLIENTE" ? "cliente" : "fornecedor"}...`}
          value={filtro}
          onChange={(e) => setFiltro(e.target.value)}
        />
      </div>

      {/* Tabela */}
      <div
        className="clientes-table-card desktop-data-table"
        style={{
          background: "var(--surface-elevated)",
          border: "1px solid var(--border)",
          borderRadius: 12,
          overflow: "hidden",
        }}
      >
        <div className="clientes-table-scroll" style={{ overflowX: "auto" }}>
          <table className="clientes-table" style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr>
                <Th k="nome" label="Nome" />
                <th
                  style={{
                    padding: "10px 14px",
                    fontSize: 11,
                    fontWeight: 600,
                    color: "var(--foreground-muted)",
                    textTransform: "uppercase",
                    letterSpacing: ".06em",
                    textAlign: "left",
                    background: "var(--surface)",
                  }}
                >
                  Telefone
                </th>
                <Th k="email" label="E-mail" />
                <th
                  style={{
                    padding: "10px 14px",
                    fontSize: 11,
                    fontWeight: 600,
                    color: "var(--foreground-muted)",
                    textTransform: "uppercase",
                    letterSpacing: ".06em",
                    textAlign: "left",
                    background: "var(--surface)",
                  }}
                >
                  {aba === "CLIENTE" ? "CPF" : "CNPJ"}
                </th>
                {aba === "FORNECEDOR" && (
                  <th
                    style={{
                      padding: "10px 14px",
                      fontSize: 11,
                      fontWeight: 600,
                      color: "var(--foreground-muted)",
                      textTransform: "uppercase",
                      letterSpacing: ".06em",
                      textAlign: "left",
                      background: "var(--surface)",
                    }}
                  >
                    Contato
                  </th>
                )}
                <th
                  style={{
                    padding: "10px 14px",
                    fontSize: 11,
                    fontWeight: 600,
                    color: "var(--foreground-muted)",
                    textTransform: "uppercase",
                    letterSpacing: ".06em",
                    textAlign: "left",
                    background: "var(--surface)",
                  }}
                >
                  Ações
                </th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                Array.from({ length: 4 }).map((_, i) => (
                  <tr key={i}>
                    {Array.from({ length: 5 }).map((_, j) => (
                      <td key={j} style={{ padding: "12px 14px" }}>
                        <div
                          className="skeleton"
                          style={{
                            height: 14,
                            width: j === 0 ? "60%" : "50%",
                            borderRadius: 6,
                          }}
                        />
                      </td>
                    ))}
                  </tr>
                ))
              ) : lista.length === 0 ? (
                <tr>
                  <td colSpan={aba === "FORNECEDOR" ? 6 : 5}>
                    <div
                      style={{
                        padding: 48,
                        textAlign: "center",
                        display: "flex",
                        flexDirection: "column",
                        alignItems: "center",
                        gap: 10,
                        color: "var(--foreground-subtle)",
                      }}
                    >
                      {aba === "CLIENTE" ? (
                        <Users size={36} />
                      ) : (
                        <Truck size={36} />
                      )}
                      <p style={{ fontSize: 14 }}>
                        {abaLista.length === 0
                          ? `Nenhum ${aba === "CLIENTE" ? "cliente" : "fornecedor"} cadastrado.`
                          : "Nenhum resultado."}
                      </p>
                    </div>
                  </td>
                </tr>
              ) : (
                lista.map((c) => (
                  <tr
                    key={c.id}
                    style={{ borderTop: "1px solid var(--border-subtle)" }}
                    onMouseEnter={(e) =>
                      ((
                        e.currentTarget as HTMLTableRowElement
                      ).style.background = "var(--surface-overlay)")
                    }
                    onMouseLeave={(e) =>
                      ((
                        e.currentTarget as HTMLTableRowElement
                      ).style.background = "transparent")
                    }
                  >
                    <td className="cliente-cell-nome" style={{ padding: "11px 14px" }}>
                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: 10,
                          cursor: "pointer",
                        }}
                        onClick={() => setModal({ tipo: "detalhe", item: c })}
                      >
                        <div
                          style={{
                            width: 32,
                            height: 32,
                            borderRadius: "50%",
                            background:
                              aba === "FORNECEDOR"
                                ? "rgba(59,130,246,0.1)"
                                : "var(--primary-muted)",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            flexShrink: 0,
                          }}
                        >
                          <span
                            style={{
                              fontSize: 13,
                              fontWeight: 700,
                              color:
                                aba === "FORNECEDOR"
                                  ? "#3b82f6"
                                  : "var(--primary)",
                            }}
                          >
                            {c.nome.charAt(0).toUpperCase()}
                          </span>
                        </div>
                        <div className="cliente-identidade">
                          <span className="cliente-nome" style={{ fontSize: 14, fontWeight: 500, color: "var(--foreground)" }}>{c.nome}</span>
                          <span className="cliente-mobile-meta">
                            {[c.telefone, c.email].filter(Boolean).join(" · ") || "Sem contato"}
                          </span>
                        </div>
                      </div>
                    </td>

                    <td
                      style={{
                        padding: "11px 14px",
                        fontSize: 13,
                        color: "var(--foreground-muted)",
                      }}
                    >
                      {c.telefone ? (
                        <a
                          href={`https://wa.me/55${c.telefone.replace(/\D/g, "")}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          style={{
                            color: "var(--foreground-muted)",
                            textDecoration: "none",
                            display: "flex",
                            alignItems: "center",
                            gap: 5,
                          }}
                          onMouseEnter={(e) =>
                            ((e.currentTarget as any).style.color = "#25D366")
                          }
                          onMouseLeave={(e) =>
                            ((e.currentTarget as any).style.color =
                              "var(--foreground-muted)")
                          }
                        >
                          <Phone size={12} />
                          {c.telefone}
                        </a>
                      ) : (
                        <span style={{ color: "var(--foreground-subtle)" }}>
                          —
                        </span>
                      )}
                    </td>

                    <td
                      style={{
                        padding: "11px 14px",
                        fontSize: 13,
                        color: "var(--foreground-muted)",
                      }}
                    >
                      {c.email ? (
                        <a
                          href={`mailto:${c.email}`}
                          style={{
                            color: "var(--foreground-muted)",
                            textDecoration: "none",
                          }}
                          onMouseEnter={(e) =>
                            ((e.currentTarget as any).style.color =
                              "var(--primary)")
                          }
                          onMouseLeave={(e) =>
                            ((e.currentTarget as any).style.color =
                              "var(--foreground-muted)")
                          }
                        >
                          {c.email}
                        </a>
                      ) : (
                        <span style={{ color: "var(--foreground-subtle)" }}>
                          —
                        </span>
                      )}
                    </td>

                    <td
                      style={{
                        padding: "11px 14px",
                        fontSize: 13,
                        color: "var(--foreground-muted)",
                      }}
                    >
                      {aba === "CLIENTE" ? (
                        c.cpf ? (
                          fmtCpf(c.cpf.replace(/\D/g, ""))
                        ) : (
                          <span style={{ color: "var(--foreground-subtle)" }}>
                            —
                          </span>
                        )
                      ) : c.cnpj ? (
                        fmtCnpj(c.cnpj.replace(/\D/g, ""))
                      ) : (
                        <span style={{ color: "var(--foreground-subtle)" }}>
                          —
                        </span>
                      )}
                    </td>

                    {aba === "FORNECEDOR" && (
                      <td
                        style={{
                          padding: "11px 14px",
                          fontSize: 13,
                          color: "var(--foreground-muted)",
                        }}
                      >
                        {c.contato ?? (
                          <span style={{ color: "var(--foreground-subtle)" }}>
                            —
                          </span>
                        )}
                      </td>
                    )}

                    <td style={{ padding: "11px 14px" }}>
                      <div style={{ display: "flex", gap: 6 }}>
                        {/* Botão de dívidas — apenas para clientes */}
                        {aba === "CLIENTE" && (
                          <button
                            onClick={() => setModalDividas(c)}
                            style={{
                              ...btnG,
                              padding: "6px 8px",
                              borderColor: c.deveAlgo
                                ? "rgba(239,68,68,0.3)"
                                : "var(--border)",
                              color: c.deveAlgo
                                ? "var(--destructive)"
                                : "var(--foreground-muted)",
                            }}
                            title="Ver dívidas"
                          >
                            <DollarSign size={14} />
                          </button>
                        )}
                        <button
                          onClick={() => setModal({ tipo: "editar", item: c })}
                          style={{ ...btnG, padding: "6px 8px" }}
                        >
                          <Edit2 size={14} />
                        </button>
                        <button
                          onClick={() => handleExcluir(c.id)}
                          disabled={deletingId === c.id}
                          style={{
                            ...btnG,
                            padding: "6px 8px",
                            borderColor: "rgba(239,68,68,0.3)",
                            color: "var(--destructive)",
                            opacity: deletingId === c.id ? 0.5 : 1,
                          }}
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        {!loading && lista.length > 0 && (
          <div
            style={{
              padding: "9px 14px",
              borderTop: "1px solid var(--border)",
              fontSize: 12,
              color: "var(--foreground-muted)",
            }}
          >
            {lista.length} de {abaLista.length} registro(s)
          </div>
        )}
      </div>

      <div className="mobile-only-list clientes-mobile-list">
        {lista.length === 0 ? (
          <div className="mobile-list-empty">Nenhum {aba === "CLIENTE" ? "cliente" : "fornecedor"} encontrado.</div>
        ) : lista.map((c) => (
          <article className="cliente-mobile-card" key={c.id}>
            <button className="cliente-mobile-info" onClick={() => setModal({ tipo: "detalhe", item: c })}>
              <span className="cliente-mobile-avatar">{c.nome.charAt(0).toUpperCase()}</span>
              <span className="cliente-mobile-texto">
                <strong>{c.nome}</strong>
                <small>{c.telefone || c.email || "Sem contato informado"}</small>
              </span>
            </button>
            <div className="cliente-mobile-acoes">
              {aba === "CLIENTE" && <button className={c.deveAlgo ? "danger" : ""} aria-label="Ver dívidas" title="Ver dívidas" onClick={() => setModalDividas(c)}><DollarSign size={13} /></button>}
              <button aria-label="Editar" title="Editar" onClick={() => setModal({ tipo: "editar", item: c })}><Edit2 size={13} /></button>
              <button className="danger" aria-label="Excluir" title="Excluir" disabled={deletingId === c.id} onClick={() => handleExcluir(c.id)}><Trash2 size={13} /></button>
            </div>
          </article>
        ))}
        {lista.length > 0 && <div className="mobile-list-count">{lista.length} de {abaLista.length}</div>}
      </div>

      {/* Modais */}
      {(modal?.tipo === "novo" || modal?.tipo === "editar") && (
        <ModalContato
          item={modal.item}
          tipoInicial={aba}
          onSave={handleSalvar}
          onClose={() => { setFormError(""); setModal(null); }}
          saving={saving}
          error={formError}
        />
      )}
      {modal?.tipo === "detalhe" && modal.item && (
        <DetalheContato
          item={modal.item}
          onEditar={() => setModal({ tipo: "editar", item: modal.item })}
          onClose={() => setModal(null)}
        />
      )}
      {modalDividas && empresaAtiva && (
        <ModalDividas
          cliente={modalDividas}
          empresaId={empresaAtiva.id}
          onClose={() => setModalDividas(null)}
        />
      )}
    </div>
  );
}
