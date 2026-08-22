"use client";

import { useEffect, useState, useMemo } from "react";
import { useEmpresa } from "../context/Empresacontext";
import {
  PlusCircle, Search, Edit2, Trash2, X, Check, Package, 
  ChevronUp, ChevronDown, AlertTriangle, Boxes, Tag, 
  Barcode, Ruler, DollarSign, ShoppingCart, Minus, Plus, Zap, Archive, RotateCcw,
  AlertCircle
} from "lucide-react";
import { toast } from "sonner";
import { fetchAuth, fetchAuthJson } from "@/lib/api-v2";
import { montarProdutoPayload, numeroDecimal, type ProdutoFormData } from "@/lib/produtos";

interface Produto {
  id: number;
  nome: string;
  categoria?: string;
  descricao?: string;
  unidade?: string;
  codigoBarras?: string;
  preco: number;
  precoCusto?: number;
  lucroUnitario?: number;
  margemLucro?: number;
  quantidadeEstoque: number;
  estoqueMinimo?: number;
  ativo: boolean;
}

type ProdutoForm = ProdutoFormData;

const LIMITE_PRODUTOS: Record<string, number> = {
  EXPERIMENTAL: 300,
  BASICO: 500,
  PRO: 999999,
  PREMIUM: 999999,
};

const FORM_VAZIO: ProdutoForm = {
  nome: "", categoria: "", descricao: "", unidade: "UN",
  codigoBarras: "", preco: "", precoCusto: "",
  quantidadeEstoque: "", estoqueMinimo: "", ativo: true,
};

const UNIDADES = ["UN", "KG", "G", "L", "ML", "CX", "PCT", "PAR", "M", "CM"];

const fmt = (v?: number | null) =>
  v != null ? new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(v) : "—";

const inp: React.CSSProperties = {
  width: "100%", padding: "9px 12px", background: "var(--surface-overlay)",
  border: "1px solid var(--border)", borderRadius: 8, color: "var(--foreground)",
  fontSize: 13, outline: "none",
};
const btnPrimary: React.CSSProperties = {
  display: "flex", alignItems: "center", gap: 6, padding: "9px 16px",
  background: "var(--primary)", border: "none", borderRadius: 8,
  color: "#000", fontSize: 13, fontWeight: 700, cursor: "pointer",
};
const btnGhost: React.CSSProperties = {
  display: "flex", alignItems: "center", gap: 6, padding: "8px 12px",
  background: "transparent", border: "1px solid var(--border)", borderRadius: 8,
  color: "var(--foreground-muted)", fontSize: 13, cursor: "pointer",
};
const btnDanger: React.CSSProperties = {
  display: "flex", alignItems: "center", gap: 6, padding: "9px 16px",
  background: "var(--destructive)", border: "none", borderRadius: 8,
  color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer",
};

function BarraUsoProdutos({ total, plano, onUpgrade }: { total: number; plano: string; onUpgrade: () => void; }) {
  const limite = LIMITE_PRODUTOS[plano] ?? 999999;
  const ilimitado = limite >= 999999;
  const restantes = ilimitado ? null : Math.max(limite - total, 0);
  const pct = ilimitado ? 0 : Math.min((total / limite) * 100, 100);
  const critico = pct >= 90;
  const aviso = pct >= 70;
  const cor = critico ? "#ef4444" : aviso ? "#f59e0b" : "var(--primary)";

  return (
    <div style={{ padding: "12px 16px", background: critico ? "rgba(239,68,68,0.07)" : "var(--surface-elevated)", border: `1px solid ${critico ? "rgba(239,68,68,0.25)" : aviso ? "rgba(245,158,11,0.2)" : "var(--border)"}`, borderRadius: 10, display: "flex", alignItems: "center", gap: 14 }}>
      <div style={{ flex: 1 }}>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
          <span style={{ fontSize: 12, color: "var(--foreground-muted)", fontWeight: 500 }}>Capacidade de Produtos Ativos</span>
          <span style={{ fontSize: 12, fontWeight: 700, color: ilimitado ? "var(--foreground)" : cor, display: "inline-flex", alignItems: "center", gap: 10 }}>
            {ilimitado ? <>{total} <span style={{ opacity: 0.5 }}>·</span> Ilimitado</> : <>{total} / {limite}<span style={{ color: "var(--foreground-muted)", fontWeight: 600 }}>· Restam {restantes}</span></>}
          </span>
        </div>
        {!ilimitado && (
          <div style={{ height: 5, background: "var(--border)", borderRadius: 99 }}>
            <div style={{ height: 5, width: `${pct}%`, background: cor, borderRadius: 99, transition: "width .3s" }} />
          </div>
        )}
        {critico && <p style={{ fontSize: 11, color: "#ef4444", margin: "5px 0 0" }}>Você está perto do limite. Faça upgrade para cadastrar produtos ilimitados.</p>}
      </div>
      {(critico || aviso) && (
        <button onClick={onUpgrade} style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 12px", background: cor, border: "none", borderRadius: 7, color: "#000", fontSize: 12, fontWeight: 700, cursor: "pointer", whiteSpace: "nowrap", flexShrink: 0 }}>
          <Zap size={12} /> Fazer upgrade
        </button>
      )}
    </div>
  );
}

