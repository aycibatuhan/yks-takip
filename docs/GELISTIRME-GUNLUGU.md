# Geliştirme ve Doğrulama Günlüğü / Development & Verification Log

> **TR:** Bu dosya, v1.0.0–v1.3.0 sürümleri geliştirilirken tutulan orijinal README'nin
> arşividir: modül durum tablosu, sürüm başına doğrulama kayıtları (unit test sayıları,
> canlı migration yükseltmeleri, cihaz turları, bulunan hatalar) ve cihazda elle
> doğrulanacaklar listesi. Tarihsel kayıt olarak olduğu gibi korunur; güncel kullanım ve
> derleme talimatları için depo kökündeki [README](../README.md)'ye bakın.
>
> **EN:** This file archives the original README kept while v1.0.0–v1.3.0 were being
> built: the module status table, per-release verification records (unit-test counts,
> live migration upgrades, device tours, bugs found), and the manual on-device checklist.
> It is preserved verbatim as a historical record — Turkish only; see the repository-root
> [README](../README.md) for current usage and build instructions.

---

# YKS Takip — Android (v1.3)

Offline-first YKS deneme/plan/sayaç + AI koç uygulaması. Spec: [`PRD-v2.md`](PRD-v2.md) — PRD'nin tamamı + M4 (Konu Takibi) + v1.2 (Profiller/İçe-Dışa Aktar/Notlar/Görsel Paso) + v1.3 (AI Koç oturum paneli/klasörler/arama).

> **Ad notu:** v1.2'de görünen ad "YKS Takip" oldu; yıl etiketi ("YKS 2027") ayarlardaki
> TYT tarihinden türetilir. `applicationId` **com.yks2027.tracker olarak sabittir** —
> değişirse imzalı güncellemeler mevcut kurulumun üzerine kurulamaz.

