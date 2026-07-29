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
| **Product retirement** | Retire discontinued products without deleting history |
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

## 🖥️ System Requirements

| Requirement | Minimum |
|---|---|
| Operating System | Windows 10 / Windows 11 |
| RAM | 4 GB |
| Storage | 500 MB free |
| Internet | Required for auto-updates (optional otherwise) |
| Java | **Not required** — bundled inside the app |

---

## 🚀 Installation & Deployment

### For a new PC (first time setup)

1. Download the latest release from the [Releases page](https://github.com/Collomut/StoreUpdater/releases)
2. Download `StockManager-1.0.0.jar` from the release assets
3. Copy the entire `StockManager/` folder (containing the `.exe`) to the target PC
4. Double-click `StockManager.exe`
5. Login with your credentials

> **No Java installation required.** The JRE is bundled inside the app folder.

### For developers (building from source)

```bash
# Build the JAR
mvn package

# Build the standalone EXE (Windows)
build_release.bat
```

---

## 🔄 Auto-Update System

The app checks this GitHub repository for updates **every time it launches**.

**How it works:**
1. App starts → silently fetches `version.json` from this repo
2. If a newer version is available → shows an update prompt
3. Staff click **"Update Now"** → downloads new version in background with progress bar
4. App restarts automatically with the new version

**Update flow for the developer (releasing a new version):**
1. Make code changes
2. Bump `app.version` in `version.properties` (e.g. `1.0.1` → `1.0.2`)
3. Run `build_release.bat`
4. Create a new GitHub Release tagged `v1.0.2` and upload the new JAR
5. Update `version.json` in this repo with the new version and download URL
6. All shop PCs update automatically on next launch — no manual visits needed

---

## 📁 Repository Structure

```
StoreUpdater/
├── version.json        ← Current version info (checked by the app on startup)
├── README.md           ← This file
├── USER_MANUAL.md      ← Full staff user manual
└── releases/           ← GitHub Releases (JAR assets attached per release)
```

---

## 🔑 Default Login Credentials

> ⚠️ Change these after first login via Settings.

| Username | Password | Role |
|---|---|---|
| `admin` | *(set during setup)* | Administrator |
| *(shop staff)* | *(set by admin)* | Worker |

---

## 🗄️ Database

- Hosted on **Supabase** (PostgreSQL in the cloud)
- All shops share the same central database over the internet
- Data is **never affected** by app updates

---

## 📄 Documentation

- 📘 [User Manual](USER_MANUAL.md) — Step-by-step guide for shop staff

---

## 📋 Changelog

| Version | Date | Summary |
|---|---|---|
| `1.0.1` | 2026-07-29 | Bug fixes: edit dialog pre-fill, sales colour display, bead sizes. Auto-updater introduced. |
| `1.0.0` | 2026-07-27 | Initial release |

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| UI Framework | JavaFX 21 |
| Database | PostgreSQL (Supabase) |
| Connection Pool | HikariCP |
| Build Tool | Apache Maven |
| Packaging | jpackage (JDK built-in) |
| Update Hosting | GitHub Releases |
