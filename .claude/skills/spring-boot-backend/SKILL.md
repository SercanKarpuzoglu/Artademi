---
name: spring-boot-backend
description: Backend patterns for Spring Boot services — entity, repository, service, controller, DTO mapping, validation, exception handling, and Flyway migrations. Consult this when adding or changing any backend code in a Spring Boot project.
---

# Spring Boot Backend Konvansiyonları

## Katmanlar ve sorumluluklar
- **Controller** — ince. HTTP'yi alır, DTO doğrular, servisi çağırır, `ApiResponse` döndürür. İş kuralı YOK.
- **Service** — tüm iş kuralı burada. `@Transactional` burada. Entity ⇄ DTO dönüşümünü yönetir (mapper).
- **Repository** — `JpaRepository` arayüzü. Sadece veri erişimi. Sorgu mantığı method isimleri veya `@Query` ile.
- **Entity** — JPA `@Entity`. Dışarıya asla doğrudan dönülmez; DTO'ya çevrilir.
- **DTO** — `dto/` altında. İstek DTO'su `@Valid` ile doğrulanır; yanıt DTO'su entity'den üretilir.

## Bir özellik eklerken üretilen iskelet (örnek: Student)
```
student/
  Student.java               # @Entity
  StudentRepository.java     # interface JpaRepository<Student, Long>
  StudentService.java        # @Service, iş kuralı + @Transactional
  StudentController.java     # @RestController @RequestMapping("/api/students")
  dto/CreateStudentRequest.java   # record + bean validation
  dto/StudentResponse.java        # record
  dto/StudentMapper.java          # entity <-> dto (statik metotlar veya MapStruct)
```

## Doğrulama
- İstek DTO'larında Bean Validation (`@NotBlank`, `@Email`, `@Positive`...).
- Controller parametresi `@Valid`. Doğrulama hatası `GlobalExceptionHandler`'da `400` + `VALIDATION_ERROR`'a çevrilir.

## Hata yönetimi (common/)
- Tek bir `@RestControllerAdvice GlobalExceptionHandler`.
- Özel istisnalar: `NotFoundException` → 404, `ConflictException` → 409, `MethodArgumentNotValidException` → 400.
- Hiçbir yerde ham stacktrace dışarı sızmaz; `INTERNAL` koduna düşer ve loglanır.

## ApiResponse (common/)
- Tüm controller dönüşleri `ApiResponse<T>` sarmalı (bkz. `api-contract`).
- Liste dönüşlerinde `PageMeta` doldurulur.

## Flyway migration
- Şema değişikliği = yeni migration dosyası. Mevcut migration ASLA düzenlenmez.
- İsim: `V<sıra>__<açıklama>.sql` (ör. `V3__add_attendance_table.sql`).
- `hibernate.ddl-auto=validate` (asla `update`/`create` değil). Şemayı migration yönetir.
- Geri alınamaz/yıkıcı değişikliklerde (kolon silme) önce uyar.

## Güvenlik
- Endpoint'ler varsayılan kapalı; açıkça izin verilenler dışında kimlik ister.
- Parola `BCrypt` ile hash'lenir. Sır/anahtar koda girmez, ortam değişkeninden okunur.
- Kullanıcı girdisi sorguya doğrudan string olarak gömülmez (JPA parametreleri / named query).

## Test
- Servis birim testi (Mockito ile repository mock'lanır).
- Controller/entegrasyon testi Testcontainers (gerçek PostgreSQL) ile. Bkz. `testing-standards`.
