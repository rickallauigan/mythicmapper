#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Owned AVD only. Never selects a physical ADB target or wipes app/device data."""
import argparse, base64, hashlib, json, os, pathlib, re, shutil, subprocess, sys, time, uuid, zipfile
import xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
CONFIG=json.loads((ROOT/'tools/virtual-g8-avd.json').read_text())
WORK=ROOT/'app/build/virtual-g8'
SDK=pathlib.Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT',str(pathlib.Path.home()/'Android/Sdk'))))
PKG='io.github.rickallauigan.mythicmapper.debug'
ENV=dict(os.environ,ANDROID_AVD_HOME=str(WORK/'avd'))
def command(argv, **kw):
    shown=[str(x) if len(str(x))<200 else '<argument '+str(len(str(x)))+' bytes>' for x in argv]
    print('+ '+' '.join(shown),file=sys.stderr,flush=True)
    kw.setdefault("text",True)
    return subprocess.run(list(map(str,argv)),check=True,env=ENV,**kw)
def adb(*args,**kw):return command([SDK/'platform-tools/adb','-s',CONFIG['serial'],*args],**kw)
def output(*args):return adb(*args,capture_output=True).stdout.strip()
def save(name,data):
    WORK.mkdir(parents=True,exist_ok=True);(WORK/name).write_text(json.dumps(data,indent=2)+'\n')
def owned():
    state=json.loads((WORK/'owned-process.json').read_text())
    args=pathlib.Path('/proc')/str(state['pid'])/'cmdline'
    if not args.exists() or CONFIG['name'] not in args.read_bytes().decode(errors='replace'):
        raise RuntimeError('Recorded emulator process is no longer owned/alive')
def verify():
    if output('shell','getprop','ro.kernel.qemu')!='1':raise RuntimeError('Refusing non-emulator target')
    if output('emu','avd','name').splitlines()[0]!=CONFIG['name']:raise RuntimeError('Target is not this workflow AVD')
def doctor():
    required=[SDK/'platform-tools/adb',SDK/'emulator/emulator',SDK/'cmdline-tools/latest/bin/avdmanager']
    r={'host':os.uname().sysname,'arch':os.uname().machine,'tools':{str(p.relative_to(SDK)):p.exists()for p in required},'kvm_access':os.access('/dev/kvm',os.R_OK|os.W_OK),'image_installed':(SDK/'system-images/android-36/default/x86_64/system.img').exists(),'config':CONFIG}
    r['cpu_cores']=os.cpu_count();r['available_memory_kb']=next((int(line.split()[1])for line in pathlib.Path('/proc/meminfo').read_text().splitlines()if line.startswith('MemAvailable:')),0)
    r['free_storage_bytes']=shutil.disk_usage(ROOT).free
    r['ndk_versions']=[p.name for p in (SDK/'ndk').glob('*')]
    r['emulator_version']=command([SDK/'emulator/emulator','-version'],capture_output=True).stdout.splitlines()[0]
    command([SDK/'emulator/emulator','-accel-check']);save('doctor.json',r);print(json.dumps(r,indent=2))
    if not all(r['tools'].values()) or not r['kvm_access']:raise RuntimeError('Missing tools or KVM access; see doctor.json')
def create(install=False):
    image=SDK/'system-images/android-36/default/x86_64'
    if not (image/'system.img').exists():
        if not install:raise RuntimeError('Image missing: run setup --install-sdk (SDK licenses must be accepted)')
        command([SDK/'cmdline-tools/latest/bin/sdkmanager',CONFIG['image']])
    props=dict(line.split('=',1)for line in (image/'source.properties').read_text().splitlines()if'='in line)
    if props.get('Pkg.Revision')!=CONFIG['image_revision']:raise RuntimeError('Image revision differs from pin; review config rather than silently changing it')
    avd=WORK/'avd'/f"{CONFIG['name']}.avd";marker=avd/'.mythicmapper-owned'
    if avd.exists() and not marker.exists():raise RuntimeError('Refusing unrelated existing AVD')
    if not avd.exists():
        avd.parent.mkdir(parents=True,exist_ok=True)
        command([SDK/'cmdline-tools/latest/bin/avdmanager','create','avd','-n',CONFIG['name'],'-k',CONFIG['image'],'-p',avd,'--device','pixel_6'],input='no\n')
        marker.write_text('virtual-g8 schema 1\n')
        path=avd/'config.ini';s=path.read_text();updates={'hw.ramSize':str(CONFIG['ram_mb']),'hw.cpu.ncore':str(CONFIG['cores']),'hw.lcd.width':str(CONFIG['width']),'hw.lcd.height':str(CONFIG['height']),'hw.lcd.density':str(CONFIG['density']),'hw.initialOrientation':'landscape','disk.dataPartition.size':'4G','hw.keyboard':'yes','showDeviceFrame':'no','skin.name':'2280x1080','skin.dynamic':'yes'}
        lines=[line for line in s.splitlines()if line.split('=',1)[0]not in updates];path.write_text('\n'.join(lines+[f'{k}={v}'for k,v in updates.items()])+'\n')
    return avd
def wait():
    deadline=time.monotonic()+240
    while time.monotonic()<deadline:
        try:
            if output('shell','getprop','sys.boot_completed')=='1':verify();return
        except subprocess.CalledProcessError:pass
        time.sleep(2)
    raise RuntimeError('AVD boot timeout; inspect app/build/virtual-g8/emulator.log')
