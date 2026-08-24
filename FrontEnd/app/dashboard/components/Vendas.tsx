"use client";

import { useEffect, useState, useMemo } from "react";
import { useEmpresa } from "../context/Empresacontext";
import {
  Plus,
  X,
  Check,
  Search,
  ChevronDown,
  ChevronUp,
  AlertCircle,
  CreditCard,
  DollarSign,
  Smartphone,
  Receipt,
  Store,
  ChevronRight,
  BarChart3,
  CheckCircle2,
  Trash2,
  Edit2,
  Ban,
} from "lucide-react";
import { toast } from "sonner";
import { fetchAuthJson as apiFetchAuthJson } from "@/lib/api-v2";
import { prepararSomDeVenda } from "@/lib/som-venda";

interface Produto {
  id: number;
  nome: string;
  preco: number;
  quantidadeEstoque: number;
  categoria?: string;
  ativo?: boolean;
}
interface ItemCarrinho {
  produto: Produto;
  quantidade: number;
}
interface ItemVendaDTO {
  idProduto: number;
  nomeProduto: string;
  quantidade: number;
  precoUnitario: number;
  subtotal: number;
}
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
  itens: ItemVendaDTO[];
  nomeCliente?: string;
  cancelada?: boolean;
  motivoCancelamento?: string;
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
interface CaixaInfo {
  id: number;
  status: string;
  aberto: boolean;
  valorInicial: number;
  valorFinal?: number;
  totalVendas: number;
  dataAbertura: string;
  dataFechamento?: string;
  empresaId: number;
}
type FormaPagamento = "PIX" | "DINHEIRO" | "CARTAO_DEBITO" | "CARTAO_CREDITO";

const FORMAS: {
  value: FormaPagamento;
  label: string;
  icon: React.ReactNode;
}[] = [
  { value: "PIX", label: "Pix", icon: <Smartphone size={14} /> },
  { value: "DINHEIRO", label: "Dinheiro", icon: <DollarSign size={14} /> },
  { value: "CARTAO_DEBITO", label: "Débito", icon: <CreditCard size={14} /> },
  { value: "CARTAO_CREDITO", label: "Crédito", icon: <CreditCard size={14} /> },
];
const FORMA_LABEL: Record<string, string> = {
  PIX: "Pix",
  DINHEIRO: "Dinheiro",
  CARTAO_DEBITO: "Débito",
  CARTAO_CREDITO: "Crédito",
};

const fmt = (v?: number | null) =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(
    v ?? 0,
  );
const esc = (value: unknown) =>
  String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");

// Parseia data do backend em qualquer formato:
// string ISO, string sem timezone, array [year, month, day, hour, min, sec], ou null
const parseDate = (s?: any): Date | null => {
  if (!s) return null;
  // Array: trata como UTC e converte para horário local do usuário
  if (Array.isArray(s)) {
    const [y, mo, d, h = 0, mi = 0, sec = 0] = s;
    return new Date(Date.UTC(y, mo - 1, d, h, mi, sec));
  }
  if (typeof s !== "string") return null;

  const raw = s.trim().replace(/\.\d+/, "");
  const hasTimezone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(raw);

  // Strings com timezone explícito (Z/+00:00/-03:00)
  if (hasTimezone) {
    const d = new Date(raw);
    if (!Number.isNaN(d.getTime())) return d;
  }

  // Strings sem timezone: assume UTC do backend
  const isoNoTz = raw.match(
    /^(\d{4})-(\d{2})-(\d{2})[ T]?(\d{2})?:?(\d{2})?:?(\d{2})?$/,
  );
  if (isoNoTz) {
    const [, y, mo, d, h = "0", mi = "0", sec = "0"] = isoNoTz;
    return new Date(
      Date.UTC(
        Number(y),
        Number(mo) - 1,
        Number(d),
        Number(h),
        Number(mi),
        Number(sec),
      ),
    );
  }

  // Fallback: Date nativo
  const d = new Date(raw.replace(" ", "T"));
  if (!Number.isNaN(d.getTime())) return d;
  return null;
};

