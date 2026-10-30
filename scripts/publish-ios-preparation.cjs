// Publishes an audited, separate draft branch in the existing public repository.
// It never commits the dirty local index and never activates a workflow implicitly.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..'),repository='Jeamlake/ayudix-releases',branch='ios/preparation-2026-10-30';
const logs=process.env.AYUDIX_PREPARATION_LOGS||'C:/Desarrollo/Logs/Ayudix/iOS-Preparacion-2026-10-30';
const gh=process.env.AYUDIX_GH||'C:/Program Files/GitHub CLI/gh.exe';
fs.mkdirSync(logs,{recursive:true});
let requestId=0;
function api(endpoint,body){
 const args=['api',endpoint];
 if(body){const file=path.join(logs,`github-request-${++requestId}.json`);fs.writeFileSync(file,JSON.stringify(body));args.push('--method','POST','--input',file)}
 return JSON.parse(cp.execFileSync(gh,args,{encoding:'utf8',windowsHide:true,maxBuffer:8*1024*1024}));
}
function digest(bytes){return crypto.createHash('sha256').update(bytes).digest('hex')}
function gitDigest(bytes){return crypto.createHash('sha1').update(Buffer.from(`blob ${bytes.length}\0`)).update(bytes).digest('hex')}
const audit=JSON.parse(fs.readFileSync(path.join(root,'release/ios-preparation/source-audit.json')));
const verification=JSON.parse(fs.readFileSync(path.join(logs,'verification.json')));
if(!verification.ok||verification.sharedJvmTests<22||verification.failures||verification.errors)throw Error('Preparation verification required');
if(digest(fs.readFileSync(audit.archive))!==audit.sha256)throw Error('Source archive changed after review');
const files=audit.files.map(item=>{
 const bytes=fs.readFileSync(path.join(root,item.file));
 if(bytes.length!==item.size||digest(bytes)!==item.sha256)throw Error('Source changed after review: '+item.file);
 // With no workflow scope this remains a document, not an executable GitHub workflow.
 const remote=item.file==='.github/workflows/ayudix-ios.yml'?'docs/ios/ayudix-ios.workflow.yml':item.file;
 return {...item,remote,bytes,gitSha:gitDigest(bytes)};
});
if(files.some(f=>f.remote.startsWith('.github/workflows/')))throw Error('Workflow activation is outside this preparation publication');
if(!process.argv.includes('--publish')){console.log(JSON.stringify({prepared:true,published:false,repository,branch,files:files.length,workflowActivated:false}));process.exit(0)}
const repo=api(`repos/${repository}`);
if(repo.private||repo.owner.login!=='Jeamlake'||!repo.permissions?.push)throw Error('The authorized public repository is required');
const existing=api(`repos/${repository}/branches?per_page=100`);
if(existing.some(b=>b.name===branch))throw Error('Preparation branch already exists; do not replace it');
const reference=api(`repos/${repository}/git/ref/heads/${repo.default_branch}`);
const parent=api(`repos/${repository}/git/commits/${reference.object.sha}`);
const tree=api(`repos/${repository}/git/trees/${parent.tree.sha}?recursive=1`);
for(const file of files){const found=tree.tree.find(e=>e.path===file.remote);if(found&&found.sha!==file.gitSha)throw Error('Remote source collision; review first: '+file.remote)}
const entries=[];
for(const file of files){
 if(/\.(png|jar)$/.test(file.file)){
  const blob=api(`repos/${repository}/git/blobs`,{content:file.bytes.toString('base64'),encoding:'base64'});
  if(blob.sha!==file.gitSha)throw Error('Binary blob mismatch: '+file.remote);
  entries.push({path:file.remote,mode:'100644',type:'blob',sha:blob.sha});
 }else entries.push({path:file.remote,mode:'100644',type:'blob',content:file.bytes.toString('utf8')});
}
const added=api(`repos/${repository}/git/trees`,{base_tree:parent.tree.sha,tree:entries});
const committed=api(`repos/${repository}/git/commits`,{message:'Preparar cliente iOS y módulos móviles compartidos de Ayudix',tree:added.sha,parents:[parent.sha]});
api(`repos/${repository}/git/refs`,{ref:`refs/heads/${branch}`,sha:committed.sha});
const uploaded=api(`repos/${repository}/git/trees/${added.sha}?recursive=1`);
for(const file of files)if(uploaded.tree.find(e=>e.path===file.remote)?.sha!==file.gitSha)throw Error('Published source mismatch: '+file.remote);
const body=[
 'Añade la preparación de Ayudix para iPhone/iPad al mismo repositorio, con carpetas `mobile-ios` y `mobile-shared` y scripts de compilación/prueba.',
 '',
 'Implementa contratos del servidor Windows, registro propio, identidad de PC, carpetas y chat; prepara Keychain y cámara en Swift. El cliente Android existente todavía no consume estos módulos.',
 '',
 'Validación: 22 pruebas JVM pasadas, interfaz compartida compilada y renderizada sin ventanas, YAML/plist y sintaxis Bash comprobados. Swift/UIKit, Xcode, simulador Apple, firma y teléfono físico siguen pendientes. No hay un IPA firmado ni instalación acreditada.',
 '',
 'El workflow se aporta como documento inerte en `docs/ios/ayudix-ios.workflow.yml`: el acceso actual carece de `workflow`. No se añadió un archivo ejecutable de Actions ni se amplió el permiso por otra vía. Tras la autorización oficial puede revisarse y colocarse en `.github/workflows/ayudix-ios.yml`.',
 '',
 'Las fuentes proceden de una lista explícita revisada. No se publicó el índice local con cambios históricos, ni perfiles, bots, sesiones, credenciales, datos de teléfonos o instaladores Windows/Android. Las Releases y la rama principal conservan su estado.',
].join('\n');
const pr=api(`repos/${repository}/pulls`,{title:'Preparar Ayudix para iOS en el mismo repositorio',head:branch,base:repo.default_branch,draft:true,body});
const result={ok:true,repository,branch,commit:committed.sha,pullRequest:pr.html_url,draft:true,files:files.length,publicHashesVerified:true,workflowActivated:false};
fs.writeFileSync(path.join(logs,'github-preparation.json'),JSON.stringify(result,null,2));
console.log(JSON.stringify(result));
