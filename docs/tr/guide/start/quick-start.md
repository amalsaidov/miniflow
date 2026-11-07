# 5 dakikada ilk akış

Hedef: bülten kaydı alan, e-postayı doğrulayan ve yanıt veren bir webhook.

1. **Akışlar → + Yeni akış**, ad `Bülten kaydı`, id `newsletter`.
2. Editörde **Tetikleyici** düğümüne tıklayıp parametreleri yazın:
   ```json
   { "path": "newsletter", "sample": { "email": "Ayse@Example.com", "name": "Ayşe" } }
   ```
3. Bir **If** düğümü ekleyin, Tetikleyici → If bağlayın:
   ```json
   { "condition": "{{ input.email != null and input.email.contains('@') }}" }
   ```
4. If'in **true** çıkışına bir **Set** düğümü bağlayın:
   ```json
   { "keepInput": false, "values": { "ok": true, "email": "{{ input.email.trim().toLowerCase() }}", "message": "Teşekkürler {{ input.name }}!" } }
   ```
5. **false** çıkışına başka bir Set: `{ "keepInput": false, "values": { "ok": false, "error": "Geçerli bir e-posta gerekli" } }`.
6. **Kaydet**, sonra **▶ Test et**. Düğümlere tıklayıp çıktıları inceleyin.
7. Akışı **Aktif** yapıp çağırın:
   ```bash
   curl -X POST localhost:8080/webhook/newsletter -H 'Content-Type: application/json' \
     -d '{"email":"Ayse@Example.com","name":"Ayşe"}'
   ```
8. Faydalı hale getirin: Set'ten sonra **Google Sheets** (e-postayı ekle) veya **Slack** düğümü ekleyin. Bkz. [Kimlik bilgileri](../credentials.md).
