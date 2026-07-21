package org.pluribourse.domain.seller.controller;

import com.jPageFlow.utils.FilterDto;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.seller.dto.SellerDto;
import org.pluribourse.domain.seller.service.SellerService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/sellers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSellerController {

    private final SellerService service;

    @GetMapping
    public ResponseEntity<Page<SellerDto>> getSellers(FilterDto filterDto) {
        return ResponseEntity.ok(service.getSellers(filterDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSeller(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
