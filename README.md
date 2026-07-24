# 🌳 School Canopy

A complete multi-tenant school management platform built for Indian schools.

## Architecture

- **Backend**: Java Quarkus (JAX-RS, Panache, Flyway, PostgreSQL with RLS)
- **Platform Admin**: Angular 19 (port 3000)
- **School Portal**: Angular 19 (port 3001)
- **Parent Portal**: Angular 19 (port 3002)

## Local Development

### Prerequisites
- Java 17+
- Node.js 18+
- PostgreSQL 15+ (via Podman/Docker on port 5433)

### Start PostgreSQL
```bash
podman run -d --name school-canopy-db -p 5433:5432 \
  -e POSTGRES_DB=schoolcanopy -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  postgres:15
```

### Start Backend
```bash
cd backend
./gradlew quarkusDev
```
Backend starts on http://localhost:8080

### Start Frontends
```bash
cd platform-admin && npm install && npx ng serve --port 3000
cd school-portal && npm install && npx ng serve --port 3001
cd parent-portal && npm install && npx ng serve --port 3002
```

## Demo Accounts (password: `Admin@123456`)

| Portal | URL | Email | Role |
|--------|-----|-------|------|
| Platform Admin | http://localhost:3000 | admin@schoolcanopy.com | Super Admin |
| School Portal | http://localhost:3001 | schooladmin@demo.edu | School Admin |
| School Portal | http://localhost:3001 | indumathi@demoui.edu | Teacher |
| Parent Portal | http://localhost:3002 | shailendra@demo.edu | Parent |

## Deployment

See [DEPLOYMENT.md](./DEPLOYMENT.md) for staging and production deployment instructions.

## Key Features

- Multi-tenant with PostgreSQL Row-Level Security
- Student management with auto-generated IDs
- Attendance tracking with summaries
- Parent-teacher messaging (threaded)
- Announcements & Events with calendar view
- Support ticket system
- School branding (custom color + logo)
- Bearer token auth (cross-domain ready)
- Role-based access (6 roles)
- Indian school-specific fields (Board, UDISE, etc.)
