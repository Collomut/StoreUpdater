const express = require('express');
const cors = require('cors');
const { Pool } = require('pg');
const jwt = require('jsonwebtoken');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || 'fallback_secret_for_jwt_tokens_12345';

// Database Connection Pool
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: {
    rejectUnauthorized: false
  }
});

// Expose pool to routes
app.set('dbPool', pool);

app.use(cors());
app.use(express.json());

// ─── JWT Authentication Middleware ───────────────────────────────────────────
function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({ error: 'Access token missing' });
  }

  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) {
      return res.status(403).json({ error: 'Access token invalid or expired' });
    }
    req.user = user; // Contains: { userId, username, role, shopId }
    next();
  });
}

// ─── Schema Auto-Initialization (Run on Startup) ─────────────────────────────
async function initializeDatabase() {
  const client = await pool.connect();
  try {
    console.log('Initializing database schema if absent...');
    
    // Create shops table
    await client.query(`
      CREATE TABLE IF NOT EXISTS shops (
        id SERIAL PRIMARY KEY,
        name VARCHAR(100) NOT NULL,
        description TEXT
      )
    `);

    // Create products table
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

    // Create sales table
    await client.query(`
      CREATE TABLE IF NOT EXISTS sales (
        id SERIAL PRIMARY KEY,
        shop_id INTEGER NOT NULL REFERENCES shops(id),
        sale_date DATE NOT NULL,
        total_amount NUMERIC(12,2) NOT NULL,
        receipt_number VARCHAR(50)
      )
    `);

    // Create sale_items table
    await client.query(`
      CREATE TABLE IF NOT EXISTS sale_items (
        id SERIAL PRIMARY KEY,
        sale_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
        product_id INTEGER NOT NULL REFERENCES products(id),
        quantity_sold INTEGER NOT NULL,
        unit_price NUMERIC(12,2) NOT NULL
      )
    `);

    // Create settings table
    await client.query(`
      CREATE TABLE IF NOT EXISTS settings (
        key VARCHAR(100) PRIMARY KEY,
        value TEXT NOT NULL
      )
    `);

    // Create users table
    await client.query(`
      CREATE TABLE IF NOT EXISTS users (
        id SERIAL PRIMARY KEY,
        username VARCHAR(50) UNIQUE NOT NULL,
        password_hash VARCHAR(255) NOT NULL,
        role VARCHAR(20) NOT NULL DEFAULT 'WORKER',
        shop_id INTEGER REFERENCES shops(id),
        must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
        failed_attempts INTEGER NOT NULL DEFAULT 0,
        locked_until TIMESTAMP
      )
    `);

    // Run schema column migrations
    try {
      await client.query('ALTER TABLE products ALTER COLUMN cost_price TYPE NUMERIC(12,2)');
      await client.query('ALTER TABLE products ALTER COLUMN selling_price TYPE NUMERIC(12,2)');
      await client.query('ALTER TABLE sales ALTER COLUMN total_amount TYPE NUMERIC(12,2)');
      await client.query('ALTER TABLE sale_items ALTER COLUMN unit_price TYPE NUMERIC(12,2)');
    } catch (ignored) {}

    try {
      await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE');
      await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_attempts INTEGER NOT NULL DEFAULT 0');
      await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP');
    } catch (ignored) {}

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
    console.error('Error during database initialization:', err);
  } finally {
    client.release();
  }
}

// ─── Routing ─────────────────────────────────────────────────────────────────
const authRouter = require('./routes/auth');
const productsRouter = require('./routes/products');
const salesRouter = require('./routes/sales');
const shopsRouter = require('./routes/shops');
const settingsRouter = require('./routes/settings');
const usersRouter = require('./routes/users');

app.use('/api/auth', authRouter);
app.use('/api/products', authenticateToken, productsRouter);
app.use('/api/sales', authenticateToken, salesRouter);
app.use('/api/shops', authenticateToken, shopsRouter);
app.use('/api/settings', authenticateToken, settingsRouter);
app.use('/api/users', authenticateToken, usersRouter);
app.get('/', (req, res) => {
  res.json({ status: 'Stock Manager API Backend is running.' });
});

// Global Error Handler
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).json({ error: 'Internal Server Error' });
});

// Run Init and Start Server
initializeDatabase().then(() => {
  app.listen(PORT, () => {
    console.log(`Stock Manager API Backend listening on port ${PORT}`);
  });
});
