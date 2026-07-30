package org.pluribourse.domain.pos.controller;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.pos.dto.ScanResultDto;
import org.pluribourse.domain.pos.service.PosScanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pos")
@RequiredArgsConstructor
public class PosController {

    private final PosScanService service;

    @GetMapping("/scan")
    public ResponseEntity<ScanResultDto> scan(@RequestParam String barcode) {
        return ResponseEntity.ok(service.scan(barcode));
    }
}
