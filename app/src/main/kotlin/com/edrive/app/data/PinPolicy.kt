package com.edrive.app.data

/**
 * PIN qaydaları (saf məntiq — Android-dən asılı deyil, unit testlə yoxlanılır).
 *
 * PIN yalnız proqramın qapısıdır. Uzunluq 4 rəqəmdir; bariz PIN-lər rədd edilir.
 * Hər [ATTEMPTS_PER_STEP] ardıcıl yanlış cəhddən sonra gözləmə artır: 1 dəq → 5 dəq → 15 dəq → 1 saat.
 */
object PinPolicy {
    const val LENGTH = 4
    const val ATTEMPTS_PER_STEP = 5

    private val LOCKOUT_STEPS_MS = longArrayOf(60_000L, 5 * 60_000L, 15 * 60_000L, 60 * 60_000L)

    fun validate(pin: CharArray) {
        if (pin.size != LENGTH || pin.any { it !in '0'..'9' }) throw AccountException("PIN $LENGTH rəqəmdən ibarət olmalıdır")
        if (isTrivial(pin)) throw AccountException("Bu PIN çox asandır — başqa birini seçin")
    }

    /** Eyni rəqəmlər (0000), düz ardıcıllıq (1234, 9876) və ən çox işlənən bir neçə PIN. */
    fun isTrivial(pin: CharArray): Boolean {
        val d = pin.map { it - '0' }
        if (d.distinct().size == 1) return true
        val steps = d.zipWithNext { a, b -> b - a }.distinct()
        if (steps == listOf(1) || steps == listOf(-1)) return true
        return String(pin) in COMMON
    }

    /** [failures] ardıcıl yanlış cəhddən sonra nə qədər gözləmək lazımdır (0 = gözləmə yoxdur). */
    fun lockoutMillis(failures: Int): Long {
        if (failures <= 0 || failures % ATTEMPTS_PER_STEP != 0) return 0
        val step = (failures / ATTEMPTS_PER_STEP - 1).coerceAtMost(LOCKOUT_STEPS_MS.lastIndex)
        return LOCKOUT_STEPS_MS[step]
    }

    private val COMMON = setOf("1212", "1122", "1313", "2580", "0852", "6969", "2000", "1010", "2020")
}
