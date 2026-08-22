"use client";

import { useState, useMemo, useEffect, ReactNode } from "react";
import { 
  Check, X, Search, Plus, Minus, ShoppingCart, 
  Smartphone, DollarSign, CreditCard, Receipt, CheckCircle2 
} from "lucide-react";
import { toast } from "sonner";
import { useEmpresa } from "../context/Empresacontext"; // Para pegar o nome da empresa pro cupom
import { fetchAuthJson } from "@/lib/api-v2";

interface Produto { id: number; nome: string; preco: number; quantidadeEstoque: number; categoria?: string }
interface ItemCarrinho { produto: Produto; quantidade: number }
interface Venda {
  id: number;
  formaPagamento: string;
  formaPagamento2?: string;
  valorPagamento2?: number;
  valorTotal: number;
  desconto: number;
  valorFinal: number;
  valorRecebido?: number;
  troco?: number;
  observacao?: string;
  dataVenda: string;
  itens: { idProduto: number; nomeProduto?: string; quantidade: number; precoUnitario: number; subtotal: number; }[];
  nomeCliente?: string;
  cancelada?: boolean;
}

function normalizarTroco(venda: Venda): Venda {
  const segundoPagamento = venda.formaPagamento2
    ? Math.max(0, Number(venda.valorPagamento2) || 0)
    : 0;
  const valorEmDinheiro =
    venda.formaPagamento === "DINHEIRO"
      ? Math.max(0, venda.valorFinal - segundoPagamento)
      : venda.formaPagamento2 === "DINHEIRO"
        ? segundoPagamento
        : 0;

  if (valorEmDinheiro <= 0 || !venda.valorRecebido) return venda;
  return {
    ...venda,
    troco: Math.max(0, Number(venda.valorRecebido) - valorEmDinheiro),
  };
}

type FormaPagamento = "PIX" | "DINHEIRO" | "CARTAO_DEBITO" | "CARTAO_CREDITO";

const FORMAS: { value: FormaPagamento; label: string; icon: ReactNode }[] = [
  { value: "PIX", label: "Pix", icon: <Smartphone size={13} /> },
  { value: "DINHEIRO", label: "Dinheiro", icon: <DollarSign size={13} /> },
  { value: "CARTAO_DEBITO", label: "Débito", icon: <CreditCard size={13} /> },
  { value: "CARTAO_CREDITO", label: "Crédito", icon: <CreditCard size={13} /> },
];

const FORMA_LABEL: Record<string, string> = {
  PIX: "Pix", DINHEIRO: "Dinheiro", CARTAO_DEBITO: "Débito", CARTAO_CREDITO: "Crédito",
};

