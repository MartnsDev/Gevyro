# Backup externo do MySQL

Serviço de execução única para Railway Cron. Ele cria um dump consistente do
MySQL pela rede privada, compacta, cifra com `age` e envia ao Google Drive via
`rclone`. Arquivos temporários são removidos ao final da execução.

O serviço usa o cliente oficial do MySQL 9.4, compatível com a autenticação
`caching_sha2_password` do banco.

## Variáveis obrigatórias

- `DB_HOST`: `mysql.railway.internal`
- `DB_PORT`: `3306`
- `DB_NAME`: `gestpro`
- `DB_USER`: referência para `${{MySQL.MYSQLUSER}}`
- `DB_PASSWORD`: referência para `${{MySQL.MYSQLPASSWORD}}`
- `RCLONE_CONFIG_BASE64`: conteúdo Base64 do arquivo de configuração local
- `BACKUP_AGE_RECIPIENT`: chave pública `age1...` usada para criptografar

## Variáveis opcionais

- `BACKUP_REMOTE`: padrão `gestpro-drive:GestPro-Backups`
- `BACKUP_RETENTION_DAYS`: padrão `30`, mínimo `7`

`DB_PASSWORD` e `RCLONE_CONFIG_BASE64` devem ser seladas na Railway. A chave
privada correspondente ao `BACKUP_AGE_RECIPIENT` deve ficar somente fora da
Railway, guardada em um gerenciador de senhas e em uma cópia offline segura.
Perdê-la torna os backups irrecuperáveis.

## Railway

Crie um serviço a partir deste mesmo repositório com Root Directory `/Backup`.
Configure o cron como `0 3 * * *` (03:00 UTC, meia-noite de Brasília) e mantenha
o serviço no mesmo projeto e ambiente do MySQL para usar a rede privada.

Execute manualmente uma vez antes de habilitar o cron e confirme no Drive a
presença do arquivo `.age` e de seu `.sha256`.

## Teste de restauração

O arquivo `restore-verify.sh` descriptografa e restaura um backup em um banco
descartável. Ele recusa o banco de origem e exige que o destino termine em
`_restore_test`, reduzindo o risco de sobrescrita acidental. Use uma instância
isolada ou um usuário MySQL limitado ao banco de teste.

Variáveis adicionais:

- `RESTORE_TEST_DB`: nome descartável terminado em `_restore_test`;
- `AGE_IDENTITY_BASE64`: chave privada age codificada em Base64 e fornecida por
  secret manager somente durante o teste;
- `BACKUP_FILE`: caminho local para o `.sql.gz.age` já conferido pelo SHA-256.

Execute no contêiner substituindo o entrypoint por
`/backup/restore-verify.sh`. Apague o banco descartável após inspecionar a
contagem e amostras esperadas; o script deliberadamente não o remove para que
uma falha possa ser investigada. Nunca configure a chave privada no serviço
regular de backup.
