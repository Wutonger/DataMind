package com.datamine.analysis.web.controller;

import com.datamine.analysis.common.dto.PageResponseDTO;
import com.datamine.analysis.common.dto.auth.UserConnectionAccessDTO;
import com.datamine.analysis.common.dto.auth.UserProfileDTO;
import com.datamine.analysis.common.dto.auth.UserStatusUpdateRequest;
import com.datamine.analysis.common.dto.auth.UserUpsertRequest;
import com.datamine.analysis.core.service.ConnectionAccessService;
import com.datamine.analysis.core.service.CurrentUserService;
import com.datamine.analysis.core.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAdminService userAdminService;
    private final ConnectionAccessService connectionAccessService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<PageResponseDTO<UserProfileDTO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status
    ) {
        currentUserService.checkAdmin();
        return ResponseEntity.ok(userAdminService.listUsers(page, pageSize, username, status));
    }

    @PostMapping
    public ResponseEntity<UserProfileDTO> createUser(@RequestBody UserUpsertRequest request) {
        currentUserService.checkAdmin();
        return ResponseEntity.ok(userAdminService.createUser(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserProfileDTO> updateUser(@PathVariable Long id, @RequestBody UserUpsertRequest request) {
        currentUserService.checkAdmin();
        return ResponseEntity.ok(userAdminService.updateUser(id, request));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<UserProfileDTO> updateStatus(@PathVariable Long id,
                                                       @RequestBody UserStatusUpdateRequest request) {
        currentUserService.checkAdmin();
        return ResponseEntity.ok(userAdminService.updateStatus(id, request));
    }

    @GetMapping("/{id}/connections")
    public ResponseEntity<UserConnectionAccessDTO> getAuthorizedConnections(@PathVariable Long id) {
        currentUserService.checkAdmin();
        return ResponseEntity.ok(new UserConnectionAccessDTO(id, connectionAccessService.getAuthorizedConnectionIds(id)));
    }

    @PutMapping("/{id}/connections")
    public ResponseEntity<UserConnectionAccessDTO> updateAuthorizedConnections(@PathVariable Long id,
                                                                               @RequestBody List<Long> connectionIds) {
        currentUserService.checkAdmin();
        Long operatorId = currentUserService.getRequiredUserId();
        return ResponseEntity.ok(connectionAccessService.updateAuthorizedConnections(id, connectionIds, operatorId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        currentUserService.checkAdmin();
        userAdminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
