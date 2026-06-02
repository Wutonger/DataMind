package com.datamine.analysis.web.controller;

import com.datamine.analysis.common.dto.auth.AuthLoginRequest;
import com.datamine.analysis.common.dto.auth.AuthLoginResponse;
import com.datamine.analysis.common.dto.auth.LastConnectionUpdateRequest;
import com.datamine.analysis.common.dto.auth.UserProfileDTO;
import com.datamine.analysis.core.service.AuthService;
import com.datamine.analysis.core.service.ConnectionAccessService;
import com.datamine.analysis.core.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;
    private final ConnectionAccessService connectionAccessService;

    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponse> login(@RequestBody AuthLoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authService.logout();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> me() {
        return ResponseEntity.ok(authService.currentUser());
    }

    @PutMapping("/me/last-connection")
    public ResponseEntity<UserProfileDTO> updateLastConnection(@RequestBody LastConnectionUpdateRequest request) {
        Long connectionId = request.connectionId();
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        if (connectionId != null) {
            connectionAccessService.checkConnectionAccessible(userId, admin, connectionId);
        }
        return ResponseEntity.ok(currentUserService.updateLastConnection(connectionId));
    }
}
