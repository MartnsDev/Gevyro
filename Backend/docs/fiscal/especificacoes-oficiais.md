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
