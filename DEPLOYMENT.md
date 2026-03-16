# Deployment Guide: MaScan QR Attendance System

This guide walks you through deploying the Flask QR Attendance System to production using Render or Fly.io.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Testing with Docker](#local-testing-with-docker)
3. [Deployment to Render](#deployment-to-render)
4. [Deployment to Fly.io](#deployment-to-flyio)
5. [Database Migration](#database-migration)
6. [Post-Deployment Setup](#post-deployment-setup)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

You'll need:
- **GitHub account** (to store your code)
- **Render account** ([render.com](https://render.com)) OR **Fly.io account** ([fly.io](https://fly.io))
- **Docker** installed locally (optional, but recommended for testing)
- **Git** installed

---

## Local Testing with Docker

Before deploying to production, test the application locally with PostgreSQL:

### 1. Build and run with Docker Compose

```bash
# Navigate to project root
cd QR-Attendance-Checker---Flask-Version

# Build and start services (PostgreSQL + Flask app)
docker-compose up --build
```

This will:
- Start PostgreSQL database on `localhost:5432`
- Start Flask app on `localhost:5000`
- Automatically create database tables

### 2. Test the application

```bash
# Open in browser
http://localhost:5000

# Login with default credentials
# Username: admin
# Password: admin123
```

### 3. Run smoke tests
- Create a test event
- Generate a QR code
- Test PDF export
- Mark attendance

### 4. Stop services

```bash
docker-compose down
```

---

## Deployment to Render

### Step 1: Push code to GitHub

```bash
git add .
git commit -m "Prepare for production deployment"
git push origin main
```

### Step 2: Create Render account and connect GitHub

1. Go to [render.com](https://render.com)
2. Sign up with GitHub
3. Grant repository access to Render

### Step 3: Deploy using render.yaml

1. In Render dashboard, click **"New +"** → **"Blueprint"**
2. Connect your GitHub repository
3. Render will auto-detect `render.yaml` and deploy
4. The deployment will:
   - Build Docker image
   - Create PostgreSQL database
   - Deploy Flask application
   - Set up environment variables

### Step 4: Configure environment variables

In Render dashboard, go to your web service → **"Environment":

- `SECRET_KEY`: Generate with:
  ```bash
  python -c "import secrets; print(secrets.token_hex(32))"
  ```
- `FLASK_ENV`: `production`
- `DEBUG`: `False`
- `DATABASE_URL`: Auto-populated by Render PostgreSQL

### Step 5: Deploy

```bash
# Render auto-deploys on git push
git push origin main
```

Monitor deployment in Render dashboard. Once complete:
```
https://mascan-attendance-<random>.onrender.com
```

### Step 6: Configure custom domain (optional)

In Render dashboard:
1. Go to your web service → **"Settings"**
2. Add Custom Domain
3. Update DNS records in your domain registrar

---

## Deployment to Fly.io

### Step 1: Install Fly CLI

```bash
# macOS
brew install flyctl

# Windows (PowerShell)
iwr https://fly.io/install.ps1 -useb | iex

# Linux
curl -L https://fly.io/install.sh | sh
```

### Step 2: Authenticate with Fly.io

```bash
fly auth login
```

### Step 3: Configure and deploy

```bash
cd QR-Attendance-Checker---Flask-Version

# Create/update app configuration
fly launch

# Follow prompts:
# - App name: mascan-attendance
# - Region: Choose closest to users
# - Use existing fly.toml: Yes
```

### Step 4: Set secrets

```bash
# Generate SECRET_KEY
python -c "import secrets; print(secrets.token_hex(32))" > secret.txt

# Set secrets
fly secrets set SECRET_KEY=$(cat secret.txt)
fly secrets set FLASK_ENV=production
fly secrets set DEBUG=False
```

### Step 5: Create PostgreSQL database

```bash
# Option 1: Use Fly Postgres (free tier available)
fly postgres create
  # Follow prompts
  # App name: mascan-attendance-db

# Option 2: Use external PostgreSQL (e.g., from Render, AWS RDS)
fly secrets set DATABASE_URL=postgresql://user:password@host:port/database
```

### Step 6: Deploy application

```bash
fly deploy
```

Monitor deployment:
```bash
fly logs -f
```

Once complete, your app is live at:
```
https://mascan-attendance.fly.dev
```

### Step 7: Configure custom domain (optional)

```bash
fly certs create yourdomain.com
```

---

## Database Migration

### Only if migrating from existing SQLite to PostgreSQL:

**⚠️ Backup first!** The migration script creates automatic backups.

### Step 1: Prepare environment

```bash
export DATABASE_URL=postgresql://user:password@host:port/mascan_attendance
```

### Step 2: Run migration script

```bash
cd QR-Attendance-Checker---Flask-Version/app

python database/migrate_to_postgres.py
```

The script will:
1. Read all data from SQLite (`mascan_attendance.db`)
2. Connect to PostgreSQL via `DATABASE_URL`
3. Create backup of SQLite file
4. Migrate all tables and data
5. Verify integrity

### Step 3: Verify migration

```bash
# Check row counts
psql $DATABASE_URL -c "SELECT tablename FROM pg_tables WHERE schemaname='public';"

psql $DATABASE_URL -c "SELECT COUNT(*) FROM events;"
psql $DATABASE_URL -c "SELECT COUNT(*) FROM attendance;"
psql $DATABASE_URL -c "SELECT COUNT(*) FROM users;"
```

### Step 4: Clean up (optional)

Once verified, remove old SQLite file:
```bash
rm mascan_attendance.db
```

---

## Post-Deployment Setup

### 1. Change default admin password

After deployment:
1. Open your deployed app URL
2. Login with `admin` / `admin123`
3. Navigate to User Settings
4. Change password immediately

### 2. Configure Android app

Update the Flask app endpoint in Android app:
- File: `android-app/app/src/main/java/com/mascan/attendance/MainActivity.java`
- Change: `API_ENDPOINT = "http://your-deployed-url.com"`

### 3. Set up HTTPS

Both Render and Fly.io provide free HTTPS. Ensure your app uses `https://` in production.

### 4. Configure backups

#### Render
- Backups are automatic (daily by default)
- Dashboard → PostgreSQL → Backups

#### Fly.io
- For Fly Postgres: `fly postgres backup list`
- For external DB: Configure backups in your database provider

### 5. Monitor application

#### Render
- Dashboard → Logs → Web service logs

#### Fly.io
```bash
fly logs -f
fly metrics
```

---

## Troubleshooting

### Issue: Database connection error

**Solution:**
```bash
# Verify DATABASE_URL is set correctly
fly secrets list  # Fly.io
# or check Render dashboard

# Test connection
psql $DATABASE_URL -c "SELECT 1;"
```

### Issue: Application starts but shows database error

**Cause:** Flask-Session trying to use filesystem storage in ephemeral container

**Solution:**
- Verify `FLASK_ENV=production` is set
- Verify `DATABASE_URL` is correctly configured
- The code automatically switches to sqlalchemy session storage when DATABASE_URL is present

### Issue: Deployment fails during build

**Solution:**
```bash
# Check build logs in dashboard
# Ensure requirements.txt is complete
# Test locally: docker-compose up --build

# Push fix to GitHub
git push origin main
```

### Issue: Large file uploads fail

**Solution:**
- The app is configured for max 16MB uploads
- For larger files, increase `MAX_CONTENT_LENGTH` in `src/flask_app.py`
- Redeploy after change

### Issue: Emails or external services not working

**Cause:** Environment variables not set

**Solution:**
```bash
# Render
# Dashboard → Web Service → Environment

# Fly.io
fly secrets set KEY=value

# Redeploy
fly deploy
```

### Issue: Static files not loading (CSS/JS broken)

**Solution:**
```bash
# Restart the application
fly restart  # Fly.io
# or redeploy via Render dashboard

# Or rebuild Docker image
fly deploy --rebuild  # Fly.io
```

---

## Environment Variables Reference

| Variable | Value | Required | Notes |
|----------|-------|----------|-------|
| `FLASK_ENV` | `production` | Yes | Enable production mode |
| `DEBUG` | `False` | Yes | Disable debug mode in production |
| `SECRET_KEY` | Random hex string | Yes | Session encryption key |
| `DATABASE_URL` | PostgreSQL connection string | Yes for production | Format: `postgresql://user:pass@host:port/db` |
| `HOST` | `0.0.0.0` | No | Server bind address (default: 0.0.0.0) |
| `PORT` | `5000` | No | Server port (default: 5000) |

---

## Additional Resources

- [Render Documentation](https://render.com/docs)
- [Fly.io Documentation](https://fly.io/docs)
- [Flask Deployment Guide](https://flask.palletsprojects.com/en/2.3.x/deploying/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Docker Documentation](https://docs.docker.com/)

---

## Support & Questions

For issues or questions:
1. Check the Troubleshooting section above
2. Review application logs in platform dashboard
3. Verify all environment variables are set correctly
4. Test locally with Docker Compose first
