# Deploying TutorsPoint

How TutorsPoint runs in production, how to deploy it for the first time, and how to release,
roll back and restore. Everything described here lives in [`deploy/`](deploy/).

- [How it fits together](#how-it-fits-together)
- [Environment variables](#environment-variables)
- [First deploy](#first-deploy)
- [Releasing](#releasing)
- [Rollback](#rollback)
- [Backups](#backups)
- [Restore](#restore)
- [Restore drill log](#restore-drill-log)
- [Routine operations](#routine-operations)

## How it fits together

One VPS running Docker Compose ([`deploy/compose.yml`](deploy/compose.yml)):

```
internet ──:80/:443──▶ nginx ──/api/*──▶ backend:8080 ──▶ postgres:5432
                        │                   │
                        └─ static bundle    └─ uploads volume
certbot  ── renews the TLS certificate through nginx's /.well-known/acme-challenge/
backup   ── nightly: pg_dump + uploads ─▶ encrypted (age) ─▶ S3-compatible storage off-server
```

| Service    | Image                                            | Published ports |
| ---------- | ------------------------------------------------ | --------------- |
| `nginx`    | `ghcr.io/dilshanrp2001/tutorspoint-frontend:<tag>` | 80, 443         |
| `backend`  | `ghcr.io/dilshanrp2001/tutorspoint-backend:<tag>`  | none            |
| `postgres` | `postgres:16`                                    | none            |
| `certbot`  | `certbot/certbot:v5.8.0`                         | none            |
| `backup`   | built on the server from `deploy/backup`         | none            |

- **Images.** CI builds both images on every push and publishes them to GHCR on every push to
  `main`, tagged `sha-<7-char commit>` and `latest`. The server always runs a pinned `sha-` tag
  (`BACKEND_TAG` / `FRONTEND_TAG` in `deploy/.env`), never `latest`, so a rollback is a matter of
  naming the previous tag.
- **Frontend image.** nginx carrying the built bundle. It owns how the bundle is served (cache
  rules, the page's Content-Security-Policy) in `tutorspoint-frontend/nginx/`. The deployment
  mounts [`deploy/nginx/templates/default.conf.template`](deploy/nginx/templates/default.conf.template)
  over the image's default server block to add TLS and the `/api` proxy. The bundle calls
  `/api` on its own origin, so there is no CORS in production.
- **Backend image.** JRE 21 on Alpine, running as uid 10001, with a `HEALTHCHECK` on
  `/actuator/health`. Only `/api/` is proxied; the actuator is not reachable from outside.
- **Schema.** Flyway migrates on startup, before the web server starts. The backend **refuses to
  start** on a schema it does not match: an edited migration, a migration from a newer release
  (see [Rollback](#rollback)), or a table changed by hand (Hibernate `ddl-auto=validate`).
  `SchemaMismatchStartupIT` proves all three cases.
- **Database roles.** `POSTGRES_USER` is the superuser. It is used only to create the cluster and
  by restores. The backend connects as `DB_USERNAME`, which owns the `DB_NAME` database and
  nothing else. [`deploy/postgres/init`](deploy/postgres/init/10-app-role.sh) creates both on
  the first start with an empty volume.
- **Volumes.** `postgres-data`, `uploads` (documents, photos, videos), `letsencrypt` and
  `certbot-webroot`. `docker compose down` keeps them. `down -v` deletes them: **never run it on
  the server.**

## Environment variables

All of these go in `deploy/.env` on the server. [`deploy/.env.example`](deploy/.env.example) is
the template. **No real value is ever committed.** `.env` is gitignored, and the file on the
server should be `chmod 600`, owned by the deploy user. Compose refuses to start at all when a
required variable is missing (`${VAR:?}` in `compose.yml`).

Keep a copy of the whole `.env` in the team password manager. It is needed to rebuild the server,
and it is not in any backup.

### Site and releases

| Variable            | Required | Example / how to generate | Used by |
| ------------------- | -------- | ------------------------- | ------- |
| `DOMAIN`            | yes | `tutorspoint.xyz`: the public host name, with no scheme | nginx (server name, certificate path), backend (`FRONTEND_BASE_URL=https://$DOMAIN`, the CORS origin and email links) |
| `LETSENCRYPT_EMAIL` | yes | an ops mailbox: Let's Encrypt sends expiry warnings here | `issue-certificate.sh` |
| `BACKEND_TAG`       | yes | `sha-1a2b3c4`, written by `deploy.sh` | backend image tag |
| `FRONTEND_TAG`      | yes | `sha-5d6e7f8`, written by `deploy.sh` | frontend image tag |
| `BACKEND_IMAGE`     | no  | default `ghcr.io/dilshanrp2001/tutorspoint-backend` | override only to test a locally built image |
| `FRONTEND_IMAGE`    | no  | default `ghcr.io/dilshanrp2001/tutorspoint-frontend` | as above |

### Database

| Variable            | Required | Example / how to generate | Used by |
| ------------------- | -------- | ------------------------- | ------- |
| `POSTGRES_USER`     | yes | `postgres` | postgres superuser: cluster creation and restores only |
| `POSTGRES_PASSWORD` | yes | `openssl rand -base64 24` | as above |
| `DB_NAME`           | yes | `tutorspoint` | the application database |
| `DB_USERNAME`       | yes | `tutorspoint` | the application's role, which owns `DB_NAME` |
| `DB_PASSWORD`       | yes | `openssl rand -base64 24` | backend, backup |
| `DB_POOL_SIZE`      | no  | default `10` | backend Hikari pool |

The postgres init script reads these **only when the data volume is empty**. Changing them later
does not change the database: rotate a password with `ALTER ROLE` first, then update `.env`.

### Backend

| Variable                      | Required | Example / how to generate | Notes |
| ----------------------------- | -------- | ------------------------- | ----- |
| `JWT_SECRET`                  | yes | `openssl rand -base64 32` | Signs access tokens. Rotating it signs everyone out. |
| `MAIL_HOST`                   | yes | `smtp.resend.com` | SMTP with STARTTLS |
| `MAIL_PORT`                   | yes | `587` | |
| `MAIL_USERNAME`               | yes | `resend` | |
| `MAIL_PASSWORD`               | yes | a Resend API key with sending access to the domain | |
| `MAIL_FROM`                   | yes | `no-reply@tutorspoint.xyz` | must be a verified sender domain |
| `MAIL_FROM_NAME`              | yes | `TutorsPoint` | |
| `PHONE_VERIFICATION_ENABLED`  | no  | default `false` | See the backend `.env.example` before turning this on. |
| `OTP_DELIVERY`                | no  | default `EMAIL`; `SMS` once the gateway is live | only read while phone verification is on |
| `SMS_NOTIFY_LK_BASE_URL`      | yes | `https://app.notify.lk/api/v1` | Validated at startup even while SMS is unused. Placeholders are fine until SMS goes live. |
| `SMS_NOTIFY_LK_USER_ID`       | yes | from the Notify.lk dashboard | as above |
| `SMS_NOTIFY_LK_API_KEY`       | yes | from the Notify.lk dashboard | as above |
| `SMS_NOTIFY_LK_SENDER_ID`     | yes | `TutorsPoint` (must be approved by Notify.lk) | as above |
| `ADMIN_BOOTSTRAP_EMAIL`       | first deploy | the first admin's email | Creates the first admin at startup. **Remove all four `ADMIN_BOOTSTRAP_*` lines after the first successful sign-in.** |
| `ADMIN_BOOTSTRAP_PASSWORD`    | first deploy | 12+ characters, from the password manager | |
| `ADMIN_BOOTSTRAP_FULL_NAME`   | no  | `TutorsPoint Admin` | |
| `ADMIN_BOOTSTRAP_PHONE`       | first deploy | E.164, e.g. `+9477…`, not used by any account | |
| `JAVA_TOOL_OPTIONS`           | no  | default `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true` | JVM flags |

Set by `compose.yml` or the image, never in `.env`: `SPRING_PROFILES_ACTIVE=prod`,
`DB_URL` (built from `DB_NAME`), `FRONTEND_BASE_URL` (built from `DOMAIN`),
`STORAGE_ROOT=/var/lib/tutorspoint/uploads` (the `uploads` volume) and `SERVER_PORT=8080`.

### Backups

| Variable                      | Required | Example / how to generate | Notes |
| ----------------------------- | -------- | ------------------------- | ----- |
| `BACKUP_REMOTE`               | yes | `offsite:tutorspoint-backups/production` | `offsite:` + bucket + path. **Create the bucket in the provider's console first.** The key is deliberately not allowed to create buckets, so a backup to a missing bucket fails with `NoSuchBucket`. |
| `BACKUP_S3_PROVIDER`          | yes | `Cloudflare`, `AWS`, `Backblaze`, `Wasabi`, `Minio`, … | rclone's S3 provider name |
| `BACKUP_S3_ENDPOINT`          | depends | `https://<account>.r2.cloudflarestorage.com` | empty for AWS |
| `BACKUP_S3_REGION`            | depends | `auto` (R2), `ap-south-1` (AWS) | |
| `BACKUP_S3_ACCESS_KEY_ID`     | yes | an access key scoped to **this bucket only** | |
| `BACKUP_S3_SECRET_ACCESS_KEY` | yes | as above | |
| `BACKUP_AGE_RECIPIENT`        | yes | `age1…`, the **public** half of the backup key ([generate](#backup-key)) | not a secret |
| `BACKUP_TIME_UTC`             | no  | default `20:30` (02:00 in Sri Lanka) | `HH:MM`, UTC |
| `BACKUP_RETENTION_DAYS`       | no  | default `30` | older backups are deleted after each run |
| `BACKUP_HEARTBEAT_URL`        | no  | a healthchecks.io ping URL | Pinged on success, and `…/fail` on failure. Set it, and set the check's period to one day: it is the only thing that notices a backup that silently stopped running. |

## First deploy

**Prerequisites:** a VPS (2 vCPU / 4 GB is comfortable) running Ubuntu 24.04 with Docker Engine
and the Compose plugin; a DNS `A` (and `AAAA`, if IPv6) record for `DOMAIN` pointing at it;
ports 80 and 443 open to the internet; the bucket created at the storage provider.

1. **Clone the deployment files** (as a non-root deploy user in the `docker` group):
   ```bash
   sudo mkdir -p /opt/tutorspoint && sudo chown "$USER" /opt/tutorspoint
   git clone https://github.com/dilshanrp2001/tutorspoint-backend.git /opt/tutorspoint
   cd /opt/tutorspoint/deploy
   ```
   Only `deploy/` is used on the server. The application arrives as images.

2. <a id="backup-key"></a>**Create the backup key, on your own machine, not the server:**
   ```bash
   age-keygen -o tutorspoint-backup.key     # prints "Public key: age1..."
   ```
   Put the **private** key file in the password manager, and in at least one more place the
   server's owner controls (for example an encrypted USB drive). Without it no backup can be
   restored. Only the public key (`age1…`) goes in `.env`.

3. **Fill in `.env`:**
   ```bash
   cp .env.example .env && chmod 600 .env && nano .env
   ```
   Set `BACKEND_TAG` and `FRONTEND_TAG` to the `sha-` tags of the commits on `main` you are
   deploying (GitHub → the repository → Packages, or the CI run's summary).

4. **Log in to GHCR** if the packages are private. Use a classic personal access token with
   only `read:packages`:
   ```bash
   echo "<token>" | docker login ghcr.io -u <github-user> --password-stdin
   ```

5. **Start the database** and check that the init script created the application role:
   ```bash
   docker compose up -d postgres
   docker compose exec postgres psql -U postgres -c '\l'      # DB_NAME, owned by DB_USERNAME
   ```

6. **Issue the certificate.** nginx must not be running yet:
   ```bash
   ./issue-certificate.sh --staging   # first, to prove DNS and port 80 without using up the rate limit
   docker compose run --rm --entrypoint certbot certbot delete --cert-name "$(grep ^DOMAIN= .env | cut -d= -f2)"
   ./issue-certificate.sh             # the real certificate
   ```

7. **Start everything:**
   ```bash
   ./deploy.sh --no-backup "$(grep ^BACKEND_TAG= .env | cut -d= -f2)" "$(grep ^FRONTEND_TAG= .env | cut -d= -f2)"
   ```
   The script waits for the backend's healthcheck, then requests `https://$DOMAIN/` and
   `/api/reference`.

8. **Sign in as the bootstrap admin** at `https://$DOMAIN/login`. Then remove the four
   `ADMIN_BOOTSTRAP_*` lines from `.env` and run `docker compose up -d backend`.

9. **Prove the backups work before anyone uses the site:**
   ```bash
   docker compose run --rm backup backup manual     # must end with "done"
   ```
   Then run a [restore drill](#restore-drill) and add a row to the [drill log](#restore-drill-log).

10. **Check from outside:** `https://$DOMAIN` loads over a valid certificate, `http://` redirects
    to it, and https://www.ssllabs.com/ssltest/ grades it A or better. Then run the end-to-end
    smoke test from the development plan (register a tutor, verify, publish, search, enquire,
    reply).

## Releasing

Merge to `main` in either repository. CI tests the commit and publishes the image as
`sha-<commit>`. Then, on the server:

```bash
cd /opt/tutorspoint/deploy
git pull                                    # when deploy/ itself changed
./deploy.sh sha-<backend> sha-<frontend>    # the current tag for a side that has not changed
```

`deploy.sh` pulls both images first, so a mistyped tag fails before anything changes. It then
takes a **`predeploy` backup**, writes the tags to `.env`, appends a line to `releases.log`,
recreates the containers and waits for the backend's healthcheck. The backend is healthy only
after Flyway has migrated and Hibernate has validated the schema. If it does not become healthy,
the script prints the backend's last log lines and the exact rollback command.

Expect a few seconds of 502s from `/api` while the backend restarts. Compose runs one replica,
and zero-downtime releases are out of scope for the pilot.

## Rollback

Find the previous tags in `deploy/releases.log`:

```bash
tail -n 5 releases.log
```

**If the bad release added no migration** (the usual case; `git diff <old>..<new> --stat --
src/main/resources/db/migration` in the backend repository shows nothing):

```bash
./deploy.sh --no-backup sha-<previous-backend> sha-<previous-frontend>
```

**If the bad release applied a migration**, the previous backend image will **refuse to start**
with `FlywayValidateException: … Detected applied migration not resolved locally`. This is
deliberate: the old code was never tested against the new schema. Pick one:

1. **Roll forward (preferred).** Fix the bug in a new commit. If the migration itself was wrong,
   add a new migration that corrects it. Migrations are never edited or deleted. Then release as
   usual.
2. **Restore the `predeploy` backup** that `deploy.sh` took just before the bad release. **This
   loses every write made since that release.** Weigh that against option 1.
   ```bash
   docker compose run --rm backup list | grep predeploy | tail -n 3    # pick the one just before the release
   docker compose stop backend
   # restore it: see "Restore" below, using that backup's name instead of "latest"
   ./deploy.sh --no-backup sha-<previous-backend> sha-<previous-frontend>
   ```

A frontend-only rollback never involves the database.

## Backups

The `backup` service runs [`tp-backup`](deploy/backup/tp-backup) every day at `BACKUP_TIME_UTC`.
Each run:

1. `pg_dump --format=custom` of `DB_NAME`, then `pg_restore --list` on the result to prove it is
   readable;
2. `tar` of the whole `uploads` volume. A database restored without these files would lose every
   verification document;
3. encrypts both with `age` to `BACKUP_AGE_RECIPIENT`, and writes a `MANIFEST` with the schema
   version, the file count and the SHA-256 of both **unencrypted** files;
4. uploads the three files to `$BACKUP_REMOTE/<yyyymmddThhmmssZ>-<label>/`, then `rclone check`s
   the uploaded copies against the local ones;
5. deletes backups older than `BACKUP_RETENTION_DAYS`, then pings `BACKUP_HEARTBEAT_URL`.

The server holds only the public key, so neither a stolen server nor a leaked bucket exposes a
backup. Labels are `nightly`, `predeploy` (from `deploy.sh`) and `manual`.

```bash
docker compose logs --tail 50 backup              # the last runs
docker compose run --rm backup list               # what is off-server
docker compose run --rm backup backup manual      # one now
```

Also enable versioning or object lock on the bucket if the provider offers it. The access key on
the server can delete objects, because pruning needs it to.

## Restore

The private backup key is needed. Copy it onto the server for the duration of the restore only:

```bash
cd /opt/tutorspoint/deploy
install -m 600 /dev/stdin /root/tutorspoint-backup.key   # paste the key, then Ctrl-D
KEY="-v /root/tutorspoint-backup.key:/run/secrets/age-identity:ro"
```

### Restore drill

A drill restores a backup into a scratch database (`<DB_NAME>_restore_drill`), checks it, then
drops it. **The live database is not touched and the site stays up.** Run one after the first
deploy, after any change to `deploy/backup`, and monthly.

```bash
docker compose --profile tools run --rm $KEY restore drill latest     # or a backup's name
```

It downloads and decrypts the backup and fails if either file's SHA-256 differs from the
`MANIFEST`. It restores with `pg_restore --single-transaction --exit-on-error`, then checks that:

- the restored schema version matches the one recorded at backup time;
- no migration in the restored history failed;
- every file the restored database references (documents, photos, videos) is in the restored
  uploads archive.

It also prints row counts for the main tables. It ends with `drill <name>: PASSED` or exits
non-zero.

### Restoring production

For when the live database is lost or corrupted, or for a rollback across a migration.

1. **Stop the backend**, so nothing writes during the restore:
   ```bash
   docker compose stop backend
   ```
2. **Restore.** Use a backup's name from `list` instead of `latest` for a specific one:
   ```bash
   docker compose --profile tools run --rm $KEY restore restore latest --yes --with-uploads
   ```
   The current database is **renamed**, not dropped, to `<DB_NAME>_before_restore_<timestamp>`.
   The backup is restored as a fresh `DB_NAME` owned by `DB_USERNAME`, and the same checks as
   the drill run against it. `--with-uploads` extracts the uploads archive over the volume. It
   never deletes files, so uploads newer than the backup are kept. Leave it off when only the
   database is bad.
3. **Start the backend on the image that matches the backup's schema** (`MANIFEST`
   `schema_version`, printed during the restore). The currently deployed image is right unless
   you are rolling back across a migration:
   ```bash
   docker compose up -d backend && docker compose ps backend     # wait for "healthy"
   ```
   A backup older than the image is fine: Flyway applies the missing migrations on startup. A
   backup newer than the image refuses to start, by design.
4. **Check the site**, sign in as an admin, and open a tutor's documents.
5. **Clean up:**
   ```bash
   shred -u /root/tutorspoint-backup.key
   # after a day or two, once you are sure of the restore:
   docker compose exec postgres psql -U postgres -c 'DROP DATABASE "tutorspoint_before_restore_<timestamp>"'
   ```

### Restoring onto a new server

When the VPS itself is gone: do [First deploy](#first-deploy) steps 1–6 on the new server, using
the `.env` from the password manager. Then `docker compose up -d postgres backup`, and do
[Restoring production](#restoring-production) from step 2. Finally run `./deploy.sh --no-backup`
with the tags from the last line of the old `releases.log`, or with the image matching the
backup's schema version.

## Restore drill log

Every drill and real restore gets a row. A backup that has never been restored is a hope, not a
backup.

| Date | Environment | Backup restored | Result | By |
| ---- | ----------- | --------------- | ------ | -- |
| 2026-09-18 | Local rehearsal of this exact `deploy/` stack (Docker Desktop; rclone's S3 server standing in for the bucket; self-signed certificate) | `20260918T145200Z-nightly`, from the scheduled run | **Passed**, see below | Claude Code, for Pramoth |

**What the 2026-09-18 rehearsal did.** It deployed the stack, registered a tutor through nginx,
verified the email from the real message, uploaded a photo and a PDF, and had the admin approve
the PDF. The scheduled backup then fired on time. The rehearsal then:

- **Drill:** `restore drill latest` passed. The same drill on a copy with one ciphertext byte
  changed failed at decryption (`age: failed to decrypt and authenticate`).
- **Total loss:** both the `postgres-data` and `uploads` volumes were deleted, and postgres was
  started empty. `restore restore latest --yes --with-uploads` passed its checks. The backend
  came up healthy on the restored database, the tutor signed in, and the restored photo and
  document downloaded through `https://…/api/…` byte-identical to the originals.
- **Rollback across a migration:** `deploy.sh` v1, then v2 (which added a migration V12), then
  back to v1. v1 refused to start (`Detected applied migration not resolved locally: 12`), and
  `deploy.sh` reported it after one restart. Restoring the `predeploy` backup taken before v2
  and running `deploy.sh --no-backup v1 v1` brought v1 back up healthy on V11.
- **Refusals:** `restore` refused without `--yes`, without the key, and while the backend held
  connections. A backup to a missing bucket exited non-zero.

Not covered by the rehearsal: Let's Encrypt issuance and renewal, which need a public domain
(the ACME webroot path through nginx was checked). The first-deploy drill on the real server is
still to be done and logged here.

## Routine operations

| Task | Command (in `/opt/tutorspoint/deploy`) |
| ---- | -------------------------------------- |
| Status | `docker compose ps` |
| Logs | `docker compose logs -f --tail 100 backend` (JSON, one object per line; `request_id` links a request's lines) |
| Restart the backend | `docker compose restart backend` |
| Certificate expiry | `docker compose run --rm --entrypoint certbot certbot certificates` |
| Force a renewal test | `docker compose run --rm --entrypoint certbot certbot renew --dry-run --webroot -w /var/www/certbot` |
| psql | `docker compose exec postgres psql -U "$(grep ^DB_USERNAME= .env | cut -d= -f2)" "$(grep ^DB_NAME= .env | cut -d= -f2)"` |
| Disk usage | `docker system df`; `docker image prune -a --filter "until=720h"` keeps a month of images to roll back to |

**Upgrading PostgreSQL** to a new major version is not an in-place image bump. A new major
cannot open the old data directory. Take a `manual` backup, bump `postgres:16` in
`compose.yml` **and** `FROM postgres:16-alpine` in `deploy/backup/Dockerfile`, start with a new
empty volume, and restore into it as in [Restoring onto a new server](#restoring-onto-a-new-server).