**Stack (2026-08 itibarıyla doğrulandı):** AGP 9.3.2 (built-in Kotlin 2.3.21) · Gradle 9.7.1 · Jetpack Compose BOM 2026.08 (M3 + adaptive 1.3) · **Vico 3.3.1** grafikler · Room 2.8.4 (schema v1, exportSchema) · DataStore 1.2 · Hilt 2.60.1 · kotlinx.serialization · minSdk 29 / targetSdk 35 / **compileSdk 37** (SDK "android-36.1" minör platformuna denk gelir — Vico/Compose 1.12 AAR'ları bunu şart koşar).

## Durum

| Modül | Durum |
|---|---|
| Ana Sayfa | ✅ Çift geri sayım (tarih **ve saat** ayarlanabilir, "tahmini" uyarısı), bugünün görevleri (dürüst çözülen-soru yakalama ile), son net KPI'ları, yedek hatırlatması |
| Denemeler | ✅ TYT/AYT **+ Branş** ekle-düzenle-sil (geri al), canlı boş/net önizleme, negatif net, kopya uyarısı |
| Analiz | ✅ Vico grafikleri: toplam net trendi (TYT/AYT), **ders bazında** net trendi + doğruluk % + boş-yanlış kolonları, Son-5 ortalaması dahil KPI'lar |
| Planlayıcı | ✅ Hafta anahtarlı pano, örtük arşiv, "geçen haftayı kopyala", **çözülen-soru yakalama**, haftalık **çalışma süresi** (kategori bazında), Sıfırla/Temizle + geri al |
| Geçmiş Haftalar | ✅ Haftalık özet listesi (tamamlanma %, hedef/çözülen, çalışma dk) → salt-okunur pano görünümü |
| Sayaç | ✅ Tam durum makinesi: DataStore + FGS (kronometre bildirimi) + exact alarm + boot receiver + oturum kaydı (≥60 sn) |
| Yedekleme | ✅ SAF JSON dışa/içe aktarma **+ haftalık otomatik yedek** (klasör seçimi, son 8 tutulur) + panoda hatırlatma |
| Tema | ✅ Açık/Koyu/Sistem |
| **AI Koç (M3)** | ✅ BYOK sohbet: Claude (resmî Anthropic Java SDK, varsayılan `claude-opus-5`) + OpenAI-uyumlu istemci (OpenAI/Gemini/xAI/**Ollama-LAN**); akış (streaming), durdurma, sohbet geçmişi; istatistik bağlamı (`ai_share_stats` ile kapatılabilir); anahtar Keystore-AES ile şifreli, yedeklere girmez; anahtar yokken sıfır ağ trafiği ve sekme gizli |
| Koçluk (M3) | ✅ Geçmiş Haftalar'da haftalık birleşik rapor (görev + soru + çalışma dk + deneme sayısı/ortalaması); panoda **seri** (streak) KPI'ı; deneme başına **eksik konu** etiketleri → Ders Analizi'nde zayıflık listesi |
| Widget & Sayaç (M3) | ✅ Glance ana ekran geri sayım widget'ı; sayaçta otomatik mola önerisi (5/10 dk, mola oturumları istatistiklere yazılmaz) |
| **Konu Takibi (M4, v1.1)** | ✅ ~140 konuluk gömülü katalog (MEB onayı: 2027'de mevcut müfredat geçerli, Maarif soru modeli 2028'de); deneme girişinde **konu işaretleri** (hangi konudan kaç Y/B + hata türü: Bilgi/İşlem/Dikkat/Süre); Analiz'de **Zayıf Konular** sıralaması (genel + ders bazında, hata dökümüyle); **Konular** ekranı (çalıştım/soru/tekrar çizelgesi + ilerleme + yanlış rozetleri); AI Koç bağlamına zayıf konular eklendi |
| **AI Profilleri (v1.2)** | ✅ Tek-slot yapı yerine **profiller**: ad + protokol (Anthropic / OpenAI-uyumlu) + taban URL (vekil için Anthropic'te bile düzenlenebilir) + model + profil başına şifreli anahtar. Şablonlar: Claude, OpenAI, Gemini, xAI, OpenRouter, OpenCode Zen, Ollama-LAN, Özel. **Bağlantıyı Sına** (dost Türkçe mesaj + HTTP kodu + sunucunun kendi hatası) ve **Modelleri Getir** (canlı model listesi, arama + seçim; Ollama'da yerel modeller). Eski v1.0/v1.1 yapılandırması ve anahtar ilk açılışta otomatik taşınır. AI Koç başlığında aktif profil + hızlı profil değiştirici |
| **Kronometre (v1.2)** | ✅ Sayaç'ta ileri sayım modu: durum makinesi kalıcı (uygulama ölse de sürer), FGS ileri sayan kronometre bildirimi, alarm yok; DURAKLAT/DEVAM/BİTİR & KAYDET; ≥1 dk oturumlar `plannedMin=0` ile kaydedilir ("kronometre" etiketi) |
| **CSV + AI içe/dışa aktarma (v1.2)** | ✅ Denemeler → CSV **dışa aktar** (SAF) ve **paylaş** (paylaşım sayfası). **İçe Aktar** merkezi (Ayarlar + Denemeler'den): CSV → önizleme tablosu + mükerrer uyarısı (aynı gün+tür) → seçilenler EKLENİR (yedek geri yükleme gibi silmez); **AI ile okuma**: karne görseli/PDF/metni Claude'a gider, sonuç ONAY İÇİN doldurulmuş deneme formunda açılır — otomatik kayıt yok (yalnız Anthropic profilleri) |
| **Notlar (v1.2)** | ✅ Markdown notlar (başlık + gövde, önizleme: mikepenz renderer); AI Koç yanıtlarında **"Nota kaydet"**; yedek v4'e dahil |
| **Paylaşım (v1.2)** | ✅ Geçmiş Haftalar'da satır başına **"Haftalık Raporu Paylaş"** (görev/soru/çalışma/deneme ort./seri/öncelikli konular — YALNIZ özet; sohbet ve notlar asla). Uzaktan takip için sıfır-kod yol: otomatik yedek klasörünü Drive-eşitlenen paylaşımlı klasöre yönlendir |
| **AI Koç oturumları (v1.3)** | ✅ Sohbetler birinci sınıf: geniş ekranda **kalıcı sol panel** (~320dp), dar ekranda alt sayfa (geçmiş ikonunun arkasında). TÜM sohbetler listelenir (eski 20 sınırı kalktı): **sabitli önce**, sonra son MESAJ etkinliğine göre; satırda başlık + göreli zaman + mesaj sayısı + son mesaj özeti. Uzun basınca: **Yeniden Adlandır** (elle verilen ad asla otomatik başlıkla ezilmez) / **Sabitle** / **Klasöre Taşı** / **Sil** (onay + Geri Al — mesajlar ve klasör atamasıyla geri gelir). Üst bar artık sohbetin başlığı (tıkla → yeniden adlandır); profil değiştirici korunur |
| **Klasörler + arama (v1.3)** | ✅ `chat_folders` + sohbet başına klasör (FK **SET NULL** — klasör silmek sohbet SİLMEZ, Klasörsüz'e düşerler). Liste üstünde çipler: Tümü · Klasörsüz · klasörler · "+" (uzun basış: yeniden adlandır/sil). **Arama** başlık + mesaj içeriğinde (LIKE; bu ölçekte FTS yok) — sonuçlar klasörler arası gelir ve klasör rozeti taşır. Klasörler ve atamalar yedek v5'te sabit id'lerle taşınır |
| **Görsel paso (v1.2)** | ✅ KPI **sparkline**'ları (son 10 net) + hafta satırlarında günlük dk; **ilerleme halkaları** (haftalık plan, Konular, Sayaç kadranı); **Zayıf Konular şerit çubukları** (uzunluk = yanlış, dilimler = hata türü); Planlayıcı kategori süresi tek şeritte; **8 haftalık aktivite ısı şeridi** (seri KPI'ının altında); Geçmiş Haftalar'da **çift eksenli trend** (çalışma dk kolonları + biten görev çizgisi) |
| Şema | ✅ **v5** — v4'e `chat_folders` + `chat_threads.pinned/folder_id` (FK SET NULL; SQLite FK ekleyemediği için chat_threads yeniden kuruldu — veri olarak %100 korunumlu, üretilen şemayla birebir doğrulandı). Önceki: v4 `ai_profiles`+`notes` (v1.1→v1.2 canlı yükseltme anahtar taşımasıyla kanıtlı). **v1.2→v1.3 yerinde yükseltme emülatörde 4 gerçek sohbetle test edildi** (doğru sırada geldiler; yeniden adlandır/sabitle/klasörle/ara/sil+geri al sıfır kayıpla). Yedek formatı v5 (v1–v4 dosyaları okunur; klasörler sabit id'lerle, profil META verisi dahil, **anahtarlar asla**). |

## Derleme

Gereksinim: JDK 17 + Android SDK (platform 35). En kolayı **Android Studio** (JDK ve SDK'yı kendisi kurar, lisans akışını kendisi yürütür): projeyi `File → Open` ile açıp sync + Run.

Komut satırı (bu Mac'te kurulu toolchain: Homebrew openjdk@17 + Android SDK 35 → `~/Library/Android/sdk`, `local.properties` hazır):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew :app:assembleDebug
```

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew :app:testDebugUnitTest
```

> ✅ Doğrulandı (2026-08-30, **v1.3.0**): **75/75 unit test** (sıralama/filtre/arama
> mantığı, klasör-öksüz semantiği, geri-yükleme klasör koruması dahil 12 yeni); imzalı
> release (26MB) emülatörde: **v1.2.0 üzerine yerinde yükseltme** (migration 4→5,
> chat_threads yeniden kurulumu) 4 sohbet + mesajlarıyla kayıpsız; panel sıralaması son
> mesaj etkinliğine göre birebir; sabitleme, klasör oluştur/taşı/filtre, klasörler-arası
> içerik araması (klasör filtresi açıkken bile), sil + **Geri Al** (mesajlar ve klasör
> atamasıyla döner), üst barda başlığa tıkla-yeniden-adlandır — hepsi cihazda canlı
> doğrulandı; dar ekranda (dikey) alt-sayfa paneli; açık/koyu tur; 0 çökme.
> (Bulunan ve düzeltilen hata: silme snackbar'ı, LaunchedEffect anahtarını erken
> sıfırladığı için hiç görünmüyordu — consume artık showSnackbar'dan sonra.)
>
> Önceki (2026-08-30, **v1.2.0**): **63/63 unit test** (CSV round-trip, AI-çıkarım
> JSON sözleşmesi, kronometre geçişleri, profil migrasyonu, ısı-şeridi kovalaması dahil);
> imzalı release (26MB, R8) emülatörde uçtan uca: **v1.1.0 üzerine yerinde yükseltme**
> (migration 3→4) gerçek veri + kayıtlı OpenAI anahtarıyla — anahtar migre edilen aktif
> profile taşındı ve "Bağlantıyı Sına" gerçek api.openai.com'a giderek sunucunun 401
> gövdesinde anahtarı maskeli göstermesiyle **uçtan uca kanıtlandı**; yanlış URL (404) ve
> ağsız (UnknownHost) hata yolları ayrıntılı mesajlarıyla doğrulandı; OpenRouter'dan canlı
> 396 modellik liste çekildi; CSV dışa aktar → aynı dosyayı geri içe aktar → 17/17 deneme
> mükerrer işaretli (0 seçili) turu geçti; kronometre gerçek 75 sn oturum kaydetti;
> "Nota kaydet" çalıştı; açık/koyu ekran turu çekildi; oturum boyunca 0 çökme.
>
> Önceki: v1.1.0 — 29/29 test, migration 2→3 canlı doğrulama. v1.0.0 — 24/24 test,
> ilk imzalı release, tam duman testi. `./gradlew :app:assembleRelease` çıktısı:
> `app/build/outputs/apk/release/app-release.apk`.
>
> **İmzalama:** `keystore/release.keystore` + `keystore.properties` (ikisi de git dışı).
> ⚠️ Bu iki dosyayı ve şifreyi MUTLAKA yedekleyin (parola yöneticisi + ayrı bir konum):
> kaybolursa v1.0.0 üzerine güncelleme kurulamaz, uygulama silinip yeniden kurulur
> (veriler de gider — JSON yedek varsa geri gelir). Sertifika: CN=YKS 2027 Takip,
> SHA-256 `a20e34b8…49b897`.

## CSV formatı (v1.2)

Dışa aktarma ve içe aktarma aynı biçimi kullanır — UTF-8 (BOM'lu), ayraç `;`
(netlerdeki ondalık virgülle çakışmasın diye), başlık satırı zorunlu:

```text
tarih;tur;ad;yayinevi;ders;soru;dogru;yanlis;bos;net
2026-08-22;TYT_FULL;TYT Deneme 8;Limit;TYT_MATEMATIK;40;28;8;4;26,00
```

- Bir satır = bir dersin sonucu; tam deneme = aynı `tarih+tur+ad` ile ders başına satır.
- `tur`: `TYT_FULL | AYT_SAY_FULL | BRANS_TYT | BRANS_AYT`; `ders`: `TYT_TURKCE`,
  `TYT_SOSYAL`, `TYT_MATEMATIK`, `TYT_FEN`, `AYT_MATEMATIK`, `AYT_FIZIK`, `AYT_KIMYA`,
  `AYT_BIYOLOJI`.
- `bos` ve `net` türetilmiş kolonlardır — içe aktarırken YOK SAYILIR (yeniden hesaplanır).
- `;` veya `"` içeren alanlar çift tırnakla kaçırılır (Excel standardı).
- İçe aktarma EK yapar; aynı gün+tür zaten varsa satır mükerrer işaretlenir ve
  varsayılan olarak seçimsiz gelir. Bozuk satırlar satır numarasıyla raporlanır,
  kalanı yine de aktarılır.

## Notlar / cihazda doğrulanacaklar

- `INTERNET` izni **yalnızca** AI profillerinin uçları için (PRD §8); profil eklenmeden
  sıfır ağ trafiği. `usesCleartextTraffic` Ollama-LAN (http://…:11434) senaryosu için açık.
- AI uçtan uca akış gerçek bir API anahtarıyla cihazda bir kez denenmeli (Ayarlar →
  AI Koç — Profiller → Claude şablonu; varsayılan model `claude-opus-5`, ekonomik
  seçenekler `claude-sonnet-5` / `claude-haiku-4-5`). Sağlayıcı panelinde harcama limiti
  önerilir. "Bağlantıyı Sına" ilk kontrol için yeterli (models ucuna gider).
- AI ile karne okuma (görsel/PDF/metin) şimdilik yalnız **Anthropic** profilleriyle çalışır;
  gerçek anahtarla bir karne fotoğrafı denenmeli.
- Uzaktan takip (istenirse): Ayarlar'daki otomatik yedek klasörünü Google Drive ile
  eşitlenen ve abiyle paylaşılan bir klasöre yönlendirin — haftalık JSON yedekler ona da
  ulaşır. Uygulamada hesap/telemetri yoktur; paylaşım her zaman öğrencinin elindedir.
- Refusal (`stop_reason: refusal`) yakalanıp kullanıcıya nazikçe gösteriliyor; sunucu
  taraflı otomatik fallback bilinçli olarak MOBİL istemciye eklenmedi (beta yüzeyi).
- Widget ve Boş/Yanlış lejant renkleri cihazda görsel kontrol ister.
- Diğer PRD-dışı fikirler (hedef net, puan tahmini, konu-analizi genişletmesi, PDF rapor)
  bilinçli kapsam dışı — PRD §15.

## Kabul kriterleri (PRD §13) — cihazda doğrulanacaklar

- Sayaç çalışırken uygulamayı öldür → bildirim yine `end_at`'te gelir; yeniden başlat → alarm yeniden kurulur.
- `POST_NOTIFICATIONS` reddedilirse sayaç uygulama içinde tamamlanır.
- D+Y > soru sayısı girişte reddedilir; negatif net her yerde doğru görünür.
- Pazar → Pazartesi geçişinde yeni hafta açılır, eski hafta verisi durur.
- Dışa aktar → verileri sil → içe aktar → aynı durum.

---

## v2.0.0 doğrulama kaydı (2026-09-02, "Çoklu Platform")

> ✅ **Testler:** 75 v1.x testi değişmeden `jvmTest`'te + 41 yeni = **116/116** (`:shared:desktopTest`).
> Şema v5: dosya silinip KMP Room derleyicisince yeniden üretildi → commit'li v1.3 dosyasıyla **bit-bit aynı**.
>
> **Android (emülatör `yks_tab`, gerçek v1.3.0 kurulumu + verisi):** imzalı v2.0.0 (`apksigner`: aynı sertifika
> `a20e34b8…49b897`) `adb install -r` ile yerinde güncellendi → versionCode 8 / 2.0.0; `user_version=5`,
> 17 deneme / 59 ders / 4 sohbet / 10 mesaj / 1 klasör / 3 not / 2 profil / 12 konu işareti **değişmedi**;
> `ai_secrets` (91 B) / `settings` / `timer_state` DataStore dosyaları dokunulmadı. Ayarlar → OpenAI profili →
> "Bağlantıyı Sına": gerçek api.openai.com 401 gövdesinde v1.3'te kaydedilen anahtar maskeli
> (`sk-test-******c123`) göründü → **Keystore anahtarı DI/Room değişiminden sonra hâlâ çözülüyor**. 1 dakikalık
> gerçek geri sayım: FGS bildirimi, 70 sn sonra "Süre doldu! / Odak oturumu tamamlandı." bildirimi,
> `focus_sessions` 24→25 (planned 1, completed, 60 000 ms), durum IDLE. 9 ekranlık açık tur + 3 koyu ekran;
> oturum boyunca **0 FATAL**.
>
> **Masaüstü (bu Mac):** `packageDmg` → `YKS Takip-2.0.0.dmg` (153 MB, JRE dahil; Homebrew JDK için
> `checkJdkVendor=false`). Paketlenmiş uygulama, emülatörden alınan **gerçek v1.3 `yks.db`** ile açıldı
> (BundledSQLiteDriver; WAL oluştu, tüm sayılar aynı), `datastore/`, `secrets/`, `window.properties` yazıldı,
> günlük temiz. Ekran-kaydı/erişilebilirlik izni olmadığı için tur **çevrimdışı** çizildi
> (`DesktopTourTest`, 14 PNG: geniş 1280 / dar 700, açık/koyu; AI Koç panelinin genişliğe göre
> gelip gitmesi assert'li). Enter ile deneme kaydı (`DesktopKeyboardFlowTest`), gerçek Anthropic + OpenAI
> uçlarına "Bağlantıyı Sına" (401 + ayrıntı), zamanlayıcı sözleşmesi ve gizli depo gidiş-dönüşü testlerde.
>
> **Çapraz platform:** Android → `Dışa Aktar` (SAF) → `android_export.json` → masaüstü `importReplace`:
> 17/4/10/3 birebir. Masaüstü `exportJson` → Android `Geri Yükle` (SAF seçici + onay): 17/59/4/10/1/3/2/12
> birebir, `pre_import_snapshot.json` yazıldı. CSV: Android CSV'si masaüstü İçe Aktar merkezinden
> (17 satır, 17 mükerrer işaretli, seçilince +17 eklendi); masaüstü CSV'si Android'de önizlendi.
>
> **Bulunan ve düzeltilen hatalar:** (1) `application{}` kapsamında `collectAsStateWithLifecycle` →
> "LocalLifecycleOwner not present" açılış çökmesi; (2) `jvmTest.dependsOn(jvmMain)` — test kaynak
> setinin ana kaynak setine bağlanması `expect`'leri test derlemesine taşıyıp `actual`'sız bırakıyordu;
> (3) Room Gradle eklentisi KMP hedefleri için şema yazmıyordu (`copyRoomSchemas NO-SOURCE`) → KSP
> `room.schemaLocation`; (4) `sqlite-bundled` commonMain'de Android APK'sını 5 MB şişiriyordu → yalnız masaüstü.
>
> **Bilinçli sınırlar:** Windows `.msi` bu Mac'te üretilemez (jpackage host-OS) — CI matrisi üretir;
> masaüstü paketleri imzasız; canlı pencere yeniden boyutlandırma ekran görüntüsüyle belgelenemedi (izin yok),
> aynı `currentWindowAdaptiveInfo()` yolu iki genişlikte çevrimdışı doğrulandı.
