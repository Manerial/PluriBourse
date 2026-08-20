package org.pluribourse.domain.edition.controller;

import jakarta.validation.*;
import lombok.*;
import org.pluribourse.domain.archive.service.*;
import org.pluribourse.domain.edition.dto.*;
import org.pluribourse.domain.edition.service.*;
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
    private final EditionClosingService closingService;
    private final EditionArchivingService archivingService;

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

    @PostMapping("/{id}/close")
    public ResponseEntity<EditionDto> closeEdition(@PathVariable Long id) {
        EditionDto closedEdition = closingService.closeEdition(id);
        return ResponseEntity.ok(closedEdition);
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<EditionDto> archiveEdition(@PathVariable Long id) {
        EditionDto archivedEdition = archivingService.archiveEdition(id);
        return ResponseEntity.ok(archivedEdition);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEdition(@PathVariable Long id) {
        service.deleteEdition(id);
        return ResponseEntity.noContent().build();
    }
}
