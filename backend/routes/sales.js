const express = require('express');
const router = express.Router();

// POST /api/sales — Record a transaction (uses database transaction)
router.post('/', async (req, res) => {
  const { shopId, saleDate, totalAmount, items, paymentMethod } = req.body;
  const pool     = req.app.get('dbPool');
  const auditLog = req.app.get('auditLog');

  // Validate payment method (default to CASH)
  const validMethods = ['CASH', 'PHONE'];
  const method = validMethods.includes(paymentMethod) ? paymentMethod : 'CASH';

  if (!shopId || !saleDate || !totalAmount || !items || !Array.isArray(items) || items.length === 0) {
    return res.status(400).json({ error: 'Invalid sale transaction payload' });
  }

  // RLS Isolation: Workers can only record sales for their assigned shop
  if (req.user.role === 'WORKER' && parseInt(shopId) !== req.user.shopId) {
    return res.status(403).json({ error: 'Access denied: You can only record sales for your own shop' });
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // Increment receipt counter (locked for this transaction)
    const counterRes = await client.query("SELECT value FROM settings WHERE key = 'receipt_counter' FOR UPDATE");
    let counter = 1;
    if (counterRes.rows.length > 0) {
      const parsed = parseInt(counterRes.rows[0].value);
      // L-4: Guard against corrupted counter value
      counter = isNaN(parsed) ? 1 : parsed;
    }
    const nextCounter = counter + 1;
    await client.query(
      "INSERT INTO settings (key, value) VALUES ('receipt_counter', $1) ON CONFLICT (key) DO UPDATE SET value = $1",
      [String(nextCounter)]
    );

    const receiptNo = `RCP-${String(counter).padStart(4, '0')}`;

    // Insert sale header
    const saleInsertRes = await client.query(
      'INSERT INTO sales (shop_id, sale_date, total_amount, receipt_number, payment_method) VALUES ($1, $2, $3, $4, $5) RETURNING id',
      [shopId, saleDate, totalAmount, receiptNo, method]
    );
    const saleId = saleInsertRes.rows[0].id;

    // Insert sale items, verifying stock and shop ownership per item
    for (const item of items) {
      // H-4: Verify each product belongs to this shop
      const productRes = await client.query(
        'SELECT shop_id, quantity FROM products WHERE id = $1',
        [item.productId]
      );
      if (productRes.rows.length === 0) {
        throw new Error(`Product ID ${item.productId} not found`);
      }
      if (productRes.rows[0].shop_id !== parseInt(shopId)) {
        throw new Error(`Product ID ${item.productId} does not belong to shop ${shopId}`);
      }

      // H-3: Verify sufficient stock before deducting
      const deductRes = await client.query(
        'UPDATE products SET quantity = quantity - $1 WHERE id = $2 AND quantity >= $1 RETURNING id',
        [item.quantitySold, item.productId]
      );
      if (deductRes.rowCount === 0) {
        throw new Error(`Insufficient stock for product ID ${item.productId}`);
      }

      await client.query(
        'INSERT INTO sale_items (sale_id, product_id, quantity_sold, unit_price) VALUES ($1, $2, $3, $4)',
        [saleId, item.productId, item.quantitySold, item.unitPrice]
      );
    }

    await client.query('COMMIT');

    // F-1: Audit the sale
    await auditLog(pool, req.user.userId, 'SALE_RECORDED',
      `Receipt ${receiptNo} — ${items.length} item(s), total ${totalAmount} [${method}] — shop ${shopId}`);

    return res.json({ saleId, receiptNumber: receiptNo });
  } catch (err) {
    await client.query('ROLLBACK');
    console.error('Sales transaction error:', err.message);
    return res.status(400).json({ error: err.message || 'Transaction failed, database rolled back.' });
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
      'SELECT id, shop_id, sale_date::text, total_amount::float8, receipt_number, payment_method FROM sales WHERE shop_id = $1 AND sale_date BETWEEN $2 AND $3 ORDER BY sale_date DESC, id DESC',
      [shopId, from, to]
    );
    return res.json(result.rows);
  } catch (err) {
    console.error('Fetch sales error:', err.message);
    return res.status(500).json({ error: 'Database error fetching sales list' });
  }
});

