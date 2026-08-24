"use client";

import React, { useEffect, useState, useMemo, useCallback } from "react";
import Link from "next/link";
import { useEmpresa } from "../context/Empresacontext";
import { fetchAuth as fetchApi, getUsuario, type Usuario } from "@/lib/api-v2";
import {
  Plus, X, Check, Search, ChevronRight, Store, Smartphone,
  DollarSign, CreditCard, ShoppingBag, Truck, CheckCircle2,
  Clock, Ban, Edit2, Package, Trash2, AlertTriangle,
  Tag, MapPin, Wallet, ChevronDown, Link2, Link2Off,
  Zap, Star, RefreshCw, TrendingUp, Lock
} from "lucide-react";
import { toast } from "sonner";
import { prepararSomDeVenda } from "@/lib/som-venda";

interface Produto { id:number; nome:string; preco:number; quantidadeEstoque:number; categoria?:string }
interface Cliente { id:number; nome:string; ativo?:boolean }
interface ItemCarrinho { produto:Produto; quantidade:number }
interface ItemPedidoDTO { idProduto:number; nomeProduto:string; quantidade:number; precoUnitario:number; subtotal:number }
interface Pedido {
  id:number; empresaId:number; nomeEmpresa:string; nomeCliente?:string;
  itens:ItemPedidoDTO[]; valorTotal:number; desconto:number; valorFinal:number; custoFrete:number;
  formaPagamento:string; canalVenda:string; status:string; contaDestino?:string;
  enderecoEntrega?:string; dataPedido:string; dataAtualizacao:string;
  observacao?:string; motivoCancelamento?:string;
}
interface MarketplaceConnection {
  id:number; marketplace:"SHOPEE"|"MERCADO_LIVRE"; sellerId:string;
  active:boolean; tokenExpiresAt?:string; createdAt:string;
}
interface MarketplaceProductLink {
  id:number; produtoId:number; nomeProduto:string;
  anuncioId:string; anuncioTitulo?:string; marketplace:"SHOPEE"|"MERCADO_LIVRE";
}
type FormaPagamento = "PIX"|"DINHEIRO"|"CARTAO_DEBITO"|"CARTAO_CREDITO";
type CanalVenda     = "WHATSAPP"|"INSTAGRAM"|"MERCADO_LIVRE"|"SHOPEE"|"IFOOD"|"TELEFONE"|"OUTRO";
type StatusPedido   = "PENDENTE"|"CONFIRMADO"|"ENVIADO"|"ENTREGUE"|"CANCELADO";
type MarketplaceKey = "SHOPEE"|"MERCADO_LIVRE";

const STATUS_META: Record<StatusPedido,{label:string;color:string;bg:string;border:string;icon:React.ReactNode}> = {
  PENDENTE:   {label:"Pendente",   color:"#f59e0b", bg:"rgba(245,158,11,0.12)", border:"rgba(245,158,11,0.4)",  icon:<Clock size={12}/>},
  CONFIRMADO: {label:"Confirmado", color:"#80a99b", bg:"rgba(16,185,129,0.12)", border:"rgba(16,185,129,0.4)",  icon:<CheckCircle2 size={12}/>},
  ENVIADO:    {label:"Enviado",    color:"#3b82f6", bg:"rgba(59,130,246,0.12)", border:"rgba(59,130,246,0.4)",  icon:<Truck size={12}/>},
  ENTREGUE:   {label:"Entregue",   color:"#10b981", bg:"rgba(16,185,129,0.18)", border:"rgba(16,185,129,0.5)",  icon:<Check size={12}/>},
  CANCELADO:  {label:"Cancelado",  color:"#ef4444", bg:"rgba(239,68,68,0.12)",  border:"rgba(239,68,68,0.4)",   icon:<Ban size={12}/>},
};
const STATUS_TRANSICOES: Record<StatusPedido, StatusPedido[]> = {
  PENDENTE: ["CONFIRMADO"],
  CONFIRMADO: ["ENVIADO", "ENTREGUE"],
  ENVIADO: ["ENTREGUE"],
  ENTREGUE: [],
  CANCELADO: [],
};

const FORMAS: {value:FormaPagamento;label:string;icon:React.ReactNode}[] = [
  {value:"PIX",label:"Pix",icon:<Smartphone size={13}/>},
  {value:"DINHEIRO",label:"Dinheiro",icon:<DollarSign size={13}/>},
  {value:"CARTAO_DEBITO",label:"Débito",icon:<CreditCard size={13}/>},
  {value:"CARTAO_CREDITO",label:"Crédito",icon:<CreditCard size={13}/>},
];

const CANAIS: { value: CanalVenda; label: string; icon: React.ReactNode }[] = [
  { value: "WHATSAPP",     label: "WhatsApp",     icon: <img src="https://cdn.jsdelivr.net/gh/MartnsDev/Icons@main/whatsapp.svg"      alt="WPP"    width={14} height={14}/> },
  { value: "INSTAGRAM",    label: "Instagram",    icon: <img src="https://cdn.jsdelivr.net/gh/MartnsDev/Icons@main/instagram.svg"     alt="IG"     width={14} height={14}/> },
  { value: "MERCADO_LIVRE",label: "Mercado Livre",icon: <img src="https://cdn.jsdelivr.net/gh/MartnsDev/Icons@main/mercadolivre.svg"  alt="ML"     width={14} height={14}/> },
  { value: "SHOPEE",       label: "Shopee",       icon: <img src="https://cdn.jsdelivr.net/gh/MartnsDev/Icons@main/shopee.svg"        alt="Shopee" width={14} height={14}/> },
  { value: "IFOOD",        label: "iFood",        icon: <img src="https://cdn.jsdelivr.net/gh/MartnsDev/Icons@main/ifood.svg"         alt="iFood"  width={14} height={14}/> },
  { value: "TELEFONE",     label: "Telefone",     icon: <img src="https://cdn.jsdelivr.net/gh/MartnsDev/Icons@main/call.svg"          alt="Tel"    width={14} height={14}/> },
  { value: "OUTRO",        label: "Outro",        icon: <Package size={14}/> },
];

const FORMA_LABEL: Record<string,string> = {
  PIX:"Pix", DINHEIRO:"Dinheiro", CARTAO_DEBITO:"Débito", CARTAO_CREDITO:"Crédito",
};

const MARKETPLACE_META: Record<MarketplaceKey,{label:string;icon:React.ReactNode;color:string;bg:string;border:string}> = {
  SHOPEE:        {label:"Shopee",        icon:<img src="https://cdn.jsdelivr.net/gh/MartnsDev/Icons@main/shopee.svg"       alt="Shopee" width={22} height={22}/>, color:"#993C1D", bg:"#FAECE7", border:"#F0997B"},
  MERCADO_LIVRE: {label:"Mercado Livre", icon:<img src="https://cdn.jsdelivr.net/gh/MartnsDev/Icons@main/mercadolivre.svg" alt="ML"     width={22} height={22}/>, color:"#854F0B", bg:"#FAEEDA", border:"#EF9F27"},
};

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "https://gestpro-backend-production.up.railway.app";

/**
 * Monta a URL de autorização OAuth de cada marketplace.
 * O `state` carrega o empresaId para o backend identificar no callback.
 *
 * Shopee:        https://partner.shopeemobile.com/api/v2/shop/auth_partner
 * Mercado Livre: https://auth.mercadolivre.com.br/authorization
 */
function buildOAuthUrl(marketplace: MarketplaceKey, empresaId: number): string {
  const callbackBase = `${API_URL}/api/v1/marketplace/callback`;
  const state        = `empresaId=${empresaId}`;

  if (marketplace === "SHOPEE") {
    // Shopee exige partner_id e redirect_url na query string.
    // O partner_id é público — pode ficar no env do frontend.
    const partnerId   = process.env.NEXT_PUBLIC_SHOPEE_PARTNER_ID ?? "";
    const redirectUrl = encodeURIComponent(`${callbackBase}/shopee`);
    return `https://partner.shopeemobile.com/api/v2/shop/auth_partner?partner_id=${encodeURIComponent(partnerId)}&redirect=${redirectUrl}&state=${encodeURIComponent(state)}`;
  }

  // Mercado Livre
  const clientId     = process.env.NEXT_PUBLIC_ML_CLIENT_ID ?? "";
  const redirectUri  = encodeURIComponent(`${callbackBase}/mercadolivre`);
  return `https://auth.mercadolivre.com.br/authorization?response_type=code&client_id=${encodeURIComponent(clientId)}&redirect_uri=${redirectUri}&state=${encodeURIComponent(state)}`;
}

const fmt = (v?:number|null) =>
  new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(v??0);
const centavos = (valor:number) => Math.round((valor + Number.EPSILON) * 100) / 100;

const fmtData = (s?:any) => {
  if(!s) return "—";
  const d = Array.isArray(s)
    ? new Date(Date.UTC(s[0],s[1]-1,s[2],s[3]??0,s[4]??0))
    : new Date(typeof s==="string"?s.replace(" ","T"):s);
  return isNaN(d.getTime())?"—"
    :d.toLocaleString("pt-BR",{day:"2-digit",month:"2-digit",year:"numeric",hour:"2-digit",minute:"2-digit"});
};

async function fetchAuth<T>(path:string,opts?:RequestInit):Promise<T> {
  const res=await fetchApi(path,opts);
  if(!res.ok){const e=await res.json().catch(()=>null);throw new Error(e?.mensagem??e?.erro??`Erro ${res.status}`);}
  if(res.status===204) return undefined as unknown as T;
  return res.json();
}

const inp:React.CSSProperties={width:"100%",padding:"8px 11px",background:"var(--surface-overlay)",border:"1px solid var(--border)",borderRadius:8,color:"var(--foreground)",fontSize:13,outline:"none"};
const btnP:React.CSSProperties={display:"flex",alignItems:"center",gap:6,padding:"9px 16px",background:"var(--primary)",border:"none",borderRadius:8,color:"#fff",fontSize:13,fontWeight:600,cursor:"pointer"};
const btnG:React.CSSProperties={display:"flex",alignItems:"center",gap:6,padding:"7px 11px",background:"transparent",border:"1px solid var(--border)",borderRadius:8,color:"var(--foreground-muted)",fontSize:12,cursor:"pointer"};
const btnDanger:React.CSSProperties={display:"flex",alignItems:"center",gap:6,padding:"7px 11px",background:"transparent",border:"1px solid rgba(239,68,68,0.35)",borderRadius:8,color:"var(--destructive)",fontSize:12,cursor:"pointer"};

