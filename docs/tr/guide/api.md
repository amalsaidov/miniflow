# HTTP API

| Metot | Yol | Açıklama |
|---|---|---|
| GET | `/api/workflows` | Tüm akışlar |
| GET / PUT / DELETE | `/api/workflows/{id}` | Oku / oluştur-güncelle / sil |
| POST | `/api/workflows/import?overwrite=true` | Tek akış veya dizi içe aktar |
| POST | `/api/workflows/{id}/run` | Manuel çalıştır; gövde = tetikleyici verisi (pasifken de çalışır) |
| GET | `/api/executions`, `/api/executions/{id}` | Son 50 çalıştırma / tek çalıştırma |
| GET | `/api/node-types` | Kayıtlı düğüm tipleri |
| GET / POST | `/webhook/{path}` | Webhook tetikleyici (akış aktif olmalı) |
| GET | `/workflows/{id}/export`, `/workflows/export` | JSON indir |

API hata mesajları seçili dili izler (`miniflow-lang` çerezi veya `?lang=`).
