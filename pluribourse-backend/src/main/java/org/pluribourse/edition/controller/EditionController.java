package org.pluribourse.edition.controller;

import jakarta.validation.*;
import lombok.*;
import org.pluribourse.edition.dto.*;
import org.pluribourse.edition.service.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin/editions")
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

    @PostMapping("/{id}/phase/advance")
    public ResponseEntity<EditionDto> advancePhase(@PathVariable Long id) {
        return ResponseEntity.ok(service.advancePhase(id));
    }

    @PostMapping("/{id}/phase/rollback")
    public ResponseEntity<EditionDto> rollbackPhase(@PathVariable Long id) {
        return ResponseEntity.ok(service.rollbackPhase(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEdition(@PathVariable Long id) {
        service.deleteEdition(id);
        return ResponseEntity.noContent().build();
    }
}
