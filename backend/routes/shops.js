const express = require('express');
const router = express.Router();

// GET /api/shops — list all shops
router.get('/', async (req, res) => {
  const pool = req.app.get('dbPool');
  try {
    const result = await pool.query('SELECT id, name, description FROM shops ORDER BY id');
    return res.json(result.rows);
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error fetching shops list' });
  }
});

// GET /api/shops/has-data/:id — check if shop has products or sales
router.get('/has-data/:id', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const shopId = parseInt(req.params.id);
  const pool = req.app.get('dbPool');

  try {
    const pCountRes = await pool.query('SELECT COUNT(*)::int FROM products WHERE shop_id = $1', [shopId]);
    const sCountRes = await pool.query('SELECT COUNT(*)::int FROM sales WHERE shop_id = $1', [shopId]);
    
    const hasData = pCountRes.rows[0].count > 0 || sCountRes.rows[0].count > 0;
    return res.json({ hasData });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error checking shop references' });
  }
});

// POST /api/shops — create a shop (Admins only)
router.post('/', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const { name, description } = req.body;
  const pool = req.app.get('dbPool');

  if (!name) {
    return res.status(400).json({ error: 'Shop name is required' });
  }

  try {
    await pool.query('INSERT INTO shops (name, description) VALUES ($1, $2)', [name, description]);
    return res.json({ success: true });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error adding shop' });
  }
});

// PUT /api/shops/:id — rename or update a shop (Admins only)
router.put('/:id', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const shopId = parseInt(req.params.id);
  const { name, description } = req.body;
  const pool = req.app.get('dbPool');

  try {
    const result = await pool.query(
      'UPDATE shops SET name = $1, description = $2 WHERE id = $3',
      [name, description, shopId]
    );
    return res.json({ success: result.rowCount > 0 });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error updating shop' });
  }
});

// DELETE /api/shops/:id — delete a shop (Admins only)
router.delete('/:id', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const shopId = parseInt(req.params.id);
  const pool = req.app.get('dbPool');

  try {
    const result = await pool.query('DELETE FROM shops WHERE id = $1', [shopId]);
    return res.json({ success: result.rowCount > 0 });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error deleting shop' });
  }
});

// DELETE /api/shops/:id/cascade — cascade delete all shop records (Admins only)
router.delete('/:id/cascade', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const shopId = parseInt(req.params.id);
  const pool = req.app.get('dbPool');

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    
    // 1. Delete sale items
    await client.query(
      'DELETE FROM sale_items WHERE sale_id IN (SELECT id FROM sales WHERE shop_id = $1)',
      [shopId]
    );
    // 2. Delete sales
    await client.query('DELETE FROM sales WHERE shop_id = $1', [shopId]);
    // 3. Delete products
    await client.query('DELETE FROM products WHERE shop_id = $1', [shopId]);
    // 4. Delete shop itself
    const shopDelRes = await client.query('DELETE FROM shops WHERE id = $1', [shopId]);

    await client.query('COMMIT');
    return res.json({ success: shopDelRes.rowCount > 0 });
  } catch (err) {
    await client.query('ROLLBACK');
    console.error('Error during cascading shop deletion:', err);
    return res.status(500).json({ error: 'Cascade deletion failed, database rolled back.' });
  } finally {
    client.release();
  }
});

module.exports = router;
