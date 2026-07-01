package org.pluribourse.edition.controller;

import lombok.RequiredArgsConstructor;
import org.pluribourse.edition.dto.EditionDto;
import org.pluribourse.edition.service.EditionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/editions")
@RequiredArgsConstructor
public class CurrentEditionController {

    private final EditionService editionService;

    @GetMapping("/current")
    public ResponseEntity<EditionDto> getCurrentEdition() {
        return editionService.getCurrentEdition()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
