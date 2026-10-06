package com.settribe.leave.controller;

import com.settribe.leave.dto.LeaveRequestDto;
import com.settribe.leave.dto.LeaveResponse;
import com.settribe.leave.entity.LeaveStatus;
import com.settribe.leave.entity.LeaveType;
import com.settribe.leave.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @GetMapping
    public List<LeaveResponse> list(Authentication auth,
                                    @RequestParam(required = false) LeaveStatus status,
                                    @RequestParam(required = false) LeaveType type) {
        return leaveService.listMine(auth.getName(), status, type);
    }

    @PostMapping
    public ResponseEntity<LeaveResponse> create(Authentication auth,
                                                @Valid @RequestBody LeaveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveService.create(auth.getName(), dto));
    }

    @PutMapping("/{id}")
    public LeaveResponse update(Authentication auth, @PathVariable Long id,
                                @Valid @RequestBody LeaveRequestDto dto) {
        return leaveService.update(auth.getName(), id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        leaveService.delete(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
