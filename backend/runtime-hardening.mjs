import fs from 'node:fs';

const path='backend/server.js';
let s=fs.readFileSync(path,'utf8');
const original=s;

function replaceOnce(a,b,label){
  if(!s.includes(a)) throw new Error(`hardening pattern missing: ${label}`);
  s=s.replace(a,b);
}
function insertOnce(anchor,insert,label){
  if(!s.includes(anchor)) throw new Error(`hardening anchor missing: ${label}`);
  s=s.replace(anchor,insert+anchor);
}

// Keep the production runtime authoritative: the service executes this patcher immediately before server.js.
replaceOnce("app.get('/api/admin/policy',(_req,res)=>", "app.get('/api/admin/policy',adminAuth,(_req,res)=>", 'admin policy protection');
replaceOnce("app.get('/api/admin/structure',(_req,res)=>", "app.get('/api/admin/structure',adminAuth,(req,res,next)=>{if(!['founder','executive','deputy1','deputy2','deputy3','supervisor'].includes(req.adminRow.role))return res.status(403).json({error:'structure_access_denied'});next();},(_req,res)=>", 'admin structure protection');
replaceOnce("app.get('/api/admin/audit-log',adminAuth,(req,res)=>{const rows=", "app.get('/api/admin/audit-log',adminAuth,(req,res)=>{if(req.adminRow.role!=='founder')return res.status(403).json({error:'founder_only'});const rows=", 'audit founder-only policy');
replaceOnce("app.get('/api/admin/users',auth,admin,(req,res)=>", "app.get('/api/admin/users',adminAuth,(req,res)=>", 'admin user search authorization');
replaceOnce("const adminTokenFor=r=>jwt.sign({sub:r.user_id,wethaqId:r.wethaq_id,admin:true,role:r.role},JWT_SECRET,{expiresIn:'30d'});", "const adminTokenFor=r=>jwt.sign({sub:r.user_id,wethaqId:r.wethaq_id,admin:true,role:r.role,codeVersion:Number(r.code_version||1)},JWT_SECRET,{expiresIn:'30d'});", 'admin token code version');
replaceOnce("if(!r||r.role!==d.role)return res.status(403).json({error:'admin_role_inactive'});req.admin=d;req.adminRow=r;next()", "if(!r||r.role!==d.role)return res.status(403).json({error:'admin_role_inactive'});if(Number(r.code_version||1)!==Number(d.codeVersion||1))return res.status(403).json({error:'admin_session_revoked'});req.admin=d;req.adminRow=r;next()", 'admin session revocation');
replaceOnce("if(r.role!=='founder'&&r.subscription_expires_at&&new Date(r.subscription_expires_at)<=new Date()){db.prepare('UPDATE admin_roles SET active=0 WHERE user_id=?').run(r.user_id);return res.status(403).json({error:'admin_subscription_expired'})}", "if(r.role!=='founder'&&r.subscription_expires_at&&new Date(r.subscription_expires_at)<=new Date()){db.prepare('UPDATE admin_roles SET active=0 WHERE user_id=?').run(r.user_id);closePositionHistory(r.user_id,r.role,now());return res.status(403).json({error:'admin_subscription_expired'})}", 'login expiry history close');

insertOnce("// WETHAQ_ADMIN_ACCESS_LAYER_V1\n", `const CAPABILITIES={
 founder:{manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:true},
 executive:{manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
 deputy1:{manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
 deputy2:{manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
 deputy3:{manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
 supervisor:{manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
 admin_member:{manageUsers:true,banPermanent:false,banTemporary:true,unban:true,warn:true,assign:false,removeRole:false,viewStructure:false,viewAudit:false},
 premium:{manageUsers:true,banPermanent:false,banTemporary:false,unban:false,warn:true,assign:false,removeRole:false,viewStructure:false,viewAudit:false}
};
`, 'capability matrix');

replaceOnce("app.get('/api/admin/role',adminAuth,(req,res)=>{const p=ADMIN_POLICY[req.adminRow.role];res.json({role:req.adminRow.role,label:p.label,icon:p.icon,fee:p.fee,subscriptionExpiresAt:req.adminRow.subscription_expires_at||'',permissions:p.permissions,appoints:p.appoints,capacity:p.capacity});});", "app.get('/api/admin/role',adminAuth,(req,res)=>{const p=ADMIN_POLICY[req.adminRow.role];res.json({role:req.adminRow.role,label:p.label,icon:p.icon,fee:p.fee,subscriptionExpiresAt:req.adminRow.subscription_expires_at||'',permissions:p.permissions,appoints:p.appoints,capacity:p.capacity,capabilities:CAPABILITIES[req.adminRow.role]||{}});});", 'role capability payload');

