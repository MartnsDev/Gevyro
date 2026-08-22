"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getUsuario } from "@/lib/api-v2";

type Choice = { necessary: true; functional: boolean; analytics: boolean; marketing: boolean; version: 1 };
const KEY = "gevyro-cookie-preferences";
const AFTER_LOGIN_KEY = "gevyro-request-cookie-consent-after-login";
const LEGAL_AFTER_LOGIN_KEY = "gevyro-require-legal-ack-after-login";
const ACCOUNT_CONSENTS_KEY = "gevyro-account-consents";
const onlyNecessary: Choice = { necessary: true, functional: false, analytics: false, marketing: false, version: 1 };

export function CookieConsent() {
  const [visible, setVisible] = useState(false);
  const [manage, setManage] = useState(false);
  const [choice, setChoice] = useState<Choice>(onlyNecessary);
  const [requireLegal, setRequireLegal] = useState(false);
  const [legalAccepted, setLegalAccepted] = useState(false);
  const [error, setError] = useState("");
  const [accountEmail, setAccountEmail] = useState("");

  const readAccountConsents = (): Record<string, { choice: Choice; savedAt: string; termosVersao: string; privacidadeVersao: string }> => {
    try { return JSON.parse(localStorage.getItem(ACCOUNT_CONSENTS_KEY) ?? "{}"); }
    catch { localStorage.removeItem(ACCOUNT_CONSENTS_KEY); return {}; }
  };

  useEffect(() => {
    const afterLogin = sessionStorage.getItem(AFTER_LOGIN_KEY) === "true";
    if (!afterLogin) return;

    // Um OAuth cancelado pode voltar para a tela de login deixando o marcador
    // pendente. Nessa rota não há sessão a consultar, então encerramos o fluxo
    // para não gerar 401 a cada montagem da página.
    if (window.location.pathname.startsWith("/auth/login")) {
      sessionStorage.removeItem(AFTER_LOGIN_KEY);
      sessionStorage.removeItem(LEGAL_AFTER_LOGIN_KEY);
      return;
    }

    let active = true;
    getUsuario().then((usuario) => {
      if (!active) return;
      const email = usuario.email.trim().toLowerCase();
      const existing = readAccountConsents()[email];
      sessionStorage.removeItem(AFTER_LOGIN_KEY);
      if (existing) {
        localStorage.setItem(KEY, JSON.stringify(existing.choice));
        sessionStorage.removeItem(LEGAL_AFTER_LOGIN_KEY);
        setVisible(false);
        return;
      }
      setAccountEmail(email);
      localStorage.removeItem(KEY);
      setManage(true);
      setRequireLegal(sessionStorage.getItem(LEGAL_AFTER_LOGIN_KEY) === "true");
      setVisible(true);
    }).catch(() => {
      sessionStorage.removeItem(AFTER_LOGIN_KEY);
      sessionStorage.removeItem(LEGAL_AFTER_LOGIN_KEY);
      setVisible(false);
    });
    return () => { active = false; };
  }, []);
  function save(value: Choice) {
    if (requireLegal && !legalAccepted) {
      setError("Confirme a leitura dos Termos de Uso e da Política de Privacidade.");
      return;
    }
    if (requireLegal) {
      sessionStorage.removeItem(LEGAL_AFTER_LOGIN_KEY);
      localStorage.setItem("gevyro-google-legal-ack", JSON.stringify({ termosVersao: "1.0", privacidadeVersao: "1.0", confirmadoEm: new Date().toISOString() }));
    }
    const savedAt = new Date().toISOString();
    localStorage.setItem(KEY, JSON.stringify({ ...value, savedAt }));
    if (accountEmail) {
      const accounts = readAccountConsents();
      accounts[accountEmail] = { choice: value, savedAt, termosVersao: "1.0", privacidadeVersao: "1.0" };
      localStorage.setItem(ACCOUNT_CONSENTS_KEY, JSON.stringify(accounts));
    }
    window.dispatchEvent(new CustomEvent("gevyro:cookie-consent", { detail: value }));
    setVisible(false);
  }
  useEffect(() => {
    const open = () => {
      getUsuario().then((usuario) => {
        const email = usuario.email.trim().toLowerCase();
        const existing = readAccountConsents()[email];
        setAccountEmail(email);
        if (existing) setChoice(existing.choice);
      }).catch(() => undefined);
      setRequireLegal(false);
      setError("");
      setVisible(true);
      setManage(true);
    };
    window.addEventListener("gevyro:open-cookie-preferences", open);
    return () => window.removeEventListener("gevyro:open-cookie-preferences", open);
  }, []);

  if (!visible) return null;
  return <aside role="dialog" aria-modal="true" aria-labelledby="cookie-title" className="fixed inset-x-4 bottom-4 z-[100] mx-auto max-w-3xl rounded-2xl border border-zinc-200 bg-white p-5 text-[#303a35] shadow-2xl sm:p-6">
    <h2 id="cookie-title" className="text-lg font-semibold">Suas preferências de privacidade</h2>
    <p className="mt-2 text-sm leading-6 text-[#66736c]">Usamos tecnologias necessárias para autenticação, segurança e preferências. Não identificamos analytics ou publicidade ativos atualmente. Se forem adicionados, só poderão ser ativados conforme sua escolha. <Link href="/cookies" className="font-semibold text-[#258c53] underline">Saiba mais</Link>.</p>
    {requireLegal && <label className="mt-3 flex cursor-pointer items-center justify-center gap-2 rounded-lg border border-zinc-200 bg-[#f8faf9] px-2 py-2 text-center text-[10px] leading-4 text-[#59665f] sm:mt-4 sm:items-start sm:justify-start sm:gap-3 sm:rounded-xl sm:p-4 sm:text-left sm:text-xs sm:leading-5"><input type="checkbox" checked={legalAccepted} onChange={(event) => { setLegalAccepted(event.target.checked); setError(""); }} className="h-3.5 w-3.5 shrink-0 accent-[#258c53] sm:mt-0.5 sm:h-4 sm:w-4" /><span>Confirmo que li os <Link href="/termos" target="_blank" className="font-semibold text-[#258c53] underline">Termos de Uso</Link> e a <Link href="/privacidade" target="_blank" className="font-semibold text-[#258c53] underline">Política de Privacidade</Link>.</span></label>}
    {error && <p role="alert" className="mt-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs text-red-600">{error}</p>}
    {manage && <div className="mt-4 grid gap-3 sm:grid-cols-2">
      <label className="rounded-xl border border-zinc-200 p-3 text-sm"><span className="flex justify-between gap-3 font-semibold">Necessários <input type="checkbox" checked disabled /></span><small className="mt-1 block text-[#718078]">Sempre ativos para segurança e funcionamento.</small></label>
      {(["functional", "analytics", "marketing"] as const).map((key) => <label key={key} className="rounded-xl border border-zinc-200 p-3 text-sm"><span className="flex justify-between gap-3 font-semibold capitalize">{key === "functional" ? "Funcionais" : key === "analytics" ? "Analíticos" : "Marketing"}<input type="checkbox" checked={choice[key]} onChange={(e) => setChoice((c) => ({ ...c, [key]: e.target.checked }))} /></span><small className="mt-1 block text-[#718078]">{key === "functional" ? "Lembram escolhas da interface." : "Nenhuma tecnologia desta categoria está ativa hoje."}</small></label>)}
    </div>}
    <div className="mt-5 flex flex-col gap-2 sm:flex-row sm:justify-end">
      <button type="button" onClick={() => save(onlyNecessary)} className="rounded-full border border-zinc-300 px-5 py-2.5 text-sm font-semibold">Recusar opcionais</button>
      <button type="button" onClick={() => setManage(true)} className="rounded-full border border-[#258c53] px-5 py-2.5 text-sm font-semibold text-[#258c53]">Gerenciar preferências</button>
      {manage ? <button type="button" onClick={() => save(choice)} className="rounded-full bg-[#258c53] px-5 py-2.5 text-sm font-semibold text-white">Salvar escolhas</button> : <button type="button" onClick={() => save({ ...onlyNecessary, functional: true, analytics: true, marketing: true })} className="rounded-full bg-[#258c53] px-5 py-2.5 text-sm font-semibold text-white">Aceitar</button>}
    </div>
  </aside>;
}
