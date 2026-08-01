/**
 * F-3: Weekly database backup export script.
 * Exports all tables from the Stock Manager database to a JSON file.
 * Run via GitHub Actions with BACKUP_DATABASE_URL secret.
 *
 * Setup: add BACKUP_DATABASE_URL as a GitHub repository secret
 *        (Settings → Secrets → Actions → New repository secret)
 */
const { Client } = require('pg');
const fs = require('fs');

const client = new Client({
  connectionString: process.env.DATABASE_URL,
  ssl: { rejectUnauthorized: false }
});

async function exportBackup() {
  await client.connect();
  console.log('Connected to database. Starting backup export...');

  const backup = {
    exported_at: new Date().toISOString(),
    tables: {}
  };

  const tables = ['shops', 'products', 'sales', 'sale_items', 'settings', 'users', 'audit_log'];

  for (const table of tables) {
    try {
      const res = await client.query(`SELECT * FROM ${table} ORDER BY id`);
      // Strip password_hash for security — this backup is for data recovery, not auth
      if (table === 'users') {
        backup.tables[table] = res.rows.map(r => {
          const { password_hash, ...safe } = r;
          return safe;
        });
      } else {
        backup.tables[table] = res.rows;
      }
      console.log(`  ✓ ${table}: ${res.rows.length} rows`);
    } catch (err) {
      console.warn(`  ⚠ Skipped ${table} (may not exist yet): ${err.message}`);
      backup.tables[table] = [];
    }
  }

  await client.end();

  fs.writeFileSync('backup.json', JSON.stringify(backup, null, 2));
  const sizeMb = (fs.statSync('backup.json').size / 1024 / 1024).toFixed(2);
  console.log(`\nBackup complete: backup.json (${sizeMb} MB)`);
}

exportBackup().catch(err => {
  console.error('Backup failed:', err);
  process.exit(1);
});
