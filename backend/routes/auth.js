const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');

// POST /api/auth/login
router.post('/login', async (req, res) => {
  const { username, password } = req.body;
  const pool = req.app.get('dbPool');
  // C-1: Secret is validated at startup — safe to read here
  const JWT_SECRET = req.app.get('jwtSecret');
  const auditLog   = req.app.get('auditLog');

  if (!username || !password) {
    return res.status(400).json({ error: 'Username and password are required' });
  }

  try {
    const result = await pool.query(
      'SELECT id, username, password_hash, role, shop_id, must_change_password, failed_attempts, locked_until, token_version FROM users WHERE username = $1',
      [username]
    );

    if (result.rows.length === 0) {
      return res.status(401).json({ error: 'Incorrect username or password.' });
    }

    const user = result.rows[0];
    const now  = new Date();

    // Check account lockout
    if (user.locked_until && new Date(user.locked_until) > now) {
      const lockSecs = Math.ceil((new Date(user.locked_until) - now) / 1000);
      return res.status(403).json({ error: `Account is locked. Try again in ${lockSecs} seconds.` });
    }

    const passwordMatch = await bcrypt.compare(password, user.password_hash);

    if (passwordMatch) {
      // Success: reset failed attempts and locks
      await pool.query(
        'UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE id = $1',
        [user.id]
      );

      // H-5: Include token_version so the token can be invalidated on logout
      const token = jwt.sign(
        {
          userId:       user.id,
          username:     user.username,
          role:         user.role,
          shopId:       user.shop_id,
          tokenVersion: user.token_version
        },
        JWT_SECRET,
        { expiresIn: '8h' }   // H-5: reduced from 24h to 8h (one workday)
      );

      // F-1: Audit successful login
      await auditLog(pool, user.id, 'LOGIN_SUCCESS', `User "${user.username}" logged in`);

      return res.json({
        token,
        user: {
          id:                 user.id,
          username:           user.username,
          role:               user.role,
          shopId:             user.shop_id,
          mustChangePassword: user.must_change_password
        }
      });
    } else {
      // Failure: increment attempts
      let failedAttempts = user.failed_attempts + 1;
      let lockedUntil    = null;
      let lockMsg        = '';

      if (failedAttempts >= 3) {
        let lockMillis = 0;
        if (failedAttempts === 3) {
          lockMillis = 30 * 1000;          lockMsg = ' Account locked for 30 seconds.';
        } else if (failedAttempts === 4) {
          lockMillis = 2 * 60 * 1000;     lockMsg = ' Account locked for 2 minutes.';
        } else if (failedAttempts === 5) {
          lockMillis = 10 * 60 * 1000;    lockMsg = ' Account locked for 10 minutes.';
        } else {
          lockMillis = 60 * 60 * 1000;    lockMsg = ' Account locked for 1 hour.';
        }
        lockedUntil = new Date(Date.now() + lockMillis);
      }

      await pool.query(
        'UPDATE users SET failed_attempts = $1, locked_until = $2 WHERE id = $3',
        [failedAttempts, lockedUntil, user.id]
      );

      // F-1: Audit failed login
      await auditLog(pool, user.id, 'LOGIN_FAILED', `Failed login attempt for "${user.username}" (attempt ${failedAttempts})`);

      return res.status(401).json({ error: `Incorrect username or password.${lockMsg}` });
    }
  } catch (err) {
    console.error('Login error:', err.message);
    return res.status(500).json({ error: 'Authentication error' });
  }
});

// POST /api/auth/logout — F-2: Revoke token by incrementing token_version
router.post('/logout', async (req, res) => {
  const authHeader = req.headers['authorization'];
  const token      = authHeader && authHeader.split(' ')[1];
  const pool       = req.app.get('dbPool');
  const JWT_SECRET = req.app.get('jwtSecret');
  const auditLog   = req.app.get('auditLog');

  if (!token) {
    return res.status(200).json({ success: true }); // already logged out
  }

  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    // Increment token_version — invalidates ALL existing tokens for this user
    await pool.query(
      'UPDATE users SET token_version = token_version + 1 WHERE id = $1',
      [decoded.userId]
    );
    await auditLog(pool, decoded.userId, 'LOGOUT', `User "${decoded.username}" logged out`);
    return res.json({ success: true });
  } catch (err) {
    // Token is already invalid/expired — treat as already logged out
    return res.json({ success: true });
  }
});

module.exports = router;
