// lib/api-v2.ts

"use client";

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL ?? "https://api.gevyro.com.br";


export interface Usuario {
  id: number;
  nome: string;
  email: string;
  foto?: string;         // foto do Google (URL completa)
  fotoUpload?: string;   // foto de upload (path relativo: /uploads/fotos/uuid.jpg)
  tipoPlano: string;
  statusAcesso?: "ATIVO" | "INATIVO";
  expiracaoPlano?: string;
}

export interface LoginResponse {
  nome: string;
  email: string;
  tipoPlano: string;
  foto?: string;
  fotoUpload?: string;
  statusAcesso?: "ATIVO" | "INATIVO";
  expiracaoPlano?: string;
}

interface ErrorResponse {
  erro?: string;
  mensagem?: string;
}

const ROTULOS_CAMPOS: Record<string, string> = {
  nome: "Nome",
  email: "E-mail",
  senha: "Senha",
  telefone: "Telefone",
  cpf: "CPF",
  cnpj: "CNPJ",
  empresaId: "Empresa",
  preco: "Preço",
  quantidadeEstoque: "Quantidade em estoque",
  estoqueMinimo: "Estoque mínimo",
};

function detalheErro(valor: unknown): string | null {
  if (typeof valor === "string" && valor.trim()) {
    const [campo, ...mensagem] = valor.split(":");
    if (mensagem.length > 0) {
      const rotulo = ROTULOS_CAMPOS[campo.trim()] ?? campo.trim();
      return `${rotulo}: ${mensagem.join(":").trim()}`;
    }
    return valor.trim();
  }
  if (valor && typeof valor === "object") {
    const item = valor as Record<string, unknown>;
    const campo = typeof item.field === "string" ? item.field : typeof item.campo === "string" ? item.campo : "";
    const mensagem = item.message ?? item.mensagem ?? item.defaultMessage;
    if (typeof mensagem === "string" && mensagem.trim()) {
      return campo ? `${ROTULOS_CAMPOS[campo] ?? campo}: ${mensagem.trim()}` : mensagem.trim();
    }
  }
  return null;
}

export function mensagemErroApi(payload: unknown, status: number): string {
  const data = payload && typeof payload === "object" ? payload as Record<string, unknown> : {};
  const detalhesBrutos = data.erros ?? data.detalhes ?? data.fieldErrors ?? data.violations;
  const detalhes = Array.isArray(detalhesBrutos)
    ? detalhesBrutos.map(detalheErro).filter((item): item is string => Boolean(item))
    : [];
  const principal = [data.mensagem, data.erro, data.error, data.message, data.detail]
    .find((item): item is string => typeof item === "string" && item.trim().length > 0)
    ?.trim();

  if (detalhes.length > 0) return detalhes.join(" • ");
  if (principal && !["Bad Request", "Erro de validação"].includes(principal)) return principal;

  if (status === 400 || status === 422) return "Verifique os dados informados e preencha corretamente os campos obrigatórios.";
  if (status === 401) return "Sua sessão expirou. Entre novamente para continuar.";
  if (status === 403) return "Você não tem permissão para realizar esta ação.";
  if (status === 404) return "O registro solicitado não foi encontrado.";
  if (status === 409) return "Esta ação entra em conflito com um registro existente.";
  if (status >= 500) return "O servidor não conseguiu concluir a operação. Tente novamente em instantes.";
  return `Não foi possível concluir a operação (erro ${status}).`;
}

// O JWT é mantido exclusivamente pelo backend em cookie HttpOnly. Estes
// aliases permanecem temporariamente para componentes legados compilarem sem
// voltar a expor a credencial ao JavaScript.
export function salvarTokenCookie(_token: string) {}
export function removerTokenCookie() {
  if (typeof window === "undefined") return;
  sessionStorage.removeItem("jwt_token");
  localStorage.removeItem("token");
  localStorage.removeItem("access_token");
}
export function lerTokenCookie(): null { return null; }
export function getToken(): null { return null; }
export function hasToken(): boolean { return false; }

