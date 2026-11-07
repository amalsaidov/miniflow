# Arayüz turu

Üst menüde beş sayfa ve **EN | TR** dil seçimi var (çereze yazılır; `?lang=tr` her sayfada çalışır).

| Sayfa | Ne yapılır |
|---|---|
| **Panel** (`/dashboard`) | Son 24 saat / 7 / 14 gün için göstergeler (akış, çalıştırma, başarı oranı, ortalama/p95 süre), zamana göre çalıştırma grafiği (sayılar için çubuğun üzerine gelin), tetiklenme şekli, en çok çalışan akışlar, son hatalar, yaklaşan zamanlanmış çalıştırmalar ve sistem durumu |
| **Akışlar** (`/`) | Ad/id ile arama, durum ve tetikleyici filtresi, sayfalama (sayfa başına 10/25/50/100). Tetikleyici etiketleri (webhook yolu, cron + sonraki çalışma, ⇄ kuyruk), **Aktif** anahtarı, düzenle / JSON dışa aktar / sil, *JSON içe aktar*, *Tümünü dışa aktar*, *+ Yeni akış* |
| **Editör** (`/workflows/{id}/edit`) | Paletten düğüm sürükleyin, çıkışları girişlere bağlayın, yan panelde parametreleri **Form** (etiketli alanlar, açılır listeler, onay kutuları, anahtar–değer satırları) veya **JSON** olarak düzenleyin, **Kaydet**, **▶ Test et**. Testten sonra düğümler yeşil/kırmızı olur; düğüme tıklayınca çıktısını görürsünüz |
| **Çalıştırmalar** (`/executions`) | Tüm çalıştırmalar (manuel, webhook, zamanlama, kuyruk); akış, durum ve tetikleyiciye göre filtre, sayfalama; tıklayınca düğüm düğüm girdi/çıktı/hata |
| **Değişkenler** (`/variables`) | `{{ vars.ad }}` ile kullanılan gizli olmayan ayarlar |
| **Kimlik bilgileri** (`/credentials`) | Şifreli gizli bilgiler (token, şifre, bağlantı adresi), akışta adıyla kullanılır |

Editör ipuçları:
- **Test verisi** kutusu "Test et" ile tetikleyiciye gönderilir; tetikleyicinin `sample` parametresinden doldurulur.
- Test, akış **pasifken** de çalışır ve `dedupe` anahtarlarını kaydetmez; test etmek gerçek bildirimi "tüketmez".
- `if` düğümünün **true/false**, `dedupe` düğümünün **new/seen** çıkışları var. Bağlanmamış çıkış akışı orada bitirir.
- Kaydedilmemiş değişiklikle sayfadan çıkarken tarayıcı uyarır.
