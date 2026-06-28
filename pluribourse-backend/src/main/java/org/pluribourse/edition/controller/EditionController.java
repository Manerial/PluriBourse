package org.pluribourse.edition.controller;

import jakarta.validation.*;
import lombok.*;
import org.pluribourse.edition.dto.EditionDto;
import org.pluribourse.edition.service.EditionService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/editions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EditionController {

    private final EditionService service;

    @GetMapping
    public ResponseEntity<List<EditionDto>> getAllEditions() {
        return ResponseEntity.ok(service.getAllEditions());
    }

    @PostMapping
    public ResponseEntity<EditionDto> createEdition(@Valid @RequestBody EditionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createEdition(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EditionDto> getEditionById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getEditionById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EditionDto> updateEdition(
            @PathVariable Long id,
            @Valid @RequestBody EditionDto dto) {
        return ResponseEntity.ok(service.updateEdition(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEdition(@PathVariable Long id) {
        service.deleteEdition(id);
        return ResponseEntity.noContent().build();
    }
}
