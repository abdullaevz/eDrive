package com.edrive.app.util

/** Cari vaxt (epoch ms). Testlərdə vaxtı idarə etmək üçün abstraksiya. */
fun interface Clock {
    fun now(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}
