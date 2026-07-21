package org.pluribourse.domain.instanceconfig.controller;

import jakarta.validation.*;
import lombok.*;
import org.pluribourse.domain.instanceconfig.dto.*;
import org.pluribourse.domain.instanceconfig.service.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/instance-config")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class GlobalInstanceConfigController {

    private final GlobalInstanceConfigService service;

    @GetMapping
    public ResponseEntity<GlobalInstanceConfigDto> getConfig() {
        return ResponseEntity.ok(service.getConfig());
    }

    @PutMapping
    public ResponseEntity<GlobalInstanceConfigDto> updateConfig(
            @Valid @RequestBody GlobalInstanceConfigDto dto) {
        return ResponseEntity.ok(service.updateConfig(dto));
    }
}
