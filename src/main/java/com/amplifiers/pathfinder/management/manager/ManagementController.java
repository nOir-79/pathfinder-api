package com.amplifiers.pathfinder.management.manager;


import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform-management")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
@RequiredArgsConstructor
public class ManagementController {
    private final ManagementService managementService;

    @PutMapping("/accept-gig/{gigId}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<?> acceptGig(
            @PathVariable Integer gigId
    ) {
        return ResponseEntity.ok(managementService.acceptGig(gigId));
    }
}