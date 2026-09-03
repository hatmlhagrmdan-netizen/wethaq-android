import http from 'http';
import net from 'net';
import {spawn} from 'child_process';
import crypto from 'crypto';
import Database from 'better-sqlite3';
import jwt from 'jsonwebtoken';

const PORT=Number(process.env.PORT||3000);
const CORE_PORT=Number(process.env.WETHAQ_CORE_PORT||39091);
const HOST='127.0.0.1';
const CORE_URL=`http://${HOST}:${CORE_PORT}`;
const DB_PATH=process.env.DB_PATH||'wethaq.db';
const DB=new Database(DB_PATH);
const OWNER_WETHAQ_ID=process.env.OWNER_WETHAQ_ID||'Hatem_Hussin_Al_Haj_Ramadan1995';
const YEAR_MS=365*24*60*60*1000;
const ROLES={
 founder:{label:'مدير ومؤسس وثاق',price:3000,rank:100,ban:'both',appoint:['executive','deputy1','deputy2','deputy3','supervisor','admin_member','premium'],quota:null},
 executive:{label:'المدير التنفيذي',price:100,rank:90,ban:'both',appoint:['admin_member','premium'],quota:{admin_member:3,premium:10}},
 deputy1:{label:'النائب الأول',price:80,rank:80,ban:'both',appoint:['admin_member','premium'],quota:{admin_member:1,premium:7}},
 deputy2:{label:'النائب الثاني',price:60,rank:70,ban:'both',appoint:['admin_member','premium'],quota:{admin_member:1,premium:5}},
 deputy3:{label:'النائب الثالث',price:40,rank:60,ban:'both',appoint:['admin_member','premium'],quota:{admin_member:1,premium:3}},
 supervisor:{label:'المشرف',price:20,rank:50,ban:'temporary',appoint:['premium'],quota:{premium:2}},
 admin_member:{label:'عضو إدارة',price:10,rank:40,ban:'temporary',appoint:['premium'],quota:{premium:1}},
 premium:{label:'عضو مميز',price:2,rank:10,ban:'alert',appoint:[],quota:{}}
};
const normalRank=0;

DB.exec(`CREATE TABLE IF NOT EXISTS admin_roles(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL UNIQUE,role TEXT NOT NULL,admin_code_hash TEXT,created_by INTEGER,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,expires_at TEXT,FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE);CREATE INDEX IF NOT EXISTS idx_admin_roles_role_v8 ON admin_roles(role);`);
const safe=v=>String(v??'').trim();
const now=()=>new Date().toISOString();
const hash=v=>crypto.createHash('sha256').update(String(v)).digest('hex');
const newCode=()=>crypto.randomBytes(6).toString('hex').toUpperCase();
const roleRank=r=>ROLES[r]?.rank??normalRank;
const roleLabel=r=>ROLES[r]?.label||'مستخدم';
const json=async r=>{try{return await r.json()}catch{return {}}};

