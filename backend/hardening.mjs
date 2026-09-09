import crypto from 'crypto';
import jwt from 'jsonwebtoken';
import Database from 'better-sqlite3';
import express from 'express';

const db = new Database(process.env.DB_PATH || 'wethaq.db');
const JWT_SECRET = String(process.env.JWT_SECRET || '').trim();
const OWNER_WETHAQ_ID = process.env.OWNER_WETHAQ_ID || 'Hatem_Hussin_Al_Haj_Ramadan1995';
const STRUCTURE_ROLES = new Set(['founder', 'executive', 'deputy1', 'deputy2', 'deputy3', 'supervisor']);
const CAPABILITIES = {
  founder: {manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:true},
  executive: {manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
  deputy1: {manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
  deputy2: {manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
  deputy3: {manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
  supervisor: {manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
  admin_member: {manageUsers:true,banPermanent:false,banTemporary:true,unban:true,warn:true,assign:false,removeRole:false,viewStructure:false,viewAudit:false},
  premium: {manageUsers:true,banPermanent:false,banTemporary:false,unban:false,warn:true,assign:false,removeRole:false,viewStructure:false,viewAudit:false}
};

function safeName(value) {
  return String(value || '').replace(/[<>\u0000-\u001f]/g, '').trim().replace(/\s+/g, ' ');
}
function verifyPersonalCode(code, stored) {
  try {
    const [saltHex, hashHex] = String(stored || '').split(':');
    if (!saltHex || !hashHex) return false;
    const salt = Buffer.from(saltHex, 'hex');
    const expected = Buffer.from(hashHex, 'hex');
    const actual = crypto.scryptSync(String(code || '').trim(), salt, 32);
    return expected.length === actual.length && crypto.timingSafeEqual(expected, actual);
  } catch { return false; }
}
function roleRow(userId) {
  return db.prepare(`SELECT ar.*,u.name,u.wethaq_id,u.birth_year FROM admin_roles ar JOIN users u ON u.id=ar.user_id WHERE ar.user_id=? AND ar.active=1`).get(Number(userId));
}
function activeRole(userId) {
  const row = roleRow(userId);
  if (!row) return null;
  if (row.role !== 'founder' && row.subscription_expires_at && new Date(row.subscription_expires_at) <= new Date()) {
    db.prepare('UPDATE admin_roles SET active=0 WHERE user_id=?').run(Number(userId));
    return null;
  }
  return row;
}
function requireAdmin(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : '';
  if (!token || JWT_SECRET.length < 32) return res.status(401).json({ error: 'admin_unauthorized' });
  try {
    const claims = jwt.verify(token, JWT_SECRET);
    if (claims.admin !== true) return res.status(403).json({ error: 'admin_token_required' });
    const row = activeRole(Number(claims.sub));
    if (!row || row.role !== claims.role) return res.status(403).json({ error: 'admin_role_inactive' });
    if (Number(row.code_version || 1) !== Number(claims.codeVersion || 1)) return res.status(403).json({ error: 'admin_session_revoked' });
    req.admin = claims;
    req.adminRow = row;
    next();
  } catch { return res.status(401).json({ error: 'invalid_admin_token' }); }
}
function assignmentIdentityGuard(req, res, next) {
  const targetId = String(req.body?.wethaqId || '').trim();
  const name = safeName(req.body?.name);
  const birthYear = Number(req.body?.birthYear);
  const personalCode = String(req.body?.personalCode || '').trim();
  if (!targetId || !name || !Number.isInteger(birthYear) || !/^\d{6,12}$/.test(personalCode)) {
    return res.status(400).json({ error: 'assignment_identity_required' });
  }
  const target = db.prepare('SELECT id,name,birth_year,wethaq_id,personal_code_hash FROM users WHERE wethaq_id=?').get(targetId);
  if (!target) return res.status(404).json({ error: 'user_not_found' });
  if (safeName(target.name) !== name || Number(target.birth_year) !== birthYear) return res.status(403).json({ error: 'assignment_identity_mismatch' });
  if (!verifyPersonalCode(personalCode, target.personal_code_hash)) return res.status(403).json({ error: 'assignment_personal_code_invalid' });
  delete req.body.personalCode;
  next();
}
function nonFounderTargetGuard(req, res, next) {
  const targetId = String(req.body?.wethaqId || '').trim();
  const target = db.prepare('SELECT wethaq_id FROM users WHERE wethaq_id=?').get(targetId);
  if (target?.wethaq_id === OWNER_WETHAQ_ID) return res.status(403).json({ error: 'founder_protected' });
  next();
}
function capabilityGuard(name) {
  return (req,res,next)=>{
    const caps=CAPABILITIES[req.adminRow?.role]||{};
    if (!caps[name]) return res.status(403).json({error:`capability_${name}_denied`});
    next();
  };
}
function rolePayload(role) {
  return CAPABILITIES[role] || {};
}

const originalGet = express.application.get;
const originalPost = express.application.post;
express.application.get = function(path, ...handlers) {
  if (path === '/api/admin/policy') return originalGet.call(this, path, requireAdmin, ...handlers);
  if (path === '/api/admin/structure') return originalGet.call(this, path, requireAdmin, (req, res, next) => {
    if (!STRUCTURE_ROLES.has(req.adminRow.role)) return res.status(403).json({ error: 'structure_access_denied' });
    next();
  }, ...handlers);
  if (path === '/api/admin/audit-log') return originalGet.call(this, path, requireAdmin, (req, res, next) => {
    if (req.adminRow.role !== 'founder') return res.status(403).json({ error: 'founder_only' });
    next();
  }, ...handlers);
  if (path === '/api/admin/users') {
    const first = handlers[0];
    const last = handlers[handlers.length - 1];
    return originalGet.call(this, path, first, requireAdmin, last);
  }
  if (path === '/api/calls/signals/:wethaqId') return originalGet.call(this, path, ...handlers.slice(0, Math.max(0, handlers.length-1)), (req,res,next)=>{
    try { db.prepare("DELETE FROM call_signals WHERE created_at < datetime('now','-10 minutes')").run(); } catch {}
    next();
  }, ...handlers.slice(-1));
  if (path === '/api/admin/role') return originalGet.call(this, path, ...handlers.slice(0, Math.max(0, handlers.length-1)), (req,res,next)=>{
    const oldJson=res.json.bind(res);
    res.json=(body)=>{ if(body&&body.role) body.capabilities=rolePayload(body.role); return oldJson(body); };
    next();
  }, ...handlers.slice(-1));
  return originalGet.call(this, path, ...handlers);
};
express.application.post = function(path, ...handlers) {
  if (path === '/api/admin/assign') return originalPost.call(this, path, ...handlers.slice(0, 1), assignmentIdentityGuard, ...handlers.slice(1));
  if (path === '/api/admin/remove-role') return originalPost.call(this, path, ...handlers.slice(0, 1), nonFounderTargetGuard, capabilityGuard('removeRole'), ...handlers.slice(1));
  if (path === '/api/admin/rbac/ban') return originalPost.call(this, path, ...handlers.slice(0, 1), capabilityGuard('manageUsers'), (req,res,next)=>{
    const minutes=Math.max(0,Number(req.body?.minutes||0));
    const caps=CAPABILITIES[req.adminRow?.role]||{};
    if(minutes>0&&!caps.banTemporary)return res.status(403).json({error:'temporary_ban_denied'});
    if(minutes===0&&!caps.banPermanent)return res.status(403).json({error:'permanent_ban_denied'});
    next();
  }, ...handlers.slice(1));
  if (path === '/api/admin/unban') return originalPost.call(this, path, ...handlers.slice(0, 1), capabilityGuard('unban'), nonFounderTargetGuard, ...handlers.slice(1));
  if (path === '/api/admin/alert') return originalPost.call(this, path, ...handlers.slice(0, 1), capabilityGuard('warn'), ...handlers.slice(1));
  return originalPost.call(this, path, ...handlers);
};

process.on('exit', () => { try { db.close(); } catch {} });