function ModalConfirmarExclusao({ produto, onConfirmar, onClose, saving }: { produto: Produto; onConfirmar: () => Promise<void>; onClose: () => void; saving: boolean; }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.75)", backdropFilter: "blur(4px)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50, padding: 16 }} onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="animate-fade-in" style={{ background: "var(--surface-elevated)", border: "1px solid rgba(239,68,68,0.3)", borderRadius: 14, padding: 28, width: "100%", maxWidth: 400, textAlign: "center" }}>
        
        <div style={{ width: 56, height: 56, borderRadius: "50%", background: "rgba(239,68,68,0.1)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px" }}>
          <Archive size={28} color="var(--destructive)" />
        </div>
        
        <h2 style={{ fontSize: 18, fontWeight: 700, color: "var(--foreground)", margin: "0 0 8px" }}>Excluir / Arquivar Produto?</h2>
        <p style={{ fontSize: 14, color: "var(--foreground-muted)", marginBottom: 24, lineHeight: 1.5 }}>
          Você está removendo <strong style={{ color: "var(--foreground)" }}>{produto.nome}</strong>. <br/><br/>
          <span style={{ fontSize: 12 }}>
            💡 <strong>Nota:</strong> Se este produto já possuir histórico de vendas, ele será apenas <strong>arquivado</strong> para proteger seus relatórios financeiros.
          </span>
        </p>
        
        <div style={{ display: "flex", gap: 10 }}>
          <button onClick={onClose} disabled={saving} style={{ ...btnGhost, flex: 1, justifyContent: "center" }}>Cancelar</button>
          <button onClick={onConfirmar} disabled={saving} style={{ ...btnDanger, flex: 1, justifyContent: "center", opacity: saving ? 0.6 : 1 }}>
            {saving ? "Processando..." : "Confirmar"}
          </button>
        </div>

      </div>
    </div>
  );
}