function StatusBadge({status}:{status:string}) {
  const m=STATUS_META[status as StatusPedido]??{label:status,color:"var(--foreground-muted)",bg:"var(--surface-overlay)",icon:null};
  return <span style={{display:"inline-flex",alignItems:"center",gap:4,fontSize:11,padding:"2px 8px",borderRadius:99,fontWeight:600,color:m.color,background:m.bg}}>{m.icon} {m.label}</span>;
}
function CanalBadge({canal}:{canal:string}) {
  const m=CANAIS.find(c=>c.value===canal)??{label:canal,icon:<Package size={12}/>};
  return <span style={{fontSize:11,padding:"2px 8px",borderRadius:99,fontWeight:500,background:"var(--surface-overlay)",color:"var(--foreground-muted)",border:"1px solid var(--border)",display:"inline-flex",alignItems:"center",gap:4}}>{m.icon} {m.label}</span>;
}
function AutoBadge() {
  return (
    <span style={{display:"inline-flex",alignItems:"center",gap:3,fontSize:10,padding:"1px 7px",borderRadius:99,fontWeight:600,background:"rgba(59,130,246,0.1)",color:"#3b82f6",border:"1px solid rgba(59,130,246,0.25)"}}>
      <Zap size={9}/> automático
    </span>
  );
}

