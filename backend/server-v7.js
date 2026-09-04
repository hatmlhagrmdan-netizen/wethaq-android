import http from 'http';
import net from 'net';
import {spawn} from 'child_process';
import crypto from 'crypto';
import Database from 'better-sqlite3';
import jwt from 'jsonwebtoken';

const PUBLIC_PORT=Number(process.env.PORT||3000);
const UPSTREAM_PORT=Number(process.env.WETHAQ_CORE_PORT||39091);
const HOST='127.0.0.1';
const CORE=`http://${HOST}:${UPSTREAM_PORT}`;
const DB=new Database(process.env.DB_PATH||'wethaq.db');
const JWT_SECRET=process.env.JWT_SECRET||null;
const OWNER_WETHAQ_ID=process.env.OWNER_WETHAQ_ID||'Hatem_Hussin_Al_Haj_Ramadan1995';
const YEAR=365*24*60*60*1000;

const ROLES={
 founder:{label:'مدير ومؤسس وثاق',price:3000,rank:100,ban:'both',appoint:['executive','deputy1','deputy2','deputy3','supervisor','admin_member','premium'],quota:null},
 executive:{label:'المدير التنفيذي',price:100,rank:90,ban:'both',appoint:['admin_member','premium'],quota:{admin_member:3,premium:10}},
 deputy1:{label:'النائب الأول',price:80,rank:80,ban:'both',appoint:['admin_member','premium'],quota:{admin_member:1,premium:7}},
 deputy2:{label:'النائب الثاني',price:60,rank:70,ban:'both',appoint:['admin_member','premium'],quota:{admin_member:1,premium:5}},
 deputy3:{label:'النائب الثالث',price:40,rank:60,ban:'both',appoint:['admin_member','premium'],quota:{admin_member:1,premium:3}},
 supervisor:{label:'المشرف',price:20,rank:50,ban:'temporary',appoint:[],quota:{premium:2}},
 admin_member:{label:'عضو إدارة',price:10,rank:40,ban:'temporary',appoint:[],quota:{premium:1}},
 premium:{label:'عضو مميز',price:2,rank:10,ban:'alert',appoint:[],quota:{}}
};

DB.exec(`CREATE TABLE IF NOT EXISTS admin_roles(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL UNIQUE,role TEXT NOT NULL,admin_code_hash TEXT,created_by INTEGER,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,expires_at TEXT,FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE);CREATE INDEX IF NOT EXISTS idx_admin_roles_role_v7 ON admin_roles(role);`);
const safe=v=>String(v??'').trim();
const hash=v=>crypto.createHash('sha256').update(String(v)).digest('hex');
const newCode=()=>crypto.randomBytes(6).toString('hex').toUpperCase();
const now=()=>new Date().toISOString();
const json=async r=>{try{return await r.json()}catch{return {}}};