function ModalProduto({ produto, categorias, onSave, onClose, saving }: {
  produto?: Produto; categorias: string[]; onSave: (f: ProdutoForm) => Promise<void>; onClose: () => void; saving: boolean;
}) {
  const [form, setForm] = useState<ProdutoForm>(
    produto
      ? { nome: produto.nome, categoria: produto.categoria ?? "", descricao: produto.descricao ?? "", unidade: produto.unidade ?? "UN", codigoBarras: produto.codigoBarras ?? "", preco: String(produto.preco), precoCusto: produto.precoCusto != null ? String(produto.precoCusto) : "", quantidadeEstoque: String(produto.quantidadeEstoque), estoqueMinimo: String(produto.estoqueMinimo ?? 0), ativo: produto.ativo }
      : FORM_VAZIO,
  );

  const set = (k: keyof ProdutoForm, v: string | boolean) => setForm((f) => ({ ...f, [k]: v }));
  const precoNum = numeroDecimal(form.preco) || 0;
  const custoNum = numeroDecimal(form.precoCusto) || 0;
  const lucro = precoNum > 0 && custoNum > 0 ? precoNum - custoNum : null;
  const margem = lucro != null && precoNum > 0 ? (lucro / precoNum) * 100 : null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await onSave(form);
  };

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.75)", backdropFilter: "blur(4px)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50, padding: 16 }} onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="animate-fade-in produto-form-modal" style={{ background: "var(--surface-elevated)", border: "1px solid var(--border)", borderRadius: 14, padding: 28, width: "100%", maxWidth: 560, maxHeight: "92vh", overflowY: "auto" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
          <h2 style={{ fontSize: 16, fontWeight: 700, color: "var(--foreground)", margin: 0 }}>{produto ? "Editar Produto" : "Novo Produto"}</h2>
          <button onClick={onClose} style={{ ...btnGhost, padding: 6, border: "none" }}><X size={16} /></button>
        </div>
        <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 18 }}>
          <div>
            <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}>Nome *</label>
            <input style={inp} value={form.nome} onChange={(e) => set("nome", e.target.value)} placeholder="Ex: Coca-Cola 350ml" required />
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div>
              <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}><Tag size={11} style={{ marginRight: 4 }} />Categoria</label>
              <input style={inp} list="cats" value={form.categoria} onChange={(e) => set("categoria", e.target.value)} placeholder="Ex: Bebidas" />
              <datalist id="cats">{categorias.map((c) => <option key={c} value={c} />)}</datalist>
            </div>
            <div>
              <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}><Ruler size={11} style={{ marginRight: 4 }} />Unidade</label>
              <select style={{ ...inp, cursor: "pointer" }} value={form.unidade} onChange={(e) => set("unidade", e.target.value)}>
                {UNIDADES.map((u) => <option key={u} value={u}>{u}</option>)}
              </select>
            </div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1.2fr 1fr", gap: 12 }}>
            <div>
              <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}><Barcode size={11} style={{ marginRight: 4 }} />Código de Barras</label>
              <input style={inp} value={form.codigoBarras} onChange={(e) => set("codigoBarras", e.target.value)} placeholder="EAN-13..." />
            </div>
            <div>
              <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}>Ativo</label>
               <select style={{ ...inp, cursor: "pointer" }} value={form.ativo ? "true" : "false"} onChange={(e) => set("ativo", e.target.value === "true")}>
                <option value="true">Sim</option>
                <option value="false">Não (Arquivado)</option>
              </select>
            </div>
          </div>
          <div>
            <p style={{ fontSize: 12, color: "var(--foreground-muted)", fontWeight: 600, textTransform: "uppercase", letterSpacing: ".06em", marginBottom: 10 }}>Precificação</p>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <div>
                <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}><ShoppingCart size={11} style={{ marginRight: 4 }} />Preço de Custo (R$)</label>
                <input style={inp} type="number" step="0.01" min="0" value={form.precoCusto} onChange={(e) => set("precoCusto", e.target.value)} placeholder="0,00" />
              </div>
              <div>
                <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}><DollarSign size={11} style={{ marginRight: 4 }} />Preço de Venda (R$) *</label>
                <input style={inp} type="number" step="0.01" min="0" value={form.preco} onChange={(e) => set("preco", e.target.value)} placeholder="0,00" required />
              </div>
            </div>
            {lucro != null && (
              <div style={{ marginTop: 10, padding: "10px 14px", background: lucro >= 0 ? "rgba(16,185,129,0.08)" : "rgba(239,68,68,0.08)", border: `1px solid ${lucro >= 0 ? "rgba(16,185,129,0.2)" : "rgba(239,68,68,0.2)"}`, borderRadius: 8, display: "flex", justifyContent: "space-between", fontSize: 13 }}>
                <span style={{ color: "var(--foreground-muted)" }}>Lucro unitário</span>
                <div style={{ display: "flex", gap: 16 }}>
                  <span style={{ fontWeight: 700, color: lucro >= 0 ? "var(--success)" : "var(--destructive)" }}>{fmt(lucro)}</span>
                  {margem != null && <span style={{ color: "var(--foreground-muted)" }}>({margem.toFixed(1)}%)</span>}
                </div>
              </div>
            )}
          </div>
          <div>
            <p style={{ fontSize: 12, color: "var(--foreground-muted)", fontWeight: 600, textTransform: "uppercase", letterSpacing: ".06em", marginBottom: 10 }}>Estoque</p>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <div>
                <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}>Quantidade Atual</label>
                <input style={inp} type="number" min="0" value={form.quantidadeEstoque} onChange={(e) => set("quantidadeEstoque", e.target.value)} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}>Estoque Mínimo (alerta)</label>
                <input style={inp} type="number" min="0" value={form.estoqueMinimo} onChange={(e) => set("estoqueMinimo", e.target.value)} />
              </div>
            </div>
          </div>
          <div>
            <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}>Descrição</label>
            <textarea style={{ ...inp, resize: "vertical", minHeight: 68 }} value={form.descricao} onChange={(e) => set("descricao", e.target.value)} placeholder="Observações opcionais..." />
          </div>
          <div style={{ display: "flex", gap: 10, marginTop: 4 }}>
            <button type="button" onClick={onClose} style={{ ...btnGhost, flex: 1, justifyContent: "center" }}>Cancelar</button>
            <button type="submit" disabled={saving} style={{ ...btnPrimary, flex: 2, justifyContent: "center", opacity: saving ? 0.7 : 1 }}>
              {saving ? "Salvando..." : <><Check size={14} />{produto ? "Salvar alterações" : "Cadastrar produto"}</>}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function ModalEstoque({ produto, onSave, onClose, saving }: {
  produto: Produto; onSave: (n: number) => Promise<void>; onClose: () => void; saving: boolean;
}) {
  const [qtd, setQtd] = useState(produto.quantidadeEstoque);
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.75)", backdropFilter: "blur(4px)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50, padding: 16 }} onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="animate-fade-in" style={{ background: "var(--surface-elevated)", border: "1px solid var(--border)", borderRadius: 14, padding: 28, width: "100%", maxWidth: 360 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
          <h2 style={{ fontSize: 15, fontWeight: 700, color: "var(--foreground)", margin: 0 }}>Ajustar Estoque</h2>
          <button onClick={onClose} style={{ ...btnGhost, padding: 6, border: "none" }}><X size={15} /></button>
        </div>
        <p style={{ fontSize: 13, color: "var(--foreground-muted)", marginBottom: 20 }}>{produto.nome}</p>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 16, marginBottom: 24 }}>
          <button onClick={() => setQtd((q) => Math.max(0, q - 1))} style={{ width: 40, height: 40, borderRadius: 10, background: "var(--surface-overlay)", border: "1px solid var(--border)", display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer", color: "var(--foreground)" }}><Minus size={16} /></button>
          <input type="number" min="0" value={qtd} onChange={(e) => setQtd(Math.max(0, parseInt(e.target.value) || 0))} style={{ ...inp, width: 100, textAlign: "center", fontSize: 22, fontWeight: 700 }} />
          <button onClick={() => setQtd((q) => q + 1)} style={{ width: 40, height: 40, borderRadius: 10, background: "var(--primary-muted)", border: "1px solid rgba(16,185,129,0.3)", display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer", color: "var(--primary)" }}><Plus size={16} /></button>
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "var(--foreground-muted)", marginBottom: 20 }}>
          <span>Atual: {produto.quantidadeEstoque}</span>
          <span style={{ color: qtd > produto.quantidadeEstoque ? "var(--success)" : qtd < produto.quantidadeEstoque ? "var(--destructive)" : "var(--foreground-muted)" }}>
            Diferença: {qtd - produto.quantidadeEstoque >= 0 ? "+" : ""}{qtd - produto.quantidadeEstoque}
          </span>
        </div>
        <div style={{ display: "flex", gap: 10 }}>
          <button onClick={onClose} style={{ ...btnGhost, flex: 1, justifyContent: "center" }}>Cancelar</button>
          <button onClick={() => onSave(qtd)} disabled={saving || qtd === produto.quantidadeEstoque} style={{ ...btnPrimary, flex: 2, justifyContent: "center", opacity: saving || qtd === produto.quantidadeEstoque ? 0.6 : 1 }}>
            {saving ? "Salvando..." : <><Check size={14} />Confirmar</>}
          </button>
        </div>
      </div>
    </div>
  );
}

