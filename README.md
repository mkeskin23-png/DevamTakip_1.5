# Devam Takip Android — v1.5.0

Türkçe arayüzlü, çevrimdışı çalışan Android devam takibi uygulaması. Dersler, öğrenci listeleri, yoklama ve uygulama notları cihazdaki SQLite veritabanında tutulur.

## v1.5.0 güncellemeleri

- Tüm ekranların zemininde GS renklerinde 1905 amblem filigranı bulunur; alfa/opacity değeri `%40` olarak ayarlanmıştır. Dikey ve yatay ekran için ayrı oranlı filigran kullanılır.
- Uygulama başlatıcı ikonu siyah zemin üzerinde kırmızı `D` ve sarı `T` harflerinden oluşur.
- Sağ üstteki çıkış simgesi kaldırılmıştır. Çıkış ana menünün altındaki kırmızı düğmeden, onay penceresiyle yapılır.
- Ekran döndürülürken `MainActivity` yeniden oluşturulmadığı için geçerli ekran korunur; içerik yatay veya dikey ölçülere göre yeniden yerleşir.
- Düğmeler hafif yuvarlatılmış köşelerle tutarlı hale getirilmiştir; ana menüde her seçeneğin vektörel ikonu bulunur.
- Ders ekleme/düzenleme ekranındaki `Birim adı` serbest metin alanıdır.
- Zorunlu ders adı, birim adı ve haftalık program eksik olduğunda kırmızı çerçeve/uyarı görünür.
- Dersi öğrenci listesi olmadan kaydetmeye çalışıldığında “Öğrenci ekle” veya “Daha sonra ekle” onayı sorulur.
- Öğrenci listesi kartında listeyi temizleme düğmesi bulunur. Temizleme işlemi öncesinde onay gösterilir ve yedek alma seçeneği sunulur. Temizleme, o dersteki öğrenci kayıtlarını ve bu öğrencilere ait yoklama/uygulama notu kayıtlarını kaldırır.
- Yoklama al ve devam raporları ekranlarına ana menüden girildiğinde önce ders seçilir.
- Devamsızlık sınırında yüzdelik değerlerin yanında saat karşılığı gösterilir; saat karşılıkları ve eşik değerleri yukarı yuvarlanır.
- Ders başına uygulama notu ağırlığı `%0–%100` arasında ayarlanabilir. Her hafta, haftalık toplam ders saati sayısı kadar not alanı gösterilir; her saat için `0, 1, 2, 3, 4` puan seçilebilir. Değişiklikler otomatik kaydedilir.
- Uygulama notu raporları bireysel veya toplu ve özet/ayrıntılı olarak görüntülenebilir; PDF ve Excel (.xlsx) dışa aktarılır. Ortalama puan ve ders notuna ağırlıklı katkı hesaplanır.
- Yedek JSON dosyaları dersin uygulama notu ağırlığını ve uygulama notu kayıtlarını da içerir. Eski sürüm yedeklerinde bu alanlar yoksa varsayılan ağırlık `%0` kabul edilir.

## Genel özellikler

