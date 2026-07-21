package org.pluribourse.domain.item.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.item.dto.CreateLotDto;
import org.pluribourse.domain.item.dto.LotDto;
import org.pluribourse.domain.item.service.LotService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/lots")
@RequiredArgsConstructor
public class LotController {

    private final LotService service;

    @PostMapping
    public ResponseEntity<LotDto> create(@Valid @RequestBody CreateLotDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }
}
