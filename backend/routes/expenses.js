const express = require('express');
const router = express.Router();

// GET /api/expenses — List expenses for a shop between 'from' and 'to' dates
router.get('/', async (req, res) => {
  let shopId = req.query.shopId ? parseInt(req.query.shopId) : null;
  const { from, to } = req.query;
  const pool = req.app.get('dbPool');

  if (req.user.role === 'WORKER') {
    shopId = req.user.shopId;
  }

  if (!shopId) {
    return res.status(400).json({ error: 'shopId query parameter is required' });
  }

  try {
    let query = `
      SELECT e.id, e.shop_id, e.user_id, u.username, e.expense_date,
             e.amount::float8, e.category, e.payment_method, e.notes, e.created_at
      FROM expenses e
      LEFT JOIN users u ON e.user_id = u.id
      WHERE e.shop_id = $1
    `;
    const params = [shopId];

    if (from && to) {
      query += ' AND e.expense_date BETWEEN $2 AND $3';
      params.push(from, to);
    }

    query += ' ORDER BY e.expense_date DESC, e.id DESC';

    const result = await pool.query(query, params);
    return res.json(result.rows.map(r => ({
      id:            r.id,
      shopId:        r.shop_id,
      userId:        r.user_id,
      username:      r.username || 'System',
      expenseDate:   r.expense_date,
      amount:        r.amount,
      category:      r.category,
      paymentMethod: r.payment_method || 'CASH',
      notes:         r.notes || '',
      createdAt:     r.created_at
    })));
  } catch (err) {
    console.error('Fetch expenses error:', err.message);
    return res.status(500).json({ error: 'Database error fetching expenses' });
  }
});

// POST /api/expenses — Record a new expense
router.post('/', async (req, res) => {
  const { shopId, expenseDate, amount, category, paymentMethod, notes } = req.body;
  const pool     = req.app.get('dbPool');
  const auditLog = req.app.get('auditLog');

  if (!shopId || !amount || parseFloat(amount) <= 0) {
    return res.status(400).json({ error: 'Shop ID and a valid positive amount are required' });
  }

  // RLS Isolation: Workers can only record expenses for their assigned shop
  if (req.user.role === 'WORKER' && parseInt(shopId) !== req.user.shopId) {
    return res.status(403).json({ error: 'Access denied: You can only record expenses for your assigned shop' });
  }

  const validMethods = ['CASH', 'PHONE'];
  const method = validMethods.includes(paymentMethod) ? paymentMethod : 'CASH';
  const expCat = category && category.trim().length > 0 ? category.trim() : 'Other';
  const date   = expenseDate || new Date().toISOString().split('T')[0];

  try {
    const result = await pool.query(
      `INSERT INTO expenses (shop_id, user_id, expense_date, amount, category, payment_method, notes)
       VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING id`,
      [shopId, req.user.userId, date, amount, expCat, method, notes || null]
    );

    await auditLog(pool, req.user.userId, 'EXPENSE_RECORDED', `Expense of RWF ${amount} (${expCat}) recorded for shop ${shopId}`);
    return res.json({ success: true, id: result.rows[0].id });
  } catch (err) {
    console.error('Record expense error:', err.message);
    return res.status(500).json({ error: 'Database error recording expense' });
  }
});

// DELETE /api/expenses/:id — Delete an expense entry
router.delete('/:id', async (req, res) => {
  const expenseId = parseInt(req.params.id);
  const pool      = req.app.get('dbPool');
  const auditLog  = req.app.get('auditLog');

  try {
    const checkRes = await pool.query('SELECT shop_id, user_id, amount, category FROM expenses WHERE id = $1', [expenseId]);
    if (checkRes.rows.length === 0) {
      return res.status(404).json({ error: 'Expense record not found' });
    }

    const exp = checkRes.rows[0];

    // RLS: Workers can only delete expenses in their shop that they created, Admin can delete any
    if (req.user.role === 'WORKER' && (exp.shop_id !== req.user.shopId || exp.user_id !== req.user.userId)) {
      return res.status(403).json({ error: 'Access denied: You can only delete your own shop expenses' });
    }

    await pool.query('DELETE FROM expenses WHERE id = $1', [expenseId]);
    await auditLog(pool, req.user.userId, 'EXPENSE_DELETED', `Expense ID ${expenseId} (RWF ${exp.amount}) deleted`);
    return res.json({ success: true });
  } catch (err) {
    console.error('Delete expense error:', err.message);
    return res.status(500).json({ error: 'Database error deleting expense' });
  }
});

module.exports = router;
