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
        List<EditionDto> allEditions = service.getAllEditions();
        return ResponseEntity.ok(allEditions);
    }

    @PostMapping
    public ResponseEntity<EditionDto> createEdition(@Valid @RequestBody EditionDto dto) {
        EditionDto edition = service.createEdition(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(edition);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EditionDto> getEditionById(@PathVariable Long id) {
        EditionDto edition = service.getEditionById(id);
        return ResponseEntity.ok(edition);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EditionDto> updateEdition(
            @PathVariable Long id,
            @Valid @RequestBody EditionDto dto
    ) {
        EditionDto updatedEdition = service.updateEdition(id, dto);
        return ResponseEntity.ok(updatedEdition);
    }

    @PostMapping("/{id}/phase/advance")
    public ResponseEntity<EditionDto> advancePhase(@PathVariable Long id) {
        EditionDto editionAdvanced = service.advancePhase(id);
        return ResponseEntity.ok(editionAdvanced);
    }

    @PostMapping("/{id}/phase/rollback")
    public ResponseEntity<EditionDto> rollbackPhase(@PathVariable Long id) {
        EditionDto editionRolledback = service.rollbackPhase(id);
        return ResponseEntity.ok(editionRolledback);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEdition(@PathVariable Long id) {
        service.deleteEdition(id);
        return ResponseEntity.noContent().build();
    }
}
