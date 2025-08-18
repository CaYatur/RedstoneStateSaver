# RedstoneStateSaver

Türkçe: Redstone sistemlerinin durumunu sunucu kapatılmadan kaydeder ve sunucu açıldığında geri yükler. Koruma ve uyarı sistemi içerir.

Geliştirici: CaYaDev

Kurulum:
1. Projeyi Maven ile derleyin (Java 17).
2. Oluşan jar dosyasını sunucunuzun `plugins` klasörüne atın.
3. Sunucuyu yeniden başlatın.

Komutlar:
- /rss - Yardım
- /rss reload - Yeniden yükle (op veya uygun izin)
- /rss save - Kayıtları diske kaydet
- /rss izin <x> <y> <z> - Belirli koordinata izin ver (op veya rss.bypass yetkisi)

Notlar:
- Bu ilk sürüm temel işlevleri sağlar. Daha karmaşık blok veri tipleri veya özel ihtiyaçlar için genişletme gerekebilir.
