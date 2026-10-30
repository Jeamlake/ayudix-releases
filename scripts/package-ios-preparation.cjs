// Only reviewed preparation sources. No local state, release binaries or old bot copies.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{zipSync}=require('fflate');
const root=path.resolve(__dirname,'..');
const roots=['mobile-shared/core/src','mobile-shared/ui/src','mobile-shared/gradle','mobile-ios/Sources','mobile-ios/Resources','mobile-ios/Tests','scripts/ios','docs/ios'];
const singles=['LICENSE','.gitattributes','assets/ayudix-link.svg','mobile-shared/.gitignore','mobile-ios/.gitignore','mobile-shared/README.md','mobile-shared/settings.gradle.kts','mobile-shared/build.gradle.kts','mobile-shared/gradle.properties','mobile-shared/gradlew','mobile-shared/gradlew.bat','mobile-shared/core/build.gradle.kts','mobile-shared/ui/build.gradle.kts','mobile-ios/README.md','mobile-ios/project.yml','scripts/test-mobile-shared.ps1','scripts/verify-ios-preparation.py','scripts/package-ios-preparation.cjs','scripts/publish-ios-preparation.cjs','.github/workflows/ayudix-ios.yml'];
const skip=new Set(['build','.gradle','.kotlin','.swiftpm','DerivedData','xcuserdata']);
const files=[],zip={};
function add(relative){
 const name=relative.replaceAll('\\','/'),file=path.join(root,name),stat=fs.lstatSync(file);
 if(stat.isSymbolicLink()||!stat.isFile())throw Error('Only regular source files: '+name);
 if(/(?:^|\/)(?:\.env|auth\.json|settings\.json|local\.properties)$|\.(?:dpapi|p12|jks|pem|sqlite3?|mobileprovision|provisionprofile|ipa|exe|dll)$/i.test(name))throw Error('Private/build artifact rejected: '+name);
 const bytes=fs.readFileSync(file);
 if(bytes.length>3*1024*1024)throw Error('Unexpected source size: '+name);
 if(!/\.(png|jar)$/.test(name)){
  const text=bytes.toString('utf8');
  if(/gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,}|-----BEGIN (?:EC |RSA |OPENSSH )?PRIVATE KEY-----/.test(text))throw Error('Potential secret: '+name);
  if(/C:[\\/]Users[\\/]/i.test(text))throw Error('Personal profile path in export: '+name);
 }
 zip['Ayudix-iOS-Preparation-0.1.0/'+name]=bytes;
 files.push({file:name,size:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex')});
}
function walk(relative){for(const item of fs.readdirSync(path.join(root,relative),{withFileTypes:true})){const name=path.join(relative,item.name);if(item.isDirectory()){if(!skip.has(item.name)&&!item.name.endsWith('.xcodeproj')&&!item.name.endsWith('.xcworkspace'))walk(name)}else add(name)}}
for(const name of roots)walk(name);for(const name of singles)add(name);
const out=path.join(root,'release/ios-preparation');fs.mkdirSync(out,{recursive:true});
const archive=path.join(out,'Ayudix-iOS-Preparation-0.1.0-SOURCES.zip');fs.writeFileSync(archive,zipSync(zip,{level:6}));
const audit={schema:1,kind:'sources-not-an-installed-app',files,archive,sha256:crypto.createHash('sha256').update(fs.readFileSync(archive)).digest('hex')};
fs.writeFileSync(path.join(out,'source-audit.json'),JSON.stringify(audit,null,2));
console.log(JSON.stringify({ok:true,files:files.length,archive,sha256:audit.sha256}));
