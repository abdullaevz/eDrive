# Dəyişikliklər

Format [Keep a Changelog](https://keepachangelog.com/) əsasındadır. Versiya nömrələri `MAJOR.MINOR.PATCH` formasındadır.

## [Buraxılmamış]

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