function roleOf(userId){
 if(!userId)return null;
 const u=DB.prepare('SELECT wethaq_id FROM users WHERE id=?').get(Number(userId));
 if(u?.wethaq_id===OWNER_WETHAQ_ID)return 'founder';
 const row=DB.prepare('SELECT role,expires_at FROM admin_roles WHERE user_id=?').get(Number(userId));
 if(!row||!ROLES[row.role])return null;
 if(row.expires_at&&new Date(row.expires_at)<=new Date())return null;
 return row.role;
}
function roleOfWethaqId(id){const u=DB.prepare('SELECT id FROM users WHERE wethaq_id=?').get(id);return u?roleOf(u.id):null;}
function rank(r){return ROLES[r]?.rank??0;}
function activeRole(r){return !!r&&!!ROLES[r];}
function countAssignedBy(creatorId,role){return Number(DB.prepare('SELECT COUNT(*) n FROM admin_roles WHERE created_by=? AND role=?').get(Number(creatorId),role)?.n||0);}
function canAppoint(actor,targetRole){const r=ROLES[actor];return !!r&&r.appoint.includes(targetRole)&&targetRole!=='founder';}
function canBan(actor,target){
 const a=ROLES[actor];
 if(!a)return false;
 if(target==='founder')return false;
 if(a.ban==='alert')return false;
 if(actor==='supervisor'||actor==='admin_member')return target==='premium';
 if(actor==='deputy3')return rank(target)<rank(actor);
 return rank(target)<rank(actor);
}
function banType(actor,minutes){return ROLES[actor]?.ban==='temporary'?'temporary':(Number(minutes)>0?'temporary':'permanent');}
function publicRows(){
 const users=DB.prepare(`SELECT ar.role,ar.expires_at,u.id,u.wethaq_id,u.name,u.birth_year FROM admin_roles ar JOIN users u ON u.id=ar.user_id ORDER BY ar.id`).all();
 const active=users.filter(x=>!x.expires_at||new Date(x.expires_at)>new Date());
 const pick=r=>{const x=active.find(y=>y.role===r);return x?{id:x.id,wethaq_id:x.wethaq_id,name:x.name,birth_year:x.birth_year,role:x.role,role_label:ROLES[x.role].label,price_usd:ROLES[x.role].price,expires_at:x.expires_at}:null};
 const owner=DB.prepare('SELECT id,wethaq_id,name,birth_year FROM users WHERE wethaq_id=?').get(OWNER_WETHAQ_ID);
 return {pricing:Object.entries(ROLES).map(([role,x])=>({role,role_label:x.label,price_usd:x.price,period:'سنة واحدة',ban:x.ban,appointment:x.appoint.length>0})),founder:owner?{...owner,role:'founder',role_label:ROLES.founder.label,price_usd:ROLES.founder.price}:null,executive:pick('executive'),deputies:{deputy1:pick('deputy1'),deputy2:pick('deputy2'),deputy3:pick('deputy3')},supervisor:pick('supervisor'),members:active.filter(x=>x.role==='admin_member').map(x=>({...x,role_label:ROLES.admin_member.label})).slice(0,10),premium_members:active.filter(x=>x.role==='premium').map(x=>({...x,role_label:ROLES.premium.label}))};
}
async function coreFetch(path,opts={}){return fetch(CORE+path,opts);}
async function authenticate(req){
 const auth=req.headers.authorization||'';const token=auth.startsWith('Bearer ')?auth.slice(7):'';if(!token)return null;
 const payload=JWT_SECRET?(()=>{try{return jwt.verify(token,JWT_SECRET)}catch{return null}})():(()=>{try{return jwt.decode(token)}catch{return null}})();
 if(!payload?.sub)return null;
 try{const r=await coreFetch('/api/me',{headers:{authorization:`Bearer ${token}`}});if(!r.ok)return null;const body=await json(r);if(!body.user)return null;return {token,payload,user:body.user,role:roleOf(Number(body.user.id))};}catch{return null;}
}
async function sendCoreMessage(token,to,body){
 const cid=`admin-${Date.now()}-${crypto.randomBytes(4).toString('hex')}`;
 try{await coreFetch('/api/messages',{method:'POST',headers:{'content-type':'application/json',authorization:`Bearer ${token}`},body:JSON.stringify({to,body,client_id:cid})});}catch{}
}
async function readBody(req){return new Promise((resolve,reject)=>{let b='';req.on('data',c=>{b+=c;if(b.length>1024*1024)reject(new Error('body_too_large'))});req.on('end',()=>{try{resolve(b?JSON.parse(b):{})}catch{reject(new Error('invalid_json'))}});req.on('error',reject)});}
function reply(res,status,body){res.writeHead(status,{'content-type':'application/json; charset=utf-8','cache-control':'no-store'});res.end(JSON.stringify(body));}

