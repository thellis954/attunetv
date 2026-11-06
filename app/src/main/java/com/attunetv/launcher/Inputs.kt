package com.attunetv.launcher

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** HDMI / antenna inputs exposed by the TV (Fire TV Edition sets). */
object Inputs {
    data class Input(val id: String, val label: String, val intent: Intent)

    fun list(context: Context): List<Input> {
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        return try {
            tim.tvInputList.map { info ->
                val uri = if (info.isPassthroughInput) TvContract.buildChannelUriForPassthroughInput(info.id)
                else TvContract.buildChannelsUriForInput(info.id)
                Input(
                    info.id,
                    labelFor(context, info),
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.sortedBy { it.label }
        } catch (_: Exception) { emptyList() }
    }

    private fun labelFor(context: Context, info: TvInputInfo): String {
        val custom = try { info.loadCustomLabel(context)?.toString() } catch (_: Exception) { null }
        val base = info.loadLabel(context)?.toString().orEmpty()
        return when {
            !custom.isNullOrBlank() -> custom
            base.isNotBlank() -> base
            info.type == TvInputInfo.TYPE_HDMI -> "HDMI"
            info.type == TvInputInfo.TYPE_TUNER -> "Antenna"
            else -> "Input"
        }
    }
}
