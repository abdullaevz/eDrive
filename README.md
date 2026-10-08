# eDrive Android

**eDrive** — şəkil, video və sənədlərinizi öz Google Drive-ınızda **şifrəli** saxlayan Android tətbiqidir.

## Nə üçündür

Buludda saxlanan fayllar adətən xidmət tərəfindən oxuna bilir. eDrive faylları göndərməzdən əvvəl telefonun özündə şifrələyir. Drive-a yalnız oxunmaz məlumat çatır: məzmun da, fayl adları da, miniatürlər də şifrəlidir. Faylları yalnız sizin **Təhlükəsizlik açarınızı** bilən açır. Bu açarı nə Google, nə də tərtibatçı bilir.

- Fayllar sizin öz Drive-ınızdadır, ayrıca server yoxdur.
- Qalereya, qovluqlar, video və şəkil baxışı tətbiqin içindədir. Açılan fayl yaddaşda deşifrə olunur və diskə açıq halda yazılmır.
- Bir neçə telefon eyni Drive-a qoşula bilər. Yeni telefonda açarı yazanda bütün fayllar geri gəlir.

Məxfilik siyasəti: [PRIVACY.md](PRIVACY.md) · İstifadə şərtləri: [TERMS.md](TERMS.md). Tərtibatçı heç bir məlumat toplamır.

## Texnologiyalar

| Sahə | İstifadə olunan |
|---|---|
| Dil və UI | Kotlin, Jetpack Compose (Material 3), Navigation |
| Arxitektura | MVVM, Hilt (DI), Kotlin Coroutines / Flow |
| Lokal baza | Room (miqrasiyalarla), WorkManager (arxa fonda yükləmə) |
| Şifrələmə | AES-256-GCM (JCA), STREAM chunk şifrəsi, Argon2id (argon2kt, native) |
| Cihaz qoruması | Android Keystore (PIN yoxlaması, cihazda açar), BiometricPrompt |
| Bulud | Google Drive REST API v3 (`drive.file` icazəsi), Google Identity AuthorizationClient, OkHttp |
| Media | Media3 ExoPlayer (şifrəli mənbədən video), ExifInterface |
| Digər | kotlinx.serialization, Android PDF (açar və PIN sənədləri) |
| Test | JUnit 4, Robolectric, Roborazzi (ekran testləri), kotlinx-coroutines-test, Bouncy Castle (JVM-də Argon2id) |
| Yığım | Gradle (Kotlin DSL, version catalog), AGP, KSP, R8; minSdk 26, Java 17 |

## Necə işləyir

1. **Hesab.** Proqram istifadəçi adı və 4 rəqəmli PIN ilə açılır (istəsəniz barmaq izi ilə). PIN yalnız bu telefondakı qapıdır, şifrələməyə təsiri yoxdur.
2. **Drive-a qoşulma.** Drive-a ilk qoşulanda **Təhlükəsizlik açarı** (ən azı 12 simvol) təyin edirsiniz. Bu açardan təsadüfi bir əsas açar (DEK) qorunur və Drive-dakı `eDrive Storage/vault.json` faylında şifrəli halda saxlanılır. Açarın özü heç yerə yazılmır, onun üçün PDF sənədi verilir.
3. **Gündəlik istifadə.** Açıq DEK telefonda Android Keystore ilə sarılı saxlanılır. PIN-dən sonra avtomatik açılır, açarı hər dəfə yazmaq lazım deyil.
4. **Yükləmə.** Seçilən fayl telefonda öz təsadüfi açarı ilə AES-256-GCM-lə şifrələnir. Həmin açar DEK ilə sarılıb faylın başlığına qoyulur. Fayl adı və miniatür ayrıca `.meta` faylında şifrələnir. Arxa fonda Drive-a `<id>.edrv` + `<id>.meta` kimi gedir, istifadəçinin yaratdığı alt qovluqlara da.
5. **Baxış.** Drive-dakı siyahı lokal indekslə sinxronlaşır. Miniatür və fayl açılanda DEK ilə yaddaşda deşifrə olunur.
6. **Yeni telefon / ikinci cihaz.** Eyni Drive-a qoşulub Təhlükəsizlik açarını yazırsınız. `vault.json`-dan DEK açılır, fayllar və qovluqlar geri gəlir. Açar başqa cihazda dəyişsə, bu, açılışda aşkarlanır.
7. **Ayrılma.** Drive-dan ayrılanda telefondakı bütün izlər (şifrəli nüsxələr, miniatürlər, cihaz açarı) silinir. Drive-dakı fayllara və qalereyadakı orijinallara toxunulmur.

```
Təhlükəsizlik açarı ──Argon2id──► KEK ──sarır──► DEK ──sarır──► hər faylın açarı ──► AES-256-GCM məzmun
PIN / barmaq izi ──► yalnız proqramın qapısı (Keystore)
```

Şifrələmə formatı Spring Boot `eDrive` layihəsi ilə bayt-bayt eynidir (`.edrv` v1). Əlavə qoruma: ekran görüntüsü bloklanır, 60 saniyədən çox fonda qalan tətbiq kilidlənir, Android ehtiyat nüsxəsi söndürülüb.

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
