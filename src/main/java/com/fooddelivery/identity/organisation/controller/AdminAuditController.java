package com.fooddelivery.identity.organisation.controller;

import com.fooddelivery.common.audit.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Slice;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/internal/admin/audit-events") @PreAuthorize("hasRole('ADMIN')") @lombok.RequiredArgsConstructor
public class AdminAuditController {
    private final AuditReader reader;
    @GetMapping public Slice<AuditEvent> history(@RequestParam String subjectType,@RequestParam UUID subjectId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return reader.history(subjectType,subjectId,page,size);}
}
