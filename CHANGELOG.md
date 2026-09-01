# Changelog

**[🇹🇷 Türkçe](#turkce) · [🇬🇧 English](#english)**

Biçim [Keep a Changelog](https://keepachangelog.com/) ruhundadır; sürümleme
[SemVer](https://semver.org/)'i izler. / Format follows the spirit of
[Keep a Changelog](https://keepachangelog.com/); versioning follows
[SemVer](https://semver.org/).

---

<a id="turkce"></a>
# 🇹🇷 Türkçe

## [1.3.0] — 2026-08-30 · "AI Koç: Oturum Paneli + Klasörler + Arama"

**Şema v5 · Yedek formatı v5 (v1–v4 okunur) · 75 unit test**

### Eklendi
- **Oturum paneli:** Sohbetler açılır menüden çıkıp birinci sınıf oldu. Geniş ekranda
  ~320dp kalıcı sol panel; dar ekranda geçmiş ikonunun arkasında alt sayfa. Eski
  20-sohbet sınırı kaldırıldı — tüm sohbetler listelenir.
- **Sıralama:** sabitlenenler önce, ardından **son mesaj etkinliğine** göre (sorguyla
  hesaplanır, şemaya sıralama alanı eklenmedi). Satırlarda başlık, göreli zaman
  ("az önce / 12 dk / 5 sa / 3 gün"), mesaj sayısı ve son mesaj özeti.
- **Sohbet eylemleri (uzun basış):** Yeniden Adlandır (elle verilen ad otomatik
  başlıkla asla ezilmez) · Sabitle · Klasöre Taşı · Sil (onay + **Geri Al** —
  mesajlar ve klasör atamasıyla geri döner).
- **Üst bar:** artık aktif sohbetin başlığını gösterir; dokununca yeniden adlandırma
  açılır. Profil değiştirici ve model alt yazısı korunur.
- **Klasörler:** `chat_folders` tablosu + sohbet başına `folder_id` (FK **SET NULL**
  — klasör silmek sohbetleri asla silmez; "Klasörsüz"e düşerler). Liste üstünde filtre
  çipleri: Tümü · Klasörsüz · klasörler · "+". Çipe uzun basış: yeniden adlandır /
  sil. Boş durumda ders bazlı klasör önerisi.
- **Arama:** başlık **ve** mesaj içeriğinde, büyük/küçük harf duyarsız (Türkçe
  başlık eşleşmesi TR-locale ile). Arama etkinken sonuçlar klasörler arası gelir ve
  klasör rozeti taşır.
- Yedek v5: klasörler sabit id'lerle + sohbetlerin sabitleme/klasör alanları.

### Değişti
- `chat_threads` tablosu migration içinde SQLite yeniden-kurma reçetesiyle taşındı
  (`ALTER TABLE` FK ekleyemediği için) — veri %100 korunumlu.

### Düzeltildi
- Sohbet silme sonrasındaki "Geri Al" çubuğu hiç görünmüyordu: `LaunchedEffect`
  anahtarı `showSnackbar`'dan önce sıfırlanınca askıdaki snackbar iptal oluyordu.
  Canlı doğrulamada yakalandı; consume artık gösterimden sonra.

### Doğrulama
- 12 yeni test (sıralama, klasör-öksüz semantiği, TR harf duyarsız arama,
  başlık∪içerik birleşimi, geri yükleme klasör koruması) → toplam 75/75.
- MIGRATION_4_5 üretilen şemayla bit-bit + emülatörde v1.2.0 üzerine yerinde
  yükseltme: 4 gerçek sohbet, sıfır kayıp; tüm oturum eylemleri cihazda canlı
  doğrulandı; açık/koyu tur; 0 çökme.

## [1.2.0] — 2026-08-30 · "Profiller + İçe/Dışa Aktar + Notlar + Görsel Paso"

**Şema v4 · Yedek formatı v4 (v1–v3 okunur) · 63 unit test**

### Eklendi
- **AI sağlayıcı profilleri** (`ai_profiles`): tek-slot yapı kaldırıldı. Her profil
  ad + protokol (Anthropic / OpenAI-uyumlu) + taban URL (vekiller için Anthropic'te
  bile düzenlenebilir) + model + **profil başına Keystore-şifreli anahtar** taşır.
  Şablonlar: Claude, OpenAI, Gemini, xAI, OpenRouter, OpenCode Zen, Ollama-LAN, Özel
  (varsayılanlar 2026-08-30'da web'den doğrulandı). Eski v1.0/v1.1 yapılandırması ve
  anahtarı ilk açılışta otomatik taşınır.
- **Bağlantıyı Sına** (gerçek `models` ucu; dostça Türkçe mesaj + HTTP kodu +
  sunucunun hata gövdesi — 401/404/ağ hatası ayırt edilir) ve **Modelleri Getir**
  (canlı model listesi, arama + seçim).
- **Yeniden adlandırma:** görünen ad "YKS Takip"; yıl etiketi ("YKS 2027") ayarlardaki
  TYT tarihinden türetilir. `applicationId` değişmedi — güncellemeler kurulmaya devam
  eder.
- **Kronometre:** Sayaç'a ileri sayım modu; kalıcı durum makinesi, FGS ileri sayan
  kronometre bildirimi, alarm yok; ≥1 dk oturumlar `plannedMin=0` ile kaydedilir.
- **CSV dışa/içe aktarma:** `;` ayraçlı, BOM'lu, belgeli format; dışa aktar (SAF) +
  paylaş; içe aktarmada önizleme + mükerrer işaretleme + yalnızca EK yapan kayıt.
- **AI ile karne okuma:** görsel (≤1568px'e küçültülür) / PDF (≤20MB) / yapıştırılan
  metin → Claude → **onay için önceden doldurulmuş form**; otomatik kayıt yok; yalnız
  Anthropic profilleri.
- **Notlar** (`notes`): markdown + önizleme (mikepenz renderer); AI Koç yanıtlarında
  "Nota kaydet".
- **Paylaşım:** Geçmiş Haftalar'dan haftalık rapor metni (yalnız özet veriler);
  uzaktan takip için sıfır-kod yol belgelendi (Drive-eşitlenen otomatik yedek
  klasörü). Firebase/hesaplı canlı takip bilinçli olarak yapılmadı.
- **Görsel paso:** KPI sparkline'ları, ilerleme halkaları (plan/Konular/Sayaç
  kadranı), Zayıf Konular hata-türü dilimli şerit çubukları, Planlayıcı kategori
  şeridi, 8 haftalık aktivite ısı şeridi, Geçmiş Haftalar çift eksenli trend grafiği.

### Düzeltildi
- Tek-slot yapıda OpenAI anahtarının yanlış sağlayıcıya gidip "geçersiz" görünmesine
  yol açan yapılandırma karışıklığı, profil mimarisiyle kökten çözüldü.

### Doğrulama
- 63/63 test; migration 3→4 bit-bit + canlı v1.1→v1.2 yükseltmesi: kayıtlı anahtar
  migre edilen aktif profile bağlandı ve gerçek api.openai.com'un 401 gövdesinde
  maskeli görünmesiyle uçtan uca kanıtlandı; OpenRouter'dan canlı 396 modellik liste;
  CSV gidiş-dönüşünde 17/17 mükerrer işaretleme; gerçek 75 sn kronometre oturumu;
  açık/koyu tur; 0 çökme.

## [1.1.0] — 2026-08-30 · "M4: Konu Takibi"

**Şema v3 · 29 unit test**

### Eklendi
- ~140 konuluk gömülü müfredat kataloğu (MEB duyurusu doğrulandı: 2027'de mevcut
  müfredat geçerli; Maarif soru modeli 2028'de başlıyor).
- Deneme girişinde **konu işaretleri**: hangi konudan kaç yanlış/boş + hata türü
  (Bilgi / İşlem / Dikkat / Süre) — `exam_topic_marks` + `exam_topic_notes`.
- **Zayıf Konular** analizi (genel + ders bazında, hata dökümüyle).
- **Konular** ekranı: çalıştım / soru çözdüm / tekrar ettim çizelgesi
  (`topic_status`), ilerleme ve deneme kaynaklı yanlış rozetleri.
- AI Koç bağlamına zayıf konular eklendi.

### Doğrulama
- 29/29 test; migration 2→3 canlı doğrulama; imzalı release turu.

## [1.0.0] — 2026-08-30 · İlk sürüm (M1 + M2 + M3)

**Şema v2 · 24 unit test · İlk imzalı release**

- **Denemeler:** TYT/AYT tam + branş; ders başına ham D/Y girişi, canlı boş/net
  önizleme, çeyrek hassasiyetli **negatif netlere izin veren** net matematiği,
  kopya uyarısı, düzenleme + geri alınabilir silme.
- **Analiz:** Vico grafikleri (toplam net trendi, ders bazında trend/doğruluk/boş-
  yanlış), Son-5 ortalaması dahil KPI'lar.
- **Ana Sayfa:** çift TYT/AYT geri sayımı (tarih+saat ayarlanabilir; ÖSYM açıklayana
  kadar "tahmini" uyarısı), bugünün görevleri, son deneme KPI'ları, yedek
  hatırlatması.
- **Planlayıcı:** Pazartesi-anahtarlı 7 günlük pano (Europe/Istanbul), örtük arşiv,
  "geçen haftayı kopyala", dürüst çözülen-soru yakalama, kategori bazında haftalık
  çalışma süresi; **Geçmiş Haftalar** salt-okunur görünümü.
- **Sayaç:** tam durum makinesi — DataStore kalıcılığı, ön plan servisi (kronometre
  bildirimi), `USE_EXACT_ALARM` ile kesin bitiş garantisi, `BOOT_COMPLETED`
  kurtarması, ≥60 sn oturum kaydı, mola önerileri.
- **AI Koç (BYOK):** Claude (resmî Anthropic Java SDK) + OpenAI-uyumlu istemci
  (OpenAI/Gemini/xAI/Ollama-LAN); akışlı yanıtlar, durdurma, sohbet geçmişi,
  kapatılabilir istatistik bağlamı; anahtar Keystore-AES şifreli ve yedek dışı;
  anahtar yokken sekme gizli, sıfır ağ trafiği.
- **Koçluk:** haftalık birleşik rapor, seri (streak) KPI'ı, deneme başına eksik konu
  etiketleri.
- **Yedekleme:** SAF ile JSON dışa/içe aktarma + haftalık otomatik yedek (son 8
  tutulur); içe aktarma öncesi otomatik güvenlik anlık görüntüsü.
- **Arayüz:** Açık/Koyu/Sistem tema; `material3-adaptive` uyarlanabilir yerleşim;
  Glance geri sayım widget'ı.

---

<a id="english"></a>
# 🇬🇧 English

## [1.3.0] — 2026-08-30 · "AI Coach: Session Pane + Folders + Search"

**Schema v5 · Backup format v5 (reads v1–v4) · 75 unit tests**

### Added
- **Session pane:** chats graduated from a dropdown to first-class citizens. A ~320dp
  permanent left pane on wide screens; a bottom sheet behind the history icon on
  compact ones. The old 20-thread cap is gone — all threads are listed.
- **Ordering:** pinned first, then by **last message activity** (computed by query;
  no ordering column was added to the schema). Rows show title, relative time,
  message count, and a last-message snippet.
- **Thread actions (long-press):** Rename (a manual name is never overwritten by the
  auto-title) · Pin · Move to folder · Delete (confirm + **Undo** — restores
  messages, pin, and folder assignment).
- **Top bar** now shows the active thread's title; tapping it opens rename. The
  profile switcher and model subtitle remain.
- **Folders:** a `chat_folders` table + per-thread `folder_id` (FK **SET NULL** —
  deleting a folder never deletes threads; they drop to "Unfiled"). Filter chips
  above the list: All · Unfiled · folders · "+". Long-press a chip to rename/delete.
  Empty state suggests subject-based folders.
- **Search** over titles **and** message content, case-insensitive (Turkish-aware
  title matching). While searching, results cross folders and carry a folder badge.
- Backup v5: folders with stable ids + per-thread pin/folder fields.

### Changed
- The `chat_threads` table is migrated via the standard SQLite table-recreation
  recipe (because `ALTER TABLE` cannot add a foreign key) — fully data-preserving.

### Fixed
- The post-delete "Undo" snackbar never appeared: the `LaunchedEffect` key was
  cleared before `showSnackbar`, restarting the effect and cancelling the suspended
  snackbar. Caught during live verification; consume now happens after display.

### Verification
- 12 new tests (ordering, folder-orphan semantics, Turkish case-insensitive search,
  title∪content union, restore folder guard) → 75/75 total.
- MIGRATION_4_5 verified byte-for-byte against the generated schema + as a live
  in-place upgrade over v1.2.0 on an emulator: four real threads, zero loss; every
  session action verified on-device; light/dark tour; 0 crashes.

## [1.2.0] — 2026-08-30 · "Profiles + Import/Export + Notes + Visual Pass"

**Schema v4 · Backup format v4 (reads v1–v3) · 63 unit tests**

### Added
- **AI provider profiles** (`ai_profiles`), replacing the single-slot design. Each
  profile carries a name + protocol (Anthropic / OpenAI-compatible) + base URL
  (editable even for Anthropic, for proxies) + model + a **per-profile
  Keystore-encrypted key**. Templates: Claude, OpenAI, Gemini, xAI, OpenRouter,
  OpenCode Zen, Ollama-LAN, Custom (defaults verified against the web on
  2026-08-30). The legacy v1.0/v1.1 config and key migrate automatically on first
  launch.
- **Test Connection** (hits the real `models` endpoint; friendly Turkish message +
  HTTP status + the server's own error body — distinguishes 401/404/network) and
  **Fetch Models** (live model list with search and pick).
- **Rebrand:** display name "YKS Takip"; the year label derives from the TYT date in
  Settings. `applicationId` unchanged — updates keep installing.
- **Stopwatch:** count-up mode in the timer; persistent state machine, count-up FGS
  chronometer notification, no alarm; sessions ≥1 min log with `plannedMin=0`.
- **CSV export/import:** documented `;`-separated BOM format; export (SAF) + share;
  import with preview, duplicate flagging, and strictly additive inserts.
- **AI report-card reading:** image (downscaled to ≤1568px) / PDF (≤20MB) / pasted
  text → Claude → a **pre-filled form for confirmation**; nothing auto-saves;
  Anthropic profiles only.
- **Notes** (`notes`): markdown with preview (mikepenz renderer); "Save to note" on
  AI coach answers.
- **Sharing:** weekly report text from Past Weeks (aggregates only); the zero-code
  remote-visibility path documented (Drive-synced auto-backup folder).
  Firebase/account-based live monitoring deliberately not built.
- **Visual pass:** KPI sparklines, progress rings (plan/Topics/timer dial), Weak
  Topics error-type segmented bars, planner category strip, 8-week activity heat
  strip, dual-axis Past Weeks trend chart.

### Fixed
- The single-slot design could route an OpenAI key to the wrong provider and report
  it "invalid"; the profile architecture removes the failure mode entirely.

### Verification
- 63/63 tests; migration 3→4 byte-for-byte + live v1.1→v1.2 upgrade: the saved key
  attached to the migrated active profile, proven end-to-end by the real
  api.openai.com echoing it masked in a 401 body; a live 396-model list from
  OpenRouter; CSV round-trip with 17/17 duplicate flags; a real 75-second stopwatch
  session; light/dark tour; 0 crashes.

## [1.1.0] — 2026-08-30 · "M4: Topic Tracking"

**Schema v3 · 29 unit tests**

### Added
- An embedded ~140-topic curriculum catalog (MEB announcement verified: the current
  curriculum applies in 2027; the Maarif question model starts in 2028).
- **Topic marks** on exam entry: wrong/blank counts per topic + error type
  (knowledge / calculation / attention / time) — `exam_topic_marks` +
  `exam_topic_notes`.
- **Weak Topics** analytics (overall + per subject, with error breakdowns).
- **Topics** screen: studied / practiced / reviewed grid (`topic_status`), progress,
  and exam-fed wrong badges.
- Weak topics added to the AI coach context.

### Verification
- 29/29 tests; migration 2→3 verified live; signed-release tour.

## [1.0.0] — 2026-08-30 · Initial release (M1 + M2 + M3)

**Schema v2 · 24 unit tests · First signed release**

- **Mock exams:** full TYT/AYT + single-subject kinds; raw correct/wrong entry per
  subject, live blank/net preview, quarter-precision net math that **allows negative
  nets**, duplicate warning, edit + undoable delete.
- **Analytics:** Vico charts (total net trend, per-subject trend/accuracy/
  blank-vs-wrong), KPIs including a last-5 average.
- **Dashboard:** dual TYT/AYT countdowns (date+time configurable; an "estimated"
  banner until ÖSYM confirms), today's tasks, latest-exam KPIs, backup reminder.
- **Planner:** Monday-keyed 7-day board (Europe/Istanbul), implicit archiving,
  copy-last-week, honest solved-question capture, weekly study time per category;
  read-only **Past Weeks**.
- **Timer:** a full state machine — DataStore persistence, foreground service with a
  chronometer notification, exact-alarm completion guarantee (`USE_EXACT_ALARM`),
  `BOOT_COMPLETED` recovery, ≥60s session logging, break suggestions.
- **AI coach (BYOK):** Claude via the official Anthropic Java SDK + an
  OpenAI-compatible client (OpenAI/Gemini/xAI/Ollama-LAN); streaming with stop, chat
  history, a toggleable stats context; keys Keystore-AES encrypted and excluded from
  backups; without a key the tab is hidden and network traffic is zero.
- **Coaching:** weekly combined report, streak KPI, per-exam missing-topic tags.
- **Backup:** JSON export/import via SAF + weekly auto-backup (keeps the last 8);
  automatic pre-import safety snapshot.
- **UI:** Light/Dark/System theme; `material3-adaptive` layouts; a Glance countdown
  widget.
