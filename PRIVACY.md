# eDrive — Məxfilik siyasəti

Son yenilənmə: 2026-10-05

eDrive istifadəçinin fayllarını telefonda şifrələyib yalnız **istifadəçinin öz Google Drive-ına** yükləyən açıq mənbəli Android tətbiqidir. Tətbiqin öz serveri yoxdur.

## Hansı məlumatlar toplanır?

Tətbiqin tərtibatçısı **heç bir məlumat toplamır, saxlamır və üçüncü tərəflərə ötürmür.** Heç bir analitika, reklam və ya izləmə kitabxanası istifadə olunmur.

## Məlumatlar harada saxlanılır?

- **Telefonda:** istifadəçi adı, parolun törəməsi olan şifrələmə başlığı (parolun özü saxlanılmır), şifrələnmiş fayllar və miniatürlər. Hamısı tətbiqin qapalı yaddaşındadır və Android ehtiyat nüsxəsinə daxil edilmir.
- **İstifadəçinin Google Drive-ında:** `eDrive Storage` qovluğunda yalnız **şifrələnmiş** fayllar. Fayl adları və məzmunu Drive-da açıq görünmür.

Parol və şifrələmə açarları telefondan kənara göndərilmir. Parol itirilərsə, faylları bərpa etmək mümkün deyil.

## Telefon icazələri (foto və video)

Tətbiqdaxili qalereya üçün Android-in "Foto və video" icazəsi istənilir (və şəkillərin GPS/EXIF məlumatı olduğu kimi qalsın deyə "şəkillərdəki yer məlumatı" icazəsi). İcazə yalnız siz seçdiyiniz fayllar üçün oxuma üçündür: seçmədiyiniz fayllar oxunmur və heç yerə göndərilmir, seçilənlər telefonda şifrələnib yalnız şifrəli halda yüklənir. İcazəyə istəmirsinizsə, Android-in standart seçicisi və "Fayllar" seçimi icazəsiz işləyir. İcazəni istənilən vaxt Android tənzimləmələrindən ləğv edə bilərsiniz.

## Google icazələri

Tətbiq yalnız `https://www.googleapis.com/auth/drive.file` icazəsini istəyir. Bu icazə tətbiqə yalnız **özünün yaratdığı** fayl və qovluqlara çıxış verir, istifadəçinin digər Drive fayllarına yox. İstifadəçinin e-poçt ünvanı yalnız hansı hesabın qoşulduğunu göstərmək üçün telefonda saxlanılır.

eDrive-ın Google API-lərindən aldığı məlumatı istifadəsi və başqasına ötürməsi [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy) qaydalarına, o cümlədən Limited Use tələblərinə uyğundur. Məlumat yalnız istifadəçiyə göstərilən funksiyaları (yükləmə, bərpa, silmə) yerinə yetirmək üçün istifadə olunur.

## Çökmə hesabatları

Tətbiq çöksə, texniki xəta jurnalı telefonda saxlanılır. Jurnalda parol, açar, fayl adı və ya fayl məzmunu olmur. Hesabatı yalnız istifadəçi özü paylaşmaq qərarına gələrsə kopyalayıb göndərə və ya fayl kimi saxlaya bilər.

## Hesabın ayrılması və məlumatların silinməsi

- Tətbiqdə **Ayır** düyməsi Drive girişini ləğv edir.
- Google hesabınızın [icazələr səhifəsindən](https://myaccount.google.com/permissions) eDrive-ın girişini istənilən vaxt ləğv edə bilərsiniz.
- Drive-dakı `eDrive Storage` qovluğunu özünüz silə bilərsiniz.
- Tətbiqi silsəniz, telefondakı bütün lokal məlumat silinir.

## Əlaqə

Suallar üçün: [github.com/abdullaevz/eDrive/issues](https://github.com/abdullaevz/eDrive/issues)
