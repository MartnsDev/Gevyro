"use client";

import { useState, useEffect, useCallback, ReactNode } from "react";
import Link from "next/link";
import { toast } from "sonner";
import {
  FileText, Upload, Download, Plus, Search, CheckCircle,
  XCircle, ShieldCheck, FileArchive, Send, Trash2, Loader2,
  AlertTriangle, Receipt, Briefcase, Eye, X, RefreshCw, 
  Building2, ChevronLeft, ChevronRight, TrendingUp, Store, FilePenLine, Settings
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
type AbaGeral = "historico" | "emitir" | "configuracao" | "certificado" | "contador";
type TipoNota = "NFE" | "NFCE" | "NFSE";
type ConfiguracaoFiscal = {
  inscricaoEstadual: string; regimeTributario: string; ambiente: "HOMOLOGACAO" | "PRODUCAO";
  serieNfe: string; serieNfce: string; cscId: string; cscConfigurado: boolean;
  fiscalHabilitado: boolean; nfeHabilitada: boolean; nfceHabilitada: boolean; nfseHabilitada: boolean;
};
type ItemRascunho = {
  id: number; descricao: string; ncm: string; cfop: string; unidade: string;
  quantidade: number; valorUnitario: number; valorDesconto: number; csosn: string;
};
type RascunhoLocal = {
  versao: 1; salvoEm: number; tipoNota: TipoNota; naturezaOp: string; formaPagamento: string;
  clienteDoc: string; clienteNome: string; infoAdicionais: string; itens: ItemRascunho[];
};

const RASCUNHO_TTL_MS = 2 * 60 * 60 * 1000;
const chaveRascunho = (empresaId: number) => `gevyro:fiscal:rascunho:v1:${empresaId}`;
const textoSeguro = (valor: unknown, limite: number) => typeof valor === "string" ? valor.slice(0, limite) : "";
const numeroSeguro = (valor: unknown, padrao = 0) => {
  const numero = Number(valor);
  return Number.isFinite(numero) ? numero : padrao;
};

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
  const isErr = status === "REJEITADA" || status === "CANCELADA" || status === "ERRO_TECNICO";
  const isWarn = status === "CONTINGENCIA";
  const isPending = status === "PENDENTE_EMISSAO" || status === "PROCESSANDO" || status === "VALIDANDO";
  
  const bg = isOk ? theme.primaryAlpha : isErr ? theme.dangerAlpha : isWarn ? theme.warningAlpha : isPending ? "rgba(59,130,246,0.12)" : "var(--surface-overlay)";
  const color = isOk ? theme.primary : isErr ? theme.danger : isWarn ? theme.warning : isPending ? "#3b82f6" : "var(--primary)";
  const border = isOk ? "rgba(16,185,129,0.3)" : isErr ? "rgba(239,68,68,0.3)" : isWarn ? "rgba(245,158,11,0.3)" : isPending ? "rgba(59,130,246,0.3)" : theme.border;

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
  const [notaCartaCorrecao, setNotaCartaCorrecao] = useState<any>(null);
  const [textoCartaCorrecao, setTextoCartaCorrecao] = useState("");
  const [cienteLimitesCce, setCienteLimitesCce] = useState(false);
  const [enviandoCce, setEnviandoCce] = useState(false);

  const [tipoNota, setTipoNota] = useState<TipoNota>("NFE");
  const [passoEmissao, setPassoEmissao] = useState(1);
  const [naturezaOp, setNaturezaOp] = useState("Venda de Mercadoria");
  const [formaPagamento, setFormaPagamento] = useState("PIX");
  const [clienteDoc, setClienteDoc] = useState("");
  const [clienteNome, setClienteNome] = useState("");
  const [infoAdicionais, setInfoAdicionais] = useState("");
  const [itens, setItens] = useState<ItemRascunho[]>([]);
  const [buscandoCnpj, setBuscandoCnpj] = useState(false);
  const [rascunhoPronto, setRascunhoPronto] = useState(false);
  const [rascunhoSalvoEm, setRascunhoSalvoEm] = useState<number | null>(null);

  const [arquivoCert, setArquivoCert] = useState<File | null>(null);
  const [senhaCert, setSenhaCert] = useState("");
  const [certInfo, setCertInfo] = useState<any>(null);
  const [periodoExport, setPeriodoExport] = useState(new Date().toISOString().slice(0, 7));
  const [tipoSped, setTipoSped] = useState("EFD_ICMS_IPI");
  const [configFiscal, setConfigFiscal] = useState<ConfiguracaoFiscal | null>(null);
  const [cscNovo, setCscNovo] = useState("");
  const [confirmarProducao, setConfirmarProducao] = useState(false);
  const [salvandoConfig, setSalvandoConfig] = useState(false);

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

  const carregarConfiguracao = useCallback(async () => {
    if (!EMPRESA_ID) return;
    try {
      const res = await fetchAuth(`/api/fiscal/configuracao/${EMPRESA_ID}`);
      const json = await res.json().catch(() => null);
      if (!res.ok) throw new Error(json?.mensagem || "Não foi possível carregar a configuração fiscal.");
      const c = json?.dados;
      setConfigFiscal({
        inscricaoEstadual: c?.inscricaoEstadual ?? "", regimeTributario: c?.regimeTributario ?? "SIMPLES_NACIONAL",
        ambiente: c?.ambiente ?? "HOMOLOGACAO", serieNfe: c?.serieNfe ?? "1", serieNfce: c?.serieNfce ?? "1",
        cscId: c?.cscId ?? "", cscConfigurado: Boolean(c?.cscConfigurado),
        fiscalHabilitado: Boolean(c?.fiscalHabilitado), nfeHabilitada: Boolean(c?.nfeHabilitada),
        nfceHabilitada: Boolean(c?.nfceHabilitada), nfseHabilitada: Boolean(c?.nfseHabilitada)
      });
    } catch (e: any) { setErroApi(e.message); }
  }, [EMPRESA_ID]);

  useEffect(() => {
    setConfigFiscal(null); setCscNovo(""); setConfirmarProducao(false);
    if (EMPRESA_ID) carregarConfiguracao();
  }, [EMPRESA_ID, carregarConfiguracao]);

  useEffect(() => {
    setRascunhoPronto(false);
    setPassoEmissao(1);
    setTipoNota("NFE"); setNaturezaOp("Venda de Mercadoria"); setFormaPagamento("PIX");
    setClienteDoc(""); setClienteNome(""); setInfoAdicionais(""); setItens([]); setRascunhoSalvoEm(null);
    if (!EMPRESA_ID) return;
    try {
      const armazenado = sessionStorage.getItem(chaveRascunho(EMPRESA_ID));
      if (armazenado) {
        const bruto = JSON.parse(armazenado) as Partial<RascunhoLocal>;
        const agora = Date.now();
        if (bruto.versao === 1 && typeof bruto.salvoEm === "number" && Number.isFinite(bruto.salvoEm)
          && bruto.salvoEm <= agora && agora - bruto.salvoEm <= RASCUNHO_TTL_MS) {
          const tipo = bruto.tipoNota === "NFCE" ? "NFCE" : "NFE";
          const itensValidos = Array.isArray(bruto.itens) ? bruto.itens.slice(0, 100).map((item, indice) => ({
            id: numeroSeguro(item?.id, Date.now() + indice), descricao: textoSeguro(item?.descricao, 200),
            ncm: textoSeguro(item?.ncm, 8).replace(/\D/g, ""), cfop: textoSeguro(item?.cfop, 4).replace(/\D/g, ""),
            unidade: textoSeguro(item?.unidade, 6) || "UN", quantidade: numeroSeguro(item?.quantidade, 1),
            valorUnitario: numeroSeguro(item?.valorUnitario), valorDesconto: numeroSeguro(item?.valorDesconto),
            csosn: textoSeguro(item?.csosn, 4) || "102"
          })) : [];
          setTipoNota(tipo); setNaturezaOp(textoSeguro(bruto.naturezaOp, 100));
          setFormaPagamento(textoSeguro(bruto.formaPagamento, 30) || "PIX");
          setClienteDoc(textoSeguro(bruto.clienteDoc, 18)); setClienteNome(textoSeguro(bruto.clienteNome, 200));
          setInfoAdicionais(textoSeguro(bruto.infoAdicionais, 2000)); setItens(itensValidos);
          setRascunhoSalvoEm(bruto.salvoEm);
        } else {
          sessionStorage.removeItem(chaveRascunho(EMPRESA_ID));
        }
      }
    } catch {
      sessionStorage.removeItem(chaveRascunho(EMPRESA_ID));
    } finally {
      setRascunhoPronto(true);
    }
  }, [EMPRESA_ID]);

  useEffect(() => {
    if (!EMPRESA_ID || !rascunhoPronto) return;
    const timeout = window.setTimeout(() => {
      const vazio = !clienteDoc && !clienteNome && !infoAdicionais && itens.length === 0
        && naturezaOp === "Venda de Mercadoria" && formaPagamento === "PIX" && tipoNota === "NFE";
      if (vazio) {
        sessionStorage.removeItem(chaveRascunho(EMPRESA_ID));
        setRascunhoSalvoEm(null);
        return;
      }
      const salvoEm = Date.now();
      const rascunho: RascunhoLocal = { versao: 1, salvoEm, tipoNota, naturezaOp, formaPagamento,
        clienteDoc, clienteNome, infoAdicionais, itens };
      sessionStorage.setItem(chaveRascunho(EMPRESA_ID), JSON.stringify(rascunho));
      setRascunhoSalvoEm(salvoEm);
    }, 500);
    return () => window.clearTimeout(timeout);
  }, [EMPRESA_ID, rascunhoPronto, tipoNota, naturezaOp, formaPagamento, clienteDoc, clienteNome, infoAdicionais, itens]);

  const salvarConfiguracao = async () => {
    if (!EMPRESA_ID || !configFiscal) return;
    setSalvandoConfig(true);
    try {
      const res = await fetchAuth(`/api/fiscal/configuracao/${EMPRESA_ID}`, {
        method: "PUT", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          inscricaoEstadual: configFiscal.inscricaoEstadual,
          regimeTributario: configFiscal.regimeTributario,
          ambiente: configFiscal.ambiente,
          serieNfe: configFiscal.serieNfe,
          serieNfce: configFiscal.serieNfce,
          cscId: configFiscal.cscId,
          csc: cscNovo || null,
          fiscalHabilitado: configFiscal.fiscalHabilitado,
          nfeHabilitada: configFiscal.nfeHabilitada,
          nfceHabilitada: configFiscal.nfceHabilitada,
          nfseHabilitada: configFiscal.nfseHabilitada,
          confirmarProducao
        })
      });
      const json = await res.json().catch(() => null);
      if (!res.ok) throw new Error(json?.mensagem || "Não foi possível salvar a configuração fiscal.");
      setCscNovo(""); setConfirmarProducao(false);
      await carregarConfiguracao();
      toast.success("Configuração fiscal salva com segurança.");
    } catch (e: any) { setErroApi(e.message); }
    finally { setSalvandoConfig(false); }
  };

  const carregarNotas = useCallback(async () => {
    if (!EMPRESA_ID) return;
    setLoading(true);
    try {
      const params = new URLSearchParams({ empresaId: String(EMPRESA_ID), page: String(paginaAtual) });
      if (filtroStatus !== "TODOS") params.append("status", filtroStatus);
      if (filtroBusca) {
        const busca = filtroBusca.trim();
        if (/^\\d{1,9}$/.test(busca)) params.append("numero", String(Number(busca)));
        else params.append("clienteNome", busca);
      }

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

  const documentoHabilitado = (tipo: TipoNota) => Boolean(configFiscal?.fiscalHabilitado
    && (tipo === "NFE" ? configFiscal.nfeHabilitada : tipo === "NFCE" ? configFiscal.nfceHabilitada : false));

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
    if (!documentoHabilitado(tipoNota)) { setErroApi("Este tipo de documento não está habilitado na configuração fiscal da empresa."); return; }
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
      const storageKey = `gevyro:fiscal:idempotency:emissao:${idDaNota}`;
      const idempotencyKey = sessionStorage.getItem(storageKey) || crypto.randomUUID();
      sessionStorage.setItem(storageKey, idempotencyKey);
      const resEmitir = await fetchSeguro(`${API_BASE}/${idDaNota}/emitir`, {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey },
      });
      
      const status=resEmitir?.dados?.status||resEmitir?.status;
      if (status === "AUTORIZADA" || status === "CONTINGENCIA") {
        sessionStorage.removeItem(storageKey);
        sessionStorage.removeItem(chaveRascunho(EMPRESA_ID)); setRascunhoSalvoEm(null);
        toast.success(status==="AUTORIZADA"?"Nota autorizada com sucesso!":"Nota salva em contingência para transmissão posterior.");
        setAba("historico"); setPassoEmissao(1); setItens([]); setClienteNome(""); setClienteDoc(""); setInfoAdicionais("");
      } else if (status === "PENDENTE_EMISSAO" || status === "PROCESSANDO" || status === "VALIDANDO") {
        sessionStorage.removeItem(chaveRascunho(EMPRESA_ID)); setRascunhoSalvoEm(null);
        toast.success("Nota recebida e enfileirada para emissão fiscal.");
        setAba("historico"); setPassoEmissao(1); setItens([]); setClienteNome(""); setClienteDoc(""); setInfoAdicionais("");
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

  const abrirCartaCorrecao = (nota: any) => {
    if (nota?.tipo !== "NFE" || nota?.status !== "AUTORIZADA") return;
    setNotaSelecionada(null);
    setNotaCartaCorrecao(nota);
    setTextoCartaCorrecao("");
    setCienteLimitesCce(false);
  };

  const fecharCartaCorrecao = () => {
    if (enviandoCce) return;
    setNotaCartaCorrecao(null);
    setTextoCartaCorrecao("");
    setCienteLimitesCce(false);
  };

  const handleCartaCorrecao = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!notaCartaCorrecao) return;
    const correcao = textoCartaCorrecao.trim();
    if (correcao.length < 15 || correcao.length > 1000) {
      setErroApi("A correção deve conter entre 15 e 1000 caracteres.");
      return;
    }
    if (!cienteLimitesCce) {
      setErroApi("Confirme que a correção respeita as restrições legais da CC-e.");
      return;
    }
    setEnviandoCce(true);
    try {
      const resposta = await fetchSeguro(`${API_BASE}/carta-correcao`, {
        method: "POST",
        body: JSON.stringify({ notaId: notaCartaCorrecao.id, correcao }),
      });
      const sequencia = resposta?.dados?.sequencia;
      toast.success(`CC-e${sequencia ? ` nº ${sequencia}` : ""} registrada na SEFAZ.`);
      setNotaCartaCorrecao(null);
      setTextoCartaCorrecao("");
      setCienteLimitesCce(false);
      carregarNotas();
    } catch (e: any) {
      setErroApi(e.message);
    } finally {
      setEnviandoCce(false);
    }
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
  const validarPassoEmissao = () => {
    if (passoEmissao === 1) {
      if (!documentoHabilitado(tipoNota)) return "Selecione um modelo fiscal habilitado para esta empresa.";
      if (!naturezaOp.trim()) return "Informe a natureza da operação.";
      const doc = clienteDoc.replace(/\D/g, "");
      if (doc && doc.length !== 11 && doc.length !== 14) return "O CPF/CNPJ deve conter 11 ou 14 dígitos.";
      if (doc && !clienteNome.trim()) return "Informe o nome ou razão social do destinatário.";
    }
    if (passoEmissao === 2) {
      if (itens.length === 0) return "Adicione pelo menos um item.";
      const indice = itens.findIndex(i => !i.descricao.trim() || !/^\d{8}$/.test(i.ncm) || !/^\d{4}$/.test(i.cfop)
        || !i.unidade.trim() || i.quantidade <= 0 || i.valorUnitario < 0 || i.valorDesconto < 0
        || i.valorDesconto > i.quantidade * i.valorUnitario || !i.csosn.trim());
      if (indice >= 0) return `Revise os dados fiscais e valores do item ${indice + 1}.`;
    }
    return null;
  };
  const avancarPasso = () => {
    const erro = validarPassoEmissao();
    if (erro) { setErroApi(erro); return; }
    setPassoEmissao(p => Math.min(4, p + 1));
  };
  const notasFiltradas = notas;

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

        <div style={{ display: "flex", flexWrap: "wrap", gap: 8, borderBottomWidth: 1, borderBottomStyle: "solid", borderBottomColor: theme.border }}>
          {[
            { id: "historico", label: "Histórico" }, { id: "emitir", label: "Nova Emissão" }, { id: "configuracao", label: "Configuração" },
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
                <option value="PROCESSANDO">Em processamento</option>
                <option value="PENDENTE_EMISSAO">Aguardando emissão</option>
                <option value="ERRO_TECNICO">Erro técnico</option>
                <option value="CONTINGENCIA">Contingência</option>
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
                        {n.status === "AUTORIZADA" && (n.tipo === "NFE" || n.tipo === "NFCE") && <button onClick={() => fazerDownloadSeguro(`${API_BASE}/${n.id}/danfe`, `danfe-${n.numeroNota}.pdf`)} style={{...btnStyle, color: theme.primary, borderColor: theme.primaryAlpha}}><FileText size={14}/> DANFE</button>}
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
            {rascunhoSalvoEm && <div role="status" style={{ alignSelf: "flex-end", color: theme.textMuted, fontSize: 11 }}>
              Rascunho temporário salvo neste navegador às {new Date(rascunhoSalvoEm).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}
            </div>}
            <div aria-label="Etapas da emissão" style={{ display: "grid", gridTemplateColumns: "repeat(4, minmax(120px, 1fr))", gap: 8 }}>
              {["Operação", "Itens e tributação", "Pagamento", "Revisão"].map((nome, indice) => {
                const numero = indice + 1;
                return <button key={nome} type="button" onClick={() => numero < passoEmissao && setPassoEmissao(numero)}
                  aria-current={numero === passoEmissao ? "step" : undefined} style={{ ...btnStyle, justifyContent: "center",
                    cursor: numero < passoEmissao ? "pointer" : "default", background: numero === passoEmissao ? theme.primaryAlpha : theme.bgCard,
                    borderColor: numero === passoEmissao ? theme.primary : theme.border, color: numero === passoEmissao ? theme.primary : theme.textMuted }}>
                  {numero}. {nome}
                </button>;
              })}
            </div>

            {passoEmissao === 1 && <>
            <div>
              <SectionTitle>Selecione o Modelo</SectionTitle>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 12 }}>
                {(["NFE", "NFCE", "NFSE"] as TipoNota[]).map(t => (
                  <button key={t} onClick={() => documentoHabilitado(t)&&setTipoNota(t)} disabled={!documentoHabilitado(t)} style={{
                    display: "flex", flexDirection: "column", gap: 8, padding: "16px 15px",
                    background: tipoNota === t ? theme.primaryAlpha : theme.bgCard,
                    borderWidth: 1, borderStyle: "solid", borderColor: tipoNota === t ? theme.primary : theme.border, 
                    borderRadius: 12, cursor: !documentoHabilitado(t)?"not-allowed":"pointer", textAlign: "left", transition: "all .16s",opacity:!documentoHabilitado(t)?.5:1
                  }}>
                    <div style={{ color: tipoNota === t ? theme.primary : theme.textMain }}><Receipt size={20} /></div>
                    <div>
                      <p style={{ fontSize: 13, fontWeight: 700, color: theme.textMain, margin: 0 }}>{t}</p>
                      <p style={{ fontSize: 11, color: theme.textMuted, margin: "3px 0 0" }}>{!documentoHabilitado(t) ? "Desabilitada na configuração" : t === "NFE" ? "Produto (Mod. 55)" : "Consumidor (Mod. 65)"}</p>
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
              </div>
            </Card>
            </>}

            {passoEmissao === 2 &&
            <Card>
              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 16, alignItems: "center" }}>
                <SectionTitle>Produtos da Nota</SectionTitle>
                <button onClick={adicionarItem} style={{ background: theme.primaryAlpha, color: theme.primary, border: "none", padding: "6px 12px", borderRadius: 6, cursor: "pointer", fontWeight: 700, fontSize: 11, display: "flex", alignItems: "center", gap: 5 }}>
                  <Plus size={14}/> ADICIONAR ITEM
                </button>
              </div>
              
              {itens.length === 0 && <div style={{ textAlign: "center", padding: 30, color: theme.textMuted, fontSize: 13, borderStyle: "dashed", borderWidth: 1, borderColor: theme.border, borderRadius: 10 }}>Nenhum produto adicionado.</div>}

              {itens.map((it, idx) => (
                <div key={it.id} style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(105px, 1fr))", gap: 10, marginBottom: 20, paddingBottom: 16, borderBottom: `1px solid ${theme.border}` }}>
                  <StyledInput label="Descrição" value={it.descricao} onChange={e => { const n = [...itens]; n[idx].descricao = e.target.value; setItens(n); }} />
                  <StyledInput label="NCM" inputMode="numeric" maxLength={8} value={it.ncm} onChange={e => { const n = [...itens]; n[idx].ncm = e.target.value.replace(/\D/g, "").slice(0, 8); setItens(n); }} />
                  <StyledInput label="CFOP" inputMode="numeric" maxLength={4} value={it.cfop} onChange={e => { const n = [...itens]; n[idx].cfop = e.target.value.replace(/\D/g, "").slice(0, 4); setItens(n); }} />
                  <StyledInput label="Unidade" maxLength={6} value={it.unidade} onChange={e => { const n = [...itens]; n[idx].unidade = e.target.value.toUpperCase(); setItens(n); }} />
                  <StyledInput label="CSOSN" inputMode="numeric" maxLength={4} value={it.csosn} onChange={e => { const n = [...itens]; n[idx].csosn = e.target.value.replace(/\D/g, "").slice(0, 4); setItens(n); }} />
                  <StyledInput label="Quantidade" type="number" min="0.0001" step="0.0001" value={it.quantidade} onChange={e => { const n = [...itens]; n[idx].quantidade = Number(e.target.value); setItens(n); }} />
                  <StyledInput label="Valor unitário" type="number" min="0" step="0.01" value={it.valorUnitario} onChange={e => { const n = [...itens]; n[idx].valorUnitario = Number(e.target.value); setItens(n); }} />
                  <StyledInput label="Desconto" type="number" min="0" step="0.01" value={it.valorDesconto} onChange={e => { const n = [...itens]; n[idx].valorDesconto = Number(e.target.value); setItens(n); }} />
                  <div>
                    <label style={lblStyle}>Total</label>
                    <div style={{ padding: "9px 12px", fontWeight: 700, fontSize: 13, color: theme.primary, background: theme.primaryAlpha, borderRadius: 8, borderWidth: 1, borderStyle: "solid", borderColor: theme.primaryAlpha }}>
                      {fmt((it.quantidade * it.valorUnitario) - it.valorDesconto)}
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", justifyContent: "center", paddingTop: 20 }}>
                    <button aria-label={`Remover item ${idx + 1}`} onClick={() => setItens(itens.filter(i => i.id !== it.id))} style={{ color: theme.danger, background: "none", border: "none", cursor: "pointer" }}><Trash2 size={18}/></button>
                  </div>
                </div>
              ))}
              <p style={{ color: theme.warning, fontSize: 11, lineHeight: 1.5 }}>CFOP, NCM e CSOSN devem ser confirmados pelo responsável fiscal ou contador. A tela não corrige tributação automaticamente.</p>
              
              {itens.length > 0 && (
                <div style={{ textAlign: "right", marginTop: 20, borderTopWidth: 1, borderTopStyle: "solid", borderTopColor: theme.border, paddingTop: 16 }}>
                  <span style={{ marginRight: 15, color: theme.textMuted, fontSize: 12, fontWeight: 700, textTransform: "uppercase" }}>Total Geral:</span>
                  <span style={{ fontSize: 24, fontWeight: 800, color: theme.textMain }}>{fmt(totalNota)}</span>
                </div>
              )}
            </Card>}

            {passoEmissao === 3 && <Card title="Pagamento e informações adicionais">
              <div style={{ display: "grid", gridTemplateColumns: "minmax(180px, 1fr) 2fr", gap: 16 }}>
                <div><label style={lblStyle}>Pagamento</label><select value={formaPagamento} onChange={e => setFormaPagamento(e.target.value)} style={inpStyle}>
                  <option value="PIX">Pix</option><option value="DINHEIRO">Dinheiro</option><option value="CARTAO_CREDITO">Cartão de Crédito</option>
                </select></div>
                <div><label style={lblStyle}>Informações adicionais</label><textarea maxLength={2000} rows={5} value={infoAdicionais} onChange={e => setInfoAdicionais(e.target.value)} style={{ ...inpStyle, resize: "vertical" }}/></div>
              </div>
            </Card>}

            {passoEmissao === 4 && <Card title="Revisão antes da emissão" subtitle="Confira todos os dados. A Gevyro não altera a tributação automaticamente.">
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 14, fontSize: 13 }}>
                <div><span style={lblStyle}>Documento</span><strong>{tipoNota}</strong></div>
                <div><span style={lblStyle}>Ambiente</span><strong style={{ color: configFiscal?.ambiente === "PRODUCAO" ? theme.danger : theme.warning }}>{configFiscal?.ambiente}</strong></div>
                <div><span style={lblStyle}>Destinatário</span><strong>{clienteNome || "Consumidor não identificado"}</strong></div>
                <div><span style={lblStyle}>CPF/CNPJ</span><strong>{clienteDoc || "Não informado"}</strong></div>
                <div><span style={lblStyle}>Natureza</span><strong>{naturezaOp}</strong></div>
                <div><span style={lblStyle}>Pagamento</span><strong>{formaPagamento.replaceAll("_", " ")}</strong></div>
                <div><span style={lblStyle}>Itens</span><strong>{itens.length}</strong></div>
                <div><span style={lblStyle}>Total</span><strong style={{ color: theme.primary }}>{fmt(totalNota)}</strong></div>
              </div>
            </Card>}

            <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
              <button type="button" disabled={passoEmissao === 1 || emitindo} onClick={() => setPassoEmissao(p => Math.max(1, p - 1))} style={{ ...btnStyle, opacity: passoEmissao === 1 ? .5 : 1 }}><ChevronLeft size={16}/> Voltar</button>
              {passoEmissao < 4 && <button type="button" onClick={avancarPasso} style={{ ...btnStyle, background: theme.primary, color: "#000", borderColor: theme.primary }}>Continuar <ChevronRight size={16}/></button>}
            {passoEmissao === 4 && <button disabled={emitindo || !documentoHabilitado(tipoNota)} onClick={handleEmitir} style={{ background: theme.primary, color: "#000", padding: 16, borderRadius: 12, fontSize: 14, fontWeight: 800, border: "none", cursor: emitindo || !documentoHabilitado(tipoNota) ? "not-allowed" : "pointer", opacity: !documentoHabilitado(tipoNota) ? .55 : 1, display: "flex", justifyContent: "center", alignItems: "center", gap: 8, transition: "opacity 0.2s" }}>
              {emitindo ? <Loader2 className="animate-spin" size={18} /> : <Send size={18} />} 
              {emitindo ? "PROCESSANDO SEFAZ..." : "TRANSMITIR NOTA FISCAL"}
            </button>}
            </div>
          </div>
        )}

        {/* ABA: CONFIGURAÇÃO E ROLLOUT */}
        {aba === "configuracao" && (
          !configFiscal ? <Card><div style={{ display: "flex", alignItems: "center", gap: 10, color: theme.textMuted }}><Loader2 size={18} className="animate-spin"/> Carregando configuração fiscal...</div></Card> :
          <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <Card title="Rollout por empresa" subtitle="Nenhuma emissão é liberada apenas por aparecer na interface.">
              <label style={{ display: "flex", gap: 12, alignItems: "center", padding: 14, border: `1px solid ${theme.border}`, borderRadius: 10, cursor: "pointer" }}>
                <input type="checkbox" checked={configFiscal.fiscalHabilitado} onChange={e => setConfigFiscal({ ...configFiscal,
                  fiscalHabilitado: e.target.checked,
                  ...(!e.target.checked ? { nfeHabilitada: false, nfceHabilitada: false, nfseHabilitada: false } : {})
                })}/>
                <div><strong style={{ color: theme.textMain }}>Habilitar Gevyro Fiscal</strong><div style={{ color: theme.textMuted, fontSize: 12, marginTop: 3 }}>Chave geral desta empresa. Os documentos continuam dependendo das opções abaixo.</div></div>
              </label>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 10, marginTop: 12 }}>
                {[
                  { campo: "nfeHabilitada" as const, nome: "NF-e modelo 55", bloqueado: false },
                  { campo: "nfceHabilitada" as const, nome: "NFC-e modelo 65", bloqueado: false },
                  { campo: "nfseHabilitada" as const, nome: "NFS-e Nacional", bloqueado: configFiscal.ambiente === "PRODUCAO" }
                ].map(item => <label key={item.campo} style={{ padding: 12, border: `1px solid ${theme.border}`, borderRadius: 9, opacity: !configFiscal.fiscalHabilitado || item.bloqueado ? .55 : 1, cursor: "pointer" }}>
                  <input type="checkbox" checked={configFiscal[item.campo]} disabled={!configFiscal.fiscalHabilitado || item.bloqueado}
                    onChange={e => setConfigFiscal({ ...configFiscal, [item.campo]: e.target.checked })}/>
                  <span style={{ marginLeft: 8, fontSize: 13, fontWeight: 700 }}>{item.nome}</span>
                </label>)}
              </div>
            </Card>

            <Card title="Ambiente, séries e tributação" subtitle="Homologação não produz documento fiscal com valor jurídico.">
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 14 }}>
                <div><label style={lblStyle}>Ambiente</label><select value={configFiscal.ambiente} onChange={e => {
                  const ambiente = e.target.value as ConfiguracaoFiscal["ambiente"];
                  setConfigFiscal({ ...configFiscal, ambiente, ...(ambiente === "PRODUCAO" ? { nfseHabilitada: false } : {}) });
                  setConfirmarProducao(false);
                }} style={inpStyle}><option value="HOMOLOGACAO">Homologação</option><option value="PRODUCAO">Produção</option></select></div>
                <div><label style={lblStyle}>Regime tributário</label><select value={configFiscal.regimeTributario} onChange={e => setConfigFiscal({ ...configFiscal, regimeTributario: e.target.value })} style={inpStyle}>
                  <option value="SIMPLES_NACIONAL">Simples Nacional</option><option value="SIMPLES_NACIONAL_EXCESSO">Simples — excesso de sublimite</option><option value="LUCRO_PRESUMIDO">Lucro presumido</option><option value="LUCRO_REAL">Lucro real</option>
                </select></div>
                <StyledInput label="Inscrição estadual" maxLength={20} value={configFiscal.inscricaoEstadual} onChange={e => setConfigFiscal({ ...configFiscal, inscricaoEstadual: e.target.value })}/>
                <StyledInput label="Série NF-e" inputMode="numeric" maxLength={3} value={configFiscal.serieNfe} onChange={e => setConfigFiscal({ ...configFiscal, serieNfe: e.target.value.replace(/\D/g, "") })}/>
                <StyledInput label="Série NFC-e" inputMode="numeric" maxLength={3} value={configFiscal.serieNfce} onChange={e => setConfigFiscal({ ...configFiscal, serieNfce: e.target.value.replace(/\D/g, "") })}/>
              </div>
            </Card>

            <Card title="CSC da NFC-e" subtitle="O segredo existente nunca é retornado pelo servidor.">
              <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: 14 }}>
                <StyledInput label="Identificador CSC" maxLength={20} value={configFiscal.cscId} onChange={e => setConfigFiscal({ ...configFiscal, cscId: e.target.value })}/>
                <StyledInput label={configFiscal.cscConfigurado ? "Novo CSC (deixe vazio para manter)" : "CSC"} type="password" autoComplete="new-password" maxLength={200} value={cscNovo} onChange={e => setCscNovo(e.target.value)}/>
              </div>
              <p style={{ color: configFiscal.cscConfigurado ? theme.primary : theme.textMuted, fontSize: 12, margin: "10px 0 0" }}>{configFiscal.cscConfigurado ? "CSC protegido e configurado." : "Nenhum CSC armazenado."}</p>
            </Card>

            {configFiscal.ambiente === "PRODUCAO" && <label style={{ display: "flex", gap: 10, padding: 14, borderRadius: 10, background: theme.warningAlpha, border: `1px solid rgba(245,158,11,.35)`, fontSize: 12, lineHeight: 1.5 }}>
              <input type="checkbox" checked={confirmarProducao} onChange={e => setConfirmarProducao(e.target.checked)} style={{ marginTop: 2 }}/>
              Confirmo que revisei cadastro, tributação, séries, certificado e CSC com o responsável fiscal. Entendo que produção pode gerar documentos com validade jurídica.
            </label>}

            <button onClick={salvarConfiguracao} disabled={salvandoConfig || (configFiscal.ambiente === "PRODUCAO" && !confirmarProducao)} style={{ ...btnStyle, alignSelf: "flex-end", background: theme.primary, color: "#fff", borderColor: theme.primary, opacity: salvandoConfig || (configFiscal.ambiente === "PRODUCAO" && !confirmarProducao) ? .55 : 1 }}>
              {salvandoConfig ? <Loader2 size={16} className="animate-spin"/> : <Settings size={16}/>} Salvar configuração
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
                             {(notaSelecionada.tipo === "NFE" || notaSelecionada.tipo === "NFCE") && <button onClick={() => fazerDownloadSeguro(`${API_BASE}/${notaSelecionada.id}/danfe`, `danfe-${notaSelecionada.numeroNota}.pdf`)} style={{ ...btnStyle, flex:1,justifyContent:"center",color:theme.primary,borderColor:theme.primaryAlpha }}><FileText size={16}/> DANFE</button>}
                             {notaSelecionada.tipo === "NFE" && <button onClick={() => abrirCartaCorrecao(notaSelecionada)} style={{ ...btnStyle, flex:1,justifyContent:"center",color:theme.warning,borderColor:theme.warningAlpha }}><FilePenLine size={16}/> CC-e</button>}
                             <button onClick={() => fazerDownloadSeguro(`${API_BASE}/${notaSelecionada.id}/xml`, `nf-${notaSelecionada.numeroNota}.xml`)} style={{ ...btnStyle, flex: 1, justifyContent: "center", color: theme.primary, borderColor: theme.primaryAlpha }}><Download size={16}/> XML</button>
                             <button onClick={() => handleCancelar(notaSelecionada.id)} style={{ ...btnStyle, flex: 1, justifyContent: "center", color: theme.danger, borderColor: theme.dangerAlpha, background: theme.dangerAlpha }}><Trash2 size={16}/> Cancelar</button>
                          </>
                      )}
                  </div>
              </div>
          </div>
        )}

        {notaCartaCorrecao && (
          <div onClick={fecharCartaCorrecao} style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.85)", display: "flex", justifyContent: "center", alignItems: "center", zIndex: 1200, backdropFilter: "blur(6px)" }}>
            <form onSubmit={handleCartaCorrecao} onClick={e => e.stopPropagation()} style={{ background: theme.bgCard, padding: 28, borderRadius: 18, border: `1px solid ${theme.border}`, width: 620, maxWidth: "94%", boxShadow: "0 25px 60px rgba(0,0,0,.5)" }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 16, marginBottom: 18 }}>
                <div>
                  <h2 style={{ margin: 0, fontSize: 19 }}>Carta de Correção Eletrônica</h2>
                  <p style={{ margin: "5px 0 0", color: theme.textMuted, fontSize: 12 }}>NF-e {notaCartaCorrecao.numeroNota} · o novo evento substitui as correções anteriores.</p>
                </div>
                <button type="button" disabled={enviandoCce} onClick={fecharCartaCorrecao} aria-label="Fechar" style={{ background: "none", border: 0, color: theme.textMuted, cursor: "pointer" }}><X size={22}/></button>
              </div>

              <div style={{ padding: 14, borderRadius: 10, background: theme.warningAlpha, border: `1px solid rgba(245,158,11,.28)`, color: theme.textMain, fontSize: 12, lineHeight: 1.55, marginBottom: 18 }}>
                <strong style={{ color: theme.warning }}>A CC-e não pode alterar:</strong>
                <ul style={{ margin: "7px 0 0", paddingLeft: 18 }}>
                  <li>base de cálculo, alíquota, preço, quantidade ou valor da operação;</li>
                  <li>dados que mudem a identidade do remetente ou destinatário;</li>
                  <li>data de emissão ou de saída.</li>
                </ul>
              </div>

              <label htmlFor="texto-cce" style={lblStyle}>Correção a considerar</label>
              <textarea id="texto-cce" autoFocus required minLength={15} maxLength={1000} value={textoCartaCorrecao} onChange={e => setTextoCartaCorrecao(e.target.value)} disabled={enviandoCce} placeholder="Descreva objetivamente o dado incorreto e a informação correta..." style={{ ...inpStyle, minHeight: 130, resize: "vertical", fontFamily: "inherit", lineHeight: 1.5 }} />
              <div style={{ textAlign: "right", color: textoCartaCorrecao.trim().length > 1000 ? theme.danger : theme.textMuted, fontSize: 11, marginTop: 5 }}>{textoCartaCorrecao.trim().length}/1000</div>

              <label style={{ display: "flex", alignItems: "flex-start", gap: 10, marginTop: 14, color: theme.textMuted, fontSize: 12, lineHeight: 1.45, cursor: "pointer" }}>
                <input type="checkbox" checked={cienteLimitesCce} onChange={e => setCienteLimitesCce(e.target.checked)} disabled={enviandoCce} style={{ marginTop: 2 }} />
                Confirmo que revisei as restrições acima e que esta correção não altera valores tributários, partes ou datas fiscais.
              </label>

              <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 24 }}>
                <button type="button" onClick={fecharCartaCorrecao} disabled={enviandoCce} style={btnStyle}>Voltar</button>
                <button type="submit" disabled={enviandoCce || !cienteLimitesCce || textoCartaCorrecao.trim().length < 15 || textoCartaCorrecao.trim().length > 1000} style={{ ...btnStyle, background: theme.warning, borderColor: theme.warning, color: "#111827", opacity: enviandoCce || !cienteLimitesCce || textoCartaCorrecao.trim().length < 15 ? .55 : 1, cursor: enviandoCce ? "wait" : "pointer" }}>
                  {enviandoCce ? <Loader2 size={16} className="animate-spin"/> : <Send size={16}/>} Registrar na SEFAZ
                </button>
              </div>
            </form>
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
