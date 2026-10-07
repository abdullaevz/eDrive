# eDrive Android

Kotlin + Jetpack Compose. Fayllar telefonda **AES-256-GCM** ilə şifrələnir və istifadəçinin öz Google Drive-ındakı **eDrive Storage** qovluğuna yalnız şifrəli halda yüklənir.

Məxfilik siyasəti: [PRIVACY.md](PRIVACY.md) · İstifadə şərtləri: [TERMS.md](TERMS.md). Tərtibatçı heç bir məlumat toplamır.

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
PIN (4 rəqəm) ──► yalnız proqramın qapısı (Keystore HMAC ilə yoxlanılır, şifrələməyə təsiri yoxdur)

Təhlükəsizlik açarı ──Argon2id (64 MiB)──► KEK ──► DEK (vault açarı)
                                                   └──► hər faylın öz açarı (AES-256-GCM)
DEK bu cihazda: Keystore açarı ilə sarılı (PIN-dən sonra avtomatik açılır), açıq halda yalnız RAM-da

Yükləmə:  fayl → telefonda şifrələnir → outbox (yalnız şifrəli) → WorkManager → Drive
Drive:    eDrive Storage/vault.json        (salt + şifrəli DEK, sirr deyil)
                        /<id>.edrv          (şifrəli məzmun)
                        /<id>.meta          (şifrəli ad, tip, ölçü + miniatür)
                        /<qovluq adı>/...   (istifadəçinin alt qovluqları — adı açıq mətndir)
```

- **Hesab:** istifadəçi adı + 4 rəqəmli PIN. PIN yalnız bu telefonda proqramı açır; 5 yanlış cəhddən sonra artan gözləmə (1 dəq → 5 → 15 → 60). Barmaq izi də yalnız qapıdır.
- **Vault Drive-a bağlıdır:** Drive-a ilk qoşulanda "Təhlükəsizlik açarı" (ən azı 12 simvol) təyin edilir və `vault.json` yaranır. Eyni Drive-a qoşulan hər cihaz/hesab həmin açarla eyni faylları görür. Açar heç yerdə saxlanılmır.
- **Sənədlər:** PIN sənədi (qeydiyyatda) və Təhlükəsizlik açarı sənədi (vault yaradılanda və açar dəyişəndə; açar + `vault.json` nüsxəsi) istifadəçinin seçdiyi yerə PDF kimi yazılır.
- **Açarın dəyişdirilməsi** eyni DEK-i yeni açarla yenidən sarır — fayllar yenidən şifrələnmir. Digər cihazlar açılışda bunu `keyId` müqayisəsi ilə görür.
- **Miniatürlər** şifrəli `.meta` daxilindədir. Qalereya dərhal açılır, telefondan silinmiş şəkillər də görünür. Tam şəkil toxunanda yaddaşda deşifrə olunur və diskə yazılmır.
- **Yeni telefon:** istənilən adla hesab → Drive-a qoşulma → Təhlükəsizlik açarı. Fayllar və qovluqlar geri gəlir.
- **Drive-dan ayrılma** telefondakı bütün izləri (şifrəli nüsxələr, miniatürlər, vault, cihaz açarı) silir; Drive-a və qalereyadakı orijinallara toxunulmur.
- **Format** Spring Boot `eDrive` layihəsi ilə bayt-bayt eynidir (`.edrv` v1, test ilə yoxlanılıb).
- **Təhlükəsizlik:**
  - `FLAG_SECURE` ekran görüntüsünü bloklayır;
  - tətbiq 60 saniyədən çox fonda qalsa avtomatik kilidlənir;
  - Android ehtiyat nüsxəsi söndürülüb.

## Struktur

```
crypto/                       saf Kotlin: AES-GCM, STREAM şifrə, ixtiyari mövqedən deşifrə (RandomAccessDecryptor), vault başlığı, manifest (+ JVM testləri)
app/src/main/kotlin/com/edrive/app/
 ├─ EDriveApp.kt              @HiltAndroidApp; di/ qovluğunda Hilt modulları (AppModule, BindingsModule)
 ├─ data/
 │   ├─ AccountRepository.kt  qeydiyyat, PIN/barmaq izi ilə giriş, 1.x keçidi
 │   ├─ VaultService.kt       vault açarı: yaratma, götürmə, cihazda saxlama, açar dəyişmə
 │   ├─ PinPolicy, SecurityKeyPolicy   PIN və açar qaydaları (saf məntiq)
 │   ├─ Session.kt            açıq profil + DEK (yalnız RAM-da)
 │   ├─ db/                   Room: AppDatabase + entity/ + dao/ + Migrations
 │   └─ vault/                hər xidmət bir iş görür:
 │       ├─ DriveConnectionService   Drive-a qoşulma/ayrılma, vault-un yaradılması/götürülməsi, açar dəyişmə
 │       ├─ RemoteVaultMonitor       Drive-dakı vault.json-un yerli nüsxə ilə müqayisəsi (keyId)
 │       ├─ FolderService            alt qovluqlar: yaratma, ad, silmə, faylları köçürmə
 │       ├─ SyncService              Drive ↔ lokal indeks
 │       ├─ ImportService            faylı şifrələyib növbəyə qoymaq
 │       ├─ UploadService            növbəni Drive-a yükləmək (arxa fon)
 │       ├─ FileAccessService        miniatür, baxış, export, silmə
 │       ├─ LocalVaultStore          lokal şifrəli faylların yerləşməsi
 │       ├─ ThumbnailCache, UploadScheduler
 ├─ drive/                    DriveClient interfeysi + Google implementasiyası, OAuth
 ├─ media/                    EncryptedDataSource: ExoPlayer üçün şifrəli mənbə (video)
 ├─ security/                 Argon2id (native), PinHasher, DeviceKeyStore, BiometricGate (Keystore)
 ├─ ui/                       Compose: auth, home (süzgəc, problem fayllar), drive (açar dialoqları), folders, viewer, crash ekranı
 ├─ util/                     media, PDF sənədlər (pdf/), çökmə hesabatı
 └─ work/                     UploadWorker
```

Biznes məntiqi `DriveClient`, `DriveAuthorizer`, `PinHasher`, `DeviceKeyStore`, `BiometricGate`, `UploadScheduler` interfeyslərindən asılıdır —
testlərdə saxta implementasiyalar istifadə olunur (`app/src/test/.../FakeDrive.kt`, `TestPhone.kt`). `DriveFlowTest` bütün Drive axınını
(qoşulma və açar → yükləmə → yeni telefonda bərpa → silmə → açar dəyişmə → ayrılma, 1.x quruluşundan keçid), `FolderFlowTest` qovluqları,
`MigrationTest` Room 1 → 2 miqrasiyasını internet olmadan yoxlayır.

## Məlum məhdudiyyətlər

- Video tətbiqdaxili pleyerdə oynayır (yaddaşda deşifrə, diskə açıq mətn yazılmır), amma oynamazdan əvvəl şifrəli fayl tam endirilir; Drive-dan axınla oxuma hələ yoxdur.
- PDF tətbiqdaxili baxışda deyil, müvəqqəti deşifrə olunub kənar tətbiqdə açılır. Müvəqqəti fayl kilid zamanı silinir.
- Kəsilən yükləmə növbəti dəfə əvvəldən başlayır (resumable davamı hələ yoxdur).