def boot(gui=False,gpu='auto'):
    create();state=WORK/'owned-process.json'
    devices=command([SDK/'platform-tools/adb','devices'],capture_output=True).stdout
    if CONFIG['serial']+'\t' in devices:
        if not state.exists():raise RuntimeError('ADB port already occupied by unowned emulator')
        owned();verify();wait();return
    port=CONFIG['serial'].split('-')[1]
    args=[SDK/'emulator/emulator','-avd',CONFIG['name'],'-port',port,'-memory',str(CONFIG['ram_mb']),'-cores',str(CONFIG['cores']),'-gpu',gpu,'-accel','on','-no-snapshot','-no-boot-anim','-no-audio']
    if not gui:args+=['-no-window']
    print('+ '+' '.join(map(str,args)),file=sys.stderr)
    with (WORK/'emulator.log').open('w')as log:
        p=subprocess.Popen(list(map(str,args)),env=ENV,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
    save('owned-process.json',{'pid':p.pid,'avd':CONFIG['name'],'serial':CONFIG['serial']});wait()
def build():command(['bash',ROOT/'gradlew',':app:assembleDebug'],cwd=ROOT)
def install():
    verify();apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    if not apk.exists():raise RuntimeError('Build first')
    if output('shell','getprop','ro.product.cpu.abi')!='x86_64':raise RuntimeError('Expected x86_64 AVD')
    with zipfile.ZipFile(apk)as package:
        libs=[name for name in package.namelist()if name.startswith('lib/x86_64/')]
        if 'lib/x86_64/libmythicmapper_core_jni.so'not in libs:raise RuntimeError('x86_64 Rust JNI library missing')
    adb('install','-r',apk)
    save('install.json',{'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'abi':'x86_64','native_libraries':libs,'target':CONFIG['serial']})
def run(path):
    verify();raw=pathlib.Path(path).read_bytes()
    if len(raw)>48000:raise ValueError('Scenario exceeds 48KB')
    data=json.loads(raw)
    if not isinstance(data,dict):raise ValueError('Scenario must be an object')
    name=data.get('id','')
    if not re.fullmatch(r'[a-z0-9-]{1,64}',name):raise ValueError('Unsafe scenario id')
    request_id=uuid.uuid4().hex;data['request_id']=request_id
    encoded=base64.b64encode(json.dumps(data,separators=(',',':')).encode()).decode()
    result=adb('shell','am','instrument','-w','-e','scenario',encoded,PKG+'/xtr.keymapper.virtual.VirtualG8Instrumentation',capture_output=True,timeout=100).stdout
    WORK.mkdir(parents=True,exist_ok=True);(WORK/(name+'.instrumentation.txt')).write_text(result)
    try:
        report=json.loads(output('exec-out','run-as',PKG,'cat','files/virtual-g8/result.json'))
        if report.get('request_id')!=request_id:raise ValueError('Stale result/request mismatch')
    except ValueError as e:report={'status':'FAIL','error':str(e),'request_id':request_id}
    report['scenario_sha256']=hashlib.sha256(raw).hexdigest()
    suite=ET.Element('testsuite',name='virtual-g8',tests='1',failures=str(int(report['status']!='PASS')))
    case=ET.SubElement(suite,'testcase',name=name)
    if report['status']!='PASS':ET.SubElement(case,'failure',message=report.get('error','Failure'))
    ET.ElementTree(suite).write(WORK/(name+'.xml'),encoding='unicode')
    save(name+'.json',report);print(json.dumps({'id':name,'status':report['status'],'events':len(report.get('touch_trace',[])),'final_pointers':report.get('final_pointers'),'report':str((WORK/(name+'.json')).relative_to(ROOT))}))
    if report['status']!='PASS':raise RuntimeError(report.get('error','Scenario failed'))
def smoke():
    verify();result=adb('shell','am','instrument','-w','-e','smoke','true',PKG+'/xtr.keymapper.virtual.VirtualG8Instrumentation',capture_output=True,timeout=100).stdout
    (WORK/'smoke.instrumentation.txt').write_text(result)
    report=json.loads(output('exec-out','run-as',PKG,'cat','files/virtual-g8/result.json'));save('smoke.json',report);print(json.dumps(report))
    if report['status']!='PASS':raise RuntimeError('UI smoke failed')
    for p in sorted((ROOT/'tools/scenarios').glob('*.json')):run(p)
def collect():
    verify();(WORK/'logcat.txt').write_text(output('logcat','-d','-t','1000'));adb('exec-out','screencap','-p',stdout=(WORK/'screenshot.png').open('wb'),text=False)
def stop():
    if not (WORK/'owned-process.json').exists():raise RuntimeError('No owned emulator process recorded')
    owned();verify();adb('emu','kill')
    deadline=time.monotonic()+20
    while time.monotonic()<deadline:
        devices=command([SDK/'platform-tools/adb','devices'],capture_output=True).stdout
        if CONFIG['serial']+'\t'not in devices:
            (WORK/'owned-process.json').unlink();return
        time.sleep(.5)
    raise RuntimeError('Owned emulator shutdown timeout; marker retained')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('command',choices=['doctor','setup','boot','wait','build','install','ui','run','smoke','collect','stop']);p.add_argument('scenario',nargs='?');p.add_argument('--install-sdk',action='store_true');p.add_argument('--gui',action='store_true');p.add_argument('--gpu',default='auto');a=p.parse_args()
    if a.command=='setup':create(a.install_sdk)
    elif a.command=='boot':boot(a.gui,a.gpu)
    elif a.command=='run':
        if not a.scenario:p.error('run requires a scenario JSON path')
        run(a.scenario)
    elif a.command=='ui':verify();adb('shell','am','start','-W','-n',PKG+'/xtr.keymapper.virtual.VirtualG8Activity','--ez','enable_virtual_g8','true')
    else:globals()[a.command]()
if __name__=='__main__':
    try:main()
    except (RuntimeError,ValueError,OSError,subprocess.SubprocessError)as e:print('ERROR: '+str(e),file=sys.stderr);sys.exit(1)
