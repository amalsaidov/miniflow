# MiniFlow nedir

MiniFlow, n8n mantığında, kendi sunucunuzda çalışan küçük bir otomasyon motorudur (Spring Boot 4, Java 21).

- **Akış (workflow)** bir JSON belgesidir: **düğümler** ve aralarındaki **bağlantılar**.
- Her akış bir **tetikleyici** ile başlar: editörde test, webhook (HTTP çağrısı), cron zamanlaması veya
  kuyruk mesajı (RabbitMQ / Kafka / Redis).
- Düğümler işi yapar: veriyi dönüştürür (`set`), dallanır (`if`), tekrarı önler (`dedupe`), API çağırır (`http`)
  ve servislerle konuşur (Slack, e-posta, SQL, yapay zekâ, GitHub, Google Sheets, CSV/Excel, Redis, RabbitMQ, Kafka, RSS).
- Akışlar, çalıştırma geçmişi, (şifreli) kimlik bilgileri ve değişkenler veritabanında tutulur
  (varsayılan H2 dosyası, isteğe bağlı PostgreSQL).
- Görsel editör (Thymeleaf + Drawflow) ile akışları tarayıcıda kurar ve test edersiniz. Arayüz **İngilizce** (varsayılan) ve **Türkçe**.

Tipik kullanımlar: sipariş veya form verisini alıp birden çok sisteme dağıtmak, zamanlanmış raporlar,
izleme ve alarm, GitHub otomasyonu, yapay zekâ destekli sınıflandırma, kuyruk işçileri.
