package xtr.keymapper.server;

import android.app.ActivityManager;
import android.app.TaskStackListener;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.util.Log;
import java.lang.reflect.Method;
import xtr.keymapper.ActivityObserver;

/** Root-only event subscription, with the existing conservative polling fallback. */
public class ActivityObserverService implements Runnable {
    public ActivityObserver mCallback;
    private final ActivityManager manager;
    private final HandlerThread thread;
    private final Handler handler;
    private Object taskManager;
    private Method unregister;
    private TaskStackListener listener;
    private boolean stopped, queued;
    private String lastPackage;
    private volatile String mode="polling_5000ms";

    public ActivityObserverService(Context context, ActivityObserver callback) {
        manager=context.getSystemService(ActivityManager.class);mCallback=callback;
        thread=new HandlerThread("activity_observer");thread.start();handler=new Handler(thread.getLooper());
        try {
            Class<?> type=Class.forName("android.app.ActivityTaskManager");
            taskManager=type.getMethod("getInstance").invoke(null);
            listener=new TaskStackListener() {
                @Override public void onTaskStackChanged(){requestRefresh();}
                @Override public void onTaskMovedToFront(ActivityManager.RunningTaskInfo task){requestRefresh();}
                @Override public void onTaskFocusChanged(int taskId,boolean focused){requestRefresh();}
            };
            unregister=type.getMethod("unregisterTaskStackListener",TaskStackListener.class);
            mode="task_stack_listener";
            type.getMethod("registerTaskStackListener",TaskStackListener.class).invoke(taskManager,listener);
        } catch(ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            mode="polling_5000ms";listener=null;unregister=null;taskManager=null;
            Log.w(RemoteService.TAG,"Task listener unavailable; using polling fallback: "+unavailable.getClass().getSimpleName());
        }
        requestRefresh();
    }
    public synchronized String mode(){return mode;}
    /** Explicit unlock resample; ordinary task callbacks remain deduplicated. */
    public void refresh() {
        synchronized(this){if(stopped)return;lastPackage=null;}
        requestRefresh();
    }
    private void requestRefresh() {
        synchronized(this){if(stopped||queued)return;queued=true;handler.post(this);}
    }
    @Override public void run() {
        synchronized(this){queued=false;if(stopped)return;}
        try {
            var tasks=manager.getRunningTasks(1);
            if(!tasks.isEmpty()&&tasks.get(0).topActivity!=null) {
                String name=tasks.get(0).topActivity.getPackageName();ActivityObserver callback;
                synchronized(this){if(stopped||name.equals(lastPackage))return;lastPackage=name;callback=mCallback;}
                if(callback!=null)callback.onForegroundActivitiesChanged(name);
            }
        } catch(RemoteException | RuntimeException error) {
            Log.w(RemoteService.TAG,"Foreground observation failed",error);
        } finally {
            synchronized(this){if(!stopped&&mode.equals("polling_5000ms")){queued=true;handler.postDelayed(this,5000);}}
        }
    }
    public void stop() {
        Object target;Method method;TaskStackListener subscription;
        synchronized(this){if(stopped)return;stopped=true;mCallback=null;target=taskManager;method=unregister;subscription=listener;}
        // No monitor is held across platform Binder calls.
        if(target!=null&&method!=null&&subscription!=null)try{method.invoke(target,subscription);}
        catch(ReflectiveOperationException | RuntimeException ignored){Log.w(RemoteService.TAG,"Task listener unregister failed");}
        handler.removeCallbacksAndMessages(null);thread.quitSafely();
    }
}
