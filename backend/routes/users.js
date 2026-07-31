const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');

// GET /api/users — list all users (Admins only)
router.get('/', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const pool = req.app.get('dbPool');
  try {
    const result = await pool.query(
      'SELECT id, username, role, shop_id, must_change_password FROM users ORDER BY role, username'
    );
    return res.json(result.rows.map(r => ({
      id: r.id,
      username: r.username,
      role: r.role,
      shopId: r.shop_id,
      mustChangePassword: r.must_change_password
    })));
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error fetching users list' });
  }
});

// POST /api/users — add a new user (Admins only)
router.post('/', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const { username, password, role, shopId } = req.body;
  const pool = req.app.get('dbPool');

  if (!username || !password || !role) {
    return res.status(400).json({ error: 'Username, password, and role are required' });
  }

  try {
    const hash = await bcrypt.hash(password, 10);
    await pool.query(
      'INSERT INTO users (username, password_hash, role, shop_id, must_change_password) VALUES ($1, $2, $3, $4, TRUE)',
      [username, hash, role, shopId]
    );
    return res.json({ success: true });
  } catch (err) {
    console.error(err);
    // Unique violation in PostgreSQL is code 23505
    if (err.code === '23505') {
      return res.status(409).json({ error: 'Username already exists' });
    }
    return res.status(500).json({ error: 'Database error creating user' });
  }
});

// POST /api/users/change-password — change current user password
router.post('/change-password', async (req, res) => {
  const { userId, newPassword } = req.body;
  const pool = req.app.get('dbPool');

  // Security: Users can only change their own password, unless they are an ADMIN
  if (req.user.role !== 'ADMIN' && req.user.userId !== parseInt(userId)) {
    return res.status(403).json({ error: 'Access denied: You cannot change another user\'s password' });
  }

  if (!newPassword || newPassword.length < 4) {
    return res.status(400).json({ error: 'Password must be at least 4 characters long' });
  }

  try {
    const hash = await bcrypt.hash(newPassword, 10);
    const result = await pool.query(
      'UPDATE users SET password_hash = $1, must_change_password = FALSE WHERE id = $2',
      [hash, userId]
    );
    return res.json({ success: result.rowCount > 0 });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error changing password' });
  }
});

// POST /api/users/reset-password — reset a user's password (Admins only)
router.post('/reset-password', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const { userId, newPassword } = req.body;
  const pool = req.app.get('dbPool');

  try {
    const hash = await bcrypt.hash(newPassword, 10);
    const result = await pool.query(
      'UPDATE users SET password_hash = $1, must_change_password = TRUE WHERE id = $2',
      [hash, userId]
    );
    return res.json({ success: result.rowCount > 0 });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error resetting password' });
  }
});

// DELETE /api/users/:id — delete a user (Admins only)
router.delete('/:id', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const userId = parseInt(req.params.id);
  const pool = req.app.get('dbPool');

  try {
    const result = await pool.query('DELETE FROM users WHERE id = $1', [userId]);
    return res.json({ success: result.rowCount > 0 });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error deleting user' });
  }
});

module.exports = router;
