package com.yks2027.tracker.core.model

/**
 * M4 (Konu Takibi) — the bundled YKS topic catalog. MEB/ÖSYM confirmed the current
 * curriculum's topic scope holds for the 2026 AND 2027 exams (the Maarif Modeli
 * question approach starts 2028), so these standard ders-ders lists are correct for
 * this student. IDs are stable ASCII slugs persisted as TEXT — never rename an id;
 * relabeling is free.
 */

enum class ErrorType(val label: String) {
    BILGI("Bilgi"),
    ISLEM("İşlem"),
    DIKKAT("Dikkat"),
    SURE("Süre"),
}

data class Topic(
    val id: String,
    val subject: Subject,
    /** Sub-subject grouping (e.g. TYT Fen → Fizik/Kimya/Biyoloji); null = no grouping. */
    val group: String?,
    val label: String,
)

object TopicCatalog {

    val all: List<Topic> = buildList {
        fun t(subject: Subject, group: String?, id: String, label: String) =
            add(Topic(id, subject, group, label))

        // ---- TYT Türkçe --------------------------------------------------------
        val tr = Subject.TYT_TURKCE
        t(tr, null, "tr-sozcukte-anlam", "Sözcükte Anlam")
        t(tr, null, "tr-cumlede-anlam", "Cümlede Anlam")
        t(tr, null, "tr-paragraf", "Paragraf")
        t(tr, null, "tr-sozel-mantik", "Sözel Mantık")
        t(tr, null, "tr-ses-bilgisi", "Ses Bilgisi")
        t(tr, null, "tr-yazim-kurallari", "Yazım Kuralları")
        t(tr, null, "tr-noktalama", "Noktalama İşaretleri")
        t(tr, null, "tr-sozcuk-turleri", "Sözcük Türleri (İsim-Sıfat-Zamir-Zarf-Edat)")
        t(tr, null, "tr-fiiller", "Fiiller, Fiilimsiler ve Çatı")
        t(tr, null, "tr-cumle-ogeleri", "Cümlenin Ögeleri")
        t(tr, null, "tr-cumle-turleri", "Cümle Türleri")
        t(tr, null, "tr-anlatim-bozuklugu", "Anlatım Bozukluğu")

        // ---- TYT Matematik -----------------------------------------------------
        val tm = Subject.TYT_MATEMATIK
        t(tm, "Matematik", "tm-temel-kavramlar", "Temel Kavramlar")
        t(tm, "Matematik", "tm-sayi-basamaklari", "Sayı Basamakları")
        t(tm, "Matematik", "tm-bolme-bolunebilme", "Bölme ve Bölünebilme")
        t(tm, "Matematik", "tm-ebob-ekok", "EBOB-EKOK")
        t(tm, "Matematik", "tm-rasyonel", "Rasyonel Sayılar")
        t(tm, "Matematik", "tm-esitsizlik", "Basit Eşitsizlikler")
        t(tm, "Matematik", "tm-mutlak-deger", "Mutlak Değer")
        t(tm, "Matematik", "tm-uslu", "Üslü Sayılar")
        t(tm, "Matematik", "tm-koklu", "Köklü Sayılar")
        t(tm, "Matematik", "tm-carpanlara-ayirma", "Çarpanlara Ayırma")
        t(tm, "Matematik", "tm-oran-oranti", "Oran-Orantı")
        t(tm, "Matematik", "tm-denklem", "Denklem Çözme")
        t(tm, "Matematik", "tm-problem-sayi", "Sayı ve Kesir Problemleri")
        t(tm, "Matematik", "tm-problem-yas", "Yaş Problemleri")
        t(tm, "Matematik", "tm-problem-hareket", "Hareket ve İşçi Problemleri")
        t(tm, "Matematik", "tm-problem-yuzde", "Yüzde, Kâr-Zarar ve Karışım Problemleri")
        t(tm, "Matematik", "tm-kumeler", "Kümeler ve Kartezyen Çarpım")
        t(tm, "Matematik", "tm-mantik", "Mantık")
        t(tm, "Matematik", "tm-fonksiyonlar", "Fonksiyonlar")
        t(tm, "Matematik", "tm-perm-komb-olasilik", "Permütasyon-Kombinasyon-Olasılık")
        t(tm, "Matematik", "tm-veri", "Veri ve İstatistik")
        t(tm, "Geometri", "tg-dogruda-acilar", "Doğruda Açılar")
        t(tm, "Geometri", "tg-ucgende-acilar", "Üçgende Açılar")
        t(tm, "Geometri", "tg-eslik-benzerlik", "Eşlik ve Benzerlik")
        t(tm, "Geometri", "tg-ucgen-alan", "Üçgende Alan")
        t(tm, "Geometri", "tg-ucgen-yardimci", "Açıortay-Kenarortay")
        t(tm, "Geometri", "tg-cokgenler", "Çokgenler")
        t(tm, "Geometri", "tg-dortgenler", "Özel Dörtgenler")
        t(tm, "Geometri", "tg-cember-daire", "Çember ve Daire")
        t(tm, "Geometri", "tg-analitik", "Analitik Geometri (Temel)")
        t(tm, "Geometri", "tg-kati-cisimler", "Katı Cisimler")

        // ---- TYT Fen -----------------------------------------------------------
        val tf = Subject.TYT_FEN
        t(tf, "Fizik", "tf-fizik-giris", "Fizik Bilimine Giriş")
        t(tf, "Fizik", "tf-madde", "Madde ve Özellikleri")
        t(tf, "Fizik", "tf-hareket-kuvvet", "Hareket ve Kuvvet")
        t(tf, "Fizik", "tf-enerji", "İş, Güç ve Enerji")
        t(tf, "Fizik", "tf-isi-sicaklik", "Isı ve Sıcaklık")
        t(tf, "Fizik", "tf-elektrik", "Elektrik ve Manyetizma (Temel)")
        t(tf, "Fizik", "tf-basinc", "Basınç")
        t(tf, "Fizik", "tf-kaldirma", "Kaldırma Kuvveti")
        t(tf, "Fizik", "tf-dalgalar", "Dalgalar")
        t(tf, "Fizik", "tf-optik", "Optik")
        t(tf, "Kimya", "tk-kimya-bilimi", "Kimya Bilimi")
        t(tf, "Kimya", "tk-atom", "Atom ve Periyodik Sistem")
        t(tf, "Kimya", "tk-turler-arasi", "Kimyasal Türler Arası Etkileşimler")
        t(tf, "Kimya", "tk-haller", "Maddenin Hâlleri")
        t(tf, "Kimya", "tk-kanunlar", "Kimyanın Temel Kanunları ve Hesaplamalar")
        t(tf, "Kimya", "tk-karisimlar", "Karışımlar")
        t(tf, "Kimya", "tk-asit-baz", "Asitler, Bazlar ve Tuzlar")
        t(tf, "Kimya", "tk-kimya-her-yerde", "Kimya Her Yerde")
        t(tf, "Biyoloji", "tb-canli-ozellik", "Canlıların Ortak Özellikleri ve Bileşenleri")
        t(tf, "Biyoloji", "tb-hucre", "Hücre")
        t(tf, "Biyoloji", "tb-bolunme", "Hücre Bölünmeleri ve Üreme")
        t(tf, "Biyoloji", "tb-kalitim", "Kalıtım")
        t(tf, "Biyoloji", "tb-ekosistem", "Ekosistem ve Çevre")
        t(tf, "Biyoloji", "tb-alemler", "Canlılar Dünyası")

        // ---- TYT Sosyal --------------------------------------------------------
        val ts = Subject.TYT_SOSYAL
        t(ts, "Tarih", "th-ilk-orta-cag", "İlk ve Orta Çağ Uygarlıkları")
        t(ts, "Tarih", "th-turk-islam", "Türk-İslam Devletleri ve Beylikler")
        t(ts, "Tarih", "th-osmanli", "Osmanlı Tarihi")
        t(ts, "Tarih", "th-milli-mucadele", "Millî Mücadele")
        t(ts, "Tarih", "th-inkilap", "Atatürk İlkeleri ve İnkılaplar")
        t(ts, "Coğrafya", "cg-doga-insan", "Doğa, İnsan ve Harita Bilgisi")
        t(ts, "Coğrafya", "cg-iklim", "İklim Bilgisi")
        t(ts, "Coğrafya", "cg-yer-sekilleri", "Yer Şekilleri ve İç-Dış Kuvvetler")
        t(ts, "Coğrafya", "cg-beseri", "Nüfus, Yerleşme ve Ekonomi")
        t(ts, "Coğrafya", "cg-cevre", "Çevre ve Doğal Afetler")
        t(ts, "Felsefe", "fl-giris", "Felsefeye Giriş")
        t(ts, "Felsefe", "fl-bilgi-bilim", "Bilgi ve Bilim Felsefesi")
        t(ts, "Felsefe", "fl-varlik", "Varlık Felsefesi")
        t(ts, "Felsefe", "fl-ahlak-din", "Ahlak, Sanat ve Din Felsefesi")
        t(ts, "Din", "dn-inanc", "İnanç ve Bilgi")
        t(ts, "Din", "dn-ibadet", "İbadet")
        t(ts, "Din", "dn-ahlak", "Ahlak ve Değerler")
        t(ts, "Din", "dn-hz-muhammed", "Hz. Muhammed'in Hayatı")
        t(ts, "Din", "dn-vahiy-akil", "Vahiy ve Akıl")

        // ---- AYT Matematik -----------------------------------------------------
        val am = Subject.AYT_MATEMATIK
        t(am, "Matematik", "am-fonksiyonlar", "Fonksiyonlar ve Uygulamaları")
        t(am, "Matematik", "am-polinomlar", "Polinomlar")
        t(am, "Matematik", "am-ikinci-derece", "İkinci Dereceden Denklemler")
        t(am, "Matematik", "am-parabol", "Parabol")
        t(am, "Matematik", "am-esitsizlikler", "Eşitsizlikler")
        t(am, "Matematik", "am-trigonometri", "Trigonometri")
        t(am, "Matematik", "am-logaritma", "Üstel ve Logaritmik Fonksiyonlar")
        t(am, "Matematik", "am-diziler", "Diziler")
        t(am, "Matematik", "am-limit", "Limit ve Süreklilik")
        t(am, "Matematik", "am-turev", "Türev")
        t(am, "Matematik", "am-integral", "İntegral")
        t(am, "Matematik", "am-olasilik", "Permütasyon-Kombinasyon-Binom-Olasılık")
        t(am, "Geometri", "ag-ucgen-dortgen", "Üçgenler, Dörtgenler ve Çokgenler")
        t(am, "Geometri", "ag-cember", "Çember ve Daire")
        t(am, "Geometri", "ag-analitik", "Analitik Geometri")
        t(am, "Geometri", "ag-kati", "Katı Cisimler")

        // ---- AYT Fizik ---------------------------------------------------------
        val af = Subject.AYT_FIZIK
        t(af, null, "af-vektorler", "Vektörler")
        t(af, null, "af-kuvvet-hareket", "Kuvvet ve Hareket (Newton)")
        t(af, null, "af-atislar", "Atışlar")
        t(af, null, "af-is-enerji", "İş, Güç ve Enerji")
        t(af, null, "af-itme-momentum", "İtme ve Momentum")
        t(af, null, "af-tork-denge", "Tork ve Denge")
        t(af, null, "af-basit-makineler", "Basit Makineler")
        t(af, null, "af-elektrik-alan", "Elektrik Alan ve Potansiyel")
        t(af, null, "af-sigalar", "Düzgün Elektrik Alan ve Sığa")
        t(af, null, "af-manyetizma", "Manyetizma ve İndüksiyon")
        t(af, null, "af-alternatif-akim", "Alternatif Akım ve Transformatör")
        t(af, null, "af-cembersel", "Çembersel Hareket ve Kütle Çekimi")
        t(af, null, "af-harmonik", "Basit Harmonik Hareket")
        t(af, null, "af-dalga-mekanigi", "Dalga Mekaniği")
        t(af, null, "af-atom", "Atom Fiziği ve Radyoaktivite")
        t(af, null, "af-modern", "Modern Fizik ve Uygulamaları")

        // ---- AYT Kimya ---------------------------------------------------------
        val ak = Subject.AYT_KIMYA
        t(ak, null, "ak-modern-atom", "Modern Atom Teorisi")
        t(ak, null, "ak-gazlar", "Gazlar")
        t(ak, null, "ak-cozeltiler", "Sıvı Çözeltiler ve Çözünürlük")
        t(ak, null, "ak-tepkime-enerji", "Kimyasal Tepkimelerde Enerji")
        t(ak, null, "ak-tepkime-hiz", "Kimyasal Tepkimelerde Hız")
        t(ak, null, "ak-denge", "Kimyasal Denge")
        t(ak, null, "ak-sulu-denge", "Sulu Çözelti Dengeleri (Asit-Baz)")
        t(ak, null, "ak-elektrokimya", "Kimya ve Elektrik")
        t(ak, null, "ak-karbon", "Karbon Kimyasına Giriş")
        t(ak, null, "ak-organik", "Organik Bileşikler")
        t(ak, null, "ak-enerji-kaynaklari", "Enerji Kaynakları ve Bilimsel Gelişmeler")

        // ---- AYT Biyoloji ------------------------------------------------------
        val ab = Subject.AYT_BIYOLOJI
        t(ab, null, "ab-sinir", "Sinir Sistemi")
        t(ab, null, "ab-endokrin", "Endokrin Sistem")
        t(ab, null, "ab-duyu", "Duyu Organları")
        t(ab, null, "ab-destek-hareket", "Destek ve Hareket Sistemi")
        t(ab, null, "ab-sindirim", "Sindirim Sistemi")
        t(ab, null, "ab-dolasim", "Dolaşım ve Bağışıklık")
        t(ab, null, "ab-solunum", "Solunum Sistemi")
        t(ab, null, "ab-uriner", "Üriner Sistem")
        t(ab, null, "ab-ureme", "Üreme ve Gelişme")
        t(ab, null, "ab-komunite", "Komünite ve Popülasyon Ekolojisi")
        t(ab, null, "ab-genden-proteine", "Genden Proteine")
        t(ab, null, "ab-enerji", "Canlılarda Enerji Dönüşümleri")
        t(ab, null, "ab-bitki", "Bitki Biyolojisi")
        t(ab, null, "ab-canli-cevre", "Canlılar ve Çevre")
    }

    private val byIdMap: Map<String, Topic> = all.associateBy { it.id }
    private val bySubjectMap: Map<Subject, List<Topic>> = all.groupBy { it.subject }

    fun byId(id: String): Topic? = byIdMap[id]
    fun labelOf(id: String): String = byIdMap[id]?.label ?: id
    fun topicsFor(subject: Subject): List<Topic> = bySubjectMap[subject].orEmpty()
    fun countFor(subject: Subject): Int = topicsFor(subject).size
}