// GET /api/sales/items/:saleId — fetch items of a specific sale
router.get('/items/:saleId', async (req, res) => {
  const saleId = parseInt(req.params.saleId);
  const pool   = req.app.get('dbPool');

  try {
    // M-2: Verify the sale belongs to this user's shop (workers cannot peek at other shops' sales)
    const saleRes = await pool.query('SELECT shop_id FROM sales WHERE id = $1', [saleId]);
    if (saleRes.rows.length === 0) {
      return res.status(404).json({ error: 'Sale not found' });
    }
    if (req.user.role === 'WORKER' && saleRes.rows[0].shop_id !== req.user.shopId) {
      return res.status(403).json({ error: 'Access denied: This sale does not belong to your shop' });
    }

    const result = await pool.query(
      `SELECT si.id, si.sale_id, si.product_id, si.quantity_sold, si.unit_price::float8,
              p.name AS product_name
       FROM sale_items si
       JOIN products p ON si.product_id = p.id
       WHERE si.sale_id = $1`,
      [saleId]
    );
    return res.json(result.rows);
  } catch (err) {
    console.error('Fetch sale items error:', err.message);
    return res.status(500).json({ error: 'Database error fetching sale items' });
  }
});

// GET /api/sales/flat-rows — flat sales history listing
router.get('/flat-rows', async (req, res) => {
  let shopId = req.query.shopId ? parseInt(req.query.shopId) : null;
  let { from, to, limit } = req.query;
  const pool = req.app.get('dbPool');

  if (req.user.role === 'WORKER') {
    shopId = req.user.shopId;
  }

  if (!shopId) {
    return res.status(400).json({ error: 'shopId parameter is required' });
  }

  if (!from) from = '2020-01-01';
  if (!to)   to   = '2099-12-31';

  try {
    const result = await pool.query(
      `SELECT s.id AS sale_id, s.sale_date::text, s.receipt_number, s.total_amount::float8, s.payment_method,
              p.name AS product_name, p.category AS product_category, p.unit AS product_unit,
              si.quantity_sold, si.unit_price::float8, sh.name AS shop_name
       FROM sale_items si
       JOIN sales s    ON si.sale_id    = s.id
       JOIN products p ON si.product_id = p.id
       JOIN shops sh   ON s.shop_id     = sh.id
       WHERE s.shop_id = $1 AND s.sale_date BETWEEN $2 AND $3
       ORDER BY s.sale_date DESC, s.id DESC`,
      [shopId, from, to]
    );
    return res.json(result.rows);
  } catch (err) {
    console.error('Fetch flat-rows error:', err.message);
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
         COALESCE(SUM(CASE WHEN sale_date >= date_trunc('month', CURRENT_DATE) THEN total_amount END), 0)::float8 AS month_sales,
         COALESCE(SUM(CASE WHEN sale_date = CURRENT_DATE AND payment_method = 'CASH'  THEN total_amount END), 0)::float8 AS today_cash,
         COALESCE(SUM(CASE WHEN sale_date = CURRENT_DATE AND payment_method = 'PHONE' THEN total_amount END), 0)::float8 AS today_phone
       FROM sales WHERE shop_id = $1`,
      [shopId]
    );

    const pRes = await pool.query(
      `SELECT
         COALESCE(SUM(quantity * selling_price), 0)::float8 AS stock_value,
         COUNT(CASE WHEN quantity < 5 THEN 1 END)::int       AS low_count
       FROM products WHERE shop_id = $1`,
      [shopId]
    );

    const eRes = await pool.query(
      `SELECT
         COALESCE(SUM(CASE WHEN expense_date = CURRENT_DATE THEN amount END), 0)::float8                               AS today_expenses,
         COALESCE(SUM(CASE WHEN expense_date = CURRENT_DATE AND payment_method = 'CASH'  THEN amount END), 0)::float8 AS today_exp_cash,
         COALESCE(SUM(CASE WHEN expense_date = CURRENT_DATE AND payment_method = 'PHONE' THEN amount END), 0)::float8 AS today_exp_phone
       FROM expenses WHERE shop_id = $1`,
      [shopId]
    );

    const stats    = sRes.rows[0];
    const products = pRes.rows[0];
    const expenses = eRes.rows[0];

    return res.json([
      stats.today_sales  || 0.0,
      stats.week_sales   || 0.0,
      stats.month_sales  || 0.0,
      products.stock_value || 0.0,
      parseFloat(products.low_count || 0),
      stats.today_cash   || 0.0,
      stats.today_phone  || 0.0,
      expenses.today_expenses || 0.0,
      expenses.today_exp_cash || 0.0,
      expenses.today_exp_phone || 0.0
    ]);
  } catch (err) {
    console.error('Dashboard stats error:', err.message);
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
    console.error('Overview stats error:', err.message);
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

  const queryLimit = Math.min(parseInt(limit) || 5, 50); // cap at 50 to prevent abuse

  try {
    const result = await pool.query(
      `SELECT p.name AS product_name, SUM(si.quantity_sold * si.unit_price)::float8 AS revenue
       FROM sale_items si
       JOIN products p ON si.product_id = p.id
       JOIN sales s    ON si.sale_id    = s.id
       WHERE s.shop_id = $1 AND s.sale_date BETWEEN $2 AND $3
       GROUP BY p.name ORDER BY revenue DESC LIMIT $4`,
      [shopId, from, to, queryLimit]
    );
    return res.json(result.rows.map(r => [r.product_name, r.revenue]));
  } catch (err) {
    console.error('Top products error:', err.message);
    return res.status(500).json({ error: 'Database error fetching top products' });
  }
});

// DELETE /api/sales/:id — Admin only; deletes sale and restores product stock
router.delete('/:id', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const saleId = parseInt(req.params.id);
  if (isNaN(saleId)) return res.status(400).json({ error: 'Invalid sale ID' });

  const pool     = req.app.get('dbPool');
  const auditLog = req.app.get('auditLog');
  const client   = await pool.connect();

  try {
    await client.query('BEGIN');

    // Verify the sale exists
    const saleRes = await client.query('SELECT id, shop_id, receipt_number FROM sales WHERE id = $1', [saleId]);
    if (saleRes.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({ error: 'Sale not found' });
    }
    const { shop_id, receipt_number } = saleRes.rows[0];

    // Fetch sale items so we can restore stock
    const itemsRes = await client.query(
      'SELECT product_id, quantity_sold FROM sale_items WHERE sale_id = $1', [saleId]
    );

    // Restore each product's quantity
    for (const item of itemsRes.rows) {
      await client.query(
        'UPDATE products SET quantity = quantity + $1 WHERE id = $2',
        [item.quantity_sold, item.product_id]
      );
    }

    // Delete sale items first (FK), then the sale header
    await client.query('DELETE FROM sale_items WHERE sale_id = $1', [saleId]);
    await client.query('DELETE FROM sales WHERE id = $1', [saleId]);

    await client.query('COMMIT');

    await auditLog(pool, req.user.userId, 'SALE_DELETED',
      `Receipt ${receipt_number} deleted — stock restored — shop ${shop_id}`);

    return res.json({ message: `Sale ${receipt_number} deleted and inventory restored.` });
  } catch (err) {
    await client.query('ROLLBACK');
    console.error('Delete sale error:', err.message);
    return res.status(500).json({ error: 'Failed to delete sale: ' + err.message });
  } finally {
    client.release();
  }
});

module.exports = router;
