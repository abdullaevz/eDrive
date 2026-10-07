# Dəyişikliklər

Format [Keep a Changelog](https://keepachangelog.com/) əsasındadır. Versiya nömrələri `MAJOR.MINOR.PATCH` formasındadır.

## [1.2.0]

Bu versiyada giriş və vault modeli dəyişir. 1.x hesabları ilk girişdə köhnə parolla bir dəfə keçid edir; vault, açar və fayllar dəyişmir.

### Əlavə olundu
- Hesab 4 rəqəmli PIN ilə açılır. PIN yalnız proqramın qapısıdır (Keystore HMAC ilə yoxlanılır, şifrələməyə təsiri yoxdur). Hər 5 yanlış cəhddən sonra artan gözləmə (1 dəq → 5 dəq → 15 dəq → 1 saat); bariz PIN-lər (0000, 1234…) qəbul edilmir.
- "Təhlükəsizlik açarı": vault Drive-a ilk qoşulanda yaranır, açar ən azı 12 simvoldur (güc göstəricisi ilə). Bir dəfə daxil edildikdən sonra vault açarı telefonun Keystore-unda saxlanılır və PIN-dən sonra avtomatik açılır.
- Təhlükəsizlik açarının dəyişdirilməsi (hesab menyusu). Fayllar yenidən şifrələnmir; digər cihazlar açılışda bunu görüb bildiriş göstərir.
- İki PDF sənədi: PIN sənədi (qeydiyyatda) və Təhlükəsizlik açarı sənədi (açar + `vault.json` nüsxəsi).
- Qovluqlar: `eDrive Storage` daxilində real Drive alt qovluqları (5 səviyyəyə qədər) — yaratma, ad dəyişmə, faylları köçürmə, silmə (boş; içindəkiləri üst qovluğa köçürərək; içindəkilərlə birlikdə — Drive zibilinə). Qovluq adları Drive-da açıq mətndir.
- Problemli fayllar bildirişi: Drive-a çatmayan fayllar üçün nişan, siyahı və hərəkətlər (yenidən cəhd, hamısını yenidən cəhd, şifrəsiz saxla, sil).
- Drive-dakı `vault.json` hər açılışda yerli nüsxə ilə müqayisə olunur: başqa vault-a dəyişibsə yükləmə dayandırılır, silinibsə yerli nüsxədən bərpa olunur.
- Bir telefonda bir neçə hesab eyni Google Drive-a qoşula bilər (eyni vault və fayllar).

### Dəyişdi
- Drive quruluşu: `eDrive Storage/<istifadəçi adı>/` əvəzinə `eDrive Storage/` kökü. 1.x quruluşunda yalnız `vault.json` kökə köçürülür, fayllar köhnə qovluqda qalır və proqramda adi qovluq kimi görünür.
- Yeni telefonda bərpa üçün eyni istifadəçi adı lazım deyil: istənilən adla hesab → Drive → Təhlükəsizlik açarı.
- Drive-dan ayrılanda telefondakı bütün izlər silinir (şifrəli nüsxələr, miniatürlər, vault, cihaz açarı); hələ yüklənməmiş fayllar barədə əvvəlcədən xəbərdarlıq edilir. Drive-a və qalereyadakı orijinallara toxunulmur.
- Barmaq izi artıq heç bir açarla bağlı deyil — yalnız PIN-in alternatividir.
- Room bazası 2-ci versiyaya keçir (miqrasiya bütün məlumatı köçürür, testlə yoxlanılıb).

## [1.1.1]

### Əlavə olundu
- Tətbiqdaxili video pleyer: video şifrəli halda endirilir və yaddaşda (RAM) deşifrə olunub oynadılır, diskə açıq mətn yazılmır. Axtarış (seek), ±10 san, qoşa toxunuşla irəli/geri, tam ekran səthi. Dəstəklənməyən codec üçün "Kənar tətbiqdə aç" ehtiyatı qalır.
- Ana ekranın başında süzgəc: yan-yana "Şəkil" və "Video" düymələri. Seçiləndə ona uyğun seçimlər açılır: format (JPG, PNG, MP4, MOV…), yönəliş (üfüqi/şaquli) və sıralama (tarix, ölçü, ad).
- Çoxlu seçim: "Seç" düyməsi və ya fayla uzun basma. Toplu endirmə (şəkil/video qalereyaya, digərləri seçilən qovluğa) və toplu "Drive-dan sil": fayl Drive-dan silinir, şifrəli nüsxə cihazda qalır (yeni "yalnız cihazda" statusu, sinxronizasiya ona toxunmur). Gedişi göstərən panel, "hamısını seç", geri düyməsi ilə çıxış.
- Tətbiqdaxili qalereya ("Yüklə" → "Qalereya"): icazə verildikdən sonra telefonun bütün albomlarındakı şəkil və videolar albom üzrə görünür, toplu seçilib şifrələnir. Android 14-də "yalnız seçilmiş şəkillər" rejimi dəstəklənir. Yer məlumatı icazəsi verilibsə, şəkillər GPS/EXIF-i silinmədən olduğu kimi şifrələnir. İş profili (Work) faylları admin siyasətinə görə əlçatan olmaya bilər.
- Fayl məlumatı: baxış ekranındakı və seçim rejimindəki "Məlumat" düyməsi (ad, növ, ölçü, piksel ölçüləri, tarix, vəziyyət, şifrələmə, şifrəli ölçü, ID).
- Qeydiyyatda bərpa sənədini (PDF) saxlamaq ixtiyaridir (defolt: açıq).
- Giriş ekranı hər dəfə görünəndə (soyuq start, kilid, fondan qayıdış) barmaq izi sorğusu avtomatik açılır.
- `crypto`: `RandomAccessDecryptor` — `.edrv` formatını dəyişmədən ixtiyari mövqedən deşifrə (kəsilmə, dəyişdirmə və əlavə edilmiş baytlar aşkarlanır).

### Dəyişdi
- Əl ilə asılılıq idarəsi (`AppContainer`) Hilt ilə əvəz olundu. İstifadəçiyə görünən dəyişiklik yoxdur.
- Drive-dan endirilən şifrəli nüsxə yarımçıq qalarsa, keşə düşmür (əvvəl bu, faylın sonradan açılmamasına səbəb ola bilərdi). Eyni fayl eyni anda iki yerdən endirilmir.
- Endirmə keşi 2 GB ilə məhdudlaşdırıldı (ən köhnə nüsxələr silinir, lazım olanda yenidən endirilir).

## [0.1.0] — ilk test versiyası

### Əlavə olundu
- İstifadəçi adı və parol ilə qeydiyyat və giriş (parol ən azı 8 simvol). Parol açar kimi istifadə olunur və heç yerdə saxlanılmır.
- Bir cihazda bir neçə lokal istifadəçi.
- Qeydiyyatdan sonra istifadəçinin seçdiyi yerə bərpa sənədi (PDF).
- Barmaq izi ilə giriş (Android Keystore).
- Google Drive-a qoşulma (`drive.file` icazəsi). Hər qoşulmada Google hesabı seçilir.
- Fayllar telefonda AES-256-GCM ilə şifrələnir və Drive-dakı `eDrive Storage` qovluğuna yalnız şifrəli halda yüklənir.
- Miniatürlər şifrəli saxlanılır və fayl telefondan silinsə də görünür. Toxunanda fayl deşifrə olunub açılır.
- Yeni telefonda eyni istifadəçi adı və parolla fayllar Drive-dan bərpa olunur.
- Faylı deşifrə edib qalereyaya və ya seçilən qovluğa endirmək.
- Çökmə hesabatı: ekranda göstərilir, kopyalanır və ya `.txt` fayl kimi saxlanılır.
- Avtomatik kilid (60 saniyə fonda), ekran görüntüsü qorunması, tünd tema.

### Məlum məhdudiyyətlər
- Video və PDF tətbiqdaxili pleyerdə deyil, müvəqqəti deşifrə olunub kənar tətbiqdə açılır. Müvəqqəti fayl kilid zamanı silinir.
- Kəsilən yükləmə növbəti dəfə əvvəldən başlayır.
- Parol itərsə, fayllara çıxış bərpa oluna bilməz.
