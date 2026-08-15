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
const MAX_MESSAGE = 4000;

app.disable('x-powered-by');
app.set('trust proxy', 1);
app.use(cors({
  origin: (origin, cb) => cb(null, !process.env.CORS_ORIGIN || !origin || process.env.CORS_ORIGIN.split(',').map(x => x.trim()).includes(origin)),
  credentials: false
}));
app.use(express.json({ limit: '256kb' }));

const online = new Map();
const attempts = new Map();

function now() { return new Date().toISOString(); }
function validText(value, max) { return typeof value === 'string' && value.trim().length > 0 && value.trim().length <= max; }
function rateLimit(key, limit, windowMs) {
  const t = Date.now();
  const old = attempts.get(key) || { count: 0, at: t };
  if (t - old.at > windowMs) { attempts.set(key, { count: 1, at: t }); return true; }
  old.count += 1;
  attempts.set(key, old);
  return old.count <= limit;
}

function ensureColumn(table, column, definition) {
  const columns = db.prepare(`PRAGMA table_info(${table})`).all().map(x => x.name);
  if (!columns.includes(column)) db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
}

db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');
db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  wethaq_id TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  birth_year INTEGER NOT NULL DEFAULT 0,
  password_hash TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen TEXT
);
CREATE TABLE IF NOT EXISTS contacts (
  user_id INTEGER NOT NULL,
  contact_id INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(user_id, contact_id),
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY(contact_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sender_id INTEGER NOT NULL,
  receiver_id INTEGER NOT NULL,
  body TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'sent',
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(sender_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY(receiver_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_messages_pair ON messages(sender_id, receiver_id, id);
CREATE INDEX IF NOT EXISTS idx_contacts_user ON contacts(user_id, contact_id);
`);
ensureColumn('users', 'birth_year', 'INTEGER NOT NULL DEFAULT 0');
ensureColumn('users', 'last_seen', 'TEXT');

const AR = {
  'حاتم':'Hatem','حسين':'Hussin','الحاج':'Al_Haj','رمضان':'Ramadan','عبد':'Abd','الله':'Allah',
  'محمد':'Mohammad','أحمد':'Ahmad','علي':'Ali','خالد':'Khaled','سارة':'Sara','سما':'Sama',
  'نور':'Nour','هدى':'Huda','أمان':'Aman','وسام':'Wisam','يامن':'Yamen','هشام':'Hisham','أيمن':'Ayman','حسام':'Hossam'
};
const MAP = {'ا':'a','أ':'a','إ':'i','آ':'a','ب':'b','ت':'t','ث':'th','ج':'j','ح':'h','خ':'kh','د':'d','ذ':'dh','ر':'r','ز':'z','س':'s','ش':'sh','ص':'s','ض':'d','ط':'t','ظ':'z','ع':'a','غ':'gh','ف':'f','ق':'q','ك':'k','ل':'l','م':'m','ن':'n','ه':'h','و':'w','ي':'y','ى':'a','ة':'h'};
function transliterateWord(word) {
  if (AR[word]) return AR[word];
  let out = '';
  for (const ch of word) out += MAP[ch] ?? (/[A-Za-z0-9]/.test(ch) ? ch : '');
  return out ? out[0].toUpperCase() + out.slice(1) : '';
}
function makeBaseId(name, birthYear) {
  const words = name.replace(/ـ/g, '').trim().split(/[\s،,]+/).filter(Boolean);
  const parts = words.map(transliterateWord).filter(Boolean);
  return `${parts.join('_')}${birthYear}`.replace(/[^A-Za-z0-9_]/g, '_');
}
function makeWethaqId(name, birthYear) {
  const base = makeBaseId(name, birthYear) || `Wethaq_User${birthYear}`;
  let id = base;
  let n = 2;
  while (db.prepare('SELECT 1 FROM users WHERE wethaq_id=?').get(id)) id = `${base}_${n++}`;
  return id;
}
function tokenFor(user) { return jwt.sign({ sub: user.id, wethaqId: user.wethaq_id }, JWT_SECRET, { expiresIn: '30d' }); }
function publicUser(user) { return { id:user.id, wethaq_id:user.wethaq_id, name:user.name, birth_year:user.birth_year || 0, created_at:user.created_at, last_seen:user.last_seen || null }; }
function auth(req, res, next) {
  const value = req.headers.authorization || '';
  const token = value.startsWith('Bearer ') ? value.slice(7) : '';
  if (!token) return res.status(401).json({ error:'unauthorized' });
  try { req.user = jwt.verify(token, JWT_SECRET); next(); } catch { return res.status(401).json({ error:'invalid_token' }); }
}
function isOnline(userId) {
  const sockets = online.get(Number(userId));
  return !!sockets && [...sockets].some(s => s.readyState === 1);
}
function broadcast(userId, payload) {
  const sockets = online.get(Number(userId));
  if (!sockets) return 0;
  const raw = JSON.stringify(payload);
  let count = 0;
  for (const socket of sockets) if (socket.readyState === 1) { socket.send(raw); count++; }
  return count;
}
function markSeen(userId) { db.prepare('UPDATE users SET last_seen=? WHERE id=?').run(now(), userId); }

app.get('/health', (_req,res) => res.json({ ok:true, service:'wethaq', version:'1.4.0', time:now() }));

app.post('/api/register', async (req,res) => {
  if (!rateLimit(`register:${req.ip}`, 8, 15*60*1000)) return res.status(429).json({ error:'rate_limited' });
  const name=String(req.body?.name||'').trim();
  const birthYear=Number(req.body?.birthYear);
  const password=String(req.body?.password||'');
  const current=new Date().getUTCFullYear();
  if (!validText(name,80) || !Number.isInteger(birthYear) || birthYear<1900 || birthYear>current) return res.status(400).json({ error:'invalid_birth_year' });
  if (password.length<8 || password.length>128) return res.status(400).json({ error:'name_and_password_required' });
  const wethaqId=makeWethaqId(name,birthYear);
  const hash=await bcrypt.hash(password,12);
  const result=db.prepare('INSERT INTO users (wethaq_id,name,birth_year,password_hash,last_seen) VALUES (?,?,?,?,?)').run(wethaqId,name,birthYear,hash,now());
  const user=db.prepare('SELECT id,wethaq_id,name,birth_year,created_at,last_seen FROM users WHERE id=?').get(result.lastInsertRowid);
  res.status(201).json({ user:publicUser(user), token:tokenFor(user) });
});

app.post('/api/login', async (req,res) => {
  if (!rateLimit(`login:${req.ip}`, 12, 10*60*1000)) return res.status(429).json({ error:'rate_limited' });
  const wethaqId=String(req.body?.wethaqId||'').trim(); const password=String(req.body?.password||'');
  const user=db.prepare('SELECT * FROM users WHERE wethaq_id=?').get(wethaqId);
  if (!user || !(await bcrypt.compare(password,user.password_hash))) return res.status(401).json({ error:'invalid_credentials' });
  markSeen(user.id); const fresh=db.prepare('SELECT id,wethaq_id,name,birth_year,created_at,last_seen FROM users WHERE id=?').get(user.id);
  res.json({ user:publicUser(fresh), token:tokenFor(fresh) });
});

app.get('/api/me',auth,(req,res)=>{const user=db.prepare('SELECT id,wethaq_id,name,birth_year,created_at,last_seen FROM users WHERE id=?').get(req.user.sub);if(!user)return res.status(404).json({error:'user_not_found'});markSeen(user.id);res.json({user:publicUser(user)});});

app.get('/api/contacts',auth,(req,res)=>{const rows=db.prepare(`SELECT u.id,u.wethaq_id,u.name,u.birth_year,u.last_seen FROM contacts c JOIN users u ON u.id=c.contact_id WHERE c.user_id=? ORDER BY u.name COLLATE NOCASE`).all(req.user.sub);res.json({contacts:rows.map(publicUser)});});

app.post('/api/contacts',auth,(req,res)=>{const wethaqId=String(req.body?.wethaqId||'').trim();const contact=db.prepare('SELECT id,wethaq_id,name,birth_year,last_seen,created_at FROM users WHERE wethaq_id=?').get(wethaqId);if(!contact)return res.status(404).json({error:'user_not_found'});if(contact.id===req.user.sub)return res.status(400).json({error:'cannot_add_self'});db.prepare('INSERT OR IGNORE INTO contacts(user_id,contact_id) VALUES (?,?)').run(req.user.sub,contact.id);res.status(201).json({contact:publicUser(contact)});});

app.get('/api/messages/:wethaqId',auth,(req,res)=>{const other=db.prepare('SELECT id,wethaq_id,name,birth_year,last_seen,created_at FROM users WHERE wethaq_id=?').get(req.params.wethaqId);if(!other)return res.status(404).json({error:'user_not_found'});const messages=db.prepare(`SELECT id,sender_id,receiver_id,body,status,created_at FROM messages WHERE (sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?) ORDER BY id ASC LIMIT 500`).all(req.user.sub,other.id,other.id,req.user.sub);res.json({contact:publicUser(other),messages});});

app.post('/api/messages',auth,(req,res)=>{const to=String(req.body?.to||'').trim();const body=String(req.body?.body||'').trim();if(!to||!body||body.length>MAX_MESSAGE||/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(body))return res.status(400).json({error:'invalid_message'});const receiver=db.prepare('SELECT id,wethaq_id,name FROM users WHERE wethaq_id=?').get(to);if(!receiver)return res.status(404).json({error:'user_not_found'});const delivered=isOnline(receiver.id)?'delivered':'sent';const result=db.prepare('INSERT INTO messages(sender_id,receiver_id,body,status) VALUES (?,?,?,?)').run(req.user.sub,receiver.id,body,delivered);const message=db.prepare('SELECT id,sender_id,receiver_id,body,status,created_at FROM messages WHERE id=?').get(result.lastInsertRowid);broadcast(receiver.id,{type:'message',message});res.status(201).json({message});});

wss.on('connection',(socket,request)=>{try{const url=new URL(request.url,'http://localhost');const token=url.searchParams.get('token')||'';const decoded=jwt.verify(token,JWT_SECRET);const userId=Number(decoded.sub);if(!online.has(userId))online.set(userId,new Set());online.get(userId).add(socket);markSeen(userId);socket.send(JSON.stringify({type:'connected',online:true}));const timer=setInterval(()=>{if(socket.readyState===1)socket.ping();},25000);socket.on('close',()=>{clearInterval(timer);const set=online.get(userId);if(set){set.delete(socket);if(!set.size){online.delete(userId);markSeen(userId);}}});socket.on('error',()=>socket.close());}catch{socket.close(1008,'unauthorized');}});

server.listen(PORT,'0.0.0.0',()=>console.log(`Wethaq backend listening on ${PORT}`));
