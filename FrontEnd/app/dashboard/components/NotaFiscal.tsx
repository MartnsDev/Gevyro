"use client";

import { useState, useEffect, useCallback, ReactNode } from "react";
import Link from "next/link";
import { toast } from "sonner";
import {
  FileText, Upload, Download, Plus, Search, CheckCircle,
  XCircle, ShieldCheck, FileArchive, Send, Trash2, Loader2,
  AlertTriangle, Receipt, Briefcase, Eye, X, RefreshCw, 
  Building2, ChevronLeft, ChevronRight, TrendingUp, Store
} from "lucide-react";
import { useEmpresa } from "../context/Empresacontext";
import { fetchAuth } from "@/lib/api-v2";

const getApiBase = () => {
  const envUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  const cleanUrl = envUrl.replace(/\/api\/v1\/?$/, '').replace(/\/v1\/?$/, '').replace(/\/$/, '');
  return `${cleanUrl}/api/nota-fiscal`;
};

const API_BASE = getApiBase();

const theme = {
  bgBase: "transparent",
  bgCard: "var(--surface-elevated)",
  bgInput: "var(--surface-overlay)",
  border: "var(--border)",
  textMain: "var(--foreground)",
  textMuted: "var(--foreground-muted)",
  primary: "var(--primary)",
  primaryHover: "var(--primary-hover)",
  primaryAlpha: "var(--primary-muted)",
  danger: "#ef4444",
  dangerAlpha: "rgba(239, 68, 68, 0.1)",
  warning: "#f59e0b",
  warningAlpha: "rgba(245, 158, 11, 0.1)",
};

const inpStyle = { padding: "10px 14px", borderRadius: 8, borderWidth: 1, borderStyle: "solid", borderColor: theme.border, background: theme.bgInput, color: theme.textMain, width: "100%", fontSize: 13, outline: "none", transition: "border-color 0.2s" };
const lblStyle = { display: "block", fontSize: "11px", fontWeight: 600, color: theme.textMuted, marginBottom: "8px", letterSpacing: "0.5px", textTransform: "uppercase" as const };
const btnStyle = { background: theme.bgInput, borderWidth: 1, borderStyle: "solid", borderColor: theme.border, padding: "8px 14px", borderRadius: 8, cursor: "pointer", color: theme.textMain, display: "flex", alignItems: "center", gap: 6, fontSize: 12, fontWeight: 700, transition: "all 0.2s" };

// 3. TIPOS E COMPONENTES AUXILIARES
type AbaGeral = "historico" | "emitir" | "certificado" | "contador";
type TipoNota = "NFE" | "NFCE" | "NFSE";

const fmt = (v?: number | null) => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(v ?? 0);
const fmtDate = (s?: string) => s ? new Date(s).toLocaleString("pt-BR") : "—";
const fmtDocumento = (value?: string | null) => {
  const digits = value?.replace(/\D/g, "") ?? "";
  if (digits.length === 14) return digits.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
  if (digits.length === 11) return digits.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, "$1.$2.$3-$4");
  return "CPF/CNPJ não cadastrado";
};

function ClientOnly({ children }: { children: ReactNode }) {
  const [ok, setOk] = useState(false);
  useEffect(() => setOk(true), []);
  return ok ? <>{children}</> : null;
}

function SectionTitle({ children }: { children: ReactNode }) {
  return <p style={{ fontSize: 11, fontWeight: 700, color: theme.textMuted, marginBottom: 12, textTransform: "uppercase", letterSpacing: ".08em" }}>{children}</p>;
}

function Card({ title, subtitle, children, style }: { title?: string; subtitle?: string; children: ReactNode; style?: React.CSSProperties }) {
  return (
    <div style={{ background: theme.bgCard, borderWidth: 1, borderStyle: "solid", borderColor: theme.border, borderRadius: 14, padding: "20px 22px", ...style }}>
      {title && (
        <div style={{ marginBottom: 18 }}>
          <p style={{ fontSize: 11, fontWeight: 700, color: theme.textMuted, textTransform: "uppercase", letterSpacing: ".08em", margin: 0 }}>{title}</p>
          {subtitle && <p style={{ fontSize: 12, color: theme.textMuted, margin: "2px 0 0" }}>{subtitle}</p>}
        </div>
      )}
      {children}
    </div>
  );
}

function StyledInput({ label, ...props }: { label: string } & React.InputHTMLAttributes<HTMLInputElement | HTMLSelectElement>) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
      <label style={lblStyle}>{label}</label>
      <input {...props as any} style={{ ...inpStyle, ...props.style }} />
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const isOk = status === "AUTORIZADA";
  const isErr = status === "REJEITADA" || status === "CANCELADA";
  const isWarn = status === "CONTINGENCIA";
  
  const bg = isOk ? theme.primaryAlpha : isErr ? theme.dangerAlpha : isWarn ? theme.warningAlpha : "var(--surface-overlay)";
  const color = isOk ? theme.primary : isErr ? theme.danger : isWarn ? theme.warning : "var(--primary)";
  const border = isOk ? "rgba(16,185,129,0.3)" : isErr ? "rgba(239,68,68,0.3)" : isWarn ? "rgba(245,158,11,0.3)" : theme.border;

  return (
    <span style={{ display: "inline-flex", alignItems: "center", padding: "4px 10px", borderRadius: 99, fontSize: 11, fontWeight: 700, color, background: bg, borderWidth: 1, borderStyle: "solid", borderColor: border }}>
      {status}
    </span>
  );
}