function SeletorStatus({statusAtual,onChange,salvando}:{
  statusAtual:StatusPedido; onChange:(s:StatusPedido)=>void; salvando:boolean;
}) {
  const [open,setOpen]=useState(false);
  const meta=STATUS_META[statusAtual];
  return (
    <div style={{position:"relative"}}>
      <button onClick={()=>!salvando&&setOpen(v=>!v)} disabled={salvando}
        style={{display:"flex",alignItems:"center",gap:6,padding:"8px 13px",background:meta.bg,border:`1px solid ${meta.border}`,borderRadius:9,cursor:salvando?"not-allowed":"pointer",color:meta.color,fontSize:12,fontWeight:700,opacity:salvando?0.7:1,transition:"all .15s"}}>
        {meta.icon}
        {salvando?"Salvando...":meta.label}
        <ChevronDown size={11} style={{marginLeft:2,transform:open?"rotate(180deg)":"none",transition:"transform .15s"}}/>
      </button>
      {open&&(
        <>
          <div style={{position:"fixed",inset:0,zIndex:99}} onClick={()=>setOpen(false)}/>
          <div style={{position:"absolute",top:"calc(100% + 6px)",left:0,zIndex:100,background:"var(--surface-elevated)",border:"1px solid var(--border)",borderRadius:11,overflow:"hidden",minWidth:185,boxShadow:"0 10px 32px rgba(0,0,0,0.32)"}}>
            <div style={{padding:"6px 10px",borderBottom:"1px solid var(--border)",fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".07em"}}>Alterar status</div>
            {STATUS_TRANSICOES[statusAtual].map(s=>{
              const sm=STATUS_META[s]; const ativo=s===statusAtual;
              return (
                <button key={s} onClick={()=>{onChange(s);setOpen(false);}}
                  style={{width:"100%",display:"flex",alignItems:"center",gap:9,padding:"10px 14px",border:"none",cursor:"pointer",background:ativo?sm.bg:"transparent",color:ativo?sm.color:"var(--foreground)",fontSize:13,fontWeight:ativo?700:400,borderLeft:ativo?`3px solid ${sm.color}`:"3px solid transparent",transition:"background .1s"}}
                  onMouseEnter={e=>(e.currentTarget as HTMLButtonElement).style.background=ativo?sm.bg:"var(--surface-overlay)"}
                  onMouseLeave={e=>(e.currentTarget as HTMLButtonElement).style.background=ativo?sm.bg:"transparent"}>
                  {sm.icon} {sm.label}
                  {ativo&&<Check size={12} style={{marginLeft:"auto"}} color={sm.color}/>}
                </button>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}

function SeletorForma({value,onChange}:{value:FormaPagamento;onChange:(v:FormaPagamento)=>void}) {
  return (
    <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:5}}>
      {FORMAS.map(f=>(
        <button key={f.value} onClick={()=>onChange(f.value)} style={{display:"flex",alignItems:"center",gap:5,padding:"7px 8px",background:value===f.value?"var(--primary-muted)":"var(--surface-overlay)",border:`1px solid ${value===f.value?"var(--primary)":"var(--border)"}`,borderRadius:7,cursor:"pointer",color:value===f.value?"var(--primary)":"var(--foreground-muted)",fontSize:11,fontWeight:value===f.value?600:400}}>
          {f.icon} {f.label}
        </button>
      ))}
    </div>
  );
}

function ModalConfirmarExclusao({titulo,descricao,onConfirmar,onCancelar,confirmando}:{
  titulo:string;descricao:string;onConfirmar:()=>void;onCancelar:()=>void;confirmando:boolean;
}) {
  return (
    <div style={{position:"fixed",inset:0,background:"rgba(0,0,0,0.8)",backdropFilter:"blur(4px)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:200,padding:16}}>
      <div className="animate-fade-in" style={{background:"var(--surface-elevated)",border:"1px solid rgba(239,68,68,0.3)",borderRadius:14,padding:28,width:"100%",maxWidth:380,textAlign:"center"}}>
        <div style={{width:52,height:52,borderRadius:"50%",background:"rgba(239,68,68,0.1)",display:"flex",alignItems:"center",justifyContent:"center",margin:"0 auto 16px"}}>
          <AlertTriangle size={24} color="var(--destructive)"/>
        </div>
        <h3 style={{fontSize:16,fontWeight:700,color:"var(--foreground)",margin:"0 0 8px"}}>{titulo}</h3>
        <p style={{fontSize:13,color:"var(--foreground-muted)",margin:"0 0 24px",lineHeight:1.5}}>{descricao}</p>
        <div style={{display:"flex",gap:10}}>
          <button onClick={onCancelar} style={{...btnG,flex:1,justifyContent:"center"}}>Cancelar</button>
          <button onClick={onConfirmar} disabled={confirmando} style={{flex:2,padding:"9px 0",background:"var(--destructive)",color:"#fff",border:"none",borderRadius:8,fontSize:13,fontWeight:700,cursor:"pointer",display:"flex",alignItems:"center",justifyContent:"center",gap:6,opacity:confirmando?0.7:1}}>
            <Trash2 size={14}/> {confirmando?"Removendo...":"Confirmar Exclusão"}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * Redireciona o lojista para a página oficial de autorização OAuth do marketplace.
 * O backend recebe o callback em:
 *   Shopee:        GET /api/v1/marketplace/callback/shopee?code=...&shop_id=...&state=empresaId=X
 *   Mercado Livre: GET /api/v1/marketplace/callback/mercadolivre?code=...&state=empresaId=X
 * Após salvar o token, o backend redireciona para o frontend com ?sucesso=true ou ?erro=...
 */
function ModalConectarMarketplace({empresaId,marketplace,onClose}:{
  empresaId:number; marketplace:MarketplaceKey; onClose:()=>void;
}) {
  const meta = MARKETPLACE_META[marketplace];

  const irParaOAuth = () => {
    const url = buildOAuthUrl(marketplace, empresaId);
    // Redireciona na mesma aba — o backend devolve para /dashboard/pedidos?sucesso=true
    window.location.href = url;
  };

  return (
    <div style={{position:"fixed",inset:0,background:"rgba(0,0,0,0.8)",backdropFilter:"blur(4px)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:200,padding:16}}
      onClick={e=>e.target===e.currentTarget&&onClose()}>
      <div className="animate-fade-in" style={{background:"var(--surface-elevated)",border:"1px solid var(--border)",borderRadius:14,padding:28,width:"100%",maxWidth:440}}>
        <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:20}}>
          <div style={{display:"flex",alignItems:"center",gap:10}}>
            <span style={{display:"flex",alignItems:"center",justifyContent:"center",width:24}}>{meta.icon}</span>
            <div>
              <h3 style={{fontSize:15,fontWeight:700,color:"var(--foreground)",margin:0}}>Conectar {meta.label}</h3>
              <p style={{fontSize:12,color:"var(--foreground-muted)",margin:0}}>Integração segura via OAuth</p>
            </div>
          </div>
          <button onClick={onClose} style={{...btnG,padding:6,border:"none"}}><X size={16}/></button>
        </div>

        <div style={{display:"flex",flexDirection:"column",gap:16}}>
          <div style={{padding:16,background:"var(--surface-overlay)",border:"1px solid var(--border-subtle)",borderRadius:10,fontSize:13,color:"var(--foreground-muted)",lineHeight:1.6}}>
            <p style={{fontWeight:600,color:"var(--foreground)",marginBottom:10,display:"flex",alignItems:"center",gap:6}}>
              <Lock size={14} color="var(--primary)"/> Como funciona:
            </p>
            <ol style={{margin:0,paddingLeft:18,display:"flex",flexDirection:"column",gap:8}}>
              <li>Você será redirecionado para a página oficial da <strong>{meta.label}</strong>.</li>
              <li>Faça login na sua conta de lojista.</li>
              <li>Clique em <strong>"Autorizar"</strong> quando solicitado.</li>
              <li>Você voltará automaticamente ao Gevyro com a conta conectada.</li>
            </ol>
          </div>

          <div style={{display:"flex",gap:8,marginTop:4}}>
            <button onClick={onClose} style={{...btnG,flex:1,justifyContent:"center"}}>Cancelar</button>
            <button onClick={irParaOAuth}
              style={{...btnP,flex:2,justifyContent:"center",background:meta.color}}>
              <Link2 size={14}/> Autorizar no {meta.label}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function ModalVincularProduto({empresaId,marketplace,onClose,onSucesso}:{
  empresaId:number; marketplace:MarketplaceKey;
  onClose:()=>void; onSucesso:(l:MarketplaceProductLink)=>void;
}) {
  const meta = MARKETPLACE_META[marketplace];
  const [produtos,setProdutos]    = useState<Produto[]>([]);
  const [busca,setBusca]          = useState("");
  const [produtoId,setProdutoId]  = useState<number|null>(null);
  const [anuncioId,setAnuncioId]  = useState("");
  const [anuncioTitulo,setTitulo] = useState("");
  const [salvando,setSalvando]    = useState(false);

  useEffect(()=>{
    fetchAuth<Produto[]>(`/api/v1/produtos?empresaId=${empresaId}`)
      .then(setProdutos).catch(()=>toast.error("Erro ao carregar produtos"));
  },[empresaId]);

  const filtrados = useMemo(()=>
    produtos.filter(p=>p.nome.toLowerCase().includes(busca.toLowerCase())),[produtos,busca]);

  const vincular = async() => {
    if(!produtoId||!anuncioId.trim()){toast.error("Selecione um produto e informe o ID do anúncio.");return;}
    setSalvando(true);
    try{
      const link = await fetchAuth<MarketplaceProductLink>(
        `/api/v1/marketplace/empresa/${empresaId}/vinculos`,
        {method:"POST",body:JSON.stringify({produtoId,marketplace,anuncioId,anuncioTitulo:anuncioTitulo||null})}
      );
      onSucesso(link);
      toast.success("Produto vinculado com sucesso!");
      onClose();
    }catch(e:any){toast.error(e.message);}
    finally{setSalvando(false);}
  };

  const produtoSelecionado = produtos.find(p=>p.id===produtoId);

  return (
    <div style={{position:"fixed",inset:0,background:"rgba(0,0,0,0.8)",backdropFilter:"blur(4px)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:200,padding:16}}
      onClick={e=>e.target===e.currentTarget&&onClose()}>
      <div className="animate-fade-in" style={{background:"var(--surface-elevated)",border:"1px solid var(--border)",borderRadius:14,width:"100%",maxWidth:520,maxHeight:"90vh",display:"flex",flexDirection:"column",overflow:"hidden"}}>
        <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",padding:"16px 20px",borderBottom:"1px solid var(--border)"}}>
          <div style={{display:"flex",alignItems:"center",gap:10}}>
            <span style={{display:"flex",alignItems:"center",width:24}}>{meta.icon}</span>
            <h3 style={{fontSize:15,fontWeight:700,color:"var(--foreground)",margin:0}}>Vincular produto — {meta.label}</h3>
          </div>
          <button onClick={onClose} style={{...btnG,padding:6,border:"none"}}><X size={16}/></button>
        </div>

        <div style={{flex:1,overflowY:"auto",padding:"16px 20px",display:"flex",flexDirection:"column",gap:14}}>
          <div>
            <label style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".06em",display:"block",marginBottom:6}}>1. Produto no Gevyro</label>
            <div style={{position:"relative",marginBottom:6}}>
              <Search size={12} style={{position:"absolute",left:9,top:"50%",transform:"translateY(-50%)",color:"var(--foreground-subtle)"}}/>
              <input style={{...inp,paddingLeft:28}} placeholder="Buscar produto..." value={busca} onChange={e=>setBusca(e.target.value)}/>
            </div>
            <div style={{maxHeight:180,overflowY:"auto",border:"1px solid var(--border)",borderRadius:8,background:"var(--surface-overlay)"}}>
              {filtrados.length===0
                ?<div style={{padding:16,textAlign:"center",fontSize:12,color:"var(--foreground-muted)"}}>Nenhum produto encontrado</div>
                :filtrados.map(p=>(
                  <div key={p.id} onClick={()=>setProdutoId(p.id)}
                    style={{display:"flex",justifyContent:"space-between",alignItems:"center",padding:"9px 12px",cursor:"pointer",borderBottom:"1px solid var(--border-subtle)",background:produtoId===p.id?"var(--primary-muted)":"transparent"}}
                    onMouseEnter={e=>produtoId!==p.id&&((e.currentTarget as HTMLDivElement).style.background="var(--surface-elevated)")}
                    onMouseLeave={e=>produtoId!==p.id&&((e.currentTarget as HTMLDivElement).style.background="transparent")}>
                    <div>
                      <p style={{fontSize:13,fontWeight:500,color:produtoId===p.id?"var(--primary)":"var(--foreground)",margin:0}}>{p.nome}</p>
                      <p style={{fontSize:11,color:"var(--foreground-muted)",margin:"1px 0 0"}}>Estoque: {p.quantidadeEstoque}</p>
                    </div>
                    {produtoId===p.id&&<Check size={14} color="var(--primary)"/>}
                  </div>
                ))}
            </div>
          </div>

          <div>
            <label style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".06em",display:"block",marginBottom:6}}>2. ID do anúncio no {meta.label}</label>
            <input style={{...inp,marginBottom:8}} value={anuncioId} onChange={e=>setAnuncioId(e.target.value)}
              placeholder={marketplace==="SHOPEE"?"Ex: 123456789":"Ex: MLB1234567890"}/>
            <label style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".06em",display:"block",marginBottom:4}}>Nome do anúncio (opcional)</label>
            <input style={inp} value={anuncioTitulo} onChange={e=>setTitulo(e.target.value)}
              placeholder="Ex: Tomada Macho Margirius 2P+T"/>
          </div>

          {produtoSelecionado&&anuncioId&&(
            <div style={{padding:"10px 12px",background:"var(--surface-overlay)",borderRadius:8,fontSize:12,display:"flex",alignItems:"center",gap:8,flexWrap:"wrap"}}>
              <span style={{color:"var(--foreground-muted)"}}>{produtoSelecionado.nome}</span>
              <Link2 size={12} color="var(--primary)"/>
              <span style={{color:"var(--foreground-muted)",display:"flex",alignItems:"center",gap:6}}>
                {React.cloneElement(meta.icon as React.ReactElement<{width?:number;height?:number}>,{width:14,height:14})} {anuncioId}
              </span>
            </div>
          )}
        </div>

        <div style={{padding:"12px 20px",borderTop:"1px solid var(--border)",display:"flex",gap:8}}>
          <button onClick={onClose} style={{...btnG,flex:1,justifyContent:"center"}}>Cancelar</button>
          <button onClick={vincular} disabled={salvando||!produtoId||!anuncioId}
            style={{...btnP,flex:2,justifyContent:"center",opacity:salvando||!produtoId||!anuncioId?0.6:1}}>
            {salvando?"Salvando...":<><Link2 size={14}/> Criar vínculo</>}
          </button>
        </div>
      </div>
    </div>
  );
}

function SecaoPremiumLock() {
  return (
    <div className="animate-fade-in" style={{display:"flex",flexDirection:"column",gap:0,borderRadius:14,overflow:"hidden",border:"1px solid var(--border)"}}>
      <div style={{position:"relative",padding:"44px 32px 36px",background:"linear-gradient(135deg,#1a1a2e 0%,#16213e 50%,#0f3460 100%)",overflow:"hidden",textAlign:"center"}}>
        <div style={{position:"absolute",top:-40,left:-40,width:180,height:180,borderRadius:"50%",background:"rgba(255,111,0,0.18)",filter:"blur(48px)",pointerEvents:"none"}}/>
        <div style={{position:"absolute",bottom:-40,right:-40,width:180,height:180,borderRadius:"50%",background:"rgba(234,179,8,0.15)",filter:"blur(48px)",pointerEvents:"none"}}/>
        <div style={{position:"absolute",top:"50%",left:"50%",transform:"translate(-50%,-50%)",width:320,height:320,borderRadius:"50%",background:"rgba(59,130,246,0.07)",filter:"blur(60px)",pointerEvents:"none"}}/>
        <div style={{display:"flex",alignItems:"center",justifyContent:"center",gap:16,marginBottom:24,position:"relative",zIndex:1}}>
          <div style={{width:64,height:64,borderRadius:14,overflow:"hidden",background:"#fff",display:"flex",alignItems:"center",justifyContent:"center",boxShadow:"0 8px 24px rgba(0,0,0,0.4)",border:"2px solid rgba(255,255,255,0.15)"}}>
            <img src="https://cdn.jsdelivr.net/gh/MartnsDev/Icons@main/mercadolivre.svg" alt="Mercado Livre" style={{width:40,height:40}}/>
          </div>
          <div style={{display:"flex",alignItems:"center",gap:4,position:"relative"}}>
            <div style={{width:8,height:8,borderRadius:"50%",background:"rgba(255,255,255,0.3)"}}/>
            <div style={{width:24,height:2,background:"linear-gradient(90deg,rgba(255,255,255,0.15),rgba(255,255,255,0.5),rgba(255,255,255,0.15))",borderRadius:2}}/>
            <div style={{width:24,height:24,borderRadius:"50%",background:"linear-gradient(135deg,#FACC15,#F59E0B)",display:"flex",alignItems:"center",justifyContent:"center",fontSize:12,boxShadow:"0 0 16px rgba(250,204,21,0.5)",color:"#000"}}>
              <Zap size={14}/>
            </div>
            <div style={{width:24,height:2,background:"linear-gradient(90deg,rgba(255,255,255,0.15),rgba(255,255,255,0.5),rgba(255,255,255,0.15))",borderRadius:2}}/>
            <div style={{width:8,height:8,borderRadius:"50%",background:"rgba(255,255,255,0.3)"}}/>
          </div>
          <div style={{width:64,height:64,borderRadius:14,overflow:"hidden",background:"#fff",display:"flex",alignItems:"center",justifyContent:"center",boxShadow:"0 8px 24px rgba(0,0,0,0.4)",border:"2px solid rgba(255,255,255,0.15)"}}>
            <img src="https://cdn.jsdelivr.net/gh/MartnsDev/Icons@main/shopee.svg" alt="Shopee" style={{width:40,height:40}}/>
          </div>
        </div>
        <div style={{display:"inline-flex",alignItems:"center",gap:5,padding:"4px 12px",borderRadius:99,background:"rgba(250,204,21,0.15)",border:"1px solid rgba(250,204,21,0.35)",marginBottom:12,position:"relative",zIndex:1}}>
          <span style={{fontSize:11,color:"#FACC15",fontWeight:700,letterSpacing:".04em"}}>★ RECURSO PREMIUM</span>
        </div>
        <h2 style={{fontSize:22,fontWeight:800,color:"#fff",margin:"0 0 10px",lineHeight:1.2,position:"relative",zIndex:1}}>
          Venda em todo lugar,<br/>
          <span style={{background:"linear-gradient(90deg,#FACC15,#F97316)",WebkitBackgroundClip:"text",WebkitTextFillColor:"transparent"}}>gerencie em um só lugar</span>
        </h2>
        <p style={{fontSize:13,color:"rgba(255,255,255,0.6)",margin:0,lineHeight:1.6,maxWidth:400,marginLeft:"auto",marginRight:"auto",position:"relative",zIndex:1}}>
          Conecte Shopee e Mercado Livre ao Gevyro e esqueça o trabalho manual.
        </p>
      </div>
      <div style={{padding:"24px 24px 0",background:"var(--surface-elevated)"}}>
        <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:10}}>
          {[
            {icon:<Package size={22} color="var(--primary)"/>,title:"Estoque automático",desc:"Cada venda debita o estoque sem você tocar em nada"},
            {icon:<TrendingUp size={22} color="#3b82f6"/>,title:"Lucro em tempo real",desc:"Faturamento e margens calculados na hora da venda"},
            {icon:<Zap size={22} color="#eab308"/>,title:"Zero digitação",desc:"Pedidos entram com todos os dados preenchidos"},
            {icon:<Link2 size={22} color="#8b5cf6"/>,title:"Multi-canal",desc:"Shopee, Mercado Livre e vendas manuais no mesmo painel"},
          ].map((b,i)=>(
            <div key={i} style={{padding:"14px",background:"var(--surface-overlay)",borderRadius:10,border:"1px solid var(--border-subtle)"}}>
              <div style={{marginBottom:10}}>{b.icon}</div>
              <p style={{fontSize:12,fontWeight:700,color:"var(--foreground)",margin:"0 0 3px"}}>{b.title}</p>
              <p style={{fontSize:11,color:"var(--foreground-muted)",margin:0,lineHeight:1.5}}>{b.desc}</p>
            </div>
          ))}
        </div>
      </div>
      <div style={{padding:"24px",background:"var(--surface-elevated)",display:"flex",flexDirection:"column",alignItems:"center",gap:10}}>
        <Link href="/dashboard/planos" style={{display:"inline-flex",alignItems:"center",justifyContent:"center",gap:8,width:"100%",maxWidth:320,padding:"13px 0",background:"linear-gradient(135deg,#FACC15,#F97316)",border:"none",borderRadius:10,color:"#7C2D00",fontSize:14,fontWeight:800,cursor:"pointer",textDecoration:"none",boxShadow:"0 6px 20px rgba(249,115,22,0.4)",transition:"transform .15s, box-shadow .15s"}}
          onMouseEnter={e=>{(e.currentTarget as HTMLAnchorElement).style.transform="translateY(-2px)";(e.currentTarget as HTMLAnchorElement).style.boxShadow="0 8px 24px rgba(249,115,22,0.5)";}}
          onMouseLeave={e=>{(e.currentTarget as HTMLAnchorElement).style.transform="translateY(0)";(e.currentTarget as HTMLAnchorElement).style.boxShadow="0 6px 20px rgba(249,115,22,0.4)";}}
        >
          <Star size={16} style={{fill:"#7C2D00"}}/> Assinar Premium agora
        </Link>
        <p style={{fontSize:11,color:"var(--foreground-subtle)",margin:0,textAlign:"center"}}>
          Shopee e Mercado Livre incluídos em todos os planos Premium
        </p>
      </div>
    </div>
  );
}

/**
 * Detecta retorno OAuth via query string (?sucesso=true ou ?erro=...) e exibe toast.
 * Isso é necessário porque o backend redireciona para /dashboard/pedidos após o callback.
 */
function SecaoIntegracoes({empresaId}:{empresaId:number}) {
  const [conexoes,setConexoes]           = useState<MarketplaceConnection[]>([]);
  const [vinculos,setVinculos]           = useState<MarketplaceProductLink[]>([]);
  const [loading,setLoading]             = useState(true);
  const [modalConectar,setModalConectar] = useState<MarketplaceKey|null>(null);
  const [modalVincular,setModalVincular] = useState<MarketplaceKey|null>(null);
  const [desconectando,setDesconectando] = useState<MarketplaceKey|null>(null);
  const [confirmDesconectar,setConfirmDesc] = useState<MarketplaceKey|null>(null);
  const [removendoVinculo,setRemovendo]  = useState<number|null>(null);

  // Detecta retorno do OAuth e exibe feedback
  useEffect(()=>{
    if(typeof window === "undefined") return;
    const params = new URLSearchParams(window.location.search);
    if(params.get("sucesso")==="true"){
      toast.success("Marketplace conectado com sucesso!");
      // Remove o query param da URL sem recarregar a página
      window.history.replaceState({}, "", window.location.pathname);
    } else if(params.get("erro")){
      toast.error("Falha na integração. Tente novamente.");
      window.history.replaceState({}, "", window.location.pathname);
    }
  },[]);

  const carregarConexoes = useCallback(async()=>{
    setLoading(true);
    try{
      const conns = await fetchAuth<MarketplaceConnection[]>(`/api/v1/marketplace/empresa/${empresaId}/conexoes`);
      setConexoes(conns);
      const allLinks: MarketplaceProductLink[] = [];
      for(const conn of conns){
        try{
          const links = await fetchAuth<MarketplaceProductLink[]>(
            `/api/v1/marketplace/empresa/${empresaId}/vinculos?marketplace=${conn.marketplace}`
          );
          allLinks.push(...links);
        }catch{}
      }
      setVinculos(allLinks);
    }catch{toast.error("Erro ao carregar integrações");}
    finally{setLoading(false);}
  },[empresaId]);

  useEffect(()=>{carregarConexoes();},[carregarConexoes]);

  const desconectar = async(mk:MarketplaceKey) => {
    setDesconectando(mk);
    try{
      await fetchAuth(`/api/v1/marketplace/empresa/${empresaId}/desconectar?marketplace=${mk}`,{method:"DELETE"});
      setConexoes(prev=>prev.filter(c=>c.marketplace!==mk));
      setVinculos(prev=>prev.filter(v=>v.marketplace!==mk));
      toast.success(`${MARKETPLACE_META[mk].label} desconectado.`);
    }catch(e:any){toast.error(e.message);}
    finally{setDesconectando(null);setConfirmDesc(null);}
  };

  const removerVinculo = async(id:number) => {
    setRemovendo(id);
    try{
      await fetchAuth(`/api/v1/marketplace/empresa/${empresaId}/vinculos/${id}`,{method:"DELETE"});
      setVinculos(prev=>prev.filter(v=>v.id!==id));
      toast.success("Vínculo removido.");
    }catch(e:any){toast.error(e.message);}
    finally{setRemovendo(null);}
  };

  const MARKETPLACES: MarketplaceKey[] = ["MERCADO_LIVRE","SHOPEE"];

  return (
    <>
      <div className="animate-fade-in" style={{background:"var(--surface-elevated)",border:"1px solid var(--border)",borderRadius:12,overflow:"hidden"}}>
        <div style={{padding:"14px 18px",borderBottom:"1px solid var(--border)",display:"flex",alignItems:"center",justifyContent:"space-between",flexWrap:"wrap",gap:8}}>
          <div style={{display:"flex",alignItems:"center",gap:8}}>
            <Zap size={15} color="var(--primary)"/>
            <span style={{fontSize:14,fontWeight:700,color:"var(--foreground)"}}>Integrações</span>
            <span style={{fontSize:11,padding:"2px 8px",borderRadius:99,background:"rgba(234,179,8,0.15)",color:"#854F0B",fontWeight:600,border:"1px solid rgba(234,179,8,0.3)"}}>★ Premium</span>
          </div>
          <span style={{fontSize:12,color:"var(--foreground-muted)"}}>Pedidos chegam automaticamente quando você vende nos marketplaces</span>
        </div>

        {loading
          ?<div style={{padding:24,textAlign:"center",fontSize:13,color:"var(--foreground-muted)"}}>Carregando integrações...</div>
          :<div style={{padding:16,display:"flex",flexDirection:"column",gap:12}}>
            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:10}}>
              {MARKETPLACES.map(mk=>{
                const meta  = MARKETPLACE_META[mk];
                const conn  = conexoes.find(c=>c.marketplace===mk);
                const ativo = !!conn?.active;
                const links = vinculos.filter(v=>v.marketplace===mk);
                return (
                  <div key={mk} style={{border:`1px solid ${ativo?meta.border:"var(--border)"}`,borderRadius:10,padding:14,background:ativo?meta.bg:"var(--surface-overlay)",transition:"all .2s"}}>
                    <div style={{display:"flex",alignItems:"center",justifyContent:"space-between",marginBottom:10}}>
                      <div style={{display:"flex",alignItems:"center",gap:8}}>
                        <span style={{display:"flex",alignItems:"center",width:24}}>{meta.icon}</span>
                        <div>
                          <p style={{fontSize:13,fontWeight:700,color:ativo?meta.color:"var(--foreground)",margin:0}}>{meta.label}</p>
                          <p style={{fontSize:11,margin:0,display:"flex",alignItems:"center",gap:4,color:ativo?meta.color:"var(--foreground-muted)"}}>
                            <span style={{width:6,height:6,borderRadius:"50%",background:ativo?"#1D9E75":"var(--foreground-subtle)",display:"inline-block"}}/>
                            {ativo?"Conectado":"Não conectado"}
                          </p>
                        </div>
                      </div>
                    </div>
                    {ativo?(
                      <>
                        <div style={{fontSize:11,color:meta.color,marginBottom:10,padding:"4px 8px",background:"rgba(255,255,255,0.4)",borderRadius:6}}>
                          {links.length} produto{links.length!==1?"s":""} vinculado{links.length!==1?"s":""}
                        </div>
                        <div style={{display:"flex",gap:6}}>
                          <button onClick={()=>setModalVincular(mk)}
                            style={{...btnG,flex:1,justifyContent:"center",fontSize:11,padding:"6px 8px",color:meta.color,borderColor:meta.border}}>
                            <Link2 size={11}/> Vincular
                          </button>
                          <button onClick={()=>setConfirmDesc(mk)}
                            style={{...btnG,padding:"6px 9px",fontSize:11,color:"var(--foreground-subtle)",borderColor:"var(--border)"}}>
                            <Link2Off size={11}/>
                          </button>
                        </div>
                      </>
                    ):(
                      <button onClick={()=>setModalConectar(mk)}
                        style={{width:"100%",padding:"8px 0",background:"var(--surface-elevated)",border:"1px solid var(--border)",borderRadius:7,fontSize:12,color:"var(--foreground-muted)",cursor:"pointer",display:"flex",alignItems:"center",justifyContent:"center",gap:5}}>
                        <Plus size={12}/> Conectar
                      </button>
                    )}
                  </div>
                );
              })}
            </div>

            {vinculos.length>0&&(
              <div>
                <p style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".06em",margin:"4px 0 8px"}}>Vínculos ativos</p>
                <div style={{display:"flex",flexDirection:"column",gap:5}}>
                  {vinculos.map(v=>{
                    const meta=MARKETPLACE_META[v.marketplace];
                    return (
                      <div key={v.id} style={{display:"flex",alignItems:"center",gap:8,padding:"8px 10px",background:"var(--surface-overlay)",borderRadius:8,border:"1px solid var(--border-subtle)"}}>
                        <span style={{display:"flex",alignItems:"center",width:16}}>{React.cloneElement(meta.icon as React.ReactElement<{width?:number;height?:number}>,{width:14,height:14})}</span>
                        <div style={{flex:1,minWidth:0}}>
                          <p style={{fontSize:12,fontWeight:500,color:"var(--foreground)",margin:0,overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap"}}>{v.nomeProduto}</p>
                          <p style={{fontSize:11,color:"var(--foreground-muted)",margin:"1px 0 0"}}>
                            <Link2 size={9} style={{display:"inline",marginRight:3}}/>{v.anuncioTitulo||v.anuncioId}
                          </p>
                        </div>
                        <button onClick={()=>removerVinculo(v.id)} disabled={removendoVinculo===v.id}
                          style={{background:"none",border:"none",cursor:"pointer",color:"var(--foreground-subtle)",padding:4,opacity:removendoVinculo===v.id?0.4:1}}>
                          <X size={12}/>
                        </button>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        }
      </div>

      {modalConectar&&(
        <ModalConectarMarketplace
          empresaId={empresaId}
          marketplace={modalConectar}
          onClose={()=>setModalConectar(null)}
        />
      )}
      {modalVincular&&(
        <ModalVincularProduto
          empresaId={empresaId}
          marketplace={modalVincular}
          onClose={()=>setModalVincular(null)}
          onSucesso={link=>{setVinculos(prev=>[...prev,link]);}}
        />
      )}
      {confirmDesconectar&&(
        <ModalConfirmarExclusao
          titulo={`Desconectar ${MARKETPLACE_META[confirmDesconectar].label}?`}
          descricao="Os vínculos de produtos serão mantidos, mas novos pedidos deixarão de ser importados automaticamente."
          onConfirmar={()=>desconectar(confirmDesconectar)}
          onCancelar={()=>setConfirmDesc(null)}
          confirmando={desconectando===confirmDesconectar}
        />
      )}
    </>
  );
}

function ModalNovoPedido({empresaId,onClose,onSucesso}:{
  empresaId:number;onClose:()=>void;onSucesso:(p:Pedido)=>void;
}) {
  const [produtos,setProdutos]=useState<Produto[]>([]);
  const [clientes,setClientes]=useState<Cliente[]>([]);
  const [clienteId,setClienteId]=useState("");
  const [carrinho,setCarrinho]=useState<ItemCarrinho[]>([]);
  const [busca,setBusca]=useState("");
  const [forma,setForma]=useState<FormaPagamento>("PIX");
  const [canal,setCanal]=useState<CanalVenda>("OUTRO");
  const [contaDestino,setContaDestino]=useState("");
  const [tipoDesconto,setTipoDesconto]=useState<"REAIS"|"PORCENTAGEM">("REAIS");
  const [desconto,setDesconto]=useState("");
  const [frete,setFrete]=useState("");
  const [endereco,setEndereco]=useState("");
  const [observacao,setObservacao]=useState("");
  const [salvando,setSalvando]=useState(false);

  useEffect(()=>{
    let ignore=false;
    Promise.all([
      fetchAuth<Produto[]>(`/api/v1/produtos?empresaId=${empresaId}`),
      fetchAuth<Cliente[]>(`/api/v1/clientes?empresaId=${empresaId}&tipo=CLIENTE`),
    ]).then(([listaProdutos,listaClientes])=>{
      if(ignore)return;
      setProdutos(listaProdutos);
      setClientes(listaClientes.filter(cliente=>cliente.ativo!==false));
    }).catch(()=>{if(!ignore)toast.error("Erro ao carregar produtos e clientes");});
    return()=>{ignore=true;};
  },[empresaId]);

  const filtrados=useMemo(()=>
    produtos.filter(p=>p.quantidadeEstoque>0&&p.nome.toLowerCase().includes(busca.toLowerCase())),
  [produtos,busca]);

  const addItem=(p:Produto)=>setCarrinho(prev=>{
    const ex=prev.find(i=>i.produto.id===p.id);
    if(ex){
      if(ex.quantidade>=p.quantidadeEstoque){toast.error(`Máx: ${p.quantidadeEstoque}`);return prev;}
      return prev.map(i=>i.produto.id===p.id?{...i,quantidade:i.quantidade+1}:i);
    }
    return [...prev,{produto:p,quantidade:1}];
  });

  const setQtd=(id:number,q:number)=>{
    if(q<=0) setCarrinho(prev=>prev.filter(i=>i.produto.id!==id));
    else setCarrinho(prev=>prev.map(i=>i.produto.id===id
      ?{...i,quantidade:Math.min(q,i.produto.quantidadeEstoque)}:i));
  };

  const subtotal    = centavos(carrinho.reduce((s,i)=>s+i.produto.preco*i.quantidade,0));
  const descontoRaw = parseFloat(desconto.replace(",","."))||0;
  const descontoN   = centavos(tipoDesconto==="PORCENTAGEM"
    ? Math.min(subtotal*(descontoRaw/100),subtotal)
    : Math.max(0,descontoRaw));
  const freteN  = centavos(Math.max(0,parseFloat(frete.replace(",","."))||0));
  const total   = centavos(Math.max(subtotal-descontoN+freteN,0));

  const registrar=async()=>{
    if(salvando)return;
    if(!carrinho.length){toast.error("Adicione pelo menos um produto.");return;}
    if(subtotal<=0){toast.error("O pedido precisa ter valor maior que zero.");return;}
    if(!Number.isFinite(descontoRaw)||descontoRaw<0){toast.error("Informe um desconto válido.");return;}
    if(tipoDesconto==="PORCENTAGEM"&&descontoRaw>100){toast.error("O desconto percentual não pode passar de 100%.");return;}
    if(descontoN>subtotal){toast.error("O desconto não pode ser maior que o subtotal.");return;}
    if(!Number.isFinite(freteN)){toast.error("Informe um frete válido.");return;}
    const itemInvalido=carrinho.find(i=>i.quantidade<1||i.quantidade>i.produto.quantidadeEstoque);
    if(itemInvalido){toast.error(`Revise a quantidade de ${itemInvalido.produto.nome}.`);return;}
    if(contaDestino.trim().length>100){toast.error("A conta de destino deve ter até 100 caracteres.");return;}
    if(endereco.trim().length>300){toast.error("O endereço deve ter até 300 caracteres.");return;}
    if(observacao.trim().length>500){toast.error("A observação deve ter até 500 caracteres.");return;}
    const tocarSomDeVenda = prepararSomDeVenda();
    setSalvando(true);
    try{
      const pedido=await fetchAuth<Pedido>(`/api/v1/pedidos/empresa/${empresaId}`,{
        method:"POST",
        body:JSON.stringify({
          formaPagamento:forma,canalVenda:canal,
          idCliente:clienteId?Number(clienteId):null,
          contaDestino:contaDestino.trim()||null,desconto:descontoN,
          custoFrete:freteN,enderecoEntrega:endereco.trim()||null,
          observacao:observacao.trim()||null,
          itens:carrinho.map(i=>({idProduto:i.produto.id,quantidade:i.quantidade})),
        }),
      });
      tocarSomDeVenda();
      onClose();onSucesso(pedido);
    }catch(e:any){toast.error(e.message);}
    finally{setSalvando(false);}
  };

  return(
    <div style={{position:"fixed",inset:0,background:"rgba(0,0,0,0.75)",backdropFilter:"blur(4px)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:50,padding:16}}
      onClick={e=>e.target===e.currentTarget&&onClose()}>
      <div className="animate-fade-in pedidos-modal" style={{background:"var(--surface-elevated)",border:"1px solid var(--border)",borderRadius:14,width:"100%",maxWidth:900,maxHeight:"95vh",display:"flex",flexDirection:"column",overflow:"hidden"}}>
        <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",padding:"14px 20px",borderBottom:"1px solid var(--border)"}}>
          <div style={{display:"flex",alignItems:"center",gap:10}}>
            <ShoppingBag size={16} color="var(--primary)"/>
            <h2 style={{fontSize:15,fontWeight:700,color:"var(--foreground)",margin:0}}>Novo Pedido</h2>
          </div>
          <button onClick={onClose} style={{...btnG,padding:6,border:"none"}}><X size={16}/></button>
        </div>

        <div className="pedidos-modal-grid" style={{display:"grid",gridTemplateColumns:"1fr 360px",flex:1,minHeight:0,overflow:"hidden"}}>
          <div style={{display:"flex",flexDirection:"column",borderRight:"1px solid var(--border)",overflow:"hidden"}}>
            <div style={{padding:"10px 16px",borderBottom:"1px solid var(--border)"}}>
              <div style={{position:"relative"}}>
                <Search size={12} style={{position:"absolute",left:9,top:"50%",transform:"translateY(-50%)",color:"var(--foreground-subtle)"}}/>
                <input style={{...inp,paddingLeft:28}} placeholder="Buscar produto..." value={busca} onChange={e=>setBusca(e.target.value)} autoFocus/>
              </div>
            </div>
            <div style={{overflowY:"auto",flex:1}}>
              {filtrados.length===0
                ?<div style={{padding:28,textAlign:"center",color:"var(--foreground-subtle)",fontSize:13}}>Nenhum produto disponível</div>
                :filtrados.map(p=>{
                  const nc=carrinho.find(i=>i.produto.id===p.id);
                  return(
                    <div key={p.id} onClick={()=>addItem(p)}
                      style={{display:"flex",alignItems:"center",justifyContent:"space-between",padding:"10px 16px",cursor:"pointer",borderBottom:"1px solid var(--border-subtle)"}}
                      onMouseEnter={e=>((e.currentTarget as HTMLDivElement).style.background="var(--surface-overlay)")}
                      onMouseLeave={e=>((e.currentTarget as HTMLDivElement).style.background="transparent")}>
                      <div>
                        <p style={{fontSize:13,fontWeight:500,color:"var(--foreground)",margin:0}}>{p.nome}</p>
                        <p style={{fontSize:11,color:"var(--foreground-muted)",margin:"2px 0 0"}}>
                          Estoque: {p.quantidadeEstoque}{p.categoria?` · ${p.categoria}`:""}
                        </p>
                      </div>
                      <div style={{display:"flex",alignItems:"center",gap:8,flexShrink:0}}>
                        <span style={{fontSize:13,fontWeight:600,color:"var(--primary)"}}>{fmt(p.preco)}</span>
                        {nc&&<span style={{fontSize:11,background:"var(--primary-muted)",color:"var(--primary)",padding:"2px 7px",borderRadius:99,fontWeight:600}}>{nc.quantidade}×</span>}
                        <Plus size={13} color="var(--foreground-muted)"/>
                      </div>
                    </div>
                  );
                })}
            </div>
          </div>

          <div style={{display:"flex",flexDirection:"column",overflow:"hidden"}}>
            <div style={{flex:1,overflowY:"auto",padding:"10px 13px"}}>
              <p style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".07em",marginBottom:8}}>Carrinho ({carrinho.length})</p>
              {carrinho.length===0
                ?<div style={{textAlign:"center",color:"var(--foreground-subtle)",fontSize:12,padding:"16px 0"}}>Clique nos produtos →</div>
                :carrinho.map(item=>(
                  <div key={item.produto.id} style={{marginBottom:7,padding:"8px 9px",background:"var(--surface-overlay)",borderRadius:7,border:"1px solid var(--border-subtle)"}}>
                    <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:5}}>
                      <p style={{fontSize:12,fontWeight:500,color:"var(--foreground)",margin:0,flex:1,paddingRight:6}}>{item.produto.nome}</p>
                      <button onClick={()=>setCarrinho(prev=>prev.filter(i=>i.produto.id!==item.produto.id))} style={{background:"none",border:"none",cursor:"pointer",color:"var(--foreground-subtle)",padding:0}}><X size={11}/></button>
                    </div>
                    <div style={{display:"flex",alignItems:"center",justifyContent:"space-between"}}>
                      <div style={{display:"flex",alignItems:"center",gap:5}}>
                        <button onClick={()=>setQtd(item.produto.id,item.quantidade-1)} style={{width:20,height:20,borderRadius:5,background:"var(--surface-elevated)",border:"1px solid var(--border)",cursor:"pointer",color:"var(--foreground)",display:"flex",alignItems:"center",justifyContent:"center",fontSize:13}}>−</button>
                        <span style={{fontSize:13,fontWeight:600,minWidth:18,textAlign:"center"}}>{item.quantidade}</span>
                        <button onClick={()=>setQtd(item.produto.id,item.quantidade+1)} disabled={item.quantidade>=item.produto.quantidadeEstoque} style={{width:20,height:20,borderRadius:5,background:"var(--primary-muted)",border:"1px solid rgba(16,185,129,0.3)",cursor:"pointer",color:"var(--primary)",display:"flex",alignItems:"center",justifyContent:"center",fontSize:13,opacity:item.quantidade>=item.produto.quantidadeEstoque?0.4:1}}>+</button>
                      </div>
                      <span style={{fontSize:13,fontWeight:600}}>{fmt(item.produto.preco*item.quantidade)}</span>
                    </div>
                  </div>
                ))}
            </div>

            <div style={{padding:"10px 13px",borderTop:"1px solid var(--border)",display:"flex",flexDirection:"column",gap:9,overflowY:"auto"}}>
              <div style={{background:"var(--surface-overlay)",borderRadius:8,padding:"8px 11px",display:"flex",flexDirection:"column",gap:4}}>
                <div style={{display:"flex",justifyContent:"space-between",fontSize:12,color:"var(--foreground-muted)"}}><span>Subtotal</span><span>{fmt(subtotal)}</span></div>
                {descontoN>0&&<div style={{display:"flex",justifyContent:"space-between",fontSize:12,color:"var(--destructive)"}}><span>Desconto{tipoDesconto==="PORCENTAGEM"?` (${descontoRaw}%)`:""}</span><span>− {fmt(descontoN)}</span></div>}
                {freteN>0&&<div style={{display:"flex",justifyContent:"space-between",fontSize:12,color:"var(--foreground-muted)"}}><span>Frete</span><span>+ {fmt(freteN)}</span></div>}
                <div style={{display:"flex",justifyContent:"space-between",fontSize:15,fontWeight:700,color:"var(--primary)",paddingTop:4,borderTop:"1px solid var(--border)"}}><span>Total</span><span>{fmt(total)}</span></div>
              </div>
              <div>
                <label style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".06em",display:"block",marginBottom:5}}>Canal de Venda</label>
                <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:4}}>
                  {CANAIS.map(c=>(
                    <button key={c.value} onClick={()=>setCanal(c.value as CanalVenda)} style={{padding:"6px 8px",fontSize:11,display:"flex",alignItems:"center",gap:5,background:canal===c.value?"var(--primary-muted)":"var(--surface-overlay)",border:`1px solid ${canal===c.value?"var(--primary)":"var(--border)"}`,borderRadius:7,cursor:"pointer",color:canal===c.value?"var(--primary)":"var(--foreground-muted)",fontWeight:canal===c.value?600:400}}>
                      {c.icon} {c.label}
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <label style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".06em",display:"block",marginBottom:5}}>Pagamento</label>
                <SeletorForma value={forma} onChange={setForma}/>
              </div>
              <div>
                <label style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".06em",display:"block",marginBottom:4}}>Cliente</label>
                <select style={{...inp,cursor:"pointer"}} value={clienteId} onChange={e=>setClienteId(e.target.value)}>
                  <option value="">Consumidor não identificado</option>
                  {clientes.map(cliente=><option key={cliente.id} value={cliente.id}>{cliente.nome}</option>)}
                </select>
              </div>
              <div>
                <label style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".06em",display:"block",marginBottom:4}}>Conta Destino</label>
                <input style={inp} maxLength={100} value={contaDestino} onChange={e=>setContaDestino(e.target.value)} placeholder="Ex: Mercado Pago, Nubank..."/>
              </div>
              <div>
                <label style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".06em",display:"block",marginBottom:4}}>Desconto</label>
                <div style={{display:"flex",gap:6}}>
                  <div style={{display:"flex",border:"1px solid var(--border)",borderRadius:8,overflow:"hidden",flexShrink:0}}>
                    {(["REAIS","PORCENTAGEM"] as ("REAIS"|"PORCENTAGEM")[]).map(t=>(
                      <button key={t} onClick={()=>{setTipoDesconto(t);setDesconto("");}} style={{padding:"7px 10px",fontSize:11,fontWeight:600,cursor:"pointer",border:"none",background:tipoDesconto===t?"var(--primary)":"var(--surface-overlay)",color:tipoDesconto===t?"#fff":"var(--foreground-muted)"}}>
                        {t==="REAIS"?"R$":"%"}
                      </button>
                    ))}
                  </div>
                  <input style={{...inp,flex:1}} type="number" min="0"
                    step={tipoDesconto==="PORCENTAGEM"?"1":"0.01"}
                    max={tipoDesconto==="PORCENTAGEM"?"100":undefined}
                    value={desconto} onChange={e=>setDesconto(e.target.value)}
                    placeholder={tipoDesconto==="PORCENTAGEM"?"0 – 100":"0,00"}/>
                </div>
              </div>
              <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:6}}>
                <div>
                  <label style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".06em",display:"block",marginBottom:4}}>Frete R$</label>
                  <input style={inp} type="number" min="0" step="0.01" value={frete} onChange={e=>setFrete(e.target.value)} placeholder="0,00"/>
                </div>
                <div>
                  <label style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".06em",display:"block",marginBottom:4}}>Observação</label>
                  <input style={inp} maxLength={500} value={observacao} onChange={e=>setObservacao(e.target.value)} placeholder="Opcional..."/>
                </div>
              </div>
              <div>
                <label style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".06em",display:"block",marginBottom:4}}>Endereço de Entrega</label>
                <input style={inp} maxLength={300} value={endereco} onChange={e=>setEndereco(e.target.value)} placeholder="Rua, número, bairro, cidade..."/>
              </div>
              <button onClick={registrar} disabled={salvando||!carrinho.length}
                style={{...btnP,justifyContent:"center",padding:"11px 0",opacity:salvando||!carrinho.length?0.6:1}}>
                {salvando?"Registrando...":<><ShoppingBag size={14}/> Registrar Pedido · {fmt(total)}</>}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function DetalhePedido({pedido,onClose,onAtualizado,onRemovido}:{
  pedido:Pedido; onClose:()=>void;
  onAtualizado:(p:Pedido)=>void; onRemovido:(id:number)=>void;
}) {
  const [cancelando,setCancelando]=useState(false);
  const [motivo,setMotivo]=useState("");
  const [editandoObs,setEditandoObs]=useState(false);
  const [novaObs,setNovaObs]=useState(pedido.observacao??"");
  const [salvando,setSalvando]=useState(false);
  const [confirmandoRemocao,setConfirmandoRemocao]=useState(false);
  const [removendo,setRemovendo]=useState(false);

  const podeCancelar = pedido.status!=="CANCELADO"&&pedido.status!=="ENTREGUE";
  const pctDesconto  = pedido.valorTotal>0&&pedido.desconto>0
    ? ((pedido.desconto/pedido.valorTotal)*100).toFixed(1) : null;
  const isAutomatico = pedido.observacao?.startsWith("Pedido automático");

  const mudarStatus=async(novoStatus:StatusPedido)=>{
    if(novoStatus===pedido.status) return;
    if(!STATUS_TRANSICOES[pedido.status as StatusPedido]?.includes(novoStatus)){
      toast.error("Essa mudança de status não é permitida.");return;
    }
    setSalvando(true);
    try{
      const updated=await fetchAuth<Pedido>(`/api/v1/pedidos/${pedido.id}/status`,{
        method:"PATCH",body:JSON.stringify({status:novoStatus}),
      });
      onAtualizado(updated);
      toast.success(`Status: ${STATUS_META[novoStatus].label}`);
    }catch(e:any){toast.error(e.message);}
    finally{setSalvando(false);}
  };

  const cancelar=async()=>{
    if(salvando)return;
    if(motivo.trim().length>300){toast.error("O motivo deve ter até 300 caracteres.");return;}
    setSalvando(true);
    try{
      const updated=await fetchAuth<Pedido>(`/api/v1/pedidos/${pedido.id}/cancelar`,{
        method:"POST",body:JSON.stringify({motivo:motivo.trim()}),
      });
      onAtualizado(updated);setCancelando(false);
      toast.success("Pedido cancelado. Estoque devolvido.");
    }catch(e:any){toast.error(e.message);}
    finally{setSalvando(false);}
  };

  const salvarObs=async()=>{
    if(salvando)return;
    if(novaObs.trim().length>500){toast.error("A observação deve ter até 500 caracteres.");return;}
    setSalvando(true);
    try{
      const updated=await fetchAuth<Pedido>(`/api/v1/pedidos/${pedido.id}/observacao`,{
        method:"PATCH",body:JSON.stringify({observacao:novaObs.trim()}),
      });
      onAtualizado(updated);setEditandoObs(false);toast.success("Observação salva!");
    }catch(e:any){toast.error(e.message);}
    finally{setSalvando(false);}
  };

  const remover=async()=>{
    if(removendo)return;
    if(pedido.status!=="ENTREGUE"&&pedido.status!=="CANCELADO"){
      toast.error("Finalize ou cancele o pedido antes de removê-lo.");return;
    }
    setRemovendo(true);
    try{
      await fetchAuth(`/api/v1/pedidos/${pedido.id}`,{method:"DELETE"});
      onRemovido(pedido.id);onClose();toast.success(`Pedido #${pedido.id} removido.`);
    }catch(e:any){toast.error(e.message);}
    finally{setRemovendo(false);setConfirmandoRemocao(false);}
  };

  return(
    <>
      <div style={{position:"fixed",inset:0,background:"rgba(0,0,0,0.75)",backdropFilter:"blur(4px)",display:"flex",alignItems:"center",justifyContent:"center",zIndex:50,padding:16}}
        onClick={e=>e.target===e.currentTarget&&onClose()}>
        <div className="animate-fade-in" style={{background:"var(--surface-elevated)",border:"1px solid var(--border)",borderRadius:14,padding:24,width:"100%",maxWidth:460,maxHeight:"92vh",overflowY:"auto",display:"flex",flexDirection:"column",gap:14}}>
          <div style={{display:"flex",justifyContent:"space-between",alignItems:"flex-start"}}>
            <div>
              <div style={{display:"flex",alignItems:"center",gap:8,marginBottom:4}}>
                <h2 style={{fontSize:16,fontWeight:700,color:"var(--foreground)",margin:0}}>Pedido #{pedido.id}</h2>
                {isAutomatico&&<AutoBadge/>}
              </div>
              <p style={{fontSize:11,color:"var(--foreground-muted)",margin:0}}>{fmtData(pedido.dataPedido)}</p>
            </div>
            <button onClick={onClose} style={{...btnG,padding:5,border:"none"}}><X size={16}/></button>
          </div>

          <div style={{display:"flex",alignItems:"center",gap:10,padding:"11px 13px",background:"var(--surface-overlay)",borderRadius:10,flexWrap:"wrap"}}>
            <span style={{fontSize:12,color:"var(--foreground-muted)",fontWeight:500,flexShrink:0}}>Status do pedido:</span>
            {pedido.status==="CANCELADO"||pedido.status==="ENTREGUE"
              ?<StatusBadge status={pedido.status}/>
              :<SeletorStatus statusAtual={pedido.status as StatusPedido} onChange={mudarStatus} salvando={salvando}/>
            }
          </div>

          <div style={{display:"flex",gap:7,flexWrap:"wrap"}}>
            <CanalBadge canal={pedido.canalVenda}/>
            <span style={{fontSize:11,padding:"2px 8px",borderRadius:99,fontWeight:500,background:"var(--surface-overlay)",color:"var(--foreground-muted)",border:"1px solid var(--border)"}}>
              💳 {FORMA_LABEL[pedido.formaPagamento]??pedido.formaPagamento}
            </span>
            {pedido.contaDestino&&(
              <span style={{fontSize:11,padding:"2px 8px",borderRadius:99,background:"rgba(59,130,246,0.08)",color:"#3b82f6",border:"1px solid rgba(59,130,246,0.2)",fontWeight:500}}>
                <Wallet size={10} style={{display:"inline",marginRight:3}}/>{pedido.contaDestino}
              </span>
            )}
          </div>

          <div style={{background:"var(--surface-overlay)",borderRadius:10,padding:"12px 14px"}}>
            <p style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".07em",margin:"0 0 10px"}}>Produtos</p>
            <div style={{display:"flex",flexDirection:"column",gap:8}}>
              {pedido.itens.map((item,i)=>(
                <div key={i} style={{display:"flex",justifyContent:"space-between",alignItems:"center"}}>
                  <div>
                    <p style={{fontSize:13,color:"var(--foreground)",margin:0,fontWeight:500}}>{item.nomeProduto}</p>
                    <p style={{fontSize:11,color:"var(--foreground-muted)",margin:"1px 0 0"}}>{item.quantidade}× {fmt(item.precoUnitario)}</p>
                  </div>
                  <span style={{fontSize:13,fontWeight:600,color:"var(--foreground)"}}>{fmt(item.subtotal)}</span>
                </div>
              ))}
            </div>
          </div>

          <div style={{background:"var(--surface-overlay)",borderRadius:10,padding:"12px 14px",display:"flex",flexDirection:"column",gap:6}}>
            <div style={{display:"flex",justifyContent:"space-between",fontSize:12,color:"var(--foreground-muted)"}}><span>Subtotal</span><span>{fmt(pedido.valorTotal)}</span></div>
            {pedido.desconto>0&&(
              <div style={{display:"flex",justifyContent:"space-between",fontSize:12,color:"var(--destructive)"}}>
                <span style={{display:"flex",alignItems:"center",gap:5}}><Tag size={11}/>Desconto
                  {pctDesconto&&<span style={{fontSize:10,background:"rgba(239,68,68,0.1)",padding:"1px 6px",borderRadius:99,fontWeight:600}}>{pctDesconto}%</span>}
                </span>
                <span>− {fmt(pedido.desconto)}</span>
              </div>
            )}
            {pedido.custoFrete>0&&(
              <div style={{display:"flex",justifyContent:"space-between",fontSize:12,color:"var(--foreground-muted)"}}>
                <span style={{display:"flex",alignItems:"center",gap:5}}><Truck size={11}/>Frete</span>
                <span>+ {fmt(pedido.custoFrete)}</span>
              </div>
            )}
            <div style={{display:"flex",justifyContent:"space-between",fontSize:15,fontWeight:700,color:"var(--primary)",paddingTop:6,borderTop:"1px solid var(--border)",marginTop:2}}>
              <span>Total</span><span>{fmt(pedido.valorFinal)}</span>
            </div>
          </div>

          {pedido.enderecoEntrega&&(
            <div style={{display:"flex",gap:8,padding:"10px 12px",background:"var(--surface-overlay)",borderRadius:9,fontSize:12,color:"var(--foreground-muted)"}}>
              <MapPin size={14} style={{flexShrink:0,marginTop:1}} color="#3b82f6"/>
              <span>{pedido.enderecoEntrega}</span>
            </div>
          )}

          <div>
            {editandoObs
              ?<div style={{display:"flex",gap:6}}>
                <input style={{...inp,flex:1}} maxLength={500} value={novaObs} onChange={e=>setNovaObs(e.target.value)} placeholder="Observação..." autoFocus/>
                <button onClick={salvarObs} disabled={salvando} style={{...btnP,padding:"7px 12px"}}><Check size={13}/></button>
                <button onClick={()=>setEditandoObs(false)} style={{...btnG,padding:"7px 10px"}}><X size={13}/></button>
              </div>
              :<div style={{display:"flex",alignItems:"center",gap:8}}>
                <p style={{fontSize:12,color:"var(--foreground-muted)",margin:0,flex:1,fontStyle:pedido.observacao?"normal":"italic"}}>
                  {pedido.observacao||"Sem observação"}
                </p>
                {pedido.status!=="CANCELADO"&&(
                  <button onClick={()=>setEditandoObs(true)} style={{...btnG,padding:"4px 8px"}}><Edit2 size={12}/></button>
                )}
              </div>}
          </div>

          {pedido.motivoCancelamento&&(
            <div style={{padding:"10px 12px",background:"rgba(239,68,68,0.06)",border:"1px solid rgba(239,68,68,0.2)",borderRadius:9,fontSize:12,color:"var(--destructive)"}}>
              <strong>Motivo:</strong> {pedido.motivoCancelamento}
            </div>
          )}

          {podeCancelar&&!cancelando&&!editandoObs&&(
            <button onClick={()=>setCancelando(true)} style={{...btnDanger,justifyContent:"center"}}>
              <Ban size={13}/> Cancelar pedido
            </button>
          )}

          {cancelando&&(
            <div style={{display:"flex",flexDirection:"column",gap:8,padding:12,background:"rgba(239,68,68,0.06)",border:"1px solid rgba(239,68,68,0.2)",borderRadius:9}}>
              <p style={{fontSize:12,color:"var(--destructive)",fontWeight:600,margin:0}}>Confirmar cancelamento?</p>
              <p style={{fontSize:11,color:"var(--foreground-muted)",margin:0}}>O estoque será devolvido automaticamente.</p>
              <input style={inp} maxLength={300} value={motivo} onChange={e=>setMotivo(e.target.value)} placeholder="Motivo (opcional)..."/>
              <div style={{display:"flex",gap:7}}>
                <button onClick={()=>setCancelando(false)} style={{...btnG,flex:1,justifyContent:"center"}}>Voltar</button>
                <button onClick={cancelar} disabled={salvando} style={{flex:2,padding:"8px 0",background:"var(--destructive)",color:"#fff",border:"none",borderRadius:8,fontSize:13,fontWeight:600,cursor:"pointer",opacity:salvando?0.7:1,display:"flex",alignItems:"center",justifyContent:"center",gap:6}}>
                  <Ban size={13}/> Confirmar
                </button>
              </div>
            </div>
          )}

          {(pedido.status==="ENTREGUE"||pedido.status==="CANCELADO")&&(
            <div style={{borderTop:"1px solid var(--border)",paddingTop:12,marginTop:4}}>
              <button onClick={()=>setConfirmandoRemocao(true)} style={{...btnDanger,width:"100%",justifyContent:"center",fontSize:12}}>
                <Trash2 size={13}/> Remover do histórico
              </button>
            </div>
          )}
        </div>
      </div>

      {confirmandoRemocao&&(
        <ModalConfirmarExclusao
          titulo="Remover pedido do histórico?"
          descricao={`O pedido #${pedido.id} será permanentemente removido. Esta ação não pode ser desfeita.`}
          onConfirmar={remover}
          onCancelar={()=>setConfirmandoRemocao(false)}
          confirmando={removendo}
        />
      )}
    </>
  );
}