async function adminRoute(req,res){
 const a=await authenticate(req);
 if(!a)return reply(res,401,{error:'unauthorized'});
 const path=new URL(req.url,`http://${req.headers.host||'localhost'}`).pathname;
 if(path==='/api/admin/role'&&req.method==='GET'){
  if(!a.role)return reply(res,403,{error:'admin_forbidden'});
  return reply(res,200,{role:a.role,role_label:ROLES[a.role].label,price_usd:ROLES[a.role].price,expires_at:a.role==='founder'?null:DB.prepare('SELECT expires_at FROM admin_roles WHERE user_id=?').get(a.user.id)?.expires_at||null});
 }
 if(path==='/api/admin/structure'&&req.method==='GET'){
  if(!a.role)return reply(res,403,{error:'admin_forbidden'}); return reply(res,200,publicRows());
 }
 if(path==='/api/admin/assign'&&req.method==='POST'){
  if(!a.role)return reply(res,403,{error:'admin_forbidden'});
  const b=await readBody(req),target=DB.prepare('SELECT id,wethaq_id,name,birth_year FROM users WHERE wethaq_id=?').get(safe(b.wethaqId)),role=safe(b.role);
  if(!target||!ROLES[role])return reply(res,400,{error:'invalid_assignment'});
  if(!canAppoint(a.role,role))return reply(res,403,{error:'insufficient_role'});
  if(target.wethaq_id===OWNER_WETHAQ_ID)return reply(res,400,{error:'cannot_assign_founder'});
  if(rank(role)>=rank(a.role))return reply(res,403,{error:'cannot_assign_equal_or_higher'});
  const existing=roleOf(target.id);if(existing==='founder')return reply(res,403,{error:'protected_target'});
  const quota=ROLES[a.role].quota?.[role];if(quota!=null&&countAssignedBy(a.user.id,role)>=quota&&existing!==role)return reply(res,409,{error:'appointment_quota_exceeded',quota});
  if(role==='admin_member'&&DB.prepare('SELECT COUNT(*) n FROM admin_roles WHERE role=?').get(role).n>=10&&!existing)return reply(res,409,{error:'admin_member_limit'});
  DB.prepare('DELETE FROM admin_roles WHERE user_id=?').run(target.id);
  const code=newCode(),expires=new Date(Date.now()+YEAR).toISOString();
  DB.prepare('INSERT INTO admin_roles(user_id,role,admin_code_hash,created_by,expires_at) VALUES(?,?,?,?,?)').run(target.id,role,hash(code),a.user.id,expires);
  const msg=`تم تعيينك ${ROLES[role].label} في وَثاق. مدة الاشتراك سنة واحدة. السعر السنوي ${ROLES[role].price}$ . رمز الدخول الإداري: ${code}`;
  await sendCoreMessage(a.token,target.wethaq_id,msg);
  return reply(res,201,{ok:true,user:target,role,role_label:ROLES[role].label,admin_code:code,expires_at:expires,period:'سنة واحدة',price_usd:ROLES[role].price,quota_remaining:quota==null?null:Math.max(0,quota-countAssignedBy(a.user.id,role))});
 }
 if(path==='/api/admin/remove-role'&&req.method==='POST'){
  if(a.role!=='founder')return reply(res,403,{error:'founder_only'});
  const b=await readBody(req),target=DB.prepare('SELECT id,wethaq_id FROM users WHERE wethaq_id=?').get(safe(b.wethaqId));if(!target)return reply(res,404,{error:'user_not_found'});
  const old=DB.prepare('SELECT role FROM admin_roles WHERE user_id=?').get(target.id);DB.prepare('DELETE FROM admin_roles WHERE user_id=?').run(target.id);await sendCoreMessage(a.token,target.wethaq_id,`تمت إزالة صفتك الإدارية ${old?.role&&ROLES[old.role]?ROLES[old.role].label:''} في وَثاق من قبل المؤسس.`);return reply(res,200,{ok:true});
 }
 if(path==='/api/admin/verify-code'&&req.method==='POST'){
  const b=await readBody(req),row=DB.prepare('SELECT role,admin_code_hash,expires_at FROM admin_roles WHERE user_id=?').get(a.user.id);if(!row||!ROLES[row.role]||hash(safe(b.code).toUpperCase())!==row.admin_code_hash||row.expires_at&&new Date(row.expires_at)<=new Date())return reply(res,403,{error:'invalid_admin_code'});return reply(res,200,{ok:true,role:row.role,role_label:ROLES[row.role].label,expires_at:row.expires_at});
 }
 if(path==='/api/admin/rbac/ban'&&req.method==='POST'){
  if(!a.role)return reply(res,403,{error:'admin_forbidden'});
  const b=await readBody(req),target=DB.prepare('SELECT id,wethaq_id,name FROM users WHERE wethaq_id=?').get(safe(b.wethaqId));if(!target)return reply(res,404,{error:'user_not_found'});
  const targetRole=roleOf(target.id),minutes=Math.max(0,Number(b.minutes||0)),reason=safe(b.reason)||'بقرار من إدارة وَثاق';
  if(!canBan(a.role,targetRole))return reply(res,403,{error:'insufficient_role'});
  if(a.role==='admin_member'||a.role==='supervisor'){if(minutes<=0)return reply(res,403,{error:'temporary_ban_only'});}
  if(a.role==='premium')return reply(res,403,{error:'alert_only'});
  DB.prepare('DELETE FROM bans WHERE user_id=?').run(target.id);const type=banType(a.role,minutes),expires=type==='temporary'?new Date(Date.now()+minutes*60000).toISOString():null;DB.prepare('INSERT INTO bans(user_id,ban_type,expires_at,reason,created_by) VALUES(?,?,?,?,?)').run(target.id,type,expires,reason,a.user.id);
  await sendCoreMessage(a.token,target.wethaq_id,`تم حظرك من قبل ${ROLES[a.role].label}. النوع: ${type==='temporary'?'مؤقت':'نهائي'}. السبب: ${reason}`);return reply(res,200,{ok:true,ban_type:type,expires_at:expires});
 }
 if(path==='/api/admin/alert'&&req.method==='POST'){
  if(!a.role)return reply(res,403,{error:'admin_forbidden'});const b=await readBody(req),target=DB.prepare('SELECT id,wethaq_id,name FROM users WHERE wethaq_id=?').get(safe(b.wethaqId));if(!target)return reply(res,404,{error:'user_not_found'});const targetRole=roleOf(target.id);if(targetRole==='founder'||rank(targetRole)>=rank(a.role))return reply(res,403,{error:'protected_target'});const reason=safe(b.reason)||'تنبيه من إدارة وَثاق';await sendCoreMessage(a.token,target.wethaq_id,`⚠️ تم تنبيهك من قبل ${ROLES[a.role].label}. ${reason}`);return reply(res,200,{ok:true});
 }
 if(path==='/api/admin/unban'&&req.method==='POST'){
  if(a.role!=='founder'&&a.role!=='executive'&&a.role!=='deputy1'&&a.role!=='deputy2'&&a.role!=='deputy3')return reply(res,403,{error:'insufficient_role'});
  const b=await readBody(req),target=DB.prepare('SELECT id,wethaq_id FROM users WHERE wethaq_id=?').get(safe(b.wethaqId));if(!target)return reply(res,404,{error:'user_not_found'});
  const tr=roleOf(target.id);if(tr==='founder'||rank(tr)>=rank(a.role))return reply(res,403,{error:'protected_target'});
  DB.prepare('DELETE FROM bans WHERE user_id=?').run(target.id);await sendCoreMessage(a.token,target.wethaq_id,`✅ تم إلغاء الحظر عن حسابك من قبل ${ROLES[a.role].label}.`);return reply(res,200,{ok:true});
 }
 if(path==='/api/admin/audit-log'&&req.method==='GET'){
  if(a.role!=='founder')return reply(res,403,{error:'founder_only'});const logs=DB.prepare(`SELECT a.id,a.action,a.metadata,a.created_at,u.name actor_name,t.name target_name FROM audit_logs a LEFT JOIN users u ON u.id=a.actor_user_id LEFT JOIN users t ON t.id=a.target_user_id ORDER BY a.id DESC LIMIT 200`).all();return reply(res,200,{logs});
 }
 return reply(res,404,{error:'not_found'});
}