type SortKey = "nome" | "categoria" | "preco" | "quantidadeEstoque" | "margemLucro";
type ModalState = { tipo: "produto"; produto?: Produto } | { tipo: "estoque"; produto: Produto } | { tipo: "excluir"; produto: Produto } | null;
type AbaTipo = "ativos" | "alerta" | "esgotados" | "inativos";

function NomeProduto({ nome, unidade }: { nome: string; unidade?: string }) {
  const MAX = 24;
  return (
    <td style={{ padding: "12px", maxWidth: 220 }}>
      <p title={nome} style={{ fontSize: 13, fontWeight: 600, color: "var(--foreground)", margin: 0, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{nome}</p>
      {unidade && <span style={{ fontSize: 10, color: "var(--foreground-subtle)" }}>{unidade}</span>}
    </td>
  );
}

export default function Produtos({ onNavegar }: { onNavegar?: (s: string) => void }) {
  const { empresaAtiva } = useEmpresa();

  const [produtos, setProdutos]   = useState<Produto[]>([]);
  const [planoAtual, setPlanoAtual] = useState<string>("EXPERIMENTAL");
  const [loading, setLoading]     = useState(true);
  const [saving, setSaving]       = useState(false);
  const [filtro, setFiltro]       = useState("");
  const [catFiltro, setCatFiltro] = useState("");
  const [abaAtiva, setAbaAtiva]   = useState<AbaTipo>("ativos");
  const [sortKey, setSortKey]     = useState<SortKey>("nome");
  const [sortAsc, setSortAsc]     = useState(true);
  const [modal, setModal]         = useState<ModalState>(null);

  const categorias = useMemo(() => [...new Set(produtos.map((p) => p.categoria).filter(Boolean) as string[])], [produtos]);

  const carregar = async () => {
    if (!empresaAtiva) return;
    setLoading(true);
    try {
      const [prods, perfil] = await Promise.allSettled([
        fetchAuthJson<Produto[]>(`/api/v1/produtos?empresaId=${empresaAtiva.id}`),
        fetchAuthJson<{ tipoPlano: string }>("/api/v1/configuracoes/perfil"),
      ]);
      if (prods.status === "fulfilled") setProdutos(prods.value);
      else throw prods.reason;
      if (perfil.status === "fulfilled") setPlanoAtual(perfil.value.tipoPlano);
    } catch (e) { toast.error(e instanceof Error ? e.message : "Erro ao carregar produtos"); }
    finally { setLoading(false); }
  };

  useEffect(() => { carregar(); }, [empresaAtiva?.id]);

  // CÁLCULO DAS ESTATÍSTICAS
  const stats = useMemo(() => {
    return {
      ativos: produtos.filter(p => p.ativo).length,
      alerta: produtos.filter(p => p.ativo && p.quantidadeEstoque > 0 && p.quantidadeEstoque <= (p.estoqueMinimo || 0)).length,
      esgotados: produtos.filter(p => p.ativo && p.quantidadeEstoque === 0).length,
      inativos: produtos.filter(p => !p.ativo).length
    };
  }, [produtos]);

  const limite = LIMITE_PRODUTOS[planoAtual] ?? 999999;
  const atingiuLimite = stats.ativos >= limite;

  const lista = useMemo(() => {
    return produtos
      .filter((p) => {
        if (abaAtiva === "inativos") return !p.ativo;
        if (!p.ativo) return false;
        if (abaAtiva === "alerta") return p.quantidadeEstoque > 0 && p.quantidadeEstoque <= (p.estoqueMinimo || 0);
        if (abaAtiva === "esgotados") return p.quantidadeEstoque === 0;
        return true; // Aba ativos
      })
      .filter((p) => p.nome.toLowerCase().includes(filtro.toLowerCase()) && (!catFiltro || p.categoria === catFiltro))
      .sort((a, b) => {
        const va = (a as any)[sortKey] ?? "";
        const vb = (b as any)[sortKey] ?? "";
        if (typeof va === "string") return sortAsc ? va.localeCompare(String(vb)) : String(vb).localeCompare(va);
        return sortAsc ? Number(va) - Number(vb) : Number(vb) - Number(va);
      });
  }, [produtos, filtro, catFiltro, abaAtiva, sortKey, sortAsc]);

  const handleSalvar = async (form: ProdutoForm) => {
    if (!empresaAtiva) return;
    setSaving(true);
    try {
      const body = montarProdutoPayload(form, empresaAtiva.id);
      const editing = modal?.tipo === "produto" && modal.produto;
      if (editing) {
        const updated = await fetchAuthJson<Produto>(`/api/v1/produtos/${modal.produto!.id}`, { method: "PUT", body: JSON.stringify(body) });
        setProdutos((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
        toast.success("Produto atualizado!");
      } else {
        const created = await fetchAuthJson<Produto>("/api/v1/produtos", { method: "POST", body: JSON.stringify(body) });
        setProdutos((prev) => [created, ...prev]);
        toast.success("Produto cadastrado!");
      }
      setModal(null);
    } catch (e: any) { toast.error(e.message); }
    finally { setSaving(false); }
  };

const handleEstoque = async (novoEstoque: number) => {
    if (modal?.tipo !== "estoque") return;
    setSaving(true);
    try {
      const p = modal.produto;
      
      // Montamos o body limpo, sem o "id", exatamente como o backend espera
      const body = {
        empresaId: empresaAtiva?.id,
        nome: p.nome,
        categoria: p.categoria || null,
        descricao: p.descricao || null,
        unidade: p.unidade || null,
        codigoBarras: p.codigoBarras || null,
        preco: p.preco,
        precoCusto: p.precoCusto || null,
        quantidadeEstoque: novoEstoque, 
        estoqueMinimo: p.estoqueMinimo || 0,
        ativo: p.ativo
      };

      const updated = await fetchAuthJson<Produto>(`/api/v1/produtos/${p.id}`, {
        method: "PUT",
        body: JSON.stringify(body),
      });
      
      setProdutos((prev) => prev.map((x) => (x.id === updated.id ? updated : x)));
      toast.success("Estoque atualizado!");
      setModal(null);
    } catch (e: any) { 
      toast.error(e.message); 
    } finally { 
      setSaving(false); 
    }
  };

  const handleExcluir = async () => {
    if (modal?.tipo !== "excluir") return;
    const id = modal.produto.id;
    setSaving(true);
    try {
      const response = await fetchAuth(`/api/v1/produtos/${id}`, { method: "DELETE" });
      if (!response.ok) {
        const erro = await response.json().catch(() => null);
        throw new Error(erro?.mensagem ?? erro?.erro ?? `Erro ${response.status}`);
      }
      await carregar();
      toast.success("Produto removido do estoque com sucesso!");
      setModal(null);
    } catch (e: any) { toast.error(e.message); setModal(null); }
    finally { setSaving(false); }
  };

const handleRestaurar = async (p: Produto) => {
    try {
      // Montamos o body limpo, sem o "id" e campos calculados, exatamente como o DTO espera
      const body = {
        empresaId: empresaAtiva?.id,
        nome: p.nome,
        categoria: p.categoria || null,
        descricao: p.descricao || null,
        unidade: p.unidade || null,
        codigoBarras: p.codigoBarras || null,
        preco: p.preco,
        precoCusto: p.precoCusto || null,
        quantidadeEstoque: p.quantidadeEstoque,
        estoqueMinimo: p.estoqueMinimo || 0,
        ativo: true // ⬅️ Aqui está a mágica da restauração
      };

      const updated = await fetchAuthJson<Produto>(`/api/v1/produtos/${p.id}`, {
        method: "PUT", 
        body: JSON.stringify(body) 
      });
      
      setProdutos((prev) => prev.map((x) => (x.id === p.id ? updated : x)));
      toast.success("Produto restaurado com sucesso!");
    } catch (e: any) { 
      toast.error(e.message); 
    }
  };

  const handleSort = (key: SortKey) => {
    if (sortKey === key) setSortAsc((asc) => !asc);
    else {
      setSortKey(key);
      setSortAsc(true);
    }
  };

  const Th = ({ k, label }: { k: SortKey; label: string }) => (
    <th onClick={() => handleSort(k)} style={{ padding: "10px 12px", fontSize: 11, fontWeight: 600, color: "var(--foreground-muted)", textTransform: "uppercase", letterSpacing: ".06em", cursor: "pointer", textAlign: "left", background: "var(--surface)", userSelect: "none" }}>
      <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>{label}{sortKey === k && (sortAsc ? <ChevronUp size={12} /> : <ChevronDown size={12} />)}</span>
    </th>
  );

  if (!empresaAtiva) return <div style={{ padding: 48, textAlign: "center", color: "var(--foreground-muted)", display: "flex", flexDirection: "column", alignItems: "center", gap: 12 }}><Package size={40} color="var(--foreground-subtle)" /><p>Selecione uma empresa para ver os produtos.</p></div>;

  return (
    <div style={{ padding: 28, display: "flex", flexDirection: "column", gap: 20 }}>
      
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: 12 }}>
        <div>
          <h2 style={{ fontSize: 18, fontWeight: 700 }}>Produtos</h2>
          <p style={{ fontSize: 13, color: "var(--foreground-muted)", marginTop: 3 }}>{empresaAtiva.nomeFantasia} · {produtos.length} cadastrados</p>
        </div>
        <button style={btnPrimary} disabled={atingiuLimite} onClick={() => {
            if (atingiuLimite) { toast.error(`Limite atingido. Faça upgrade!`); onNavegar?.("planos"); return; }
            setModal({ tipo: "produto" });
        }}>
          <PlusCircle size={15} /> Novo Produto
        </button>
      </div>

      <BarraUsoProdutos total={stats.ativos} plano={planoAtual} onUpgrade={() => onNavegar?.("planos")} />

      {/* ABAS E FILTROS */}
      <div style={{ display: "flex", flexWrap: "wrap", gap: 12, alignItems: "center", justifyContent: "space-between", borderBottom: "1px solid var(--border)", paddingBottom: 12 }}>
        <div style={{ display: "flex", gap: 16, overflowX: "auto" }}>
          {[
            { id: "ativos", label: "Ativos", count: stats.ativos, color: "var(--primary)" },
            { id: "alerta", label: "Em Alerta", count: stats.alerta, color: "#f59e0b", icon: <AlertTriangle size={12} /> },
            { id: "esgotados", label: "Sem Estoque", count: stats.esgotados, color: "#ef4444", icon: <AlertCircle size={12} /> },
            { id: "inativos", label: "Inativos", count: stats.inativos, color: "var(--foreground-muted)", icon: <Archive size={12} /> }
          ].map(tab => (
            <button key={tab.id} onClick={() => setAbaAtiva(tab.id as AbaTipo)} style={{ 
              background: "none", border: "none", cursor: "pointer", fontSize: 13, fontWeight: abaAtiva === tab.id ? 700 : 500, 
              color: abaAtiva === tab.id ? tab.color : "var(--foreground-muted)", position: "relative", paddingBottom: 6,
              display: "flex", alignItems: "center", gap: 6, whiteSpace: "nowrap"
            }}>
              {tab.icon} {tab.label} ({tab.count})
              {abaAtiva === tab.id && <div style={{ position: "absolute", bottom: -13, left: 0, right: 0, height: 2, background: tab.color, borderRadius: 2 }} />}
            </button>
          ))}
        </div>

        <div style={{ display: "flex", gap: 8 }}>
          <div style={{ position: "relative", minWidth: 200 }}>
            <Search size={13} style={{ position: "absolute", left: 10, top: "50%", transform: "translateY(-50%)", color: "var(--foreground-subtle)" }} />
            <input style={{ ...inp, paddingLeft: 32 }} placeholder="Filtrar produtos..." value={filtro} onChange={(e) => setFiltro(e.target.value)} />
          </div>
          <select style={{ ...inp, width: "auto" }} value={catFiltro} onChange={(e) => setCatFiltro(e.target.value)}>
            <option value="">Categorias</option>
            {categorias.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
        </div>
      </div>

      {/* Tabela */}
      <div style={{ background: "var(--surface-elevated)", border: "1px solid var(--border)", borderRadius: 12, overflow: "hidden" }}>
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr>
                <Th k="nome" label="Produto" />
                <Th k="categoria" label="Categoria" />
                <Th k="preco" label="Venda" />
                <th style={{ padding: "10px 12px", fontSize: 11, fontWeight: 600, color: "var(--foreground-muted)", textTransform: "uppercase", background: "var(--surface)" }}>Custo</th>
                <Th k="margemLucro" label="Lucro %" />
                <Th k="quantidadeEstoque" label="Qtd" />
                <th style={{ padding: "10px 12px", fontSize: 11, fontWeight: 600, color: "var(--foreground-muted)", textTransform: "uppercase", background: "var(--surface)", textAlign: "right" }}>Ações</th>
              </tr>
            </thead>
            <tbody style={{ opacity: loading ? 0.5 : 1 }}>
              {lista.length === 0 ? (
                <tr><td colSpan={7} style={{ padding: 60, textAlign: "center", color: "var(--foreground-subtle)" }}><Package size={32} style={{ margin: "0 auto 8px", opacity: 0.3 }} /><p>Nenhum produto nesta categoria.</p></td></tr>
              ) : lista.map((p) => {
                const alerta = p.ativo && p.quantidadeEstoque > 0 && p.quantidadeEstoque <= (p.estoqueMinimo || 0);
                const esgotado = p.ativo && p.quantidadeEstoque === 0;
                return (
                  <tr key={p.id} style={{ borderTop: "1px solid var(--border-subtle)" }}>
                    <NomeProduto nome={p.nome} unidade={p.unidade} />
                    <td style={{ padding: "12px" }}>{p.categoria && <span style={{ fontSize: 11, padding: "2px 8px", background: "var(--surface-overlay)", border: "1px solid var(--border)", borderRadius: 6 }}>{p.categoria}</span>}</td>
                    <td style={{ padding: "12px", fontSize: 13, fontWeight: 700 }}>{fmt(p.preco)}</td>
                    <td style={{ padding: "12px", fontSize: 12, color: "var(--foreground-muted)" }}>{p.precoCusto != null ? fmt(p.precoCusto) : <span style={{ color: "var(--foreground-subtle)" }}>—</span>}</td>
                    <td style={{ padding: "12px" }}>
                      <span style={{ fontSize: 12, fontWeight: 600, color: (p.margemLucro || 0) >= 20 ? "var(--success)" : "var(--warning)" }}>{p.margemLucro?.toFixed(1)}%</span>
                    </td>
                    <td style={{ padding: "12px" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                        <span style={{ fontSize: 13, fontWeight: 700, color: esgotado ? "var(--destructive)" : alerta ? "#f59e0b" : "var(--foreground)" }}>{p.quantidadeEstoque}</span>
                        {esgotado && <AlertCircle size={14} color="var(--destructive)" />}
                        {alerta && <AlertTriangle size={14} color="#f59e0b" />}
                      </div>
                    </td>
                    <td style={{ padding: "12px" }}>
                      <div style={{ display: "flex", gap: 6, justifyContent: "flex-end" }}>
                        {abaAtiva !== "inativos" ? (
                          <>
                            <button onClick={() => setModal({ tipo: "estoque", produto: p })} style={{ ...btnGhost, padding: 6 }}><Boxes size={14} /></button>
                            <button onClick={() => setModal({ tipo: "produto", produto: p })} style={{ ...btnGhost, padding: 6 }}><Edit2 size={14} /></button>
                            <button onClick={() => setModal({ tipo: "excluir", produto: p })} style={{ ...btnGhost, padding: 6, color: "var(--destructive)", borderColor: "rgba(239,68,68,0.2)" }}><Trash2 size={14} /></button>
                          </>
                        ) : (
                          <button onClick={() => handleRestaurar(p)} style={{ ...btnGhost, padding: "6px 12px", color: "var(--success)", borderColor: "rgba(16,185,129,0.3)" }}><RotateCcw size={14} /> Restaurar</button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {modal?.tipo === "produto" && <ModalProduto produto={modal.produto} categorias={categorias} onSave={handleSalvar} onClose={() => setModal(null)} saving={saving} />}
      {modal?.tipo === "estoque" && <ModalEstoque produto={modal.produto} onSave={handleEstoque} onClose={() => setModal(null)} saving={saving} />}
      {modal?.tipo === "excluir" && <ModalConfirmarExclusao produto={modal.produto} onConfirmar={handleExcluir} onClose={() => setModal(null)} saving={saving} />}
    </div>
  );
}
