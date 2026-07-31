const express = require('express');
const router = express.Router();

// POST /api/sales — Record a transaction (uses database transaction)
router.post('/', async (req, res) => {
  const { shopId, saleDate, totalAmount, items } = req.body;
  const pool = req.app.get('dbPool');

  if (!shopId || !saleDate || !totalAmount || !items || !Array.isArray(items)) {
    return res.status(400).json({ error: 'Invalid sale transaction payload' });
  }

  // RLS Isolation: Workers can only record sales for their assigned shop
  if (req.user.role === 'WORKER' && parseInt(shopId) !== req.user.shopId) {
    return res.status(403).json({ error: 'Access denied: You can only record sales for your own shop' });
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN'); // Start Transaction

    // Increment receipt counter setting
    const counterRes = await client.query("SELECT value FROM settings WHERE key = 'receipt_counter' FOR UPDATE");
    let counter = 1;
    if (counterRes.rows.length > 0) {
      counter = parseInt(counterRes.rows[0].value);
    }
    
    const nextCounter = counter + 1;
    await client.query("INSERT INTO settings (key, value) VALUES ('receipt_counter', $1) ON CONFLICT (key) DO UPDATE SET value = $1", [String(nextCounter)]);

    // Generate formatted receipt number
    const receiptNo = `RCP-${String(counter).padStart(4, '0')}`;

    // Insert sale
    const saleInsertRes = await client.query(
      'INSERT INTO sales (shop_id, sale_date, total_amount, receipt_number) VALUES ($1, $2, $3, $4) RETURNING id',
      [shopId, saleDate, totalAmount, receiptNo]
    );
    const saleId = saleInsertRes.rows[0].id;

    // Insert sale items and deduct inventory quantities
    for (const item of items) {
      await client.query(
        'INSERT INTO sale_items (sale_id, product_id, quantity_sold, unit_price) VALUES ($1, $2, $3, $4)',
        [saleId, item.productId, item.quantitySold, item.unitPrice]
      );

      await client.query(
        'UPDATE products SET quantity = quantity - $1 WHERE id = $2',
        [item.quantitySold, item.productId]
      );
    }

    await client.query('COMMIT'); // Commit Transaction
    return res.json({ saleId, receiptNumber: receiptNo });
  } catch (err) {
    await client.query('ROLLBACK'); // Rollback Transaction on error
    console.error('Error during sales transaction:', err);
    return res.status(500).json({ error: 'Transaction failed, database rolled back.' });
  } finally {
    client.release();
  }
});

// GET /api/sales — fetch sales in date range
router.get('/', async (req, res) => {
  let shopId = req.query.shopId ? parseInt(req.query.shopId) : null;
  const { from, to } = req.query;
  const pool = req.app.get('dbPool');

  if (req.user.role === 'WORKER') {
    shopId = req.user.shopId;
  }

  if (!shopId || !from || !to) {
    return res.status(400).json({ error: 'shopId, from, and to parameters are required' });
  }

  try {
    const result = await pool.query(
      'SELECT id, shop_id, sale_date::text, total_amount::float8, receipt_number FROM sales WHERE shop_id = $1 AND sale_date BETWEEN $2 AND $3 ORDER BY sale_date DESC, id DESC',
      [shopId, from, to]
    );
    return res.json(result.rows);
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error fetching sales list' });
  }
});

// GET /api/sales/items/:saleId — fetch items of a sale
router.get('/items/:saleId', async (req, res) => {
  const saleId = parseInt(req.params.saleId);
  const pool = req.app.get('dbPool');

  try {
    const result = await pool.query(
      'SELECT si.id, si.sale_id, si.product_id, si.quantity_sold, si.unit_price::float8, p.name as product_name FROM sale_items si JOIN products p ON si.product_id = p.id WHERE si.sale_id = $1',
      [saleId]
    );
    return res.json(result.rows);
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error fetching sale items' });
  }
});

// GET /api/sales/flat-rows — flat sales history listing
router.get('/flat-rows', async (req, res) => {
  let shopId = req.query.shopId ? parseInt(req.query.shopId) : null;
  const { from, to } = req.query;
  const pool = req.app.get('dbPool');

  if (req.user.role === 'WORKER') {
    shopId = req.user.shopId;
  }

  if (!shopId || !from || !to) {
    return res.status(400).json({ error: 'shopId, from, and to parameters are required' });
  }

  try {
    const result = await pool.query(
      `SELECT s.sale_date::text, s.receipt_number, s.total_amount::float8, 
              p.name AS product_name, p.category AS product_category, p.unit AS product_unit, 
              si.quantity_sold, si.unit_price::float8, sh.name as shop_name 
       FROM sale_items si 
       JOIN sales s ON si.sale_id = s.id 
       JOIN products p ON si.product_id = p.id 
       JOIN shops sh ON s.shop_id = sh.id 
       WHERE s.shop_id = $1 AND s.sale_date BETWEEN $2 AND $3 
       ORDER BY s.sale_date DESC, s.id DESC`,
      [shopId, from, to]
    );
    return res.json(result.rows);
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error fetching flat sales' });
  }
});

