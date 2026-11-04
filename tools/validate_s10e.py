#!/usr/bin/env python3
"""Explicit, repeatable debug APK suites; no game launch or game/account changes."""
import argparse,json,subprocess,re,time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--suites',nargs='+',default=['jni','profiles_prepare','profiles_verify','startup','injection','overlay']);args=p.parse_args()
allowed={'jni','profiles_prepare','profiles_verify','startup','injection','overlay','screen','reader_hold','runtime','tester','tester_render'}
assert set(args.suites)<=allowed
adb=['adb','-s',args.serial];package='io.github.rickallauigan.mythicmapper.debug'
out=Path('docs/validation/s10e');out.mkdir(parents=True,exist_ok=True)
private=Path('app/build/s10e-private');private.mkdir(parents=True,exist_ok=True)
def shell(cmd):return subprocess.check_output(adb+['shell',cmd],text=True).strip()
assert shell('getprop ro.product.model')=='SM-G970F','Wrong device model'
names=shell('ps -A -o ARGS').splitlines()
assert not any(n.strip().startswith('xtr.keymapper.debug') for n in names),'XtMapper must remain stopped'
for suite in args.suites:
 # Never accept a previous run's result or root-termination readiness marker.
 shell(f'run-as {package} rm -f files/validation/{suite}.json')
 if suite=='reader_hold':shell(f'run-as {package} rm -f files/validation/reader-ready.json')
 command=adb+['shell','am','instrument','-w','-e','suite',suite,package+'/xtr.keymapper.validation.HardwareValidationInstrumentation']
 log=private/(suite+'-run.log')
 with log.open('w') as output:
  process=subprocess.Popen(command,stdout=output,stderr=subprocess.STDOUT)
  if suite=='reader_hold':
   ready=None
   for attempt in range(100):
    result=subprocess.run(adb+['exec-out','run-as',package,'cat','files/validation/reader-ready.json'],capture_output=True,text=True)
    if result.returncode==0:
     try:ready=json.loads(result.stdout)
     except json.JSONDecodeError:ready=None
     if ready is not None:break
    if process.poll() is not None:break
    time.sleep(.2)
   assert ready=={'mapping_active':False,'logical_touches':0,'reader_running':True},'No safe root-termination readiness'
   processes=shell('ps -A -o USER,PID,NAME').splitlines()
   candidates=[]
   for line in processes:
    fields=line.split()
    if len(fields)==3 and fields[0]=='root' and re.fullmatch(re.escape(package)+r':root:\d+',fields[2]):candidates.append(int(fields[1]))
   assert len(candidates)==1,'Ambiguous root process; refusing termination'
   subprocess.run(adb+['shell',f"su -c 'kill -KILL {candidates[0]}'"],check=True)
  process.wait(timeout=260 if suite=='tester' else 80)
 raw=subprocess.check_output(adb+['exec-out','run-as',package,'cat',f'files/validation/{suite}.json'],text=True)
 report=json.loads(raw);(out/(suite+'.json')).write_text(json.dumps(report,indent=2)+'\n')
 print(suite,report.get('status'))
 assert report.get('status')=='PASS',report.get('error','Suite failed')
 time.sleep(1)
 active=shell('ps -A -o NAME').splitlines()
 readers=[n.strip() for n in active if n.strip()=='getevent']
 if readers:
  time.sleep(2);readers=[n.strip() for n in shell('ps -A -o NAME').splitlines() if n.strip()=='getevent']
 assert not readers,'Reader remains after suite teardown; inspect ownership before proceeding'
print('Suites completed. No live getevent readers after teardown. No gameplay compatibility assertion.')