const fmtData = (s?: any) => {
  const d = parseDate(s);
  if (!d) return "—";
  return d.toLocaleString("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
};

async function fetchAuth<T>(path: string, opts?: RequestInit): Promise<T> {
  return apiFetchAuthJson<T>(path, opts);
}

const inp: React.CSSProperties = {
  width: "100%",
  padding: "8px 11px",
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
  padding: "7px 11px",
  background: "transparent",
  border: "1px solid var(--border)",
  borderRadius: 8,
  color: "var(--foreground-muted)",
  fontSize: 12,
  cursor: "pointer",
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

  const itensHtml = venda.itens
    .map(
      (item) => `
    <tr>
      <td style="padding:3px 0;font-size:12px;color:#1a1a2e">${esc(item.nomeProduto)} × ${item.quantidade}</td>
      <td style="padding:3px 0;font-size:12px;color:#1a1a2e;text-align:right;font-weight:600">${esc(fmt(item.subtotal))}</td>
    </tr>`,
    )
    .join("");

  const html = `<!DOCTYPE html><html lang="pt-BR"><head><meta charset="UTF-8">
<title>Cupom #${venda.id} — ${esc(nomeEmpresa)}</title>
<style>
  @page { size: 80mm auto; margin: 4mm; }
  @media print {
    body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    .no-print { display: none !important; }
  }
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
  .print-btn { margin: 16px 0 0; padding: 10px 24px; background: #10b981; color: #fff; border: none; border-radius: 8px; font-size: 14px; font-weight: 700; cursor: pointer; box-shadow: 0 2px 8px rgba(0,0,0,.15); }
</style></head><body>

<div class="cupom">
  <div class="center">
    <div class="empresa">${esc(nomeEmpresa)}</div>
    ${documentoEmpresa ? `<div style="font-size:10px;color:#475569;margin-top:3px">${esc(formatarDocumentoEmpresa(documentoEmpresa))}</div>` : ""}
    <div class="doc">Cupom Não Fiscal</div>
  </div>
  <div class="dash"></div>
  <div class="row"><span>Nº da Venda:</span><span><b>#${venda.id}</b></span></div>
  <div class="row"><span>Data/Hora:</span><span>${esc(fmtData(venda.dataVenda))}</span></div>
  ${venda.nomeCliente ? `<div class="row"><span>Cliente:</span><span>${esc(venda.nomeCliente)}</span></div>` : ""}
  <div class="dash"></div>

  <table style="width:100%;border-collapse:collapse">
    <thead>
      <tr>
        <th style="font-size:9px;color:#64748b;text-align:left;padding:2px 0;text-transform:uppercase;border-bottom:1px solid #e2e8f0">Produto</th>
        <th style="font-size:9px;color:#64748b;text-align:right;padding:2px 0;text-transform:uppercase;border-bottom:1px solid #e2e8f0">Valor</th>
      </tr>
    </thead>
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

  <div class="dash"></div>
  <div class="footer">
    Obrigado pela preferência!<br>
    Este documento não tem valor fiscal.<br>
    Emitido via Gevyro
  </div>
</div>

<button class="print-btn no-print" onclick="window.print()">🖨️ Imprimir / Salvar PDF</button>

<script>
  window.onload = () => setTimeout(() => { window.focus(); window.print(); }, 400);
</script>
</body></html>`;

  const janela = window.open("", "_blank", "width=500,height=700");
  if (!janela) {
    alert("Permita pop-ups para imprimir o cupom.");
    return;
  }
  janela.document.write(html);
  janela.document.close();
}

function TelaVendaSucesso({
  venda,
  nomeEmpresa,
  documentoEmpresa,
  onFechar,
}: {
  venda: Venda;
  nomeEmpresa: string;
  documentoEmpresa?: string | null;
  onFechar: () => void;
}) {
  const [passo, setPasso] = useState<"sucesso" | "nota">("sucesso");
  const misto = venda.formaPagamento2 && venda.valorPagamento2;

  // Auto-fecha após 5s apenas no passo de sucesso
  useEffect(() => {
    if (passo !== "sucesso") return;
    const t = setTimeout(() => setPasso("nota"), 5000);
    return () => clearTimeout(t);
  }, [passo]);

  if (passo === "nota")
    return (
      <div
        style={{
          position: "fixed",
          inset: 0,
          background: "rgba(0,0,0,0.85)",
          backdropFilter: "blur(6px)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          zIndex: 9999,
          padding: 16,
        }}
      >
        <div
          className="animate-fade-in"
          style={{
            background: "var(--surface-elevated)",
            border: "1px solid var(--border)",
            borderRadius: 20,
            padding: 36,
            textAlign: "center",
            maxWidth: 340,
            width: "100%",
          }}
        >
          <div
            style={{
              width: 60,
              height: 60,
              borderRadius: "50%",
              background: "rgba(59,130,246,0.1)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              margin: "0 auto 16px",
            }}
          >
            <Receipt size={28} color="#3b82f6" />
          </div>
          <h2
            style={{
              fontSize: 18,
              fontWeight: 700,
              color: "var(--foreground)",
              margin: "0 0 8px",
            }}
          >
            Deseja o cupom?
          </h2>
          <p
            style={{
              fontSize: 13,
              color: "var(--foreground-muted)",
              marginBottom: 24,
            }}
          >
            Imprimir cupom não fiscal da venda{" "}
            <strong style={{ color: "var(--foreground)" }}>#{venda.id}</strong>
          </p>
          <div style={{ display: "flex", gap: 10 }}>
            <button
              onClick={onFechar}
              style={{
                flex: 1,
                padding: "11px 0",
                background: "transparent",
                border: "1px solid var(--border)",
                borderRadius: 10,
                color: "var(--foreground-muted)",
                fontSize: 14,
                fontWeight: 600,
                cursor: "pointer",
              }}
            >
              Não
            </button>
            <button
              onClick={() => {
                gerarCupom(venda, nomeEmpresa, documentoEmpresa);
                onFechar();
              }}
              style={{
                flex: 2,
                padding: "11px 0",
                background: "#3b82f6",
                border: "none",
                borderRadius: 10,
                color: "#fff",
                fontSize: 14,
                fontWeight: 700,
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                gap: 8,
              }}
            >
              <Receipt size={16} /> Sim, imprimir
            </button>
          </div>
        </div>
      </div>
    );

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(0,0,0,0.85)",
        backdropFilter: "blur(6px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 9999,
        padding: 16,
      }}
    >
      <div
        className="animate-fade-in"
        style={{
          background: "var(--surface-elevated)",
          border: "1px solid rgba(16,185,129,0.3)",
          borderRadius: 20,
          padding: 36,
          textAlign: "center",
          maxWidth: 360,
          width: "100%",
        }}
      >
        <div
          style={{
            width: 68,
            height: 68,
            borderRadius: "50%",
            background: "rgba(16,185,129,0.1)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            margin: "0 auto 16px",
          }}
        >
          <CheckCircle2 size={34} color="var(--primary)" />
        </div>
        <h2
          style={{
            fontSize: 20,
            fontWeight: 700,
            color: "var(--foreground)",
            margin: "0 0 6px",
          }}
        >
          Venda Concluída!
        </h2>
        <p
          style={{
            fontSize: 30,
            fontWeight: 800,
            color: "var(--primary)",
            margin: "0 0 6px",
          }}
        >
          {fmt(venda.valorFinal)}
        </p>

        <div
          style={{
            display: "flex",
            justifyContent: "center",
            gap: 8,
            marginBottom: 10,
            flexWrap: "wrap",
          }}
        >
          <span
            style={{
              fontSize: 12,
              padding: "3px 10px",
              background: "var(--primary-muted)",
              color: "var(--primary)",
              borderRadius: 99,
              fontWeight: 500,
            }}
          >
            {FORMA_LABEL[venda.formaPagamento] ?? venda.formaPagamento}
            {misto &&
              `: ${fmt(venda.valorFinal - (venda.valorPagamento2 ?? 0))}`}
          </span>
          {misto && (
            <span
              style={{
                fontSize: 12,
                padding: "3px 10px",
                background: "var(--secondary-muted)",
                color: "var(--secondary)",
                borderRadius: 99,
                fontWeight: 500,
              }}
            >
              {FORMA_LABEL[venda.formaPagamento2!] ?? venda.formaPagamento2}:{" "}
              {fmt(venda.valorPagamento2)}
            </span>
          )}
        </div>

        {venda.troco != null && venda.troco > 0 && (
          <div
            style={{
              background: "rgba(16,185,129,0.08)",
              border: "1px solid rgba(16,185,129,0.2)",
              borderRadius: 10,
              padding: "10px 16px",
              marginBottom: 10,
            }}
          >
            <p
              style={{
                fontSize: 12,
                color: "var(--foreground-muted)",
                margin: "0 0 2px",
              }}
            >
              Recebido: {fmt(venda.valorRecebido)}
            </p>
            <p
              style={{
                fontSize: 18,
                fontWeight: 700,
                color: "var(--primary)",
                margin: 0,
              }}
            >
              Troco: {fmt(venda.troco)}
            </p>
          </div>
        )}

        {venda.desconto > 0 && (
          <p
            style={{
              fontSize: 12,
              color: "var(--foreground-muted)",
              marginBottom: 8,
            }}
          >
            Desconto: {fmt(venda.desconto)}
          </p>
        )}
        <p
          style={{
            fontSize: 12,
            color: "var(--foreground-subtle)",
            marginBottom: 20,
          }}
        >
          Venda #{venda.id} · {venda.itens.length} item(s)
        </p>
        <button
          onClick={() => setPasso("nota")}
          style={{
            ...btnP,
            justifyContent: "center",
            width: "100%",
            padding: "11px 0",
          }}
        >
          Continuar
        </button>
        <p
          style={{
            fontSize: 11,
            color: "var(--foreground-subtle)",
            marginTop: 10,
          }}
        >
          Perguntará sobre o cupom em 5s
        </p>
      </div>
    </div>
  );
}

function SeletorForma({
  value,
  onChange,
  label,
}: {
  value: FormaPagamento;
  onChange: (v: FormaPagamento) => void;
  label?: string;
}) {
  return (
    <div>
      {label && (
        <p
          style={{
            fontSize: 10,
            fontWeight: 600,
            color: "var(--foreground-muted)",
            textTransform: "uppercase",
            letterSpacing: ".06em",
            marginBottom: 6,
          }}
        >
          {label}
        </p>
      )}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 5 }}>
        {FORMAS.map((f) => (
          <button
            key={f.value}
            onClick={() => onChange(f.value)}
            style={{
              display: "flex",
              alignItems: "center",
              gap: 5,
              padding: "7px 8px",
              background:
                value === f.value
                  ? "var(--primary-muted)"
                  : "var(--surface-overlay)",
              border: `1px solid ${value === f.value ? "var(--primary)" : "var(--border)"}`,
              borderRadius: 7,
              cursor: "pointer",
              color:
                value === f.value
                  ? "var(--primary)"
                  : "var(--foreground-muted)",
              fontSize: 11,
              fontWeight: value === f.value ? 600 : 400,
            }}
          >
            {f.icon}
            {f.label}
          </button>
        ))}
      </div>
    </div>
  );
}

