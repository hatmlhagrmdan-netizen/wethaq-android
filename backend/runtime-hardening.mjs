import fs from 'node:fs';

// Wethaq production guard: validation-only. Runtime source mutation is prohibited.
const path = 'backend/server.js';
const s = fs.readFileSync(path, 'utf8');

const required = [
  ['admin RBAC definitions', 'const ADMIN_RANK='],
  ['admin authentication', 'function adminAuth'],
  ['active admin role lookup', 'const activeAdminRole='],
  ['admin role storage', 'CREATE TABLE IF NOT EXISTS admin_roles'],
  ['admin audit storage', 'CREATE TABLE IF NOT EXISTS admin_audit_log'],
  ['health endpoint', "app.get('/health'"]
];

for (const [label, marker] of required) {
  if (!s.includes(marker)) {
    throw new Error(`production validation failed: missing ${label}`);
  }
}

// The production RBAC is now committed source code. Do not reject historical marker text;
// validate the actual implementation shape instead of a comment/marker name.
const forbiddenRuntimeMutation = /(?:writeFileSync|appendFileSync|renameSync|rmSync)\([^\n]*(?:server\.js|backend\/server\.js)/;
if (forbiddenRuntimeMutation.test(s)) {
  throw new Error('production validation failed: runtime source mutation detected');
}

console.log(`Wethaq production validation passed: server.js ${Buffer.byteLength(s, 'utf8')} bytes`);