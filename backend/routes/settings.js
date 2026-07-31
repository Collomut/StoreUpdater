const express = require('express');
const router = express.Router();

// GET /api/settings/:key — get setting value
router.get('/:key', async (req, res) => {
  const { key } = req.params;
  const pool = req.app.get('dbPool');

  try {
    const result = await pool.query('SELECT value FROM settings WHERE key = $1', [key]);
    if (result.rows.length === 0) {
      return res.json({ key, value: null });
    }
    return res.json({ key, value: result.rows[0].value });
  } catch (err) {
    console.error(err);
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
    console.error(err);
    return res.status(500).json({ error: 'Database error saving setting' });
  }
});

module.exports = router;
