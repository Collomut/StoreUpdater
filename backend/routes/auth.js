const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'fallback_secret_for_jwt_tokens_12345';

// POST /api/auth/login
router.post('/login', async (req, res) => {
  const { username, password } = req.body;
  const pool = req.app.get('dbPool');

  if (!username || !password) {
    return res.status(400).json({ error: 'Username and password are required' });
  }

  try {
    const result = await pool.query(
      'SELECT id, username, password_hash, role, shop_id, must_change_password, failed_attempts, locked_until FROM users WHERE username = $1',
      [username]
    );

    if (result.rows.length === 0) {
      return res.status(401).json({ error: 'Incorrect username or password.' });
    }

    const user = result.rows[0];
    const now = new Date();

    // Check account lockout
    if (user.locked_until && new Date(user.locked_until) > now) {
      const lockSecs = Math.ceil((new Date(user.locked_until) - now) / 1000);
      return res.status(403).json({ error: `Account is locked. Try again in ${lockSecs} seconds.` });
    }

    // Verify Password
    const passwordMatch = await bcrypt.compare(password, user.password_hash);
    if (passwordMatch) {
      // Success: Reset failed attempts & locks
      await pool.query(
        'UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE id = $1',
        [user.id]
      );

      // Sign JWT token
      const token = jwt.sign(
        { userId: user.id, username: user.username, role: user.role, shopId: user.shop_id },
        JWT_SECRET,
        { expiresIn: '24h' }
      );

      return res.json({
        token,
        user: {
          id: user.id,
          username: user.username,
          role: user.role,
          shopId: user.shop_id,
          mustChangePassword: user.must_change_password
        }
      });
    } else {
      // Failure: Increment attempts
      let failedAttempts = user.failed_attempts + 1;
      let lockedUntil = null;
      let lockMsg = '';

      if (failedAttempts >= 3) {
        let lockMillis = 0;
        if (failedAttempts === 3) {
          lockMillis = 30 * 1000;
          lockMsg = ' Account locked for 30 seconds.';
        } else if (failedAttempts === 4) {
          lockMillis = 2 * 60 * 1000;
          lockMsg = ' Account locked for 2 minutes.';
        } else if (failedAttempts === 5) {
          lockMillis = 10 * 60 * 1000;
          lockMsg = ' Account locked for 10 minutes.';
        } else {
          lockMillis = 60 * 60 * 1000;
          lockMsg = ' Account locked for 1 hour.';
        }
        lockedUntil = new Date(Date.now() + lockMillis);
      }

      await pool.query(
        'UPDATE users SET failed_attempts = $1, locked_until = $2 WHERE id = $3',
        [failedAttempts, lockedUntil, user.id]
      );

      return res.status(401).json({ error: `Incorrect username or password.${lockMsg}` });
    }
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Database error during login authentication' });
  }
});

module.exports = router;
