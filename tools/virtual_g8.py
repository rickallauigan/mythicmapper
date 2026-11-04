#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Owned AVD only. Never selects a physical ADB target or wipes app/device data."""
import argparse, base64, hashlib, json, os, pathlib, re, shlex, shutil, subprocess, sys, time, uuid, zipfile
import xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
CONFIG=json.loads((ROOT/'tools/virtual-g8-avd.json').read_text())
WORK=ROOT/'app/build/virtual-g8'
SDK=pathlib.Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT',str(pathlib.Path.home()/'Android/Sdk'))))
PKG='io.github.rickallauigan.mythicmapper.debug'
AUTO_RECOVERIES=0
ENV=dict(os.environ,ANDROID_AVD_HOME=str(WORK/'avd'))
def command(argv, **kw):
    shown=[str(x) if len(str(x))<200 else '<argument '+str(len(str(x)))+' bytes>' for x in argv]
    print('+ '+shlex.join(shown),file=sys.stderr,flush=True)
    kw.setdefault("text",True)
    try:return subprocess.run(list(map(str,argv)),check=True,env=ENV,**kw)
    except subprocess.CalledProcessError as error:
        if error.stderr:print(str(error.stderr)[-4000:],file=sys.stderr)
        raise
def adb(*args,**kw):return command([SDK/'platform-tools/adb','-s',CONFIG['serial'],*args],**kw)
def output(*args):return adb(*args,capture_output=True).stdout.strip()
def instrument_report(log, key):
    prefix='INSTRUMENTATION_RESULT: '+key+'='
    lines=[line[len(prefix):]for line in log.splitlines()if line.startswith(prefix)]
    if len(lines)!=1:raise ValueError('No unique fresh instrumentation report; persisted results are not accepted')
    report=json.loads(lines[0])
    if not isinstance(report,dict)or 'status'not in report:raise ValueError('Invalid fresh report')
    return report
def save(name,data):
    WORK.mkdir(parents=True,exist_ok=True);(WORK/name).write_text(json.dumps(data,indent=2)+'\n')
def owned():
    state=json.loads((WORK/'owned-process.json').read_text())
    args=pathlib.Path('/proc')/str(state['pid'])/'cmdline'
    if not args.exists() or CONFIG['name'] not in args.read_bytes().decode(errors='replace'):
        raise RuntimeError('Recorded emulator process is no longer owned/alive')
def verify():
    owned()
    if output('shell','getprop','ro.kernel.qemu')!='1':raise RuntimeError('Refusing non-emulator target')
    if output('emu','avd','name').splitlines()[0]!=CONFIG['name']:raise RuntimeError('Target is not this workflow AVD')
def doctor():
    required=[SDK/'platform-tools/adb',SDK/'emulator/emulator',SDK/'cmdline-tools/latest/bin/avdmanager']
    r={'host':os.uname().sysname,'arch':os.uname().machine,'tools':{str(p.relative_to(SDK)):p.exists()for p in required},'kvm_access':os.access('/dev/kvm',os.R_OK|os.W_OK),'image_installed':(SDK/'system-images/android-36/default/x86_64/system.img').exists(),'config':CONFIG}
    r['cpu_cores']=os.cpu_count();r['available_memory_kb']=next((int(line.split()[1])for line in pathlib.Path('/proc/meminfo').read_text().splitlines()if line.startswith('MemAvailable:')),0)
    r['free_storage_bytes']=shutil.disk_usage(ROOT).free
    r['ndk_versions']=[p.name for p in (SDK/'ndk').glob('*')]
    r['emulator_version']=command([SDK/'emulator/emulator','-no-window','-version'],capture_output=True).stdout.splitlines()[0]
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
def home():
    verify();adb('shell','am','start','-W','-n',PKG+'/xtr.keymapper.virtual.VirtualHomeActivity')
def run(path):
    print('+ '+shlex.join(['python3','tools/virtual_g8.py','run',str(path)]),file=sys.stderr)
    verify();platform_ready();raw=pathlib.Path(path).read_bytes()
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
        report=instrument_report(result,'virtual_g8')
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
    verify();platform_ready()
    jni=adb('shell','am','instrument','-w','-e','suite','jni',PKG+'/xtr.keymapper.validation.HardwareValidationInstrumentation',capture_output=True,timeout=60).stdout
    (WORK/'jni.instrumentation.txt').write_text(jni)
    parity=instrument_report(jni,'validation');save('jni.json',parity)
    if parity['status']!='PASS' or parity.get('result',{}).get('abi')!='x86_64':raise RuntimeError('Packaged x86_64 JNI parity/ABI check failed')
    def ui_attempt():
        result=adb('shell','am','instrument','-w','-e','smoke','true',PKG+'/xtr.keymapper.virtual.VirtualG8Instrumentation',capture_output=True,timeout=100).stdout
        (WORK/'smoke.instrumentation.txt').write_text(result)
        return instrument_report(result,'virtual_g8')
    report=ui_attempt()
    if report['status']!='PASS' and 'focus'not in report.get('error','').lower():
        save('smoke.json',report);raise RuntimeError('UI smoke assertion failed')
    if platform_ready():
        save('smoke-before-platform-recovery.json',report)
        shutil.copyfile(WORK/'smoke.instrumentation.txt',WORK/'smoke-before-platform-recovery.instrumentation.txt')
        report=ui_attempt();report['environment_recovered']=True
    save('smoke.json',report);print(json.dumps(report))
    if report['status']!='PASS':raise RuntimeError('UI smoke failed')
    for p in sorted((ROOT/'tools/scenarios').glob('*.json')):run(p)
