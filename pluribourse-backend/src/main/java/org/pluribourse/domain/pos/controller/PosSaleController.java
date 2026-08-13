package org.pluribourse.domain.pos.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.pos.service.PosInvoicePrintService;
import org.pluribourse.domain.user.entities.PluriBourseUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Story 4.5 — no {@code @PreAuthorize}: inherits the global authenticated-and-not-SELLER rule from
 * {@code SecurityConfig}, same as {@link PosBasketController}.
 */
@RestController
@RequestMapping("/pos/sales")
@RequiredArgsConstructor
public class PosSaleController {

    private final PosInvoicePrintService service;

    @PostMapping("/{saleId}/invoice/print")
    public ResponseEntity<Void> printInvoice(@PathVariable Long saleId, HttpSession session, Authentication authentication) {
        service.printInvoice(saleId, userId(authentication), session);
        return ResponseEntity.noContent().build();
    }

    private Long userId(Authentication authentication) {
        return ((PluriBourseUserDetails) authentication.getPrincipal()).getUserId();
    }
}
