# Recuperação segura da migration V12

## Sintoma

O backend não inicia e o Flyway informa `Detected failed migration to version 12`.
A versão originalmente publicada utilizava sintaxe de identidade incompatível
com MySQL. A migration do repositório agora usa `AUTO_INCREMENT`.

## Procedimento operacional

O backend possui uma recuperação transitória no perfil `prod`. Antes de o
Flyway migrar, ela verifica exatamente as mesmas condições abaixo. Quando há uma
única V12 falha e a tabela-alvo não existe, remove somente esse marcador dentro
de transação e deixa o Flyway aplicar a V12 corrigida. Depois do sucesso, o
mecanismo fica inerte. Se encontrar DDL parcial ou estado divergente, o deploy
continua bloqueado.

As consultas abaixo permanecem úteis para auditoria e diagnóstico manual.

Não desative a validação do Flyway e não habilite `repair` amplo no boot.
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

Caso a recuperação controlada recuse o estado, não apague tabelas nem linhas do
histórico. Restaure/inspecione o backup e trate o DDL parcial manualmente antes de
um novo deploy. Não coloque credenciais na linha de comando, no Git ou em logs.

Após subir, confirme:

```sql
SELECT version, description, success
FROM flyway_schema_history
WHERE version BETWEEN '12' AND '16'
ORDER BY installed_rank;
```

Todas as linhas devem estar com `success = 1`. Preserve o backup até concluir os
testes de login, seleção de empresa e acesso ao módulo fiscal.
