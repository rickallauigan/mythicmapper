// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ScrollView
import xtr.keymapper.TouchPointer
import xtr.keymapper.devices.InputDeviceDiscovery
import xtr.keymapper.editor.EditorActivity
import xtr.keymapper.editor.EditorSessionGuard

/** App-owned selector only. All mapping switches go through TouchPointer's existing runtime gate. */
class FavoriteOverlay(private val service: TouchPointer, private val select: (String)->Unit) {
    private val manager=service.getSystemService(WindowManager::class.java)
    private val handler=Handler(Looper.getMainLooper())
    private val prefs=service.getSharedPreferences("mythic_overlay",0)
    private val root=LinearLayout(service).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(0xee17232b.toInt()) }
    private val header=Button(service).apply { text="M"; contentDescription="MythicMapper favorites; drag to move" }
    private val panel=LinearLayout(service).apply { orientation=LinearLayout.VERTICAL }
    private val panelScroll=ScrollView(service).apply {
        visibility=View.GONE
        addView(panel)
        layoutParams=LinearLayout.LayoutParams((280*service.resources.displayMetrics.density).toInt().coerceAtMost(service.resources.displayMetrics.widthPixels),
            (service.resources.displayMetrics.heightPixels*.65f).toInt())
    }
    private val editorVisibility: ()->Unit = {handler.post{if(attached)root.visibility=if(EditorSessionGuard.active) View.GONE else View.VISIBLE}}
    private val params=WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
        if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply {
        gravity=Gravity.TOP or Gravity.LEFT;x=prefs.getInt("x",16);y=prefs.getInt("y",120)
    }
    private var attached=false
    private var screenWidth=0
    private var screenHeight=0
    private val refresh=object:Runnable { override fun run() {
        if(!attached) return
        if(!Settings.canDrawOverlays(service)) {close();return}
        root.visibility=if(EditorSessionGuard.active) View.GONE else View.VISIBLE
        val metrics=service.resources.displayMetrics
        if(screenWidth!=metrics.widthPixels || screenHeight!=metrics.heightPixels) {
            screenWidth=metrics.widthPixels;screenHeight=metrics.heightPixels
            panelScroll.layoutParams=LinearLayout.LayoutParams((280*metrics.density).toInt().coerceAtMost(screenWidth),(screenHeight*.65f).toInt())
            clamp();manager.updateViewLayout(root,params)
        }
        handler.postDelayed(this,750)
    } }
    fun show() {
        if(!Settings.canDrawOverlays(service)) return
        root.addView(header);root.addView(panelScroll)
        EditorSessionGuard.listen(editorVisibility)
        header.setOnClickListener { if(panelScroll.visibility==View.VISIBLE)panelScroll.visibility=View.GONE else openPanel() }
        var startX=0f;var startY=0f;var baseX=0;var baseY=0;var dragging=false
        header.setOnTouchListener { view,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {startX=event.rawX;startY=event.rawY;baseX=params.x;baseY=params.y;dragging=false;true}
                MotionEvent.ACTION_MOVE -> {
                    val dx=event.rawX-startX;val dy=event.rawY-startY
                    if(kotlin.math.hypot(dx,dy)>ViewConfiguration.get(service).scaledTouchSlop)dragging=true
                    if(dragging){params.x=baseX+dx.toInt();params.y=baseY+dy.toInt();clamp();if(attached)manager.updateViewLayout(root,params)}
                    true
                }
                MotionEvent.ACTION_UP -> {if(!dragging)view.performClick();prefs.edit().putInt("x",params.x).putInt("y",params.y).apply();true}
                MotionEvent.ACTION_CANCEL -> {dragging=false;true}
                else -> false
            }
        }
        clamp();manager.addView(root,params);attached=true;handler.post(refresh)
    }
    private fun clamp() {
        val metrics=service.resources.displayMetrics
        params.x=params.x.coerceIn(0,(metrics.widthPixels-root.measuredWidth.coerceAtLeast(64)).coerceAtLeast(0))
        params.y=params.y.coerceIn(0,(metrics.heightPixels-root.measuredHeight.coerceAtLeast(64)).coerceAtLeast(0))
    }
    private fun openPanel() {
        panel.removeAllViews()
        try {
            val state=HeroLibraryStore(service).read()
            val name=state.profiles.find{it.id==service.selectedProfile}?.name ?: "None"
            panel.addView(TextView(service).apply {text="Selected: $name";setTextColor(-1);setPadding(16,8,16,8)})
            state.favorites.forEachIndexed { index,id ->
                val profile=state.profiles.find{it.id==id}
                panel.addView(Button(service).apply {
                    text="${index+1}. ${profile?.name ?: "Empty"}";isEnabled=profile!=null
                    setOnClickListener { runCatching {
                        check(InputDeviceDiscovery.connected().any(MythicScope::supported)){"Supported original G8 USB mode required"}
                        select(profile!!.id);panelScroll.visibility=View.GONE
                    }.onFailure {Toast.makeText(service,it.message,Toast.LENGTH_LONG).show()} }
                })
            }
            panel.addView(Button(service).apply {text="Visual mapping editor";isEnabled=service.selectedProfile!=null;setOnClickListener {
                panelScroll.visibility=View.GONE
                service.startActivity(Intent(service,EditorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EditorActivity.PROFILE_NAME,service.selectedProfile).putExtra(EditorActivity.VISUAL,true))
            }})
            panel.addView(Button(service).apply {text="Collapse";setOnClickListener{panelScroll.visibility=View.GONE}})
            panelScroll.visibility=View.VISIBLE;root.post {clamp();if(attached)manager.updateViewLayout(root,params)}
        } catch(error:Exception) {Toast.makeText(service,error.message,Toast.LENGTH_LONG).show()}
    }
    fun close() {
        EditorSessionGuard.remove(editorVisibility)
        handler.removeCallbacks(refresh)
        if(attached) {attached=false;runCatching{manager.removeView(root)}}
    }
}