let csrfToken: string | null = null;
export const AUTH_EVENT_KEY = "gevyro-auth-event";

export function limparDadosSessaoCliente(): void {
  if (typeof window === "undefined") return;

  removerTokenCookie();
  sessionStorage.removeItem("checkout_email");
  sessionStorage.removeItem("gevyro-request-cookie-consent-after-login");
  sessionStorage.removeItem("gevyro-require-legal-ack-after-login");
  localStorage.removeItem("gevyro-account-consents");
  localStorage.removeItem("gevyro-google-legal-ack");

  const chavesPorConta = [
    "gp_empresa_",
    "gp_caixa_",
    "empresa_ativa_uid_",
    "caixa_ativo_uid_",
  ];
  const chavesLegadas = new Set([
    "empresa_ativa",
    "caixa_ativo",
    "empresaAtiva",
    "caixaAtivo",
  ]);

  Object.keys(localStorage).forEach((key) => {
    if (chavesLegadas.has(key) || chavesPorConta.some((prefixo) => key.startsWith(prefixo))) {
      localStorage.removeItem(key);
    }
  });

  // O service worker atual é network-only, mas remove caches criados por
  // versões antigas para impedir que dados de outra conta reapareçam.
  if ("caches" in window) {
    void caches.keys().then((keys) => Promise.all(keys.map((key) => caches.delete(key))));
  }
}

function publicarLogout(): void {
  localStorage.setItem(AUTH_EVENT_KEY, JSON.stringify({ type: "logout", at: Date.now() }));
}

export async function obterCsrfToken(): Promise<string> {
  if (csrfToken) return csrfToken;
  const response = await fetch(`${API_BASE_URL}/auth/csrf`, { credentials: "include", cache: "no-store" });
  const data = await response.json().catch(() => null);
  if (!response.ok || !data?.token) throw new Error("Não foi possível iniciar uma sessão segura.");
  const token = String(data.token);
  csrfToken = token;
  return token;
}


/**
 * Fetch autenticado por cookie HttpOnly, com proteção CSRF em mutações.
 * Retorna Response - use para quando precisar verificar status manualmente.
 */
export async function fetchAuth(path: string, options: RequestInit = {}): Promise<Response> {
  const isFormData=typeof FormData!=="undefined"&&options.body instanceof FormData;
  const headers: Record<string, string> = {
    ...(!isFormData?{"Content-Type":"application/json"}:{}),
    ...(options.headers as Record<string, string> ?? {}),
  };

  const method = (options.method ?? "GET").toUpperCase();
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    headers["X-CSRF-TOKEN"] = await obterCsrfToken();
  }

  const cleanPath = path.startsWith('/') ? path : `/${path}`;
  const url = `${API_BASE_URL}${cleanPath}`;

  let response = await fetch(url, {
    ...options,
    headers,
    credentials: "include",
    cache: "no-store",
  });

  // O Spring pode rotacionar o token ao autenticar ou criar uma sessão.
  // Em mutações consecutivas (ex.: portal -> checkout), repete uma única vez
  // com o token novo quando o CSRF anterior foi invalidado.
  if (response.status === 403 && !["GET", "HEAD", "OPTIONS"].includes(method)) {
    csrfToken = null;
    headers["X-CSRF-TOKEN"] = await obterCsrfToken();
    response = await fetch(url, {
      ...options,
      headers,
      credentials: "include",
      cache: "no-store",
    });
  }

  return response;
}

/**
 * Fetch autenticado que já parseia o JSON e lança erro se não for ok.
 * Use esta função nas páginas para simplificar o código.
 */
export async function fetchAuthJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetchAuth(path, options);
  
  if (!response.ok) {
    const err = await response.json().catch(() => null);
    throw new Error(mensagemErroApi(err, response.status));
  }
  if (response.status === 204) return undefined as T;
  return response.json();
}


