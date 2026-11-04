# Network-debug power settings

After the screen-off validation passed, the user explicitly requested disabling
idle screen-off/locking during network debugging and investigating G8 standby.
This supersedes the earlier instruction against unsolicited global display changes.

The previous screen timeout was 300000 ms (five minutes). It is now 2147483647 ms,
the maximum signed 32-bit timeout (about 24.9 days), verified by reading the setting
back. This effectively removes the short idle timeout for this development session.
It persists until explicitly restored; it is not an automatic network-ADB policy.
The original values are also backed up in ignored private device evidence.

No lock credential was removed, no keyguard was dismissed through ADB, and no
secure lock-delay, charging stay-awake, SELinux, thermal or performance settings
were changed. A phone already locked still needs normal user unlock. Pressing the
power button can still lock it. After the user woke/reconnected the controller, its exact USB device was inspected.
Its power/control was already `on`, runtime_status was `active` and the unused
autosuspend delay was 2000 ms. No USB power setting needed changing; see
[usb-power.json](usb-power.json).

Restore the original screen timeout when the debugging session ends:

```sh
adb -s "$S10E_ADB_SERIAL" shell settings put system screen_off_timeout 300000
```

## G8 firmware standby versus Android USB suspend

The official original G8 manual's “Standby and wake up” image states ten-minute
idle standby. A brief A/B/X/Y/Home press wakes it; holding Home for five seconds
instead enters standby. No standby-disable setting was found in the examined
getting-started/button-combinations pages:

- [Official getting-started page](https://doc.xiaoji.com/en/g8/detail/1222.html)
- [Official standby/wake instructions](https://doc.xiaoji.com/uploads/2023/12/22/25269448709152059096.png)
- [Official button combinations](https://doc.xiaoji.com/en/g8/detail/1226.html)

This is firmware behavior, distinct from Android USB runtime autosuspend. Disabling
USB autosuspend cannot be claimed to disable firmware standby. Only the exact G8 USB device was inspected; do not
change global USB-host power policy or send undocumented HID output/feature reports.
No firmware modification or fake button keepalive has been attempted. Idle stability
after the screen timeout change and any supported per-device USB change still needs
observation. Standby is not claimed disabled.
