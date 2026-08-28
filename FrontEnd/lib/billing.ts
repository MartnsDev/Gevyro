"use client";

import { fetchAuth, fetchAuthJson } from "./api-v2";

export const PLANOS_PAGOS = [
  {
    id: "BASICO",
    nome: "Básico",
    descricao: "Para organizar a primeira loja",
    preco: "79,90",
    destaque: false,
    recursos: ["1 empresa e 1 caixa", "Até 500 produtos", "Relatórios completos", "Suporte por e-mail"],
  },
  {
    id: "PRO",
    nome: "Pro",
    descricao: "Para operações em crescimento",
    preco: "149,90",
    destaque: true,
    recursos: ["Até 5 empresas e 5 caixas", "Produtos ilimitados", "Exportação PDF/CSV", "Suporte prioritário"],
  },
  {
    id: "PREMIUM",
    nome: "Premium",
    descricao: "Para redes e franquias",
    preco: "299,90",
    destaque: false,
    recursos: ["Empresas e caixas ilimitados", "Histórico ilimitado", "Integrações com marketplaces", "Suporte dedicado"],
  },
] as const;

export type PlanoPagoId = (typeof PLANOS_PAGOS)[number]["id"];

export async function criarCheckout(plano: PlanoPagoId): Promise<string> {
  // O backend identifica o cliente pelo cookie HttpOnly. O navegador informa
  // apenas o plano, impedindo a assinatura em nome de outro e-mail.
  const data = await fetchAuthJson<{ url?: string }>("/api/payments/create-checkout-session", {
    method: "POST",
    body: JSON.stringify({ plano }),
  });
  if (!data.url) throw new Error("O backend não retornou a página de pagamento.");
  const checkoutUrl = new URL(data.url);
  if (checkoutUrl.protocol !== "https:") throw new Error("A página de pagamento retornada não é segura.");
  return checkoutUrl.toString();
}

export async function abrirPortalCobranca(): Promise<string | null> {
  const response = await fetchAuth("/api/payments/portal", {
    method: "POST",
  });
  const data = await response.json().catch(() => null) as { url?: string; error?: string; code?: string } | null;
  if (response.status === 409 && data?.code === "CHECKOUT_REQUIRED") return null;
  if (!response.ok) throw new Error(data?.error ?? `Erro ${response.status} ao abrir o portal de cobrança.`);
  const url = data?.url;
  if (!url) throw new Error("O backend não retornou o portal de cobrança.");
  const portalUrl = new URL(url);
  if (portalUrl.protocol !== "https:") throw new Error("O portal de cobrança retornado não é seguro.");
  return portalUrl.toString();
}
