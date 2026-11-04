#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Exploratory debug/AVD scroll workload; never physical display latency."""
import json,re,sys,time
import virtual_g8 as avd
label=sys.argv[1] if len(sys.argv)>1 else 'current'
if not re.fullmatch('[a-z0-9-]+',label):raise ValueError('Unsafe label')
avd.verify();avd.platform_ready();avd.home();time.sleep(2)
# Same landscape, empty-library workload before/after; verify the actual foreground.
def foreground():
    text=avd.output('shell','dumpsys','window','displays')
    if not any('mCurrentFocus' in line and 'VirtualHomeActivity' in line for line in text.splitlines()):raise RuntimeError('Home not foreground; no swipe sent')
foreground()
avd.output('shell','dumpsys','gfxinfo',avd.PKG,'reset')
for i in range(12):
    foreground()
    y1,y2=(780,240)if i%2==0 else(240,780)
    avd.adb('shell','input','swipe','1140',str(y1),'1140',str(y2),'250');time.sleep(.15)
time.sleep(1)
gfx=avd.output('shell','dumpsys','gfxinfo',avd.PKG)
mem=avd.output('shell','dumpsys','meminfo',avd.PKG)
cpu=avd.output('shell','dumpsys','cpuinfo')
for name,data in [('gfx',gfx),('memory',mem),('cpu',cpu)]:
    (avd.WORK/f'home-{label}-{name}.txt').write_text(data)
summary={'label':label,'target':avd.CONFIG['serial'],'workload':'12 alternating 250ms swipes, landscape; debug AVD; no controller input','frames':re.findall(r'Total frames rendered: (\d+)',gfx),'janky':re.findall(r'Janky frames: ([^\n]+)',gfx),'percentiles_ms':re.findall(r'(\d+th percentile): ([^\n]+)',gfx),'cpu_note':'raw sampled dumpsys, not isolated workload CPU utilization'}
avd.save('home-'+label+'-performance.json',summary);print(json.dumps(summary))
