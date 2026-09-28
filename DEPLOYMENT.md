# Deploying TutorsPoint

How TutorsPoint runs in production, how to deploy it for the first time, and how to release,
roll back and recover. Everything described here lives in [`deploy/`](deploy/).

- [How it fits together](#how-it-fits-together)
- [Environment variables](#environment-variables)
- [First deploy](#first-deploy)
- [Releasing](#releasing)
- [Rollback](#rollback)
- [Data and recovery](#data-and-recovery)
- [Moving to another server](#moving-to-another-server)
- [Routine operations](#routine-operations)

## How it fits together

One VPS running Docker Compose ([`deploy/compose.yml`](deploy/compose.yml)), and a hosted
PostgreSQL database on [Neon](https://neon.com):

```
internet ──:80/:443──▶ nginx ──/api/*──▶ backend:8080 ──TLS──▶ Neon PostgreSQL 16 (Singapore)
                        │                   │
                        └─ static bundle    └─ uploads volume (server disk)
certbot  ── renews the TLS certificate through nginx's /.well-known/acme-challenge/
```

| Service    | Image                                              | Published ports |
| ---------- | -------------------------------------------------- | --------------- |
| `nginx`    | `ghcr.io/dilshanrp2001/tutorspoint-frontend:<tag>` | 80, 443         |
| `backend`  | `ghcr.io/dilshanrp2001/tutorspoint-backend:<tag>`  | none            |
| `certbot`  | `certbot/certbot:v5.8.0`                           | none            |

- **Images.** CI builds both images on every push and publishes them to GHCR on every push to
  `main`, tagged `sha-<7-char commit>` and `latest`. The server always runs a pinned `sha-` tag
  (`BACKEND_TAG` / `FRONTEND_TAG` in `deploy/.env`), never `latest`, so a rollback is a matter of
  naming the previous tag. The images are amd64 only.
- **Frontend image.** nginx carrying the built bundle. It owns how the bundle is served (cache
  rules, the page's Content-Security-Policy) in `tutorspoint-frontend/nginx/`. The deployment
  mounts [`deploy/nginx/templates/default.conf.template`](deploy/nginx/templates/default.conf.template)
  over the image's default server block to add TLS, the `/api` proxy and the `www` → bare-domain
  redirect. The bundle calls `/api` on its own origin, so there is no CORS in production.
- **Backend image.** JRE 21 on Alpine, running as uid 10001, with a `HEALTHCHECK` on
  `/actuator/health`. Only `/api/` is proxied; the actuator is not reachable from outside.
- **Database.** Neon, over TLS, on the **direct** (not pooled) endpoint: Flyway migrates over
  the application's own connection at startup, and Neon's pooler (PgBouncer in transaction
  mode) breaks the session-level lock Flyway takes. The backend connects as `DB_USERNAME`, which
  owns the database and nothing else.
- **Scale to zero.** Neon's free compute sleeps after 5 minutes without queries, and its monthly
  compute hours do not cover a database that is awake all month. So `compose.yml` lets the
  connection pool drain when idle (no minimum, no background keepalive), and keeps the database
  out of the container healthcheck. The first request after a quiet spell waits a moment while
  Neon wakes up; sleeping drops the open connections, and the pool simply opens new ones.
- **Schema.** Flyway migrates on startup, before the web server starts. The backend **refuses to
  start** on a schema it does not match: an edited migration, a migration from a newer release
  (see [Rollback](#rollback)), or a table changed by hand (Hibernate `ddl-auto=validate`).
  `SchemaMismatchStartupIT` proves all three cases. Since the healthcheck no longer queries the
  database, this is what "healthy" means: the backend only serves once the schema matched.
- **Volumes.** `uploads` (documents, photos, videos), `letsencrypt` and `certbot-webroot`.
  `docker compose down` keeps them. `down -v` deletes them: **never run it on the server.**
  **`uploads` is not backed up** (see [Data and recovery](#data-and-recovery)).

## Environment variables

All of these go in `deploy/.env` on the server. [`deploy/.env.example`](deploy/.env.example) is
the template. **No real value is ever committed.** `.env` is gitignored, and the file on the
server should be `chmod 600`, owned by the deploy user. Compose refuses to start at all when a
required variable is missing (`${VAR:?}` in `compose.yml`).

Keep a copy of the whole `.env` in the team password manager. It is needed to rebuild the server.

### Site and releases

| Variable            | Required | Example / how to generate | Used by |
| ------------------- | -------- | ------------------------- | ------- |
| `DOMAIN`            | yes | `tutorspoint.xyz`: the public host name, with no scheme | nginx (server name, certificate path, `www` redirect), backend (`FRONTEND_BASE_URL=https://$DOMAIN`, the CORS origin and email links) |
| `LETSENCRYPT_EMAIL` | yes | an ops mailbox: Let's Encrypt sends expiry warnings here | `issue-certificate.sh` |
| `BACKEND_TAG`       | yes | `sha-1a2b3c4`, written by `deploy.sh` | backend image tag |
| `FRONTEND_TAG`      | yes | `sha-5d6e7f8`, written by `deploy.sh` | frontend image tag |
| `BACKEND_IMAGE`     | no  | default `ghcr.io/dilshanrp2001/tutorspoint-backend` | override only to test a locally built image |
| `FRONTEND_IMAGE`    | no  | default `ghcr.io/dilshanrp2001/tutorspoint-frontend` | as above |

### Database

| Variable       | Required | Example / how to generate | Notes |
| -------------- | -------- | ------------------------- | ----- |
| `DB_URL`       | yes | `jdbc:postgresql://ep-….ap-southeast-1.aws.neon.tech/tutorspoint?sslmode=require&channelBinding=require` | Neon's **direct** endpoint (no `-pooler` in the host), in JDBC form: see [Neon](#neon). |
| `DB_USERNAME`  | yes | `tutorspoint` | the Neon role that owns the database |
| `DB_PASSWORD`  | yes | the role's password, from the Neon console | Resetting it in Neon means updating `.env` and running `docker compose up -d backend`. |
| `DB_POOL_SIZE` | no  | default `5` | backend Hikari pool (maximum; it drains to zero when idle) |

### Backend

| Variable                      | Required | Example / how to generate | Notes |
| ----------------------------- | -------- | ------------------------- | ----- |
| `JWT_SECRET`                  | yes | `openssl rand -base64 32` | Signs access tokens. Rotating it signs everyone out. |
| `MAIL_HOST`                   | yes | `smtp.resend.com` | SMTP with STARTTLS |
| `MAIL_PORT`                   | yes | `587` | |
| `MAIL_USERNAME`               | yes | `resend` | |
| `MAIL_PASSWORD`               | yes | a Resend API key with sending access to the domain | |
| `MAIL_FROM`                   | yes | `no-reply@tutorspoint.xyz` | must be a verified sender domain ([Email DNS](#email-dns)) |
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
`FRONTEND_BASE_URL` (built from `DOMAIN`), the pool and healthcheck settings for scale to zero
(`SPRING_DATASOURCE_HIKARI_*`, `MANAGEMENT_HEALTH_DB_ENABLED=false`),
`STORAGE_ROOT=/var/lib/tutorspoint/uploads` (the `uploads` volume) and `SERVER_PORT=8080`.

## First deploy

**Prerequisites:** the [Neon](#neon) database; a VPS ([Server: AWS EC2](#server-aws-ec2))
running Ubuntu 24.04 with Docker Engine and the Compose plugin; a DNS `A` record for `DOMAIN`
pointing at it, and `CNAME www → DOMAIN` (the certificate covers both, and nginx redirects `www`
to the bare domain); ports 80 and 443 open to the internet; the sending domain verified at the
mail provider ([Email DNS](#email-dns)).

### Neon

1. **Create the project:** Postgres version **16** (the version development and the tests run,
   so the schema and text sorting behave the same), region **AWS Asia Pacific (Singapore)**,
   the closest to Sri Lanka. The region cannot be changed later.
2. **Limit the compute** (the branch's compute → Edit): autoscaling 0.25 → 0.25 CU. The free
   plan's monthly compute hours are counted in CU-hours: a compute allowed to scale up uses them
   several times faster, and this site does not need more.
3. **Create the role and database:** Roles → New role `tutorspoint` (copy its password: it is
   shown once), then Databases → New database `tutorspoint`, owner `tutorspoint`. The default
   `neondb` database and its owner are not used.
4. **Get the connection string:** Connect → database `tutorspoint`, role `tutorspoint`,
   **Connection pooling off**. From
   `postgresql://tutorspoint:<password>@ep-xxx.ap-southeast-1.aws.neon.tech/tutorspoint?sslmode=require&channel_binding=require`
   make:
   ```
   DB_URL=jdbc:postgresql://ep-xxx.ap-southeast-1.aws.neon.tech/tutorspoint?sslmode=require&channelBinding=require
   DB_USERNAME=tutorspoint
   DB_PASSWORD=<password>
   ```
   The user and password leave the URL, and `channel_binding` becomes `channelBinding`, the
   JDBC driver's name for it (the driver silently ignores the other spelling).

Free-plan limits to keep an eye on (Neon console → Usage): 0.5 GB of storage and 100 CU-hours
of compute a month (about 400 hours at 0.25 CU, which scale to zero makes last the month), and a
6-hour [restore window](#data-and-recovery).

### Server: AWS EC2

The pilot runs on AWS's Free plan: new accounts get credits that last 6 months, or until they
run out. **Before that date, upgrade the account to paid or move the server**
([Moving to another server](#moving-to-another-server)). A Free-plan account that is not
upgraded is closed, and the server's disk with it: the database is on Neon and survives that,
**the uploaded files do not**.

- **Instance:** `t3.small` (2 vCPU / 2 GB) in **`ap-southeast-1` (Singapore)**, next to the
  database: every query crosses between the two, and a request makes several. Ubuntu 24.04 LTS
  x86_64, 30 GB gp3. The images are amd64 only, so don't pick a Graviton (`t4g`) type.
- **Security group:** 22 from your own IP only; 80 and 443 from `0.0.0.0/0` and `::/0`.
- **Elastic IP:** allocate one and associate it, so the address in DNS survives a stop and start.
- **Budget:** AWS Budgets → a $5 cost budget with email alerts, to catch anything that is not
  covered by credits.
- **Too small?** If the backend restarts with `OutOfMemoryError`: stop the instance, change its
  type to `c7i-flex.large` (4 GB), start it. The volume and the Elastic IP stay, so nothing
  else changes.

### Email DNS

Add the domain at Resend. Then copy the records it shows into the DNS provider (Spaceship:
Domain Manager → the domain → Advanced DNS): the DKIM `TXT` at `resend._domainkey`, and the
SPF `MX` and `TXT` at `send`. Also add `_dmarc TXT "v=DMARC1; p=none;"`. Once Resend shows the
domain as verified, create an API key with sending access: that key is `MAIL_PASSWORD`. Mail
from an unverified domain is refused, or lands in spam.

Spaceship adds parking records to a new domain. **Delete them** before adding the `A` and `www`
records, or some visitors will be sent to the parking page.

### Steps

0. **Prepare the server** (once, over SSH as `ubuntu`):
   ```bash
   sudo apt-get update && sudo apt-get -y upgrade
   curl -fsSL https://get.docker.com | sudo sh        # Docker Engine + the Compose plugin
   sudo usermod -aG docker "$USER"                    # then log out and back in
   docker compose version                             # after logging back in: prints a version
   ```
   Unattended security updates are on by default in Ubuntu's AWS images.

1. **Clone the deployment files:**
   ```bash
   sudo mkdir -p /opt/tutorspoint && sudo chown "$USER" /opt/tutorspoint
   git clone https://github.com/dilshanrp2001/tutorspoint-backend.git /opt/tutorspoint
   cd /opt/tutorspoint/deploy
   ```
   Only `deploy/` is used on the server. The application arrives as images.

2. **Fill in `.env`:**
   ```bash
   cp .env.example .env && chmod 600 .env && nano .env
   ```
   Set `BACKEND_TAG` and `FRONTEND_TAG` to the `sha-` tags of the commits on `main` you are
   deploying (GitHub → the repository → Packages, or the CI run's summary), and the three
   database values from [Neon](#neon).

3. **Log in to GHCR** if the packages are private. Use a classic personal access token with
   only `read:packages`:
   ```bash
   echo "<token>" | docker login ghcr.io -u <github-user> --password-stdin
   ```

4. **Issue the certificate.** nginx must not be running yet:
   ```bash
   ./issue-certificate.sh --staging   # first, to prove DNS and port 80 without using up the rate limit
   docker compose run --rm --entrypoint certbot certbot delete --cert-name "$(grep ^DOMAIN= .env | cut -d= -f2)"
   ./issue-certificate.sh             # the real certificate
   ```

5. **Start everything:**
   ```bash
   ./deploy.sh "$(grep ^BACKEND_TAG= .env | cut -d= -f2)" "$(grep ^FRONTEND_TAG= .env | cut -d= -f2)"
   ```
   Flyway creates the schema in the empty Neon database on this first start. The script waits
   for the backend's healthcheck, then requests `https://$DOMAIN/` and `/api/reference`. If the
   backend does not come up, its log names the problem: a wrong `DB_URL` or password shows as a
   connection or authentication error before any migration runs.

6. **Sign in as the bootstrap admin** at `https://$DOMAIN/login`. Then remove the four
   `ADMIN_BOOTSTRAP_*` lines from `.env` and run `docker compose up -d backend`.

7. **Check from outside:** `https://$DOMAIN` loads over a valid certificate, `http://` redirects
   to it, `https://www.$DOMAIN` redirects to the bare domain, and
   https://www.ssllabs.com/ssltest/ grades it A or better. Then run the end-to-end smoke test
   from the development plan (register a tutor, verify, publish, search, enquire, reply).
   Finally, leave the site alone for 10 minutes and check that Neon shows the compute as
   **Idle**: if it never goes idle, something is keeping it awake and the month's compute hours
   will run out.

## Releasing

Merge to `main` in either repository. CI tests the commit and publishes the image as
`sha-<commit>`. Then, on the server:

```bash
cd /opt/tutorspoint/deploy
git pull                                    # when deploy/ itself changed
./deploy.sh sha-<backend> sha-<frontend>    # the current tag for a side that has not changed
```

`deploy.sh` pulls both images first, so a mistyped tag fails before anything changes. It then
writes the tags to `.env`, appends a line with the UTC time to `releases.log`, recreates the
containers and waits for the backend's healthcheck. The backend is healthy only after Flyway has
migrated and Hibernate has validated the schema. If it does not become healthy, the script
prints the backend's last log lines and the exact rollback command.

Expect a few seconds of 502s from `/api` while the backend restarts. Compose runs one replica,
and zero-downtime releases are out of scope for the pilot.

**A release that adds a migration** can only be undone within Neon's 6-hour restore window (see
[Rollback](#rollback)). For a risky migration, create a Neon branch just before releasing
(Branches → New branch, from the production branch, "now"): it is a snapshot that does not
expire, and costs nothing while the two are the same. Delete it once the release has proved
itself.

## Rollback

Find the previous tags in `deploy/releases.log`:

```bash
tail -n 5 releases.log
```

**If the bad release added no migration** (the usual case; `git diff <old>..<new> --stat --
src/main/resources/db/migration` in the backend repository shows nothing):

```bash
./deploy.sh sha-<previous-backend> sha-<previous-frontend>
```

**If the bad release applied a migration**, the previous backend image will **refuse to start**
with `FlywayValidateException: … Detected applied migration not resolved locally`. This is
deliberate: the old code was never tested against the new schema. Pick one:

1. **Roll forward (preferred).** Fix the bug in a new commit. If the migration itself was wrong,
   add a new migration that corrects it. Migrations are never edited or deleted. Then release as
   usual.
2. **Restore Neon to just before the release**, only possible within the 6-hour restore window
   (or from a branch taken before it). **This loses every write made since that time.** Weigh
   that against option 1.
   ```bash
   docker compose stop backend
   # Neon console → Branches → the production branch → Restore → "From history",
   # the time from releases.log (UTC) minus a minute → Restore
   ./deploy.sh sha-<previous-backend> sha-<previous-frontend>
   ```

A frontend-only rollback never involves the database.

## Data and recovery

There is **no backup job**. What protects each kind of data:

| Data | Where it lives | Protection |
| ---- | -------------- | ---------- |
| Database (accounts, profiles, enquiries) | Neon | Point-in-time restore within the last **6 hours** (free plan), plus any branch you create by hand. |
| Uploaded files (documents, photos, videos) | the `uploads` volume on the server's disk | **None.** A lost disk or a closed AWS account loses them. |
| Configuration (`.env`) | the server | the copy in the password manager |

**Restoring the database** after a bad write, a mistaken deletion or corruption, within the
window:

1. `docker compose stop backend`, so nothing writes during the restore.
2. Neon console → Branches → the production branch → Restore → "From history" → a time just
   before the problem. Neon keeps the replaced state as a backup branch, so a wrong choice can
   itself be undone.
3. `docker compose up -d backend`, and wait for `healthy`. A restore to before a migration is
   fine: Flyway applies it again on startup.
4. Check the site, sign in as an admin, and open a tutor's documents. Documents uploaded after
   the restore point are still on disk but no longer referenced; nothing else is affected.

**Copying the uploads off the server** by hand, before any risky operation, and before the AWS
Free plan ends:

```bash
docker run --rm -v tutorspoint_uploads:/u:ro alpine tar czf - -C /u . > ~/uploads-$(date -u +%Y%m%d).tgz
# then, from your own machine:
scp <server>:~/uploads-*.tgz .
```

## Moving to another server

For the end of the AWS Free plan, or any new host. The database stays on Neon, so only the
uploads and the configuration move.

1. On the new server, do [First deploy](#first-deploy) steps 1–3, using the `.env` from the
   password manager (or the old server).
2. **Point DNS at the new server** (lower the `A` record's TTL a day before), then issue the
   certificate there (step 4).
3. On the old server: `docker compose stop backend`, then copy the uploads as in
   [Data and recovery](#data-and-recovery), and move the archive to the new server.
4. On the new server, load them into the volume before starting:
   ```bash
   docker volume create tutorspoint_uploads
   docker run --rm -i -v tutorspoint_uploads:/u alpine sh -c 'tar xzf - -C /u && chown -R 10001:10001 /u' < uploads-<date>.tgz
   ./deploy.sh <tags from the old releases.log>
   ```
5. Check the site and a tutor's documents, then shut the old server down.

## Routine operations

| Task | Command (in `/opt/tutorspoint/deploy`) |
| ---- | -------------------------------------- |
| Status | `docker compose ps` |
| Logs | `docker compose logs -f --tail 100 backend` (JSON, one object per line; `request_id` links a request's lines) |
| Restart the backend | `docker compose restart backend` |
| Certificate expiry | `docker compose run --rm --entrypoint certbot certbot certificates` |
| Force a renewal test | `docker compose run --rm --entrypoint certbot certbot renew --dry-run --webroot -w /var/www/certbot` |
| psql | from your own machine, with the connection string from the Neon console, or the console's SQL Editor. Never change tables by hand: the backend refuses to start on a schema that does not match its migrations. |
| Neon usage | Neon console → Usage: storage against 0.5 GB, compute hours against the month's allowance |
| Disk usage | `docker system df`; `docker image prune -a --filter "until=720h"` keeps a month of images to roll back to |
