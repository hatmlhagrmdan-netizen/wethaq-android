import Database from 'better-sqlite3';

const db = new Database(process.env.DB_PATH || 'wethaq.db');
db.pragma('journal_mode=WAL');

db.exec(`CREATE TABLE IF NOT EXISTS users(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  wethaq_id TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  birth_year INTEGER NOT NULL DEFAULT 0,
  password_hash TEXT,
  device_key TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen TEXT,
  avatar_data TEXT,
  avatar_mime TEXT
)`);

const name = 'حاتم حسين الحاج رمضان';
const birthYear = 1995;
const wethaqId = 'Hatem_Hussin_Al_Haj_Ramadan1995';

const existing = db.prepare('SELECT id FROM users WHERE wethaq_id=? OR (name=? AND birth_year=?)').get(wethaqId, name, birthYear);
if (!existing) {
  db.prepare('INSERT INTO users(wethaq_id,name,birth_year,last_seen) VALUES(?,?,?,CURRENT_TIMESTAMP)').run(wethaqId, name, birthYear);
  console.log('Wethaq founder identity seeded');
} else {
  db.prepare('UPDATE users SET name=?,birth_year=? WHERE id=?').run(name, birthYear, existing.id);
  console.log('Wethaq founder identity verified');
}

db.close();
