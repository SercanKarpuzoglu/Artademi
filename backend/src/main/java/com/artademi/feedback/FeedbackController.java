package com.artademi.feedback;

import com.artademi.common.ApiResponse;
import com.artademi.feedback.dto.FeedbackRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Geri bildirim ucu — {@code POST /api/feedback} (giris yapmis HER rol).
 *
 * <p>Rol kisiti YOK: ogretmen de on buro da sorun bildirebilmeli. Kimlik oturumdan alinir.
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<Void> gonder(@Valid @RequestBody FeedbackRequest request) {
        service.gonder(request);
        return ApiResponse.ok(null);
    }
}
