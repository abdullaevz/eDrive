# Məlum problemlər və gələcək işlər

Analiz tarixi: 2026-10-06 (v0.1.0), yenilənib: 2026-10-07 (v1.2.0). Hər bənd üçün: problem → təklif olunan həll. Prioritet: 🔴 yüksək · 🟡 orta · 🟢 aşağı. ~~Üstündən xətt çəkilmiş~~ bəndlər həll olunub.

## 1.2.0 modelindən qalan risklər

1.2.0-da PIN yalnız proqramın qapısıdır, vault açarı (DEK) isə cihazda Keystore ilə sarılı saxlanılır. Şifrələmə alqoritmi zəifləməyib; risklər cihaz tərəfindədir. Ətraflı təhlil: "eDrive: risklər və təhlükəsizlik planı" sənədi.

- 🟡 **4 rəqəmli PIN** (10 000 variant). Açıq telefonu əldə edən şəxs üçün yeganə maneə pilləli gözləmədir (1 dəq → 5 → 15 → 60).
  → istəyə görə 6 rəqəmli PIN seçimi.
- 🟡 **DEK cihazda daimi saxlanılır** (`DeviceKeyStore`). Root olunmuş və ya zərərli proqramla zədələnmiş cihazda PIN qapısı keçilə bilər.
  → Keystore açarına `setUnlockedDeviceRequired(true)` və (varsa) `StrongBox`; kilid zamanı DEK yaddaşdan silinsin və deşifrə keşi təmizlənsin.
- 🟢 **PIN sayğacı bazada** (`failedAttempts`, `lockedUntil`), root cihazda sıfırlana bilər; vaxt divar saatına əsaslanır.
  → sayğacı Keystore HMAC ilə imzalamaq, `elapsedRealtime` istifadəsi.
- 🟢 **Avtomatik kilid** yalnız fonda 60 san-dan sonra. → ekran sönəndə dərhal kilid, ayarlarda kilid vaxtı.
- 🟡 **Açar sənədində Təhlükəsizlik açarı açıq yazılır** (istifadəçinin qərarı). Sənəd sızarsa, Drive-dakı faylları tək başına aça bilər.
- 🟡 **Açar dəyişəndən sonra köhnə açar + köhnə `vault.json` nüsxəsi hələ də DEK-i açır.** Drive faylın əvvəlki versiyalarını bir müddət saxlaya bilər.
  → dəyişmədən sonra `revisions.delete`; tam həll: DEK-i dəyişib faylları yenidən şifrələmək.
- 🟡 **Qovluq adları Drive-da açıq mətndir** (dizayn qərarı). UI-da xəbərdarlıq var.
- 🟢 **Argon2 parametrləri** (64 MiB) yeni vault-lar üçün artırıla bilər (128 MiB) — zəif telefonlarda sınaq lazımdır.
- 🟢 **PIN-i dəyişmə:** məntiq var (`AccountRepository.changePin`), ekranda düymə yoxdur; PIN dəyişəndə yeni PIN sənədi.
- 🟢 **PIN unudulsa** hələ heç bir yol yoxdur. → Təhlükəsizlik açarı ilə PIN-i sıfırlamaq.
- 🟢 **Parol dəyişməsi bildirişi:** digər cihaz yalnız bildiriş göstərir; yeni açarı yenidən tələb etmək sonra nəzərdən keçiriləcək.

## Təhlükəsizlik

- ~~🔴 **Parol gücü.**~~ 1.2.0: Təhlükəsizlik açarı minimum 12 simvol, güc göstəricisi var. Qalan: tipik parol siyahısı yoxlaması.
- 🔴 **KDF parametrləri yoxlanılmır.** `VaultKeys.unlock` / `VaultService.adopt` parametrləri `vault.json`-dan olduğu kimi götürür (OOM/DoS riski).
  → `algorithm == argon2id`, `version == 1`, memoryKiB ~19 MiB–512 MiB, iterations 2–10, parallelism 1–4; kənarını rədd et.
