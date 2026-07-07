package org.pluribourse.item.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pluribourse.item.dto.CreateLotDto;
import org.pluribourse.item.dto.LotDto;
import org.pluribourse.item.service.LotService;
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