// 4. COMPONENTE PRINCIPAL
export default function NotaFiscalPage() {
  const { empresaAtiva } = useEmpresa();
  const EMPRESA_ID = empresaAtiva?.id;

  const [aba, setAba] = useState<AbaGeral>("historico");
  const [loading, setLoading] = useState(false);
  const [emitindo, setEmitindo] = useState(false);
  const [salvandoCert, setSalvandoCert] = useState(false);

  // NOVO ESTADO: MODAL DE ERRO ABSOLUTO
  const [erroApi, setErroApi] = useState<string | null>(null);

  const [notas, setNotas] = useState<any[]>([]);
  const [paginacao, setPaginao] = useState<any>(null);
  const [paginaAtual, setPaginaAtual] = useState(1);
  const [estatisticas, setEstatisticas] = useState({ totalAutorizadas: 0, totalRejeitadas: 0, totalCanceladas: 0, valorTotalMes: 0 });
  const [filtroStatus, setFiltroStatus] = useState("TODOS");
  const [filtroBusca, setFiltroBusca] = useState("");
  const [notaSelecionada, setNotaSelecionada] = useState<any>(null);

  const [tipoNota, setTipoNota] = useState<TipoNota>("NFE");
  const [naturezaOp, setNaturezaOp] = useState("Venda de Mercadoria");
  const [formaPagamento, setFormaPagamento] = useState("PIX");
  const [clienteDoc, setClienteDoc] = useState("");
  const [clienteNome, setClienteNome] = useState("");
  const [infoAdicionais, setInfoAdicionais] = useState("");
  const [itens, setItens] = useState<any[]>([]);
  const [buscandoCnpj, setBuscandoCnpj] = useState(false);

  const [arquivoCert, setArquivoCert] = useState<File | null>(null);
  const [senhaCert, setSenhaCert] = useState("");
  const [certInfo, setCertInfo] = useState<any>(null);
  const [periodoExport, setPeriodoExport] = useState(new Date().toISOString().slice(0, 7));
  const [tipoSped, setTipoSped] = useState("EFD_ICMS_IPI");

  // API SEGURA
  const fetchSeguro = async (url: string, options: RequestInit = {}) => {
    const path=url.startsWith(API_BASE)?url.slice(API_BASE.length):url;
    const res = await fetchAuth(`/api/nota-fiscal${path}`, options);
    
    const text = await res.text();
    let json = null;
    try { if (text) json = JSON.parse(text); } catch (e) {}

    // SE A REQUISIÇÃO DEU ERRO:
    if (!res.ok) {
        // Se o Java devolveu a nossa classe ApiResponse com a mensagem de erro
        if (json && json.mensagem) {
            throw new Error(json.mensagem);
        }
        
        // Se o Spring Boot Validations pegou um erro (ex: @NotNull, @Positive)
        if (json && json.errors) {
             const validationErrors = json.errors.map((err: any) => `${err.field}: ${err.defaultMessage}`).join(" | ");
             throw new Error(`Erro de Validação: ${validationErrors}`);
        }

        // Se for um erro genérico do Spring
        if (json && json.message) {
             throw new Error(`Erro no Servidor: ${json.message}`);
        }

        if (res.status === 400) throw new Error("Requisição inválida. Verifique os campos enviados.");
        if (res.status === 401) throw new Error("Sessão expirada. Faça login novamente.");
        throw new Error(`Erro desconhecido no servidor (Status: ${res.status})`);
    }
    
    return json || { sucesso: true };
  };

  const fazerDownloadSeguro = async (url: string, filename: string) => {
    toast.loading("Iniciando download...", { id: "dl_toast" });
    try {
      const path=url.startsWith(API_BASE)?url.slice(API_BASE.length):url;
      const res = await fetchAuth(`/api/nota-fiscal${path}`);
      
      if (!res.ok) {
        const contentType = res.headers.get("content-type");
        if (contentType && contentType.includes("application/json")) {
            const errorJson = await res.json();
            throw new Error(errorJson.mensagem || "Erro na geração do arquivo.");
        }
        
        if (res.status === 400) throw new Error("Nenhuma nota encontrada para o período selecionado.");
        if (res.status === 401) throw new Error("Sessão expirada. Faça login novamente.");
        if (res.status === 501) throw new Error("O módulo de SPED Fiscal será liberado na próxima atualização.");
        throw new Error("Falha ao comunicar com o servidor para download.");
      }
      
      const blob = await res.blob();
      if (blob.size === 0) throw new Error("O arquivo gerado pelo servidor está vazio.");

      const blobUrl = window.URL.createObjectURL(blob);
      const a = document.createElement('a'); 
      a.href = blobUrl; 
      a.download = filename; 
      document.body.appendChild(a);a.click();a.remove();
      window.setTimeout(()=>window.URL.revokeObjectURL(blobUrl),1000);
      toast.success("Download concluído!", { id: "dl_toast" });
    } catch (e: any) {
      toast.dismiss("dl_toast"); // Tira o aviso de loading
      setErroApi(e.message); // MOSTRA O MODAL DE ERRO NA TELA
    }
  };

  // LÓGICA DE NEGÓCIO E ROTINAS
  const carregarKPIs = useCallback(async () => {
    if (!EMPRESA_ID) return;
    try {
      const json = await fetchSeguro(`${API_BASE}/estatisticas?empresaId=${EMPRESA_ID}`);
      if (json?.sucesso) setEstatisticas(json.dados);
    } catch (e) { /* Ignora no carregamento automático para não travar a tela inicial */ }
  }, [EMPRESA_ID]);

  const carregarNotas = useCallback(async () => {
    if (!EMPRESA_ID) return;
    setLoading(true);
    try {
      const params = new URLSearchParams({ empresaId: String(EMPRESA_ID), page: String(paginaAtual) });
      if (filtroStatus !== "TODOS") params.append("status", filtroStatus);
      if (filtroBusca) params.append("clienteNome", filtroBusca);

      const json = await fetchSeguro(`${API_BASE}?${params.toString()}`);
      if (json?.sucesso) {
        const lista=Array.isArray(json.dados)?json.dados:(json.dados?.data??json.dados?.content??[]);
        setNotas(lista);
        setPaginao(Array.isArray(json.dados)?null:json.dados);
      }
    } catch (error: any) {
      setErroApi(error.message); // Abre modal
    } finally {
      setLoading(false);
    }
  }, [EMPRESA_ID, filtroStatus, filtroBusca, paginaAtual]);

  useEffect(() => {
    if (aba === "historico" && EMPRESA_ID) {
      carregarKPIs();
      const timeout = setTimeout(carregarNotas, 400); 
      return () => clearTimeout(timeout);
    }
  }, [aba, EMPRESA_ID, filtroStatus, filtroBusca, paginaAtual, carregarNotas, carregarKPIs]);

  const handleConsultarCnpj = async () => {
    const limpo = clienteDoc.replace(/\D/g, "");
    if (limpo.length !== 14) { setErroApi("Digite os 14 números do CNPJ."); return; }
    
    setBuscandoCnpj(true);
    try {
      const data = await fetchSeguro(`${API_BASE}/cnpj/${limpo}`);
      if (data) { 
        setClienteNome(data.nome || data.fantasia || ""); 
        toast.success(`Dados de CNPJ recuperados com sucesso!`); 
      }
    } catch (e: any) { setErroApi(e.message); } finally { setBuscandoCnpj(false); }
  };

  const handleEmitir = async () => {
    if (itens.length === 0) { setErroApi("Você precisa adicionar pelo menos um produto/serviço à nota."); return; }
    if (!EMPRESA_ID) { setErroApi("Nenhuma empresa selecionada para a emissão."); return; }
    if (tipoNota === "NFSE") { setErroApi("A NFS-e depende da integração específica da prefeitura e ainda não está disponível para transmissão."); return; }
    if (!naturezaOp.trim()) { setErroApi("Informe a natureza da operação."); return; }
    const doc=clienteDoc.replace(/\D/g,"");
    if(doc&&doc.length!==11&&doc.length!==14){setErroApi("O CPF/CNPJ do destinatário deve conter 11 ou 14 dígitos.");return;}
    if(doc&&!clienteNome.trim()){setErroApi("Informe o nome ou razão social do destinatário identificado.");return;}
    const itemInvalido=itens.findIndex(i=>!i.descricao?.trim()||String(i.ncm??"").replace(/\D/g,"").length!==8||String(i.cfop??"").replace(/\D/g,"").length!==4||Number(i.quantidade)<=0||Number(i.valorUnitario)<0||Number(i.valorDesconto)<0||Number(i.valorDesconto)>Number(i.quantidade)*Number(i.valorUnitario));
    if(itemInvalido>=0){setErroApi(`Revise o item ${itemInvalido+1}: descrição, NCM (8 dígitos), CFOP (4 dígitos), quantidade, valor e desconto.`);return;}
    setEmitindo(true);

    try {
      const payload = {
        empresaId: EMPRESA_ID, clienteNome:clienteNome.trim(), clienteCpfCnpj: doc,
        tipo: tipoNota, naturezaOperacao: naturezaOp, formaPagamento, informacoesAdicionais: infoAdicionais,
        itens: itens.map(({id,...i}) => ({ ...i,produtoId:null }))
      };

      // 1. Cria a nota no banco
      const resCriar = await fetchSeguro(API_BASE, { method: "POST", body: JSON.stringify(payload) });
      
      // 2. Tenta capturar o ID de forma inteligente (caso o Java retorne de um jeito diferente)
      const idDaNota = resCriar?.dados?.id || resCriar?.data?.id || resCriar?.id;

      if (!idDaNota) {
        throw new Error("A nota foi salva, mas o backend não devolveu o ID para continuarmos a emissão.");
      }

      // 3. Pega o ID capturado e manda emitir
      const resEmitir = await fetchSeguro(`${API_BASE}/${idDaNota}/emitir`, { method: "POST" });
      
      const status=resEmitir?.dados?.status||resEmitir?.status;
      if (status === "AUTORIZADA" || status === "CONTINGENCIA") {
        toast.success(status==="AUTORIZADA"?"Nota autorizada com sucesso!":"Nota salva em contingência para transmissão posterior.");
        setAba("historico"); setItens([]); setClienteNome(""); setClienteDoc(""); setInfoAdicionais("");
      } else {
        setErroApi(`A nota não foi autorizada. Status retornado: ${resEmitir?.dados?.status || resEmitir?.status}`);
      }
    } catch (e: any) { 
      setErroApi(e.message); 
    } finally { 
      setEmitindo(false); 
    }
  };

  const handleCancelar = async (id: number) => {
    const motivo = window.prompt("Justificativa SEFAZ (Mín. 15 caracteres):");
    if (!motivo) return; // Se cancelou o prompt
    if (motivo.length < 15) { setErroApi("A justificativa de cancelamento deve ter pelo menos 15 caracteres."); return; }
    
    try {
      await fetchSeguro(`${API_BASE}/cancelar`, { method: "POST", body: JSON.stringify({ notaId: id, justificativa: motivo }) });
      toast.success("Nota Cancelada com sucesso!"); setNotaSelecionada(null); carregarNotas();
    } catch (e: any) { setErroApi(e.message); }
  };

  const handleExcluir = async (id: number) => {
    if (!window.confirm("Deseja excluir este rascunho permanentemente?")) return;
    try {
      await fetchSeguro(`${API_BASE}/${id}`, { method: "DELETE" });
      toast.success("Rascunho excluído!"); carregarNotas();
    } catch (e: any) { setErroApi(e.message); }
  };

  const handleUploadCertificado = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!arquivoCert || !senhaCert) { setErroApi("Preencha o arquivo .pfx e digite a senha."); return; }
    if (!EMPRESA_ID) { setErroApi("Empresa não identificada no contexto."); return; }
    
    setSalvandoCert(true);
    try {
      const formData = new FormData(); 
      formData.append("arquivo", arquivoCert); 
      formData.append("senha", senhaCert);
      
      const json = await fetchSeguro(`${API_BASE}/certificado/${EMPRESA_ID}`, { method: "POST", body: formData });
      if (json?.sucesso) { 
        toast.success("Certificado ativado e validado com sucesso!"); 
        setCertInfo(json.dados); 
      } 
    } catch (e: any) { setErroApi(e.message); } finally { setSalvandoCert(false); }
  };

  const totalNota = itens.reduce((acc, i) => acc + ((i.quantidade * i.valorUnitario) - i.valorDesconto), 0);
  const adicionarItem = () => setItens([...itens, { id: Date.now(), descricao: "", ncm: "", cfop: "5102", unidade: "UN", quantidade: 1, valorUnitario: 0, valorDesconto: 0, csosn: "102" }]);
  const notasFiltradas = notas.filter(n => filtroBusca === "" || (n.clienteNome && n.clienteNome.toLowerCase().includes(filtroBusca.toLowerCase())) || (n.numeroNota && String(n.numeroNota).includes(filtroBusca)) );

  if (!EMPRESA_ID) {
    return (
      <ClientOnly>
        <div style={{ padding: 48, textAlign: "center", color: theme.textMuted, display: "flex", flexDirection: "column", alignItems: "center", gap: 12 }}>
          <Store size={48} color={theme.border} />
          <h2 style={{ fontSize: 16, fontWeight: 600, color: theme.textMain, margin: 0 }}>Nenhuma empresa ativa</h2>
          <p style={{ fontSize: 14 }}>Selecione uma empresa no painel superior para acessar a emissão fiscal.</p>
        </div>
      </ClientOnly>
    );
  }

  // RENDERIZAÇÃO DA PÁGINA
  return (
    <ClientOnly>
      <div style={{ padding: "28px 28px 48px", display: "flex", flexDirection: "column", gap: 26, maxWidth: 1200, margin: "0 auto", background: theme.bgBase, color: theme.textMain }}>
        
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", flexWrap: "wrap", gap: 12 }}>
          <div>
            <h1 style={{ fontSize: 24, fontWeight: 800, color: theme.textMain, marginBottom: 4, display: "flex", alignItems: "center", gap: 8 }}>
              <Receipt color={theme.primary} size={24} /> Notas Fiscais
            </h1>
            <p style={{ fontSize: 13, color: theme.textMuted, margin: 0 }}>Emissão e gestão de NF-e, NFC-e e NFS-e</p>
            <p style={{ fontSize: 11, color: theme.textMuted, margin: "5px 0 0" }}><strong style={{ color: theme.textMain }}>{empresaAtiva?.razaoSocial || empresaAtiva?.nomeFantasia}</strong> · {fmtDocumento(empresaAtiva?.cnpj || empresaAtiva?.cpf)}</p>
          </div>
          <button onClick={carregarNotas} style={{ ...btnStyle, background: theme.bgCard }}>
             <RefreshCw size={14} className={loading ? "animate-spin" : ""} /> Atualizar Dados
          </button>
        </div>

        <div role="status" style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", flexWrap: "wrap", gap: 16, padding: "14px 16px", border: "1px solid rgba(245,158,11,.35)", borderRadius: 12, background: theme.warningAlpha }}>
          <div style={{ display: "flex", gap: 10, alignItems: "flex-start" }}>
            <AlertTriangle size={18} color={theme.warning} style={{ marginTop: 1, flexShrink: 0 }} />
            <div>
              <p style={{ margin: 0, fontSize: 13, fontWeight: 700, color: theme.textMain }}>Notas Fiscais em desenvolvimento e aprimoramento</p>
              <p style={{ margin: "4px 0 0", fontSize: 12, lineHeight: 1.5, color: theme.textMuted }}>A emissão fiscal pode apresentar instabilidades durante esta fase. Se encontrar qualquer erro, envie os detalhes pela Central de Suporte.</p>
            </div>
          </div>
          <Link href="/dashboard?section=configuracoes&settings=suporte" style={{ flexShrink: 0, padding: "8px 12px", borderRadius: 8, background: theme.primary, color: "#fff", fontSize: 12, fontWeight: 700, textDecoration: "none" }}>Abrir suporte</Link>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 12 }}>
          {[
            { label: "Faturamento (Mês)", val: fmt(estatisticas.valorTotalMes), ic: <TrendingUp size={16} />, cor: theme.primary },
            { label: "NF Autorizadas", val: estatisticas.totalAutorizadas, ic: <CheckCircle size={16} />, cor: "#34d399" },
            { label: "NF Rejeitadas", val: estatisticas.totalRejeitadas, ic: <AlertTriangle size={16} />, cor: theme.danger },
            { label: "NF Canceladas", val: estatisticas.totalCanceladas, ic: <XCircle size={16} />, cor: theme.textMuted }
          ].map((k, i) => (
             <div key={i} style={{ background: theme.bgCard, borderWidth: 1, borderStyle: "solid", borderColor: theme.border, borderRadius: 14, padding: "16px 20px" }}>
               <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
                  <span style={{ color: k.cor }}>{k.ic}</span>
                  <span style={{ fontSize: 11, fontWeight: 700, color: theme.textMuted, textTransform: "uppercase" }}>{k.label}</span>
               </div>
               <div style={{ fontSize: 24, fontWeight: 800, color: theme.textMain }}>{k.val}</div>
             </div>
          ))}
        </div>

        <div style={{ display: "flex", gap: 8, borderBottomWidth: 1, borderBottomStyle: "solid", borderBottomColor: theme.border }}>
          {[
            { id: "historico", label: "Histórico" }, { id: "emitir", label: "Nova Emissão" },
            { id: "certificado", label: "Certificado Digital" }, { id: "contador", label: "Contabilidade" }
          ].map(t => {
            const isAtivo = aba === t.id;
            return (
              <button key={t.id} onClick={() => setAba(t.id as AbaGeral)} 
                style={{ 
                  padding: "10px 18px", cursor: "pointer", borderRadius: "10px 10px 0 0", 
                  fontWeight: 700, fontSize: 13, marginBottom: isAtivo ? -1 : 0, transition: "all 0.15s",
                  background: isAtivo ? theme.bgCard : "transparent",
                  color: isAtivo ? theme.textMain : theme.textMuted,
                  borderTopWidth: 1, borderLeftWidth: 1, borderRightWidth: 1, borderBottomWidth: 0, borderStyle: "solid",
                  borderColor: isAtivo ? theme.border : "transparent"
                }}>
                {t.label}
              </button>
            )
          })}
        </div>

        {/* ABA: HISTÓRICO */}
        {aba === "historico" && (
          <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
              <div style={{ position: "relative", flex: 1, minWidth: 200 }}>
                <Search size={15} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: theme.textMuted }} />
                <input placeholder="Buscar por cliente ou número..." value={filtroBusca} onChange={e => { setFiltroBusca(e.target.value); setPaginaAtual(1); }} style={{ ...inpStyle, paddingLeft: 36 }} />
              </div>
              <select value={filtroStatus} onChange={e => { setFiltroStatus(e.target.value); setPaginaAtual(1); }} style={{ ...inpStyle, width: 200 }}>
                <option value="TODOS">Todos os Status</option>
                <option value="AUTORIZADA">Autorizada</option>
                <option value="REJEITADA">Rejeitada</option>
                <option value="CANCELADA">Cancelada</option>
                <option value="DIGITACAO">Rascunhos</option>
              </select>
            </div>

            <div style={{ background: theme.bgCard, borderWidth: 1, borderStyle: "solid", borderColor: theme.border, borderRadius: 14, overflow: "hidden", overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: 13 }}>
                <thead style={{ borderBottomWidth: 1, borderBottomStyle: "solid", borderBottomColor: theme.border, color: theme.textMuted, fontSize: 11, textTransform: "uppercase" }}>
                  <tr>
                    <th style={{ padding: "16px" }}>Documento</th><th style={{ padding: "16px" }}>Cliente</th><th style={{ padding: "16px" }}>Data</th>
                    <th style={{ padding: "16px" }}>Valor</th><th style={{ padding: "16px" }}>Status</th><th style={{ padding: "16px", textAlign: "right" }}>Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {notasFiltradas.length === 0 && !loading && <tr><td colSpan={6} style={{ padding: 40, textAlign: "center", color: theme.textMuted }}>Nenhuma nota localizada.</td></tr>}
                  {notasFiltradas.map(n => (
                    <tr key={n.id} style={{ borderBottomWidth: 1, borderBottomStyle: "solid", borderBottomColor: theme.border }}>
                      <td style={{ padding: "16px", fontWeight: 600 }}>{n.numeroNota || "Rascunho"} <span style={{ fontWeight: 400, color: theme.textMuted }}>({n.tipo})</span></td>
                      <td style={{ padding: "16px", color: theme.textMain }}>{n.clienteNome || "Consumidor Padrão"}</td>
                      <td style={{ padding: "16px", color: theme.textMuted }}>{fmtDate(n.dataEmissao)}</td>
                      <td style={{ padding: "16px", fontWeight: 700, color: theme.textMain }}>{fmt(n.valorTotal)}</td>
                      <td style={{ padding: "16px" }}><StatusBadge status={n.status} /></td>
                      <td style={{ padding: "16px", display: "flex", gap: 8, justifyContent: "flex-end" }}>
                        <button onClick={() => setNotaSelecionada(n)} style={btnStyle}><Eye size={14}/> Ver</button>
                        {n.status === "AUTORIZADA" && <button onClick={() => fazerDownloadSeguro(`${API_BASE}/${n.id}/xml`, `nf-${n.numeroNota}.xml`)} style={{...btnStyle, color: theme.primary, borderColor: theme.primaryAlpha}}><Download size={14}/> XML</button>}
                        {n.status === "DIGITACAO" && <button onClick={() => handleExcluir(n.id)} style={{...btnStyle, color: theme.danger, borderColor: theme.dangerAlpha}}><Trash2 size={14}/></button>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            
            {paginacao && paginacao.pages > 1 && (
              <div style={{ display: "flex", justifyContent: "flex-end", alignItems: "center", gap: 15 }}>
                <span style={{ fontSize: 12, color: theme.textMuted }}>Página {paginacao.page} de {paginacao.pages}</span>
                <button disabled={!paginacao.hasPrevious} onClick={() => setPaginaAtual(p => p - 1)} style={btnStyle}><ChevronLeft size={16}/></button>
                <button disabled={!paginacao.hasNext} onClick={() => setPaginaAtual(p => p + 1)} style={btnStyle}><ChevronRight size={16}/></button>
              </div>
            )}
          </div>
        )}

        {/* ABA: EMITIR NOVA NOTA */}
        {aba === "emitir" && (
          <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
            <div>
              <SectionTitle>Selecione o Modelo</SectionTitle>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 12 }}>
                {(["NFE", "NFCE", "NFSE"] as TipoNota[]).map(t => (
                  <button key={t} onClick={() => t!=="NFSE"&&setTipoNota(t)} disabled={t==="NFSE"} style={{
                    display: "flex", flexDirection: "column", gap: 8, padding: "16px 15px",
                    background: tipoNota === t ? theme.primaryAlpha : theme.bgCard,
                    borderWidth: 1, borderStyle: "solid", borderColor: tipoNota === t ? theme.primary : theme.border, 
                    borderRadius: 12, cursor: t==="NFSE"?"not-allowed":"pointer", textAlign: "left", transition: "all .16s",opacity:t==="NFSE"?.5:1
                  }}>
                    <div style={{ color: tipoNota === t ? theme.primary : theme.textMain }}><Receipt size={20} /></div>
                    <div>
                      <p style={{ fontSize: 13, fontWeight: 700, color: theme.textMain, margin: 0 }}>{t}</p>
                      <p style={{ fontSize: 11, color: theme.textMuted, margin: "3px 0 0" }}>{t === "NFE" ? "Produto (Mod. 55)" : t === "NFCE" ? "Consumidor (Mod. 65)" : "Integração municipal — em breve"}</p>
                    </div>
                  </button>
                ))}
              </div>
            </div>

            <Card title="Dados da Emissão">
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
                 <div>
                    <label style={lblStyle}>CPF OU CNPJ DO DESTINATÁRIO</label>
                    <div style={{ display: "flex", gap: 8 }}>
                      <input value={clienteDoc} onChange={e => setClienteDoc(e.target.value)} style={inpStyle} placeholder="Apenas números" />
                      {clienteDoc.replace(/\D/g, "").length >= 14 && (
                        <button onClick={handleConsultarCnpj} disabled={buscandoCnpj} style={{...btnStyle, padding: "0 15px"}}>
                          {buscandoCnpj ? <Loader2 size={16} className="animate-spin" /> : <Building2 size={16} />}
                        </button>
                      )}
                    </div>
                 </div>
                 <StyledInput label="Nome ou Razão Social" value={clienteNome} onChange={e => setClienteNome(e.target.value)} />
                 <StyledInput label="Natureza da Operação" value={naturezaOp} onChange={e => setNaturezaOp(e.target.value)} />
                 <div>
                    <label style={lblStyle}>PAGAMENTO</label>
                    <select value={formaPagamento} onChange={e => setFormaPagamento(e.target.value)} style={inpStyle}>
                        <option value="PIX">Pix</option><option value="DINHEIRO">Dinheiro</option><option value="CARTAO_CREDITO">Cartão de Crédito</option>
                    </select>
                 </div>
              </div>
            </Card>

            <Card>
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 16, alignItems: "center" }}>
                <SectionTitle>Produtos da Nota</SectionTitle>
                <button onClick={adicionarItem} style={{ background: theme.primaryAlpha, color: theme.primary, border: "none", padding: "6px 12px", borderRadius: 6, cursor: "pointer", fontWeight: 700, fontSize: 11, display: "flex", alignItems: "center", gap: 5 }}>
                  <Plus size={14}/> ADICIONAR ITEM
                </button>
              </div>
              
              {itens.length === 0 && <div style={{ textAlign: "center", padding: 30, color: theme.textMuted, fontSize: 13, borderStyle: "dashed", borderWidth: 1, borderColor: theme.border, borderRadius: 10 }}>Nenhum produto adicionado.</div>}

              {itens.map((it, idx) => (
                <div key={it.id} style={{ display: "grid", gridTemplateColumns: "3fr 1fr 1fr 1fr 1fr 40px", gap: 10, marginBottom: 15 }}>
                  <StyledInput label="Descrição" value={it.descricao} onChange={e => { const n = [...itens]; n[idx].descricao = e.target.value; setItens(n); }} />
                  <StyledInput label="NCM" value={it.ncm} onChange={e => { const n = [...itens]; n[idx].ncm = e.target.value; setItens(n); }} />
                  <StyledInput label="Qtd" type="number" value={it.quantidade} onChange={e => { const n = [...itens]; n[idx].quantidade = Number(e.target.value); setItens(n); }} />
                  <StyledInput label="R$ Unit" type="number" value={it.valorUnitario} onChange={e => { const n = [...itens]; n[idx].valorUnitario = Number(e.target.value); setItens(n); }} />
                  <div>
                    <label style={lblStyle}>Total</label>
                    <div style={{ padding: "9px 12px", fontWeight: 700, fontSize: 13, color: theme.primary, background: theme.primaryAlpha, borderRadius: 8, borderWidth: 1, borderStyle: "solid", borderColor: theme.primaryAlpha }}>
                      {fmt((it.quantidade * it.valorUnitario) - it.valorDesconto)}
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", justifyContent: "center", paddingTop: 20 }}>
                    <button onClick={() => setItens(itens.filter(i => i.id !== it.id))} style={{ color: theme.danger, background: "none", border: "none", cursor: "pointer" }}><Trash2 size={18}/></button>
                  </div>
                </div>
              ))}
              
              {itens.length > 0 && (
                <div style={{ textAlign: "right", marginTop: 20, borderTopWidth: 1, borderTopStyle: "solid", borderTopColor: theme.border, paddingTop: 16 }}>
                  <span style={{ marginRight: 15, color: theme.textMuted, fontSize: 12, fontWeight: 700, textTransform: "uppercase" }}>Total Geral:</span>
                  <span style={{ fontSize: 24, fontWeight: 800, color: theme.textMain }}>{fmt(totalNota)}</span>
                </div>
              )}
            </Card>

            <button disabled={emitindo} onClick={handleEmitir} style={{ background: theme.primary, color: "#000", padding: 16, borderRadius: 12, fontSize: 14, fontWeight: 800, border: "none", cursor: emitindo ? "not-allowed" : "pointer", display: "flex", justifyContent: "center", alignItems: "center", gap: 8, transition: "opacity 0.2s" }}>
              {emitindo ? <Loader2 className="animate-spin" size={18} /> : <Send size={18} />} 
              {emitindo ? "PROCESSANDO SEFAZ..." : "TRANSMITIR NOTA FISCAL"}
            </button>
          </div>
        )}

        {/* ABA: CERTIFICADO DIGITAL */}
        {aba === "certificado" && (
           <Card title="Certificado Digital A1 (.pfx)" style={{ maxWidth: 600 }}>
              <p style={{ fontSize: 13, color: theme.textMuted, margin: "-10px 0 20px" }}>Obrigatório para assinatura HTTPS mTLS com a SEFAZ.</p>
              <form onSubmit={handleUploadCertificado} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                 <div style={{ padding: 20, borderWidth: 2, borderStyle: "dashed", borderColor: theme.border, borderRadius: 10, textAlign: "center", background: theme.bgBase, position: "relative" }}>
                    <input type="file" accept=".pfx,.p12" onChange={e => setArquivoCert(e.target.files?.[0] || null)} style={{ opacity: 0, position: "absolute", inset: 0, cursor: "pointer", zIndex: 10 }} />
                    <Upload size={24} style={{ color: theme.textMuted, marginBottom: 8, margin: "0 auto" }} />
                    <p style={{ margin: 0, fontSize: 13, fontWeight: 600, color: arquivoCert ? theme.primary : theme.textMuted }}>
                      {arquivoCert ? `✓ ${arquivoCert.name}` : "Clique ou arraste seu arquivo .pfx aqui"}
                    </p>
                 </div>
                 <StyledInput label="Senha do Certificado" type="password" value={senhaCert} onChange={e => setSenhaCert(e.target.value)} />
                 <button type="submit" disabled={salvandoCert} style={{ background: theme.primary, color: theme.bgBase, padding: 14, borderRadius: 10, border: "none", fontWeight: 800, cursor: "pointer", display: "flex", justifyContent: "center", gap: 8 }}>
                   {salvandoCert ? <Loader2 className="animate-spin" size={16}/> : <ShieldCheck size={16}/>}
                   {salvandoCert ? "VALIDANDO..." : "SALVAR E ATIVAR CERTIFICADO"}
                 </button>
              </form>
              {certInfo && (
                <div style={{ marginTop: 20, padding: 16, background: theme.primaryAlpha, borderWidth: 1, borderStyle: "solid", borderColor: theme.primaryAlpha, borderRadius: 10, fontSize: 13 }}>
                  <p style={{ margin: "0 0 8px 0", fontWeight: 700, color: theme.primary, display: "flex", alignItems: "center", gap: 6 }}><CheckCircle size={16}/> Certificado Operacional</p>
                  <p style={{ margin: "0 0 4px", color: theme.textMain }}><strong>Titular:</strong> {certInfo.titular}</p>
                  <p style={{ margin: 0, color: theme.textMain }}><strong>Validade:</strong> {certInfo.validoAte}</p>
                </div>
              )}
           </Card>
        )}

        {/* ABA: CONTADOR (EXPORTAÇÕES) */}
        {aba === "contador" && (
           <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 20 }}>
              <Card title="Exportação de Arquivos XML (Mensal)">
                 <p style={{ fontSize: 13, color: theme.textMuted, margin: "-10px 0 20px" }}>ZIP contendo todos os XMLs autorizados para a contabilidade.</p>
                 <StyledInput label="Mês de Referência" type="month" value={periodoExport} onChange={e => setPeriodoExport(e.target.value)} style={{ marginBottom: 16 }} />
                 <button onClick={() => fazerDownloadSeguro(`${API_BASE}/exportar/xml-mensal?empresaId=${EMPRESA_ID}&periodo=${periodoExport}`, `xmls-${periodoExport}.zip`)} style={{...btnStyle, width: "100%", justifyContent: "center", padding: 12, color: theme.primary, borderColor: theme.primaryAlpha}}><Download size={16}/> GERAR E BAIXAR ZIP</button>
              </Card>
              <Card title="Geração SPED Fiscal">
                 <p style={{ fontSize: 13, color: theme.textMuted, margin: "-10px 0 20px" }}>Geração de TXT (Lucro Presumido/Real).</p>
                 <StyledInput label="Mês de Referência" type="month" value={periodoExport} onChange={e => setPeriodoExport(e.target.value)} style={{ marginBottom: 12 }} />
                 <div style={{ marginBottom: 16 }}>
                    <label style={lblStyle}>Tipo</label>
                    <select value={tipoSped} onChange={e => setTipoSped(e.target.value)} style={inpStyle}><option value="EFD_ICMS_IPI">EFD ICMS/IPI</option><option value="EFD_CONTRIBUICOES">EFD Contribuições</option></select>
                 </div>
                 <button disabled style={{...btnStyle, width: "100%", justifyContent:"center",padding:12,cursor:"not-allowed",opacity:.55}} title="Integração SPED ainda não implementada"><FileText size={16}/> SPED — EM BREVE</button>
              </Card>
           </div>
        )}

        {/* MODAL DE DETALHES DA NOTA */}
        {notaSelecionada && (
          <div onClick={() => setNotaSelecionada(null)} style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.8)", display: "flex", justifyContent: "center", alignItems: "center", zIndex: 999, backdropFilter: "blur(4px)" }}>
              <div onClick={e => e.stopPropagation()} style={{ background: theme.bgCard, padding: 32, borderRadius: 16, borderWidth: 1, borderStyle: "solid", borderColor: theme.border, width: 500, maxWidth: "90%", boxShadow: "0 20px 40px rgba(0,0,0,0.5)" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 24 }}>
                      <h2 style={{ margin: 0, fontSize: 18, fontWeight: 800 }}>{notaSelecionada.tipo} - {notaSelecionada.numeroNota || "Rascunho"}</h2>
                      <button onClick={() => setNotaSelecionada(null)} style={{ background: "none", border: "none", cursor: "pointer", color: theme.textMuted }}><X size={24}/></button>
                  </div>
                  
                  <div style={{ display: "flex", flexDirection: "column", gap: 16, fontSize: 13 }}>
                      <div style={{ display: "flex", justifyContent: "space-between", borderBottomWidth: 1, borderBottomStyle: "solid", borderBottomColor: theme.border, paddingBottom: 12 }}><span style={{ color: theme.textMuted }}>Cliente:</span><strong style={{ color: theme.textMain }}>{notaSelecionada.clienteNome || "Consumidor Padrão"}</strong></div>
                      <div style={{ display: "flex", justifyContent: "space-between", borderBottomWidth: 1, borderBottomStyle: "solid", borderBottomColor: theme.border, paddingBottom: 12 }}><span style={{ color: theme.textMuted }}>Status:</span><StatusBadge status={notaSelecionada.status} /></div>
                      <div style={{ display: "flex", justifyContent: "space-between", borderBottomWidth: 1, borderBottomStyle: "solid", borderBottomColor: theme.border, paddingBottom: 12 }}><span style={{ color: theme.textMuted }}>Chave:</span><span style={{ fontFamily: "monospace", color: theme.primary }}>{notaSelecionada.chaveAcesso || "Não gerada"}</span></div>
                      <div style={{ display: "flex", justifyContent: "space-between" }}><span style={{ color: theme.textMuted }}>Protocolo:</span><strong style={{ fontFamily: "monospace", color: theme.textMain }}>{notaSelecionada.protocolo || "—"}</strong></div>
                      {notaSelecionada.motivoRejeicao && (<div style={{ padding: 12, background: theme.dangerAlpha, color: theme.danger, borderRadius: 8, borderWidth: 1, borderStyle: "solid", borderColor: theme.dangerAlpha }}><strong>Rejeição/Falha:</strong><br/>{notaSelecionada.motivoRejeicao}</div>)}
                  </div>

                  <div style={{ marginTop: 30, display: "flex", gap: 10 }}>
                      {notaSelecionada.status === "AUTORIZADA" && (
                          <>
                             <button disabled title="Gerador de DANFE ainda não configurado" style={{ ...btnStyle, flex:1,justifyContent:"center",cursor:"not-allowed",opacity:.55 }}><FileText size={16}/> DANFE em breve</button>
                             <button onClick={() => fazerDownloadSeguro(`${API_BASE}/${notaSelecionada.id}/xml`, `nf-${notaSelecionada.numeroNota}.xml`)} style={{ ...btnStyle, flex: 1, justifyContent: "center", color: theme.primary, borderColor: theme.primaryAlpha }}><Download size={16}/> XML</button>
                             <button onClick={() => handleCancelar(notaSelecionada.id)} style={{ ...btnStyle, flex: 1, justifyContent: "center", color: theme.danger, borderColor: theme.dangerAlpha, background: theme.dangerAlpha }}><Trash2 size={16}/> Cancelar</button>
                          </>
                      )}
                  </div>
              </div>
          </div>
        )}

        {/* MODAL GLOBAL DE ERRO (O que você pediu!) */}
        {erroApi && (
          <div onClick={() => setErroApi(null)} style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.85)", display: "flex", justifyContent: "center", alignItems: "center", zIndex: 9999, backdropFilter: "blur(6px)" }}>
            <div onClick={e => e.stopPropagation()} style={{ background: theme.bgCard, padding: "40px 32px", borderRadius: 20, borderWidth: 1, borderStyle: "solid", borderColor: theme.danger, width: 400, maxWidth: "90%", boxShadow: "0 25px 50px -12px rgba(239,68,68,0.25)", textAlign: "center", animation: "slideUp 0.3s ease-out" }}>
              <div style={{ display: "inline-flex", padding: 16, background: theme.dangerAlpha, borderRadius: "50%", marginBottom: 20 }}>
                <AlertTriangle size={48} color={theme.danger} />
              </div>
              <h2 style={{ margin: "0 0 12px", fontSize: 20, fontWeight: 800, color: theme.textMain }}>Atenção!</h2>
              <p style={{ margin: "0 0 30px", fontSize: 14, color: theme.textMuted, lineHeight: 1.6 }}>{erroApi}</p>
              <button onClick={() => setErroApi(null)} style={{ background: theme.danger, color: "#fff", border: "none", padding: "14px 20px", borderRadius: 10, fontSize: 14, fontWeight: 800, cursor: "pointer", width: "100%", transition: "opacity 0.2s" }}>
                ENTENDI E FECHAR
              </button>
            </div>
          </div>
        )}

      </div>
      <style>{`
        @keyframes spin { 100% { transform: rotate(360deg); } } 
        .animate-spin { animation: spin 1s linear infinite; }
        @keyframes slideUp { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: translateY(0); } }
      `}</style>
    </ClientOnly>
  );
}
