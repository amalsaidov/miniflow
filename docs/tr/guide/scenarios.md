# Gerçekçi senaryolar adım adım

`examples/realistic.json` küçük bir online mağazayı ve yazılım ekibini modeller. 10 akışın hepsi **pasif** gelir.

## Bir kerelik kurulum
1. **İçe aktarın:** Akışlar → *JSON içe aktar* → `examples/realistic.json` → *İçe aktar*.
2. **Demo mağaza veritabanı:** MiniFlow'u `MINIFLOW_H2_CONSOLE=true` ile başlatıp `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/miniflow`, kullanıcı `sa`, şifre boş)
   açın, `examples/sql/shop-schema.sql` içeriğini yapıştırıp çalıştırın. (PostgreSQL kullanıyorsanız kendi veritabanınızda çalıştırın.)
3. **Kimlik bilgileri:** her senaryonun ihtiyacını [Kimlik bilgileri](credentials.md) sayfasındaki adlarla (`shop-db`, `smtp`, `slack`, …) oluşturun.
4. **Değişkenler:** gerekenleri girin: `reportEmail`, `healthCheckUrl`, `githubRepo`, `leadsSheetId`, `supportSheetId`.
5. Akışı açın, örnek veriyle **▶ Test et**, düğümleri inceleyin, sonra **Aktif** yapın.

| Akış | Tetikleyici | Kimlik bilgileri | Değişkenler |
|---|---|---|---|
| `order-pipeline` | webhook `/webhook/shop-orders` | shop-db, rabbitmq, slack, smtp | — |
| `support-triage` | webhook `/webhook/support` | openai, github, slack, smtp, google | githubRepo, supportSheetId |
| `uptime-incident` | 5 dakikada bir | github, slack | healthCheckUrl, githubRepo |
| `daily-sales-report` | hafta içi 07:45 | shop-db, smtp, slack | reportEmail |
| `lead-capture` | webhook `/webhook/leads` | google, slack | leadsSheetId |
| `payment-failure-monitor` | Kafka `payments.events` | kafka, redis, slack | — |
| `release-watch-ai` | her gün 09:00 | openai, slack, github | githubRepo |
| `pr-issue-sync` | webhook `/webhook/github-pr` (imzalı) | github, slack | githubWebhookSecret |
| `export-request` | webhook `/webhook/exports` | redis | — |
| `export-worker` | Redis listesi `export-jobs` | redis, shop-db, smtp | — |

## `order-pipeline` — e-ticaret sipariş alımı
```
webhook → geçerli ve ödenmiş mi? ─✗→ rejected
            ✓ → normalize (trim, küçük harf e-posta, para birimi) → daha önce işlendi mi? ─seen→ duplicate-ignored
                                                                    new → INSERT shop_orders → RabbitMQ fulfillment.orders
                                                                          → ≥ 1000? ─✓→ Slack #satış → onay e-postası → accepted
                                                                                    ─✗──────────────→ onay e-postası → accepted
```
Neden gerçekçi: mağazalar webhook'u tekrar dener; `order-{{ orderNo }}` ile `dedupe` işlemi idempotent yapar; depo kalıcı bir kuyruktan tüketir; yalnızca büyük siparişler satışa bildirilir.
```bash
curl -X POST localhost:8080/webhook/shop-orders -H 'Content-Type: application/json' -d '{
  "orderNo":"ORD-1001","currency":"eur","total":1250.0,"paymentStatus":"paid",
  "customer":{"name":"Alice Smith","email":"Alice@Example.com"},
  "items":[{"sku":"HP-200","name":"Headphones","qty":2,"price":450},{"sku":"CB-10","name":"USB-C cable","qty":1,"price":350}]}'
```
İki kez gönderin: ikinci yanıt `"status": "duplicate-ignored"` olur. H2'de satırı (`SELECT * FROM shop_orders`), RabbitMQ arayüzünde mesajı görün.

## `support-triage` — destek taleplerinin yapay zekâyla sınıflandırılması
Sitedeki iletişim formu `/webhook/support` adresine gönderir. AI düğümü `{category, urgency, sentiment, summary}` JSON'u döner.
- **Acil** → `vars.githubRepo` deposunda GitHub issue (etiketler `support`, kategori, `urgent`) → issue bağlantısıyla Slack bildirimi.
- **Acil değil** → Google Sheets'in `Tickets` sekmesine satır (`vars.supportSheetId`).
- Her iki durumda → müşteriye alındı e-postası; webhook kategori/aciliyet/kayıt no ile yanıt verir.

