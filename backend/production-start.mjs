import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const sourcePath=path.resolve(process.cwd(),'server.js');
const source=fs.readFileSync(sourcePath,'utf8');
const marker="server.listen(PORT,'0.0.0.0',()=>console.log(`Wethaq backend listening on ${PORT}`));";
const injectionPath=path.resolve(process.cwd(),'production-injection.mjsinc');
const injected=fs.readFileSync(injectionPath,'utf8');
if(!source.includes(marker))throw new Error('WETHAQ production start marker not found');
const runtimeSource=source.replace(marker,injected+'\n'+marker);
// يُحفظ الملف المؤقت داخل backend حتى تتبع وحدات جافاسكربت شجرة node_modules المحلية.
const runtimePath=path.join(path.dirname(sourcePath),`.wethaq-server-runtime-${process.pid}.mjs`);
fs.writeFileSync(runtimePath,runtimeSource,'utf8');
try{
  await import(pathToFileURL(runtimePath).href);
}finally{
  try{fs.rmSync(runtimePath,{force:true});}catch{}
}