// GET /api/sales/dashboard-stats — dashboard aggregates
router.get('/dashboard-stats', async (req, res) => {
  let shopId = req.query.shopId ? parseInt(req.query.shopId) : null;
  const pool = req.app.get('dbPool');

  if (req.user.role === 'WORKER') {
    shopId = req.user.shopId;
  }

  if (!shopId) {
    return res.status(400).json({ error: 'shopId parameter is required' });
  }

  try {
    const sRes = await pool.query(
      `SELECT 
         COALESCE(SUM(CASE WHEN sale_date = CURRENT_DATE THEN total_amount END), 0)::float8              AS today_sales,
         COALESCE(SUM(CASE WHEN sale_date >= date_trunc('week',  CURRENT_DATE) THEN total_amount END), 0)::float8 AS week_sales,
         COALESCE(SUM(CASE WHEN sale_date >= date_trunc('month', CURRENT_DATE) THEN total_amount END), 0)::float8 AS month_sales 
       FROM sales WHERE shop_id = $1`,
      [shopId]
    );

    const pRes = await pool.query(
      `SELECT 
         COALESCE(SUM(quantity * selling_price), 0)::float8               AS stock_value,
         COUNT(CASE WHEN quantity < 5 THEN 1 END)::int                     AS low_count 
       FROM products WHERE shop_id = $1`,
      [shopId]
    );

    const stats = sRes.rows[0];
    const products = pRes.rows[0];

    return res.json([
      stats.today_sales || 0.0,
      stats.week_sales || 0.0,
      stats.month_sales || 0.0,
      products.stock_value || 0.0,
      parseFloat(products.low_count || 0)
    ]);
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error calculating dashboard stats' });
  }
});

// GET /api/sales/overview-stats — global admin shop summary stats
router.get('/overview-stats', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }
  const pool = req.app.get('dbPool');

  try {
    const result = await pool.query(
      `SELECT sh.id, sh.name,
              COALESCE(SUM(CASE WHEN s.sale_date = CURRENT_DATE THEN s.total_amount END), 0)::float8               AS today_sales,
              COALESCE(SUM(CASE WHEN s.sale_date >= date_trunc('month', CURRENT_DATE) THEN s.total_amount END), 0)::float8 AS month_sales,
              COALESCE((SELECT SUM(p.quantity * p.selling_price) FROM products p WHERE p.shop_id = sh.id), 0)::float8 AS stock_value,
              COALESCE((SELECT COUNT(*) FROM products p WHERE p.shop_id = sh.id AND p.quantity < 5), 0)::int    AS low_stock,
              COALESCE((SELECT COUNT(*) FROM products p WHERE p.shop_id = sh.id), 0)::int                       AS total_products 
       FROM shops sh 
       LEFT JOIN sales s ON s.shop_id = sh.id 
       GROUP BY sh.id, sh.name ORDER BY sh.id`
    );
    return res.json(result.rows);
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error calculating global overview stats' });
  }
});

// GET /api/sales/top-products — top product sales rank report
router.get('/top-products', async (req, res) => {
  let shopId = req.query.shopId ? parseInt(req.query.shopId) : null;
  const { from, to, limit } = req.query;
  const pool = req.app.get('dbPool');

  if (req.user.role === 'WORKER') {
    shopId = req.user.shopId;
  }

  if (!shopId || !from || !to) {
    return res.status(400).json({ error: 'shopId, from, and to parameters are required' });
  }

  const queryLimit = limit ? parseInt(limit) : 5;

  try {
    const result = await pool.query(
      `SELECT p.name as product_name, SUM(si.quantity_sold * si.unit_price)::float8 as revenue 
       FROM sale_items si JOIN products p ON si.product_id = p.id 
       JOIN sales s ON si.sale_id = s.id 
       WHERE s.shop_id = $1 AND s.sale_date BETWEEN $2 AND $3 
       GROUP BY p.name ORDER BY revenue DESC LIMIT $4`,
      [shopId, from, to, queryLimit]
    );
    return res.json(result.rows.map(r => [r.product_name, r.revenue]));
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error fetching top products' });
  }
});

module.exports = router;