async function handle(req,res){
 const path=new URL(req.url,`http://${req.headers.host||'localhost'}`).pathname;
 if(path==='/health'&&req.method==='GET')return reply(res,200,{ok:true,service:'wethaq',version:'7.0.0',auth:'device_bound_name_birth_year',search:'public',websocket:'/ws',audio:true,image:true,avatar:true,video:true,contacts:true,notifications:true,rbac:true,subscription:true,executive:true,supervisor:true,premium:true,time:now()});
 if(path==='/api/public/administration'&&req.method==='GET')return reply(res,200,publicRows());
 if(path.startsWith('/api/admin/'))return adminRoute(req,res);
 if(path==='/api/login'&&req.method==='POST'){
  let raw='';try{raw=await new Promise((resolve,reject)=>{let b='';req.on('data',c=>b+=c);req.on('end',()=>resolve(b));req.on('error',reject)});}catch{return reply(res,400,{error:'invalid_request'})}
  const r=await coreFetch(path,{method:'POST',headers:{...req.headers,host:undefined},body:raw});const body=await json(r);const user=body?.user;const actual=user?roleOf(Number(user.id)):null;
  if(r.ok&&actual){body.actual_role=actual;body.actual_role_label=ROLES[actual].label;body.role=['founder','deputy1','deputy2','deputy3','admin_member'].includes(actual)?actual:'admin_member';body.admin_notice=`تم تفعيل ${ROLES[actual].label} في وَثاق.`;}
  return reply(res,r.status,body);
 }
 const upstream=await coreFetch(req.url,{method:req.method,headers:{...req.headers,host:undefined},body:['GET','HEAD'].includes(req.method)?undefined:await new Promise(resolve=>{let b=[];req.on('data',c=>b.push(c));req.on('end',()=>resolve(Buffer.concat(b)))}).catch(()=>undefined)});
 res.writeHead(upstream.status,Object.fromEntries(upstream.headers.entries()));res.end(Buffer.from(await upstream.arrayBuffer()));
}

