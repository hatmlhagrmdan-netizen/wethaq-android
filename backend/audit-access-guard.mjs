import crypto from 'node:crypto';
import jwt from 'jsonwebtoken';
import Database from 'better-sqlite3';
import express from 'express';

// Production guard: /api/admin/audit-log is founder-only at runtime.
// This module is preloaded before server.js so the protection is applied
// without mutating source files at runtime.
const db = new Database(process.env.DB_PATH || 'wethaq.db');
const JWT_SECRET = String(process.env.JWT_SECRET || '').trim();

function activeRole(userId) {
  try {
    const row = db.prepare(`SELECT role, code_version FROM admin_roles WHERE user_id=? AND active=1`).get(Number(userId));
    if (!row) return null;
    return row;
  } catch {
    return null;
  }
}

function founderOnly(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : '';
  if (!token || JWT_SECRET.length < 32) return res.status(401).json({ error: 'admin_unauthorized' });
  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    if (decoded.admin !== true) return res.status(403).json({ error: 'admin_token_required' });
    const role = activeRole(decoded.sub);
    if (!role || role.role !== decoded.role) return res.status(403).json({ error: 'admin_role_inactive' });
    if (Number(role.code_version || 1) !== Number(decoded.codeVersion || 1)) return res.status(403).json({ error: 'admin_session_revoked' });
    if (role.role !== 'founder') return res.status(403).json({ error: 'founder_only' });
    next();
  } catch {
    res.status(401).json({ error: 'invalid_admin_token' });
  }
}

const originalGet = express.application.get;
express.application.get = function patchedGet(path, ...handlers) {
  if (path === '/api/admin/audit-log') {
    return originalGet.call(this, path, founderOnly, ...handlers);
  }
  return originalGet.call(this, path, ...handlers);
};

process.on('exit', () => {
  try { db.close(); } catch {}
});
