package org.pluribourse.domain.archive.controller;

import jakarta.validation.constraints.*;
import lombok.*;
import org.pluribourse.domain.archive.dto.*;
import org.pluribourse.domain.archive.service.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.*;
import org.springframework.validation.annotation.*;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/admin/archive/editions/{editionId}/items")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ArchivedItemController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ArchivedItemService service;

    @GetMapping
    public ResponseEntity<ArchivedItemPageDto> getArchivedCatalog(
            @PathVariable Long editionId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) Boolean sold,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(required = false) String sort) {
        ArchivedItemFilterDto filter = new ArchivedItemFilterDto(name, categoryName, sold, page, size, sort);
        return ResponseEntity.ok(service.getArchivedCatalog(editionId, filter));
    }
}
