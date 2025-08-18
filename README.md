# RedstoneStateSaver

> 🇹🇷 Türkçe aşağıda · 🇬🇧 English below

Paper (1.21) için redstone devrelerinin durumunu kaydedip sunucu yeniden açıldığında geri yükleyen, aynı zamanda aşırı yoğun redstone sistemlerine karşı uyarı ve koruma sunan bir Minecraft eklentisi.

---

## 🇹🇷 Türkçe

### Ne yapar?
Sunucu kapanırken redstone tozu (wire) ve repeater gibi blokların durumu kaybolur ve devreler açılışta sıfırdan başlar. RedstoneStateSaver, redstone sinyali değişen her bloğun durumunu kaydeder; sunucu açıldığında blokları ve durumlarını geri yükleyip fizik güncellemesi uygular, böylece devreler kaldığı yerden devam eder.

### Özellikler
- **Durum kaydı:** `BlockRedstoneEvent` tetiklenen her bloğun materyali, tam `BlockData` metni ve redstone özellikleri (tozun gücü; repeater'ın `powered` ve `delay` değerleri) kaydedilir.
- **Periyodik ve kapanışta kayıt:** Ayarlanabilir aralıkla (varsayılan 60 sn) ve sunucu kapanırken diske yazılır. Yazma işlemi geçici dosya + `.bak` yedeği ile yapılır.
- **Açılışta geri yükleme:** Bloklar ana thread'de geri konur, 2 tick sonra toz ve repeater'lara fizik güncellemesi uygulanır.
- **Yoğunluk uyarısı:** Aynı blok belirli bir süre içinde eşik değerinden fazla tetiklenirse (varsayılan 10 sn'de 200), `rss.notify` yetkili oyunculara tıklanabilir uyarı gider: koordinata ışınlanma ve **[Chunk'a izin ver]** butonu.
- **Koruma:** Kaydedilmiş redstone bloklarının kırılması ve üzerine blok konması engellenir (`rss.bypass` olanlar hariç). Kırma engellendiğinde tıklanabilir **[Sistem'e izin ver]** butonu çıkar.
- **Chunk izinleri:** İzin verilen chunk'larda uyarı ve koruma devre dışı kalır. İzinler `states.yml` içinde saklanır.

### Gereksinimler
- **Paper** 1.21.x (`paper-api 1.21.1-R0.1-SNAPSHOT`, `api-version: 1.21`). Tıklanabilir mesajlar için BungeeCord chat API'si kullanılır (Paper/Spigot'ta mevcut).
- **Java 21** (`pom.xml` → `release 21`).

### Kurulum
1. [Releases](../../releases) sayfasından `RedstoneStateSaver-1.0.0.jar` dosyasını indirin.
2. Sunucunun `plugins/` klasörüne koyun.
3. Sunucuyu yeniden başlatın. `plugins/RedstoneStateSaver/config.yml` ve `states.yml` oluşturulur.

### Komutlar
Tek komut `/rss` (alias yok, `plugin.yml` yetkisi: `rss.use`, varsayılan herkes).

| Komut | Açıklama | Yetki |
|---|---|---|
| `/rss` | Yardım mesajı | `rss.use` |
| `/rss reload` | `config.yml`'yi ve `states.yml`'yi yeniden yükler | `rss.admin` |
| `/rss save` | Tüm durumları diske kaydeder | `rss.admin` |
| `/rss izin <x> <y> <z>` | Koordinatın bulunduğu chunk'a ve çevresine (`allow-radius-default`) izin verir. Sadece oyuncular | `rss.bypass` (config'teki `permissions.bypass`) |
| `/rss izinchunk <chunkX> <chunkZ> <yarıçap>` | Belirtilen chunk ve yarıçap içindeki chunk'lara izin verir, hemen kaydeder. Konsoldan çalıştırılırsa ilk dünya kullanılır | `rss.admin` veya `rss.bypass` |
| `/rss izinbolge <chunkX> <chunkZ> <yarıçap>` | `izinchunk` ile aynı (takma ad) | `rss.admin` veya `rss.bypass` |

### Yetkiler
| Yetki | Varsayılan | Açıklama |
|---|---|---|
| `rss.use` | herkes | `/rss` komutunu kullanma |
| `rss.admin` | op | `reload`, `save`, `izinchunk` |
| `rss.bypass` | op | Korumayı atlar, `izin` komutları |
| `rss.notify` | op | Yoğunluk uyarılarını alır |

### Yapılandırma (`config.yml`)
```yaml
save-on-change: true          # redstone değişince bloğu belleğe kaydet
save-interval-seconds: 60     # diske periyodik kayıt (0 = kapalı)
protection:
  enabled: true               # kırma/koyma koruması
warn:
  threshold: 200              # pencere içinde aynı blok için tetikleme eşiği
  window-seconds: 10          # uyarı sayım penceresi
  allow-radius-default: 1     # izin verirken çevre chunk yarıçapı
permissions:
  bypass: rss.bypass
  notify: rss.notify
messages:
  warning: "§c[RSS] Yoğun redstone sistemi tespit edildi: %x% %y% %z%"
  protected: "§cBu sistem korumalıdır."
```

