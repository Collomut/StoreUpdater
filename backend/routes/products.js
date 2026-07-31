const express = require('express');
const router = express.Router();

// GET /api/products — list products for a specific shop
router.get('/', async (req, res) => {
  let shopId = req.query.shopId ? parseInt(req.query.shopId) : null;
  const pool = req.app.get('dbPool');

  // RLS Isolation: Workers are locked to their assigned shop
  if (req.user.role === 'WORKER') {
    shopId = req.user.shopId;
  }

  if (!shopId) {
    return res.status(400).json({ error: 'shopId query parameter is required' });
  }

  try {
    const result = await pool.query(
      'SELECT id, shop_id, name, category, sku, unit, quantity, reorder_level, cost_price::float8, selling_price::float8 FROM products WHERE shop_id = $1 ORDER BY name',
      [shopId]
    );
    return res.json(result.rows);
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error fetching products' });
  }
});

// GET /api/products/has-sales/:id — check if a product has transactions
router.get('/has-sales/:id', async (req, res) => {
  const productId = parseInt(req.params.id);
  const pool = req.app.get('dbPool');

  try {
    const result = await pool.query(
      'SELECT COUNT(*)::int FROM sale_items WHERE product_id = $1',
      [productId]
    );
    return res.json({ hasSales: result.rows[0].count > 0 });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error checking product sales' });
  }
});

// POST /api/products — add a new product (Admins only)
router.post('/', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const { shopId, name, category, sku, unit, quantity, reorderLevel, costPrice, sellingPrice } = req.body;
  const pool = req.app.get('dbPool');

  try {
    await pool.query(
      'INSERT INTO products (shop_id, name, category, sku, unit, quantity, reorder_level, cost_price, selling_price) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)',
      [shopId, name, category, sku, unit, quantity || 0, reorderLevel || 5, costPrice || 0, sellingPrice || 0]
    );
    return res.json({ success: true });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error adding product' });
  }
});

// PUT /api/products/:id — update an existing product (Admins only)
router.put('/:id', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const productId = parseInt(req.params.id);
  const { name, category, sku, unit, quantity, reorderLevel, costPrice, sellingPrice } = req.body;
  const pool = req.app.get('dbPool');

  try {
    const result = await pool.query(
      'UPDATE products SET name = $1, category = $2, sku = $3, unit = $4, quantity = $5, reorder_level = $6, cost_price = $7, selling_price = $8 WHERE id = $9',
      [name, category, sku, unit, quantity, reorderLevel, costPrice, sellingPrice, productId]
    );
    return res.json({ success: result.rowCount > 0 });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error updating product' });
  }
});

// POST /api/products/:id/retire — retire a product (Admins only)
router.post('/:id/retire', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const productId = parseInt(req.params.id);
  const pool = req.app.get('dbPool');

  try {
    const result = await pool.query(
      'UPDATE products SET quantity = 0, reorder_level = -1 WHERE id = $1',
      [productId]
    );
    return res.json({ success: result.rowCount > 0 });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error retiring product' });
  }
});

// DELETE /api/products/:id — delete a product (Admins only)
router.delete('/:id', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const productId = parseInt(req.params.id);
  const pool = req.app.get('dbPool');

  try {
    const result = await pool.query(
      'DELETE FROM products WHERE id = $1',
      [productId]
    );
    return res.json({ success: result.rowCount > 0 });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error deleting product' });
  }
});

module.exports = router;
