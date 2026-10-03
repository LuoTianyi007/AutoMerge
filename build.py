"""Build a signed local APK without Gradle. Python 3 + JDK 17 + Android SDK 35.
Usage: python build.py --tools /path/to/tools --out /path/to/AutoMerge.apk
Expected tools: jdk/*/bin/java.exe, platform/*/android.jar,
buildtools/*/aapt2.exe, ffmpeg.aar, smart.jar, smart-common.jar. See README.md.
"""
import argparse, hashlib, json, os, pathlib, shutil, struct, subprocess, zipfile

p=argparse.ArgumentParser()
p.add_argument('--tools',required=True)
p.add_argument('--out',required=True)
p.add_argument('--keystore',help='Use an existing signing key for upgrades')
args=p.parse_args()
root=pathlib.Path(__file__).resolve().parent
tools=pathlib.Path(args.tools).resolve()
out=pathlib.Path(args.out).resolve()
work=tools.parent/'apk-build-publication-1.3'
work.mkdir(parents=True,exist_ok=True)
def find(folder,name):
    hits=list((tools/folder).rglob(name))
    if not hits: raise RuntimeError(f'Missing {folder}/{name}')
    return hits[0]
java=find('jdk','java.exe'); javac=java.with_name('javac.exe')
keytool=java.with_name('keytool.exe'); bt=find('buildtools','aapt2.exe').parent
android=find('platform','android.jar')
env=dict(os.environ);env['JAVA_HOME']=str(java.parent.parent);env['PATH']=str(java.parent)+os.pathsep+env.get('PATH','')
def run(*cmd):
    print('Running',pathlib.Path(str(cmd[0])).name,flush=True)
    subprocess.run([str(x) for x in cmd],env=env,check=True)
lock=json.loads((root/'dependency-manifest.json').read_text(encoding='utf-8'))
for name,item in lock['binaries'].items():
    if hashlib.sha256((tools/name).read_bytes()).hexdigest()!=item['sha256']:
        raise RuntimeError('Dependency hash mismatch: '+name)
print('PASS: pinned binary dependency hashes',flush=True)
native=work/'ffmpeg'; native.mkdir(exist_ok=True)
with zipfile.ZipFile(tools/'ffmpeg.aar') as z: z.extractall(native)
app=root/'app/src/main'; generated=work/'generated';generated.mkdir(exist_ok=True)
classes=work/'classes';classes.mkdir(exist_ok=True)
dex=work/'dex';dex.mkdir(exist_ok=True)
reszip=work/'res.zip'; unsigned=work/'unsigned.apk'
run(bt/'aapt2.exe','compile','--dir',app/'res','-o',reszip)
libraryres=work/'library-res.zip'
run(bt/'aapt2.exe','compile','--dir',native/'res','-o',libraryres)
run(bt/'aapt2.exe','link','-I',android,'--manifest',app/'AndroidManifest.xml','--java',generated,'--extra-packages','com.arthenica.ffmpegkit','-o',unsigned,reszip,libraryres)
sources=list((app/'java').rglob('*.java'))+list(generated.rglob('*.java'))
dependencies=[native/'classes.jar',tools/'smart.jar',tools/'smart-common.jar']
cp=os.pathsep.join(map(str,[android,*dependencies]))
# Check all third-party Java class references, including transitive dependencies.
audit=subprocess.run([str(java.with_name('jdeps.exe')),'--missing-deps','--class-path',cp,*map(str,dependencies)],env=env,check=True,capture_output=True,encoding='utf-8',errors='replace')
if ' -> ' in audit.stdout: raise RuntimeError('Missing runtime dependencies:\n'+audit.stdout)
print('PASS: third-party runtime class dependency closure',flush=True)
run(javac,'-encoding','UTF-8','-source','8','-target','8','-classpath',cp,'-d',classes,*sources)
testclasses=work/'tests';testclasses.mkdir(exist_ok=True)
run(javac,'-encoding','UTF-8','-d',testclasses,app/'java/cn/local/automerge/Pairing.java',root/'tests/PairingTest.java')
run(java,'-cp',testclasses,'cn.local.automerge.PairingTest')
run(javac,'-encoding','UTF-8','-classpath',testclasses,'-d',testclasses,app/'java/cn/local/automerge/CleanupPlan.java',root/'tests/CleanupTest.java')
run(java,'-cp',testclasses,'cn.local.automerge.CleanupTest')
smokecp=os.pathsep.join(map(str,[testclasses,tools/'smart.jar',tools/'smart-common.jar']))
run(javac,'-encoding','UTF-8','-classpath',smokecp,'-d',testclasses,app/'java/cn/local/automerge/ErrorReport.java',root/'tests/DependencySmokeTest.java')
# Negative control: reproduce the old missing-common-library initialization error.
broken=subprocess.run([str(java),'-cp',os.pathsep.join(map(str,[testclasses,tools/'smart.jar'])),'cn.local.automerge.DependencySmokeTest'],env=env,capture_output=True)
if broken.returncode==0 or b'NoClassDefFoundError' not in broken.stderr: raise RuntimeError('Regression negative control did not reproduce the old error')
print('PASS: old missing-library initialization error reproduced by negative control',flush=True)
run(java,'-cp',smokecp,'cn.local.automerge.DependencySmokeTest')
classjar=work/'app.jar'
with zipfile.ZipFile(classjar,'w',zipfile.ZIP_DEFLATED) as z:
    for file in classes.rglob('*.class'): z.write(file,file.relative_to(classes).as_posix())
