package com.metrolist.music.utils.cipher

import android.content.Context

object CipherDeobfuscator {
    lateinit var appContext: Context
        private set

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
}
