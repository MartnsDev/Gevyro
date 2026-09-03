# Gevyro Fiscal: arquitetura e operação

## Limites e fonte de verdade

O módulo fiscal é isolado por empresa e habilitado gradualmente. A legislação,
os XSDs e os portais oficiais são a fonte de verdade; sugestões tributárias da
interface sempre exigem validação do responsável fiscal. Integrações sem endpoint,
credencial ou adapter validado falham fechadas. Development e test usam
homologação e não recebem liberação implícita para produção.

## Componentes

- configuração e prontidão: cadastro do emitente, séries, ambiente e feature flags;
- autorização: papéis por empresa e permissões por operação;
- certificado: validação A1 e criptografia autenticada em repouso;
- sequência: reserva transacional por empresa, modelo, série e ambiente;
- idempotência/fila: operação e job persistidos, recuperção após reinício e
  consulta antes de repetir resultado desconhecido;
- XML: geração versionada, assinatura XMLDSig, validação XSD e preservação
  cifrada com SHA-256 do documento autorizado;
- providers: fronteira Strategy para SEFAZ direta e NFS-e Nacional/adapters;
- entrega: solicitações de e-mail entram em outbox transacional, com destinatário cifrado e índice cego HMAC para deduplicação. Sem despachante autorizado, permanecem em `AGUARDANDO_CONFIGURACAO` e nenhum dado sai da Gevyro;
- auditoria/observabilidade: trilha encadeada, correlation ID, métricas e health
  fiscal separado da saúde geral da aplicação.

## Estados e transições

`DIGITACAO` pode ser excluído ou submetido. A fila usa `PENDENTE_EMISSAO`,
`VALIDANDO` e `PROCESSANDO`. Uma resposta fiscal produz `AUTORIZADA` ou
`REJEITADA`; falha local/externa produz `ERRO_TECNICO` ou resultado desconhecido
no job, que agenda consulta. Somente autorizado pode receber cancelamento e CC-e
quando o modelo permite. `CONTINGENCIA` nasce apenas de uma ação manual de NFC-e
em digitação, com justificativa, reconfirmação e QR Code v3 assinado. A geração
offline não chama a SEFAZ.

Cancelamento fiscal não cancela venda nem devolve estoque. Emissão a partir de
venda referencia a movimentação existente e não baixa estoque novamente.

## Segurança operacional

- toda rota fiscal autentica e a camada de serviço valida empresa e permissão;
- alteração de configuração/certificado, eventos, inutilização e contingência
  exigem token opaco de uso único, TTL de cinco minutos e senha atual;
- Redis indisponível fecha rate limit e reconfirmação de operações sensíveis;
- DTOs limitam propriedades aceitas; IDs, status, protocolo e empresa do cliente
  nunca autorizam uma operação por si só;
- uploads possuem tipo/tamanho limitados; parsers XML desabilitam DTD e entidades;
- certificados, CSC, tokens, CPF/CNPJ e XML não entram em logs livres;
- Swagger e `/v3/api-docs` ficam desativados em produção.

Não existe promessa de "zero vulnerabilidades". O controle adotado é defesa em
profundidade, testes recorrentes, revisão de dependências e correção de achados.

## Recuperação de falhas

Jobs são persistidos no banco. Um worker reivindica com controle transacional,
usa backoff exponencial com jitter e recupera jobs abandonados. Timeout de
transmissão é resultado desconhecido: consultar a situação precede novo envio.
Rejeição fiscal não recebe retry infinito. Circuit breaker envolve somente a
fronteira externa, preservando o restante do ERP.

Backup usa dump consistente, compressão, `age`, SHA-256 e armazenamento externo.
`Backup/restore-verify.sh` restaura apenas em banco descartável terminado em
`_restore_test` e recusa o banco de origem.

## Configuração e segredos

Consulte `application.properties` para nomes e padrões. Em produção, forneça
JWT, `FISCAL_MASTER_KEY`, banco, Redis, OAuth, Stripe e SMTP por secret manager.
Nunca copie valores de exemplo ou testes. Origens CORS são allowlist explícita.
O certificado A1 é enviado uma vez, validado e nunca devolvido pela API.
`FISCAL_DELIVERY_EMAIL_ENABLED` permanece `false` por padrão. A flag sozinha não envia mensagens: um despachante externo revisado ainda precisa ser configurado e autorizado.

## Adicionar provider ou atualizar schema

1. registre a fonte oficial e data em `especificacoes-oficiais.md`;
2. versione metadados em `FiscalSpecificationVersion`;
3. adicione XSD com origem e SHA-256, sem download silencioso em runtime;
4. implemente `FiscalProvider` ou `NfseProvider`/adapter com capacidades explícitas;
5. valide ambiente, UF/município e certificados em inicialização;
6. crie testes unitários, integração, golden files e falhas externas;
7. habilite primeiro para uma empresa em homologação;
8. somente após homologação oficial e revisão humana permita produção.

## API e validação local

Em ambiente não produtivo, OpenAPI está em `/v3/api-docs` e Swagger UI em
`/swagger-ui.html`. A coleção `Backend/api-client/` usa variáveis e não versiona
tokens, senhas ou IDs reais. Antes de publicar: `./mvnw test`, `npm run typecheck`
e os workflows de dependency review e secret scan devem passar.