### Veri dosyası (`states.yml`)
- `blocks.<dünya:x:y:z>`: `world`, `x`, `y`, `z`, `material`, `blockData`, isteğe bağlı `props` (`wirePower`, `repeaterPowered`, `repeaterDelay`).
- `allowedChunks.<n>`: `dünya:chunkX:chunkZ` biçiminde izinli chunk'lar.
- Kayıt sırasında önceki dosya `states.yml.bak` olarak saklanır.

### Kaynaktan derleme
```bash
mvn package
# çıktı: target/RedstoneStateSaver-1.0.0.jar
```

### Proje yapısı
```
src/main/java/dev/cayadev/redstonestatesaver/
  RedstoneStateSaver.java  # ana sınıf, zamanlayıcılar
  RedstoneListener.java    # redstone olayı, uyarı, kırma/koyma koruması
  CommandRss.java          # /rss komutu
  Storage.java             # states.yml okuma/yazma, geri yükleme, chunk izinleri
src/main/resources/
  plugin.yml, config.yml
```

### Bilinen sorunlar (koddan)
- Periyodik kayıt asenkron thread'de çalışıyor ve dünya bloklarını okuyor; Bukkit API'si açısından thread-güvenli değil.
- `states.yml` açılışta iki kez yükleniyor (constructor'da ve `onEnable` içinde).
- İzinli chunk'larda da bloklar kaydediliyor (izin kontrolü kayıttan sonra yapılıyor).
- `/rss izin` sadece chunk izni ekliyor ve diske hemen yazmıyor (periyodik kayıtta yazılır). `y` değeri kullanılmıyor.
- Kaldırılan blokların kayıtları silinmiyor; `/rss reload` bellekteki kayıtları temizlemiyor.
- Uyarıdaki ışınlanma butonu `/tp` çalıştırır, oyuncunun `/tp` yetkisi olmalıdır.
- Tab tamamlama yok.

### Proje tarihleri
- **Oluşturulma:** 18 Ağustos 2025
- **Son güncelleme:** 18 Ağustos 2025

### Lisans
Lisans belirtilmemiştir.

---

## 🇬🇧 English

### What it does
Redstone dust and repeater states are lost on shutdown, so circuits restart from scratch. RedstoneStateSaver records the state of every block whose redstone signal changes and restores the blocks and their states when the server starts, then applies a physics update so circuits continue where they left off.

### Features
- **State capture:** for every `BlockRedstoneEvent`, stores material, full `BlockData` string and redstone properties (wire power; repeater `powered` and `delay`).
- **Periodic and shutdown save:** configurable interval (default 60 s) and on disable; written via a temp file with a `.bak` backup.
- **Restore on startup:** blocks are restored on the main thread; 2 ticks later wire and repeaters get a physics update.
- **Activity warning:** if the same block fires more than the threshold within a window (default 200 in 10 s), players with `rss.notify` get a clickable warning (teleport + **[allow chunk]** button).
- **Protection:** breaking saved redstone blocks or placing into them is cancelled (except `rss.bypass`), with a clickable **[allow system]** button.
- **Chunk allowlist:** allowed chunks skip warnings and protection; stored in `states.yml`.

### Requirements
- **Paper** 1.21.x (`paper-api 1.21.1-R0.1-SNAPSHOT`, `api-version: 1.21`).
- **Java 21**.

### Installation
1. Download `RedstoneStateSaver-1.0.0.jar` from [Releases](../../releases).
2. Put it in the server's `plugins/` folder and restart.

### Commands
Single command `/rss` (no aliases; permission `rss.use`, default everyone).

| Command | Description | Permission |
|---|---|---|
| `/rss` | Help | `rss.use` |
| `/rss reload` | Reload `config.yml` and `states.yml` | `rss.admin` |
| `/rss save` | Save all states to disk | `rss.admin` |
| `/rss izin <x> <y> <z>` | Allow the chunk at that coordinate plus `allow-radius-default`. Players only | `rss.bypass` |
| `/rss izinchunk <chunkX> <chunkZ> <radius>` | Allow chunks in radius and save immediately (console uses the first world) | `rss.admin` or `rss.bypass` |
| `/rss izinbolge <chunkX> <chunkZ> <radius>` | Alias of `izinchunk` | `rss.admin` or `rss.bypass` |

### Permissions
`rss.use` (everyone), `rss.admin` (op), `rss.bypass` (op), `rss.notify` (op).

### Configuration & data
See the Turkish section for the full `config.yml`. `states.yml` stores `blocks.<world:x:y:z>` (`world`, `x`, `y`, `z`, `material`, `blockData`, optional `props`) and `allowedChunks` (`world:chunkX:chunkZ`); the previous file is kept as `states.yml.bak`. Messages are in Turkish.

### Building
```bash
mvn package   # -> target/RedstoneStateSaver-1.0.0.jar
```

### Known issues (from the code)
- The periodic save runs asynchronously and reads world blocks, which is not thread-safe in Bukkit.
- `states.yml` is loaded twice on startup.
- Blocks in allowed chunks are still saved (the allow check happens after saving).
- `/rss izin` only adds a chunk allowance and does not save immediately; `y` is unused.
- Records of removed blocks are never deleted; `/rss reload` does not clear in-memory records.
- The teleport button runs `/tp`, which requires `/tp` permission.
- No tab completion.

### Project dates
- **Created:** August 18, 2025
- **Last updated:** August 18, 2025

### License
No license specified.

*Developer: CaYaDev*