function userById(id){return DB.prepare('SELECT id,wethaq_id,name,birth_year FROM users WHERE id=?').get(Number(id))||null;}
function userByWethaqId(id){return DB.prepare('SELECT id,wethaq_id,name,birth_year FROM users WHERE wethaq_id=?').get(safe(id))||null;}
function roleOf(userId){
 if(!userId)return null;
 const u=userById(userId);
 if(!u)return null;
 if(u.wethaq_id===OWNER_WETHAQ_ID)return 'founder';
 const row=DB.prepare('SELECT role,expires_at FROM admin_roles WHERE user_id=?').get(Number(userId));
 if(!row||!ROLES[row.role])return null;
 if(row.expires_at&&new Date(row.expires_at)<=new Date())return null;
 return row.role;
}
function currentRoleRow(userId){
 const role=roleOf(userId);
 if(!role||role==='founder')return {role,expires_at:null};
 return DB.prepare('SELECT role,expires_at FROM admin_roles WHERE user_id=?').get(Number(userId))||{role:null,expires_at:null};
}
function countAssignedBy(actorId,role){return Number(DB.prepare('SELECT COUNT(*) n FROM admin_roles WHERE created_by=? AND role=? AND (expires_at IS NULL OR expires_at>?)').get(Number(actorId),role,now())?.n||0);}
function audit(actorId,targetId,action,metadata={}){try{DB.prepare('INSERT INTO audit_logs(actor_user_id,target_user_id,action,metadata,created_at) VALUES(?,?,?,?,?)').run(Number(actorId),targetId?Number(targetId):null,String(action),JSON.stringify(metadata),now());}catch{}}
function makePublic(){
 const active=DB.prepare(`SELECT ar.role,ar.expires_at,u.id,u.wethaq_id,u.name,u.birth_year FROM admin_roles ar JOIN users u ON u.id=ar.user_id`).all().filter(x=>!x.expires_at||new Date(x.expires_at)>new Date());
 const one=r=>{const x=active.find(v=>v.role===r);return x?{wethaq_id:x.wethaq_id,name:x.name,birth_year:x.birth_year,role:r,role_label:roleLabel(r),price_usd:ROLES[r].price,expires_at:x.expires_at}:null};
 const founder=userByWethaqId(OWNER_WETHAQ_ID);
 return {version:'8.0.0',pricing:Object.entries(ROLES).map(([role,r])=>({role,role_label:r.label,price_usd:r.price,period:'سنة واحدة',permission:r.ban==='alert'?'تنبيه فقط':r.ban==='temporary'?'حظر مؤقت + تنبيه':'حظر مؤقت أو نهائي + تنبيه'})),founder:founder?{wethaq_id:founder.wethaq_id,name:founder.name,birth_year:founder.birth_year,role:'founder',role_label:ROLES.founder.label,price_usd:ROLES.founder.price}:null,executive:one('executive'),deputies:{deputy1:one('deputy1'),deputy2:one('deputy2'),deputy3:one('deputy3')},supervisor:one('supervisor'),members:active.filter(x=>x.role==='admin_member').slice(0,10).map(x=>({...x,role_label:roleLabel(x.role),price_usd:ROLES.admin_member.price})),premium_members:active.filter(x=>x.role==='premium').map(x=>({...x,role_label:roleLabel(x.role),price_usd:ROLES.premium.price}))};
}
async function coreFetch(path,opts={}){return fetch(CORE_URL+path,opts);}
async function authenticate(req){
 const auth=req.headers.authorization||'';if(!auth.startsWith('Bearer '))return null;const token=auth.slice(7);
 try{
  const r=await coreFetch('/api/me',{headers:{authorization:`Bearer ${token}`}});if(!r.ok)return null;
  const body=await json(r);if(!body.user?.id)return null;
  return {token,user:body.user,role:roleOf(Number(body.user.id))};
 }catch{return null;}
}
async function readBody(req){return await new Promise((resolve,reject)=>{let b='';req.on('data',c=>{b+=c;if(b.length>2*1024*1024)reject(new Error('body_too_large'))});req.on('end',()=>{try{resolve(b?JSON.parse(b):{})}catch{reject(new Error('invalid_json'))}});req.on('error',reject);});}
function reply(res,status,body,headers={}){res.writeHead(status,{'content-type':'application/json; charset=utf-8','cache-control':'no-store',...headers});res.end(JSON.stringify(body));}
async function sendMessage(token,to,body){try{const r=await coreFetch('/api/messages',{method:'POST',headers:{'content-type':'application/json',authorization:`Bearer ${token}`},body:JSON.stringify({to,body,client_id:`admin-${Date.now()}-${crypto.randomBytes(4).toString('hex')}`)});return r.ok;}catch{return false;}}
function adminCodeValid(userId,code){const row=DB.prepare('SELECT admin_code_hash,expires_at FROM admin_roles WHERE user_id=?').get(Number(userId));return !!row&&!!ROLES[roleOf(userId)]&&!!row.admin_code_hash&&hash(safe(code).toUpperCase())===row.admin_code_hash&&(!row.expires_at||new Date(row.expires_at)>new Date());}
function canAppoint(actorRole,targetRole){return !!ROLES[actorRole]&&ROLES[actorRole].appoint.includes(targetRole)&&targetRole!=='founder'&&roleRank(targetRole)<roleRank(actorRole);}
function canBan(actorRole,targetRole){if(!ROLES[actorRole]||targetRole==='founder')return false;return roleRank(targetRole)<roleRank(actorRole);}
function canAlert(actorRole,targetRole){if(!ROLES[actorRole]||targetRole==='founder')return false;return roleRank(targetRole)<roleRank(actorRole);}
function permissionFor(actorRole,minutes){const mode=ROLES[actorRole]?.ban;if(mode==='alert')return {ok:false,error:'alert_only'};if(mode==='temporary'&&Number(minutes)<=0)return {ok:false,error:'temporary_ban_only'};return {ok:true};}

async function authMutationResponse(path,body,req,res){
 try{
  const r=await coreFetch(path,{method:req.method,headers:{'content-type':'application/json',...(req.headers.authorization?{authorization:req.headers.authorization}:{}),...(req.headers['user-agent']?{'user-agent':req.headers['user-agent']}: {})},body:JSON.stringify(body)});
  const payload=await json(r);
  if((path==='/api/login'||path==='/api/identity')&&r.ok&&payload?.user?.id){
   const actual=roleOf(Number(payload.user.id));
   if(actual){payload.role=actual==='founder'?'founder':'admin_member';payload.admin_role=actual;payload.admin_role_label=roleLabel(actual);}
  }
  reply(res,r.status,payload);return true;
 }catch(e){reply(res,502,{error:'core_unavailable',message:safe(e.message)});return true;}
}

async function adminRoute(req,res,path){
 const a=await authenticate(req);if(!a)return reply(res,401,{error:'unauthorized'});
 if(path==='/api/admin/role'&&req.method==='GET'){
  if(!a.role)return reply(res,403,{error:'admin_forbidden'});const row=currentRoleRow(a.user.id);return reply(res,200,{role:a.role,role_label:roleLabel(a.role),price_usd:ROLES[a.role].price,expires_at:row.expires_at});
 }
 if(path==='/api/admin/verify-code'&&req.method==='POST'){
  const b=await readBody(req);if(a.role==='founder')return reply(res,200,{ok:true,role:'founder',role_label:ROLES.founder.label,expires_at:null});if(!adminCodeValid(a.user.id,b.code))return reply(res,403,{error:'invalid_admin_code'});return reply(res,200,{ok:true,role:a.role,role_label:roleLabel(a.role),expires_at:currentRoleRow(a.user.id).expires_at});
 }
 if(path==='/api/admin/assign'&&req.method==='POST'){
  const b=await readBody(req),target=userByWethaqId(b.wethaqId),targetRole=safe(b.role);if(!target||!ROLES[targetRole])return reply(res,400,{error:'invalid_assignment'});if(!canAppoint(a.role,targetRole))return reply(res,403,{error:'insufficient_role'});if(target.id===a.user.id)return reply(res,403,{error:'cannot_assign_self'});if(target.wethaq_id===OWNER_WETHAQ_ID)return reply(res,403,{error:'protected_target'});
  const existing=roleOf(target.id);if(existing&&roleRank(existing)>=roleRank(a.role))return reply(res,403,{error:'cannot_assign_equal_or_higher'});
  const quota=ROLES[a.role].quota?.[targetRole];const count=countAssignedBy(a.user.id,targetRole);if(quota!=null&&count>=quota&&existing!==targetRole)return reply(res,409,{error:'appointment_quota_exceeded',quota});if(targetRole==='admin_member'&&Number(DB.prepare('SELECT COUNT(*) n FROM admin_roles WHERE role=?').get('admin_member')?.n||0)>=10&&existing!=='admin_member')return reply(res,409,{error:'admin_member_limit'});
  const code=newCode(),expires=new Date(Date.now()+YEAR_MS).toISOString();DB.prepare('DELETE FROM admin_roles WHERE user_id=?').run(target.id);DB.prepare('INSERT INTO admin_roles(user_id,role,admin_code_hash,created_by,expires_at) VALUES(?,?,?,?,?)').run(target.id,targetRole,hash(code),a.user.id,expires);
  const sent=await sendMessage(a.token,target.wethaq_id,`تم تعيينك ${roleLabel(targetRole)} في وَثاق لمدة سنة واحدة. السعر السنوي: $${ROLES[targetRole].price}. رمز الدخول الإداري: ${code}`);audit(a.user.id,target.id,'assign_role',{role:targetRole,price_usd:ROLES[targetRole].price,expires_at:expires,message_sent:sent});
  return reply(res,201,{ok:true,user:target,role:targetRole,role_label:roleLabel(targetRole),price_usd:ROLES[targetRole].price,period:'سنة واحدة',expires_at:expires,admin_code:code,message_sent:sent,quota_remaining:quota==null?null:Math.max(0,quota-countAssignedBy(a.user.id,targetRole))});
 }
 if(path==='/api/admin/remove-role'&&req.method==='POST'){
  if(a.role!=='founder')return reply(res,403,{error:'founder_only'});const b=await readBody(req),target=userByWethaqId(b.wethaqId);if(!target)return reply(res,404,{error:'user_not_found'});const old=roleOf(target.id);if(target.wethaq_id===OWNER_WETHAQ_ID)return reply(res,403,{error:'protected_target'});DB.prepare('DELETE FROM admin_roles WHERE user_id=?').run(target.id);const sent=await sendMessage(a.token,target.wethaq_id,`تمت إزالة صفتك الإدارية ${old?roleLabel(old):''} من وَثاق بواسطة المؤسس.`);audit(a.user.id,target.id,'remove_role',{old_role:old,message_sent:sent});return reply(res,200,{ok:true});
 }
 if(path==='/api/admin/rbac/ban'&&req.method==='POST'){
  const b=await readBody(req),target=userByWethaqId(b.wethaqId),minutes=Math.max(0,Number(b.minutes||0)),reason=safe(b.reason)||'بقرار من إدارة وَثاق';if(!target)return reply(res,404,{error:'user_not_found'});const targetRole=roleOf(target.id);if(!canBan(a.role,targetRole))return reply(res,403,{error:'insufficient_role'});const p=permissionFor(a.role,minutes);if(!p.ok)return reply(res,403,{error:p.error});
  const type=minutes>0?'temporary':'permanent',expires=type==='temporary'?new Date(Date.now()+minutes*60000).toISOString():null;DB.prepare('DELETE FROM bans WHERE user_id=?').run(target.id);DB.prepare('INSERT INTO bans(user_id,ban_type,expires_at,reason,created_by) VALUES(?,?,?,?,?)').run(target.id,type,expires,reason,a.user.id);const sent=await sendMessage(a.token,target.wethaq_id,`تم حظرك من قبل ${roleLabel(a.role)}. النوع: ${type==='temporary'?'مؤقت':'نهائي'}. السبب: ${reason}`);audit(a.user.id,target.id,'ban',{ban_type:type,minutes,reason,message_sent:sent});return reply(res,200,{ok:true,ban_type:type,expires_at:expires,message_sent:sent});
 }
 if(path==='/api/admin/unban'&&req.method==='POST'){
  if(!['founder','executive','deputy1','deputy2','deputy3'].includes(a.role))return reply(res,403,{error:'insufficient_role'});const b=await readBody(req),target=userByWethaqId(b.wethaqId);if(!target)return reply(res,404,{error:'user_not_found'});const targetRole=roleOf(target.id);if(!canBan(a.role,targetRole))return reply(res,403,{error:'protected_target'});DB.prepare('DELETE FROM bans WHERE user_id=?').run(target.id);const sent=await sendMessage(a.token,target.wethaq_id,`✅ تم إلغاء الحظر عن حسابك من قبل ${roleLabel(a.role)}.`);audit(a.user.id,target.id,'unban',{message_sent:sent});return reply(res,200,{ok:true,message_sent:sent});
 }
 if(path==='/api/admin/alert'&&req.method==='POST'){
  const b=await readBody(req),target=userByWethaqId(b.wethaqId),reason=safe(b.reason)||'تنبيه من إدارة وَثاق';if(!target)return reply(res,404,{error:'user_not_found'});const targetRole=roleOf(target.id);if(!canAlert(a.role,targetRole))return reply(res,403,{error:'protected_target'});const sent=await sendMessage(a.token,target.wethaq_id,`⚠️ تم تنبيهك من قبل ${roleLabel(a.role)}. ${reason}`);audit(a.user.id,target.id,'alert',{reason,message_sent:sent});return reply(res,200,{ok:true,message_sent:sent});
 }
 if(path==='/api/admin/structure'&&req.method==='GET'){
  if(!a.role)return reply(res,403,{error:'admin_forbidden'});return reply(res,200,makePublic());
 }
 if(path==='/api/admin/audit-log'&&req.method==='GET'){
  if(a.role!=='founder')return reply(res,403,{error:'founder_only'});const logs=DB.prepare(`SELECT a.id,a.action,a.metadata,a.created_at,u.name actor_name,t.name target_name FROM audit_logs a LEFT JOIN users u ON u.id=a.actor_user_id LEFT JOIN users t ON t.id=a.target_user_id ORDER BY a.id DESC LIMIT 200`).all();return reply(res,200,{logs});
 }
 return reply(res,404,{error:'not_found'});
}

async function handle(req,res){
 const url=new URL(req.url,`http://${req.headers.host||'localhost'}`),path=url.pathname;
 if((path==='/api/login'||path==='/api/identity')&&req.method==='POST')return authMutationResponse(path,await readBody(req),req,res);
 if(path==='/api/public/administration'&&req.method==='GET')return reply(res,200,makePublic());
 if(path.startsWith('/api/admin/'))return adminRoute(req,res,path);
 if(path==='/health'&&req.method==='GET'){
  let core={ok:false};try{const r=await fetch(CORE_URL+'/health');core=await json(r)}catch{}return reply(res,200,{ok:true,service:'wethaq',version:'8.0.0',rbac:true,core});
 }
 proxyHttp(req,res);
}
function proxyHttp(req,res){
 const u=new URL(req.url,CORE_URL),options={hostname:HOST,port:CORE_PORT,path:u.pathname+u.search,method:req.method,headers:{...req.headers,host:`${HOST}:${CORE_PORT}`}};const p=http.request(options,pr=>{res.writeHead(pr.statusCode||502,pr.headers);pr.pipe(res);});p.on('error',()=>{if(!res.headersSent)reply(res,502,{error:'core_unavailable'});else res.end();});req.pipe(p);}
function upgrade(req,socket,head){const u=new URL(req.url,`http://${req.headers.host||'localhost'}`);if(u.pathname!=='/ws'){socket.destroy();return;}const upstream=net.connect(CORE_PORT,HOST,()=>{const h=[`GET ${u.pathname}${u.search} HTTP/1.1`,`Host: ${HOST}:${CORE_PORT}`,`Connection: Upgrade`,`Upgrade: websocket`];for(const [k,v] of Object.entries(req.headers||{}))if(!['host','connection','upgrade'].includes(k.toLowerCase()))h.push(`${k}: ${Array.isArray(v)?v.join(', '):v}`);h.push('\r\n');upstream.write(h.join('\r\n'));if(head?.length)upstream.write(head);socket.pipe(upstream).pipe(socket);});upstream.on('error',()=>socket.destroy());socket.on('error',()=>upstream.destroy());}

const core=spawn(process.execPath,['server.js'],{cwd:process.cwd(),env:{...process.env,PORT:String(CORE_PORT)}});
core.on('exit',(code)=>{if(code!==0)process.exitCode=1;});
const server=http.createServer((req,res)=>handle(req,res));server.on('upgrade',upgrade);server.listen(PORT,'0.0.0.0',()=>console.log(`Wethaq gateway v8 listening on ${PORT}, core ${CORE_PORT}`));
process.on('SIGTERM',()=>{try{core.kill('SIGTERM')}catch{}process.exit(0);});
process.on('SIGINT',()=>{try{core.kill('SIGINT')}catch{}process.exit(0);});
