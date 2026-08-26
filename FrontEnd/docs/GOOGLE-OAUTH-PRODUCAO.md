# Google OAuth em produção

No cliente OAuth 2.0 do Google Cloud, configure exatamente:

## Origens JavaScript autorizadas

- `https://www.gevyro.com.br`
- `https://gevyro.com.br`
- `http://localhost:3000` (somente para desenvolvimento)

## URIs de redirecionamento autorizados

- `https://api.gevyro.com.br/login/oauth2/code/google`
- `http://localhost:8080/login/oauth2/code/google` (somente para desenvolvimento)

Não adicione `/dashboard`, `/auth/login` ou o domínio do frontend como callback. O Google
retorna ao backend; o backend valida a identidade, cria o cookie seguro e só então encaminha
o navegador para o frontend.

## Variáveis na Railway

```text
APP_PROFILE=prod
APP_BASE_URL=https://api.gevyro.com.br
APP_FRONTEND_URL=https://www.gevyro.com.br
CORS_ALLOWED_ORIGINS=https://www.gevyro.com.br,https://gevyro.com.br
GOOGLE_REDIRECT_URI=https://api.gevyro.com.br/login/oauth2/code/google
```

O valor de `GOOGLE_REDIRECT_URI` precisa ser idêntico, incluindo protocolo, domínio e caminho,
ao URI cadastrado no Google Cloud. Não use barra no final.
