# Üretimde çalıştırma

Kontrol listesi:
- [ ] **Kimlik doğrulama.** MiniFlow'da giriş yok. Kimlik doğrulamalı bir reverse proxy / VPN / SSO arkasına koyun veya Spring Security ekleyin. Dışarıya yalnızca `/webhook/**` açın.
- [ ] `MINIFLOW_ENCRYPTION_KEY` değerini bir gizli bilgi deposundan verin ve yedekleyin.
- [ ] PostgreSQL kullanın ve H2 konsolunu kapalı tutun (`MINIFLOW_H2_CONSOLE=false`, varsayılan).
- [ ] İmza destekleyen açık webhook'larda (GitHub) `signatureSecretVar` kullanın; diğerlerinde yola/sorguya gizli bir token koyup `if` ile kontrol edin.
- [ ] Akışların kullandığı veritabanı kullanıcıları en az yetkiye sahip olsun (raporlar için salt okunur).
- [ ] Tek örnek çalıştırın: cron ve kuyruk dinleyiciler uygulama içinde çalışır (birden çok örnek zamanlamaları iki kez çalıştırır).
- [ ] Webhook'lar senkron çalışır — yavaş işleri kuyruğa alın (bkz. export-request/worker).
- [ ] Kontrollü şema değişikliği için `ddl-auto=update` yerine Flyway/Liquibase kullanın.
- [ ] Logları (`log` düğümleri için `miniflow.log-node`) ve Çalıştırmalar sayfasını izleyin.
