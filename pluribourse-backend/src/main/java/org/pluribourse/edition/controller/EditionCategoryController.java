package org.pluribourse.edition.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pluribourse.edition.dto.EditionCategoryDto;
import org.pluribourse.edition.service.EditionCategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/editions/{editionId}/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EditionCategoryController {

    private final EditionCategoryService service;

    @GetMapping
    public ResponseEntity<List<EditionCategoryDto>> getCategories(@PathVariable Long editionId) {
        return ResponseEntity.ok(service.getCategories(editionId));
    }

    @PutMapping
    public ResponseEntity<List<EditionCategoryDto>> saveCategories(
            @PathVariable Long editionId,
            @Valid @RequestBody List<@Valid EditionCategoryDto> dtos) {
        return ResponseEntity.ok(service.saveCategories(editionId, dtos));
    }

    @PostMapping("/copy-from/{sourceEditionId}")
    public ResponseEntity<List<EditionCategoryDto>> copyFromEdition(
            @PathVariable Long editionId,
            @PathVariable Long sourceEditionId) {
        return ResponseEntity.ok(service.copyFromEdition(editionId, sourceEditionId));
    }
}
