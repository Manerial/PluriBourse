package org.pluribourse.domain.pos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.pos.dto.BasketDto;
import org.pluribourse.domain.pos.dto.SaleDto;
import org.pluribourse.domain.pos.dto.ValidateBasketDto;
import org.pluribourse.domain.pos.service.PosBasketService;
import org.pluribourse.domain.user.entities.PluriBourseUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Story 4.2 — persisted POS basket. No {@code @PreAuthorize}: inherits the global
 * authenticated-and-not-SELLER rule from {@code SecurityConfig}, same as {@link PosController}.
 */
@RestController
@RequestMapping("/pos/baskets")
@RequiredArgsConstructor
public class PosBasketController {

    private final PosBasketService service;

    @GetMapping("/current")
    public ResponseEntity<BasketDto> getCurrentBasket(Authentication authentication) {
        return ResponseEntity.ok(service.getOrCreateCurrentBasket(userId(authentication)));
    }

    @PostMapping("/{basketId}/items")
    public ResponseEntity<BasketDto> addItem(
            @PathVariable Long basketId, @RequestParam String barcode, Authentication authentication) {
        return ResponseEntity.ok(service.addItem(basketId, barcode, userId(authentication)));
    }

    @DeleteMapping("/{basketId}/items/{itemId}")
    public ResponseEntity<BasketDto> removeItem(
            @PathVariable Long basketId, @PathVariable Long itemId, Authentication authentication) {
        return ResponseEntity.ok(service.removeItem(basketId, itemId, userId(authentication)));
    }

    @DeleteMapping("/{basketId}/lots/{lotId}")
    public ResponseEntity<BasketDto> removeLot(
            @PathVariable Long basketId, @PathVariable Long lotId, Authentication authentication) {
        return ResponseEntity.ok(service.removeLot(basketId, lotId, userId(authentication)));
    }

    @PostMapping("/{basketId}/validate")
    public ResponseEntity<SaleDto> validate(
            @PathVariable Long basketId, @Valid @RequestBody ValidateBasketDto dto, Authentication authentication) {
        return ResponseEntity.ok(service.validate(basketId, dto, userId(authentication)));
    }

    /**
     * FR-110 / FR-066 (SCP 2026-09-04) — the POS page pings this while it is open so the server can
     * tell a live terminal from an abandoned one. No body, 204 on success; 404 (basket-not-found)
     * if the basket is unknown or not the caller's, 422 (sale-phase-required) outside the Sale phase.
     */
    @PostMapping("/{basketId}/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable Long basketId, Authentication authentication) {
        service.recordHeartbeat(basketId, userId(authentication));
        return ResponseEntity.noContent().build();
    }

    private Long userId(Authentication authentication) {
        return ((PluriBourseUserDetails) authentication.getPrincipal()).getUserId();
    }
}
