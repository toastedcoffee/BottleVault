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
git clone https://github.com/yourusername/bottlevault.git
cd bottlevault

# Create environment file
cp .env.example .env
# Edit .env with your own DB_PASSWORD and JWT_SECRET

# Start all services
docker compose up -d

# Access the app
open http://localhost
```

The app starts three containers:
- **PostgreSQL** on port 5432
- **Spring Boot API** on port 8080
- **Nginx + React** on port 80

### Development Setup

**Backend (requires JDK 22):**

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

## Features

- **Bottle Management** - Add, edit, delete, and browse your bottle collection
- **Barcode Scanning** - Scan bottle barcodes to auto-fill product details (uses Open Food Facts API)
- **Smart Filtering** - Filter by status (unopened/opened/empty), alcohol type, or search by name
- **Product Catalog** - Pre-seeded with 20 brands and 21 products; add your own
- **User Authentication** - JWT-based auth with registration and login
- **Docker Deployment** - Single `docker compose up` to deploy

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
