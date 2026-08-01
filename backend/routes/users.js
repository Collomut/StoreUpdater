const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');

// H-2: Minimum password length raised to 8 characters
const MIN_PASSWORD_LENGTH = 8;

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
      id:                 r.id,
      username:           r.username,
      role:               r.role,
      shopId:             r.shop_id,
      mustChangePassword: r.must_change_password
    })));
  } catch (err) {
    console.error('Fetch users error:', err.message);
    return res.status(500).json({ error: 'Database error fetching users list' });
  }
});

// POST /api/users — add a new user (Admins only)
router.post('/', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const { username, password, role, shopId } = req.body;
  const pool     = req.app.get('dbPool');
  const auditLog = req.app.get('auditLog');

  if (!username || !password || !role) {
    return res.status(400).json({ error: 'Username, password, and role are required' });
  }

  // H-2: Enforce minimum password length
  if (password.length < MIN_PASSWORD_LENGTH) {
    return res.status(400).json({ error: `Password must be at least ${MIN_PASSWORD_LENGTH} characters long` });
  }

  if (!['ADMIN', 'WORKER'].includes(role)) {
    return res.status(400).json({ error: 'Role must be ADMIN or WORKER' });
  }

  try {
    const hash = await bcrypt.hash(password, 10);
    await pool.query(
      'INSERT INTO users (username, password_hash, role, shop_id, must_change_password) VALUES ($1, $2, $3, $4, TRUE)',
      [username, hash, role, shopId || null]
    );
    await auditLog(pool, req.user.userId, 'USER_CREATED', `Admin "${req.user.username}" created user "${username}" (${role})`);
    return res.json({ success: true });
  } catch (err) {
    console.error('Create user error:', err.message);
    if (err.code === '23505') {
      return res.status(409).json({ error: 'Username already exists' });
    }
    return res.status(500).json({ error: 'Database error creating user' });
  }
});

// POST /api/users/change-password — change current user's own password
router.post('/change-password', async (req, res) => {
  const { userId, newPassword } = req.body;
  const pool     = req.app.get('dbPool');
  const auditLog = req.app.get('auditLog');

  // Security: users can only change their own password, unless ADMIN
  if (req.user.role !== 'ADMIN' && req.user.userId !== parseInt(userId)) {
    return res.status(403).json({ error: 'Access denied: You cannot change another user\'s password' });
  }

  // H-2: Enforce minimum password length
  if (!newPassword || newPassword.length < MIN_PASSWORD_LENGTH) {
    return res.status(400).json({ error: `Password must be at least ${MIN_PASSWORD_LENGTH} characters long` });
  }

  try {
    const hash = await bcrypt.hash(newPassword, 10);
    // H-5: Increment token_version to revoke all existing sessions for this user
    const result = await pool.query(
      'UPDATE users SET password_hash = $1, must_change_password = FALSE, token_version = token_version + 1 WHERE id = $2',
      [hash, userId]
    );
    await auditLog(pool, req.user.userId, 'PASSWORD_CHANGED', `Password changed for user ID ${userId}`);
    return res.json({ success: result.rowCount > 0 });
  } catch (err) {
    console.error('Change password error:', err.message);
    return res.status(500).json({ error: 'Database error changing password' });
  }
});

// POST /api/users/reset-password — reset a user's password (Admins only)
router.post('/reset-password', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const { userId, newPassword } = req.body;
  const pool     = req.app.get('dbPool');
  const auditLog = req.app.get('auditLog');

  // H-2: Enforce minimum password length
  if (!newPassword || newPassword.length < MIN_PASSWORD_LENGTH) {
    return res.status(400).json({ error: `Password must be at least ${MIN_PASSWORD_LENGTH} characters long` });
  }

  try {
    const hash = await bcrypt.hash(newPassword, 10);
    // H-5: Increment token_version to revoke existing sessions for reset user
    const result = await pool.query(
      'UPDATE users SET password_hash = $1, must_change_password = TRUE, token_version = token_version + 1 WHERE id = $2',
      [hash, userId]
    );
    await auditLog(pool, req.user.userId, 'PASSWORD_RESET', `Admin "${req.user.username}" reset password for user ID ${userId}`);
    return res.json({ success: result.rowCount > 0 });
  } catch (err) {
    console.error('Reset password error:', err.message);
    return res.status(500).json({ error: 'Database error resetting password' });
  }
});

// DELETE /api/users/:id — delete a user (Admins only)
router.delete('/:id', async (req, res) => {
  if (req.user.role !== 'ADMIN') {
    return res.status(403).json({ error: 'Access denied: Administrators only' });
  }

  const userId   = parseInt(req.params.id);
  const pool     = req.app.get('dbPool');
  const auditLog = req.app.get('auditLog');

  // C-3: Prevent self-deletion
  if (userId === req.user.userId) {
    return res.status(400).json({ error: 'You cannot delete your own account.' });
  }

  // C-3: Prevent deleting the last admin
  try {
    const targetRes = await pool.query('SELECT role, username FROM users WHERE id = $1', [userId]);
    if (targetRes.rows.length === 0) {
      return res.status(404).json({ error: 'User not found' });
    }

    if (targetRes.rows[0].role === 'ADMIN') {
      const adminCountRes = await pool.query("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'");
      if (parseInt(adminCountRes.rows[0].count) <= 1) {
        return res.status(400).json({ error: 'Cannot delete the last administrator. Create another admin first.' });
      }
    }

    const deletedUsername = targetRes.rows[0].username;
    const result = await pool.query('DELETE FROM users WHERE id = $1', [userId]);
    await auditLog(pool, req.user.userId, 'USER_DELETED', `Admin "${req.user.username}" deleted user "${deletedUsername}"`);
    return res.json({ success: result.rowCount > 0 });
  } catch (err) {
    console.error('Delete user error:', err.message);
    return res.status(500).json({ error: 'Database error deleting user' });
  }
});

module.exports = router;
