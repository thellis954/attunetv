package com.attunetv.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SearchActivity : TvActivity() {
    private lateinit var query: EditText
    private lateinit var grid: RecyclerView
    private lateinit var adapter: AppAdapter
    private lateinit var prefs: Prefs
    private var apps: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        prefs = Prefs(this)
        query = findViewById(R.id.search_query)
        grid = findViewById(R.id.search_grid)
        Wallpaper.current?.let { findViewById<ImageView>(R.id.search_wall).setImageBitmap(it.blurred) }
        val width = resources.displayMetrics.widthPixels - (100 * resources.displayMetrics.density).toInt()
        val tile = resources.getDimensionPixelSize(R.dimen.card_w) + 2 * resources.getDimensionPixelSize(R.dimen.tile_pad)
        grid.layoutManager = GridLayoutManager(this, (width / tile).coerceIn(2, 8))
        adapter = AppAdapter(::launch, {}, { findViewById<TextView>(R.id.search_selection).text = it.label })
        grid.adapter = adapter
        grid.itemAnimator = null
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = filter()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        query.setOnEditorActionListener { _, _, _ -> focusResults(); true }
        findViewById<View>(R.id.search_movies).setOnClickListener { openMovies() }
        query.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        apps = AppRepository(this).loadApps(prefs).filter { !it.hidden || prefs.showHidden }
            .sortedBy { it.label.lowercase() }
        filter()
    }

    private fun filter() {
        adapter.submit(apps.filter { AppSearch.matches(it.label, query.text.toString()) })
        findViewById<TextView>(R.id.search_count).text = getString(R.string.search_apps_count, adapter.itemCount)
        findViewById<View>(R.id.search_empty).visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.search_selection).setText(R.string.search_hint)
        grid.scrollToPosition(0)
    }

    private fun focusResults() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(query.windowToken, 0)
        grid.post {
            if (adapter.itemCount > 0) grid.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            else findViewById<View>(R.id.search_movies).requestFocus()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && currentFocus === query) {
                focusResults(); return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                val item = currentFocus?.let { grid.findContainingItemView(it) }
                val position = item?.let { grid.getChildAdapterPosition(it) } ?: -1
                if (position in 0 until (grid.layoutManager as GridLayoutManager).spanCount) {
                    query.requestFocus(); return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun launch(app: AppEntry) {
        try {
            startActivity(app.launchIntent)
            prefs.markLaunched(app.key)
            prefs.recent = listOf(app.key) + (prefs.recent - app.key)
        } catch (_: Exception) { Toast.makeText(this, getString(R.string.cant_open, app.label), Toast.LENGTH_SHORT).show() }
    }

    private fun openMovies() {
        // The launcher exposes this navigation link to ordinary apps; its direct
        // SHOW_TEXT_SEARCH intent requires an Amazon signature permission.
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("amzn://com.amazon.tv.launcher/menuNavigation?locationID=l_text_search"))
            .setPackage("com.amazon.tv.launcher")
        try { startActivity(intent) }
        catch (_: Exception) { Toast.makeText(this, R.string.search_unavailable, Toast.LENGTH_LONG).show() }
    }
}
