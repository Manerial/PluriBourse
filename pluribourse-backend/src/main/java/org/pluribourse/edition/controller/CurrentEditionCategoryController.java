package org.pluribourse.edition.controller;

import lombok.*;
import org.pluribourse.edition.dto.*;
import org.pluribourse.edition.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CurrentEditionCategoryController {

    private final EditionCategoryService categoryService;
    private final EditionService editionService;

    @GetMapping
    public ResponseEntity<List<EditionCategoryDto>> getCategoriesForActiveEdition() {
        Long activeEditionId = editionService.getActiveEdition().getId();
        List<EditionCategoryDto> categories = categoryService.getCategories(activeEditionId);
        return ResponseEntity.ok(categories);
    }
}
