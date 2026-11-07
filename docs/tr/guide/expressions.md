# İfadeler

Her metin parametresi `{{ … }}` ifadesi içerebilir (Spring Expression Language, kısıtlı ortamda).
Değerin tamamı tek bir ifadeyse sonuç tipini korur (sayı, boolean, liste, nesne); değilse metne eklenir.

| Ad | Anlamı | Örnek |
|---|---|---|
| `input` | Önceki düğümün çıktısı | `{{ input.total * 1.2 }}` |
| `trigger` | Akışı başlatan veri | `{{ trigger.customer.email }}` |
| `nodes` | Önceki herhangi bir düğümün çıktısı | `{{ nodes['normalize'].orderNo }}` |
| `vars` | Değişkenler sayfası | `{{ vars.reportEmail }}` |
| `now` | Şu anki zaman (ISO metin) | `{{ now }}` → `2026-11-04T10:15:00` |
| `today` | Bugünün tarihi | `report-{{ today }}` |
| `nowMillis` | Epoch milisaniye | `{{ input.time > nowMillis - 600000 }}` |

**Kısa düğüm adları:** Her düğümün çıktısına id'siyle de ulaşılır: `{{ ping.status }}`, `{{ nodes['ping'].status }}` ile aynıdır.
Tire içeren id'ler camelCase olur: `first-alert` → `{{ firstAlert.key }}`.

**Yardımcı fonksiyonlar:**

| Fonksiyon | Örnek | Sonuç |
|---|---|---|
| `join(liste[, ayraç])` | `{{ join(rows.![name], '\n') }}` | her satıra bir ad (varsayılan ayraç `, `) |
| `default(değer, yedek)` | `{{ default(input.company, '-') }}` | null / boşsa yedek değer |
| `format(tarih, kalıp)` | `{{ format(now, 'dd.MM.yyyy HH:mm') }}` | ISO metin, tarih nesnesi veya epoch ms |
| `round(sayı[, basamak])` | `{{ round(input.total * 1.2, 2) }}` | yarıdan yukarı yuvarlama |
| `sum(liste)` / `count(liste)` | `{{ sum(rows.![total]) }}` | toplam / eleman sayısı |
| `json(değer)` | `{{ json(input) }}` | değerin JSON metni |

**Editörde:** Bir düğüm seçince **Kullanılabilir veri** paneli açılır: önceki adımların çıktıları (son testten veya son çalıştırmadan),
`trigger`, `vars` ve fonksiyonlar. Bir alana tıklayınca ifade imlecin olduğu yere eklenir; parametrelerin altındaki **Önizleme**
her parametrenin neye dönüşeceğini gösterir — önizleme sırasında hiçbir şey gönderilmez.

Sık kullanılan kalıplar:
```text
{{ input.email == null }}                               olmayan alan null döner, hata vermez
{{ input.customer?.name?.trim() }}                      güvenli erişim
{{ input.plan == null ? 'free' : input.plan }}          varsayılan değer
{{ input.email.trim().toLowerCase() }}                  String metotları
{{ input.email.split('@')[1] }}                         e-postanın alan adı
{{ input.items.size() }}                                liste boyutu
{{ input.body.?[!fork].size() }}                        listeyi filtrele (selection)
{{ input.body.![name] }}                                listeden alan seç (projection)
{{ input.title.matches('.*[A-Z]+-[0-9]+.*') }}          regex kontrolü
{{ ((input.total) * 100 + 0.5).intValue() / 100.0 }}    2 haneye yuvarla
{{ now.substring(0, 13) }}                              "2026-11-04T10" → saatte bir anahtar
{{ input._headers['x-github-event'] }}                  webhook istek başlığı
```
Operatörler: `and`, `or`, `!`, `==`, `!=`, `>`, `<`, `+`, üçlü `a ? b : c`.
Güvenlik: `T(...)`, `new`, statik metotlar, `getClass()` ve değiştiren metotlar engellidir; kimlik bilgileri ifadelerden **okunamaz**.
