package org.pluribourse.domain.pos.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.pos.dto.SaleListFilterDto;
import org.pluribourse.domain.pos.dto.SaleListPageDto;
import org.pluribourse.domain.pos.service.PosInvoicePrintService;
import org.pluribourse.domain.pos.service.SaleListService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Story 4.5 — no {@code @PreAuthorize}: inherits the global authenticated-and-not-SELLER rule from
 * {@code SecurityConfig}, same as {@link PosBasketController}. Both ADMIN and VOLUNTEER must reach
 * {@code /pos/sales} (story 4.7, sales list screen).
 * <p>
 * {@code @Validated} on the class (not the methods) is what turns the {@code @Min}/{@code @Max}
 * bounds on the list query params into a {@code ConstraintViolationException} → 422, same placement
 * as {@code ItemCatalogController}.
 */
@Validated
@RestController
@RequestMapping("/pos/sales")
@RequiredArgsConstructor
public class PosSaleController {

    private static final int MAX_PAGE_SIZE = 200;

    private final PosInvoicePrintService service;
    private final SaleListService saleListService;

    @PostMapping("/{saleId}/invoice/print")
    public ResponseEntity<Void> printInvoice(@PathVariable Long saleId, HttpSession session) {
        service.printInvoice(saleId, session);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<SaleListPageDto> listSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) String cashier,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(saleListService.getSales(
                new SaleListFilterDto(dateFrom, dateTo, cashier, page, size, sort)));
    }

    @GetMapping("/cashiers")
    public ResponseEntity<List<String>> listCashiers() {
        return ResponseEntity.ok(saleListService.getCashiers());
    }
}
