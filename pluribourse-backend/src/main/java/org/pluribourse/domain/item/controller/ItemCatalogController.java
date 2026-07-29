package org.pluribourse.domain.item.controller;

import jakarta.validation.constraints.*;
import lombok.*;
import org.pluribourse.domain.item.dto.*;
import org.pluribourse.domain.item.service.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.*;
import org.springframework.web.bind.annotation.*;

/**
 * Not under {@code /admin/**}: both ADMIN and VOLUNTEER must reach the catalog, and
 * {@code SecurityConfig}'s {@code anyRequest()} rule (authenticated + non-SELLER) already
 * covers both roles — same precedent as {@code CurrentEditionCategoryController}.
 */
@Validated
@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
public class ItemCatalogController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ItemCatalogService service;

    @GetMapping
    public ResponseEntity<ItemCatalogPageDto> getCatalog(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String barcode,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer tableNumber,
            @RequestParam(required = false) Boolean sold,
            @RequestParam(required = false) Boolean incomplete,
            @RequestParam(required = false) String sellerName,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(required = false) String sort) {
        ItemCatalogFilterDto filter = new ItemCatalogFilterDto(name, barcode, categoryId, tableNumber, sold, incomplete, sellerName, page, size, sort);
        return ResponseEntity.ok(service.getCatalog(filter));
    }
}