function ModalNovaVenda({
  caixaId,
  empresaId,
  onClose,
  onSucesso,
}: {
  caixaId: number;
  empresaId: number;
  onClose: () => void;
  onSucesso: (v: Venda) => void;
}) {
  const [produtos, setProdutos] = useState<Produto[]>([]);
  const [carrinho, setCarrinho] = useState<ItemCarrinho[]>([]);
  const [busca, setBusca] = useState("");
  const [forma, setForma] = useState<FormaPagamento>("PIX");
  const [misto, setMisto] = useState(false);
  const [forma2, setForma2] = useState<FormaPagamento>("DINHEIRO");
  const [valPag2, setValPag2] = useState("");
  const [desconto, setDesconto] = useState("");
  const [recebido, setRecebido] = useState("");
  const [observacao, setObservacao] = useState("");
  const [salvando, setSalvando] = useState(false);

  useEffect(() => {
    fetchAuth<Produto[]>(`/api/v1/produtos?empresaId=${empresaId}`)
      .then(setProdutos)
      .catch(() => toast.error("Erro ao carregar produtos"));
  }, [empresaId]);

 const filtrados = useMemo(() =>
    produtos.filter(p => 
      p.ativo !== false && 
      p.quantidadeEstoque > 0 && 
      p.nome.toLowerCase().includes(busca.toLowerCase())
    ),
  [produtos, busca]);

  const addItem = (p: Produto) =>
    setCarrinho((prev) => {
      const ex = prev.find((i) => i.produto.id === p.id);
      if (ex) {
        if (ex.quantidade >= p.quantidadeEstoque) {
          toast.error(`Máx: ${p.quantidadeEstoque}`);
          return prev;
        }
        return prev.map((i) =>
          i.produto.id === p.id ? { ...i, quantidade: i.quantidade + 1 } : i,
        );
      }
      return [...prev, { produto: p, quantidade: 1 }];
    });

  const setQtd = (id: number, q: number) => {
    if (q <= 0) setCarrinho((prev) => prev.filter((i) => i.produto.id !== id));
    else
      setCarrinho((prev) =>
        prev.map((i) => (i.produto.id === id ? { ...i, quantidade: q } : i)),
      );
  };

  const subtotal = carrinho.reduce(
    (s, i) => s + i.produto.preco * i.quantidade,
    0,
  );
  const descontoN = Math.max(0, parseFloat(desconto.replace(",", ".")) || 0);
  const total = Math.max(subtotal - descontoN, 0);
  const recebidoN = parseFloat(recebido.replace(",", ".")) || 0;
  const valPag2N = misto ? parseFloat(valPag2.replace(",", ".")) || 0 : 0;
  const valPag1 = misto ? Math.max(total - valPag2N, 0) : total;

  // Verifica se alguma das formas é dinheiro (para mostrar troco)
  const temDinheiro = forma === "DINHEIRO" || (misto && forma2 === "DINHEIRO");
  // Valor em dinheiro nessa venda
  const valorEmDinheiro = misto
    ? forma === "DINHEIRO"
      ? valPag1
      : forma2 === "DINHEIRO"
        ? valPag2N
        : 0
    : total;
  // Troco: quanto o cliente deu a mais no dinheiro
  const troco =
    temDinheiro && recebidoN > 0
      ? Math.max(recebidoN - valorEmDinheiro, 0)
      : null;
  const falta =
    temDinheiro && recebidoN > 0 && recebidoN < valorEmDinheiro
      ? valorEmDinheiro - recebidoN
      : null;

  const registrar = async () => {
    if (salvando) return;
    if (!carrinho.length) {
      toast.error("Adicione pelo menos um produto.");
      return;
    }
    if (subtotal <= 0) {
      toast.error("A venda precisa ter um valor maior que zero.");
      return;
    }
    if (descontoN >= subtotal) {
      toast.error("O desconto deve ser menor que o subtotal da venda.");
      return;
    }
    const itemInvalido = carrinho.find(
      (item) => item.quantidade <= 0 || item.quantidade > item.produto.quantidadeEstoque,
    );
    if (itemInvalido) {
      toast.error(`Revise a quantidade disponível de ${itemInvalido.produto.nome}.`);
      return;
    }
    if (misto && forma === forma2) {
      toast.error("Escolha duas formas de pagamento diferentes.");
      return;
    }
    if (misto && valPag2N <= 0) {
      toast.error("Informe o valor da segunda forma de pagamento.");
      return;
    }
    if (temDinheiro && recebidoN > 0 && recebidoN < valorEmDinheiro) {
      toast.error(`Faltam ${fmt(valorEmDinheiro - recebidoN)} no pagamento em dinheiro.`);
      return;
    }
    if (misto && valPag2N >= total) {
      toast.error("Informe apenas a parte paga na 2ª forma. Ela precisa ser menor que o total para sobrar valor na 1ª forma.");
      return;
    }
    const tocarSomDeVenda = prepararSomDeVenda();
    setSalvando(true);
    try {
      const body: any = {
        idCaixa: caixaId,
        formaPagamento: forma,
        desconto: descontoN,
        observacao: observacao || null,
        itens: carrinho.map((i) => ({
          idProduto: i.produto.id,
          quantidade: i.quantidade,
        })),
      };
      if (misto) {
        body.formaPagamento2 = forma2;
        body.valorPagamento2 = valPag2N;
      }
      // Valor recebido é usado no cálculo do troco.
      if (temDinheiro) {
        body.valorRecebido = recebidoN > 0 ? recebidoN : valorEmDinheiro;
      }

      const venda = normalizarTroco(await fetchAuth<Venda>("/api/v1/vendas/registrar", {
        method: "POST",
        body: JSON.stringify(body),
      }));
      tocarSomDeVenda();
      onClose();
      onSucesso(venda);
    } catch (e: any) {
      toast.error(e.message);
    } finally {
      setSalvando(false);
    }
  };

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
        className="animate-fade-in vendas-modal"
        style={{
          background: "var(--surface-elevated)",
          border: "1px solid var(--border)",
          borderRadius: 14,
          width: "100%",
          maxWidth: 840,
          maxHeight: "93vh",
          display: "flex",
          flexDirection: "column",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            padding: "14px 20px",
            borderBottom: "1px solid var(--border)",
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
            Nova Venda — Caixa #{caixaId}
          </h2>
          <button
            onClick={onClose}
            style={{ ...btnG, padding: 6, border: "none" }}
          >
            <X size={16} />
          </button>
        </div>

        <div
          className="vendas-modal-grid"
          style={{
            display: "grid",
            gridTemplateColumns: "1fr 330px",
            flex: 1,
            minHeight: 0,
            overflow: "hidden",
          }}
        >
          {/* Produtos */}
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              borderRight: "1px solid var(--border)",
              overflow: "hidden",
            }}
          >
            <div
              style={{
                padding: "10px 16px",
                borderBottom: "1px solid var(--border)",
              }}
            >
              <div style={{ position: "relative" }}>
                <Search
                  size={12}
                  style={{
                    position: "absolute",
                    left: 9,
                    top: "50%",
                    transform: "translateY(-50%)",
                    color: "var(--foreground-subtle)",
                  }}
                />
                <input
                  style={{ ...inp, paddingLeft: 28 }}
                  placeholder="Buscar produto..."
                  value={busca}
                  onChange={(e) => setBusca(e.target.value)}
                  autoFocus
                />
              </div>
            </div>
            <div style={{ overflowY: "auto", flex: 1 }}>
              {filtrados.length === 0 ? (
                <div
                  style={{
                    padding: 28,
                    textAlign: "center",
                    color: "var(--foreground-subtle)",
                    fontSize: 13,
                  }}
                >
                  Nenhum produto disponível
                </div>
              ) : (
                filtrados.map((p) => {
                  const nc = carrinho.find((i) => i.produto.id === p.id);
                  return (
                    <div
                      key={p.id}
                      onClick={() => addItem(p)}
                      style={{
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        padding: "9px 16px",
                        cursor: "pointer",
                        borderBottom: "1px solid var(--border-subtle)",
                      }}
                      onMouseEnter={(e) =>
                        ((e.currentTarget as HTMLDivElement).style.background =
                          "var(--surface-overlay)")
                      }
                      onMouseLeave={(e) =>
                        ((e.currentTarget as HTMLDivElement).style.background =
                          "transparent")
                      }
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
                          {p.nome}
                        </p>
                        <p
                          style={{
                            fontSize: 11,
                            color: "var(--foreground-muted)",
                            margin: "1px 0 0",
                          }}
                        >
                          Estoque: {p.quantidadeEstoque}
                          {p.categoria ? ` · ${p.categoria}` : ""}
                        </p>
                      </div>
                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: 8,
                          flexShrink: 0,
                        }}
                      >
                        <span
                          style={{
                            fontSize: 13,
                            fontWeight: 600,
                            color: "var(--primary)",
                          }}
                        >
                          {fmt(p.preco)}
                        </span>
                        {nc && (
                          <span
                            style={{
                              fontSize: 11,
                              background: "var(--primary-muted)",
                              color: "var(--primary)",
                              padding: "2px 7px",
                              borderRadius: 99,
                              fontWeight: 600,
                            }}
                          >
                            {nc.quantidade}×
                          </span>
                        )}
                        <Plus size={13} color="var(--foreground-muted)" />
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>

          {/* Carrinho + pagamento */}
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              overflow: "hidden",
            }}
          >
            <div style={{ flex: 1, overflowY: "auto", padding: "10px 13px" }}>
              <p
                style={{
                  fontSize: 10,
                  fontWeight: 600,
                  color: "var(--foreground-muted)",
                  textTransform: "uppercase",
                  letterSpacing: ".07em",
                  marginBottom: 8,
                }}
              >
                Carrinho ({carrinho.length})
              </p>
              {carrinho.length === 0 ? (
                <div
                  style={{
                    textAlign: "center",
                    color: "var(--foreground-subtle)",
                    fontSize: 12,
                    padding: "16px 0",
                  }}
                >
                  Clique nos produtos
                </div>
              ) : (
                carrinho.map((item) => (
                  <div
                    key={item.produto.id}
                    style={{
                      marginBottom: 7,
                      padding: "8px 9px",
                      background: "var(--surface-overlay)",
                      borderRadius: 7,
                      border: "1px solid var(--border-subtle)",
                    }}
                  >
                    <div
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        marginBottom: 5,
                      }}
                    >
                      <p
                        style={{
                          fontSize: 12,
                          fontWeight: 500,
                          color: "var(--foreground)",
                          margin: 0,
                          flex: 1,
                          paddingRight: 6,
                        }}
                      >
                        {item.produto.nome}
                      </p>
                      <button
                        onClick={() =>
                          setCarrinho((prev) =>
                            prev.filter(
                              (i) => i.produto.id !== item.produto.id,
                            ),
                          )
                        }
                        style={{
                          background: "none",
                          border: "none",
                          cursor: "pointer",
                          color: "var(--foreground-subtle)",
                          padding: 0,
                        }}
                      >
                        <X size={11} />
                      </button>
                    </div>
                    <div
                      style={{
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                      }}
                    >
                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: 5,
                        }}
                      >
                        <button
                          onClick={() =>
                            setQtd(item.produto.id, item.quantidade - 1)
                          }
                          style={{
                            width: 20,
                            height: 20,
                            borderRadius: 5,
                            background: "var(--surface-elevated)",
                            border: "1px solid var(--border)",
                            cursor: "pointer",
                            color: "var(--foreground)",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            fontSize: 13,
                          }}
                        >
                          −
                        </button>
                        <span
                          style={{
                            fontSize: 13,
                            fontWeight: 600,
                            minWidth: 18,
                            textAlign: "center",
                          }}
                        >
                          {item.quantidade}
                        </span>
                        <button
                          onClick={() =>
                            setQtd(item.produto.id, item.quantidade + 1)
                          }
                          disabled={
                            item.quantidade >= item.produto.quantidadeEstoque
                          }
                          style={{
                            width: 20,
                            height: 20,
                            borderRadius: 5,
                            background: "var(--primary-muted)",
                            border: "1px solid rgba(16,185,129,0.3)",
                            cursor: "pointer",
                            color: "var(--primary)",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            fontSize: 13,
                            opacity:
                              item.quantidade >= item.produto.quantidadeEstoque
                                ? 0.4
                                : 1,
                          }}
                        >
                          +
                        </button>
                      </div>
                      <span style={{ fontSize: 13, fontWeight: 600 }}>
                        {fmt(item.produto.preco * item.quantidade)}
                      </span>
                    </div>
                  </div>
                ))
              )}
            </div>

            <div
              style={{
                padding: "10px 13px",
                borderTop: "1px solid var(--border)",
                display: "flex",
                flexDirection: "column",
                gap: 10,
              }}
            >
              <div
                style={{
                  background: "var(--surface-overlay)",
                  borderRadius: 8,
                  padding: "8px 11px",
                  display: "flex",
                  flexDirection: "column",
                  gap: 4,
                }}
              >
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    fontSize: 12,
                    color: "var(--foreground-muted)",
                  }}
                >
                  <span>Subtotal</span>
                  <span>{fmt(subtotal)}</span>
                </div>
                {descontoN > 0 && (
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      fontSize: 12,
                      color: "var(--destructive)",
                    }}
                  >
                    <span>Desconto</span>
                    <span>− {fmt(descontoN)}</span>
                  </div>
                )}
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    fontSize: 16,
                    fontWeight: 700,
                    color: "var(--primary)",
                    paddingTop: 4,
                    borderTop: "1px solid var(--border)",
                  }}
                >
                  <span>Total a pagar</span>
                  <span>{fmt(total)}</span>
                </div>
              </div>

              <div style={{ display: "flex", gap: 8, alignItems: "flex-end" }}>
                <div style={{ flex: 1 }}>
                  <label
                    style={{
                      fontSize: 10,
                      fontWeight: 600,
                      color: "var(--foreground-muted)",
                      textTransform: "uppercase",
                      letterSpacing: ".06em",
                      display: "block",
                      marginBottom: 4,
                    }}
                  >
                    Desconto R$
                  </label>
                  <input
                    style={inp}
                    type="number"
                    min="0"
                    step="0.01"
                    value={desconto}
                    onChange={(e) => setDesconto(e.target.value)}
                    placeholder="0,00"
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <label
                    style={{
                      fontSize: 10,
                      fontWeight: 600,
                      color: "var(--foreground-muted)",
                      textTransform: "uppercase",
                      letterSpacing: ".06em",
                      display: "block",
                      marginBottom: 4,
                    }}
                  >
                    Observação
                  </label>
                  <input
                    style={inp}
                    value={observacao}
                    onChange={(e) => setObservacao(e.target.value)}
                    placeholder="Opcional..."
                  />
                </div>
              </div>

              <div>
                <label
                  style={{
                    fontSize: 10,
                    fontWeight: 600,
                    color: "var(--foreground-muted)",
                    textTransform: "uppercase",
                    letterSpacing: ".06em",
                    display: "block",
                    marginBottom: 6,
                  }}
                >
                  {misto ? `1ª Forma — ${fmt(valPag1)}` : "Forma de Pagamento"}
                </label>
                <SeletorForma value={forma} onChange={(f) => setForma(f)} />
              </div>

              <button
                onClick={() => {
                  setMisto((v) => !v);
                  setValPag2("");
                  setRecebido("");
                }}
                style={{
                  ...btnG,
                  justifyContent: "center",
                  fontSize: 11,
                  background: misto ? "rgba(59,130,246,0.08)" : "transparent",
                  borderColor: misto ? "#3b82f6" : "var(--border)",
                  color: misto ? "#3b82f6" : "var(--foreground-muted)",
                }}
              >
                {misto ? (
                  <>
                    <X size={11} />
                    Remover 2ª forma
                  </>
                ) : (
                  <>
                    <Plus size={11} />
                    Dividir em 2 formas de pagamento
                  </>
                )}
              </button>

              {misto && (
                <div
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    gap: 8,
                    padding: "10px",
                    background: "rgba(59,130,246,0.05)",
                    border: "1px solid rgba(59,130,246,0.2)",
                    borderRadius: 9,
                  }}
                >
                  <label
                    style={{
                      fontSize: 10,
                      fontWeight: 600,
                      color: "#3b82f6",
                      textTransform: "uppercase",
                      letterSpacing: ".06em",
                      margin: 0,
                    }}
                  >
                    2ª Forma — quanto o cliente vai pagar nela?
                  </label>
                  <SeletorForma
                    value={forma2}
                    onChange={(f) => {
                      setForma2(f);
                      setRecebido("");
                    }}
                  />
                  <div>
                    <label
                      style={{
                        fontSize: 10,
                        fontWeight: 600,
                        color: "var(--foreground-muted)",
                        textTransform: "uppercase",
                        letterSpacing: ".06em",
                        display: "block",
                        marginBottom: 4,
                      }}
                    >
                      {forma2 === "DINHEIRO"
                        ? "Quanto do total será pago em dinheiro?"
                        : `Quanto será pago em ${FORMA_LABEL[forma2]}?`}
                    </label>
                    <input
                      style={inp}
                      type="number"
                      min="0"
                      max={Math.max(total - 0.01, 0)}
                      step="0.01"
                      value={valPag2}
                      onChange={(e) => setValPag2(e.target.value)}
                      placeholder="Ex: 5,00"
                      autoFocus
                    />
                    <p style={{ fontSize: 10, color: "var(--foreground-subtle)", margin: "6px 0 0", lineHeight: 1.5 }}>
                      O restante, {fmt(valPag1)}, será cobrado em {FORMA_LABEL[forma]}. Este campo define a divisão, não o valor da nota entregue.
                    </p>
                  </div>
                  {/* Split visual */}
                  {valPag2N > 0 && valPag2N < total && (
                    <div
                      style={{
                        display: "grid",
                        gridTemplateColumns: "1fr 1fr",
                        gap: 6,
                      }}
                    >
                      {[
                        { label: FORMA_LABEL[forma], valor: valPag1 },
                        { label: FORMA_LABEL[forma2], valor: valPag2N },
                      ].map(({ label, valor }) => (
                        <div
                          key={label}
                          style={{
                            background: "var(--surface-overlay)",
                            borderRadius: 7,
                            padding: "7px 10px",
                            textAlign: "center",
                          }}
                        >
                          <p
                            style={{
                              fontSize: 10,
                              color: "var(--foreground-muted)",
                              margin: "0 0 2px",
                              fontWeight: 600,
                            }}
                          >
                            {label}
                          </p>
                          <p
                            style={{
                              fontSize: 14,
                              fontWeight: 700,
                              color: "var(--foreground)",
                              margin: 0,
                            }}
                          >
                            {fmt(valor)}
                          </p>
                        </div>
                      ))}
                    </div>
                  )}
                  {valPag2N >= total && valPag2N > 0 && (
                    <p
                      style={{
                        fontSize: 12,
                        color: "var(--destructive)",
                        margin: 0,
                      }}
                    >
                      ⚠ Informe somente a parte paga nessa forma. Para dinheiro + {FORMA_LABEL[forma]}, use um valor menor que {fmt(total)}.
                    </p>
                  )}
                </div>
              )}

              {temDinheiro && (
                <div
                  style={{ display: "flex", flexDirection: "column", gap: 6 }}
                >
                  <label
                    style={{
                      fontSize: 10,
                      fontWeight: 600,
                      color: "var(--foreground-muted)",
                      textTransform: "uppercase",
                      letterSpacing: ".06em",
                    }}
                  >
                    Valor da nota entregue
                    {misto && valorEmDinheiro > 0 && (
                      <span
                        style={{
                          fontWeight: 400,
                          marginLeft: 5,
                          textTransform: "none",
                        }}
                      >
                        (parte em dinheiro: {fmt(valorEmDinheiro)})
                      </span>
                    )}
                  </label>
                  <input
                    style={inp}
                    type="number"
                    min="0"
                    step="0.01"
                    value={recebido}
                    onChange={(e) => setRecebido(e.target.value)}
                    placeholder="Opcional — use para calcular o troco"
                  />
                  <p style={{ fontSize: 10, color: "var(--foreground-subtle)", margin: 0, lineHeight: 1.5 }}>
                    Exemplo: informe R$ 10,00 na divisão e R$ 50,00 aqui para aplicar R$ 10,00 em dinheiro, devolver R$ 40,00 e cobrar o restante na outra forma.
                  </p>

                  {/* Resultado */}
                  {recebidoN > 0 &&
                    recebidoN >= valorEmDinheiro &&
                    troco !== null &&
                    troco > 0 && (
                      <div
                        style={{
                          padding: "10px 13px",
                          background: "rgba(16,185,129,0.08)",
                          border: "1px solid rgba(16,185,129,0.25)",
                          borderRadius: 8,
                          display: "flex",
                          justifyContent: "space-between",
                          alignItems: "center",
                        }}
                      >
                        <div>
                          <p
                            style={{
                              fontSize: 11,
                              color: "var(--foreground-muted)",
                              margin: "0 0 1px",
                            }}
                          >
                            Recebeu em dinheiro
                          </p>
                          <p
                            style={{
                              fontSize: 13,
                              fontWeight: 600,
                              color: "var(--foreground)",
                              margin: 0,
                            }}
                          >
                            {fmt(recebidoN)}
                          </p>
                        </div>
                        <div style={{ textAlign: "right" }}>
                          <p
                            style={{
                              fontSize: 11,
                              color: "var(--foreground-muted)",
                              margin: "0 0 1px",
                            }}
                          >
                            Devolver de troco
                          </p>
                          <p
                            style={{
                              fontSize: 18,
                              fontWeight: 800,
                              color: "var(--primary)",
                              margin: 0,
                            }}
                          >
                            {fmt(troco)}
                          </p>
                        </div>
                      </div>
                    )}
                  {recebidoN > 0 && recebidoN === valorEmDinheiro && (
                    <div
                      style={{
                        padding: "8px 12px",
                        background: "rgba(16,185,129,0.08)",
                        border: "1px solid rgba(16,185,129,0.25)",
                        borderRadius: 8,
                      }}
                    >
                      <span
                        style={{
                          fontSize: 13,
                          fontWeight: 600,
                          color: "var(--primary)",
                        }}
                      >
                        ✓ Valor exato — sem troco
                      </span>
                    </div>
                  )}
                  {falta !== null && falta > 0 && (
                    <div
                      style={{
                        padding: "10px 13px",
                        background: "rgba(239,68,68,0.08)",
                        border: "1px solid rgba(239,68,68,0.25)",
                        borderRadius: 8,
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                      }}
                    >
                      <div>
                        <p
                          style={{
                            fontSize: 11,
                            color: "var(--foreground-muted)",
                            margin: "0 0 1px",
                          }}
                        >
                          Recebeu em dinheiro
                        </p>
                        <p
                          style={{
                            fontSize: 13,
                            fontWeight: 600,
                            color: "var(--foreground)",
                            margin: 0,
                          }}
                        >
                          {fmt(recebidoN)}
                        </p>
                      </div>
                      <div style={{ textAlign: "right" }}>
                        <p
                          style={{
                            fontSize: 11,
                            color: "var(--foreground-muted)",
                            margin: "0 0 1px",
                          }}
                        >
                          Ainda falta
                        </p>
                        <p
                          style={{
                            fontSize: 18,
                            fontWeight: 800,
                            color: "var(--destructive)",
                            margin: 0,
                          }}
                        >
                          {fmt(falta)}
                        </p>
                      </div>
                    </div>
                  )}
                </div>
              )}

              <button
                onClick={registrar}
                disabled={salvando || !carrinho.length}
                style={{
                  ...btnP,
                  justifyContent: "center",
                  padding: "11px 0",
                  opacity: salvando || !carrinho.length ? 0.6 : 1,
                }}
              >
                {salvando ? (
                  "Registrando..."
                ) : (
                  <>
                    <Check size={14} />
                    Confirmar · {fmt(total)}
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function DetalheVenda({
  venda,
  nomeEmpresa,
  documentoEmpresa,
  onClose,
  onAtualizado,
}: {
  venda: Venda;
  nomeEmpresa: string;
  documentoEmpresa?: string | null;
  onClose: () => void;
  onAtualizado: (v: Venda) => void;
}) {
  const [editandoObs, setEditandoObs] = useState(false);
  const [novaObs, setNovaObs] = useState(venda.observacao ?? "");
  const [cancelando, setCancelando] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [salvando, setSalvando] = useState(false);

  const misto = venda.formaPagamento2 && venda.valorPagamento2;

  const salvarObs = async () => {
    setSalvando(true);
    try {
      const updated = await fetchAuth<Venda>(
        `/api/v1/vendas/${venda.id}/observacao`,
        { method: "PATCH", body: JSON.stringify({ observacao: novaObs }) },
      );
      onAtualizado(updated);
      setEditandoObs(false);
      toast.success("Observação salva!");
    } catch (e: any) {
      toast.error(e.message);
    } finally {
      setSalvando(false);
    }
  };

  const confirmarCancelamento = async () => {
    setSalvando(true);
    try {
      const updated = await fetchAuth<Venda>(
        `/api/v1/vendas/${venda.id}/cancelar`,
        { method: "POST", body: JSON.stringify({ motivo }) },
      );
      onAtualizado(updated);
      setCancelando(false);
      toast.success("Venda cancelada. Estoque devolvido.");
    } catch (e: any) {
      toast.error(e.message);
    } finally {
      setSalvando(false);
    }
  };

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
          maxWidth: 440,
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: 16,
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <h2
              style={{
                fontSize: 15,
                fontWeight: 700,
                color: "var(--foreground)",
                margin: 0,
              }}
            >
              Venda #{venda.id}
            </h2>
            {venda.cancelada && (
              <span
                style={{
                  fontSize: 11,
                  padding: "2px 8px",
                  background: "rgba(239,68,68,0.1)",
                  color: "var(--destructive)",
                  borderRadius: 99,
                  fontWeight: 600,
                }}
              >
                CANCELADA
              </span>
            )}
          </div>
          <button
            onClick={onClose}
            style={{ ...btnG, padding: 5, border: "none" }}
          >
            <X size={16} />
          </button>
        </div>

        <p
          style={{
            fontSize: 11,
            color: "var(--foreground-muted)",
            marginBottom: 14,
          }}
        >
          {fmtData(venda.dataVenda)}
        </p>

        {/* Itens */}
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: 6,
            marginBottom: 14,
          }}
        >
          {venda.itens.map((item, i) => (
            <div
              key={i}
              style={{
                display: "flex",
                justifyContent: "space-between",
                fontSize: 13,
              }}
            >
              <span style={{ color: "var(--foreground)" }}>
                {item.nomeProduto}{" "}
                <span style={{ color: "var(--foreground-muted)" }}>
                  × {item.quantidade}
                </span>
              </span>
              <span style={{ fontWeight: 500 }}>{fmt(item.subtotal)}</span>
            </div>
          ))}
        </div>

        {/* Totais */}
        <div
          style={{
            background: "var(--surface-overlay)",
            borderRadius: 8,
            padding: "10px 12px",
            display: "flex",
            flexDirection: "column",
            gap: 5,
            marginBottom: 14,
          }}
        >
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              fontSize: 12,
              color: "var(--foreground-muted)",
            }}
          >
            <span>Subtotal</span>
            <span>{fmt(venda.valorTotal)}</span>
          </div>
          {venda.desconto > 0 && (
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                fontSize: 12,
                color: "var(--destructive)",
              }}
            >
              <span>Desconto</span>
              <span>− {fmt(venda.desconto)}</span>
            </div>
          )}
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              fontSize: 14,
              fontWeight: 700,
              color: "var(--primary)",
              paddingTop: 5,
              borderTop: "1px solid var(--border)",
            }}
          >
            <span>Total</span>
            <span>{fmt(venda.valorFinal)}</span>
          </div>
        </div>

        {/* Pagamento */}
        <div
          style={{
            display: "flex",
            gap: 6,
            flexWrap: "wrap",
            marginBottom: 10,
          }}
        >
          <span
            style={{
              fontSize: 12,
              padding: "3px 9px",
              background: "var(--primary-muted)",
              color: "var(--primary)",
              borderRadius: 99,
              fontWeight: 500,
            }}
          >
            {FORMA_LABEL[venda.formaPagamento] ?? venda.formaPagamento}
            {misto &&
              `: ${fmt(venda.valorFinal - (venda.valorPagamento2 ?? 0))}`}
          </span>
          {misto && (
            <span
              style={{
                fontSize: 12,
                padding: "3px 9px",
                background: "var(--secondary-muted)",
                color: "var(--secondary)",
                borderRadius: 99,
                fontWeight: 500,
              }}
            >
              {FORMA_LABEL[venda.formaPagamento2!] ?? venda.formaPagamento2}:{" "}
              {fmt(venda.valorPagamento2)}
            </span>
          )}
        </div>

        {/* Troco */}
        {venda.troco != null && venda.troco > 0 && (
          <p
            style={{
              fontSize: 12,
              color: "var(--foreground-muted)",
              marginBottom: 8,
            }}
          >
            Recebido: {fmt(venda.valorRecebido)} ·{" "}
            <strong style={{ color: "var(--primary)" }}>
              Troco: {fmt(venda.troco)}
            </strong>
          </p>
        )}

        {venda.nomeCliente && (
          <p
            style={{
              fontSize: 12,
              color: "var(--foreground-muted)",
              marginBottom: 8,
            }}
          >
            Cliente: <strong>{venda.nomeCliente}</strong>
          </p>
        )}

        {/* Motivo cancelamento */}
        {venda.cancelada && venda.motivoCancelamento && (
          <div
            style={{
              padding: "8px 10px",
              background: "rgba(239,68,68,0.06)",
              border: "1px solid rgba(239,68,68,0.2)",
              borderRadius: 7,
              marginBottom: 10,
              fontSize: 12,
              color: "var(--destructive)",
            }}
          >
            Motivo: {venda.motivoCancelamento}
          </div>
        )}

        {/* Observação */}
        {!cancelando && (
          <div style={{ marginBottom: 14 }}>
            {editandoObs ? (
              <div style={{ display: "flex", gap: 6 }}>
                <input
                  style={{
                    ...{ ...btnG, padding: "7px 10px" },
                    flex: 1,
                    background: "var(--surface-overlay)",
                    color: "var(--foreground)",
                  }}
                  value={novaObs}
                  onChange={(e) => setNovaObs(e.target.value)}
                  placeholder="Observação..."
                  autoFocus
                />
                <button
                  onClick={salvarObs}
                  disabled={salvando}
                  style={{ ...btnP, padding: "7px 12px" }}
                >
                  <Check size={13} />
                </button>
                <button
                  onClick={() => setEditandoObs(false)}
                  style={{ ...btnG, padding: "7px 10px" }}
                >
                  <X size={13} />
                </button>
              </div>
            ) : (
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <p
                  style={{
                    fontSize: 12,
                    color: "var(--foreground-muted)",
                    margin: 0,
                    flex: 1,
                  }}
                >
                  {venda.observacao || (
                    <span style={{ fontStyle: "italic" }}>Sem observação</span>
                  )}
                </p>
                {!venda.cancelada && (
                  <button
                    onClick={() => setEditandoObs(true)}
                    style={{ ...btnG, padding: "4px 8px" }}
                  >
                    <Edit2 size={12} />
                  </button>
                )}
              </div>
            )}
          </div>
        )}

        <button
          onClick={() => gerarCupom(venda, nomeEmpresa, documentoEmpresa)}
          style={{
            ...btnG,
            width: "100%",
            justifyContent: "center",
            borderColor: "rgba(59,130,246,0.3)",
            color: "#3b82f6",
          }}
        >
          <Receipt size={13} /> Imprimir nota da venda
        </button>

        {/* Cancelar */}
        {!venda.cancelada &&
          !editandoObs &&
          (cancelando ? (
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                gap: 8,
                padding: "12px",
                background: "rgba(239,68,68,0.06)",
                border: "1px solid rgba(239,68,68,0.2)",
                borderRadius: 9,
              }}
            >
              <p
                style={{
                  fontSize: 12,
                  color: "var(--destructive)",
                  fontWeight: 600,
                  margin: 0,
                }}
              >
                Confirmar cancelamento?
              </p>
              <p
                style={{
                  fontSize: 11,
                  color: "var(--foreground-muted)",
                  margin: 0,
                }}
              >
                O estoque será devolvido automaticamente.
              </p>
              <input
                style={{
                  ...btnG,
                  padding: "7px 10px",
                  background: "var(--surface-overlay)",
                  color: "var(--foreground)",
                  width: "100%",
                }}
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                placeholder="Motivo (opcional)..."
              />
              <div style={{ display: "flex", gap: 7 }}>
                <button
                  onClick={() => setCancelando(false)}
                  style={{ ...btnG, flex: 1, justifyContent: "center" }}
                >
                  Voltar
                </button>
                <button
                  onClick={confirmarCancelamento}
                  disabled={salvando}
                  style={{
                    flex: 2,
                    padding: "8px 0",
                    background: "var(--destructive)",
                    color: "#fff",
                    border: "none",
                    borderRadius: 8,
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: "pointer",
                    opacity: salvando ? 0.7 : 1,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: 6,
                  }}
                >
                  <Ban size={13} /> Confirmar
                </button>
              </div>
            </div>
          ) : (
            <div style={{ display: "flex", gap: 8 }}>
              <button
                onClick={() => setCancelando(true)}
                style={{
                  width: "100%",
                  ...btnG,
                  justifyContent: "center",
                  borderColor: "rgba(239,68,68,0.3)",
                  color: "var(--destructive)",
                }}
              >
                <Trash2 size={13} /> Cancelar
              </button>
            </div>
          ))}
      </div>
    </div>
  );
}

