# Sorun giderme / SSS

| Belirti | Sebep / çözüm |
|---|---|
| Webhook 404 "not active" dönüyor | Akışı **Aktif** yapın (test için gerekmez) |
| Webhook 404 bulunamadı | O `path` ile bir akış yok; tetikleyici parametrelerini kontrol edin |
| GitHub webhook'unda 401 | `githubWebhookSecret` GitHub'daki secret ile aynı değil veya istek imzasız |
| `Credential 'x' not found` | Tam bu adla bir kimlik bilgisi oluşturun veya düğümdeki `credential` değerini değiştirin |
| Zamanlanmış akış hiç çalışmıyor | Aktif mi? Akışlar sayfasında *Sonraki çalışma*'ya bakın; cron sunucu saat dilimini kullanır |
| Bildirimler tekrarlanıyor | Bir `dedupe` düğümü ekleyin; testlerin anahtar kaydetmediğini unutmayın |
| Testten sonra bildirim hiç gelmiyor | Anahtar gerçek bir çalıştırmada zaten kaydedildi; anahtarı değiştirin veya `seen_keys` tablosunu temizleyin |
| Kuyruk etiketi **bağlanamadı** | Sunucuya ulaşılamıyor veya kimlik bilgisi yanlış; sebebi görmek için üzerine gelin, düzeltip pasif/aktif yapın |
| SQL sütunu ifadede null | Sütun adları küçük harf: `{{ input.first.order_no }}` |
| Gmail girişi reddediyor | Uygulama şifresi, port 587, starttls true |
| Google Sheets 403 | Tabloyu servis hesabı e-postasıyla Düzenleyen olarak paylaşın; Sheets API'yi etkinleştirin |
| AI düğümü JSON yerine metin dönüyor | `"json": true` verin ve `system` içinde JSON biçimini tarif edin |
| Editör paleti boş | Tarayıcı cdn.jsdelivr.net'e ulaşamıyor |
| Kimlik bilgileri çözülemiyor / *okunamıyor* etiketi | Şifreleme anahtarı değişti (ör. proje klasörü değiştirildi, `data/encryption.key` silindi ama PostgreSQL'deki veriler kaldı). Eski anahtarı geri yükleyin ya da her kaydı düzenleyip gizli değerleri yeniden girin. PostgreSQL ile her zaman `MINIFLOW_ENCRYPTION_KEY` verin; anahtar proje klasörüne bağlı kalmasın |
