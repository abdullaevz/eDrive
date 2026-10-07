package com.edrive.app.ui

import com.edrive.app.drive.DriveException
import java.io.IOException

/** Xətanı istifadəçiyə göstəriləcək qısa mətnə çevirir (ViewModel-lər üçün ortaq). */
fun Throwable.userMessage(): String = when (this) {
    is IOException -> "İnternet bağlantısını yoxlayın"
    is DriveException -> if (code == 403) "Google Drive girişi rədd edildi (403). Drive API aktivdirmi?" else message ?: "Drive xətası"
    is com.google.android.gms.common.api.ApiException ->
        "Google girişi alınmadı (kod $statusCode). Google Cloud Console-da Android OAuth client (paket adı + SHA-1) qeyd olunubmu?"
    else -> message ?: "Gözlənilməz xəta"
}
