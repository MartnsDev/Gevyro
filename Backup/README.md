# Backup externo do MySQL

Serviço de execução única para Railway Cron. Ele cria um dump consistente do
MySQL pela rede privada, compacta, cifra com `age` e envia ao Google Drive via
`rclone`. Arquivos temporários são removidos ao final da execução.

## Variáveis obrigatórias

- `DB_HOST`: `mysql.railway.internal`
- `DB_PORT`: `3306`
- `DB_NAME`: `gestpro`
- `DB_USER`: referência para `${{MySQL.MYSQLUSER}}`
- `DB_PASSWORD`: referência para `${{MySQL.MYSQLPASSWORD}}`
- `RCLONE_CONFIG_BASE64`: conteúdo Base64 do arquivo de configuração local
- `BACKUP_ENCRYPTION_PASSPHRASE`: senha aleatória com no mínimo 24 caracteres

## Variáveis opcionais

- `BACKUP_REMOTE`: padrão `gestpro-drive:GestPro-Backups`
- `BACKUP_RETENTION_DAYS`: padrão `30`, mínimo `7`

`DB_PASSWORD`, `RCLONE_CONFIG_BASE64` e `BACKUP_ENCRYPTION_PASSPHRASE` devem ser
seladas na Railway. A senha de criptografia também deve ficar guardada fora da
Railway em um gerenciador de senhas; perdê-la torna os backups irrecuperáveis.

## Railway

Crie um serviço a partir deste mesmo repositório com Root Directory `/Backup`.
Configure o cron como `0 3 * * *` (03:00 UTC, meia-noite de Brasília) e mantenha
o serviço no mesmo projeto e ambiente do MySQL para usar a rede privada.

Execute manualmente uma vez antes de habilitar o cron e confirme no Drive a
presença do arquivo `.age` e de seu `.sha256`.
