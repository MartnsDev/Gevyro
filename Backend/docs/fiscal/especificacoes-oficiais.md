# Especificações oficiais do Gevyro Fiscal

Última verificação: 2026-09-01.

Este registro impede que layouts e regras fiscais sejam tratados como conhecimento implícito do código. Uma atualização só pode ser aplicada depois de revisão, testes de golden files/XSD e registro de auditoria da versão implantada.

## NF-e e NFC-e

- Leiaute dos Web Services: 4.00.
- Manual de Orientação do Contribuinte: versão 7.0.
- Pacote de schemas em implementação: `010e_v1.01`, publicado em 2026-06-26, contemplando NT 2025.002 v1.40, NT 2026.002 v1.0 e NT 2026.003 v1.0.
- Informe Técnico rastreado: 2025.002 v1.60.
- Serviços que a implementação direta deve tratar separadamente: autorização, retorno da autorização, inutilização, consulta protocolo/situação, status do serviço e recepção de eventos.
- Relação de autorizadores e endpoints não possui fallback universal: cada UF/autorizador, ambiente, modelo e serviço precisa de uma entrada oficial versionada.

Fontes oficiais:

- Portal Nacional, schemas: https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=BMPFMBoln3w=
- Portal Nacional, Web Services: https://www.nfe.fazenda.gov.br/portal/webServices.aspx
- Informe Técnico 2025.002 v1.60: https://www.nfe.fazenda.gov.br/portal/exibirArquivo.aspx?conteudo=jxTMMQeEVM8=

## NFS-e Padrão Nacional

- Schemas atuais rastreados: `NFSe-ESQUEMAS_XSD-v1.01-20260209`.
- Leiaute atual rastreado: `ANEXO_I-SEFIN_ADN-DPS_NFSe-SNNFSe-v1.01-20260209`.
- Domínios IBS/CBS rastreados: `ANEXO_C-INDOP_IBSCBS-SNNFSe-v1.01`.
- A integração será um provider próprio; não reutilizará o domínio nem o SOAP da NF-e.

Fonte oficial: https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual/documentacao-atual

## Decisões arquiteturais

- `FiscalProvider` isola regras de aplicação de integrações oficiais ou opcionais.
- `SEFAZ_DIRETO` é a implementação oficial para NF-e/NFC-e; estados sem endpoint oficial configurado devem falhar de forma explícita.
- Falha de transporte nunca é convertida em rejeição fiscal.
- Resultado desconhecido bloqueia reenvio cego; a consulta de situação deve ocorrer antes de qualquer nova transmissão.
- O circuit breaker envolve a fronteira externa, sem alterar validação, idempotência ou máquina de estados.
- A fila baseada em banco foi escolhida porque o projeto já depende de banco transacional e não possui RabbitMQ/Kafka. O job e a operação idempotente sobrevivem a reinícios e são reconciliados de forma transacional.

## Rate limiting distribuído

O limitador fiscal usa um script Lua atômico no Redis. A chave combina operação, empresa, hash do usuário e hash do endereço remoto; e-mail e IP não ficam legíveis no Redis. A indisponibilidade do Redis fecha operações fiscais sensíveis com HTTP 503, pois uma instância isolada não pode garantir o limite distribuído.

| Operação | Padrão | Variável de ambiente |
| --- | ---: | --- |
| Emissão e eventos críticos | 30/minuto | `FISCAL_RATE_EMISSAO_PER_MINUTE` |
| Consultas e downloads | 120/minuto | `FISCAL_RATE_CONSULTA_PER_MINUTE` |
| Upload/substituição de certificado | 5/hora | `FISCAL_RATE_CERTIFICADO_PER_HOUR` |
| Exportações em massa | 10/minuto | `FISCAL_RATE_EXPORTACAO_PER_MINUTE` |

Quando o limite é excedido a API retorna HTTP 429 e `Retry-After` calculado pelo TTL do contador Redis.

## Segredos de teste

Nenhuma chave criptográfica fixa é versionada. Testes criam uma chave AES descartável em memória. A variável `FISCAL_MASTER_KEY` de staging/produção deve vir do secret manager, possuir 32 bytes codificados em Base64 e nunca reutilizar qualquer valor visto em commits, logs, screenshots ou alertas de scanner.
