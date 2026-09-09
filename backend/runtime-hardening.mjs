import fs from 'node:fs';

const path='backend/server.js';
let s=fs.readFileSync(path,'utf8');
const original=s;

function replaceOnce(a,b,label){
  if(!s.includes(a)) throw new Error(`hardening pattern missing: ${label}`);
  s=s.replace(a,b);
}

replaceOnce("app.get('/api/admin/users',auth,admin,", "app.get('/api/admin/users',adminAuth,", 'admin users middleware');
replaceOnce("app.get('/api/admin/policy',(_req,res)=>", "app.get('/api/admin/policy',adminAuth,(_req,res)=>", 'admin policy protection');
replaceOnce("app.get('/api/admin/structure',(_req,res)=>", "app.get('/api/admin/structure',adminAuth,(_req,res)=>", 'admin structure protection');
replaceOnce("app.get('/api/admin/audit-log',adminAuth,(req,res)=>{const rows=", "app.get('/api/admin/audit-log',adminAuth,(req,res)=>{if(req.adminRow.role!=='founder')return res.status(403).json({error:'founder_only'});const rows=", 'audit founder-only policy');

const oldAssign="const targetId=String(req.body?.wethaqId||'').trim(),targetRole=String(req.body?.role||'').trim();if(!ADMIN_POLICY[targetRole]||!targetId)return res.status(400).json({error:'invalid_assignment'});if(!canAssign(req.adminRow.role,targetRole))return res.status(403).json({error:'assignment_not_allowed'});const target=db.prepare('SELECT id,name,wethaq_id,birth_year FROM users WHERE wethaq_id=?').get(targetId);if(!target)return res.status(404).json({error:'user_not_found'});";
const newAssign="const targetId=String(req.body?.wethaqId||'').trim(),targetRole=String(req.body?.role||'').trim(),claimedName=safeName(req.body?.name),claimedBirthYear=Number(req.body?.birthYear),claimedPersonalCode=String(req.body?.personalCode||'').trim();if(!ADMIN_POLICY[targetRole]||!targetId)return res.status(400).json({error:'invalid_assignment'});if(!canAssign(req.adminRow.role,targetRole))return res.status(403).json({error:'assignment_not_allowed'});if(!validText(claimedName,MAX_NAME)||claimedName.split(/\\s+/).length<3||!Number.isInteger(claimedBirthYear)||!validPersonalCode(claimedPersonalCode))return res.status(400).json({error:'identity_verification_required'});const target=db.prepare('SELECT id,name,wethaq_id,birth_year,personal_code_hash FROM users WHERE wethaq_id=?').get(targetId);if(!target)return res.status(404).json({error:'user_not_found'});if(target.name!==claimedName||Number(target.birth_year)!==claimedBirthYear||!verifyPersonalCode(claimedPersonalCode,target.personal_code_hash))return res.status(401).json({error:'identity_verification_failed'});";
replaceOnce(oldAssign,newAssign,'server-side assignment identity verification');

replaceOnce("app.post('/api/calls/signal',auth,(req,res)=>{", "app.post('/api/calls/signal',auth,(req,res)=>{if(!rateLimit(`call-signal:${req.user.sub}`,60,60*1000))return res.status(429).json({error:'call_signal_rate_limited'});", 'call signaling rate limit');

if(s===original) throw new Error('hardening made no changes');
fs.writeFileSync(path,s);
console.log(`Wethaq runtime hardening applied: ${original.length} -> ${s.length} bytes`);
