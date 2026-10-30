// Audited-source sync and an explicit, read-only manual macOS build in the existing repo.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..'),repo='Jeamlake/ayudix-releases',branch='ios/preparation-2026-10-30';
const logs=process.env.AYUDIX_PREPARATION_LOGS||'C:/Desarrollo/Logs/Ayudix/iOS-Preparacion-2026-10-30';
const gh=process.env.AYUDIX_GH||'C:/Program Files/GitHub CLI/gh.exe';
const args=new Set(process.argv.slice(2));
for(const flag of args)if(!['--sync','--activate','--run','--status'].includes(flag))throw Error('Unknown operation: '+flag);
fs.mkdirSync(logs,{recursive:true});let n=0;
function call(endpoint,body,method){
 const request=['api',endpoint];
 if(method||body)request.push('--method',method||'POST');
 if(body){const file=path.join(logs,'ios-operation-'+Date.now()+'-'+(++n)+'.json');fs.writeFileSync(file,JSON.stringify(body));request.push('--input',file)}
 const out=cp.execFileSync(gh,request,{encoding:'utf8',windowsHide:true,maxBuffer:10*1024*1024});
 return out.trim()?JSON.parse(out):null;
}
function hash(bytes,kind='sha256'){return crypto.createHash(kind).update(bytes).digest('hex')}
function gitHash(bytes){return crypto.createHash('sha1').update(Buffer.from(`blob ${bytes.length}\0`)).update(bytes).digest('hex')}
const publicationFile=path.join(logs,'github-preparation.json');
const publication=JSON.parse(fs.readFileSync(publicationFile));
const metadata=call(`repos/${repo}`);
if(metadata.private||metadata.owner.login!=='Jeamlake'||!metadata.permissions?.push)throw Error('Expected authorized public repository');
if(args.has('--sync')){
 const audit=JSON.parse(fs.readFileSync(path.join(root,'release/ios-preparation/source-audit.json')));
 const verification=JSON.parse(fs.readFileSync(path.join(logs,'verification.json')));
 if(!verification.ok||verification.sharedJvmTests<22||verification.failures||verification.errors)throw Error('Local validation required');
 if(hash(fs.readFileSync(audit.archive))!==audit.sha256)throw Error('Reviewed source archive mismatch');
 const ref=call(`repos/${repo}/git/ref/heads/${branch}`);
 if(ref.object.sha!==publication.commit)throw Error('Source branch changed outside this operation; inspect before syncing');
 const commit=call(`repos/${repo}/git/commits/${ref.object.sha}`);
 const tree=call(`repos/${repo}/git/trees/${commit.tree.sha}?recursive=1`);
 const updates=[];const expected=[];
 for(const source of audit.files){
  const bytes=fs.readFileSync(path.join(root,source.file));
  if(bytes.length!==source.size||hash(bytes)!==source.sha256)throw Error('Source changed after review: '+source.file);
  const remote=source.file==='.github/workflows/ayudix-ios.yml'?'docs/ios/ayudix-ios.workflow.yml':source.file;
  if(remote.startsWith('.github/workflows/'))throw Error('Source sync cannot activate workflows');
  const sha=gitHash(bytes);expected.push({remote,sha});
  if(tree.tree.find(e=>e.path===remote)?.sha===sha)continue;
  if(/\.(png|jar)$/.test(remote)){
   const blob=call(`repos/${repo}/git/blobs`,{content:bytes.toString('base64'),encoding:'base64'});
   if(blob.sha!==sha)throw Error('Binary blob mismatch');
   updates.push({path:remote,mode:'100644',type:'blob',sha});
  }else updates.push({path:remote,mode:'100644',type:'blob',content:bytes.toString('utf8')});
 }
 if(updates.length){
  const added=call(`repos/${repo}/git/trees`,{base_tree:commit.tree.sha,tree:updates});
  const next=call(`repos/${repo}/git/commits`,{message:'Preparar compilación remota de iOS por commit y firma de simulador',tree:added.sha,parents:[commit.sha]});
  call(`repos/${repo}/git/refs/heads/${branch}`,{sha:next.sha,force:false},'PATCH');
  publication.commit=next.sha;publication.files=expected.length;
  fs.writeFileSync(publicationFile,JSON.stringify(publication,null,2));
 }
 const checked=call(`repos/${repo}/git/trees/${publication.commit}?recursive=1`);
 for(const file of expected)if(checked.tree.find(e=>e.path===file.remote)?.sha!==file.sha)throw Error('Remote source hash mismatch: '+file.remote);
 console.log(JSON.stringify({synced:true,sourceCommit:publication.commit,changedFiles:updates.length,publicHashesVerified:true}));
}
const workflowFile=path.join(root,'.github/workflows/ayudix-ios.yml');
const workflow=fs.readFileSync(workflowFile);
const text=workflow.toString('utf8');
if(!text.includes('workflow_dispatch:')||!text.includes('source_ref:')||!text.includes('runs-on: macos-26')||!text.includes('github.event.repository.private == false')||!text.includes('contents: read')||text.includes('secrets.'))throw Error('Workflow must remain manual, public-runner-only and credential-free');
const activationFile=path.join(logs,'workflow-activation.json');
if(args.has('--activate')){
 const headers=cp.execFileSync(gh,['api','--include','user'],{encoding:'utf8',windowsHide:true});
 if(!/^x-oauth-scopes:.*\bworkflow\b/im.test(headers))throw Error('Complete the official GitHub workflow authorization before activation');
 const currentTree=call(`repos/${repo}/git/trees/${metadata.default_branch}?recursive=1`);
 const current=currentTree.tree.find(e=>e.path==='.github/workflows/ayudix-ios.yml');
 const wanted=gitHash(workflow);
 const prior=fs.existsSync(activationFile)?JSON.parse(fs.readFileSync(activationFile)):null;
 if(current&&current.sha!==wanted&&current.sha!==prior?.workflowGitSha)throw Error('The default-branch workflow changed; inspect before replacing');
 let activation=prior;
 if(current?.sha!==wanted){
  const result=call(`repos/${repo}/contents/.github/workflows/ayudix-ios.yml`,{message:'Activar compilación manual de Ayudix iOS desde fuentes revisadas',content:workflow.toString('base64'),branch:metadata.default_branch,...(current?{sha:current.sha}:{})},'PUT');
  if(result.content.sha!==wanted)throw Error('Activated workflow hash mismatch');
  activation={workflowGitSha:wanted,commit:result.commit.sha,repository:repo,workflow:'.github/workflows/ayudix-ios.yml',sourceBranch:branch,sourceCommit:publication.commit};
  fs.writeFileSync(activationFile,JSON.stringify(activation,null,2));
 }
 console.log(JSON.stringify({activated:true,workflowCommit:activation?.commit,workflowGitSha:wanted}));
}
const runFile=path.join(logs,'workflow-run.json');
if(args.has('--run')){
 const head=call(`repos/${repo}/git/ref/heads/${branch}`);
 if(head.object.sha!==publication.commit)throw Error('Source branch changed; verify the new commit before dispatch');
 const active=call(`repos/${repo}/contents/.github/workflows/ayudix-ios.yml?ref=${metadata.default_branch}`);
 if(active.sha!==gitHash(workflow))throw Error('The activated workflow differs from the reviewed local definition');
 const started=new Date().toISOString();
 call(`repos/${repo}/actions/workflows/ayudix-ios.yml/dispatches`,{ref:metadata.default_branch,inputs:{source_ref:publication.commit}});
 fs.writeFileSync(runFile,JSON.stringify({dispatched:true,started,sourceCommit:publication.commit,repository:repo,workflowRef:metadata.default_branch},null,2));
 console.log(JSON.stringify({dispatched:true,sourceCommit:publication.commit,started}));
}
if(args.has('--status')){
 const saved=fs.existsSync(runFile)?JSON.parse(fs.readFileSync(runFile)):null;
 if(saved){
  const runs=call(`repos/${repo}/actions/workflows/ayudix-ios.yml/runs?event=workflow_dispatch&per_page=20`).workflow_runs;
  const matching=runs.filter(r=>r.display_title.includes(saved.sourceCommit)&&Date.parse(r.created_at)>=Date.parse(saved.started)-5000);
  const run=matching.sort((a,b)=>b.id-a.id)[0];
  if(run){const status={...saved,runId:run.id,url:run.html_url,status:run.status,conclusion:run.conclusion};fs.writeFileSync(runFile,JSON.stringify(status,null,2));console.log(JSON.stringify(status))}
  else console.log(JSON.stringify({...saved,status:'awaiting-github-run'}));
 }else console.log(JSON.stringify({dispatched:false,sourceCommit:publication.commit,workflowActivated:fs.existsSync(activationFile)}));
}
