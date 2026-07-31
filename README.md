# 🛍️ Stock Manager

> A multi-shop retail stock management system built for small businesses in Rwanda.  
> Manages inventory, sales, reports, and staff access across multiple shop locations.

---

## ✨ Features

| Feature | Description |
|---|---|
| **Multi-shop support** | Each shop has its own isolated inventory and sales history |
| **Inventory management** | Add, edit, retire and restock products with full specifications |
| **Nyabugogo catalogue** | Structured catalogue for beads, strings, bracelets, hats and crosses with colour/size variants |
| **Record sales** | Basket-based sales entry with stock deduction and receipt numbering |
| **Bargain pricing** | Downtown shop supports per-sale price overrides for negotiated sales |
| **Reports & history** | View daily/weekly/monthly sales summaries and full transaction history |
| **Low stock alerts** | Products highlighted when stock drops below reorder level |
| **Product retirement** | Discontinue products cleanly without losing historical sales reports |
| **Maximum Security** | Database credentials remain hidden in the cloud; client talks strictly via HTTPS using JWT |
| **Auto-updater** | App checks GitHub for updates on every launch and updates itself silently |
| **Standalone EXE** | No Java installation required on shop PCs — bundled JRE included |

---

## 🏪 Shops Supported

| Shop | Special Behaviour |
|---|---|
| **Nyabugogo** | Full structured catalogue (Beads → Type → Colour/Size cascade) |
| **Downtown** | Bargain mode — editable price per sale transaction |
| *Other shops* | Standard inventory and sales |

---

## 🚀 Installation & Deployment

### 1. Deploy the Backend Proxy (API)
The central database is securely isolated behind a Node.js Express proxy.
1. Log in to your Render account, click **New +** -> **Blueprint**.
2. Connect this repository and choose the `main` branch.
3. Render will read the `render.yaml` blueprint. Provide your Supabase database connection string under `DATABASE_URL` env variable.
4. Click **Apply**. Render will deploy the API server and provide a public URL (e.g. `https://stockmanager-api-4hw3.onrender.com`).

### 2. Configure Client URLs
The Java client dynamically configures itself on startup.
- Edit the `api_url` parameter inside `version.json` in your GitHub repository to point to your live Render backend URL.
- When shop PCs open the app, they will automatically download the manifest, update their local `config.properties`, and connect to the secure API.

### 3. Build & Run Client
```bash
# Build the JAR
mvn package

# Build the standalone Windows installer
build_release.bat
```

---

## 🔄 Auto-Update & Signing System

The app checks this GitHub repository for updates **every time it launches**.

**How it works:**
1. App starts → silently fetches `version.json` from this repo.
2. If a newer version is available → shows an update prompt.
3. Staff click **"Update Now"** → downloads new version in background with progress bar.
4. **Cryptographic Signature Verification**: The downloaded binary's SHA-256 hash is verified against the signature in `version.json` using an embedded RSA public key. If verification fails, the update is aborted.
5. App restarts automatically with the new version.

---

## 📁 Repository Structure

```
StoreUpdater/
├── backend/            ← Node.js Express API server proxy code
├── render.yaml         ← Render Blueprint deployment file
├── version.json        ← Current version and dynamic API URL info
├── README.md           ← This file
├── USER_MANUAL.md      ← Full staff user manual
└── src/                ← JavaFX desktop client source code
```

---

## 🔑 Default Login Credentials

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | Administrator |
| *(shop staff)* | *(set by admin)* | Worker |

---

## 📋 Changelog

| Version | Date | Summary |
|---|---|---|
| `1.3.3` | 2026-07-31 | Layout Hotfix: Updated `colPrice` type to `BigDecimal` in `InventoryController` to resolve runtime ClassCastException layout rendering crashes. |
| `1.3.2` | 2026-07-31 | Local Dev Mode: Excluded localhost / 127.0.0.1 settings from automatic version.json config migration. |
| `1.3.1` | 2026-07-31 | Auto-Migration: Enabled dynamic auto-migration of local `config.properties` using `api_url` from GitHub version.json. Wipes old JDBC passwords. |
| `1.3.0` | 2026-07-31 | REST API Backend: Migrated database access to a secure Express API backend. Removed JDBC drivers & connection pools from desktop client binary. |
| `1.2.0` | 2026-07-30 | Security: Cryptographic update signing, login rate lockout, forced password change dialog, logback file logging, and JUnit 5 test suite. |
| `1.1.0` | 2026-07-29 | Icon & Linux Relaunch: Generated ICO files for taskbar shortcut icons. Resolved Linux "Text file busy" restart lockouts using file unlinking. |
| `1.0.1` | 2026-07-29 | Bug fixes: edit dialog pre-fill, sales colour display, bead sizes. Auto-updater introduced. |
| `1.0.0` | 2026-07-27 | Initial release |

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Java 21 |
| **Client UI** | JavaFX 21 |
| **Client Parser** | Gson (JSON) |
| **Backend Framework**| Node.js + Express |
| **Database Pool** | pg (PostgreSQL connection pool on backend) |
| **Database** | Supabase (PostgreSQL in the cloud) |
| **Security** | JSON Web Tokens (JWT) + BCrypt password hashing |
| **Logging** | SLF4J + Logback Classic (Client) + Winston/Console (Backend) |
| **Testing** | JUnit 5 (Jupiter) |
| **Build Tool** | Apache Maven |
| **Packaging** | jpackage (JDK built-in) |
| **Update Hosting** | GitHub Releases |
