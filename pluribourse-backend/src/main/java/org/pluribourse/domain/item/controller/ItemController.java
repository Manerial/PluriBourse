package org.pluribourse.domain.item.controller;

import jakarta.validation.*;
import lombok.*;
import org.pluribourse.domain.item.dto.*;
import org.pluribourse.domain.item.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService service;

    @GetMapping
    public ResponseEntity<List<ItemDto>> getBySeller(@RequestParam Long sellerProfileId) {
        return ResponseEntity.ok(service.getBySellerProfile(sellerProfileId));
    }

    @PostMapping
    public ResponseEntity<ItemDto> create(@Valid @RequestBody CreateItemDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ItemDto> update(@PathVariable Long id, @Valid @RequestBody CreateItemDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ItemDto> updateCompleteness(@PathVariable Long id, @Valid @RequestBody ItemCompletenessDto dto) {
        return ResponseEntity.ok(service.updateCompleteness(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
