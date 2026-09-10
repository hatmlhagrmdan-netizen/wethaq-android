import fs from 'node:fs';

// Wethaq production guard: this file is intentionally validation-only.
// Runtime source mutation is unsafe because it can create ordering/TDZ failures
// and makes production behavior differ from the committed source tree.
const path = 'backend/server.js';
const s = fs.readFileSync(path, 'utf8');

const required = [
  ['admin access layer', 'WETHAQ_ADMIN_ACCESS_LAYER_V1'],
  ['admin authentication', 'function adminAuth'],
  ['health endpoint', "app.get('/health'"]
];

for (const [label, marker] of required) {
  if (!s.includes(marker)) {
    throw new Error(`production validation failed: missing ${label}`);
  }
}

console.log(`Wethaq production validation passed: server.js ${Buffer.byteLength(s, 'utf8')} bytes`);