function startCore(){const child=spawn(process.execPath,['server.js'],{cwd:process.cwd(),env:{...process.env,PORT:String(UPSTREAM_PORT)}});child.stdout.on('data',d=>process.stdout.write(`[core] ${d}`));child.stderr.on('data',d=>process.stderr.write(`[core] ${d}`));child.on('exit',code=>{if(code!==0){console.error(`Wethaq core exited with ${code}`);process.exit(code??1)}});return child;}
const core=startCore();
const server=http.createServer((req,res)=>handle(req,res).catch(e=>reply(res,500,{error:'proxy_failure',detail:String(e.message||e)})));
server.on('upgrade',(req,socket,head)=>{
 const target=net.connect(UPSTREAM_PORT,HOST,()=>{
  let requestLine=`${req.method} ${req.url} HTTP/${req.httpVersion}\r\n`;let headers='';for(let i=0;i<req.rawHeaders.length;i+=2){headers+=`${req.rawHeaders[i]}: ${req.rawHeaders[i+1]}\r\n`;}target.write(requestLine+headers+'\r\n');if(head?.length)target.write(head);socket.pipe(target).pipe(socket);
 });target.on('error',()=>socket.destroy());socket.on('error',()=>target.destroy());
});
server.listen(PUBLIC_PORT,'0.0.0.0',()=>console.log(`Wethaq v7 proxy listening on ${PUBLIC_PORT}, core on ${UPSTREAM_PORT}`));
process.on('SIGTERM',()=>{server.close();core.kill('SIGTERM');DB.close()});
process.on('SIGINT',()=>{server.close();core.kill('SIGINT');DB.close()});
