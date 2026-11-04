// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

/** Compile-only platform declaration; never packaged. Android provides the implementation.
 * Signature reference: AOSP android16-release core/java/android/app/TaskStackListener.java. */
public abstract class TaskStackListener {
    public TaskStackListener() { throw new RuntimeException("STUB"); }
    public void onTaskStackChanged() throws android.os.RemoteException {}
    public void onTaskMovedToFront(ActivityManager.RunningTaskInfo task) throws android.os.RemoteException {}
    public void onTaskFocusChanged(int taskId, boolean focused) throws android.os.RemoteException {}
}
