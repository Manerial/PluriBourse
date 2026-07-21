package org.pluribourse.domain.seller.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.item.service.DepositValidationService;
import org.pluribourse.domain.seller.dto.SellerDto;
import org.pluribourse.domain.seller.service.SellerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sellers")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService service;
    private final DepositValidationService depositValidationService;

    @GetMapping("/search")
    public ResponseEntity<List<SellerDto>> search(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(service.search(query));
    }

    @PostMapping
    public ResponseEntity<SellerDto> create(@Valid @RequestBody SellerDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PostMapping("/{id}/deposit/validate")
    public ResponseEntity<Void> validateDeposit(@PathVariable Long id, HttpSession session) {
        depositValidationService.validateDeposit(id, session);
        return ResponseEntity.noContent().build();
    }
}
