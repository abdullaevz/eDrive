package com.edrive.app.util.pdf

import com.edrive.app.drive.DriveLayout
import com.edrive.crypto.VaultHeader
import java.io.OutputStream

/**
 * İstifadəçinin seçdiyi yerə (Storage Access Framework) yazılan iki sənəd. Proqram nə PIN-i, nə də
 * Təhlükəsizlik açarını saxlamır — onlar yalnız sənəd yaradılan anda yaddaşdadır.
 */
object RecoveryDocuments {

    /** PIN sənədi: hesab yaradılanda və PIN dəyişəndə. PIN yalnız bu telefonda proqramı açır. */
    fun writePin(out: OutputStream, username: String, pin: CharArray) = PdfSheet.write(out) {
        title("Giriş PIN-i", "Yaradılıb")
        fields("İstifadəçi adı" to username, "PIN" to String(pin))
        section("Bu sənəd nə üçündür", listOf(
            "PIN yalnız bu telefonda eDrive proqramını açır. Fayllarınız PIN ilə yox, Təhlükəsizlik açarı ilə şifrələnir.",
            "",
            "Sənədi gizli saxlayın: telefonunuz əlinə keçən və bu sənədi tapan şəxs proqrama daxil ola bilər.",
            "PIN-i dəyişsəniz, yeni sənəd yaradılır — köhnəsini məhv edin.",
        ), warning = true)
    }

    /**
     * Təhlükəsizlik açarı sənədi: vault yaradılanda və açar dəyişəndə. Açar + `vault.json`-un tam nüsxəsi —
     * bu sənəd Drive-dakı şifrəli faylları tək başına aça bilər.
     */
    fun writeSecurityKey(out: OutputStream, email: String, key: CharArray, header: VaultHeader) = PdfSheet.write(out) {
        title("Təhlükəsizlik açarı", "Yaradılıb")
        fields("Google Drive" to email, "Təhlükəsizlik açarı" to String(key), "Açar ID (keyId)" to header.keyId)
        section("DİQQƏT", listOf(
            "Bu sənəd Google Drive-dakı \"${DriveLayout.ROOT_FOLDER}\" qovluğundakı bütün faylları aça bilər. " +
                "Onu çap edib təhlükəsiz yerdə saxlayın; telefonda, e-poçtda və ya buludda açıq saxlamayın.",
            "",
            "Açarı unutsanız və bu sənəd itsə, fayllarınızı bərpa etmək MÜMKÜN DEYİL — nə siz, nə də tətbiq tərtibatçısı onları aça bilər.",
            "",
            "Açarı dəyişsəniz, yeni sənəd yaradılır. Köhnə sənəd köhnə açarla hələ də işləyə bilər — onu məhv edin.",
        ), warning = true)
        section("Yeni telefonda bərpa", listOf(
            "eDrive-ı quraşdırın → istənilən adla hesab yaradın → Google Drive-a qoşulun → Təhlükəsizlik açarını daxil edin.",
        ))
        section("vault.json nüsxəsi", listOf(
            "Drive-dakı \"${DriveLayout.ROOT_FOLDER}/${DriveLayout.VAULT_FILE}\" silinsə, aşağıdakı mətni eyni adlı fayl kimi " +
                "həmin qovluğa yazmaqla vault bərpa olunur. Bu mətn açarsız heç nəyi açmır.",
        ))
        code(header.toJson())
    }
}
