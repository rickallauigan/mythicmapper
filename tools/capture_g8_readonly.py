#!/usr/bin/env python3
"""Read only the exact G8 evdev node; publish aggregate button/axis evidence, never identities."""
import argparse, json, re, shlex, subprocess, time, select, os
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--seconds',type=int,default=75);args=p.parse_args()
assert 1<=args.seconds<=120
adb=['adb','-s',args.serial]
def shell(command):return subprocess.check_output(adb+['shell',command],text=True).strip()
block=None
for attempt in range(60):
 blocks=shell("su -c 'cat /proc/bus/input/devices'").split('\n\n')
 block=next((b for b in blocks if 'N: Name="Sony Interactive Entertainment GameSir-G8"' in b),None)
 if block:break
 time.sleep(1)
if block is None:
 Path('docs/validation/s10e/g8-events.json').write_text(json.dumps({'status':'BLOCKED','reason':'Controller interface remained absent/asleep; physical button exercise not observed'},indent=2)+'\n')
 raise SystemExit(2)
subprocess.run(adb+['shell','am','start','-n','io.github.rickallauigan.mythicmapper.debug/xtr.keymapper.validation.ValidationActivity'],stdout=subprocess.DEVNULL,check=True)
node='/dev/input/'+re.search(r'\bevent\d+\b',block).group()
apk=shell('pm path io.github.rickallauigan.mythicmapper.debug').removeprefix('package:')
library=str(Path(apk).parent/'lib/arm64/libgetevent.so')
subprocess.run(adb+['shell',f"su -c {shlex.quote('test -f '+shlex.quote(library))}"],check=True)
command=f'env LD_PRELOAD={shlex.quote(library)} getevent -lt {shlex.quote(node)} & child=$!; echo MYTHIC_CAPTURE_PID=$child; wait "$child"'
raw=Path('app/build/s10e-private/g8-events.log');raw.parent.mkdir(parents=True,exist_ok=True)
process=subprocess.Popen(adb+['shell','su -c '+shlex.quote(command)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,bufsize=0)
started=time.monotonic();first=None;captured=bytearray();child=None
try:
 while process.poll() is None:
  now=time.monotonic()
  if now-started>180 or (first is not None and now-first>=args.seconds):break
  readable,_,_=select.select([process.stdout],[],[],1)
  if not readable:continue
  data=os.read(process.stdout.fileno(),65536)
  if not data:break
  captured.extend(data)
  marker=re.search(rb'MYTHIC_CAPTURE_PID=(\d+)',captured)
  if marker:child=int(marker.group(1))
  if first is None and re.search(rb'EV_(KEY|ABS|SYN)',data):first=now
finally:
 if child is not None and process.poll() is None:
  current=shell(f"su -c 'cat /proc/{child}/cmdline'")
  if 'getevent' in current and node in current:
   subprocess.run(adb+['shell',f"su -c 'kill -TERM {child}'"],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 try:process.wait(timeout=5)
 except subprocess.TimeoutExpired:process.terminate()
 raw.write_bytes(captured)
keys={};axes={};held=set();duplicates=0;drops=0;frames=0
for line in raw.read_text().splitlines():
 m=re.search(r'(EV_KEY|EV_ABS|EV_SYN)\s+(\w+)\s+(\w+)',line)
 if not m:continue
 kind,code,value=m.groups()
 if kind=='EV_KEY':
  counts=keys.setdefault(code,{'down':0,'up':0,'repeat':0})
  if value=='DOWN':
   if code in held:duplicates+=1
   held.add(code);counts['down']+=1
  elif value=='UP':held.discard(code);counts['up']+=1
  else:counts['repeat']+=1
 elif kind=='EV_ABS':
  v=int(value,16);v=v-(1<<32) if v>=(1<<31) else v
  record=axes.setdefault(code,{'min':v,'max':v,'last':v,'events':0});record.update(min=min(record['min'],v),max=max(record['max'],v),last=v,events=record['events']+1)
 elif code=='SYN_DROPPED':drops+=1
 elif code=='SYN_REPORT':frames+=1
result={'capture_seconds':round(time.monotonic()-first,2) if first else 0,'wait_for_first_event_seconds':round((first or time.monotonic())-started,2),'capture_return_code':process.returncode,'source':'physical G8 evdev, read-only; no grab/injection','buttons':keys,'axes':axes,'duplicate_downs':duplicates,'held_buttons_at_end':sorted(held),'syn_dropped':drops,'syn_reports':frames,'gameplay_validated':False}
Path('docs/validation/s10e/g8-events.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
