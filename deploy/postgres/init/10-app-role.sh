#!/bin/sh
# Runs once, when the postgres container starts on an empty data volume.
#
# The application gets its own login role that owns its database and nothing else. It can
# create and alter tables there (Flyway needs to), but it is not a superuser: it cannot read
# other databases, create roles, or run COPY ... PROGRAM on the host's behalf.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
     -v app_user="$APP_DB_USER" -v app_password="$APP_DB_PASSWORD" -v app_db="$APP_DB_NAME" <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';
CREATE DATABASE :"app_db" OWNER :"app_user" ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE :"app_db" FROM PUBLIC;
SQL
