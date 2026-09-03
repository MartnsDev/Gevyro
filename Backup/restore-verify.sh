#!/usr/bin/env bash
set -Eeuo pipefail

required_variables=(DB_HOST DB_PORT DB_USER DB_PASSWORD DB_NAME RESTORE_TEST_DB AGE_IDENTITY_BASE64 BACKUP_FILE)
for variable_name in "${required_variables[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "ERRO: variável obrigatória ausente: ${variable_name}" >&2
    exit 1
  fi
done

if [[ ! "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "ERRO: DB_NAME possui formato inseguro." >&2
  exit 1
fi
if [[ ! "$RESTORE_TEST_DB" =~ ^[A-Za-z0-9_]+_restore_test$ ]]; then
  echo "ERRO: RESTORE_TEST_DB deve terminar com _restore_test." >&2
  exit 1
fi
if [[ "$RESTORE_TEST_DB" == "$DB_NAME" ]]; then
  echo "ERRO: o banco de restauração jamais pode ser o banco de origem." >&2
  exit 1
fi
if [[ ! -f "$BACKUP_FILE" || "$BACKUP_FILE" != *.sql.gz.age ]]; then
  echo "ERRO: BACKUP_FILE deve apontar para um arquivo .sql.gz.age existente." >&2
  exit 1
fi

work_directory="$(mktemp -d)"
identity_file="${work_directory}/identity.age"
sql_file="${work_directory}/restore.sql"
cleanup() {
  rm -rf -- "$work_directory"
}
trap cleanup EXIT
umask 077

printf '%s' "$AGE_IDENTITY_BASE64" | base64 -d > "$identity_file"
age --decrypt --identity "$identity_file" "$BACKUP_FILE" | gzip -dc > "$sql_file"
if [[ ! -s "$sql_file" ]]; then
  echo "ERRO: o backup descriptografado está vazio." >&2
  exit 1
fi

export MYSQL_PWD="$DB_PASSWORD"
mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" \
  --execute="CREATE DATABASE IF NOT EXISTS \`${RESTORE_TEST_DB}\`;"

# O dump contém CREATE/USE do banco original. Reescrevemos apenas essas
# diretivas estritamente delimitadas para manter a restauração isolada.
sed -E \
  -e "s/^CREATE DATABASE .*\`${DB_NAME}\`.*/CREATE DATABASE IF NOT EXISTS \`${RESTORE_TEST_DB}\`;/" \
  -e "s/^USE \`${DB_NAME}\`;/USE \`${RESTORE_TEST_DB}\`;/" \
  "$sql_file" | mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER"

table_count="$(mysql --batch --skip-column-names --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" \
  --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${RESTORE_TEST_DB}';")"
unset MYSQL_PWD

if ! [[ "$table_count" =~ ^[0-9]+$ ]] || (( table_count < 1 )); then
  echo "ERRO: restauração concluída sem tabelas verificáveis." >&2
  exit 1
fi
echo "Restauração verificada com sucesso em banco descartável (${table_count} tabelas)."
