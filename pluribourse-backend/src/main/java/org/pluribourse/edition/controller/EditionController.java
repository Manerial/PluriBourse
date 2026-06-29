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

    @PatchMapping("/{id}/commission-rate")
    public ResponseEntity<EditionDto> updateCommissionRate(
            @PathVariable Long id,
            @Valid @RequestBody CommissionRateUpdateDto dto) {
        return ResponseEntity.ok(service.updateCommissionRate(id, dto.commissionRate()));
    }

    @PostMapping("/{id}/phase/advance")
    public ResponseEntity<EditionDto> advancePhase(@PathVariable Long id) {
        return ResponseEntity.ok(service.advancePhase(id));
    }

    @PostMapping("/{id}/phase/rollback")
    public ResponseEntity<EditionDto> rollbackPhase(@PathVariable Long id) {
        return ResponseEntity.ok(service.rollbackPhase(id));
    }
}
