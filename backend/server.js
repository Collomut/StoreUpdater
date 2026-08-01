const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const { Pool } = require('pg');
const jwt = require('jsonwebtoken');
require('dotenv').config();

// ─── C-1: Handle JWT_SECRET with default fallback if missing in Render env ─────
const JWT_SECRET = process.env.JWT_SECRET || 'stockmanager_jwt_secure_secret_key_2026_prod';


const app = express();
const PORT = process.env.PORT || 3000;

// ─── Database Connection Pool ────────────────────────────────────────────────
// Note: rejectUnauthorized is false due to Supabase PgBouncer pooler (port 6543)
// presenting a hostname mismatch. Connection is still TLS-encrypted end-to-end.
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: {
    rejectUnauthorized: false
  }
});

// Expose pool to routes
app.set('dbPool', pool);
app.set('jwtSecret', JWT_SECRET);

// ─── L-5: Security headers via helmet ────────────────────────────────────────
app.use(helmet());

// ─── C-2: Restrictive CORS — desktop client only, no browser cross-origin ─────
// The app is a desktop JavaFX client. No browser needs cross-origin access.
app.use(cors({ origin: false }));

app.use(express.json({ limit: '1mb' }));

// Trust Render's reverse proxy (needed for correct IP detection in rate limiter)
app.set('trust proxy', 1);

// ─── H-1: Global and per-route rate limiting ─────────────────────────────────
const globalLimiter = rateLimit({
  windowMs: 1 * 60 * 1000,    // 1 minute window
  max: 300,                    // max 300 requests per IP per minute
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { error: 'Too many requests. Please slow down.' }
});

const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,   // 15 minute window
  max: 20,                     // max 20 login attempts per IP per 15 minutes
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { error: 'Too many login attempts from this IP. Try again in 15 minutes.' }
});

app.use('/api/', globalLimiter);
app.use('/api/auth/login', loginLimiter);

// ─── JWT Authentication Middleware ───────────────────────────────────────────
async function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({ error: 'Access token missing' });
  }

  let decoded;
  try {
    decoded = jwt.verify(token, JWT_SECRET);
  } catch (err) {
    return res.status(403).json({ error: 'Access token invalid or expired' });
  }

  // ─── H-5: Validate token_version to support logout/revocation ──────────────
  try {
    const result = await pool.query(
      'SELECT token_version FROM users WHERE id = $1',
      [decoded.userId]
    );
    if (result.rows.length === 0) {
      return res.status(403).json({ error: 'User no longer exists' });
    }
    if (result.rows[0].token_version !== decoded.tokenVersion) {
      return res.status(403).json({ error: 'Session has been revoked. Please log in again.' });
    }
  } catch (err) {
    console.error('Token version check error:', err.message);
    return res.status(500).json({ error: 'Authentication error' });
  }

  req.user = decoded; // Contains: { userId, username, role, shopId, tokenVersion }
  next();
}

// ─── Helper: write to audit log (non-blocking, fire and forget) ───────────────
async function auditLog(pool, userId, action, detail) {
  try {
    await pool.query(
      'INSERT INTO audit_log (user_id, action, detail) VALUES ($1, $2, $3)',
      [userId || null, action, detail || null]
    );
  } catch (err) {
    // Never let audit log failures break main operations
    console.error('Audit log write failed:', err.message);
  }
}

app.set('auditLog', auditLog);

