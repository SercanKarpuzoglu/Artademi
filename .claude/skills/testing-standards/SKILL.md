---
name: testing-standards
description: How we test across the stack — JUnit 5 + Mockito + Testcontainers for Spring Boot, Vitest + React Testing Library for the web, and what is worth testing. Consult this when writing or updating tests, or when finishing a feature.
---

# Test Standartları

## Backend (Spring Boot)
- **Birim testi:** Servis mantığı JUnit 5 + Mockito ile. Repository mock'lanır; iş kuralı, sınır durumlar ve hata yolları test edilir.
- **Entegrasyon testi:** Controller + repository, Testcontainers ile gerçek PostgreSQL üstünde. H2 yerine Testcontainers (davranış üretimle aynı olsun).
- İsimlendirme: `should<Beklenen>_when<Durum>` (ör. `shouldReturn404_whenStudentMissing`).
- Her yeni servis metodu için en az: mutlu yol + bir hata yolu.

## Web (React)
- Vitest + React Testing Library. Kullanıcı davranışını test et, iç uygulamayı değil ("ekranda 'Kaydedildi' görünür", "buton tıklanınca mutation çağrılır").
- API çağrıları mock'lanır (msw veya basit mock). Query'li bileşenler `QueryClientProvider` ile sarılır.
- Form doğrulama: geçersiz girdide hata mesajı görünür, submit engellenir.

## Neyi test ederiz, neyi etmeyiz
- Test edilir: iş kuralı, doğrulama, hata yolları, sözleşmeye uyum (zarf/şekil), kritik kullanıcı akışları.
- Edilmez: framework'ün kendisi, basit getter/setter, üçüncü parti kütüphane içi.

## Tamamlanma ölçütü
- Bir özellik "bitti" sayılmaz: backend servis + (varsa) entegrasyon testi ve web kritik akış testi geçmeden.
- Testler yeşil olmadan commit önerilmez (bkz. `code-reviewer`).