run(java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','26','--lib',android,'--output',dex,classjar,*dependencies)
# Verify that these are class definitions in the final Dex, not just references.
definitions=set()
for dexfile in dex.glob('*.dex'):
    data=dexfile.read_bytes()
    sn,so=struct.unpack_from('<II',data,56);tn,to=struct.unpack_from('<II',data,64);cn,co=struct.unpack_from('<II',data,96)
    strings=[]
    for index in range(sn):
        pos=struct.unpack_from('<I',data,so+index*4)[0]
        while data[pos]&128:pos+=1
        pos+=1;end=data.index(0,pos);strings.append(data[pos:end].decode('utf-8',errors='replace'))
    types=[strings[struct.unpack_from('<I',data,to+i*4)[0]] for i in range(tn)]
    for index in range(cn):definitions.add(types[struct.unpack_from('<I',data,co+index*32)[0]])
required={'Lcom/arthenica/ffmpegkit/FFmpegKitConfig;','Lcom/arthenica/smartexception/java/Exceptions;','Lcom/arthenica/smartexception/AbstractExceptions;','Lcom/arthenica/smartexception/ClassLoader;','Lcom/arthenica/smartexception/PackageLoader;','Lcom/arthenica/smartexception/ThrowableWrapper;','Lcom/arthenica/smartexception/StackTraceElementSerializer;'}
if required-definitions:raise RuntimeError('Final Dex is missing required classes: '+str(required-definitions))
print('PASS: required FFmpeg initialization classes defined in packaged Dex',flush=True)
with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:
    for file in dex.glob('*.dex'): z.write(file,file.name)
    for abi in ('arm64-v8a','armeabi-v7a'):
        libs=list((native/'jni'/abi).glob('*.so'))
        if not libs: raise RuntimeError('FFmpeg AAR has no native libraries for '+abi)
        for lib in libs:z.write(lib,'lib/'+abi+'/'+lib.name)
    z.writestr('assets/THIRD_PARTY.md',(root/'THIRD_PARTY.md').read_text(encoding='utf-8'))
    z.writestr('assets/APP_LICENSE.txt',(root/'LICENSE').read_text(encoding='utf-8'))
    for folder in ('assets',):
        if (native/folder).exists():
            for file in (native/folder).rglob('*'):
                if file.is_file():z.write(file,file.relative_to(native).as_posix())
aligned=work/'aligned.apk'
run(bt/'zipalign.exe','-f','4',unsigned,aligned)
keystore=pathlib.Path(args.keystore).resolve() if args.keystore else work/'local-signing.jks'
if not keystore.exists():
    run(keytool,'-genkeypair','-keystore',keystore,'-storepass','android','-keypass','android','-alias','automerge','-dname','CN=AutoMerge Local Build','-keyalg','RSA','-keysize','2048','-validity','10000')
out.parent.mkdir(parents=True,exist_ok=True)
# Feed OS cryptographic entropy explicitly: some restricted Windows runtimes
# otherwise fall back to the very slow threaded Java entropy generator.
seed=work/'signing-entropy.bin'
seed.write_bytes(os.urandom(65536))
try:
    run(java,'-Djava.security.egd='+seed.as_uri(),'-jar',bt/'lib/apksigner.jar','sign','--ks',keystore,'--ks-key-alias','automerge','--ks-pass','pass:android','--key-pass','pass:android','--out',out,aligned)
finally:
    seed.unlink(missing_ok=True)
run(java,'-jar',bt/'lib/apksigner.jar','verify','--verbose',out)
run(bt/'aapt2.exe','dump','badging',out)
print('APK:',out,'bytes:',out.stat().st_size,'sha256:',hashlib.sha256(out.read_bytes()).hexdigest(),flush=True)
