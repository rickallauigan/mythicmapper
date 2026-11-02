// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import android.content.Context
import android.os.*
import xtr.keymapper.IRemoteService
import xtr.keymapper.editor.EditorSessionGuard
import xtr.keymapper.server.RemoteServiceHelper

/** Configuration-only lease over the proven Binder guard. No input reader or touch producer. */
class RootDiagnosticLease(private val context:Context,private val changed:(Boolean,String)->Unit){
    private val thread=HandlerThread("hero-setup-lease").apply{start()}
    private val worker=Handler(thread.looper);private val main=Handler(Looper.getMainLooper())
    private val token=Binder();@Volatile private var started=false;@Volatile private var generation=0
    private var remote:IRemoteService?=null;private var binding:RemoteServiceHelper.RemoteServiceConnection?=null
    private val death=IBinder.DeathRecipient{main.post{changed(false,"Root backend disconnected; close and reopen setup")}}
    fun start(){started=true;val current=++generation;EditorSessionGuard.acquire(this)
        worker.post{val granted=runCatching{com.topjohnwu.superuser.Shell.getShell().isRoot}.getOrDefault(false)
            main.post{
                if(!started||generation!=current)return@post
                if(!granted){changed(false,"App-specific root consent is required");return@post}
                binding=RemoteServiceHelper.getInstance(context){service->worker.post{
                    if(!started||generation!=current)return@post
                    try{
                        requireNotNull(service);check(service.beginDiagnostics(token)){"Exclusive setup lease unavailable"}
                        if(!started||generation!=current){service.endDiagnostics(token);service.stopServer();return@post}
                        remote=service;service.asBinder().linkToDeath(death,0)
                        main.post{if(started&&generation==current)changed(true,"Mapping stopped · exclusive calibration setup")}
                    }catch(error:Exception){runCatching{service?.endDiagnostics(token)};main.post{changed(false,"Setup unavailable: ${error.javaClass.simpleName}")}}
                }}
            }
        }
    }
    fun stop(){started=false;generation++;val owned=binding;binding=null;changed(false,"Mapping remains stopped")
        worker.post{runCatching{remote?.endDiagnostics(token);remote?.asBinder()?.unlinkToDeath(death,0);remote?.stopServer()};remote=null
            main.post{if(owned!=null&&!RemoteServiceHelper.useShizuku)runCatching{com.topjohnwu.superuser.ipc.RootService.unbind(owned)};EditorSessionGuard.release(this)}}
    }
    fun close(){worker.post{thread.quitSafely()}}
}
