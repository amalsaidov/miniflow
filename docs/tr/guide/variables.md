# Değişkenler

Değişkenler gizli olmayan ayarları tutar: e-posta alıcıları, tablo id'leri, adresler, depo adları, eşikler. Kullanım: `{{ vars.ad }}`.
- **Değişkenler** sayfasından düzenlenir; değişiklik hemen geçerli olur.
- İlk açılışta `application.properties` içindeki `miniflow.vars[...]` değerleri tabloya kopyalanır. Yalnızca properties'te
  veya ortamda (`MINIFLOW_VARS_REPORTEMAIL=…`) tanımlı değerler *application.properties* etiketiyle görünür; düzenlerseniz tabloya alınır (tablo kazanır).
- Ad: harf, rakam ve `_`; harf veya `_` ile başlar. Büyük/küçük harf duyarsız.
- Değerler arayüzde açık görünür — **token ve şifreler Kimlik bilgilerine**.

Örneklerin kullandığı değişkenler: `reportEmail`, `healthCheckUrl`, `watchUrl`, `alertWebhook`, `githubUser`, `githubRepo`,
`githubWatchRepo`, `githubToken` (isteğe bağlı), `githubWebhookSecret`, `sheetId`, `leadsSheetId`, `supportSheetId`.
