package com.edrive.app.data

/**
 * Təhlükəsizlik açarı (vault parolu) qaydaları. Bu açardan DEK-i qoruyan KEK törədilir və `vault.json`
 * Drive-da saxlandığı üçün oflayn sınama riski var — ona görə minimum uzunluq 12 simvoldur.
 */
object SecurityKeyPolicy {
    const val MIN_LENGTH = 12

    enum class Strength(val label: String) { WEAK("Zəif"), FAIR("Orta"), STRONG("Güclü") }

    fun validate(key: CharArray, confirm: CharArray) {
        if (key.size < MIN_LENGTH) throw AccountException("Təhlükəsizlik açarı ən azı $MIN_LENGTH simvol olmalıdır")
        if (!key.contentEquals(confirm)) throw AccountException("Açarlar uyğun gəlmir")
    }

    /**
     * Sadə güc qiyməti: uzunluq və simvol növləri (kiçik/böyük hərf, rəqəm, digər).
     * Uzun söz birləşməsi (passphrase) qısa mürəkkəb paroldan güclü sayılır.
     */
    fun strength(key: CharArray): Strength {
        if (key.size < MIN_LENGTH) return Strength.WEAK
        val classes = listOf(
            key.any { it.isLowerCase() },
            key.any { it.isUpperCase() },
            key.any { it.isDigit() },
            key.any { !it.isLetterOrDigit() },
        ).count { it }
        if (key.distinct().size < 6) return Strength.WEAK
        return when {
            key.size >= 20 || (key.size >= 14 && classes >= 3) -> Strength.STRONG
            classes >= 2 -> Strength.FAIR
            else -> Strength.WEAK
        }
    }
}
