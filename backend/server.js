import express from 'express';
import cors from 'cors';
import http from 'http';
import crypto from 'crypto';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import Database from 'better-sqlite3';
import { WebSocketServer } from 'ws';

const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });
const db = new Database(process.env.DB_PATH || 'wethaq.db');
const PORT = Number(process.env.PORT || 3000);
const JWT_SECRET = process.env.JWT_SECRET || crypto.randomBytes(32).toString('hex');

app.use(cors({ origin: true }));
app.use(express.json({ limit: '1mb' }));

const online = new Map();

db.pragma('journal_mode = WAL');
db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  wethaq_id TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS contacts (
  user_id INTEGER NOT NULL,
  contact_id INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(user_id, contact_id)
);
CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sender_id INTEGER NOT NULL,
  receiver_id INTEGER NOT NULL,
  body TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'sent',
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_messages_pair ON messages(sender_id, receiver_id, id);
`);

function tokenFor(user) {
  return jwt.sign({ sub: user.id, wethaqId: user.wethaq_id }, JWT_SECRET, { expiresIn: '30d' });
}

function auth(req, res, next) {
  const value = req.headers.authorization || '';
  const token = value.startsWith('Bearer ') ? value.slice(7) : '';
  if (!token) return res.status(401).json({ error: 'unauthorized' });
  try {
    req.user = jwt.verify(token, JWT_SECRET);
    next();
  } catch {
    return res.status(401).json({ error: 'invalid_token' });
  }
}

function makeWethaqId() {
  const words = ['nour','sama','wafa','saf','huda','aman','sham','ward','zain','mira','sabr','fajr'];
  for (;;) {
    const id = `${words[Math.floor(Math.random()*words.length)]}-${words[Math.floor(Math.random()*words.length)]}-${Math.floor(1000 + Math.random()*9000)}`;
    if (!db.prepare('SELECT 1 FROM users WHERE wethaq_id=?').get(id)) return id;
  }
}

app.get('/health', (_req, res) => res.json({ ok: true, service: 'wethaq', version: '1.0.0' }));

app.post('/api/register', async (req, res) => {
  const name = String(req.body?.name || '').trim();
  const password = String(req.body?.password || '');
  if (name.length < 2 || password.length < 8) return res.status(400).json({ error: 'name_and_password_required' });
  const hash = await bcrypt.hash(password, 12);
  const wethaqId = makeWethaqId();
  const result = db.prepare('INSERT INTO users (wethaq_id,name,password_hash) VALUES (?,?,?)').run(wethaqId, name, hash);
  const user = db.prepare('SELECT id,wethaq_id,name,created_at FROM users WHERE id=?').get(result.lastInsertRowid);
  res.status(201).json({ user, token: tokenFor(user) });
});

app.post('/api/login', async (req, res) => {
  const wethaqId = String(req.body?.wethaqId || '').trim();
  const password = String(req.body?.password || '');
  const user = db.prepare('SELECT * FROM users WHERE wethaq_id=?').get(wethaqId);
  if (!user || !(await bcrypt.compare(password, user.password_hash))) return res.status(401).json({ error: 'invalid_credentials' });
  res.json({ user: { id:user.id, wethaq_id:user.wethaq_id, name:user.name, created_at:user.created_at }, token: tokenFor(user) });
});

app.get('/api/me', auth, (req, res) => {
  const user = db.prepare('SELECT id,wethaq_id,name,created_at FROM users WHERE id=?').get(req.user.sub);
  res.json({ user });
});

app.get('/api/contacts', auth, (req, res) => {
  const rows = db.prepare(`SELECT u.id,u.wethaq_id,u.name FROM contacts c JOIN users u ON u.id=c.contact_id WHERE c.user_id=? ORDER BY u.name`).all(req.user.sub);
  res.json({ contacts: rows });
});

app.post('/api/contacts', auth, (req, res) => {
  const wethaqId = String(req.body?.wethaqId || '').trim();
  const contact = db.prepare('SELECT id,wethaq_id,name FROM users WHERE wethaq_id=?').get(wethaqId);
  if (!contact) return res.status(404).json({ error: 'user_not_found' });
  if (contact.id === req.user.sub) return res.status(400).json({ error: 'cannot_add_self' });
  db.prepare('INSERT OR IGNORE INTO contacts(user_id,contact_id) VALUES (?,?)').run(req.user.sub, contact.id);
  res.status(201).json({ contact });
});

app.get('/api/messages/:wethaqId', auth, (req, res) => {
  const other = db.prepare('SELECT id,wethaq_id,name FROM users WHERE wethaq_id=?').get(req.params.wethaqId);
  if (!other) return res.status(404).json({ error: 'user_not_found' });
  const messages = db.prepare(`SELECT id,sender_id,receiver_id,body,status,created_at FROM messages WHERE (sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?) ORDER BY id ASC LIMIT 500`).all(req.user.sub, other.id, other.id, req.user.sub);
  res.json({ contact: other, messages });
});

app.post('/api/messages', auth, (req, res) => {
  const to = String(req.body?.to || '').trim();
  const body = String(req.body?.body || '').trim();
  if (!to || !body || body.length > 4000) return res.status(400).json({ error: 'invalid_message' });
  const receiver = db.prepare('SELECT id,wethaq_id,name FROM users WHERE wethaq_id=?').get(to);
  if (!receiver) return res.status(404).json({ error: 'user_not_found' });
  const result = db.prepare('INSERT INTO messages(sender_id,receiver_id,body,status) VALUES (?,?,?,?)').run(req.user.sub, receiver.id, body, 'sent');
  const message = db.prepare('SELECT id,sender_id,receiver_id,body,status,created_at FROM messages WHERE id=?').get(result.lastInsertRowid);
  broadcast(receiver.id, { type: 'message', message });
  res.status(201).json({ message });
});

function broadcast(userId, payload) {
  const socket = online.get(Number(userId));
  if (socket?.readyState === 1) socket.send(JSON.stringify(payload));
}

wss.on('connection', (socket, request) => {
  try {
    const url = new URL(request.url, 'http://localhost');
    const token = url.searchParams.get('token') || '';
    const decoded = jwt.verify(token, JWT_SECRET);
    const userId = Number(decoded.sub);
    online.set(userId, socket);
    socket.send(JSON.stringify({ type: 'connected', online: true }));
    socket.on('close', () => { if (online.get(userId) === socket) online.delete(userId); });
  } catch {
    socket.close(1008, 'unauthorized');
  }
});

server.listen(PORT, '0.0.0.0', () => console.log(`Wethaq backend listening on ${PORT}`));
