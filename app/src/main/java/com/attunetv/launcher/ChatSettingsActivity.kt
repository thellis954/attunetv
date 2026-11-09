package com.attunetv.launcher

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ChatSettingsActivity : TvActivity() {
    private lateinit var list:LinearLayout
    private val chat by lazy {NativeChat.get(this)}
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        Backgrounds.applyDimmed(this,Prefs(this))
        list=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(36),dp(24),dp(36),dp(24))}
        setContentView(ScrollView(this).apply {addView(list)})
    }
    override fun onResume() {super.onResume();chat.onChange={render()};render()}
    override fun onPause() {chat.onChange=null;super.onPause()}
    private fun label(text:String,size:Float=14f) {list.addView(TextView(this).apply {this.text=text;textSize=size;setTextColor(android.graphics.Color.WHITE);setPadding(0,dp(8),0,dp(8))})}
    private fun row(text:String,action:()->Unit) {list.addView(Button(this).apply {this.text=text;isAllCaps=false;textSize=14f;setOnClickListener{action()}},LinearLayout.LayoutParams(-1,dp(44)))}
    private fun render() {
        val focus=list.indexOfChild(list.focusedChild)
        list.removeAllViews()
        label("Ask with AttuneTV",28f)
        label("Runs on this TV. No Mac, PC, phone, or ongoing ADB connection is required.")
        label(chat.status)
        row("Continue with ChatGPT") {chat.signIn()}
        for((id,name) in chat.accountLabels) {
            row("${if(id==chat.activeId) "✓ " else ""}$name") {chat.selectAccount(id)}
        }
        if(chat.activeId.isNotBlank()) row("Reconnect selected account") {chat.signIn(chat.activeId)}
        if(chat.connected) {
            row("Sign out on this TV") {chat.signOut()}
            row("Refresh available models") {chat.refreshModels()}
            for((id,name) in chat.models) row("${if(id==chat.selectedModel) "✓ " else ""}$name") {chat.selectModel(id)}
        }
        label("Streaming app support",20f)
        val access=android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        if(access.resolveActivity(packageManager)!=null)row("Optional: allow playback metadata access") {startActivity(access)}
        else label("This Fire TV does not offer Android's playback-metadata access screen. You can enter the title directly; chat does not require that permission.")
        label(if(StreamingSupport.verifiedPackages.isEmpty()) "Provider verification is pending. Chat stays hidden inside streaming apps until each app passes native TV tests."
            else StreamingSupport.verifiedPackages.joinToString {StreamingSupport.candidates[it].orEmpty()})
        if(StreamingSupport.verifiedPackages.isNotEmpty()) {
            val prefs=getSharedPreferences("native-chat",MODE_PRIVATE)
            row("Hold Menu shortcut: ${if(prefs.getBoolean("enabled",false)) "On" else "Off"}") {
                prefs.edit().putBoolean("enabled",!prefs.getBoolean("enabled",false)).apply()
                HomeButtonService.instance?.watchCompanionWindows(false);render()
            }
        }
        row("Try the native panel on this screen") {
            val service=HomeButtonService.instance
            if(service==null) android.widget.Toast.makeText(this,"Enable AttuneTV Home redirection first.",android.widget.Toast.LENGTH_LONG).show()
            else service.nativePanel.show(packageName,preview=true)
        }
        label("Use the TV keyboard and remote. Back closes the panel. Movie, episode and scene context are entered by you in this build. It does not see or hear the video. ChatGPT plan eligibility and limits apply; answers and spoiler avoidance are not guaranteed.")
        row("Back") {finish()}
        (list.getChildAt(focus)?.takeIf {it is Button} ?: list.getChildAt(3))?.requestFocus()
    }
}
