package com.example.inclinometro

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout

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

    /** Dos opciones, cada una con su acción (por ejemplo «Permitir» / «Grabar sin GPS»). */
    fun choice(ctx: Context, title: String, message: String, yes: String, no: String, onYes: () -> Unit, onNo: () -> Unit) {
        val d = builder(ctx).setTitle(title).setMessage(message)
            .setPositiveButton(yes) { _, _ -> onYes() }
            .setNegativeButton(no) { _, _ -> onNo() }
            .setCancelable(false)
            .show()
        bigButtons(ctx, d)
    }

    /** Lista de opciones; [onPick] recibe la posición elegida. */
    fun pick(ctx: Context, title: String, items: List<String>, onPick: (Int) -> Unit) {
        builder(ctx).setTitle(title).setItems(items.toTypedArray()) { _, i -> onPick(i) }.setNegativeButton("Cancelar", null).show()
    }

    /** Pide un texto (por ejemplo, el nombre de una salida). */
    fun prompt(ctx: Context, title: String, initial: String, ok: String = "Guardar", onOk: (String) -> Unit) {
        val dp = ctx.resources.displayMetrics.density
        val field = EditText(ctx).apply { setText(initial); setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; selectAll(); textSize = 18f }
        val box = FrameLayout(ctx).apply { setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0); addView(field) }
        val d = builder(ctx).setTitle(title).setView(box)
            .setPositiveButton(ok) { _, _ -> field.text.toString().trim().let { if (it.isNotEmpty()) onOk(it) } }
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
