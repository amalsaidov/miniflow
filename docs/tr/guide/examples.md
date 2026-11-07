# Örnek kütüphanesi

Tüm örnekler `examples/` klasöründe. Her dosya tek seferde içe aktarılabilir; `examples/workflows/` içinde her akış ayrı dosyadır.

| Dosya | İçerik | Gereken |
|---|---|---|
| *(ilk açılışta yüklenir)* | `order-flow`, `contact-form`, `fx-rate-alert`, `site-health-check` | hiçbir şey |
| `basics.json` | yağmur uyarısı, sabah özeti, deprem uyarısı, kripto alarmı, GitHub webhook yönlendirici, destek talebi yönlendirici, sepet hesaplayıcı, mini kur API'si | hiçbir şey (uyarılar `vars.alertWebhook` adresine) |
| `github.json` | GitHub aktivitesi, yıldız takibi, haftalık özet, sürüm takibi, imzalı webhook yönlendirici, haftalık SQL raporu → gizli gist, geliştirici günlüğü (commit + PR → AI → veritabanı) ve stand-up notu | hiçbir şey (isteğe bağlı `githubToken`); gist ve günlük akışları `github` / `github-http`, `miniflow-db`, `gemini`, `slack` ister (tablo: `examples/sql/dev-journal.sql`) |
| `integrations.json` | RSS → Slack, günlük çalıştırma raporu (SQL → Excel → e-posta), iletişim formu → e-posta + Slack, webhook → Kafka | `slack`, `smtp`, `miniflow-db`, `kafka` |
| `realistic.json` | 10 iş senaryosu (sonraki bölüm) | her senaryoda ayrı |
| `google-sheets.json` | Görev satırı ekleme (webhook), açık görev özeti (okuma + filtre), dünkü çalıştırmalar → tablo (SQL → ekleme) | `google` (+ `miniflow-db`), `sheetId` değişkeni |
| `sql/shop-schema.sql` | demo `shop_orders` tablosu + veri | — |

## Google Sheets hızlı başlangıç (`examples/google-sheets.json`)
1. `google` kimlik bilgisini oluşturun (servis hesabı JSON'u, bkz. [Kimlik bilgileri](credentials.md)) ve **tabloyu** servis hesabının e-postasıyla *Düzenleyen* olarak paylaşın.
2. Tabloya şu başlık satırlarıyla iki sekme ekleyin:
   - `Tasks`: `created | task | owner | due | status`
   - `Executions`: `started_at | workflow | mode | status | duration_ms`
3. Değişkenler sayfası → `sheetId` = tablo adresindeki id (`https://docs.google.com/spreadsheets/d/<sheetId>/edit`).
4. Dosyayı içe aktarın, `sheets-add-task` akışını açıp **▶ Test et** → `Tasks` sekmesine yeni satır gelir. Aktif yapıp her yerden görev ekleyin:
   ```bash
   curl -X POST localhost:8080/webhook/tasks -H 'Content-Type: application/json' \
     -d '{"task":"SSL sertifikasını yenile","owner":"Bob","due":"2026-11-30"}'
   ```
5. `sheets-open-tasks-digest` sekmeyi okur, `status = open` olan satırları süzer (`{{ input.rows.?[status == 'open'] }}`) ve madde listesi
   oluşturur; `log` düğümüyle biter — yerine Slack, e-posta veya gist koyabilirsiniz. Bir görevi bitirmek için tabloda durumunu `done` yapın.
6. `sheets-execution-log` her sabah dünkü MiniFlow çalıştırmalarını ekler (SQL satırları + `columns` sütun sırasını belirler).

Değerler tabloda göründüğü gibi (metin) okunur, bu yüzden metin olarak karşılaştırın (`status == 'open'`); eklenen değerler elle yazılmış gibi
(`USER_ENTERED`) girilir, tarih ve sayıları Sheets tanır.

Hemen deneyin (ağ ve kimlik bilgisi gerekmez):
```bash
curl -X POST localhost:8080/webhook/cart -H 'Content-Type: application/json' \
  -d '{"product":"Headphones","price":450,"qty":2,"coupon":"SAVE10"}'
curl "localhost:8080/webhook/fx-rate?from=USD&to=TRY"
curl -X POST localhost:8080/webhook/orders -H 'Content-Type: application/json' -d '{"orderId":42,"customer":"Ayşe","amount":1500}'
```
