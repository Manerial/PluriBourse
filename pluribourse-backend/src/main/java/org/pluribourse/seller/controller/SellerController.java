package org.pluribourse.seller.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pluribourse.seller.dto.SellerDto;
import org.pluribourse.seller.service.SellerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sellers")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService service;

    @GetMapping("/search")
    public ResponseEntity<List<SellerDto>> search(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(service.search(query));
    }

    @PostMapping
    public ResponseEntity<SellerDto> create(@Valid @RequestBody SellerDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }
}