// ─── Schema Auto-Initialization (Run on Startup) ─────────────────────────────
async function initializeDatabase() {
  let client;
  try {
    client = await pool.connect();
    console.log('Initializing database schema if absent...');

    // Core tables
    await client.query(`
      CREATE TABLE IF NOT EXISTS shops (
        id SERIAL PRIMARY KEY,
        name VARCHAR(100) NOT NULL,
        description TEXT
      )
    `);

    await client.query(`
      CREATE TABLE IF NOT EXISTS products (
        id SERIAL PRIMARY KEY,
        shop_id INTEGER NOT NULL REFERENCES shops(id),
        name VARCHAR(200) NOT NULL,
        category VARCHAR(100),
        sku VARCHAR(50),
        unit VARCHAR(50),
        quantity INTEGER NOT NULL DEFAULT 0,
        reorder_level INTEGER NOT NULL DEFAULT 5,
        cost_price NUMERIC(12,2) NOT NULL DEFAULT 0,
        selling_price NUMERIC(12,2) NOT NULL DEFAULT 0
      )
    `);

    await client.query(`
      CREATE TABLE IF NOT EXISTS sales (
        id SERIAL PRIMARY KEY,
        shop_id INTEGER NOT NULL REFERENCES shops(id),
        sale_date DATE NOT NULL,
        total_amount NUMERIC(12,2) NOT NULL,
        receipt_number VARCHAR(50)
      )
    `);

    await client.query(`
      CREATE TABLE IF NOT EXISTS sale_items (
        id SERIAL PRIMARY KEY,
        sale_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
        product_id INTEGER NOT NULL REFERENCES products(id),
        quantity_sold INTEGER NOT NULL,
        unit_price NUMERIC(12,2) NOT NULL
      )
    `);

    await client.query(`
      CREATE TABLE IF NOT EXISTS settings (
        key VARCHAR(100) PRIMARY KEY,
        value TEXT NOT NULL
      )
    `);

    await client.query(`
      CREATE TABLE IF NOT EXISTS users (
        id SERIAL PRIMARY KEY,
        username VARCHAR(50) UNIQUE NOT NULL,
        password_hash VARCHAR(255) NOT NULL,
        role VARCHAR(20) NOT NULL DEFAULT 'WORKER',
        shop_id INTEGER REFERENCES shops(id),
        must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
        failed_attempts INTEGER NOT NULL DEFAULT 0,
        locked_until TIMESTAMP,
        token_version INTEGER NOT NULL DEFAULT 0
      )
    `);

    // ─── F-1: Audit log table ────────────────────────────────────────────────
    await client.query(`
      CREATE TABLE IF NOT EXISTS audit_log (
        id SERIAL PRIMARY KEY,
        user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
        action VARCHAR(100) NOT NULL,
        detail TEXT,
        created_at TIMESTAMP NOT NULL DEFAULT NOW()
      )
    `);

    // Schema column migrations (idempotent)
    try { await client.query('ALTER TABLE products ALTER COLUMN cost_price TYPE NUMERIC(12,2)'); } catch (_) {}
    try { await client.query('ALTER TABLE products ALTER COLUMN selling_price TYPE NUMERIC(12,2)'); } catch (_) {}
    try { await client.query('ALTER TABLE sales ALTER COLUMN total_amount TYPE NUMERIC(12,2)'); } catch (_) {}
    try { await client.query('ALTER TABLE sale_items ALTER COLUMN unit_price TYPE NUMERIC(12,2)'); } catch (_) {}
    try { await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE'); } catch (_) {}
    try { await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_attempts INTEGER NOT NULL DEFAULT 0'); } catch (_) {}
    try { await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP'); } catch (_) {}
    // H-5: token_version column for JWT revocation
    try { await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0'); } catch (_) {}

    // Seed default data if empty
    const shopsRes = await client.query('SELECT COUNT(*) FROM shops');
    if (parseInt(shopsRes.rows[0].count) === 0) {
      await client.query("INSERT INTO shops (name, description) VALUES ('Shoes Shop', 'Footwear & accessories')");
      await client.query("INSERT INTO shops (name, description) VALUES ('Curios Shop', 'Souvenirs & curio items')");
      await client.query("INSERT INTO shops (name, description) VALUES ('Beads & Hats Shop', 'Beads, hats & fashion accessories')");
    }

    const settingsRes = await client.query('SELECT COUNT(*) FROM settings');
    if (parseInt(settingsRes.rows[0].count) === 0) {
      await client.query("INSERT INTO settings (key, value) VALUES ('usd_rate', '1380')");
      await client.query("INSERT INTO settings (key, value) VALUES ('receipt_counter', '1')");
    }

    const usersRes = await client.query('SELECT COUNT(*) FROM users');
    if (parseInt(usersRes.rows[0].count) === 0) {
      const bcrypt = require('bcryptjs');
      const adminHash = await bcrypt.hash('admin123', 10);
      const shop1Hash = await bcrypt.hash('shop1pass', 10);
      const shop2Hash = await bcrypt.hash('shop2pass', 10);
      const shop3Hash = await bcrypt.hash('shop3pass', 10);

      const shops = await client.query('SELECT id FROM shops ORDER BY id');
      const ids = shops.rows.map(r => r.id);

      await client.query('INSERT INTO users (username, password_hash, role, shop_id, must_change_password) VALUES ($1, $2, $3, $4, $5)',
        ['admin', adminHash, 'ADMIN', null, true]);
      if (ids[0]) await client.query('INSERT INTO users (username, password_hash, role, shop_id, must_change_password) VALUES ($1, $2, $3, $4, $5)',
        ['shop1', shop1Hash, 'WORKER', ids[0], true]);
      if (ids[1]) await client.query('INSERT INTO users (username, password_hash, role, shop_id, must_change_password) VALUES ($1, $2, $3, $4, $5)',
        ['shop2', shop2Hash, 'WORKER', ids[1], true]);
      if (ids[2]) await client.query('INSERT INTO users (username, password_hash, role, shop_id, must_change_password) VALUES ($1, $2, $3, $4, $5)',
        ['shop3', shop3Hash, 'WORKER', ids[2], true]);
    }

    console.log('Database initialization complete.');
  } catch (err) {
    console.error('Error during database initialization:', err.message);
  } finally {
    if (client) client.release();
  }
}

// ─── Routing ─────────────────────────────────────────────────────────────────
const authRouter     = require('./routes/auth');
const productsRouter = require('./routes/products');
const salesRouter    = require('./routes/sales');
const shopsRouter    = require('./routes/shops');
const settingsRouter = require('./routes/settings');
const usersRouter    = require('./routes/users');

app.use('/api/auth',     authRouter);
app.use('/api/products', authenticateToken, productsRouter);
app.use('/api/sales',    authenticateToken, salesRouter);
app.use('/api/shops',    authenticateToken, shopsRouter);
app.use('/api/settings', authenticateToken, settingsRouter);
app.use('/api/users',    authenticateToken, usersRouter);

app.get('/', (req, res) => {
  res.json({ status: 'Stock Manager API Backend is running.' });
});

// Global Error Handler
app.use((err, req, res, next) => {
  console.error('Unhandled error:', err.message);
  res.status(500).json({ error: 'Internal Server Error' });
});

// ─── Start Server then run DB init in background ─────────────────────────────
// Start HTTP listener first so Render's health check on the port passes
// immediately, even if the database takes a moment to warm up.
app.listen(PORT, () => {
  console.log(`Stock Manager API Backend listening on port ${PORT}`);
});

// Run schema migration and seeding in background — non-fatal if it fails
initializeDatabase().catch(err => {
  console.error('Non-fatal: Database initialization error on startup:', err.message);
});