export default function Pedidos() {
  const {empresaAtiva}               = useEmpresa();
  const [usuario,setUsuario]         = useState<Usuario|null>(null);
  const isPremium                    = usuario?.tipoPlano === "PREMIUM";

  useEffect(()=>{
    getUsuario().then(setUsuario).catch(()=>{});
  },[]);

  const [pedidos,setPedidos]             = useState<Pedido[]>([]);
  const [loading,setLoading]             = useState(false);
  const [modalNovo,setModalNovo]         = useState(false);
  const [detalhe,setDetalhe]             = useState<Pedido|null>(null);
  const [filtroStatus,setFiltroStatus]   = useState<string>("TODOS");
  const [busca,setBusca]                 = useState("");
  const [confirmandoLimpar,setConfirmandoLimpar] = useState(false);
  const [limpando,setLimpando]           = useState(false);
  const [abaAtiva,setAbaAtiva]           = useState<"pedidos"|"integracoes">("pedidos");

  const abrirIntegracoes=()=>{
    setAbaAtiva("integracoes");
    toast.info("As integrações estão em desenvolvimento e aprimoramento contínuo.",{
      description:"Se encontrar qualquer erro, acesse a Central de Suporte.",
      action:{label:"Abrir suporte",onClick:()=>{window.location.href="/dashboard?section=configuracoes&settings=suporte";}},
      duration:7000,
    });
  };

  const carregar=useCallback(async()=>{
    if(!empresaAtiva) return;
    setLoading(true);
    try{setPedidos(await fetchAuth<Pedido[]>(`/api/v1/pedidos/empresa/${empresaAtiva.id}`));}
    catch{toast.error("Erro ao carregar pedidos");}
    finally{setLoading(false);}
  },[empresaAtiva?.id]);

  useEffect(()=>{carregar();},[carregar]);

  // Abre a aba de integrações automaticamente se voltou do OAuth com sucesso
  useEffect(()=>{
    if(typeof window === "undefined") return;
    const params = new URLSearchParams(window.location.search);
    if(params.get("sucesso")==="true") setAbaAtiva("integracoes");
  },[]);

  const adicionarPedido=useCallback((p:Pedido)=>{
    setPedidos(prev=>[p,...prev]);
    toast.success(`Pedido #${p.id} registrado!`);
  },[]);

  const removerPedido=useCallback((id:number)=>{
    setPedidos(prev=>prev.filter(p=>p.id!==id));
  },[]);

  const atualizarPedido=useCallback((updated:Pedido)=>{
    setPedidos(prev=>prev.map(p=>p.id===updated.id?updated:p));
    setDetalhe(updated);
  },[]);

  const limparTudo=async()=>{
    if(!empresaAtiva) return;
    if(pedidos.some(p=>!["ENTREGUE","CANCELADO"].includes(p.status))){
      toast.error("Finalize ou cancele os pedidos em andamento antes de limpar o histórico.");return;
    }
    setLimpando(true);
    try{
      await fetchAuth(`/api/v1/pedidos/empresa/${empresaAtiva.id}/historico`,{method:"DELETE"});
      setPedidos([]);setConfirmandoLimpar(false);
      toast.success("Histórico apagado.");
    }catch(e:any){toast.error(e.message);}
    finally{setLimpando(false);}
  };

  const pedidosFiltrados=useMemo(()=>
    pedidos
      .filter(p=>filtroStatus==="TODOS"||p.status===filtroStatus)
      .filter(p=>
        busca===""||
        String(p.id).includes(busca)||
        (p.nomeCliente??"").toLowerCase().includes(busca.toLowerCase())||
        (CANAIS.find(c=>c.value===p.canalVenda)?.label??"").toLowerCase().includes(busca.toLowerCase())||
        p.itens.some(item=>(item.nomeProduto??"").toLowerCase().includes(busca.toLowerCase()))
      ),
  [pedidos,filtroStatus,busca]);

  const ativos      = pedidos.filter(p=>p.status!=="CANCELADO");
  const totalBruto  = ativos.reduce((s,p)=>s+p.valorFinal,0);
  const pendentes   = pedidos.filter(p=>p.status==="PENDENTE").length;
  const automaticos = pedidos.filter(p=>p.observacao?.startsWith("Pedido automático")).length;
  const podeLimparHistorico = pedidos.length>0&&pedidos.every(p=>["ENTREGUE","CANCELADO"].includes(p.status));

  if(!empresaAtiva) return(
    <div style={{padding:48,textAlign:"center",color:"var(--foreground-muted)",display:"flex",flexDirection:"column",alignItems:"center",gap:12}}>
      <Store size={40} color="var(--foreground-subtle)"/>
      <p style={{fontSize:14}}>Selecione uma empresa para ver os pedidos.</p>
    </div>
  );

  return(
    <div className="pedidos-page" style={{padding:28,display:"flex",flexDirection:"column",gap:20}}>
      <div style={{display:"flex",alignItems:"flex-start",justifyContent:"space-between",flexWrap:"wrap",gap:12}}>
        <div>
          <h2 style={{fontSize:18,fontWeight:700,color:"var(--foreground)",margin:0}}>Pedidos</h2>
          <p style={{fontSize:13,color:"var(--foreground-muted)",marginTop:3}}>
            {empresaAtiva.nomeFantasia} · vendas remotas &amp; online
          </p>
        </div>
        <div style={{display:"flex",gap:8}}>
          {abaAtiva==="pedidos"&&podeLimparHistorico&&(
            <button onClick={()=>setConfirmandoLimpar(true)} style={btnDanger}>
              <Trash2 size={14}/> Limpar histórico
            </button>
          )}
          {abaAtiva==="pedidos"&&(
            <button style={btnP} onClick={()=>setModalNovo(true)}>
              <Plus size={15}/> Novo Pedido
            </button>
          )}
        </div>
      </div>

      <div className="pedidos-summary-grid" style={{display:"grid",gridTemplateColumns:"repeat(auto-fill,minmax(150px,1fr))",gap:10}}>
        {[
          {label:"Total Faturado", value:fmt(totalBruto), destaque:true},
          {label:"Total Pedidos",  value:String(ativos.length)},
          {label:"Pendentes",      value:String(pendentes), warn:pendentes>0},
          ...(isPremium&&automaticos>0
            ?[{label:"Via Marketplace", value:String(automaticos), info:true}]
            :[{label:"Cancelados", value:String(pedidos.filter(p=>p.status==="CANCELADO").length)}]
          ),
        ].map((k:any,i)=>(
          <div key={i} style={{background:"var(--surface-elevated)",border:"1px solid var(--border)",borderRadius:10,padding:"12px 14px"}}>
            <p style={{fontSize:10,fontWeight:600,color:"var(--foreground-muted)",textTransform:"uppercase",letterSpacing:".06em",margin:"0 0 4px"}}>{k.label}</p>
            <p style={{fontSize:18,fontWeight:700,margin:0,color:k.destaque?"var(--primary)":k.warn?"#f59e0b":k.info?"#3b82f6":"var(--foreground)"}}>{k.value}</p>
          </div>
        ))}
      </div>

      <div style={{display:"flex",gap:0,borderBottom:"1px solid var(--border)"}}>
        <button onClick={()=>setAbaAtiva("pedidos")} style={{padding:"8px 16px",fontSize:13,cursor:"pointer",background:"none",border:"none",color:abaAtiva==="pedidos"?"var(--foreground)":"var(--foreground-muted)",fontWeight:abaAtiva==="pedidos"?600:400,borderBottom:abaAtiva==="pedidos"?"2px solid var(--primary)":"2px solid transparent",marginBottom:-1}}>
          Pedidos
        </button>
        <button onClick={abrirIntegracoes} style={{padding:"8px 16px",fontSize:13,cursor:"pointer",background:"none",border:"none",color:abaAtiva==="integracoes"?"var(--foreground)":"var(--foreground-muted)",fontWeight:abaAtiva==="integracoes"?600:400,borderBottom:abaAtiva==="integracoes"?"2px solid var(--primary)":"2px solid transparent",marginBottom:-1,display:"flex",alignItems:"center",gap:6}}>
          <Zap size={13}/> Integrações
          {!isPremium&&<span style={{fontSize:10,padding:"1px 6px",borderRadius:99,background:"rgba(234,179,8,0.15)",color:"#854F0B",fontWeight:600,marginLeft:2}}>Premium</span>}
        </button>
      </div>

      {abaAtiva==="integracoes"&&(
        <div style={{display:"flex",flexDirection:"column",gap:14}}>
          <div role="status" style={{display:"flex",alignItems:"flex-start",justifyContent:"space-between",gap:16,padding:"14px 16px",border:"1px solid rgba(245,158,11,.35)",borderRadius:12,background:"rgba(245,158,11,.09)"}}>
            <div style={{display:"flex",gap:10,alignItems:"flex-start"}}><AlertTriangle size={18} color="#f59e0b" style={{marginTop:1,flexShrink:0}}/><div><p style={{margin:0,fontSize:13,fontWeight:700,color:"var(--foreground)"}}>Integrações em desenvolvimento e aprimoramento</p><p style={{margin:"4px 0 0",fontSize:12,lineHeight:1.5,color:"var(--foreground-muted)"}}>Algumas conexões podem apresentar instabilidade. Se encontrar qualquer erro, envie os detalhes pela Central de Suporte.</p></div></div>
            <Link href="/dashboard?section=configuracoes&settings=suporte" style={{flexShrink:0,padding:"8px 12px",borderRadius:8,background:"var(--primary)",color:"#fff",fontSize:12,fontWeight:700,textDecoration:"none"}}>Abrir suporte</Link>
          </div>
          {isPremium?<SecaoIntegracoes empresaId={empresaAtiva.id}/>:<SecaoPremiumLock/>}
        </div>
      )}

      {abaAtiva==="pedidos"&&(
        <>
          <div className="pedidos-filters" style={{display:"flex",gap:8,flexWrap:"wrap",alignItems:"center"}}>
            {["TODOS",...Object.keys(STATUS_META)].map(s=>(
              <button key={s} onClick={()=>setFiltroStatus(s)} style={{padding:"5px 12px",borderRadius:99,fontSize:12,fontWeight:500,cursor:"pointer",background:filtroStatus===s?"var(--primary-muted)":"transparent",border:`1px solid ${filtroStatus===s?"var(--primary)":"var(--border)"}`,color:filtroStatus===s?"var(--primary)":"var(--foreground-muted)"}}>
                {s==="TODOS"?"Todos":STATUS_META[s as StatusPedido].label}
              </button>
            ))}
            <div style={{position:"relative",marginLeft:"auto"}}>
              <Search size={11} style={{position:"absolute",left:8,top:"50%",transform:"translateY(-50%)",color:"var(--foreground-subtle)"}}/>
              <input style={{...inp,paddingLeft:26,padding:"6px 9px 6px 26px",fontSize:12,maxWidth:220}}
                placeholder="Buscar pedido..." value={busca} onChange={e=>setBusca(e.target.value)}/>
            </div>
          </div>

          {loading
            ?<div style={{textAlign:"center",color:"var(--foreground-muted)",fontSize:13,padding:32}}>Carregando...</div>
            :pedidosFiltrados.length===0
              ?<div style={{padding:48,textAlign:"center",display:"flex",flexDirection:"column",alignItems:"center",gap:10,color:"var(--foreground-subtle)"}}>
                <Package size={40}/>
                <p style={{fontSize:14}}>{pedidos.length===0?"Nenhum pedido registrado.":"Sem resultados para este filtro."}</p>
              </div>
              :<div style={{display:"flex",flexDirection:"column",gap:6}}>
                {pedidosFiltrados.map(p=>{
                  const isAuto = p.observacao?.startsWith("Pedido automático");
                  return (
                    <div className="pedidos-list-row" key={p.id} onClick={()=>setDetalhe(p)}
                      style={{display:"flex",alignItems:"center",justifyContent:"space-between",padding:"12px 14px",background:"var(--surface-elevated)",border:"1px solid var(--border)",borderRadius:10,cursor:"pointer",transition:"border-color .1s"}}
                      onMouseEnter={e=>((e.currentTarget as HTMLDivElement).style.borderColor="var(--primary)")}
                      onMouseLeave={e=>((e.currentTarget as HTMLDivElement).style.borderColor="var(--border)")}>
                      <div style={{display:"flex",alignItems:"center",gap:12}}>
                        <span style={{fontSize:12,fontWeight:600,color:"var(--foreground-muted)",minWidth:32}}>#{p.id}</span>
                        <div>
                          <div style={{display:"flex",alignItems:"center",gap:7,flexWrap:"wrap"}}>
                            <StatusBadge status={p.status}/>
                            <CanalBadge canal={p.canalVenda}/>
                            {isAuto&&<AutoBadge/>}
                            {p.contaDestino&&<span style={{fontSize:11,color:"var(--foreground-muted)"}}>💳 {p.contaDestino}</span>}
                          </div>
                          <p style={{fontSize:11,color:"var(--foreground-subtle)",margin:"3px 0 0"}}>
                            {fmtData(p.dataPedido)} · {p.itens?.length??0} item(s){p.nomeCliente?` · ${p.nomeCliente}`:""}
                          </p>
                        </div>
                      </div>
                      <div style={{display:"flex",alignItems:"center",gap:8}}>
                        <span style={{fontSize:14,fontWeight:700,color:p.status==="CANCELADO"?"var(--foreground-subtle)":"var(--primary)",textDecoration:p.status==="CANCELADO"?"line-through":"none"}}>
                          {fmt(p.valorFinal)}
                        </span>
                        <ChevronRight size={14} color="var(--foreground-subtle)"/>
                      </div>
                    </div>
                  );
                })}
              </div>
          }
        </>
      )}

      {modalNovo&&(
        <ModalNovoPedido empresaId={empresaAtiva.id} onClose={()=>setModalNovo(false)} onSucesso={adicionarPedido}/>
      )}
      {detalhe&&(
        <DetalhePedido pedido={detalhe} onClose={()=>setDetalhe(null)} onAtualizado={atualizarPedido} onRemovido={removerPedido}/>
      )}
      {confirmandoLimpar&&(
        <ModalConfirmarExclusao
          titulo="Limpar todo o histórico?"
          descricao="Todos os pedidos desta empresa serão permanentemente removidos. Esta ação não pode ser desfeita."
          onConfirmar={limparTudo}
          onCancelar={()=>setConfirmandoLimpar(false)}
          confirmando={limpando}
        />
      )}
    </div>
  );
}
