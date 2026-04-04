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

### LAN Deployment (e.g. TrueNAS / home server)

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

## API Documentation

When the backend is running, Swagger UI is available at:
- http://localhost:8080/swagger-ui.html (direct)
- http://localhost/swagger-ui/ (through nginx)

## Project Structure

```
bottlevault/
├── backend/          # Kotlin + Spring Boot API
├── frontend/         # React + TypeScript SPA
├── docker-compose.yml
└── .github/workflows/ci.yml
```

## License

MIT
