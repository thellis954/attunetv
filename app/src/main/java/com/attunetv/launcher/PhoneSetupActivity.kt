package com.attunetv.launcher

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Shows a QR code + URL; while open, a phone on the same Wi-Fi can configure AttuneTV. */
class PhoneSetupActivity : TvActivity() {

    private var server: SetupServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_setup)
        Backgrounds.applyDimmed(this, Prefs(this))
    }

    override fun onStart() {
        super.onStart()
        val s = SetupServer(this) {
            Toast.makeText(this, R.string.phone_saved, Toast.LENGTH_SHORT).show()
        }
        s.start()
        server = s
        val ip = SetupServer.localIp()
        val url = ip?.let { "http://$it:${s.port}/?pin=${s.pin}" }
        findViewById<TextView>(R.id.setup_url).text = url ?: getString(R.string.phone_no_network)
        findViewById<TextView>(R.id.setup_pin).text = getString(R.string.phone_pin, s.pin)
        url?.let { findViewById<ImageView>(R.id.setup_qr).setImageBitmap(qr(it, 560)) }
    }

    override fun onStop() {
        server?.stop()
        server = null
        super.onStop()
    }

    private fun qr(text: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size) { i -> if (m[i % size, i / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }
}
