package org.pluribourse.domain.edition.controller;

import jakarta.validation.*;
import lombok.*;
import org.pluribourse.domain.edition.dto.*;
import org.pluribourse.domain.edition.service.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin/editions/{editionId}/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EditionCategoryController {

    private final EditionCategoryService service;

    @GetMapping
    public ResponseEntity<List<EditionCategoryDto>> getCategories(@PathVariable Long editionId) {
        List<EditionCategoryDto> categories = service.getCategories(editionId);
        return ResponseEntity.ok(categories);
    }

    @PutMapping
    public ResponseEntity<List<EditionCategoryDto>> saveCategories(
            @PathVariable Long editionId,
            @Valid @RequestBody List<@Valid EditionCategoryDto> dtos
    ) {
        List<EditionCategoryDto> categories = service.saveCategories(editionId, dtos);
        return ResponseEntity.ok(categories);
    }

    @PostMapping("/copy-from/{sourceEditionId}")
    public ResponseEntity<List<EditionCategoryDto>> copyFromEdition(
            @PathVariable Long editionId,
            @PathVariable Long sourceEditionId) {
        List<EditionCategoryDto> copiedFromEdition = service.copyFromEdition(editionId, sourceEditionId);
        return ResponseEntity.ok(copiedFromEdition);
    }
}