const oldAssign="const targetId=String(req.body?.wethaqId||'').trim(),targetRole=String(req.body?.role||'').trim();if(!ADMIN_POLICY[targetRole]||!targetId)return res.status(400).json({error:'invalid_assignment'});if(!canAssign(req.adminRow.role,targetRole))return res.status(403).json({error:'assignment_not_allowed'});const target=db.prepare('SELECT id,name,wethaq_id,birth_year FROM users WHERE wethaq_id=?').get(targetId);if(!target)return res.status(404).json({error:'user_not_found'});";
const newAssign="const targetId=String(req.body?.wethaqId||'').trim(),targetRole=String(req.body?.role||'').trim(),claimedName=safeName(req.body?.name),claimedBirthYear=Number(req.body?.birthYear),claimedPersonalCode=String(req.body?.personalCode||'').trim();if(!ADMIN_POLICY[targetRole]||!targetId)return res.status(400).json({error:'invalid_assignment'});if(!canAssign(req.adminRow.role,targetRole))return res.status(403).json({error:'assignment_not_allowed'});if(!validText(claimedName,MAX_NAME)||claimedName.split(/\\s+/).length<3||!Number.isInteger(claimedBirthYear)||!validPersonalCode(claimedPersonalCode))return res.status(400).json({error:'identity_verification_required'});const target=db.prepare('SELECT id,name,wethaq_id,birth_year,personal_code_hash FROM users WHERE wethaq_id=?').get(targetId);if(!target)return res.status(404).json({error:'user_not_found'});if(target.name!==claimedName||Number(target.birth_year)!==claimedBirthYear||!verifyPersonalCode(claimedPersonalCode,target.personal_code_hash))return res.status(401).json({error:'identity_verification_failed'});";
replaceOnce(oldAssign,newAssign,'server-side assignment identity verification');

replaceOnce("app.post('/api/calls/signal',auth,(req,res)=>{", "app.post('/api/calls/signal',auth,(req,res)=>{if(!rateLimit(`call-signal:${req.user.sub}`,60,60*1000))return res.status(429).json({error:'call_signal_rate_limited'});", 'call signaling rate limit');
replaceOnce("app.get('/api/calls/signals/:wethaqId',auth,(req,res)=>{", "app.get('/api/calls/signals/:wethaqId',auth,(req,res)=>{try{db.prepare(\"DELETE FROM call_signals WHERE created_at < datetime('now','-10 minutes')\").run();}catch{}", 'stale call signal cleanup');

const adminGuard="const __CAP_GUARD=(name)=>(req,res,next)=>{const c=CAPABILITIES[req.adminRow?.role]||{};if(!c[name])return res.status(403).json({error:`capability_${name}_denied`});next();};\n";
insertOnce("const ADMIN_CODE_LIMIT=64;",adminGuard,'capability guard');
replaceOnce("app.post('/api/admin/rbac/ban',adminAuth,(req,res)=>{", "app.post('/api/admin/rbac/ban',adminAuth,__CAP_GUARD('manageUsers'),(req,res,next)=>{const mins=Math.max(0,Number(req.body?.minutes||0));const c=CAPABILITIES[req.adminRow.role]||{};if(mins>0&&!c.banTemporary)return res.status(403).json({error:'temporary_ban_denied'});if(mins===0&&!c.banPermanent)return res.status(403).json({error:'permanent_ban_denied'});next();},(req,res)=>{", 'ban capabilities');
replaceOnce("app.post('/api/admin/unban',adminAuth,(req,res)=>{", "app.post('/api/admin/unban',adminAuth,__CAP_GUARD('unban'),(req,res)=>{", 'unban capabilities');
replaceOnce("app.post('/api/admin/alert',adminAuth,(req,res)=>{", "app.post('/api/admin/alert',adminAuth,__CAP_GUARD('warn'),(req,res)=>{", 'alert capabilities');
replaceOnce("app.post('/api/admin/remove-role',adminAuth,(req,res)=>{", "app.post('/api/admin/remove-role',adminAuth,__CAP_GUARD('removeRole'),(req,res)=>{", 'remove role capability');

if(s===original) throw new Error('hardening made no changes');
fs.writeFileSync(path,s);
console.log(`Wethaq runtime hardening applied: ${original.length} -> ${s.length} bytes`);