- Birden çok ders; her ders için ders adı, serbest birim adı, başlangıç tarihi, haftalık Pazartesi–Cuma ders saatleri, vize haftası ve devamsızlık sınırı.
- Ders bazında ayrı öğrenci listeleri. Excel/CSV dosyası yalnızca seçili derse yüklenir.
- Excel/CSV içinden `Öğrenci No` + `Adı Soyadı` veya `Öğrenci No` + ayrı `Ad` ve `Soyad` başlıklarıyla öğrenci aktarımı. İsim başlıkları yoksa veya kayıt satırında numara olduğu hâlde isim boşsa dosya reddedilir; başlıksız dosyalardan tahmini aktarım yapılmaz. XLSX ve CSV desteklenir; eski XLS dosyası desteklenmez.
- Bir derste hâlihazırda öğrenci listesi varken yeni liste seçilirse onay penceresi açılır. Öğrenci numarası ve tam adı eşleşen öğrencilerin yoklama kayıtları korunur; eşleşmeyen eski öğrenciler ve onlara ait devamsızlık kayıtları kaldırılır. Yeni listenin değişiklikleri ders ayarları kaydedildiğinde uygulanır.
- Yoklama haftalarının dikey listesi. Vize haftası açıkça belirtilir; vize haftasında yoklama girişi kapalıdır ve bu hafta devamsızlık hesaplarına eklenmez.
- Yoklama ekranında aranabilir öğrenci listesi, öğrenci numarası ve adı soyadı, dört saate kadar yan yana saat düğmeleri, her öğrencinin saat düğmelerinin altında `Tüm saatleri seç` ve `Tüm saatleri kaldır` işlemleri.
- Yoklama değişiklikleri taslakta tutulur. Ekranın üstünde ve altında `YOKLAMAYI KAYDET` düğmeleri bulunur. Kaydedilmemiş yoklama ile ayrılırken uyarı ve onay sorusu çıkar. Önceki haftalar açılıp düzeltilebilir.
- Öğrenciye özel, seçili haftaya kadar devamsızlık listesi.
- Devamsızlık sınırına yaklaşma (%80 eşiği), sınıra ulaşma ve aşım uyarıları.
- Devam raporlarında PDF/Excel, özet veya ayrıntılı devam çizelgesi; tarih/saat bazında yeşil `✓` katılım ve kırmızı `X` devamsızlık gösterimi.
- Rapor ekranında devamsızlığı en yüksek öğrenciler, hafta bazında özetler ve birim bazında raporlar.
- Ayrı menü düğmesinden açılan ders programı takvimi.
- JSON yedek dosyası oluşturma ve geri yükleme. Geri yükleme öncesinde onay ister.

## Excel dosyasının biçimi

Tek ad-soyad sütunlu biçim:

| Öğrenci No | Adı Soyadı |
|---|---|
| 1001 | Ayşe Yılmaz |
| 1002 | Mehmet Demir |

Ayrı ad/soyad sütunlu biçim:

| Öğrenci No | Ad | Soyad |
|---|---|---|
| 1001 | Ayşe | Yılmaz |
| 1002 | Mehmet | Demir |

İlk çalışma sayfası okunur; başlık satırı ilk 25 satır içinde aranır.

## GitHub Actions üzerinden APK oluşturma

1. ZIP dosyasını çıkarın. GitHub deposunun köküne `DevamTakipAndroid` klasörünün içindeki dosya ve klasörleri yükleyin. `settings.gradle.kts`, `build.gradle.kts`, `app/` ve `.github/workflows/android.yml` aynı proje kökünde olmalı.
2. `main` veya `master` dalına gönderin ya da GitHub'da **Actions → Android APK Build → Run workflow** seçeneğini kullanın.
3. Başarılı iş akışının **Artifacts** bölümünden `devam-takip-debug-apk` dosyasını indirin.

İş akışı Android SDK Platform 35, Build Tools 35.0.0, Java 17 ve Gradle 8.9 kullanır. APK'nın başarıyla oluşturulduğu, GitHub Actions sonucu yeşil onay işaretiyle tamamlandığında doğrulanır.

## Veritabanı ve hesaplama notları

- Veritabanı şeması 7. sürüme yükseltildi. Var olan kurslara yeni uygulama notu ağırlığı sütunu `%0` varsayılanıyla eklenir; mevcut dersler ve yoklama kayıtları korunur.
- Devamsızlık sınırı 14 normal ders haftasının planlanan saatleri üzerinden değerlendirilir. Vize haftası devamsızlık hesabına katılmaz. Resmî tatiller otomatik düşülmez.
- Uygulama notu raporundaki ağırlıklı katkı `ortalama puan / 4 × dersin uygulama notu ağırlığı` formülüyle hesaplanır.
- Veriler cihazda kalır; cihaz kaybına karşı düzenli yedek alınması önerilir.
