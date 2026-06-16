# Claude Ekibi — Kurulum ve Kullanım

Spring Boot + React (web) + React Native/Expo (mobil) projeleri için, tek başına çalışan geliştiriciye "ekip" sağlayan skill + agent seti. Hepsi düz markdown; gizli script, hook veya npm kurulumu yoktur. Her satırını okuyabilir, güvenle kullanabilirsin.

## İçindekiler
```
.claude/
  skills/                      # konvansiyonlar — Claude'a otomatik bağlam olur
    project-architecture/SKILL.md   # yığın + klasör düzeni + temel kurallar (temel)
    api-contract/SKILL.md           # backend/web/mobil ortak API sözleşmesi
    spring-boot-backend/SKILL.md    # backend katman ve desenleri
    react-web/SKILL.md              # web frontend desenleri
    react-native-mobile/SKILL.md    # mobil desenler + web ile paylaşım
    testing-standards/SKILL.md      # test yaklaşımı
  agents/                      # delege edebileceğin uzmanlar
    feature-builder.md              # uçtan uca özellik kurar (BE→web→mobil)  [ana otomasyon]
    code-reviewer.md                # commit öncesi salt-okunur inceleme
    test-author.md                  # değişen kod için test yazar
    db-migrator.md                  # güvenli şema/Flyway migration
```

## Kurulum
Bu `.claude/` klasörünü projenin köküne kopyala. Claude Code'u o klasörde başlat (veya zaten açıksa oturumu yeniden başlat — agentlar oturum başında yüklenir).

- **Proje düzeyi (önerilen):** `<proje>/.claude/` → sadece o projede geçerli.
- **Kullanıcı düzeyi (tüm projeler):** `~/.claude/` → her projede kullanılabilir. Stack'in hep aynı olduğu için bu da mantıklı.

Yüklendiğini doğrulama: Claude Code içinde `/agents` yaz → dört agent listede görünmeli.

## Nasıl çalışır
- **Skiller** pasiftir: Claude işin bağlamına göre ilgili skill'i kendiliğinden devreye alır (ör. backend dosyası düzenlerken `spring-boot-backend`). Sen ekstra bir şey yapmazsın.
- **Agentlar** aktiftir: ya Claude uygun görevde otomatik delege eder, ya da sen açıkça çağırırsın.

## Tipik akış (örnek: "yoklama" özelliği)
1. `feature-builder` agent'ına özelliği anlat → BE migration + entity/servis/controller, web ekran, mobil ekran ve testleri kurar; belirsiz iş kurallarını sana sorar.
2. `test-author` ile testleri tamamlat/genişlet.
3. `code-reviewer` ile commit öncesi incele.
4. Şema değişikliği gerekiyorsa `db-migrator` güvenli migration'ı yazar.

Açıkça çağırma örnekleri:
- "feature-builder agent'ını kullanarak 'ürün satışı' özelliğini kur."
- "code-reviewer agent'ı ile son değişiklikleri incele."
- "db-migrator ile attendance tablosunu ekle."

## Uyarlama
- Yığın varsayımları: Java 21 / Spring Boot 3 / Maven / PostgreSQL / Flyway · React+Vite+TS / TanStack Query · Expo. Farklıysa (Gradle, Redux, Flutter...) önce `project-architecture` skill'ini güncelle; diğerleri ona bakar.
- Yeni bir rutin işin varsa (ör. "her özellikte API dokümantasyonu üret") yeni bir skill/agent ekleyebiliriz — söyle, beraber yazarız.

## Güvenlik notu
Bu set bilerek "sade"dir: hiçbir hook, otomatik komut veya dış kurulum içermez. Agentların tool erişimi dar tutuldu (ör. `code-reviewer` ve `db-migrator` gerçek veritabanına dokunamaz, yıkıcı işlemde onay ister). Üçüncü parti bir paket eklemeden önce hep aynı disiplini uygula: ne çalıştırdığını oku, sırları koddan uzak tut.
