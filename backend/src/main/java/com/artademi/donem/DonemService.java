package com.artademi.donem;

import com.artademi.common.exception.NotFoundException;
import com.artademi.common.exception.ValidationException;
import com.artademi.donem.dto.DonemRequest;
import com.artademi.donem.dto.DonemResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DonemService {

    private final DonemRepository repository;

    public DonemService(DonemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DonemResponse> list(Boolean aktif) {
        List<Donem> liste = aktif == null ? repository.findAllSirali() : repository.findByAktif(aktif);
        return liste.stream().map(DonemResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DonemResponse get(Long id) {
        return DonemResponse.from(findOrThrow(id));
    }

    @Transactional
    public DonemResponse create(DonemRequest req) {
        dogrula(req);
        Donem d = Donem.create();
        uygula(d, req);
        return DonemResponse.from(repository.save(d));
    }

    @Transactional
    public DonemResponse update(Long id, DonemRequest req) {
        dogrula(req);
        Donem d = findOrThrow(id);
        uygula(d, req);
        return DonemResponse.from(d);
    }

    @Transactional
    public DonemResponse changeActive(Long id, boolean aktif) {
        Donem d = findOrThrow(id);
        d.setAktif(aktif);
        return DonemResponse.from(d);
    }

    public Donem findOrThrow(Long id) {
        return repository.findScopedById(id).orElseThrow(() -> new NotFoundException("Dönem bulunamadı: " + id));
    }

    private static void dogrula(DonemRequest req) {
        if (!req.bitis().isAfter(req.baslangic())) {
            throw new ValidationException("Bitiş başlangıçtan sonra olmalıdır");
        }
    }

    private static void uygula(Donem d, DonemRequest req) {
        d.setAd(req.ad().trim());
        d.setBaslangic(req.baslangic());
        d.setBitis(req.bitis());
    }
}
