# İçe/dışa aktarma ve yedek

- **İçe aktarma (arayüz):** Akışlar → *JSON içe aktar* → dosya seçin veya JSON yapıştırın. Değiştirmek için *Aynı id'li akışların üzerine yaz* işaretleyin.
  İşaretli değilse var olan bir id içeren içe aktarma tamamen reddedilir, hiçbir şey değişmez.
- **İçe aktarma (API):** `curl -X POST 'localhost:8080/api/workflows/import?overwrite=true' -H 'Content-Type: application/json' --data-binary @examples/realistic.json`
- **Dışa aktarma:** her akışın *JSON* düğmesi veya *Tümünü dışa aktar*. Dışa aktarılan JSON gizli bilgi içermez; kimlik bilgileri adla geçer.
- **Başka sunucuya taşıma:** akışları dışa aktarın, kimlik bilgilerini (aynı adlarla) ve değişkenleri oluşturun, içe aktarın.
- **Yedek:** veritabanını (H2: uygulama kapalıyken `data/` klasörü; PostgreSQL: `pg_dump`) **ve** şifreleme anahtarını yedekleyin.