function CaixaCard({
  caixa,
  empresaId,
  nomeEmpresa,
  documentoEmpresa,
  onNovaVenda,
  refreshKey,
}: {
  caixa: CaixaInfo;
  empresaId: number;
  nomeEmpresa: string;
  documentoEmpresa?: string | null;
  onNovaVenda?: () => void;
  refreshKey: number;
}) {
  const [vendas, setVendas] = useState<Venda[]>([]);
  const [loading, setLoading] = useState(false);
  const [aberto, setAberto] = useState(caixa.aberto);
  const [detalhe, setDetalhe] = useState<Venda | null>(null);
  const [filtro, setFiltro] = useState("");

  useEffect(() => {
    setLoading(true);
    fetchAuth<Venda[]>(`/api/v1/vendas/caixa/${caixa.id}`)
      .then((resultado) => setVendas(resultado.map(normalizarTroco)))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [caixa.id, refreshKey]);

  const resumoPagamento = useMemo(() => {
    const map: Record<string, number> = {};
    vendas
      .filter((v) => !v.cancelada)
      .forEach((v) => {
        const k = FORMA_LABEL[v.formaPagamento] ?? v.formaPagamento;
        const segundoPagamento = v.formaPagamento2
          ? Math.max(0, Number(v.valorPagamento2) || 0)
          : 0;
        map[k] = (map[k] ?? 0) + Math.max(0, v.valorFinal - segundoPagamento);
        if (v.formaPagamento2 && segundoPagamento > 0) {
          const k2 = FORMA_LABEL[v.formaPagamento2] ?? v.formaPagamento2;
          map[k2] = (map[k2] ?? 0) + segundoPagamento;
        }
      });
    return Object.entries(map).sort((a, b) => b[1] - a[1]);
  }, [vendas]);

  const vendasFiltradas = useMemo(
    () =>
      vendas
        .filter(
          (v) =>
            String(v.id).includes(filtro) ||
            (FORMA_LABEL[v.formaPagamento] ?? v.formaPagamento)
              .toLowerCase()
              .includes(filtro.toLowerCase()),
        )
        .sort((a, b) => {
          const db = parseDate(b.dataVenda)?.getTime() ?? 0;
          const da = parseDate(a.dataVenda)?.getTime() ?? 0;
          return db - da;
        }),
    [vendas, filtro],
  );

  const totalCaixa = vendas
    .filter((v) => !v.cancelada)
    .reduce((s, v) => s + (v.valorFinal ?? 0), 0);
  const canceladas = vendas.filter((v) => v.cancelada).length;

  const handleAtualizado = (updated: Venda) => {
    setVendas((prev) => prev.map((v) => (v.id === updated.id ? updated : v)));
    setDetalhe(updated);
  };

  return (
    <div
      className="vendas-caixa-card"
      style={{
        background: "var(--surface-elevated)",
        border: `1px solid ${caixa.aberto ? "rgba(16,185,129,0.3)" : "var(--border)"}`,
        borderRadius: 14,
        overflow: "hidden",
      }}
    >
      <div
        className="vendas-caixa-header"
        style={{
          padding: "13px 16px",
          borderBottom: "1px solid var(--border)",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          cursor: "pointer",
        }}
        onClick={() => setAberto((v) => !v)}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 11 }}>
          <div
            style={{
              width: 34,
              height: 34,
              borderRadius: 8,
              background: caixa.aberto
                ? "var(--primary-muted)"
                : "var(--surface-overlay)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <Receipt
              size={15}
              color={
                caixa.aberto ? "var(--primary)" : "var(--foreground-muted)"
              }
            />
          </div>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <span
                style={{
                  fontSize: 14,
                  fontWeight: 600,
                  color: "var(--foreground)",
                }}
              >
                Caixa #{caixa.id}
              </span>
              <span
                style={{
                  fontSize: 11,
                  padding: "2px 7px",
                  borderRadius: 99,
                  fontWeight: 600,
                  background: caixa.aberto
                    ? "var(--primary-muted)"
                    : "var(--surface-overlay)",
                  color: caixa.aberto
                    ? "var(--primary)"
                    : "var(--foreground-muted)",
                }}
              >
                {caixa.aberto ? "● ABERTO" : "FECHADO"}
              </span>
              {canceladas > 0 && (
                <span
                  style={{
                    fontSize: 11,
                    padding: "2px 7px",
                    borderRadius: 99,
                    background: "rgba(239,68,68,0.1)",
                    color: "var(--destructive)",
                    fontWeight: 500,
                  }}
                >
                  {canceladas} cancelada(s)
                </span>
              )}
            </div>
            <p
              style={{
                fontSize: 11,
                color: "var(--foreground-muted)",
                margin: "2px 0 0",
              }}
            >
              Aberto em {fmtData(caixa.dataAbertura)}
              {caixa.dataFechamento &&
                ` · Fechado em ${fmtData(caixa.dataFechamento)}`}
            </p>
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
          <div style={{ textAlign: "right" }}>
            <p
              style={{
                fontSize: 16,
                fontWeight: 700,
                color: "var(--primary)",
                margin: 0,
              }}
            >
              {fmt(totalCaixa)}
            </p>
            <p
              style={{
                fontSize: 11,
                color: "var(--foreground-muted)",
                margin: 0,
              }}
            >
              {vendas.filter((v) => !v.cancelada).length} venda(s)
            </p>
          </div>
          {caixa.aberto && onNovaVenda && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                onNovaVenda();
              }}
              style={{ ...btnP, padding: "6px 11px", fontSize: 12 }}
            >
              <Plus size={12} /> Nova
            </button>
          )}
          {aberto ? (
            <ChevronUp size={15} color="var(--foreground-muted)" />
          ) : (
            <ChevronDown size={15} color="var(--foreground-muted)" />
          )}
        </div>
      </div>

      {aberto && (
        <div
          className="vendas-caixa-body"
          style={{
            padding: "13px 16px",
            display: "flex",
            flexDirection: "column",
            gap: 14,
          }}
        >
          <div
            className="vendas-resumo-grid"
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(130px, 1fr))",
              gap: 9,
            }}
          >
            {[
              { label: "Saldo Inicial", value: fmt(caixa.valorInicial) },
              { label: "Total Vendas", value: fmt(totalCaixa), destaque: true },
              {
                label: "Ticket Médio",
                value:
                  vendas.filter((v) => !v.cancelada).length > 0
                    ? fmt(
                        totalCaixa / vendas.filter((v) => !v.cancelada).length,
                      )
                    : "—",
              },
              ...(caixa.dataFechamento
                ? [{ label: "Saldo Final", value: fmt(caixa.valorFinal) }]
                : []),
            ].map((c, i) => (
              <div
                key={i}
                style={{
                  background: "var(--surface-overlay)",
                  borderRadius: 8,
                  padding: "9px 11px",
                }}
              >
                <p
                  style={{
                    fontSize: 10,
                    fontWeight: 600,
                    color: "var(--foreground-muted)",
                    textTransform: "uppercase",
                    letterSpacing: ".06em",
                    margin: "0 0 3px",
                  }}
                >
                  {c.label}
                </p>
                <p
                  style={{
                    fontSize: 15,
                    fontWeight: 700,
                    color: (c as any).destaque
                      ? "var(--primary)"
                      : "var(--foreground)",
                    margin: 0,
                  }}
                >
                  {c.value}
                </p>
              </div>
            ))}
          </div>

          {resumoPagamento.length > 0 && (
            <div>
              <p
                style={{
                  fontSize: 10,
                  fontWeight: 600,
                  color: "var(--foreground-muted)",
                  textTransform: "uppercase",
                  letterSpacing: ".07em",
                  marginBottom: 7,
                  display: "flex",
                  alignItems: "center",
                  gap: 5,
                }}
              >
                <BarChart3 size={11} /> Formas de Pagamento
              </p>
              <div style={{ display: "flex", gap: 7, flexWrap: "wrap" }}>
                {resumoPagamento.map(([forma, valor]) => (
                  <div
                    key={forma}
                    style={{
                      background: "var(--surface-overlay)",
                      border: "1px solid var(--border)",
                      borderRadius: 8,
                      padding: "7px 11px",
                    }}
                  >
                    <span
                      style={{
                        fontSize: 11,
                        color: "var(--foreground-muted)",
                        fontWeight: 500,
                        display: "block",
                      }}
                    >
                      {forma}
                    </span>
                    <span
                      style={{
                        fontSize: 14,
                        fontWeight: 700,
                        color: "var(--primary)",
                      }}
                    >
                      {fmt(valor)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {vendas.length > 0 && (
            <div style={{ position: "relative", maxWidth: 280 }}>
              <Search
                size={11}
                style={{
                  position: "absolute",
                  left: 8,
                  top: "50%",
                  transform: "translateY(-50%)",
                  color: "var(--foreground-subtle)",
                }}
              />
              <input
                style={{
                  ...inp,
                  paddingLeft: 26,
                  padding: "6px 9px 6px 26px",
                  fontSize: 12,
                }}
                placeholder="Filtrar vendas..."
                value={filtro}
                onChange={(e) => setFiltro(e.target.value)}
              />
            </div>
          )}

          {loading ? (
            <div
              style={{
                textAlign: "center",
                color: "var(--foreground-muted)",
                fontSize: 13,
                padding: 14,
              }}
            >
              Carregando...
            </div>
          ) : vendasFiltradas.length === 0 ? (
            <div
              style={{
                textAlign: "center",
                color: "var(--foreground-subtle)",
                fontSize: 13,
                padding: 14,
              }}
            >
              {vendas.length === 0
                ? "Nenhuma venda neste caixa."
                : "Sem resultados."}
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
              {vendasFiltradas.map((v) => (
                <div
                  className="vendas-list-row"
                  key={v.id}
                  onClick={() => setDetalhe(v)}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    padding: "9px 11px",
                    background: v.cancelada
                      ? "rgba(239,68,68,0.04)"
                      : "var(--surface-overlay)",
                    borderRadius: 8,
                    cursor: "pointer",
                    border: `1px solid ${v.cancelada ? "rgba(239,68,68,0.15)" : "transparent"}`,
                    transition: "all .1s",
                    opacity: v.cancelada ? 0.7 : 1,
                  }}
                  onMouseEnter={(e) => {
                    if (!v.cancelada)
                      (e.currentTarget as HTMLDivElement).style.borderColor =
                        "var(--border)";
                  }}
                  onMouseLeave={(e) => {
                    if (!v.cancelada)
                      (e.currentTarget as HTMLDivElement).style.borderColor =
                        "transparent";
                  }}
                >
                  <div
                    style={{ display: "flex", alignItems: "center", gap: 10 }}
                  >
                    <span
                      style={{
                        fontSize: 12,
                        fontWeight: 600,
                        color: "var(--foreground-muted)",
                      }}
                    >
                      #{v.id}
                    </span>
                    <div>
                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: 6,
                        }}
                      >
                        <span
                          style={{
                            fontSize: 11,
                            padding: "1px 6px",
                            background: v.cancelada
                              ? "rgba(239,68,68,0.1)"
                              : "var(--primary-muted)",
                            color: v.cancelada
                              ? "var(--destructive)"
                              : "var(--primary)",
                            borderRadius: 99,
                            fontWeight: 500,
                          }}
                        >
                          {v.cancelada
                            ? "Cancelada"
                            : (FORMA_LABEL[v.formaPagamento] ??
                              v.formaPagamento)}
                        </span>
                        {v.formaPagamento2 && !v.cancelada && (
                          <span
                            style={{
                              fontSize: 11,
                              padding: "1px 6px",
                              background: "var(--secondary-muted)",
                              color: "var(--secondary)",
                              borderRadius: 99,
                              fontWeight: 500,
                            }}
                          >
                            +{" "}
                            {FORMA_LABEL[v.formaPagamento2] ??
                              v.formaPagamento2}
                          </span>
                        )}
                        {v.desconto > 0 && (
                          <span
                            style={{
                              fontSize: 11,
                              color: "var(--destructive)",
                            }}
                          >
                            − {fmt(v.desconto)}
                          </span>
                        )}
                      </div>
                      <p
                        style={{
                          fontSize: 11,
                          color: "var(--foreground-subtle)",
                          margin: "2px 0 0",
                        }}
                      >
                        {fmtData(v.dataVenda)} · {v.itens?.length ?? 0} item(s)
                      </p>
                    </div>
                  </div>
                  <div
                    style={{ display: "flex", alignItems: "center", gap: 7 }}
                  >
                    <span
                      style={{
                        fontSize: 13,
                        fontWeight: 700,
                        color: v.cancelada
                          ? "var(--foreground-subtle)"
                          : "var(--primary)",
                        textDecoration: v.cancelada ? "line-through" : "none",
                      }}
                    >
                      {fmt(v.valorFinal)}
                    </span>
                    <ChevronRight size={13} color="var(--foreground-subtle)" />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {detalhe && (
        <DetalheVenda
          venda={detalhe}
          nomeEmpresa={nomeEmpresa}
          documentoEmpresa={documentoEmpresa}
          onClose={() => setDetalhe(null)}
          onAtualizado={handleAtualizado}
        />
      )}
    </div>
  );
}

export default function Vendas() {
  const { empresaAtiva, caixaAtivo } = useEmpresa();
  const [caixas, setCaixas] = useState<CaixaInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalNova, setModalNova] = useState(false);
  const [vendaSucesso, setVendaSucesso] = useState<Venda | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const carregar = async () => {
    if (!empresaAtiva) return;
    setLoading(true);
    try {
      setCaixas(
        await fetchAuth<CaixaInfo[]>(
          `/api/v1/caixas/empresa/${empresaAtiva.id}`,
        ),
      );
    } catch {
      toast.error("Erro ao carregar caixas");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    carregar();
  }, [empresaAtiva?.id]);

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
          Selecione uma empresa para ver as vendas.
        </p>
      </div>
    );

  return (
    <div
      className="vendas-page"
      style={{ padding: 28, display: "flex", flexDirection: "column", gap: 20 }}
    >
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
            Vendas
          </h2>
          <p
            style={{
              fontSize: 13,
              color: "var(--foreground-muted)",
              marginTop: 3,
            }}
          >
            {empresaAtiva.nomeFantasia} · {caixas.length} caixa(s)
          </p>
        </div>
        {caixaAtivo ? (
          <button style={btnP} onClick={() => setModalNova(true)}>
            <Plus size={15} /> Nova Venda
          </button>
        ) : (
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: 8,
              padding: "8px 14px",
              background: "var(--warning-muted)",
              border: "1px solid rgba(245,158,11,0.2)",
              borderRadius: 8,
              fontSize: 13,
              color: "var(--warning)",
            }}
          >
            <AlertCircle size={14} /> Abra o caixa para registrar vendas
          </div>
        )}
      </div>

      {loading ? (
        <div
          style={{
            textAlign: "center",
            color: "var(--foreground-muted)",
            fontSize: 13,
            padding: 32,
          }}
        >
          Carregando...
        </div>
      ) : caixas.length === 0 ? (
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
          <Receipt size={40} />
          <p style={{ fontSize: 14 }}>Nenhum caixa encontrado.</p>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {caixas.map((c) => (
            <CaixaCard
              key={c.id}
              caixa={c}
              empresaId={empresaAtiva.id}
              nomeEmpresa={empresaAtiva.nomeFantasia}
              documentoEmpresa={empresaAtiva.cnpj || empresaAtiva.cpf}
              refreshKey={refreshKey}
              onNovaVenda={
                caixaAtivo?.id === c.id ? () => setModalNova(true) : undefined
              }
            />
          ))}
        </div>
      )}

      {modalNova && caixaAtivo && (
        <ModalNovaVenda
          caixaId={caixaAtivo.id}
          empresaId={empresaAtiva.id}
          onClose={() => setModalNova(false)}
          onSucesso={(venda) => {
            carregar();
            setRefreshKey((value) => value + 1);
            setVendaSucesso(venda);
          }}
        />
      )}
      {vendaSucesso && (
        <TelaVendaSucesso
          venda={vendaSucesso}
          nomeEmpresa={empresaAtiva.nomeFantasia}
          documentoEmpresa={empresaAtiva.cnpj || empresaAtiva.cpf}
          onFechar={() => setVendaSucesso(null)}
        />
      )}
    </div>
  );
}
