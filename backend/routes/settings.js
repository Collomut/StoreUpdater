const express = require('express');
const router = express.Router();

// M-3: Settings keys that non-admin workers are allowed to read
const WORKER_READABLE_KEYS = ['usd_rate'];

// GET /api/settings — list settings
router.get('/', async (req, res) => {
  const pool = req.app.get('dbPool');
  try {
    let query = 'SELECT key, value FROM settings';
    let params = [];
    if (req.user.role !== 'ADMIN') {
      query = 'SELECT key, value FROM settings WHERE key = ANY($1)';
      params = [WORKER_READABLE_KEYS];
    }
    const result = await pool.query(query, params);
    return res.json(result.rows);
  } catch (err) {
    console.error('Fetch settings error:', err.message);
    return res.status(500).json({ error: 'Database error fetching settings' });
  }
});

// GET /api/settings/:key — get setting value
router.get('/:key', async (req, res) => {
  const { key } = req.params;
  const pool    = req.app.get('dbPool');

  // M-3: Workers may only read whitelisted setting keys
  if (req.user.role !== 'ADMIN' && !WORKER_READABLE_KEYS.includes(key)) {
    return res.status(403).json({ error: 'Access denied: You do not have permission to read this setting' });
  }

  try {
    const result = await pool.query('SELECT value FROM settings WHERE key = $1', [key]);
    if (result.rows.length === 0) {
      return res.json({ key, value: null });
    }
    return res.json({ key, value: result.rows[0].value });
  } catch (err) {
    console.error('Fetch setting error:', err.message);
    return res.status(500).json({ error: 'Database error fetching setting key' });
  }
});

// POST /api/settings — save a setting (Admins only)
router.post('/', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const { key, value } = req.body;
  const pool = req.app.get('dbPool');

  if (!key || value === undefined) {
    return res.status(400).json({ error: 'Key and value parameters are required' });
  }

  try {
    await pool.query(
      'INSERT INTO settings (key, value) VALUES ($1, $2) ON CONFLICT (key) DO UPDATE SET value = $2',
      [key, value]
    );
    return res.json({ success: true });
  } catch (err) {
    console.error('Save setting error:', err.message);
    return res.status(500).json({ error: 'Database error saving setting' });
  }
});

module.exports = router;