`projectKey`, issue tipi ve sistem mesajını kendi ortamınıza göre değiştirin. Tabloda önce `Tickets` sekmesini oluşturun.

## `uptime-incident` — olay kaydı açan izleme
5 dakikada bir `failOnError: false` ile `GET {{ vars.healthCheckUrl }}`. Site çöktüğünde (2xx dışı veya bağlantı yok) `vars.githubRepo`
deposunda GitHub issue açılır (etiketler `incident`, `monitoring`) ve bağlantısıyla Slack'e yazılır —
`incident-{{ now.substring(0, 13) }}` anahtarlı `dedupe` sayesinde **saatte en fazla bir kez**.

## `daily-sales-report` — SQL → Excel → e-posta + Slack
Hafta içi 07:45: *dünün* özet sorgusu (sipariş sayısı, ciro, ortalama sepet), tüm sipariş listesi → `spreadsheet` (xlsx) →
Excel ekli e-posta `vars.reportEmail` adresine → Slack'e tek satır. Demo veriyle "3 orders, 1759.90 revenue" görürsünüz.
Gerçek veritabanında `shop-db` için **salt okunur** kullanıcı kullanın.

## `lead-capture` — açılış sayfası → CRM tablosu
`POST /webhook/leads` (veya HTML form) → normalize (şirket yoksa e-posta alan adından, `utm_source`) → e-postaya göre `dedupe` →
`vars.leadsSheetId` tablosunun `Leads` sekmesine ekle (sütun sırası: receivedAt, name, email, company, plan, source) → Slack #satış.
Tekrar kayıt olanlar yalnızca teşekkür yanıtı alır.

## `payment-failure-monitor` — Kafka + Redis sayacı
`payments.events` topic'ini dinler. Her `FAILED` olayında Redis'teki `payment-failures:<saat>` anahtarını artırır;
bir saat içinde 5'e ulaşınca ödeme ekibi o saat için **tek** Slack uyarısı alır. Mesaj üreterek deneyin:
```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic payments.events
{"paymentId":"pay_1","orderNo":"ORD-1001","status":"FAILED","reason":"card_declined"}
```
Veya `integrations.json` içindeki `kafka-event-publisher` örneğini kullanın.

## `release-watch-ai` — bağımlılık sürüm özeti
Her gün: Spring Boot sürümlerinin Atom akışını okur, kayıt id'sine göre `dedupe`, yapay zekâdan 3 maddelik özet ister (önce kırıcı değişiklikler),
Slack'e yazar ve `vars.githubRepo` deposunda GitHub yükseltme issue'su açar. RSS adresini herhangi bir GitHub projesi için değiştirin (`https://github.com/<sahip>/<depo>/releases.atom`).

## `pr-issue-sync` — GitHub pull request → bağlı issue'ya yorum + Slack
GitHub deposu → Settings → Webhooks: payload URL `https://<genel-adres>/webhook/github-pr`, içerik tipi `application/json`,
secret = `githubWebhookSecret` değişkeni, olay **Pull requests**. PR başlığında veya açıklamasında bir issue geçiyorsa
(`Fixes #142`) o issue'ya "Pull request #77 merged by @…" yorumu eklenir ve Slack'e PR bağlantılı tek satır gider;
diğer PR'lar yok sayılır. Yerelde `ngrok http 8080` veya
`gh webhook forward --repo=<sahip>/<depo> --events=pull_request --url=http://localhost:8080/webhook/github-pr` kullanın.

## / 11.10 `export-request` + `export-worker` — asenkron işler
Klasik üretici/tüketici ikilisi: istek webhook'u **hemen** yanıt verir ("hazırlanıyor") ve işi Redis listesi `export-jobs`'a ekler;
işçi akış (onu da aktif yapın) işleri alır, müşterinin siparişlerini sorgular, Excel üretir ve e-postayla gönderir.
```bash
curl -X POST localhost:8080/webhook/exports -H 'Content-Type: application/json' -d '{"email":"alice@example.com"}'
```
Yavaş işlerde (rapor, yapay zekâ, büyük dosya) bu kalıbı kullanın; webhook'lar hızlı kalır.
