import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Production validation only. This gate must validate the actual server.js contract
// instead of historical implementation names from retired hardening layers.
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const serverPath = path.join(__dirname, 'server.js');
const s = fs.readFileSync(serverPath, 'utf8');

const required = [
  ['JWT secret configuration', /process\.env\.JWT_SECRET/],
  ['owner identity configuration', /OWNER_WETHAQ_ID/],
  ['user authentication middleware', /function auth\s*\(/],
  ['JWT verification', /jwt\.verify\s*\(/],
  ['SQLite foreign-key enforcement', /foreign_keys=ON/],
  ['SQLite WAL mode', /journal_mode=WAL/],
  ['request size limit', /express\.json\(\{limit:'12mb'\}\)/],
  ['rate limiting', /function rateLimit\s*\(/],
  ['health endpoint', /app\.get\('\/health'/]
];

for (const [label, marker] of required) {
  const matched = marker instanceof RegExp ? marker.test(s) : s.includes(marker);
  if (!matched) {
    throw new Error(`production validation failed: missing ${label}`);
  }
}

// Runtime source mutation is prohibited.
const forbiddenRuntimeMutation = /(?:writeFileSync|appendFileSync|renameSync|rmSync)\([^\n]*(?:server\.js|backend\/server\.js)/;
if (forbiddenRuntimeMutation.test(s)) {
  throw new Error('production validation failed: runtime source mutation detected');
}

console.log(`Wethaq production validation passed: server.js ${Buffer.byteLength(s, 'utf8')} bytes`);