const fmt = (v?: number | null) => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(v ?? 0);
const esc = (value: unknown) => String(value ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");

// Formata Data
const fmtData = (s?: any) => {
  if (!s) return "—";
  const d = Array.isArray(s) ? new Date(Date.UTC(s[0], s[1] - 1, s[2], s[3] ?? 0, s[4] ?? 0)) : new Date(typeof s === "string" ? s.replace(" ", "T") : s);
  return isNaN(d.getTime()) ? "—" : d.toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
};

function Overlay({ children, onClose }: { children: ReactNode; onClose?: () => void }) {
  return (
    <div
      onClick={(event) => event.target === event.currentTarget && onClose?.()}
      style={{
        position: "fixed", inset: 0, background: "rgba(0,0,0,0.8)", backdropFilter: "blur(4px)",
        display: "flex", alignItems: "center", justifyContent: "center", zIndex: 100, padding: 16
      }}
    >
      {children}
    </div>
  );
}

const inpStyle: React.CSSProperties = {
  width: "100%", padding: "9px 12px", background: "var(--surface-overlay)",
  border: "1px solid var(--border)", borderRadius: 8, color: "var(--foreground)",
  fontSize: 13, outline: "none", transition: "border-color 0.2s"
};
const btnG: React.CSSProperties = {
  display: "flex", alignItems: "center", gap: 6, padding: "8px 12px",
  background: "transparent", border: "1px solid var(--border)", borderRadius: 8,
  color: "var(--foreground-muted)", fontSize: 13, cursor: "pointer", justifyContent: "center"
};

function formatarDocumentoEmpresa(value?: string | null) {
  const digits = value?.replace(/\D/g, "") ?? "";
  if (digits.length === 14) return `CNPJ: ${digits.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5")}`;
  if (digits.length === 11) return `CPF: ${digits.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, "$1.$2.$3-$4")}`;
  return value ? `Documento: ${value}` : "";
}

function gerarCupom(venda: Venda, nomeEmpresa: string, documentoEmpresa?: string | null) {
  const misto = venda.formaPagamento2 && venda.valorPagamento2;
  const pagamento = misto
    ? `${esc(FORMA_LABEL[venda.formaPagamento] ?? venda.formaPagamento)}: ${esc(fmt(venda.valorFinal - (venda.valorPagamento2 ?? 0)))} + ${esc(FORMA_LABEL[venda.formaPagamento2!] ?? venda.formaPagamento2)}: ${esc(fmt(venda.valorPagamento2))}`
    : esc(FORMA_LABEL[venda.formaPagamento] ?? venda.formaPagamento);

  const itensHtml = venda.itens.map((item) => `
    <tr>
      <td style="padding:3px 0;font-size:12px;color:#1a1a2e">${esc(item.nomeProduto || `Item #${item.idProduto}`)} × ${item.quantidade}</td>
      <td style="padding:3px 0;font-size:12px;color:#1a1a2e;text-align:right;font-weight:600">${esc(fmt(item.subtotal))}</td>
    </tr>`).join("");

  const html = `<!DOCTYPE html><html lang="pt-BR"><head><meta charset="UTF-8"><title>Cupom #${venda.id} — ${esc(nomeEmpresa)}</title><style>
  @page { size: 80mm auto; margin: 4mm; }
  @media print { body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } .no-print { display: none !important; } }
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: 'Courier New', Courier, monospace; background: #f5f5f5; display: flex; flex-direction: column; align-items: center; padding: 16px; }
  .cupom { background: #fff; width: 80mm; padding: 12px 10px; border-radius: 4px; box-shadow: 0 1px 6px rgba(0,0,0,.12); }
  .center { text-align: center; }
  .empresa { font-size: 16px; font-weight: 900; color: #1a1a2e; letter-spacing: .03em; }
  .doc { font-size: 9px; color: #64748b; text-transform: uppercase; letter-spacing: .12em; margin: 3px 0 8px; }
  .dash { border-top: 1px dashed #cbd5e1; margin: 8px 0; }
  .row { display: flex; justify-content: space-between; font-size: 11px; color: #334155; padding: 2px 0; }
  .total-row { display: flex; justify-content: space-between; font-size: 15px; font-weight: 900; color: #0f172a; padding: 4px 0; }
  .green { color: #059669 !important; }
  .red { color: #dc2626 !important; }
  .footer { text-align: center; margin-top: 8px; font-size: 9px; color: #94a3b8; line-height: 1.5; }
  .print-btn { margin: 16px 0 0; padding: 10px 24px; background: #10b981; color: #fff; border: none; border-radius: 8px; font-size: 14px; font-weight: 700; cursor: pointer; }
  </style></head><body>
  <div class="cupom">
    <div class="center"><div class="empresa">${esc(nomeEmpresa)}</div>${documentoEmpresa ? `<div style="font-size:10px;color:#475569;margin-top:3px">${esc(formatarDocumentoEmpresa(documentoEmpresa))}</div>` : ""}<div class="doc">Cupom Não Fiscal</div></div>
    <div class="dash"></div>
    <div class="row"><span>Nº da Venda:</span><span><b>#${venda.id}</b></span></div>
    <div class="row"><span>Data/Hora:</span><span>${esc(fmtData(venda.dataVenda))}</span></div>
    ${venda.nomeCliente ? `<div class="row"><span>Cliente:</span><span>${esc(venda.nomeCliente)}</span></div>` : ""}
    <div class="dash"></div>
    <table style="width:100%;border-collapse:collapse">
      <thead><tr><th style="font-size:9px;color:#64748b;text-align:left;padding:2px 0;text-transform:uppercase;border-bottom:1px solid #e2e8f0">Produto</th><th style="font-size:9px;color:#64748b;text-align:right;padding:2px 0;text-transform:uppercase;border-bottom:1px solid #e2e8f0">Valor</th></tr></thead>
      <tbody>${itensHtml}</tbody>
    </table>
    <div class="dash"></div>
    <div class="row"><span>Subtotal:</span><span>${esc(fmt(venda.valorTotal))}</span></div>
    ${venda.desconto > 0 ? `<div class="row red"><span>Desconto:</span><span>− ${esc(fmt(venda.desconto))}</span></div>` : ""}
    <div class="total-row"><span>TOTAL:</span><span class="green">${esc(fmt(venda.valorFinal))}</span></div>
    <div class="dash"></div>
    <div class="row"><span>Pagamento:</span><span style="text-align:right;max-width:55%;font-weight:600">${pagamento}</span></div>
    ${venda.valorRecebido && venda.valorRecebido > 0 ? `<div class="row"><span>Recebido:</span><span>${esc(fmt(venda.valorRecebido))}</span></div>` : ""}
    ${venda.troco && venda.troco > 0 ? `<div class="row green"><span>Troco:</span><span><b>${esc(fmt(venda.troco))}</b></span></div>` : ""}
    ${venda.observacao ? `<div class="dash"></div><div class="row"><span>Obs:</span><span>${esc(venda.observacao)}</span></div>` : ""}
    <div class="dash"></div><div class="footer">Obrigado pela preferência!<br>Este documento não tem valor fiscal.<br>Emitido via Gevyro</div>
  </div>
  <button class="print-btn no-print" onclick="window.print()">🖨️ Imprimir / Salvar PDF</button>
  <script>window.onload = () => setTimeout(() => { window.focus(); window.print(); }, 400);</script>
  </body></html>`;

  const janela = window.open("", "_blank", "width=500,height=700");
  if (!janela) { alert("Permita pop-ups para imprimir o cupom."); return; }
  janela.document.write(html);
  janela.document.close();
}

function SeletorForma({ value, onChange, label }: { value: FormaPagamento; onChange: (v: FormaPagamento) => void; label?: string; }) {
  return (
    <div>
      {label && <p style={{ fontSize: 10, fontWeight: 600, color: "var(--foreground-muted)", textTransform: "uppercase", letterSpacing: ".06em", marginBottom: 6 }}>{label}</p>}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 5 }}>
        {FORMAS.map((f) => (
          <button key={f.value} onClick={() => onChange(f.value)} style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 8px", background: value === f.value ? "var(--primary-muted)" : "var(--surface-overlay)", border: `1px solid ${value === f.value ? "var(--primary)" : "var(--border)"}`, borderRadius: 7, cursor: "pointer", color: value === f.value ? "var(--primary)" : "var(--foreground-muted)", fontSize: 11, fontWeight: value === f.value ? 600 : 400 }}>
            {f.icon} {f.label}
          </button>
        ))}
      </div>
    </div>
  );
}

function TituloEtapa({ numero, titulo, detalhe }: { numero: number; titulo: string; detalhe?: string }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 9, marginBottom: 11 }}>
      <span style={{ width: 23, height: 23, borderRadius: 7, background: "var(--primary-muted)", color: "var(--primary)", display: "grid", placeItems: "center", fontSize: 11, fontWeight: 800, flexShrink: 0 }}>{numero}</span>
      <div>
        <p style={{ fontSize: 12, fontWeight: 750, color: "var(--foreground)", margin: 0 }}>{titulo}</p>
        {detalhe && <p style={{ fontSize: 10, color: "var(--foreground-subtle)", margin: "2px 0 0" }}>{detalhe}</p>}
      </div>
    </div>
  );
}

function TelaVendaSucesso({ venda, nomeEmpresa, documentoEmpresa, onFechar }: { venda: Venda; nomeEmpresa: string; documentoEmpresa?: string | null; onFechar: () => void; }) {
  const [passo, setPasso] = useState<"sucesso" | "nota">("sucesso");
  const misto = venda.formaPagamento2 && venda.valorPagamento2;

  useEffect(() => {
    if (passo !== "sucesso") return;
    const t = setTimeout(() => setPasso("nota"), 5000);
    return () => clearTimeout(t);
  }, [passo]);

  if (passo === "nota") {
    return (
      <Overlay>
        <div className="animate-fade-in" style={{ background: "var(--surface-elevated)", border: "1px solid var(--border)", borderRadius: 20, padding: 36, textAlign: "center", maxWidth: 340, width: "100%" }}>
          <div style={{ width: 60, height: 60, borderRadius: "50%", background: "rgba(59,130,246,0.1)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px" }}>
            <Receipt size={28} color="#3b82f6" />
          </div>
          <h2 style={{ fontSize: 18, fontWeight: 700, color: "var(--foreground)", margin: "0 0 8px" }}>Deseja o cupom?</h2>
          <p style={{ fontSize: 13, color: "var(--foreground-muted)", marginBottom: 24 }}>Imprimir cupom não fiscal da venda <strong style={{ color: "var(--foreground)" }}>#{venda.id}</strong></p>
          <div style={{ display: "flex", gap: 10 }}>
            <button onClick={onFechar} style={{ flex: 1, padding: "11px 0", background: "transparent", border: "1px solid var(--border)", borderRadius: 10, color: "var(--foreground-muted)", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>Não</button>
            <button onClick={() => { gerarCupom(venda, nomeEmpresa, documentoEmpresa); onFechar(); }} style={{ flex: 2, padding: "11px 0", background: "#3b82f6", border: "none", borderRadius: 10, color: "#fff", fontSize: 14, fontWeight: 700, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 8 }}>
              <Receipt size={16} /> Sim, imprimir
            </button>
          </div>
        </div>
      </Overlay>
    );
  }

  return (
    <Overlay>
      <div className="animate-fade-in" style={{ background: "var(--surface-elevated)", border: "1px solid rgba(16,185,129,0.3)", borderRadius: 20, padding: 36, textAlign: "center", maxWidth: 360, width: "100%" }}>
        <div style={{ width: 68, height: 68, borderRadius: "50%", background: "rgba(16,185,129,0.1)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px" }}>
          <CheckCircle2 size={34} color="var(--primary)" />
        </div>
        <h2 style={{ fontSize: 20, fontWeight: 700, color: "var(--foreground)", margin: "0 0 6px" }}>Venda Concluída!</h2>
        <p style={{ fontSize: 30, fontWeight: 800, color: "var(--primary)", margin: "0 0 6px" }}>{fmt(venda.valorFinal)}</p>

        <div style={{ display: "flex", justifyContent: "center", gap: 8, marginBottom: 10, flexWrap: "wrap" }}>
          <span style={{ fontSize: 12, padding: "3px 10px", background: "var(--primary-muted)", color: "var(--primary)", borderRadius: 99, fontWeight: 500 }}>
            {FORMA_LABEL[venda.formaPagamento] ?? venda.formaPagamento} {misto && `: ${fmt(venda.valorFinal - (venda.valorPagamento2 ?? 0))}`}
          </span>
          {misto && (
            <span style={{ fontSize: 12, padding: "3px 10px", background: "var(--secondary-muted)", color: "var(--secondary)", borderRadius: 99, fontWeight: 500 }}>
              {FORMA_LABEL[venda.formaPagamento2!] ?? venda.formaPagamento2}: {fmt(venda.valorPagamento2)}
            </span>
          )}
        </div>

        {venda.troco != null && venda.troco > 0 && (
          <div style={{ background: "rgba(16,185,129,0.08)", border: "1px solid rgba(16,185,129,0.2)", borderRadius: 10, padding: "10px 16px", marginBottom: 10 }}>
            <p style={{ fontSize: 12, color: "var(--foreground-muted)", margin: "0 0 2px" }}>Recebido: {fmt(venda.valorRecebido)}</p>
            <p style={{ fontSize: 18, fontWeight: 700, color: "var(--primary)", margin: 0 }}>Troco: {fmt(venda.troco)}</p>
          </div>
        )}

        {venda.desconto > 0 && <p style={{ fontSize: 12, color: "var(--foreground-muted)", marginBottom: 8 }}>Desconto: {fmt(venda.desconto)}</p>}
        <p style={{ fontSize: 12, color: "var(--foreground-subtle)", marginBottom: 20 }}>Venda #{venda.id} · {venda.itens.length} item(s)</p>
        <button onClick={() => setPasso("nota")} style={{ display: "flex", alignItems: "center", justifyContent: "center", width: "100%", padding: "11px 0", background: "var(--primary)", border: "none", borderRadius: 8, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Continuar</button>
        <p style={{ fontSize: 11, color: "var(--foreground-subtle)", marginTop: 10 }}>Perguntará sobre o cupom em 5s</p>
      </div>
    </Overlay>
  );
}


interface Props {
  caixaId: number;
  empresaId: number;
  onClose: () => void;
  onConcluido: (venda?: Venda) => void;
}

export default function NovaVenda({ caixaId, empresaId, onClose, onConcluido }: Props) {
  const { empresaAtiva } = useEmpresa();
  const [produtos, setProdutos] = useState<Produto[]>([]);
  const [carrinho, setCarrinho] = useState<ItemCarrinho[]>([]);
  const [busca, setBusca] = useState("");
  
  // Estado Financeiro
  const [forma, setForma] = useState<FormaPagamento>("PIX");
  const [misto, setMisto] = useState(false);
  const [forma2, setForma2] = useState<FormaPagamento>("DINHEIRO");
  const [valPag2, setValPag2] = useState("");
  const [desconto, setDesconto] = useState("");
  const [recebido, setRecebido] = useState("");
  const [observacao, setObservacao] = useState("");
  const [salvando, setSalvando] = useState(false);
  
  // Estado de Sucesso
  const [vendaSucesso, setVendaSucesso] = useState<Venda | null>(null);

  // Busca do Catálogo
  useEffect(() => {
    const fetchProdutos = async () => {
      try {
        setProdutos(await fetchAuthJson<Produto[]>(`/api/v1/produtos?empresaId=${empresaId}`));
      } catch {
        toast.error("Erro ao carregar catálogo.");
      }
    };
    fetchProdutos();
  }, [empresaId]);

  const filtrados = useMemo(() =>
    produtos.filter(p => p.quantidadeEstoque > 0 && p.nome.toLowerCase().includes(busca.toLowerCase())),
  [produtos, busca]);

  // Ações do Carrinho
  const addItem = (p: Produto) => setCarrinho(prev => {
    const ex = prev.find(i => i.produto.id === p.id);
    if (ex) {
      if (ex.quantidade >= p.quantidadeEstoque) { toast.error(`Máximo em estoque: ${p.quantidadeEstoque}`); return prev; }
      return prev.map(i => i.produto.id === p.id ? { ...i, quantidade: i.quantidade + 1 } : i);
    }
    return [...prev, { produto: p, quantidade: 1 }];
  });

  const setQtd = (id: number, q: number) => {
    if (q <= 0) setCarrinho(prev => prev.filter(i => i.produto.id !== id));
    else setCarrinho(prev => prev.map(i => i.produto.id === id ? { ...i, quantidade: q } : i));
  };

  // Cálculos Financeiros Dinâmicos
  const subtotal = carrinho.reduce((s, i) => s + i.produto.preco * i.quantidade, 0);
  const descontoN = Math.max(0, parseFloat(desconto.replace(",", ".")) || 0);
  const total = Math.max(subtotal - descontoN, 0);
  
  const recebidoN = parseFloat(recebido.replace(",", ".")) || 0;
  const valPag2N = misto ? parseFloat(valPag2.replace(",", ".")) || 0 : 0;
  const valPag1 = misto ? Math.max(total - valPag2N, 0) : total;

  const temDinheiro = forma === "DINHEIRO" || (misto && forma2 === "DINHEIRO");
  const valorEmDinheiro = misto ? (forma === "DINHEIRO" ? valPag1 : forma2 === "DINHEIRO" ? valPag2N : 0) : total;
  
  const troco = temDinheiro && recebidoN > 0 ? Math.max(recebidoN - valorEmDinheiro, 0) : null;
  const falta = temDinheiro && recebidoN > 0 && recebidoN < valorEmDinheiro ? valorEmDinheiro - recebidoN : null;

  const registrar = async () => {
    if (salvando) return;
    if (!carrinho.length) { toast.error("Adicione produtos ao pedido."); return; }
    if (subtotal <= 0) { toast.error("A venda precisa ter um valor maior que zero."); return; }
    if (descontoN >= subtotal) { toast.error("O desconto deve ser menor que o subtotal da venda."); return; }
    const itemInvalido = carrinho.find(item => item.quantidade <= 0 || item.quantidade > item.produto.quantidadeEstoque);
    if (itemInvalido) { toast.error(`Revise a quantidade disponível de ${itemInvalido.produto.nome}.`); return; }
    if (misto && forma === forma2) { toast.error("Escolha duas formas de pagamento diferentes."); return; }
    if (misto && valPag2N <= 0) { toast.error("Informe o valor da segunda forma de pagamento."); return; }
    if (misto && valPag2N >= total) { toast.error("Informe apenas a parte paga na 2ª forma. Ela precisa ser menor que o total para sobrar valor na 1ª forma."); return; }
    if (temDinheiro && recebidoN > 0 && recebidoN < valorEmDinheiro) { toast.error(`Faltam ${fmt(valorEmDinheiro - recebidoN)} no pagamento em dinheiro.`); return; }
    
    setSalvando(true);
    try {
      const body: any = {
        idCaixa: caixaId,
        formaPagamento: forma,
        desconto: descontoN,
        observacao: observacao || null,
        itens: carrinho.map((i) => ({ idProduto: i.produto.id, quantidade: i.quantidade })),
      };
      
      if (misto) {
        body.formaPagamento2 = forma2;
        body.valorPagamento2 = valPag2N;
      }
      if (temDinheiro) body.valorRecebido = recebidoN > 0 ? recebidoN : valorEmDinheiro;

      const vendaConcluida = normalizarTroco(await fetchAuthJson<Venda>("/api/v1/vendas/registrar", {
        method: "POST",
        body: JSON.stringify(body),
      }));
      
      toast.success("Venda finalizada com sucesso!");
      
      // Adiciona o nome do produto de volta aos itens para o cupom não quebrar
      const itensComNome = vendaConcluida.itens.map((iv: any) => {
        const p = produtos.find(prod => prod.id === iv.idProduto);
        return { ...iv, nomeProduto: p ? p.nome : `Produto #${iv.idProduto}` };
      });
      vendaConcluida.itens = itensComNome;

      setVendaSucesso(vendaConcluida); // Ativa a tela de sucesso do próprio componente
    } catch (e: any) {
      toast.error(e.message);
    } finally {
      setSalvando(false);
    }
  };

  const fecharTudo = () => {
    onConcluido(vendaSucesso || undefined);
    onClose();
  };

  // Se tem venda sucesso, intercepta e mostra a telinha do cupom ao invés do PDV
  if (vendaSucesso) {
    return (
      <TelaVendaSucesso 
        venda={vendaSucesso} 
        nomeEmpresa={empresaAtiva?.nomeFantasia || "Empresa"} 
        documentoEmpresa={empresaAtiva?.cnpj || empresaAtiva?.cpf}
        onFechar={fecharTudo} 
      />
    );
  }

  return (
    <Overlay onClose={onClose}>
      <div className="animate-fade-in quick-sale-modal" style={{
        background: "var(--surface-elevated)", border: "1px solid var(--border)",
        borderRadius: 18, width: "100%", maxWidth: 1080, maxHeight: "calc(100vh - 24px)",
        display: "flex", flexDirection: "column", overflowY: "auto", overflowX: "hidden",
        boxShadow: "0 20px 40px rgba(0,0,0,0.4)"
      }}>
        
        {/* Header Modal */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 20px", borderBottom: "1px solid var(--border)" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <div style={{ background: "rgba(16,185,129,0.15)", padding: 8, borderRadius: 10 }}>
              <ShoppingCart size={18} color="var(--primary)" />
            </div>
            <div>
              <h2 style={{ fontSize: 16, fontWeight: 750, color: "var(--foreground)", margin: 0 }}>Nova venda</h2>
              <p style={{ fontSize: 11, color: "var(--foreground-muted)", margin: "2px 0 0" }}>Caixa #{caixaId} aberto · produtos e pagamento</p>
            </div>
          </div>
          <button onClick={onClose} style={{ background: "var(--surface-overlay)", border: "1px solid var(--border)", borderRadius: 8, padding: 6, cursor: "pointer", color: "var(--foreground-muted)" }}>
            <X size={16} />
          </button>
        </div>

        {/* Layout em 2 Colunas */}
        <div className="venda-layout quick-sale-grid" style={{ display: "grid", gridTemplateColumns: "minmax(0, 1.25fr) minmax(380px, .9fr)", alignItems: "start" }}>
          
          {/* COLUNA ESQUERDA: Busca e Carrinho */}
          <div className="venda-catalogo" style={{ display: "flex", flexDirection: "column", borderRight: "1px solid var(--border)", background: "var(--surface-main)" }}>
            
            <div style={{ padding: "14px 18px 12px", borderBottom: "1px solid var(--border)" }}>
              <TituloEtapa numero={1} titulo="Escolha os produtos" detalhe={`${filtrados.length} produto(s) disponível(is)`} />
              <div style={{ position: "relative" }}>
                <Search size={14} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: "var(--foreground-subtle)" }} />
                <input 
                  style={{ ...inpStyle, paddingLeft: 34, background: "var(--surface-elevated)" }} 
                  placeholder="Pesquisar produto..." 
                  value={busca} 
                  onChange={e => setBusca(e.target.value)} 
                  autoFocus 
                />
              </div>
            </div>

            {/* Catálogo de Produtos */}
            <div style={{ overflowY: "auto", maxHeight: 230, borderBottom: "1px solid var(--border)" }}>
              {filtrados.length === 0 ? (
                 <div style={{ padding: 40, textAlign: "center", color: "var(--foreground-subtle)" }}>Nenhum produto encontrado.</div>
              ) : (
                filtrados.map(p => {
                  const noCarrinho = carrinho.find(i => i.produto.id === p.id);
                  return (
                    <div key={p.id} onClick={() => addItem(p)}
                      style={{ 
                        display: "flex", alignItems: "center", justifyContent: "space-between", 
                        padding: "10px 18px", cursor: "pointer", borderBottom: "1px solid var(--border-subtle)",
                        transition: "background 0.1s"
                      }}
                      onMouseEnter={e => ((e.currentTarget as HTMLDivElement).style.background = "var(--surface-overlay)")}
                      onMouseLeave={e => ((e.currentTarget as HTMLDivElement).style.background = "transparent")}
                    >
                      <div>
                        <p style={{ fontSize: 13, fontWeight: 600, color: "var(--foreground)", margin: 0 }}>{p.nome}</p>
                        <p style={{ fontSize: 11, color: "var(--foreground-subtle)", margin: "4px 0 0" }}>
                          Em estoque: <span style={{ color: "var(--foreground-muted)" }}>{p.quantidadeEstoque} un.</span>
                        </p>
                      </div>
                      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                        <span style={{ fontSize: 14, fontWeight: 700, color: "var(--primary)" }}>{fmt(p.preco)}</span>
                        {noCarrinho ? (
                          <div style={{ background: "var(--primary)", color: "#000", fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 99 }}>
                            {noCarrinho.quantidade}×
                          </div>
                        ) : (
                          <div style={{ background: "var(--surface-overlay)", border: "1px solid var(--border)", padding: 4, borderRadius: 6 }}>
                            <Plus size={14} color="var(--foreground-muted)" />
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })
              )}
            </div>

            {/* Mini-carrinho inferior */}
            <div style={{ maxHeight: 260, minHeight: carrinho.length ? 110 : 82, overflowY: "auto", padding: "12px 18px", background: "var(--surface-overlay)" }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 10 }}>
                <p style={{ fontSize: 11, fontWeight: 750, color: "var(--foreground)", margin: 0 }}>Carrinho</p>
                <span style={{ fontSize: 10, color: "var(--foreground-muted)", background: "var(--surface-elevated)", border: "1px solid var(--border)", borderRadius: 99, padding: "3px 8px" }}>{carrinho.reduce((s, item) => s + item.quantidade, 0)} item(ns)</span>
              </div>
              {carrinho.length === 0 ? (
                <p style={{ fontSize: 12, color: "var(--foreground-subtle)", fontStyle: "italic" }}>Clique nos produtos acima para adicionar.</p>
              ) : (
                carrinho.map(item => (
                  <div key={item.produto.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 8, background: "var(--surface-elevated)", border: "1px solid var(--border)", padding: "8px 12px", borderRadius: 8 }}>
                    <div style={{ flex: 1 }}>
                      <p style={{ fontSize: 12, fontWeight: 600, color: "var(--foreground)", margin: 0 }}>{item.produto.nome}</p>
                      <p style={{ fontSize: 11, color: "var(--foreground-muted)", margin: "2px 0 0" }}>{fmt(item.produto.preco)} un.</p>
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                      <div style={{ display: "flex", alignItems: "center", background: "var(--surface-main)", border: "1px solid var(--border)", borderRadius: 6, overflow: "hidden" }}>
                        <button onClick={() => setQtd(item.produto.id, item.quantidade - 1)} style={{ width: 24, height: 24, background: "transparent", border: "none", cursor: "pointer", color: "var(--foreground-muted)" }}>
                          <Minus size={12} />
                        </button>
                        <span style={{ fontSize: 12, fontWeight: 600, minWidth: 20, textAlign: "center" }}>{item.quantidade}</span>
                        <button onClick={() => setQtd(item.produto.id, item.quantidade + 1)} disabled={item.quantidade >= item.produto.quantidadeEstoque} style={{ width: 24, height: 24, background: "transparent", border: "none", cursor: item.quantidade >= item.produto.quantidadeEstoque ? "not-allowed" : "pointer", color: "var(--primary)" }}>
                          <Plus size={12} />
                        </button>
                      </div>
                      <button onClick={() => setCarrinho(prev => prev.filter(i => i.produto.id !== item.produto.id))} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--foreground-subtle)" }}>
                        <X size={14} />
                      </button>
                    </div>
                  </div>
                ))
              )}
            </div>

          </div>

          {/* COLUNA DIREITA: Pagamento e Finalização */}
          <div className="venda-pagamento" style={{ display: "flex", flexDirection: "column", background: "var(--surface-elevated)" }}>
            <div style={{ padding: "14px 16px", display: "flex", flexDirection: "column", gap: 10 }}>
              
              {/* Formas de Pagamento */}
              <div style={{ padding: 12, border: "1px solid var(--border)", borderRadius: 12, background: "var(--surface-main)" }}>
                <TituloEtapa numero={2} titulo="Defina o pagamento" detalhe={misto ? `Primeira forma: ${fmt(valPag1)}` : `Total nesta forma: ${fmt(total)}`} />
                <SeletorForma value={forma} onChange={setForma} />
                
                <button
                  onClick={() => { setMisto(v => !v); setValPag2(""); setRecebido(""); }}
                  style={{ ...btnG, width: "100%", marginTop: 8, background: misto ? "rgba(59,130,246,0.05)" : "transparent", borderColor: misto ? "#3b82f6" : "var(--border)", color: misto ? "#3b82f6" : "var(--foreground-muted)" }}
                >
                  {misto ? <><X size={13} /> Usar uma forma</> : <><Plus size={13} /> Dividir pagamento</>}
                </button>
              </div>

              {/* Pagamento Secundário (Misto) */}
              {misto && (
                <div style={{ padding: 12, background: "rgba(59,130,246,0.05)", border: "1px solid rgba(59,130,246,0.2)", borderRadius: 12 }}>
                  <SeletorForma label="Segunda forma" value={forma2} onChange={f => { setForma2(f); setRecebido(""); }} />
                  <div style={{ marginTop: 10 }}>
                    <label style={{ fontSize: 10, fontWeight: 600, color: "var(--foreground-muted)", textTransform: "uppercase", display: "block", marginBottom: 4 }}>{forma2 === "DINHEIRO" ? "Quanto do total será pago em dinheiro?" : `Quanto será pago em ${FORMA_LABEL[forma2]}?`}</label>
                    <input style={inpStyle} type="number" min="0" max={Math.max(total - 0.01, 0)} step="0.01" value={valPag2} onChange={e => setValPag2(e.target.value)} placeholder="Ex.: 10,00" />
                    <p style={{ fontSize: 10, color: "var(--foreground-subtle)", margin: "6px 0 0" }}>Restante em {FORMA_LABEL[forma]}: <strong>{fmt(valPag1)}</strong></p>
                  </div>
                  {valPag2N >= total && valPag2N > 0 && (
                    <p style={{ fontSize: 11, color: "var(--destructive)", marginTop: 6, fontWeight: 500 }}>⚠ Informe somente a parte paga nessa forma. Para dinheiro + {FORMA_LABEL[forma]}, use um valor menor que {fmt(total)}.</p>
                  )}
                </div>
              )}

              {/* Dinheiro / Troco Dinâmico */}
              {temDinheiro && (
                <div style={{ padding: 12, border: "1px solid var(--border)", borderRadius: 12, background: "var(--surface-main)" }}>
                  <label style={{ fontSize: 10, fontWeight: 600, color: "var(--foreground-muted)", textTransform: "uppercase", display: "block", marginBottom: 6 }}>
                    Valor da nota entregue {misto && valorEmDinheiro > 0 && <span style={{ textTransform: "none", fontWeight: 400 }}>(parte em dinheiro: {fmt(valorEmDinheiro)})</span>}
                  </label>
                  <input style={inpStyle} type="number" min="0" step="0.01" value={recebido} onChange={e => setRecebido(e.target.value)} placeholder="Opcional — use para calcular o troco" />
                  <p style={{ fontSize: 10, color: "var(--foreground-subtle)", margin: "6px 0 0" }}>Preencha somente se precisar calcular o troco.</p>
                  
                  {recebidoN > 0 && recebidoN >= valorEmDinheiro && troco !== null && troco > 0 && (
                    <div style={{ display: "flex", justifyContent: "space-between", background: "rgba(16,185,129,0.1)", padding: "10px 14px", borderRadius: 8, marginTop: 8 }}>
                      <span style={{ fontSize: 12, color: "var(--success)" }}>Devolver Troco:</span>
                      <span style={{ fontSize: 14, fontWeight: 800, color: "var(--success)" }}>{fmt(troco)}</span>
                    </div>
                  )}
                  {falta !== null && falta > 0 && (
                    <div style={{ display: "flex", justifyContent: "space-between", background: "rgba(239,68,68,0.1)", padding: "10px 14px", borderRadius: 8, marginTop: 8 }}>
                      <span style={{ fontSize: 12, color: "var(--destructive)" }}>Falta receber:</span>
                      <span style={{ fontSize: 14, fontWeight: 800, color: "var(--destructive)" }}>{fmt(falta)}</span>
                    </div>
                  )}
                </div>
              )}

              {/* Desconto & Obs */}
              <div style={{ padding: 12, border: "1px solid var(--border)", borderRadius: 12, background: "var(--surface-main)" }}>
                <TituloEtapa numero={3} titulo="Ajustes opcionais" />
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1.5fr", gap: 10 }}>
                  <div>
                    <label style={{ fontSize: 10, fontWeight: 600, color: "var(--foreground-muted)", textTransform: "uppercase", display: "block", marginBottom: 6 }}>Desconto R$</label>
                    <input style={inpStyle} type="number" min="0" step="0.01" value={desconto} onChange={e => setDesconto(e.target.value)} placeholder="0,00" />
                  </div>
                  <div>
                    <label style={{ fontSize: 10, fontWeight: 600, color: "var(--foreground-muted)", textTransform: "uppercase", display: "block", marginBottom: 6 }}>Observação</label>
                    <input style={inpStyle} value={observacao} onChange={e => setObservacao(e.target.value)} placeholder="Opcional..." />
                  </div>
                </div>
              </div>

            </div>

            {/* Rodapé de Resumo e Botão */}
            <div style={{ padding: "12px 16px 14px", borderTop: "1px solid var(--border)", background: "var(--surface-overlay)" }}>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, color: "var(--foreground-muted)", marginBottom: 6 }}>
                <span>Subtotal dos Itens</span><span>{fmt(subtotal)}</span>
              </div>
              {descontoN > 0 && (
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, color: "var(--destructive)", marginBottom: 6 }}>
                  <span>Desconto Aplicado</span><span>− {fmt(descontoN)}</span>
                </div>
              )}
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: 18, fontWeight: 800, color: "var(--primary)", marginTop: 8, paddingTop: 10, borderTop: "1px solid var(--border)", marginBottom: 16 }}>
                <span>Total a Pagar</span><span>{fmt(total)}</span>
              </div>

              <button 
                onClick={registrar} 
                disabled={salvando || !carrinho.length}
                style={{
                  width: "100%", padding: "14px 0", borderRadius: 10, background: "var(--primary)", border: "none",
                  color: "#fff", fontSize: 14, fontWeight: 700, cursor: salvando || !carrinho.length ? "not-allowed" : "pointer",
                  display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
                  opacity: salvando || !carrinho.length ? 0.5 : 1, transition: "all 0.2s"
                }}
              >
                {salvando ? "Processando Pagamento..." : <><Check size={16} /> Finalizar Venda</>}
              </button>
            </div>
          </div>
        </div>
        <style jsx>{`
          @media (max-width: 760px) {
            .venda-layout { grid-template-columns: 1fr !important; overflow-y: auto; }
            .venda-catalogo { min-height: 520px !important; border-right: 0 !important; border-bottom: 1px solid var(--border); }
            .venda-pagamento { overflow: visible !important; }
          }
        `}</style>
      </div>
    </Overlay>
  );
}
