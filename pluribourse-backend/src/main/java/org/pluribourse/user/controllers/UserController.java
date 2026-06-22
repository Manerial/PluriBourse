package org.pluribourse.user.controllers;

import jakarta.validation.*;
import lombok.*;
import org.pluribourse.user.dtos.*;
import org.pluribourse.user.services.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.*;

import java.net.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserDto>> listVolunteers() {
        return ResponseEntity.ok(userService.listVolunteers());
    }

    @PostMapping
    public ResponseEntity<UserDto> createVolunteer(@Valid @RequestBody CreateUserDto dto) {
        UserDto created = userService.createVolunteer(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordDto dto) {
        userService.resetVolunteerPassword(id, dto.newPassword());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        userService.disableVolunteer(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/enable")
    public ResponseEntity<Void> enable(@PathVariable Long id) {
        userService.enableVolunteer(id);
        return ResponseEntity.ok().build();
    }
}
