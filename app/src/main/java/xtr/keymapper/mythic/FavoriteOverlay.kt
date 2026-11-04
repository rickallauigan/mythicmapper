// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.WindowInsets
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
class FavoriteOverlay internal constructor(private val service: Context, private val selected:()->String?,
    private val select: (String)->Unit, private val permitted:()->Boolean) {
    constructor(service:TouchPointer,select:(String)->Unit):this(service,{service.selectedProfileForUi()},select,
        {InputDeviceDiscovery.connected().any(MythicScope::supported)})
    private fun dp(value:Int)=(value*service.resources.displayMetrics.density+.5f).toInt()
    private fun rounded()=GradientDrawable().apply{setColor(0xff12121f.toInt());cornerRadius=dp(18).toFloat();setStroke(dp(1),0xff72509b.toInt())}

    private fun panelButton()=Button(service).apply {
        isAllCaps=false;textSize=14f;minHeight=dp(48);minimumHeight=dp(48)
        setTextColor(android.content.res.ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(0xff82798e.toInt(),0xffeee6ff.toInt())))
        background=GradientDrawable().apply{setColor(0xff241b36.toInt());cornerRadius=dp(10).toFloat();setStroke(dp(1),0xff443454.toInt())}
        layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply{setMargins(dp(4),dp(4),dp(4),dp(4))}
        setPadding(dp(10),dp(8),dp(10),dp(8))
    }
    private val manager=service.getSystemService(WindowManager::class.java)
    private val handler=Handler(Looper.getMainLooper())
    private val prefs=service.getSharedPreferences("mythic_overlay",0)
    private val root=LinearLayout(service).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(android.graphics.Color.TRANSPARENT) }
    private val header=MythicBubble(service).apply {
        contentDescription="MythicMapper favorites; tap to expand or collapse, drag to move"
        layoutParams=LinearLayout.LayoutParams(dp(48),dp(48))
    }
    private val panel=LinearLayout(service).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(8),dp(8),dp(8)) }
    private val panelScroll=ScrollView(service).apply {
        visibility=View.GONE
        addView(panel)
        layoutParams=LinearLayout.LayoutParams((320*service.resources.displayMetrics.density).toInt().coerceAtMost(service.resources.displayMetrics.widthPixels),
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
            panelScroll.layoutParams=LinearLayout.LayoutParams((320*metrics.density).toInt().coerceAtMost(screenWidth),(screenHeight*.65f).toInt())
            clamp();updateWindow()
        }
        handler.postDelayed(this,750)
    } }
    fun show() {
        if(attached || !Settings.canDrawOverlays(service)) return
        root.removeAllViews();root.setBackgroundColor(android.graphics.Color.TRANSPARENT);panelScroll.visibility=View.GONE;root.visibility=if(EditorSessionGuard.active)View.GONE else View.VISIBLE
        root.addView(header);root.addView(panelScroll)
        EditorSessionGuard.listen(editorVisibility)
        header.setOnClickListener { if(panelScroll.visibility==View.VISIBLE)collapse() else openPanel() }
        var startX=0f;var startY=0f;var baseX=0;var baseY=0;var dragging=false
        header.setOnTouchListener { view,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {view.isPressed=true;startX=event.rawX;startY=event.rawY;baseX=params.x;baseY=params.y;dragging=false;true}
                MotionEvent.ACTION_MOVE -> {
                    val dx=event.rawX-startX;val dy=event.rawY-startY
                    if(kotlin.math.hypot(dx,dy)>ViewConfiguration.get(service).scaledTouchSlop)dragging=true
                    if(dragging){params.x=baseX+dx.toInt();params.y=baseY+dy.toInt();clamp();updateWindow()}
                    true
                }
                MotionEvent.ACTION_UP -> {view.isPressed=false;if(!dragging)view.performClick();prefs.edit().putInt("x",params.x).putInt("y",params.y).apply();true}
                MotionEvent.ACTION_CANCEL -> {view.isPressed=false;dragging=false;true}
                else -> false
            }
        }
        clamp();try{manager.addView(root,params)}catch(_:RuntimeException){EditorSessionGuard.remove(editorVisibility);return};attached=true
        root.addOnLayoutChangeListener(layoutChanged)
        handler.post(refresh)
    }
    private val layoutChanged=View.OnLayoutChangeListener {_,_,_,_,_,_,_,_,_->
        if(attached){val x=params.x;val y=params.y;clamp();if(x!=params.x||y!=params.y)updateWindow()}
    }
    private fun updateWindow(){if(attached)runCatching{manager.updateViewLayout(root,params)}.onFailure{close()}}
    private fun clamp() {
        val metrics=service.resources.displayMetrics
        val bounds=if(Build.VERSION.SDK_INT>=30)manager.currentWindowMetrics.bounds else android.graphics.Rect(0,0,metrics.widthPixels,metrics.heightPixels)
        val inset=if(Build.VERSION.SDK_INT>=30)manager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).let{android.graphics.Rect(it.left,it.top,it.right,it.bottom)}else android.graphics.Rect()
        val width=root.measuredWidth.takeIf{it>0}?:dp(48)
        val height=root.measuredHeight.takeIf{it>0}?:dp(48)
        // Coordinates are in the default inset overlay frame. Reserve cutouts/system bars conservatively.
        params.x=params.x.coerceIn(0,(bounds.width()-inset.left-inset.right-width).coerceAtLeast(0))
        params.y=params.y.coerceIn(0,(bounds.height()-inset.top-inset.bottom-height).coerceAtLeast(0))
    }
    private fun collapse(){panelScroll.visibility=View.GONE;root.setBackgroundColor(android.graphics.Color.TRANSPARENT);root.post{clamp();updateWindow()}}
    private fun openPanel() {
        panel.removeAllViews()
        try {
            val state=HeroLibraryStore(service).read()
            val name=state.profiles.find{it.id==selected()}?.name ?: "None"
            panel.addView(TextView(service).apply {text="Selected: $name";setTextColor(-1);setPadding(16,8,16,8)})
            state.favorites.forEachIndexed { index,id ->
                val profile=state.profiles.find{it.id==id}
                panel.addView(panelButton().apply {
                    text="${if(id!=null&&id==selected())"● "else""}${index+1}. ${profile?.name ?: "Empty"}";isEnabled=profile!=null
                    setOnClickListener { runCatching {
                        check(permitted()){"Supported original G8 USB mode required"}
                        select(profile!!.id);collapse()
                    }.onFailure {Toast.makeText(service,it.message,Toast.LENGTH_LONG).show()} }
                })
            }
            panel.addView(panelButton().apply {text="Visual mapping editor";isEnabled=selected()!=null;setOnClickListener {
                collapse()
                service.startActivity(Intent(service,EditorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EditorActivity.PROFILE_NAME,selected()).putExtra(EditorActivity.VISUAL,true))
            }})
            panel.addView(panelButton().apply {text="Collapse";setOnClickListener{collapse()}})
            root.background=rounded();panelScroll.visibility=View.VISIBLE;root.post {clamp();updateWindow()}
        } catch(error:Exception) {Toast.makeText(service,error.message,Toast.LENGTH_LONG).show()}
    }
    fun isAttached() = attached
    fun close() {
        EditorSessionGuard.remove(editorVisibility)
        handler.removeCallbacks(refresh)
        root.removeOnLayoutChangeListener(layoutChanged)
        if(attached) {attached=false;runCatching{manager.removeView(root)}}
    }
}

/** Original vector-like Canvas glyph. Transparent 48dp target, 44dp visible circular face. */
internal class MythicBubble(context:Context):androidx.appcompat.widget.AppCompatButton(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD_ITALIC)}
    init {background=null;minimumWidth=0;minimumHeight=0;setPadding(0,0,0,0);isAllCaps=false}
    override fun onDraw(canvas:Canvas) {
        val d=resources.displayMetrics.density;val cx=width/2f;val cy=height/2f;val radius=22*d
        paint.style=Paint.Style.FILL;paint.color=if(isPressed)0xff472269.toInt()else 0xff1f1636.toInt();canvas.drawCircle(cx,cy,radius,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1.5f*d;paint.color=0xffb779ff.toInt();canvas.drawCircle(cx,cy,radius-d,paint)
        paint.style=Paint.Style.FILL;paint.textAlign=Paint.Align.CENTER;paint.textSize=28*d;paint.color=0xffd9b8ff.toInt()
        canvas.drawText("M",cx,cy-(paint.ascent()+paint.descent())/2,paint)
    }
}
