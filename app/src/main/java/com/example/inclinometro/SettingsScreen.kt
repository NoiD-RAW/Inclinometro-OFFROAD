package com.example.inclinometro

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Pantalla de Ajustes, construida con las piezas del tema activo. Se reconstruye entera al cambiar algo. */
class SettingsScreen(private val a: MainActivity) {
    private val ctx: Context get() = a
    private val t get() = a.appTheme
    private val ui get() = t.ui
    private val accent get() = Themes.accent(t)
    val slots = mutableMapOf<String, ImageView>()

    private fun dp(v: Int) = (v * a.resources.displayMetrics.density).toInt()
    private fun btnLp() = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) }
    private fun button(text: String, filled: Boolean, onClick: () -> Unit) = ui.button(ctx, text, filled, accent, onClick).apply { layoutParams = btnLp() }

    fun build(): ScrollView {
        slots.clear()
        val themes = ui.section(ctx, "Tema", accent, listOf(themePicker()))
        val vehicle = ui.section(ctx, "Personaliza tu vehículo", accent, listOf(
            LinearLayout(ctx).apply { for (k in t.vehicleKinds) addView(slot(k), LinearLayout.LayoutParams(0, -2, 1f)) },
            ui.note(ctx, "PNG sin fondo. Lateral: con el morro a la izquierda. Trasera o frontal: vista recta." +
                (if ("top" in t.vehicleKinds) " Superior: con el morro hacia arriba." else "") + " La app recorta los márgenes vacíos y ajusta el tamaño sola. Cada tema guarda sus propias imágenes."),
            button("Subir fotos", true) { a.pickChain(t.vehicleKinds) },
            button("Cargar desde carpeta de la app", false) { a.loadFolder() },
            ui.note(ctx, "Sin selector de archivos: copia lateral.png, trasera.png y superior.png a la carpeta de la app."),
            button("Restablecer vehículo original", false) { a.resetVehicle() }))
        val color = ui.section(ctx, "Color principal", accent, listOf(colorSwatches(), hexRow()))
        val sensor = ui.section(ctx, "Montaje del sensor", accent, listOf(
            button(if (Config.flat) "Montaje: plano" else "Montaje: vertical", false) { Config.flat = !Config.flat; Engine.resetCal(); a.rebuildSettings() },
            button("Ejes: ${Config.rot * 90}°  (toca para girar 90°)", false) { Config.rot = (Config.rot + 1) % 4; Engine.resetCal(); a.rebuildSettings() },
            button(if (Config.invertPitch) "Cabeceo: invertido" else "Cabeceo: normal", false) { Config.invertPitch = !Config.invertPitch; Engine.resetCal(); a.rebuildSettings() }))
        val pip = ui.section(ctx, "Mini ventana (PiP)", accent, listOf(
            button("Abrir mini ventana ahora", false) { a.enterPip(false) },
            button(if (Config.autoPip) "Mini ventana al pulsar Inicio: sí" else "Mini ventana al pulsar Inicio: no", false) {
                Config.autoPip = !Config.autoPip; a.updatePipParams(); a.rebuildSettings()
            }))

        val wide = a.resources.configuration.screenWidthDp >= 840
        fun gap() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        val body = LinearLayout(ctx).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            if (wide) {                                                         // pantallas anchas: dos columnas
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(themes, LinearLayout.LayoutParams(-1, -2)); addView(vehicle, gap())
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(color, LinearLayout.LayoutParams(-1, -2)); addView(sensor, gap()); addView(pip, gap())
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
            } else {
                addView(themes, LinearLayout.LayoutParams(-1, -2))
                for (v in listOf(vehicle, color, sensor, pip)) addView(v, gap())
            }
        }
        val header = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.button(ctx, "←  Volver", false, accent) { a.showDash() }, LinearLayout.LayoutParams(dp(130), dp(50)))
            addView(ui.label(ctx, "Ajustes", 22f, ui.text).apply { setPadding(dp(16), 0, 0, 0) }, LinearLayout.LayoutParams(-2, -2))
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(16), dp(12), dp(16), dp(28))
            addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            addView(body, LinearLayout.LayoutParams(if (!wide && a.resources.configuration.screenWidthDp > 700) dp(640) else -1, -2))
        }
        return ScrollView(ctx).apply { background = ui.backgroundDrawable(ctx); addView(col) }
    }

    // ---------- selector de tema: miniatura de la pantalla real de cada tema ----------
    private fun themePicker() = LinearLayout(ctx).apply {
        val dm = a.resources.displayMetrics
        for (th in Themes.all) {
            val sel = th.id == t.id
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(6), dp(6), dp(6), dp(6))
                background = GradientDrawable().apply { setColor(Color.rgb(22, 22, 25)); cornerRadius = dp(10).toFloat(); setStroke(dp(if (sel) 4 else 2), if (sel) accent else Color.rgb(90, 90, 96)) }
                addView(ImageView(ctx).apply {
                    adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageBitmap(Themes.preview(ctx, th, dm.widthPixels, dm.heightPixels, dp(220)))
                }, LinearLayout.LayoutParams(-1, -2))
                addView(TextView(ctx).apply {
                    text = if (sel) "✓ ${th.name}" else th.name; textSize = 15f; setTextColor(if (sel) accent else ui.text); gravity = Gravity.CENTER
                    setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, dp(8), 0, dp(4))
                })
                contentDescription = "Tema ${th.name}"
                setOnClickListener { if (!sel) a.applyTheme(th.id) }
            }
            addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
    }

    // ---------- imágenes del vehículo ----------
    private val captionOf = mapOf("side" to "Lateral", "rear" to "Trasera / frontal", "top" to "Superior")
    private fun slot(kind: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(4), 0, dp(4), 0)
        val iv = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply { setColor(Color.rgb(22, 22, 25)); setStroke(dp(2), Color.rgb(90, 90, 96), dp(6).toFloat(), dp(4).toFloat()); cornerRadius = dp(10).toFloat() }
            setOnClickListener { a.pickChain(listOf(kind)) }
        }
        slots[kind] = iv
        addView(iv, LinearLayout.LayoutParams(-1, dp(92)))
        addView(ui.note(ctx, captionOf[kind]!!).apply { gravity = Gravity.CENTER })
    }

    // ---------- color principal ----------
    private fun swatch(c: Int) = View(ctx).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke(dp(if (c == accent) 4 else 2), if (c == accent) Color.WHITE else Color.rgb(20, 20, 22)) }
        setOnClickListener { a.setAccent(c) }
    }
    private fun colorSwatches() = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        for (row in 0..1) addView(LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            for (i in 0..3) addView(swatch(t.accentPresets[row * 4 + i]), LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(8), dp(6), dp(8), dp(6)) })
        })
    }

    private fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)
    private fun parseHex(s: String): Int? {
        var t = s.trim().removePrefix("#")
        if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
        if (t.length != 6 || !t.all { it in "0123456789abcdefABCDEF" }) return null
        return Color.parseColor("#$t")
    }
    private fun hexRow() = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        addView(ui.note(ctx, "Código de color HEX (por ejemplo #3FD0B2):"))
        val dot = View(ctx).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent); setStroke(dp(2), Color.WHITE) } }
        val field = EditText(ctx).apply {
            setText(hex(accent)); setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); hint = "#3FD0B2"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(7)); imeOptions = EditorInfo.IME_ACTION_DONE
            background = GradientDrawable().apply { setColor(Color.rgb(24, 26, 29)); setStroke(dp(2), Color.rgb(90, 92, 98)); cornerRadius = dp(10).toFloat() }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        fun applyHex() {
            val c = parseHex(field.text.toString())
            (a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(field.windowToken, 0)
            if (c == null) Toast.makeText(ctx, "Código no válido. Ejemplo: #3FD0B2", Toast.LENGTH_LONG).show() else a.setAccent(c)
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { parseHex(s.toString())?.let { (dot.background as GradientDrawable).setColor(it) } }
        })
        field.setOnEditorActionListener { _, act, _ -> if (act == EditorInfo.IME_ACTION_DONE) { applyHex(); true } else false }
        addView(LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(dot, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(10) })
            addView(field, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ui.button(ctx, "Aplicar", true, accent) { applyHex() }, LinearLayout.LayoutParams(dp(110), dp(48)).apply { leftMargin = dp(10) })
        })
    }
}
