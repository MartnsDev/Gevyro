"use client";

import { useState, useEffect, ReactNode } from "react";
import { 
  Check, X, Tag, Ruler, Barcode, 
  ShoppingCart, DollarSign 
} from "lucide-react";
import { toast } from "sonner";
import { fetchAuthJson } from "@/lib/api-v2";
import { montarProdutoPayload, numeroDecimal } from "@/lib/produtos";

function Overlay({ onClose, children }: { onClose: () => void; children: ReactNode }) {
  return (
    <div
      style={{
        position: "fixed", inset: 0, background: "rgba(0,0,0,0.8)", backdropFilter: "blur(4px)",
        display: "flex", alignItems: "center", justifyContent: "center", zIndex: 200, padding: 16
      }}
      onClick={e => e.target === e.currentTarget && onClose()}
    >
      {children}
    </div>
  );
}

function ModalBox({ title, sub, onClose, children }: { title: string; sub?: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="animate-fade-in produto-form-modal" style={{
      background: "var(--surface-elevated)", border: "1px solid var(--border)",
      borderRadius: 14, padding: 28, width: "100%", maxWidth: 560, maxHeight: "92vh", overflowY: "auto",
    }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 22 }}>
        <div>
          <h2 style={{ fontSize: 16, fontWeight: 700, color: "var(--foreground)", margin: 0 }}>{title}</h2>
          {sub && <p style={{ fontSize: 12, color: "var(--foreground-muted)", margin: "3px 0 0" }}>{sub}</p>}
        </div>
        <button onClick={onClose} style={{ background: "transparent", border: "1px solid var(--border)", borderRadius: 8, cursor: "pointer", color: "var(--foreground-muted)", padding: 6 }}>
          <X size={16} />
        </button>
      </div>
      {children}
    </div>
  );
}

const UNIDADES = ["UN", "KG", "G", "L", "ML", "CX", "PCT", "PAR", "M", "CM"];

const inp: React.CSSProperties = {
  width: "100%", padding: "9px 12px", background: "var(--surface-overlay)",
  border: "1px solid var(--border)", borderRadius: 8, color: "var(--foreground)",
  fontSize: 13, outline: "none"
};
const btnP: React.CSSProperties = {
  display: "flex", alignItems: "center", gap: 6, padding: "9px 16px",
  background: "var(--primary)", border: "none", borderRadius: 8,
  color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer", justifyContent: "center"
};
const btnG: React.CSSProperties = {
  display: "flex", alignItems: "center", gap: 6, padding: "8px 12px",
  background: "transparent", border: "1px solid var(--border)", borderRadius: 8,
  color: "var(--foreground-muted)", fontSize: 13, cursor: "pointer", justifyContent: "center"
};

const fmt = (v?: number | null) => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(v ?? 0);

interface Props {
  empresaId: number;
  onClose: () => void;
  onConcluido: () => void;
}

