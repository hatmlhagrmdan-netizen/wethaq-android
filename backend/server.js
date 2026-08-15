import express from 'express';
import cors from 'cors';
import http from 'http';
import crypto from 'crypto';
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
const MAX_NAME = 80;

app.disable('x-powered-by');
app.set('trust proxy', 1);
app.use(cors({ origin: (_origin, cb) => cb(null, true), credentials: false }));
app.use(express.json({ limit: '256kb' }));

const online = new Map();
const attempts = new Map();
const now = () => new Date().toISOString();
const validText = (v, max) => typeof v === 'string' && v.trim().length > 0 && v.trim().length <= max;
function rateLimit(key, limit, windowMs) {
  const t = Date.now(); const old = attempts.get(key) || { count: 0, at: t };
  if (t - old.at > windowMs) { attempts.set(key, { count: 1, at: t }); return true; }
  old.count += 1; attempts.set(key, old); return old.count <= limit;
}
function ensureColumn(table, column, definition) {
  const cols = db.prepare(`PRAGMA table_info(${table})`).all().map(x => x.name);
  if (!cols.includes(column)) db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
}

db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');
db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  wethaq_id TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  birth_year INTEGER NOT NULL DEFAULT 0,
  password_hash TEXT,
  device_key TEXT,
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
CREATE INDEX IF NOT EXISTS idx_users_name ON users(name COLLATE NOCASE);
`);
ensureColumn('users', 'birth_year', 'INTEGER NOT NULL DEFAULT 0');
ensureColumn('users', 'last_seen', 'TEXT');
ensureColumn('users', 'device_key', 'TEXT');

const AR = {
  'حاتم':'Hatem','حسين':'Hussin','الحاج':'Al_Haj','رمضان':'Ramadan','عبد':'Abd','الله':'Allah',
  'محمد':'Mohammad','أحمد':'Ahmad','علي':'Ali','خالد':'Khaled','سارة':'Sara','سما':'Sama',
  'نور':'Nour','هدى':'Huda','أمان':'Aman','وسام':'Wisam','يامن':'Yamen','هشام':'Hisham','أيمن':'Ayman','حسام':'Hossam'
};
const MAP = {'ا':'a','أ':'a','إ':'i','آ':'a','ب':'b','ت':'t','ث':'th','ج':'j','ح':'h','خ':'kh','د':'d','ذ':'dh','ر':'r','ز':'z','س':'s','ش':'sh','ص':'s','ض':'d','ط':'t','ظ':'z','ع':'a','غ':'gh','ف':'f','ق':'q','ك':'k','ل':'l','م':'m','ن':'n','ه':'h','و':'w','ي':'y','ى':'a','ة':'h'};
function transliterateWord(word) { if (AR[word]) return AR[word]; let out=''; for (const ch of word) out += MAP[ch] ?? (/[A-Za-z0-9]/.test(ch) ? ch : ''); return out ? out[0].toUpperCase()+out.slice(1) : ''; }
function makeBaseId(name, birthYear) { const parts=name.replace(/ـ/g,'').trim().split(/[\s،,]+/).filter(Boolean).map(transliterateWord).filter(Boolean); return `${parts.join('_')}${birthYear}`.replace(/[^A-Za-z0-9_]/g,'_'); }
function makeWethaqId(name, birthYear) { const base=makeBaseId(name,birthYear)||`Wethaq_User${birthYear}`; let id=base,n=2; while(db.prepare('SELECT 1 FROM users WHERE wethaq_id=?').get(id)) id=`${base}_${n++}`; return id; }
function publicUser(u) { return { id:u.id, wethaq_id:u.wethaq_id, name:u.name, birth_year:u.birth_year||0, created_at:u.created_at, last_seen:u.last_seen||null, online:isOnline(u.id) }; }
function tokenFor(u) { return jwt.sign({ sub:u.id, wethaqId:u.wethaq_id }, JWT_SECRET, { expiresIn:'365d' }); }
function auth(req,res,next) { const h=req.headers.authorization||''; const token=h.startsWith('Bearer ')?h.slice(7):''; if(!token)return res.status(401).json({error:'unauthorized'}); try{req.user=jwt.verify(token,JWT_SECRET);next();}catch{return res.status(401).json({error:'invalid_token'});} }
function isOnline(userId) { const s=online.get(Number(userId)); return !!s && [...s].some(x=>x.readyState===1); }
function broadcast(userId,payload) { const s=online.get(Number(userId)); if(!s)return 0; const raw=JSON.stringify(payload); let n=0; for(const ws of s)if(ws.readyState===1){ws.send(raw);n++;} return n; }
function markSeen(id){db.prepare('UPDATE users SET last_seen=? WHERE id=?').run(now(),id);}
function safeName(name){return name.replace(/[<>\u0000-\u001f]/g,'').trim().replace(/\s+/g,' ');}

app.get('/health',(_req,res)=>res.json({ok:true,service:'wethaq',version:'2.0.0',auth:'name_birth_device_key',time:now()}));

// إنشاء هوية أو استعادتها على الجهاز نفسه. لا توجد كلمة مرور.
app.post('/api/identity', (req,res)=>{
  if(!rateLimit(`identity:${req.ip}`,20,15*60*1000))return res.status(429).json({error:'rate_limited'});
  const name=safeName(String(req.body?.name||'')); const birthYear=Number(req.body?.birthYear); const deviceKey=String(req.body?.deviceKey||'').trim();
  const current=new Date().getUTCFullYear();
  if(!validText(name,MAX_NAME)||name.split(/\s+/).length<2||!Number.isInteger(birthYear)||birthYear<1900||birthYear>current||deviceKey.length<24||deviceKey.length>256)return res.status(400).json({error:'invalid_identity'});
  const base=makeBaseId(name,birthYear);
  let user=db.prepare('SELECT * FROM users WHERE wethaq_id=?').get(base);
  if(user){
    if(user.device_key && user.device_key!==deviceKey)return res.status(409).json({error:'identity_claimed'});
    if(!user.device_key)db.prepare('UPDATE users SET device_key=?,name=?,birth_year=?,last_seen=? WHERE id=?').run(deviceKey,name,birthYear,now(),user.id);
    else db.prepare('UPDATE users SET last_seen=? WHERE id=?').run(now(),user.id);
    user=db.prepare('SELECT * FROM users WHERE id=?').get(user.id);
  } else {
    const wethaqId=makeWethaqId(name,birthYear);
    const result=db.prepare('INSERT INTO users (wethaq_id,name,birth_year,password_hash,device_key,last_seen) VALUES (?,?,?,?,?,?)').run(wethaqId,name,birthYear,null,deviceKey,now());
    user=db.prepare('SELECT * FROM users WHERE id=?').get(result.lastInsertRowid);
  }
  res.status(200).json({user:publicUser(user),token:tokenFor(user)});
});

app.get('/api/me',auth,(req,res)=>{const u=db.prepare('SELECT * FROM users WHERE id=?').get(req.user.sub);if(!u)return res.status(404).json({error:'user_not_found'});markSeen(u.id);res.json({user:publicUser(u)});});
app.get('/api/search',auth,(req,res)=>{const q=String(req.query.q||'').trim();if(q.length<2)return res.json({users:[]});const rows=db.prepare(`SELECT id,wethaq_id,name,birth_year,last_seen,created_at FROM users WHERE (wethaq_id LIKE ? OR name LIKE ?) AND id<>? ORDER BY name COLLATE NOCASE LIMIT 30`).all(`%${q}%`,`%${q}%`,req.user.sub);res.json({users:rows.map(publicUser)});});
app.get('/api/contacts',auth,(req,res)=>{const rows=db.prepare(`SELECT u.id,u.wethaq_id,u.name,u.birth_year,u.last_seen,u.created_at FROM contacts c JOIN users u ON u.id=c.contact_id WHERE c.user_id=? ORDER BY u.name COLLATE NOCASE`).all(req.user.sub);res.json({contacts:rows.map(publicUser)});});
app.post('/api/contacts',auth,(req,res)=>{const id=String(req.body?.wethaqId||'').trim();const c=db.prepare('SELECT id,wethaq_id,name,birth_year,last_seen,created_at FROM users WHERE wethaq_id=?').get(id);if(!c)return res.status(404).json({error:'user_not_found'});if(c.id===req.user.sub)return res.status(400).json({error:'cannot_add_self'});db.prepare('INSERT OR IGNORE INTO contacts(user_id,contact_id) VALUES(?,?)').run(req.user.sub,c.id);res.status(201).json({contact:publicUser(c)});});
app.get('/api/messages/:wethaqId',auth,(req,res)=>{const other=db.prepare('SELECT id,wethaq_id,name,birth_year,last_seen,created_at FROM users WHERE wethaq_id=?').get(req.params.wethaqId);if(!other)return res.status(404).json({error:'user_not_found'});const rows=db.prepare(`SELECT id,sender_id,receiver_id,body,status,created_at FROM messages WHERE (sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?) ORDER BY id ASC LIMIT 500`).all(req.user.sub,other.id,other.id,req.user.sub);res.json({contact:publicUser(other),messages:rows});});
app.post('/api/messages',auth,(req,res)=>{const to=String(req.body?.to||'').trim();const body=String(req.body?.body||'').trim();if(!to||!body||body.length>MAX_MESSAGE||/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(body))return res.status(400).json({error:'invalid_message'});const receiver=db.prepare('SELECT id,wethaq_id,name FROM users WHERE wethaq_id=?').get(to);if(!receiver)return res.status(404).json({error:'user_not_found'});const status=isOnline(receiver.id)?'delivered':'sent';const r=db.prepare('INSERT INTO messages(sender_id,receiver_id,body,status) VALUES(?,?,?,?)').run(req.user.sub,receiver.id,body,status);const message=db.prepare('SELECT id,sender_id,receiver_id,body,status,created_at FROM messages WHERE id=?').get(r.lastInsertRowid);broadcast(receiver.id,{type:'message',message});res.status(201).json({message});});

wss.on('connection',(socket,request)=>{try{const u=new URL(request.url,'http://localhost');const token=u.searchParams.get('token')||'';const decoded=jwt.verify(token,JWT_SECRET);const userId=Number(decoded.sub);if(!online.has(userId))online.set(userId,new Set());online.get(userId).add(socket);markSeen(userId);socket.send(JSON.stringify({type:'connected',online:true}));const timer=setInterval(()=>{if(socket.readyState===1)socket.ping();},25000);socket.on('close',()=>{clearInterval(timer);const set=online.get(userId);if(set){set.delete(socket);if(!set.size){online.delete(userId);markSeen(userId);}}});socket.on('error',()=>socket.close());}catch{socket.close(1008,'unauthorized');}});
server.listen(PORT,'0.0.0.0',()=>console.log(`Wethaq backend listening on ${PORT}`));
