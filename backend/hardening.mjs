import crypto from 'crypto';
import jwt from 'jsonwebtoken';
import Database from 'better-sqlite3';
import express from 'express';

const db = new Database(process.env.DB_PATH || 'wethaq.db');
const JWT_SECRET = String(process.env.JWT_SECRET || '').trim();
const OWNER_WETHAQ_ID = process.env.OWNER_WETHAQ_ID || 'Hatem_Hussin_Al_Haj_Ramadan1995';
const RANK = { founder: 0, executive: 1, deputy1: 2, deputy2: 3, deputy3: 4, supervisor: 5, admin_member: 6, premium: 7, user: 8 };
const STRUCTURE_ROLES = new Set(['founder','executive','deputy1','deputy2','deputy3','supervisor']);
const CAPABILITIES = {
  founder:{manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:true},
  executive:{manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
  deputy1:{manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
  deputy2:{manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
  deputy3:{manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
  supervisor:{manageUsers:true,banPermanent:true,banTemporary:true,unban:true,warn:true,assign:true,removeRole:true,viewStructure:true,viewAudit:false},
  admin_member:{manageUsers:true,banPermanent:false,banTemporary:true,unban:false,warn:true,assign:false,removeRole:false,viewStructure:false,viewAudit:false},
  premium:{manageUsers:false,banPermanent:false,banTemporary:false,unban:false,warn:true,assign:false,removeRole:false,viewStructure:false,viewAudit:false}
};
const attemptBuckets = new Map();
function safeName(value){return String(value||'').replace(/[<>\u0000-\u001f]/g,'').trim().replace(/\s+/g,' ');}
function verifyPersonalCode(code,stored){try{const [saltHex,hashHex]=String(stored||'').split(':');if(!saltHex||!hashHex)return false;const salt=Buffer.from(saltHex,'hex'),expected=Buffer.from(hashHex,'hex'),actual=crypto.scryptSync(String(code||'').trim(),salt,32);return expected.length===actual.length&&crypto.timingSafeEqual(expected,actual);}catch{return false;}}
function roleRow(userId){return db.prepare(`SELECT ar.*,u.name,u.wethaq_id,u.birth_year FROM admin_roles ar JOIN users u ON u.id=ar.user_id WHERE ar.user_id=? AND ar.active=1`).get(Number(userId));}
function activeRole(userId){const row=roleRow(userId);if(!row)return null;if(row.role!=='founder'&&row.subscription_expires_at&&new Date(row.subscription_expires_at)<=new Date()){db.prepare('UPDATE admin_roles SET active=0 WHERE user_id=?').run(Number(userId));return null;}return row;}
function requireAdmin(req,res,next){const h=req.headers.authorization||'',t=h.startsWith('Bearer ')?h.slice(7):'';if(!t||JWT_SECRET.length<32)return res.status(401).json({error:'admin_unauthorized'});try{const d=jwt.verify(t,JWT_SECRET);if(d.admin!==true)return res.status(403).json({error:'admin_token_required'});const r=activeRole(Number(d.sub));if(!r||r.role!==d.role)return res.status(403).json({error:'admin_role_inactive'});if(Number(r.code_version||1)!==Number(d.codeVersion||1))return res.status(403).json({error:'admin_session_revoked'});req.admin=d;req.adminRow=r;next();}catch{return res.status(401).json({error:'invalid_admin_token'});}}
function targetManagementGuard(req,res,next){const id=String(req.body?.wethaqId||'').trim();const t=db.prepare('SELECT id,wethaq_id FROM users WHERE wethaq_id=?').get(id);if(!t)return res.status(404).json({error:'user_not_found'});const tr=activeRole(t.id),targetRole=tr?.role||'user';if(targetRole==='founder')return res.status(403).json({error:'founder_protected'});const ar=RANK[req.adminRow?.role]??RANK.user,trank=RANK[targetRole]??RANK.user;if(ar>=trank)return res.status(403).json({error:'target_scope_denied'});next();}
function capabilityGuard(name){return(req,res,next)=>{if(!(CAPABILITIES[req.adminRow?.role]||{})[name])return res.status(403).json({error:`capability_${name}_denied`});next();};}
function assignmentIdentityGuard(req,res,next){const id=String(req.body?.wethaqId||'').trim(),name=safeName(req.body?.name),birthYear=Number(req.body?.birthYear),personalCode=String(req.body?.personalCode||'').trim();if(!id||!name||!Number.isInteger(birthYear)||!/^\d{6,12}$/.test(personalCode))return res.status(400).json({error:'assignment_identity_required'});const t=db.prepare('SELECT id,name,birth_year,wethaq_id,personal_code_hash FROM users WHERE wethaq_id=?').get(id);if(!t)return res.status(404).json({error:'user_not_found'});if(safeName(t.name)!==name||Number(t.birth_year)!==birthYear)return res.status(403).json({error:'assignment_identity_mismatch'});if(!verifyPersonalCode(personalCode,t.personal_code_hash))return res.status(403).json({error:'assignment_personal_code_invalid'});delete req.body.personalCode;next();}
function limited(key,limit,windowMs){const now=Date.now(),old=attemptBuckets.get(key);if(!old||now-old.at>windowMs){attemptBuckets.set(key,{at:now,count:1});return true;}old.count++;return old.count<=limit;}
function loginGuard(req,res,next){const name=safeName(req.body?.name);const key=`user-login:${req.ip}:${name}`;if(!limited(key,8,15*60*1000))return res.status(429).json({error:'too_many_attempts'});next();}
function adminLoginGuard(req,res,next){const name=safeName(req.body?.name);const key=`admin-login:${req.ip}:${name}`;if(!limited(key,8,15*60*1000))return res.status(429).json({error:'too_many_attempts'});next();}
function reshapeMessages(limit){return(req,res,next)=>{const old=res.json.bind(res);res.json=body=>{if(body&&Array.isArray(body.messages)){const all=body.messages,hasMore=all.length>limit;body.messages=hasMore?all.slice(-limit):all;body.hasMore=hasMore;}return old(body);};next();};}
function stripSearchFields(){return(req,res,next)=>{const old=res.json.bind(res);res.json=body=>{if(body&&Array.isArray(body.users))body.users=body.users.map(u=>{const z={...u};delete z.birth_year;delete z.avatar_data;delete z.personal_code_hash;delete z.password_hash;return z;});return old(body);};next();};}
function injectRolePayload(req,res,next){const old=res.json.bind(res);res.json=body=>{if(body&&body.role)body.capabilities=CAPABILITIES[body.role]||{};return old(body);};next();}

const originalGet=express.application.get;
const originalPost=express.application.post;
const originalListen=express.application.listen;
express.application.get=function(path,...handlers){
  if(path==='/api/admin/policy')return originalGet.call(this,path,requireAdmin,...handlers);
  if(path==='/api/admin/structure')return originalGet.call(this,path,requireAdmin,(req,res,next)=>{if(!STRUCTURE_ROLES.has(req.adminRow.role))return res.status(403).json({error:'structure_access_denied'});next();},...handlers);
  if(path==='/api/admin/audit-log')return originalGet.call(this,path,requireAdmin,(req,res,next)=>{if(req.adminRow.role!=='founder')return res.status(403).json({error:'founder_only'});next();},...handlers);
  if(path==='/api/admin/users')return originalGet.call(this,path,...handlers.slice(0,Math.max(0,handlers.length-1)),requireAdmin,capabilityGuard('manageUsers'),...handlers.slice(-1));
  if(path==='/api/admin/role')return originalGet.call(this,path,...handlers.slice(0,Math.max(0,handlers.length-1)),requireAdmin,injectRolePayload,...handlers.slice(-1));
  if(path==='/api/messages/inbox')return originalGet.call(this,path,...handlers.slice(0,Math.max(0,handlers.length-1)),reshapeMessages(50),...handlers.slice(-1));
  if(path==='/api/messages/:wethaqId')return originalGet.call(this,path,...handlers.slice(0,Math.max(0,handlers.length-1)),reshapeMessages(50),...handlers.slice(-1));
  if(path==='/api/search')return originalGet.call(this,path,...handlers,stripSearchFields());
  if(path==='/api/calls/signals/:wethaqId')return originalGet.call(this,path,...handlers.slice(0,Math.max(0,handlers.length-1)),(req,res,next)=>{try{db.prepare("DELETE FROM call_signals WHERE created_at < datetime('now','-10 minutes')").run();}catch{}next();},...handlers.slice(-1));
  return originalGet.call(this,path,...handlers);
};
express.application.post=function(path,...handlers){
  if(path==='/api/identity'||path==='/api/login')return originalPost.call(this,path,path==='/api/identity'?loginGuard:loginGuard,...handlers);
  if(path==='/api/admin/login')return originalPost.call(this,path,adminLoginGuard,...handlers);
  if(path==='/api/admin/assign')return originalPost.call(this,path,...handlers.slice(0,1),requireAdmin,capabilityGuard('assign'),assignmentIdentityGuard,...handlers.slice(1));
  if(path==='/api/admin/remove-role')return originalPost.call(this,path,...handlers.slice(0,1),requireAdmin,capabilityGuard('removeRole'),targetManagementGuard,...handlers.slice(1));
  if(path==='/api/admin/rbac/ban')return originalPost.call(this,path,...handlers.slice(0,1),requireAdmin,capabilityGuard('manageUsers'),(req,res,next)=>{const m=Math.max(0,Number(req.body?.minutes||0)),c=CAPABILITIES[req.adminRow.role]||{};if(m>0&&!c.banTemporary)return res.status(403).json({error:'temporary_ban_denied'});if(m===0&&!c.banPermanent)return res.status(403).json({error:'permanent_ban_denied'});next();},targetManagementGuard,...handlers.slice(1));
  if(path==='/api/admin/unban')return originalPost.call(this,path,...handlers.slice(0,1),requireAdmin,capabilityGuard('unban'),targetManagementGuard,...handlers.slice(1));
  if(path==='/api/admin/alert')return originalPost.call(this,path,...handlers.slice(0,1),requireAdmin,capabilityGuard('warn'),targetManagementGuard,...handlers.slice(1));
  return originalPost.call(this,path,...handlers);
};

express.application.listen=function(...args){
  const app=this;
  if(!app.__wethaqExtendedRoutes){
    app.__wethaqExtendedRoutes=true;
    originalGet.call(app,'/api/trust/:wethaqId',function(req,res){
      const h=req.headers.authorization||'',t=h.startsWith('Bearer ')?h.slice(7):'';
      if(!t)return res.status(401).json({error:'unauthorized'});
      try{
        const d=jwt.verify(t,JWT_SECRET),u=db.prepare('SELECT id,wethaq_id,name,created_at,last_seen,avatar_data FROM users WHERE wethaq_id=?').get(req.params.wethaqId);
        if(!u||Number(d.sub)!==Number(u.id))return res.status(403).json({error:'owner_access_only'});
        res.json({trusted:true,wethaq_id:u.wethaq_id,name:u.name,created_at:u.created_at,last_seen:u.last_seen||null,has_avatar:!!u.avatar_data,trust_version:'1'});
      }catch{return res.status(401).json({error:'invalid_token'});}
    });
  }
  return originalListen.apply(app,args);
};
process.on('exit',()=>{try{db.close();}catch{}});