/**
 * Login com e-mail e senha. A credencial permanece apenas no cookie HttpOnly.
 */
export async function login(email: string, senha: string): Promise<Usuario> {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": await obterCsrfToken() },
    body: JSON.stringify({ email, senha }),
    credentials: "include",
    cache: "no-store",
  });

  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const mensagem = mensagemErroApi(data, response.status);
    throw new Error(
      mensagem === "NAO_AUTENTICADO"
        ? "E-mail ou senha inválidos."
        : mensagem
    );
  }

  // O Spring rotaciona/invalida o CSRF token ao autenticar.
  csrfToken = null;

  return {
    id:             Number.isSafeInteger(Number(data.id)) ? Number(data.id) : 0,
    nome:           data.nome,
    email:          data.email,
    foto:           data.foto || undefined,
    fotoUpload:     data.fotoUpload || undefined,
    tipoPlano:      data.tipoPlano,
    statusAcesso:   data.statusAcesso,
    expiracaoPlano: data.expiracaoPlano,
  };
}

/**
 * Cadastro de novo usuário (multipart/form-data).
 */
export async function cadastrar(
  nome: string,
  email: string,
  senha: string,
  foto?: File,
): Promise<void> {
  const formData = new FormData();
  formData.append("nome", nome);
  formData.append("email", email);
  formData.append("senha", senha);
  if (foto) formData.append("foto", foto);

  const response = await fetch(`${API_BASE_URL}/auth/cadastro`, {
    method: "POST",
    headers: { "X-CSRF-TOKEN": await obterCsrfToken() },
    body: formData,
    credentials: "include",
  });

  const data = await response.json().catch(() => null) as ErrorResponse | null;
  if (!response.ok) {
    throw new Error(mensagemErroApi(data, response.status));
  }
}

/**
 * Solicita um novo link sem revelar se o e-mail está cadastrado.
 */
export async function reenviarConfirmacao(email: string): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/auth/reenviar-confirmacao`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-TOKEN": await obterCsrfToken(),
    },
    body: JSON.stringify({ email: email.trim().toLowerCase() }),
    credentials: "include",
    cache: "no-store",
  });

  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(mensagemErroApi(data, response.status));
  return data?.mensagem ?? "Se houver uma conta pendente, enviaremos um novo link.";
}

/**
 * Logout — remove cookie local e invalida sessão no backend.
 */
export async function logout(): Promise<void> {
  const response = await fetchAuth("/auth/logout", { method: "POST" });
  if (!response.ok) {
    throw new Error("Não foi possível encerrar a sessão com segurança. Tente novamente.");
  }

  csrfToken = null;
  limparDadosSessaoCliente();
  publicarLogout();
}

/**
 * Obtém dados do usuário autenticado.
 * O navegador envia somente o cookie HttpOnly definido pelo backend.
 */
export async function getUsuario(): Promise<Usuario> {
  const response = await fetchAuth("/api/usuario");

  if (response.status === 401 || response.status === 403) {
    throw new Error(response.status === 403 ? "PLANO_INATIVO" : "NAO_AUTENTICADO");
  }

  const data = await response.json().catch(() => null);
  if (!response.ok || !data) {
    throw new Error(data?.erro || data?.mensagem || "Erro ao obter usuário");
  }

  return {
    id:             Number.isSafeInteger(Number(data.id)) ? Number(data.id) : 0,
    nome:           data.nome,
    email:          data.email,
    foto:           data.foto || undefined,
    fotoUpload:     data.fotoUpload || undefined,
    tipoPlano:      data.tipoPlano,
    statusAcesso:   data.statusAcesso,
    expiracaoPlano: data.expiracaoPlano,
  };
}

/**
 * Login com Google — redireciona para o backend iniciar o fluxo OAuth2.
 */
export function loginComGoogle() {
  globalThis.window.location.href = `${API_BASE_URL}/oauth2/authorization/google`;
}
