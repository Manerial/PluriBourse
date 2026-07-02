package org.pluribourse.edition.controller;

import lombok.*;
import org.pluribourse.edition.dto.*;
import org.pluribourse.edition.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/editions")
@RequiredArgsConstructor
public class CurrentEditionController {

    private final EditionService editionService;

    @GetMapping("/current")
    public ResponseEntity<EditionDto> getCurrentEdition() {
        return ResponseEntity.ok(editionService.getActiveEditionDto());
    }
}
