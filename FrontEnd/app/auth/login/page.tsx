"use client";

import { useEffect, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, ArrowRight, Eye, EyeOff, LockKeyhole, Mail } from "lucide-react";
import { getUsuario, limparDadosSessaoCliente, login, loginComGoogle, logout, reenviarConfirmacao } from "@/lib/api-v2";
import { useActionCooldown } from "@/hooks/use-action-cooldown";

const AFTER_LOGIN_KEY = "gevyro-request-cookie-consent-after-login";

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
      <path d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844c-.209 1.125-.843 2.078-1.796 2.717v2.258h2.908c1.702-1.567 2.684-3.875 2.684-6.615z" fill="#4285F4" />
      <path d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18z" fill="#34A853" />
      <path d="M3.964 10.71A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.042l3.007-2.332z" fill="#FBBC05" />
      <path d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58z" fill="#EA4335" />
    </svg>
  );
}

export default function LoginPage() {
  const router = useRouter();
  const [form, setForm] = useState({ email: "", senha: "" });
  const [loading, setLoading] = useState(false);
  const [preparandoSessao, setPreparandoSessao] = useState(true);
  const [sessaoPreparada, setSessaoPreparada] = useState(false);
  const [erro, setErro] = useState("");
  const [showPass, setShowPass] = useState(false);
  const [precisaConfirmar, setPrecisaConfirmar] = useState(false);
  const [reenviando, setReenviando] = useState(false);
  const loginCooldown = useActionCooldown("login", 5);
  const preparacaoIniciada = useRef(false);

  useEffect(() => {
    if (preparacaoIniciada.current) return;
    preparacaoIniciada.current = true;

    // A rota de login é uma fronteira de sessão: nenhum cookie ou dado da
    // conta anterior pode sobreviver enquanto novas credenciais são exibidas.
    logout()
      .then(() => {
        setForm({ email: "", senha: "" });
        const oauthFalhou = new URLSearchParams(window.location.search).get("error") === "oauth2";
        setErro(oauthFalhou ? "Não foi possível entrar com o Google. Tente novamente ou use e-mail e senha." : "");
        setSessaoPreparada(true);
      })
      .catch(() => {
        // O próprio POST /auth/login invalida qualquer sessão anterior antes
        // de validar as novas credenciais. Se o logout preventivo falhar, a
        // limpeza local basta para liberar a tentativa sem exigir F5 do usuário.
        limparDadosSessaoCliente();
        setForm({ email: "", senha: "" });
        setSessaoPreparada(true);
      })
      .finally(() => setPreparandoSessao(false));
  }, []);

  const handleGoogle = () => {
    if (preparandoSessao || !sessaoPreparada) return;
    if (!loginCooldown.tryStart()) {
      setErro(`Aguarde ${loginCooldown.remaining}s antes de tentar entrar novamente.`);
      return;
    }
    // Login de conta existente não deve solicitar novamente aceite jurídico
    // nem interromper o redirecionamento com preferências de primeiro acesso.
    sessionStorage.removeItem(AFTER_LOGIN_KEY);
    loginComGoogle();
  };

  const set = (key: "email" | "senha", value: string) => {
    setForm((current) => ({ ...current, [key]: value }));
    setErro("");
    setPrecisaConfirmar(false);
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!sessaoPreparada) return setErro("Aguarde enquanto preparamos um acesso seguro.");
    if (!form.email || !form.senha) return setErro("Preencha todos os campos");
    if (!loginCooldown.tryStart()) return setErro(`Aguarde ${loginCooldown.remaining}s antes de tentar entrar novamente.`);
    setLoading(true);
    setErro("");
    try {
      const usuarioLogin = await login(form.email.trim().toLowerCase(), form.senha);
      const usuarioSessao = await getUsuario();
      const mesmoEmail =
        usuarioLogin.email.trim().toLowerCase() === usuarioSessao.email.trim().toLowerCase();
      const idsCompativeis =
        usuarioLogin.id <= 0 ||
        usuarioSessao.id <= 0 ||
        usuarioLogin.id === usuarioSessao.id;
      const mesmaConta = mesmoEmail && idsCompativeis;

      if (!mesmaConta) {
        await logout().catch(() => limparDadosSessaoCliente());
        throw new Error("A sessão retornada não corresponde à conta informada. Entre novamente.");
      }

      router.replace(usuarioSessao.statusAcesso === "INATIVO" ? "/pagamento" : "/dashboard");
      if (sessionStorage.getItem(AFTER_LOGIN_KEY) === "true") {
        sessionStorage.removeItem(AFTER_LOGIN_KEY);
        localStorage.removeItem("gevyro-cookie-preferences");
        window.setTimeout(() => window.dispatchEvent(new Event("gevyro:open-cookie-preferences")), 250);
      }
    } catch (error: unknown) {
      // Evita que uma tentativa OAuth interrompida mantenha uma consulta de
      // sessão pendente a cada nova abertura da página de login.
      sessionStorage.removeItem(AFTER_LOGIN_KEY);
      const message = error instanceof Error ? error.message : "Credenciais inválidas ou erro no servidor";
      setErro(message === "PLANO_INATIVO" ? "Não foi possível iniciar uma nova sessão. Tente novamente." : message);
      setPrecisaConfirmar(message.toLowerCase().includes("confirme seu e-mail"));
    } finally {
      setLoading(false);
    }
  };

  const handleReenvio = async () => {
    if (!form.email.trim() || reenviando) return;
    setReenviando(true);
    try {
      setErro(await reenviarConfirmacao(form.email));
      setPrecisaConfirmar(false);
    } catch (error) {
      setErro(error instanceof Error ? error.message : "Não foi possível reenviar agora.");
    } finally {
      setReenviando(false);
    }
  };

  return (
    <main className="min-h-screen bg-white text-[#343b37] lg:grid lg:grid-cols-[1.05fr_.95fr]">
      <section className="flex min-h-screen flex-col px-4 py-5 sm:px-10 sm:py-6 lg:px-16 xl:px-24">
        <header className="flex items-center justify-between">
          <Link href="/" className="flex items-center gap-3">
            <Image src="/images/gevyro-logo-400.webp" alt="Gevyro" width={400} height={145} priority className="h-auto w-[150px] object-contain sm:w-[200px]" />
          </Link>
          <Link href="/" className="flex items-center gap-2 text-xs text-[#718078] hover:text-[#258c53]"><ArrowLeft size={15} /> Início</Link>
        </header>

        <div className="mx-auto flex w-full max-w-[430px] flex-1 flex-col justify-center py-9 sm:py-14">
          <p className="text-[11px] font-bold uppercase tracking-[.14em] text-[#258c53]">Área do cliente</p>
          <h1 className="mt-4 text-3xl font-light tracking-[-.04em] text-[#343b37] sm:text-5xl">Bem-vindo <span className="italic text-[#258c53]">de volta</span></h1>
          <p className="mt-4 text-sm leading-6 text-[#718078]">Entre para acompanhar vendas, estoque, caixa e relatórios.</p>

          <button type="button" onClick={handleGoogle} disabled={preparandoSessao||!sessaoPreparada||loading||loginCooldown.blocked} className="mt-9 flex h-[52px] w-full items-center justify-center gap-3 rounded-full border-2 border-[#258c53]/35 bg-[#f7fcf9] text-sm font-bold text-[#244b36] shadow-[0_8px_24px_rgba(37,140,83,.10)] transition hover:-translate-y-0.5 hover:border-[#258c53] hover:bg-[#eff9f3] hover:shadow-[0_10px_28px_rgba(37,140,83,.16)] disabled:cursor-not-allowed disabled:opacity-60">
            <GoogleIcon /> {preparandoSessao || !sessaoPreparada ? "Preparando acesso..." : loginCooldown.blocked?`Aguarde ${loginCooldown.remaining}s` : "Continuar com Google"}
          </button>
          <div className="my-7 flex items-center gap-4"><span className="h-px flex-1 bg-zinc-200" /><span className="text-[10px] font-semibold uppercase tracking-widest text-zinc-400">ou</span><span className="h-px flex-1 bg-zinc-200" /></div>

          <form onSubmit={handleSubmit} autoComplete="on" className="space-y-5">
            <label className="block">
              <span className="mb-2 block text-xs font-semibold text-[#46514b]">E-mail</span>
              <span className="group relative block">
                <Mail size={18} className="pointer-events-none absolute left-4 top-1/2 z-10 -translate-y-1/2 text-[#9aa59f] transition-colors group-focus-within:text-[#258c53]" />
                <input type="email" value={form.email} onChange={(event) => set("email", event.target.value)} autoComplete="email" placeholder="seu@email.com" className="auth-input h-[54px] w-full rounded-2xl border border-[#dce3df] bg-white pl-11 pr-4 text-sm text-[#303a35] shadow-[0_1px_2px_rgba(30,50,40,.04)] outline-none transition placeholder:text-[#a3ada7] hover:border-[#bdc9c2] focus:border-[#258c53] focus:ring-4 focus:ring-[#258c53]/10" />
              </span>
            </label>
            <label className="block">
              <span className="mb-2 flex items-center justify-between text-xs font-semibold text-[#46514b]">Senha <Link href="/esqueceu-senha" className="font-normal text-[#258c53] hover:underline">Esqueceu a senha?</Link></span>
              <span className="group relative block">
                <LockKeyhole size={18} className="pointer-events-none absolute left-4 top-1/2 z-10 -translate-y-1/2 text-[#9aa59f] transition-colors group-focus-within:text-[#258c53]" />
                <input type={showPass ? "text" : "password"} value={form.senha} onChange={(event) => set("senha", event.target.value)} autoComplete="current-password" placeholder="Sua senha" className="auth-input h-[54px] w-full rounded-2xl border border-[#dce3df] bg-white pl-11 pr-12 text-sm text-[#303a35] shadow-[0_1px_2px_rgba(30,50,40,.04)] outline-none transition placeholder:text-[#a3ada7] hover:border-[#bdc9c2] focus:border-[#258c53] focus:ring-4 focus:ring-[#258c53]/10" />
                <button type="button" onClick={() => setShowPass((value) => !value)} className="absolute right-2 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-xl text-[#9aa59f] transition hover:bg-[#eff7f2] hover:text-[#258c53]" aria-label={showPass ? "Ocultar senha" : "Mostrar senha"}>{showPass ? <EyeOff size={18} /> : <Eye size={18} />}</button>
              </span>
            </label>
            {erro && <p role="alert" className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">{erro}</p>}
            {precisaConfirmar && <button type="button" onClick={handleReenvio} disabled={reenviando} className="w-full text-sm font-semibold text-[#258c53] hover:underline disabled:opacity-60">{reenviando ? "Reenviando..." : "Reenviar e-mail de confirmação"}</button>}
            <button type="submit" disabled={preparandoSessao||!sessaoPreparada||loading||loginCooldown.blocked} className="flex h-[52px] w-full items-center justify-center gap-3 rounded-full bg-[#258c53] text-sm font-bold text-white transition hover:bg-[#1d7544] disabled:cursor-not-allowed disabled:opacity-60">{preparandoSessao || !sessaoPreparada ? "Preparando acesso seguro..." : loading ? "Entrando..." : loginCooldown.blocked?`Tente novamente em ${loginCooldown.remaining}s`:<>Entrar <ArrowRight size={17} /></>}</button>
          </form>

          <p className="mt-7 text-center text-sm text-[#718078]">Ainda não tem conta? <Link href="/auth/cadastro" className="font-semibold text-[#258c53] hover:underline">Teste por 30 dias</Link></p>
        </div>
      </section>

      <aside className="relative hidden overflow-hidden bg-[#303a35] p-16 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="absolute -right-24 -top-24 h-80 w-80 rounded-full border border-[#78d6a3]/15" /><div className="absolute -right-8 -top-8 h-48 w-48 rounded-full border border-[#78d6a3]/20" />
        <p className="relative text-[11px] font-bold uppercase tracking-[.14em] text-[#78d6a3]">Gestão em evolução.</p>
        <div className="relative max-w-lg"><h2 className="text-5xl font-light leading-[1.08]">Sua operação continua <span className="italic text-[#78d6a3]">organizada</span></h2><p className="mt-6 max-w-md text-sm leading-7 text-zinc-300">Acesse seus dados e retome o trabalho exatamente de onde parou.</p></div>
        <p className="relative text-xs leading-5 text-zinc-400">© 2026 Gevyro<br />CNPJ 68.259.534/0001-70</p>
      </aside>
    </main>
  );
}
