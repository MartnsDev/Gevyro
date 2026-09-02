# Schemas oficiais da NFS-e Nacional v1.01

- Fonte: https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual/nfse-esquemas_xsd-v1-01-20260209.zip
- Pacote publicado: `NFSe-ESQUEMAS_XSD-v1.01-20260209`
- SHA-256 do ZIP: `e7935cbd9470527c6cc32984c1b2263e614183bf0139ce2733eaaed2de9a8072`
- Arquivos incorporados: conteúdo integral do diretório oficial `Schemas/1.01`.
- Hardening local: removido somente o `DOCTYPE` legado e não utilizado de
  `xmldsig-core-schema.xsd`, evitando resolução externa de DTD/XXE. Os tipos XSD
  e as regras fiscais do arquivo permanecem inalterados.
- Compatibilidade XSD 1.0: removidas as âncoras `^` e `$` do pattern de
  `TSSerieDPS`. Em XML Schema a expressão já exige correspondência integral e
  essas âncoras seriam literais; a alteração preserva a regra publicada de série
  numérica com 1 a 5 dígitos e até quatro zeros à esquerda.

Não atualizar silenciosamente. Novas versões devem ser revisadas, testadas e registradas separadamente.
