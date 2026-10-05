# eDrive Android

Kotlin + Jetpack Compose. Fayllar telefonda **AES-256-GCM** ilə şifrələnir və istifadəçinin öz Google Drive-ındakı **eDrive Storage** qovluğuna yalnız şifrəli halda yüklənir.

## Yüklə

Hazır APK [Releases](https://github.com/abdullaevz/eDrive/releases/latest) bölməsindədir. Faylı telefona yükləyib açın. Tətbiq Play Store-dan olmadığı üçün ilk dəfə "naməlum mənbələrdən quraşdırma" icazəsi soruşulacaq.

## Özünüz yığmaq

Android SDK lazımdır (Android Studio və ya IntelliJ IDEA).

```bash
./gradlew assembleDebug   # → app/build/outputs/apk/debug/app-debug.apk
./gradlew test            # bütün testlər
```

Windows-da `gradlew.bat` istifadə edin.

### Google Drive girişini qurmaq

Tətbiq Google Drive-a OAuth ilə qoşulur. Öz yığımınızda "Google ilə qoşul" düyməsinin işləməsi üçün öz Google Cloud layihənizi yaratmalısınız.

1. **Paket adını dəyişin.** `app/build.gradle.kts` faylında `applicationId` dəyərini özünüzə məxsus bir adla əvəz edin (məsələn, `com.sizinadınız.edrive`). Google eyni paket adı və SHA-1 cütünü yalnız bir OAuth client-ə bağlamağa icazə verir.
2. **SHA-1-i öyrənin:**
   ```bash
   ./gradlew signingReport
   ```
3. [console.cloud.google.com](https://console.cloud.google.com) saytında:
   1. Yeni layihə yaradın.
   2. **APIs & Services → Library → Google Drive API → Enable**.
   3. **Google Auth Platform / OAuth consent screen**:
      - Audience: **External**;
      - test rejimində **Test users** siyahısına öz Gmail ünvanınızı əlavə edin;
      - Data access (scope): `https://www.googleapis.com/auth/drive.file`.
   4. **Credentials → Create credentials → OAuth client ID → Android**: paket adı (debug yığımı üçün sonuna `.debug` əlavə olunur) və 2-ci addımdakı SHA-1.

Web client ID və ya API key **lazım deyil**: tətbiq yalnız Drive icazəsi istəyir (AuthorizationClient), e-poçtu isə Drive API-dən oxuyur.

## Necə işləyir

```
istifadəçi adı + parol ──Argon2id (64 MiB)──► KEK ──► DEK (vault açarı, yalnız RAM-da)
                                                       └──► hər faylın öz açarı (AES-256-GCM)

Yükləmə:  fayl → telefonda şifrələnir → outbox (yalnız şifrəli) → WorkManager → Drive
Drive:    eDrive Storage/<istifadəçi>/vault.json   (salt + şifrəli DEK, sirr deyil)
                                     /<id>.edrv     (şifrəli məzmun)
                                     /<id>.meta     (şifrəli ad, tip, ölçü + miniatür)
```

- **Parol saxlanılmır.** Lokal SQLite-da (Room) yalnız vault başlığı durur. Giriş zamanı parol düzgündürsə DEK açılır, səhvdirsə açılmır.
- **Bərpa sənədi:** qeydiyyatdan sonra istifadəçi adı və parol olan PDF istifadəçinin seçdiyi yerə yazılır. Bu addım məcburidir.
- **Barmaq izi:** DEK Android Keystore açarı ilə şifrələnir. Açar yalnız biometrik təsdiqdən sonra işləyir. Yeni barmaq izi əlavə olunsa, açar etibarsız olur.
- **Miniatürlər** şifrəli `.meta` daxilindədir. Qalereya dərhal açılır, telefondan silinmiş şəkillər də görünür. Tam şəkil toxunanda yaddaşda deşifrə olunur və diskə yazılmır.
- **Yeni telefon:** eyni istifadəçi adı və parolla qeydiyyat, sonra Drive-a qoşulma. Tətbiq Drive-dakı `vault.json`-u tapır, parolu soruşur və fayllar geri gəlir.
- **Format** Spring Boot `eDrive` layihəsi ilə bayt-bayt eynidir (`.edrv` v1, test ilə yoxlanılıb).
- **Təhlükəsizlik:**
  - `FLAG_SECURE` ekran görüntüsünü bloklayır;
  - tətbiq 60 saniyədən çox fonda qalsa avtomatik kilidlənir;
  - Android ehtiyat nüsxəsi söndürülüb.

## Struktur

```
crypto/                       saf Kotlin: AES-GCM, STREAM şifrə, vault başlığı, manifest (+ JVM testləri)
app/src/main/kotlin/com/edrive/app/
 ├─ EDriveApp.kt              AppContainer — bütün asılılıqlar burada qurulur (əl ilə DI)
 ├─ data/
 │   ├─ AccountRepository.kt  qeydiyyat, giriş, barmaq izi
 │   ├─ Session.kt            açıq vault (DEK yalnız RAM-da)
 │   ├─ db/                   Room: AppDatabase + entity/ + dao/
 │   └─ vault/                hər xidmət bir iş görür:
 │       ├─ DriveConnectionService   Drive-a qoşulma/ayrılma, vault başlığının uzlaşdırılması
 │       ├─ SyncService              Drive ↔ lokal indeks
 │       ├─ ImportService            faylı şifrələyib növbəyə qoymaq
 │       ├─ UploadService            növbəni Drive-a yükləmək (arxa fon)
 │       ├─ FileAccessService        miniatür, baxış, export, silmə
 │       ├─ LocalVaultStore          lokal şifrəli faylların yerləşməsi
 │       ├─ ThumbnailCache, UploadScheduler
 ├─ drive/                    DriveClient interfeysi + Google implementasiyası, OAuth
 ├─ security/                 Argon2id (native), BiometricKeyStore
 ├─ ui/                       Compose: auth, home, viewer, crash ekranı
 ├─ util/                     media, bərpa PDF-i, çökmə hesabatı
 └─ work/                     UploadWorker
```

Biznes məntiqi `DriveClient`, `DriveAuthorizer`, `BiometricKeyStore`, `UploadScheduler` interfeyslərindən asılıdır —
testlərdə saxta implementasiyalar istifadə olunur (`app/src/test/.../FakeDrive.kt`). `DriveFlowTest` bütün Drive axınını
(qoşulma → yükləmə → yeni telefonda bərpa → silmə → sinxronizasiya) internet olmadan yoxlayır.

## Məlum məhdudiyyətlər (v0.1)

- Video və PDF tətbiqdaxili pleyerdə deyil, müvəqqəti deşifrə olunub kənar tətbiqdə açılır. Müvəqqəti fayl kilid zamanı silinir.
- Kəsilən yükləmə növbəti dəfə əvvəldən başlayır (resumable davamı hələ yoxdur).
