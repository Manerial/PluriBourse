package org.pluribourse.domain.edition.controller;

import lombok.*;
import org.pluribourse.domain.edition.dto.*;
import org.pluribourse.domain.edition.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/editions")
@RequiredArgsConstructor
public class CurrentEditionController {

    private final EditionService editionService;

    @GetMapping("/current")
    public ResponseEntity<EditionDto> getCurrentEdition() {
        EditionDto activeEditionDto = editionService.getActiveEditionDto();
        return ResponseEntity.ok(activeEditionDto);
    }
}
