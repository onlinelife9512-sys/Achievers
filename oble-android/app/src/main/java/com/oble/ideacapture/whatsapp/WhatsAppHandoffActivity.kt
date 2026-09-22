package com.oble.ideacapture.whatsapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.oble.ideacapture.ObleApp
import kotlinx.coroutines.runBlocking

/**
 * Invisible activity started by the "Send to WhatsApp" notification action.
 * A notification may start an activity (a user tap is a foreground interaction),
 * which is the compliant way to open WhatsApp from a background event.
 */
class WhatsAppHandoffActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ObleApp).container
        val ids = intent.getLongArrayExtra(EXTRA_IDS)?.toList().orEmpty()
        // Small DB reads; safe to run synchronously for this one-shot trampoline.
        val result = runBlocking {
            val ideas = ids.mapNotNull { container.repository.getIdea(it) }
            container.whatsApp.openInWhatsApp(this@WhatsAppHandoffActivity, ideas)
        }
        if (result == WhatsAppDispatcher.OpenResult.WHATSAPP_NOT_INSTALLED) {
            Toast.makeText(this, "WhatsApp is not installed. Use Share from the Ideas screen.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_IDS = "idea_ids"

        fun intent(context: Context, ids: LongArray): Intent =
            Intent(context, WhatsAppHandoffActivity::class.java)
                .putExtra(EXTRA_IDS, ids)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    }
}