- ~~🟡 **Parol dəyişdirmə yoxdur.**~~ 1.2.0: Təhlükəsizlik açarının dəyişdirilməsi (`VaultKeys.rewrap`), digər cihazlar açılışda `keyId` ilə görür.
- 🟡 **Açar sənədi açarı açıq mətnlə saxlayır** (`RecoveryDocuments`, 1.2.0-da istifadəçinin qərarı ilə).
  → gələcəkdə: təsadüfi 256-bit bərpa açarı; DEK ikinci dəfə onunla sarılsın (`recoveryWrappedDek`).
- 🟡 **Avtomatik kilid zamanlayıcı deyil.** `AutoLock` yalnız `onStart`-da yoxlayır; fonda DEK RAM-da qala bilər.
  → `onStop`-da zamanlayıcı (60 s), vaxt bitəndə `session.lock()`.
- 🟢 **Zəif KDF parametrlərinin yüksəldilməsi.** Uğurlu girişdən sonra daha güclü parametrlərlə yenidən sarmaq.
- 🟢 **Debug keystore repodadır** (`app/debug.keystore`, parol `android`). Debug OAuth client yalnız test rejimində qalmalıdır.

## Metadata sızıntısı (Drive-da görünən)

Məzmun, ad və miniatür şifrəlidir; amma kənardan görünür: istifadəçinin yaratdığı qovluq adları (1.2.0), fayl sayı, `.edrv` ölçüsü (≈ açıq mətn ölçüsü), `.meta` ölçüsü (miniatür var/yox), yükləmə/silmə vaxtları.

- ~~🟡 Qovluq adında istifadəçi adı~~ 1.2.0: `eDrive Storage/<istifadəçi adı>` artıq yoxdur, vault kökdədir (1.x qovluğu istifadəçi qovluğu kimi qalır).
- 🟡 `.meta`-nı sabit ölçüyə doldur (`ItemManifest`-ə `pad` sahəsi, `ignoreUnknownKeys` buna icazə verir; `.edrv` formatı dəyişmir).
- 🟢 `.edrv` ölçüsünü yuvarlaqlaşdırmaq → yeni format `v2` tələb edir (Spring Boot uyğunluğu pozulur; ayrıca qərar).
- 🟡 README/PRIVACY dəqiqləşdirilsin: "fayl adları görünmür" natamamdır; istifadəçi adı, say, ölçü və vaxtlar görünür.
- 🟢 Bütövlük: Drive-a yazma çıxışı olan şəxs faylı silə və ya eyni ID-nin köhnə versiyasını geri qoya bilər (GCM bunu aşkarlamır) → `.meta`-da versiya sayğacı.

## Yükləmə / Drive

- 🔴 **Təkrar yükləmədə dublikat.** `.edrv` yüklənib `.meta` uğursuz olsa, növbəti cəhd `.edrv`-i yenidən yükləyir; Drive eyni adlı faylları qəbul edir → yetim fayllar, `SyncService.associateBy` qeyri-müəyyən.
  → `driveDataId`-ni data yüklənən kimi bazaya yaz və təkrarda istifadə et (və ya yükləmədən əvvəl `findByName`).
- 🔴 **Xəta təsnifatı.** `UploadService` yalnız 429/5xx-i təkrar cəhd edir. Drive sürət limitini 403 (`rateLimitExceeded`/`userRateLimitExceeded`) ilə qaytarır; `storageQuotaExceeded` də 403-dür.
  → 403 səbəbini oxu: limit → retry, kvota → istifadəçiyə aydın mesaj.
- 🟡 **Resumable yükləmə davam etdirilmir.** Session açılır, amma kəsilmədə əvvəldən başlayır (Content-Range ilə sorğu + davam).
- 🟡 **Sinxronizasiya** hər yeni `.meta` üçün ardıcıl sorğu göndərir (minlərlə fayl üçün yavaş) → paralel/səhifələmə.

## Yaddaş və keş

- ~~🟡 `cache/blobs` heç vaxt təmizlənmir~~ — 2 GB limit, ən köhnələr silinir.
- 🟡 `FileAccessService.decryptToMemory` böyük şəkillərdə OOM riski daşıyır (tam ByteArray) → `inSampleSize` / axınla deşifrə.

