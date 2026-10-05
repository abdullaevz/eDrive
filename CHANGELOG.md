# Dəyişikliklər

Format [Keep a Changelog](https://keepachangelog.com/) əsasındadır. Versiya nömrələri `MAJOR.MINOR.PATCH` formasındadır.

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