export default function NovoProduto({ empresaId, onClose, onConcluido }: Props) {
  const [form, setForm] = useState({
    nome: "", categoria: "", descricao: "", unidade: "UN",
    codigoBarras: "", preco: "", precoCusto: "",
    quantidadeEstoque: "0", estoqueMinimo: "0", ativo: true
  });
  const [saving, setSaving] = useState(false);
  const [categorias, setCategorias] = useState<string[]>([]);

  // Busca categorias existentes na empresa para popular o datalist
  useEffect(() => {
    const carregarCategorias = async () => {
      try {
        const prods = await fetchAuthJson<Array<{ categoria?: string }>>(`/api/v1/produtos?empresaId=${empresaId}`);
        const cats = [...new Set(prods.map((p) => p.categoria).filter(Boolean))] as string[];
        setCategorias(cats);
      } catch (e) {
        console.error("Erro ao carregar categorias", e);
      }
    };
    carregarCategorias();
  }, [empresaId]);

  const set = (k: string, v: string | boolean) => setForm(f => ({ ...f, [k]: v }));
  
  // Cálculos em tempo real
  const precoNum = numeroDecimal(form.preco) || 0;
  const custoNum = numeroDecimal(form.precoCusto) || 0;
  const lucro = precoNum > 0 && custoNum > 0 ? precoNum - custoNum : null;
  const margem = lucro != null && precoNum > 0 ? (lucro / precoNum) * 100 : null;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      const payload = montarProdutoPayload(form, empresaId);
      await fetchAuthJson("/api/v1/produtos", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      
      toast.success(`"${payload.nome}" cadastrado!`);
      onConcluido(); 
      onClose();
    } catch (error: any) { 
      toast.error(error.message); 
    } finally { 
      setSaving(false); 
    }
  };

  return (
    <Overlay onClose={onClose}>
      <ModalBox title="Novo Produto" onClose={onClose}>
        <form className="produto-form-compact" onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: 18 }}>
          
          {/* NOME */}
          <div>
            <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}>Nome *</label>
            <input style={inp} value={form.nome} onChange={e => set("nome", e.target.value)} placeholder="Ex: Coca-Cola 350ml" autoFocus required />
          </div>
          
          {/* CATEGORIA E UNIDADE */}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div>
              <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}>
                <Tag size={11} style={{ marginRight: 4 }} />Categoria
              </label>
              <input style={inp} list="cats-rapido" value={form.categoria} onChange={e => set("categoria", e.target.value)} placeholder="Ex: Bebidas" />
              <datalist id="cats-rapido">
                {categorias.map(c => <option key={c} value={c} />)}
              </datalist>
            </div>
            <div>
              <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}>
                <Ruler size={11} style={{ marginRight: 4 }} />Unidade
              </label>
              <select style={{ ...inp, cursor: "pointer" }} value={form.unidade} onChange={e => set("unidade", e.target.value)}>
                {UNIDADES.map(u => <option key={u} value={u}>{u}</option>)}
              </select>
            </div>
          </div>
          
          {/* CÓDIGO DE BARRAS */}
          <div>
            <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}>
              <Barcode size={11} style={{ marginRight: 4 }} />Código de Barras
            </label>
            <input style={inp} value={form.codigoBarras} onChange={e => set("codigoBarras", e.target.value)} placeholder="EAN-13, EAN-8..." />
          </div>
          
          {/* PRECIFICAÇÃO E LUCRO */}
          <div>
            <p style={{ fontSize: 12, color: "var(--foreground-muted)", fontWeight: 600, textTransform: "uppercase", letterSpacing: ".06em", marginBottom: 10 }}>Precificação</p>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <div>
                <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}>
                  <ShoppingCart size={11} style={{ marginRight: 4 }} />Preço de Custo (R$)
                </label>
                <input style={inp} type="number" step="0.01" min="0" value={form.precoCusto} onChange={e => set("precoCusto", e.target.value)} placeholder="0,00" />
              </div>
              <div>
                <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}>
                  <DollarSign size={11} style={{ marginRight: 4 }} />Preço de Venda (R$) *
                </label>
                <input style={inp} type="number" step="0.01" min="0" value={form.preco} onChange={e => set("preco", e.target.value)} placeholder="0,00" required />
              </div>
            </div>
            
            {lucro != null && (
              <div style={{ 
                marginTop: 10, padding: "10px 14px", 
                background: lucro >= 0 ? "var(--success-muted)" : "var(--destructive-muted)", 
                border: `1px solid ${lucro >= 0 ? "rgba(16,185,129,0.2)" : "rgba(239,68,68,0.2)"}`, 
                borderRadius: 8, display: "flex", justifyContent: "space-between", fontSize: 13 
              }}>
                <span style={{ color: "var(--foreground-muted)" }}>Lucro unitário</span>
                <div style={{ display: "flex", gap: 16 }}>
                  <span style={{ fontWeight: 700, color: lucro >= 0 ? "var(--success)" : "var(--destructive)" }}>
                    {fmt(lucro)}
                  </span>
                  {margem != null && <span style={{ color: "var(--foreground-muted)" }}>({margem.toFixed(1)}%)</span>}
                </div>
              </div>
            )}
          </div>
          
          {/* ESTOQUE */}
          <div>
            <p style={{ fontSize: 12, color: "var(--foreground-muted)", fontWeight: 600, textTransform: "uppercase", letterSpacing: ".06em", marginBottom: 10 }}>Estoque</p>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <div>
                <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}>Quantidade Atual</label>
                <input style={inp} type="number" min="0" value={form.quantidadeEstoque} onChange={e => set("quantidadeEstoque", e.target.value)} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}>Estoque Mínimo (alerta)</label>
                <input style={inp} type="number" min="0" value={form.estoqueMinimo} onChange={e => set("estoqueMinimo", e.target.value)} />
              </div>
            </div>
          </div>

          {/* OBSERVAÇÃO / DESCRIÇÃO */}
          <div>
            <label style={{ fontSize: 12, color: "var(--foreground-muted)", display: "block", marginBottom: 6, fontWeight: 500 }}>Descrição</label>
            <textarea style={{ ...inp, resize: "vertical", minHeight: 68 }} value={form.descricao} onChange={e => set("descricao", e.target.value)} placeholder="Observações opcionais..." />
          </div>

          {/* PRODUTO ATIVO */}
          <label style={{ display: "flex", alignItems: "center", gap: 10, cursor: "pointer" }}>
            <input type="checkbox" checked={form.ativo} onChange={e => set("ativo", e.target.checked)} style={{ accentColor: "var(--primary)", width: 15, height: 15 }} />
            <span style={{ fontSize: 13, color: "var(--foreground-muted)" }}>Produto ativo</span>
          </label>
          
          {/* BOTÕES */}
          <div style={{ display: "flex", gap: 10, marginTop: 4 }}>
            <button type="button" onClick={onClose} style={{ flex: 1, ...btnG }}>Cancelar</button>
            <button type="submit" disabled={saving} style={{ flex: 2, ...btnP, opacity: saving ? 0.7 : 1 }}>
              {saving ? "Salvando..." : <><Check size={14} />Cadastrar produto</>}
            </button>
          </div>

        </form>
      </ModalBox>
    </Overlay>
  );
}
