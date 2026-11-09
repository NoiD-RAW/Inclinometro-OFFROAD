package com.example.inclinometro

import android.app.Application

/** Arranque de la app: carga los ajustes guardados antes de abrir cualquier pantalla. */
class App : Application() {
    override fun onCreate() { super.onCreate(); Config.init(this) }
}
