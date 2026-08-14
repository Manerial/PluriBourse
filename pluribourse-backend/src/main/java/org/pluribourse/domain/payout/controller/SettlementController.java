package org.pluribourse.domain.payout.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.payout.dto.SettleDto;
import org.pluribourse.domain.payout.dto.SettlementDto;
import org.pluribourse.domain.payout.service.SettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService service;

    @GetMapping
    public ResponseEntity<List<SettlementDto>> getSettlements() {
        return ResponseEntity.ok(service.getSettlements());
    }

    @PostMapping("/{sellerId}/settle")
    public ResponseEntity<SettlementDto> settle(@PathVariable Long sellerId, @Valid @RequestBody SettleDto dto) {
        return ResponseEntity.ok(service.settle(sellerId, dto));
    }

    @PostMapping("/{sellerId}/unclaimed")
    public ResponseEntity<SettlementDto> markUnclaimed(@PathVariable Long sellerId) {
        return ResponseEntity.ok(service.markUnclaimed(sellerId));
    }
}
