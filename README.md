# BottleVault

A personal spirits and bottle inventory management web application. Track your collection, scan barcodes to add bottles, and explore your drinking habits through statistics.

## Tech Stack

- **Backend:** Kotlin + Spring Boot 3, PostgreSQL, Flyway migrations
- **Frontend:** React 19 + TypeScript, Tailwind CSS, TanStack Query
- **Infrastructure:** Docker Compose, Nginx reverse proxy, GitHub Actions CI

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Git

### Run with Docker Compose

```bash
# Clone the repository
git clone https://github.com/toastedcoffee/BottleVault.git
cd BottleVault

# Create environment file and set your secrets
cp .env.example .env
# Edit .env — DB_PASSWORD and JWT_SECRET are required

# Start all services
docker compose up -d

# Access the app
open http://localhost
```

The app starts three containers:
- **PostgreSQL** — internal only, not exposed to the host
- **Spring Boot API** — internal only, health-checked via Actuator
- **Nginx + React** — exposed on port 80 (configurable via `PORT` in `.env`)

### Development Setup

**Backend (requires JDK 21):**

```bash
# Start dev database
docker compose -f docker-compose.dev.yml up -d

# Run backend
cd backend
./gradlew bootRun
```

**Frontend (requires Node.js 22):**

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` requests to `localhost:8080`.

### LAN Deployment with Dockge (TrueNAS SCALE / home server)

Pre-built images are published to GitHub Container Registry on every merge to main. No git clone needed on your server.

1. **Create the stack** in Dockge — name it `bottlevault`
2. **Paste** the contents of [`docker-compose.prod.yml`](docker-compose.prod.yml) into the compose editor
3. **Set environment variables** in Dockge's UI:
   - `DB_PASSWORD` — any strong password
   - `JWT_SECRET` — generate with `openssl rand -base64 48`
   - `REGISTRATION_ENABLED` — `true` (change to `false` after creating your account)
   - `PORT` — (optional, default `80`)
4. **Create the data directory** from TrueNAS shell:
   ```bash
   mkdir -p /mnt/AppPool/configs/stacks/bottlevault/data/postgres
   ```
5. **Deploy** the stack from Dockge
6. **Register** your account at `http://<server-ip>`
7. **Lock down** — set `REGISTRATION_ENABLED=false` in Dockge and redeploy

### LAN Deployment with CLI

```bash
# On your server, clone and configure
git clone https://github.com/toastedcoffee/BottleVault.git
cd BottleVault
cp .env.example .env

# Generate real secrets
echo "DB_PASSWORD=$(openssl rand -base64 24)" >> .env
echo "JWT_SECRET=$(openssl rand -base64 48)" >> .env

# Start the stack
docker compose up -d

# After creating your account, lock down registration
# Edit .env and set REGISTRATION_ENABLED=false, then:
docker compose up -d
```

Access the app from any device on your network at `http://<server-ip>`.

## Features

- **Bottle Management** — Add, edit, delete, and browse your bottle collection
- **Barcode Scanning** — Scan bottle barcodes to auto-fill product details (uses Open Food Facts API)
- **Smart Filtering** — Filter by status (unopened/opened/empty), alcohol type, or search by name
- **Statistics Dashboard** — Collection value, average cost, rating trends, spending over time, and type distribution charts
- **Product Catalog** — Pre-seeded with 20 brands and 21 products; add your own
- **User Authentication** — JWT-based auth with registration and login
- **Responsive Design** — Mobile-friendly with touch targets and hamburger nav
- **Docker Deployment** — Single `docker compose up` to deploy on any Docker host

## Emergency Password Reset

If a user is locked out, reset their password directly against the database from the Docker host:

```bash
./scripts/reset-password.sh user@example.com
```

The script verifies the user exists, prompts for a new password (twice), generates a BCrypt hash via a throwaway `httpd:alpine` container, and updates the `users` table. Existing JWTs for that user remain valid until they expire — this only changes the credentials used at login.

For everyday password changes, use the Settings page in the app instead.

## API Documentation

Swagger UI is disabled by default for security. To enable it for development, set `SWAGGER_ENABLED=true` in your environment or `.env` file, then access:
- http://localhost:8080/swagger-ui.html (direct, dev profile)
- http://localhost/swagger-ui/ (through nginx, requires nginx config change)

## Project Structure

```
bottlevault/
├── backend/               # Kotlin + Spring Boot API
├── frontend/              # React + TypeScript SPA
├── docker-compose.yml     # Development (builds from source)
├── docker-compose.prod.yml # Production (pre-built images from GHCR)
└── .github/workflows/ci.yml
```

## License

MIT
