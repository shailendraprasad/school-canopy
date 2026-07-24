# School Canopy — Deployment Guide

## Architecture

```
Users → Firebase Hosting (Global CDN) → Angular Static Files
                ↓ (API calls via Bearer token)
         GCP Cloud Run (Quarkus Java API)
                ↓
         Supabase PostgreSQL
```

**Total monthly cost: $0** (all within free tiers for staging)

---

## Prerequisites

- Google Cloud account with a project
- Node.js 18+ and npm
- Java 17+
- [gcloud CLI](https://cloud.google.com/sdk/docs/install)
- [Firebase CLI](https://firebase.google.com/docs/cli): `npm install -g firebase-tools`

---

# PART 1: DATABASE (Supabase)

## Via UI:

1. Go to [supabase.com](https://supabase.com) → Sign up / Login
2. Click **New Project**
3. Fill in:
   - Organization: your org
   - Project name: `school-canopy-staging`
   - Database password: set a strong password → **copy it somewhere safe**
   - Region: **South Asia (Mumbai)** or **Southeast Asia (Singapore)**
4. Click **Create new project** → wait 2 minutes
5. Go to **Settings** (gear icon) → **Database**
6. Under **Connection string** → **URI** tab, copy the string:
   ```
   postgresql://postgres:[YOUR-PASSWORD]@db.xxxxxx.supabase.co:5432/postgres
   ```

**Note down these values:**
- Host: `db.xxxxxx.supabase.co`
- Port: `5432`
- Database: `postgres`
- Username: `postgres`
- Password: your password

---

# PART 2: API (GCP Cloud Run)

## Option A: Via GCP Console UI

### 2.1 Enable APIs

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Select your project (or create one)
3. Go to **APIs & Services → Enable APIs**
4. Enable:
   - **Cloud Run Admin API**
   - **Cloud Build API**
   - **Artifact Registry API**

### 2.2 Deploy via Cloud Run UI

1. Go to **Cloud Run** in the GCP Console
2. Click **Create Service**
3. Select **Continuously deploy from a source repository** → click **Set up Cloud Build**
   - OR select **Deploy one revision from an existing container image** (if you've built the image already)
4. For source deploy:
   - Connect your GitHub/GitLab repo or upload source
   - Build type: **Dockerfile**
   - Source location: `/backend`
5. Configure:
   - Service name: `school-canopy-api-staging`
   - Region: `asia-south1 (Mumbai)`
   - Authentication: **Allow unauthenticated invocations**
   - Container port: `8080`
   - Memory: `512 MiB`
   - CPU: `1`
   - Minimum instances: `0`
   - Maximum instances: `2`
6. Under **Variables & Secrets** → **Environment variables**, add:

   | Name | Value |
   |------|-------|
   | `QUARKUS_DATASOURCE_JDBC_URL` | `jdbc:postgresql://db.xxxxxx.supabase.co:5432/postgres?sslmode=require` |
   | `QUARKUS_DATASOURCE_USERNAME` | `postgres` |
   | `QUARKUS_DATASOURCE_PASSWORD` | your Supabase password |
   | `QUARKUS_HTTP_CORS_ORIGINS` | `*` (update after Firebase deploy) |
   | `QUARKUS_FLYWAY_MIGRATE_AT_START` | `true` |
   | `QUARKUS_PROFILE` | `prod` |

7. Click **Create**
8. Wait for build & deploy (5-10 minutes)
9. Copy the service URL: `https://school-canopy-api-staging-xxxxx-el.a.run.app`

---

## Option B: Via CLI

### 2.1 Setup

```bash
gcloud auth login
gcloud config set project YOUR_PROJECT_ID
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com
```

### 2.2 Deploy

```bash
cd backend

gcloud run deploy school-canopy-api-staging \
  --source . \
  --region asia-south1 \
  --allow-unauthenticated \
  --memory 512Mi \
  --cpu 1 \
  --min-instances 0 \
  --max-instances 2 \
  --set-env-vars "QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db.xxxxxx.supabase.co:5432/postgres?sslmode=require" \
  --set-env-vars "QUARKUS_DATASOURCE_USERNAME=postgres" \
  --set-env-vars "QUARKUS_DATASOURCE_PASSWORD=YOUR_PASSWORD" \
  --set-env-vars "QUARKUS_HTTP_CORS_ORIGINS=*" \
  --set-env-vars "QUARKUS_FLYWAY_MIGRATE_AT_START=true" \
  --set-env-vars "QUARKUS_PROFILE=prod"
```

### 2.3 Verify

```bash
curl https://school-canopy-api-staging-xxxxx-el.a.run.app/q/health
# Should return: {"status":"UP"}
```

### Dockerfile (backend/Dockerfile)

Ensure this exists:

```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew build -Dquarkus.package.type=uber-jar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/*-runner.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

---

# PART 3: UPDATE FRONTEND API URL

Edit the staging environment in each portal with your Cloud Run URL:

**Files to edit:**
- `platform-admin/src/environments/environment.staging.ts`
- `school-portal/src/environments/environment.staging.ts`
- `parent-portal/src/environments/environment.staging.ts`

Change `apiUrl` to:
```typescript
export const environment = {
  production: false,
  staging: true,
  apiUrl: 'https://school-canopy-api-staging-xxxxx-el.a.run.app'
};
```

---

# PART 4: BUILD FRONTENDS

```bash
cd platform-admin
npx ng build --configuration staging

cd ../school-portal
npx ng build --configuration staging

cd ../parent-portal
npx ng build --configuration staging
```

Output goes to each portal's `dist/` folder.

---

# PART 5: UI (Firebase Hosting)

## Option A: Via Firebase Console UI

### 5.1 Setup

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Click **Add project** → select your existing GCP project → Continue
3. Disable Google Analytics (not needed) → Create project

### 5.2 Create Hosting Sites

1. In Firebase Console → **Hosting** (left menu)
2. Click **Get started** → follow setup wizard
3. Go to **Hosting** → **Add another site** (bottom of page)
4. Create 3 sites:
   - `school-canopy-platform`
   - `school-canopy-school`
   - `school-canopy-parent`

### 5.3 Deploy via CLI

Even with UI setup, deploy is done via CLI:

```bash
firebase login
firebase use YOUR_PROJECT_ID
firebase deploy --only hosting
```

---

## Option B: Via CLI (Full)

### 5.1 Login & Setup

```bash
# Install Firebase CLI (if not installed)
npm install -g firebase-tools

# Login
firebase login

# Set project
firebase use YOUR_GCP_PROJECT_ID
```

### 5.2 Create Hosting Sites

```bash
firebase hosting:sites:create school-canopy-platform
firebase hosting:sites:create school-canopy-school
firebase hosting:sites:create school-canopy-parent
```

### 5.3 Apply Targets

```bash
firebase target:apply hosting platform school-canopy-platform
firebase target:apply hosting school school-canopy-school
firebase target:apply hosting parent school-canopy-parent
```

### 5.4 Deploy

```bash
firebase deploy --only hosting
```

### 5.5 Result URLs

After deploy, you get:
- `https://school-canopy-platform.web.app` — Platform Admin
- `https://school-canopy-school.web.app` — School Portal
- `https://school-canopy-parent.web.app` — Parent Portal

---

# PART 6: LOCK DOWN CORS

Once you have the Firebase URLs, update Cloud Run to only allow those origins.

## Via GCP Console UI:

1. Go to **Cloud Run** → click your service
2. Click **Edit & Deploy New Revision**
3. Under **Variables & Secrets**, update `QUARKUS_HTTP_CORS_ORIGINS`:
   ```
   https://school-canopy-platform.web.app,https://school-canopy-school.web.app,https://school-canopy-parent.web.app
   ```
4. Click **Deploy**

## Via CLI:

```bash
gcloud run services update school-canopy-api-staging \
  --region asia-south1 \
  --update-env-vars "QUARKUS_HTTP_CORS_ORIGINS=https://school-canopy-platform.web.app,https://school-canopy-school.web.app,https://school-canopy-parent.web.app"
```

---

# PART 7: VERIFY

1. Open `https://school-canopy-platform.web.app`
2. Login: `schoolcanopyadmin@gmail.com` / `Admin@123456`
3. Onboard a school from the Platform Admin
4. Open School Portal → login as school admin
5. Open Parent Portal → login as parent

---

# Configuration Files

## firebase.json (project root)

```json
{
  "hosting": [
    {
      "site": "school-canopy-platform",
      "public": "platform-admin/dist/platform-admin/browser",
      "ignore": ["firebase.json", "**/.*", "**/node_modules/**"],
      "rewrites": [{ "source": "**", "destination": "/index.html" }]
    },
    {
      "site": "school-canopy-school",
      "public": "school-portal/dist/school-portal/browser",
      "ignore": ["firebase.json", "**/.*", "**/node_modules/**"],
      "rewrites": [{ "source": "**", "destination": "/index.html" }]
    },
    {
      "site": "school-canopy-parent",
      "public": "parent-portal/dist/parent-portal/browser",
      "ignore": ["firebase.json", "**/.*", "**/node_modules/**"],
      "rewrites": [{ "source": "**", "destination": "/index.html" }]
    }
  ]
}
```

## .firebaserc (project root)

```json
{
  "projects": {
    "default": "YOUR_GCP_PROJECT_ID"
  },
  "targets": {
    "YOUR_GCP_PROJECT_ID": {
      "hosting": {
        "platform": ["school-canopy-platform"],
        "school": ["school-canopy-school"],
        "parent": ["school-canopy-parent"]
      }
    }
  }
}
```

## backend/Dockerfile

```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew build -Dquarkus.package.type=uber-jar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/*-runner.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

---

# Redeployment

## API changes:
```bash
cd backend
gcloud run deploy school-canopy-api-staging --source . --region asia-south1
```

## UI changes:
```bash
# Build all
cd platform-admin && npx ng build --configuration staging
cd ../school-portal && npx ng build --configuration staging
cd ../parent-portal && npx ng build --configuration staging

# Deploy all
cd ..
firebase deploy --only hosting
```

## Deploy single portal:
```bash
firebase deploy --only hosting:platform
firebase deploy --only hosting:school
firebase deploy --only hosting:parent
```

---

# Environment Summary

| Environment | API URL | Build Command | Deploy |
|-------------|---------|---------------|--------|
| Local | `http://localhost:8080` | `npx ng serve` | — |
| Staging | Cloud Run staging URL | `npx ng build --configuration staging` | `firebase deploy` |
| Production | Cloud Run prod URL | `npx ng build --configuration production` | `firebase deploy` |

---

# Cost

| Service | Free Tier | Monthly Cost |
|---------|-----------|-------------|
| Supabase | 500MB DB, 1GB transfer | $0 |
| Cloud Run | 2M requests, 360K GB-sec | $0 |
| Firebase Hosting | 10GB storage, 360MB/day | $0 |
| **Total** | | **$0** |

---

# Troubleshooting

| Issue | Fix |
|-------|-----|
| CORS error in browser | Update `QUARKUS_HTTP_CORS_ORIGINS` on Cloud Run |
| 401 Unauthorized | Clear localStorage in browser, login again |
| Blank page after deploy | `firebase.json` rewrites handle SPA routing |
| Cold start 5-10s | Normal for `min-instances=0`, first request wakes container |
| DB connection timeout | Add `?sslmode=require` to JDBC URL |
| Build fails on Cloud Run | Ensure `backend/Dockerfile` exists and `gradlew` has exec permission |
| Firebase deploy fails | Run `firebase use YOUR_PROJECT_ID` and ensure targets are set |
| "Site not found" | Create sites first: `firebase hosting:sites:create site-name` |