## Kiçik məsələlər

- 🟢 Qeydiyyat ortasında proses ölsə, istifadəçi adı PIN sənədi olmadan lokal olaraq "tutulmuş" qalır (PIN ilə daxil olmaq mümkündür).
- 🟢 `UserEntity` data class-ında `ByteArray` → `equals`/`hashCode` xəbərdarlığı.
- 🟢 Testlər: `UploadService`/`SyncService` şəbəkə xəta yolları və KDF parametr yoxlaması üçün test yoxdur. Keystore siniflərinin (`KeystorePinHasher`, `KeystoreDeviceKeys`, `AndroidBiometricGate`) testi yalnız cihazda mümkündür.
- ~~🟢 Hələ heç bir Room migration yoxdur~~ — 1.2.0: Room 1 → 2 (`Migrations.MIGRATION_1_2` + `MigrationTest`).

## Planlaşdırılan funksiyalar

- ~~Tətbiqdaxili video pleyer~~ — 1–2-ci mərhələ edildi (2026-10-06): şifrəli nüsxə tam endirilir, sonra yaddaşda deşifrə ilə oynayır. Qalan: **Drive `Range` ilə axınla oxuma** (endirmədən başlasın) + şifrəli `SimpleCache`.
- Tətbiqdaxili PDF baxışı.
- Hesabın və Drive məlumatlarının tam silinməsi.

## Yaddaş (RAM) qiymətləndirməsi (2026-10-06)

Miniatür keşi `LruCache` ilə 24 MB-a məhdudlaşdırılıb, şəbəkə `LazyVerticalGrid`-dir, fayl məzmunu axınla işlənir; minlərlə fayl RAM-ı praktiki artırmır (yalnız `FileEntity` siyahısı xətti artır, bir neçə MB). Real risklər:

- 🔴 **`decryptToMemory` iki nüsxə yaradır.** `ByteArrayOutputStream(size)` + `toByteArray()` + bitmap → 50 MB şəkil üçün pik təxminən 100+ MB. Şəkil `decodeFull`-a tam `ByteArray` kimi ötürülür.
  → axınla deşifrə (`InputStream`) + `inSampleSize` ilə birbaşa açma; ByteArray nüsxələrini sıfırla.
- 🟡 **Yeni telefonda sinxronizasiya** hər `.meta`-nı ardıcıl endirir (minlərlə fayl = uzun gözləmə; RAM problem deyil) → məhdud paralellik (məs. 4–8) + irəliləyiş göstəricisi.
- 🟡 **Real cihazda ölçülməyib.** Minlərlə fayllı sınaq vault-u yaradıb Android Studio Profiler ilə yoxlamaq (siyahı, sürüşdürmə, miniatür keşi, sinxronizasiya).
- 🟡 **`cache/blobs` diski** limitsizdir (video üçün kritikləşir; yuxarıdakı "Yaddaş və keş" bəndi).

## Video pleyer: ehtimal olunan problemlər (plan 0–4-cü mərhələ)

Plan: crypto-da təsadüfi giriş (`ChunkDecryptor`) → Media3/ExoPlayer + `EncryptedDataSource` → (sonra) Drive `Range` + şifrəli `SimpleCache`. Format və mövcud `encrypt/decrypt` dəyişmir.