def ux(suite='bubble'):
    if suite not in ('bubble','home'):raise ValueError('Unknown UX suite')
    verify();platform_ready()
    # App-scoped overlay permission on this owned emulator only; no root/system policy changes.
    adb('shell','appops','set',PKG,'SYSTEM_ALERT_WINDOW','allow')
    log=adb('shell','am','instrument','-w','-e','suite',suite,PKG+'/xtr.keymapper.ux.UxInstrumentation',capture_output=True,timeout=120).stdout
    (WORK/('ux-'+suite+'.instrumentation.txt')).write_text(log)
    report=instrument_report(log,'ux');save('ux-'+suite+'.json',report);print(json.dumps(report))
    for name in (['bubble-collapsed','bubble-expanded','bubble-portrait']if suite=='bubble'else ['home-overview','home-favorites','home-library','home-landscape']):
        adb('exec-out','run-as',PKG,'cat','files/ux/'+name+'.png',stdout=(WORK/(name+'.png')).open('wb'),text=False)
    if report['status']!='PASS':raise RuntimeError('UX suite failed')
def collect():
    verify();(WORK/'logcat.txt').write_text(output('logcat','-d','-t','1000'));adb('exec-out','screencap','-p',stdout=(WORK/'screenshot.png').open('wb'),text=False)
def platform_ready():
    global AUTO_RECOVERIES
    windows=output('shell','dumpsys','window','displays')
    if 'Application Not Responding: com.android.systemui' in windows:
        if AUTO_RECOVERIES>=1:raise RuntimeError('Repeated SystemUI startup ANR; no further automatic recovery')
        recover();AUTO_RECOVERIES+=1;return True
    if 'Application Not Responding:'in windows:raise RuntimeError('Application ANR blocks tests; no automatic recovery')
    return False
def recover():
    """One explicit Wait on a verified owned-AVD SystemUI startup ANR, never an app ANR."""
    verify();windows=output('shell','dumpsys','window','displays')
    title='Application Not Responding: com.android.systemui'
    if title not in windows:
        if 'Application Not Responding:'in windows:raise RuntimeError('Refusing recovery for a non-SystemUI ANR')
        print('No SystemUI startup ANR to recover');return
    adb('shell','uiautomator','dump','/data/local/tmp/mythicmapper-boot-recovery.xml')
    tree=ET.fromstring(output('exec-out','cat','/data/local/tmp/mythicmapper-boot-recovery.xml'))
    nodes=list(tree.iter('node'))
    if not any(n.get('resource-id')=='android:id/alertTitle'and n.get('text')=="System UI isn't responding"for n in nodes):raise RuntimeError('Unrecognized SystemUI dialog')
    button=next(n for n in nodes if n.get('resource-id')=='android:id/aerr_wait'and n.get('package')=='android')
    bounds=list(map(int,re.findall(r'\d+',button.get('bounds',''))))
    if len(bounds)!=4 or not(0<=bounds[0]<bounds[2]<=CONFIG['width']and 0<=bounds[1]<bounds[3]<=CONFIG['height']):raise RuntimeError('Unexpected Wait bounds')
    if title not in output('shell','dumpsys','window','displays'):raise RuntimeError('Recovery focus changed; no tap submitted')
    events=output('logcat','-d','-b','events','-s','am_anr')
    save('boot-recovery.json',{'observed_systemui_startup_anr':True,'action':'Wait on verified owned AVD only','events':events})
    adb('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
    time.sleep(1)
    if title in output('shell','dumpsys','window','displays'):raise RuntimeError('SystemUI recovery did not dismiss dialog')
    print('Observed SystemUI startup ANR acknowledged with Wait; rerun the complete failed suite')
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
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('command',choices=['doctor','setup','boot','wait','build','install','ui','home','run','smoke','collect','recover','stop','ux']);p.add_argument('scenario',nargs='?');p.add_argument('--install-sdk',action='store_true');p.add_argument('--gui',action='store_true');p.add_argument('--gpu',default='auto');a=p.parse_args()
    if a.command=='setup':create(a.install_sdk)
    elif a.command=='boot':boot(a.gui,a.gpu)
    elif a.command=='run':
        if not a.scenario:p.error('run requires a scenario JSON path')
        run(a.scenario)
    elif a.command=='ux':ux(a.scenario or 'bubble')
    elif a.command=='ui':verify();adb('shell','am','start','-W','-n',PKG+'/xtr.keymapper.virtual.VirtualG8Activity','--ez','enable_virtual_g8','true')
    else:globals()[a.command]()
if __name__=='__main__':
    try:main()
    except (RuntimeError,ValueError,OSError,subprocess.SubprocessError)as e:print('ERROR: '+str(e),file=sys.stderr);sys.exit(1)
