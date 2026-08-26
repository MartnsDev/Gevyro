#!/usr/bin/env bash
set -Eeuo pipefail

required_variables=(
  DB_HOST
  DB_PORT
  DB_NAME
  DB_USER
  DB_PASSWORD
  RCLONE_CONFIG_BASE64
  BACKUP_ENCRYPTION_PASSPHRASE
)

for variable_name in "${required_variables[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "ERRO: variável obrigatória ausente: ${variable_name}" >&2
    exit 1
  fi
done

if [[ ${#BACKUP_ENCRYPTION_PASSPHRASE} -lt 24 ]]; then
  echo "ERRO: BACKUP_ENCRYPTION_PASSPHRASE deve ter pelo menos 24 caracteres." >&2
  exit 1
fi

backup_remote="${BACKUP_REMOTE:-gestpro-drive:GestPro-Backups}"
retention_days="${BACKUP_RETENTION_DAYS:-30}"

if ! [[ "$retention_days" =~ ^[0-9]+$ ]] || (( retention_days < 7 )); then
  echo "ERRO: BACKUP_RETENTION_DAYS deve ser um número igual ou superior a 7." >&2
  exit 1
fi

work_directory="$(mktemp -d)"
config_file="${work_directory}/rclone.conf"
timestamp="$(date -u +'%Y-%m-%dT%H-%M-%SZ')"
backup_name="gestpro-mysql-${timestamp}.sql.gz.age"
compressed_file="${work_directory}/gestpro.sql.gz"
encrypted_file="${work_directory}/${backup_name}"
checksum_file="${encrypted_file}.sha256"

cleanup() {
  rm -rf -- "$work_directory"
}
trap cleanup EXIT

umask 077
printf '%s' "$RCLONE_CONFIG_BASE64" | base64 -d > "$config_file"

echo "Iniciando backup MySQL em ${timestamp}."

export MYSQL_PWD="$DB_PASSWORD"
mariadb-dump \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --user="$DB_USER" \
  --databases "$DB_NAME" \
  --single-transaction \
  --quick \
  --routines \
  --events \
  --triggers \
  --hex-blob \
  --default-character-set=utf8mb4 \
  | gzip -9 > "$compressed_file"
unset MYSQL_PWD

if [[ ! -s "$compressed_file" ]]; then
  echo "ERRO: o dump produzido está vazio." >&2
  exit 1
fi

export AGE_PASSPHRASE="$BACKUP_ENCRYPTION_PASSPHRASE"
age --passphrase --output "$encrypted_file" "$compressed_file"
unset AGE_PASSPHRASE

(
  cd "$work_directory"
  sha256sum "$backup_name" > "${backup_name}.sha256"
)

rclone --config "$config_file" copyto \
  "$encrypted_file" "${backup_remote}/${backup_name}" \
  --checkers 4 \
  --transfers 1 \
  --retries 3

rclone --config "$config_file" copyto \
  "$checksum_file" "${backup_remote}/${backup_name}.sha256" \
  --retries 3

rclone --config "$config_file" delete "$backup_remote" \
  --min-age "${retention_days}d" \
  --include 'gestpro-mysql-*.sql.gz.age' \
  --include 'gestpro-mysql-*.sql.gz.age.sha256' \
  --retries 3

encrypted_size="$(du -h "$encrypted_file" | cut -f1)"
echo "Backup concluído: ${backup_name} (${encrypted_size}), retenção de ${retention_days} dias."
