# Yeni versiya necə çıxarılır

Release APK debug ilə eyni koddan yığılır. Fərq: imza, paket adı (`com.edrive.app`) və kodun sıxılmasıdır.

## Bir dəfəlik
1. Release keystore (`edrive-release.jks`) proyektdən kənarda saxlanılır, ehtiyat nüsxəsi alınır.
2. `keystore.properties.example` faylını `keystore.properties` adı ilə kopyalayıb doldurun (git-ə düşmür).
3. Google Cloud Console-da `com.edrive.app` + release SHA-1 üçün Android OAuth client yaradılır.

## Hər yeni versiyada
1. `app/build.gradle.kts`-də `versionCode` artırılır (heç vaxt azaldılmır), `versionName` dəyişdirilir.
2. `CHANGELOG.md` yenilənir.
3. `UserEntity` / `FileEntity` dəyişibsə: Room `version` artırılır, Migration yazılır, `app/schemas` commit olunur.
4. Testlər: `gradlew.bat test`
5. Yığma: `gradlew.bat assembleRelease` → `app\build\outputs\apk\release\app-release.apk`
6. Əvvəlki release-in üstünə quraşdırıb məlumatların qaldığını yoxlayın. Google ilə qoşulmanı ikinci hesabla sınayın.
7. `app\build\outputs\mapping\release\mapping.txt` faylı `eDrive-<versiya>-mapping.txt` adı ilə repodan kənarda saxlanılır.
8. Tag: `git tag vX.Y.Z` və `git push origin vX.Y.Z`
9. GitHub → Releases → Draft a new release: tag seçilir, APK `eDrive.apk` adı ilə əlavə olunur, qeydlər `CHANGELOG.md`-dən götürülür. v0.x versiyalar "pre-release" işarələnir.

## Heç vaxt dəyişməməlidir
- Release keystore
- Paket adı `com.edrive.app`
- `versionCode`-un azalması
- `.edrv` formatı (dəyişərsə köhnə fayllar açılmaz)
