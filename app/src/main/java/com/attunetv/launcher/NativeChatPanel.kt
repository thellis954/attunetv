package com.attunetv.launcher

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged

/** Native TV chat. This overlay is a window, not an Activity launched over the player. */
class NativeChatPanel(private val service: HomeButtonService) {
    private val context = Screen.normalize(service)
    private val manager = service.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val chat = NativeChat.get(service)
    private var panel: LinearLayout? = null
    private var expected: String? = null
    private var epoch=0
    private val timeout=Runnable { hide() }
    val isShowing get() = panel != null
    private fun dp(n:Int)=(n*context.resources.displayMetrics.density).toInt()
    private fun label(value:String,size:Float)=TextView(context).apply {text=value;textSize=size;setTextColor(Color.WHITE)}
    private fun button(value:String,action:()->Unit)=ChatUi.style(Button(context)).apply {
        text=value;isAllCaps=false;textSize=13f;setOnClickListener { touch();action() }
        setOnFocusChangeListener { _,focused -> if(focused)touch() }
    }
    private fun touch() {main.removeCallbacks(timeout);if(isShowing)main.postDelayed(timeout,120000)}

    fun show(pkg:String, preview:Boolean=false) {
        if(!StreamingSupport.supported(pkg) && !(preview && pkg==service.packageName))return
        hide();expected=pkg;val instance=++epoch
        val column=object:LinearLayout(context) {
            override fun dispatchKeyEvent(event:KeyEvent):Boolean {
                touch()
                if(event.keyCode==KeyEvent.KEYCODE_BACK) {if(event.action==KeyEvent.ACTION_UP)hide();return true}
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(16),dp(18),dp(12))
            background=GradientDrawable().apply {setColor(Color.argb(245,14,25,33));cornerRadius=dp(18).toFloat();setStroke(dp(1),Color.rgb(101,134,129))}
        }
        val scroll=ScrollView(context);val body=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL}
        scroll.addView(body);column.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        body.addView(label(if(preview)"Attune • panel preview" else "Attune • ${StreamingSupport.candidates[pkg]}",19f))
        val detected=PlaybackContext.read(service,pkg)
        var source=if(detected.title!=null)"app metadata" else "entered by viewer"
        val provenance=label(if(detected.title!=null)"Title shared by the app. Confirm it before asking." else "Enter the title: this app hasn't shared it. No video capture.",11f)
        body.addView(provenance)
        val title=EditText(context).apply {hint="Movie or show title";textSize=14f;setSingleLine();maxLines=1}
        val episode=EditText(context).apply {hint="Episode / where you are in the story";textSize=14f;setSingleLine()}
        val question=EditText(context).apply {hint="Ask about what you’re watching";textSize=14f;minLines=1;maxLines=3}
        title.setText(detected.title.orEmpty());episode.setText(detected.episode.orEmpty())
        title.doAfterTextChanged {source="entered by viewer";provenance.text="Context entered by you. No video or audio capture."}
        episode.doAfterTextChanged {source="entered by viewer";provenance.text="Context entered by you. No video or audio capture."}
        body.addView(title);body.addView(episode);body.addView(question)
        val answer=label(if(chat.connected)"Ready when you are." else "Connect ChatGPT in AttuneTV Settings first.",15f).apply {setPadding(0,dp(8),0,dp(12));setTextIsSelectable(false)}
        val ask=button("Ask") {}
        ask.setOnClickListener {
            touch()
            if(title.text.isBlank()||question.text.isBlank()) {answer.text="Enter the title and your question first.";return@setOnClickListener}
            if(!chat.connected) {answer.text="Connect ChatGPT in AttuneTV Settings first.";return@setOnClickListener}
            service.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(column.windowToken,0)
            ask.isEnabled=false;answer.text="Thinking…"
            val fresh=PlaybackContext.read(service,pkg)
            chat.ask(if(preview)"TV preview" else StreamingSupport.candidates[pkg].orEmpty(),title.text.toString(),episode.text.toString(),question.text.toString(),source,
                if(source=="app metadata" && fresh.title==title.text.toString())fresh.positionMs else null) {text,done->
                if(panel===column && epoch==instance) {answer.text=text;if(done)ask.isEnabled=true}
            }
        }
        val actions=LinearLayout(context).apply {orientation=LinearLayout.HORIZONTAL}
        actions.addView(ask,LinearLayout.LayoutParams(0,dp(42),1f))
        actions.addView(button("Stop") {chat.cancel();ask.isEnabled=true;answer.append("\nAnswer stopped.")},LinearLayout.LayoutParams(0,dp(42),1f))
        body.addView(actions);body.addView(answer)
        column.addView(button("Close • Back") {hide()},LinearLayout.LayoutParams(-1,dp(42)))
        column.addView(label("Closes after 2 minutes idle. Answers may contain errors or spoilers.",10f))
        val params=WindowManager.LayoutParams(dp(370),dp(480),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply {
            gravity=Gravity.END or Gravity.TOP;x=dp(20);y=dp(20);this.title="AttuneTV native chat"
            softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        try {manager.addView(column,params);panel=column;service.watchCompanionWindows(true);touch();title.requestFocus()}
        catch (_:Exception) {expected=null;chat.cancel()}
    }
    fun onWindow(pkg:String) {
        if(!isShowing)return
        val keyboard=Settings.Secure.getString(service.contentResolver,Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        if(pkg!=expected && pkg!=service.packageName && pkg!=keyboard)hide()
    }
    fun hide() {
        epoch++;main.removeCallbacks(timeout);chat.cancel()
        panel?.let { view ->
            service.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(view.windowToken,0)
            try {manager.removeViewImmediate(view)} catch (_:Exception) {}
        }
        panel=null;expected=null;service.watchCompanionWindows(false)
    }
}
