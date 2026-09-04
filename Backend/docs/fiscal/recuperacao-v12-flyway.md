# Recuperação segura da migration V12

## Sintoma

O backend não inicia e o Flyway informa `Detected failed migration to version 12`.
A versão originalmente publicada utilizava sintaxe de identidade incompatível
com MySQL. A migration do repositório agora usa `AUTO_INCREMENT`.

## Procedimento operacional

Não desative a validação do Flyway e não habilite `repair` automático no boot.
Antes de modificar o histórico, faça backup e execute consultas somente leitura:

```sql
SELECT installed_rank, version, description, script, checksum, success
FROM flyway_schema_history
WHERE version = '12';

SELECT COUNT(*) AS tabela_v12_existente
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'fiscal_company_access';
```

O reparo só é seguro para este incidente quando existe exatamente uma linha V12
com `success = 0` e `tabela_v12_existente = 0`. Se a tabela existir, interrompa:
há DDL parcial que precisa de inspeção antes de qualquer alteração.

Depois do backup e dessas duas confirmações, execute o comando oficial do Flyway
`repair` usando a mesma URL, usuário e versão do runtime de produção. Não coloque
credenciais na linha de comando, no Git ou em logs. Em seguida, faça um novo
deploy; o Flyway aplicará a V12 corrigida e continuará pelas migrations seguintes.

Após subir, confirme:

```sql
SELECT version, description, success
FROM flyway_schema_history
WHERE version BETWEEN '12' AND '16'
ORDER BY installed_rank;
```

Todas as linhas devem estar com `success = 1`. Preserve o backup até concluir os
testes de login, seleção de empresa e acesso ao módulo fiscal.
