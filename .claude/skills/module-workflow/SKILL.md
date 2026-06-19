---
name: module-workflow
description: The verification workflow Claude Code runs itself after building or changing a backend module — run ./mvnw test green, optionally validate live HTTP endpoints with a running backend + curl, then report changed files, test summary, curl summary and git status WITHOUT committing. Consult this whenever a backend module is created or modified.
---

# Modül Geliştirme İş Akışı (Katman 1: Claude Code doğrular)

Bir backend modülü kurarken veya değiştirirken, job tamamlandıktan sonra
ŞU DOĞRULAMA ADIMLARINI CLAUDE CODE KENDİSİ ÇALIŞTIRIR (kullanıcıya
"sen çalıştır" DEME):

1. ./mvnw test  → çalıştır. TÜM testlerin geçtiğini gör. Ham özeti
   ("Tests run: X, Failures: Y") rapora ekle. Kırmızıysa DUR, düzelt,
   tekrar koş; yeşil olmadan ilerleme.

2. Gerçek ortam doğrulaması (sadece yeni/değişen HTTP uçları varsa):
   a. Backend zaten çalışıyorsa kod değişikliği için YENİDEN BAŞLATILMASI
      gerektiğini unutma (çalışan süreç eski kodu tutar; "No static resource"
      hatası bunun işaretidir).
   b. Backend'i başlat (set -a && source .env && set +a && ./mvnw spring-boot:run),
      Started BackendApplication ve Flyway migration satırını gör.
   c. Token al (admin.a / Test1234!, realm Artademi) ve yeni uçları curl ile
      dene: mutlu yol + en az bir hata senaryosu (validasyon/yetki).
   d. curl çıktılarının ÖZETİNİ rapora ekle (success:true/false, status, kritik alanlar).
   e. Doğrulama bitince backend'i durdur (test ile çakışmasın).

3. RAPOR: değişen dosyalar + "Tests run" özeti + curl özeti + git status.
   COMMIT ETME, PUSH ETME — commit kararı kullanıcınındır. Kullanıcı raporu
   okuyup onaylayınca commit/push yapılır.

KURALLAR:
- Test yeşil olmadan "tamamlandı" deme.
- Doğrulama uydurma; komutları gerçekten çalıştır, ham çıktıdan özetle.
- Sır dosyaları (.env) commit kapsamına girmemeli; git status'te kontrol et.
- Bu akış multi-tenancy, testing-standards skilleriyle birlikte uygulanır.
