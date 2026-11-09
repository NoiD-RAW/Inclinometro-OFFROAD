package com.example.inclinometro

import android.app.AlertDialog
import android.content.Context

/** Diálogos comunes. Las acciones que no se pueden deshacer siempre piden confirmación. */
object Dialogs {
    private fun builder(ctx: Context) = AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_Alert)

    fun confirm(ctx: Context, title: String, message: String, ok: String = "Confirmar", onOk: () -> Unit) {
        val d = builder(ctx).setTitle(title).setMessage(message)
            .setPositiveButton(ok) { _, _ -> onOk() }
            .setNegativeButton("Cancelar", null)
            .show()
        bigButtons(ctx, d)
    }

    /** Botones del diálogo grandes y fáciles de acertar con el coche en movimiento. */
    private fun bigButtons(ctx: Context, d: AlertDialog) {
        val dp = ctx.resources.displayMetrics.density
        for (w in intArrayOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)) d.getButton(w)?.apply {
            textSize = 18f; minHeight = (56 * dp).toInt(); minWidth = (120 * dp).toInt()
        }
    }
}