Kod dəyişikliyində:
- 🟡 `DriveClient` interfeysinə `downloadRange` əlavə olunsa, `FakeDrive` (testlər) kompilyasiya olunmaz → interfeysdə default implementasiya və ya `FakeDrive`-ı yenilə.
- 🟡 `ScreenshotTest` `ViewerContent(...)` / `ViewerUi(...)`-ı birbaşa çağırır (3 yerdə). İmza dəyişsə test sınar → yeni parametrlərə default dəyər ver.
- 🟡 Yeni `androidx.media3` asılılığı: AGP 9.4.1 / Kotlin 2.4.20 / compileSdk 37 ilə uyğun versiya seçilməli; release-də `isMinifyEnabled=true` olduğu üçün R8 ilə ayrıca test (APK ölçüsü bir neçə MB artır).
- 🟡 `Session.addLockListener`-in silmə metodu yoxdur → pleyerin dinləyicisi sızar. `removeLockListener` əlavə et.
- 🟡 Eyni faylı iki yerdən eyni anda açanda (`ciphertextFile`) eyni `.part` faylına yazılır, `renameTo` nəticəsi yoxlanılmır → fayl üzrə kilid/Mutex.
- 🟡 `ChunkDecryptor` chunk ölçüsünü **başlıqdan** oxumalıdır (`StreamingCipher.ciphertextSize` default 64 KiB fərz edir; backend başqa chunk ilə yarada bilər).
- 🟡 Room sxemini dəyişmə: `durationMs` yalnız `ItemManifest`-də saxlanılsın, `FileEntity`-yə əlavə olunsa migration (v2) lazımdır.
- 🟡 **Backend uyğunluğu:** `ItemManifest`-ə yeni sahə (`durationMs`, `pad`) Android tərəfdə təhlükəsizdir (`ignoreUnknownKeys`), amma Spring Boot tərəfinin naməlum sahələri qəbul edib-etmədiyi yoxlanılmayıb (backend kodunu görmədim).

Funksionallıqda:
- 🟡 Dəstəklənməyən codec/konteyner (məs. köhnə cihazda HEVC) → "kənar tətbiqdə aç" ehtiyatı saxlanmalıdır, əks halda mövcud imkan itir.
- 🟡 Kilid zamanı (auto-lock, əl ilə) pleyer dayanmalı, açar sıfırlanmalıdır; yoxsa oxuma `VaultLockedException` ilə düşür.
- 🟡 Pleyer ekrandan çıxanda/fonda `release()` edilməlidir (səs davam etməsin, açar RAM-da qalmasın); oynayanda ekran sönməsin (`keepScreenOn`).
- 🟡 `FLAG_SECURE` + video səthi (SurfaceView/TextureView) müxtəlif cihazlarda sınanmalıdır.
- 🟡 (3-cü mərhələ) Drive tokeni oxuma ortasında bitə bilər (401) və ya yenidən icazə tələb oluna bilər (`DriveConsentRequired`) — `DataSource` bloklayıcıdır, UI aça bilməz → səhv pleyerə xəta kimi çatdırılıb ekranda izah olunmalıdır; 403 sürət limiti üçün təkrar cəhd.
- 🟢 `moov` atomu sonda olan MP4 üçün Drive axınında əvvəlcə faylın sonu oxunacaq (Range dəstəyi ilə işləyir, amma ilk başlama yavaş ola bilər).

## Video pleyer: icra qeydləri (2026-10-06)

Edildi: `RandomAccessDecryptor` (crypto, 17 test), `EncryptedDataSource`, `EncryptedVideoPlayer`, `Session.removeLockListener`, fayl üzrə endirmə kilidi, yarımçıq endirmənin keşə düşməməsi, 2 GB keş limiti, ana ekranda Şəkil/Video süzgəci (+ `FileFilterTest`).

Hələ edilməyib / diqqət:
- 🟡 **Cihazda sınaq lazımdır** (build və Android testləri bu sessiyada işlədilməyib): H.264/HEVC, 1 GB+ fayl, seek, ekran çevirmə, kilid zamanı, fonda, oflayn (keşdən), `FLAG_SECURE` ilə video səthi.
- 🟡 `ScreenshotTest` Roborazzi ilə şəkilləri yenidən yazır (`roborazzi.test.record=true`) — ana ekranda süzgəc paneli əlavə olunduğu üçün Home şəkilləri dəyişəcək.
- 🟡 ExoPlayer yük nəzarəti: açıq mətn buferi 24 MB-a məhdudlaşdırılıb (standart dəyər videoda yüzlərlə MB ola bilər).
- 🟡 Video başlamazdan əvvəl şifrəli fayl tam endirilir (3-cü mərhələ: Drive `Range` axını).
- 🟢 Pleyerin yuxarı paneli (geri/endir/sil) idarə düymələri gizlənəndə də görünür.

