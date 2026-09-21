import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const backendDir=path.dirname(fileURLToPath(import.meta.url));
const sourcePath=path.join(backendDir,'server.js');
const source=fs.readFileSync(sourcePath,'utf8');
const marker="server.listen(PORT,'0.0.0.0',()=>console.log(`Wethaq backend listening on ${PORT}`));";
const injectionPath=path.join(backendDir,'production-injection.mjsinc');
const injected=fs.readFileSync(injectionPath,'utf8');

async function verifyRailwayVolumeRuntime(){
  const mount=String(process.env.RAILWAY_VOLUME_MOUNT_PATH||'').trim().replace(/\/$/,'');
  if(!mount){console.log('WETHAQ_VOLUME_RW_SKIP no Railway volume mount');return;}
  const testPath=path.join(mount,'.wethaq-volume-rw-check-'+process.pid+'-'+Date.now()+'.sqlite');
  let testDb=null;
  try{
    fs.mkdirSync(mount,{recursive:true});
    const mod=await import('better-sqlite3');
    testDb=new mod.default(testPath);
    testDb.exec('CREATE TABLE IF NOT EXISTS rw_test(id INTEGER PRIMARY KEY,value TEXT NOT NULL)');
    testDb.prepare('INSERT INTO rw_test(value) VALUES(?)').run('wethaq-volume-rw-ok');
    const row=testDb.prepare('SELECT value FROM rw_test ORDER BY id DESC LIMIT 1').get();
    if(!row||row.value!=='wethaq-volume-rw-ok')throw new Error('SQLite read-back mismatch');
    console.log('WETHAQ_VOLUME_RW_OK mount='+mount+' db='+path.join(mount,'wethaq.db'));
  }catch(error){console.error('WETHAQ_VOLUME_RW_FAIL',error?.message||error);throw error;}
  finally{try{testDb?.close();}catch{}try{fs.rmSync(testPath,{force:true});}catch{}}
}
await verifyRailwayVolumeRuntime();
if(!source.includes(marker))throw new Error('WETHAQ production start marker not found');
const runtimeSource=source.replace(marker,injected+'\n'+marker);
// يُحفظ الملف المؤقت داخل backend حتى تتبع وحدات جافاسكربت شجرة node_modules المحلية.
const runtimePath=path.join(backendDir,`.wethaq-server-runtime-${process.pid}.mjs`);
fs.writeFileSync(runtimePath,runtimeSource,'utf8');
try{
  await import(pathToFileURL(runtimePath).href);
}finally{
  try{fs.rmSync(runtimePath,{force:true});}catch{}
}
