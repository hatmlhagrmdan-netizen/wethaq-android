import fs from 'node:fs';

// Wethaq production guard: validation-only. Runtime source mutation is prohibited.
const path = 'backend/server.js';
const s = fs.readFileSync(path, 'utf8');

const required = [
  ['admin RBAC definitions', 'const ADMIN_RANK='],
  ['admin authentication', 'function adminAuth'],
  ['active admin role lookup', 'const activeAdminRole='],
  ['admin role storage', 'CREATE TABLE IF NOT EXISTS admin_roles'],
  ['health endpoint', "app.get('/health'"]
];

for (const [label, marker] of required) {
  if (!s.includes(marker)) {
    throw new Error(`production validation failed: missing ${label}`);
  }
}

if (s.includes('WETHAQ_ADMIN_ACCESS_LAYER_V1')) {
  throw new Error('production validation failed: legacy runtime-injected admin layer is still present');
}

console.log(`Wethaq production validation passed: server.js ${Buffer.byteLength(s, 'utf8')} bytes`);