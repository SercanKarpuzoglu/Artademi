#!/bin/bash
# Postgres ilk başlatmada (boş data volume) çalışır.
# Ana DB (artademi) POSTGRES_DB ile otomatik açılır; burada ek olarak
# Keycloak'ın kullanacağı "keycloak" veritabanını oluşturuyoruz.
set -euo pipefail

DB_NAME="${KEYCLOAK_DB_NAME:-keycloak}"

# Zaten varsa tekrar yaratmayı dene ve hata verme
if psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -tAc "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" | grep -q 1; then
  echo "Veritabanı '${DB_NAME}' zaten mevcut, atlanıyor."
else
  echo "Veritabanı '${DB_NAME}' oluşturuluyor..."
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -c "CREATE DATABASE ${DB_NAME} OWNER ${POSTGRES_USER};"
fi
