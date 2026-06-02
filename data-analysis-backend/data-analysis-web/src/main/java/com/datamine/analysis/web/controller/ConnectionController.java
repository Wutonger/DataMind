package com.datamine.analysis.web.controller;

import com.datamine.analysis.common.dto.connection.ConnectionAccessDTO;
import com.datamine.analysis.common.entity.Connection;
import com.datamine.analysis.core.service.ConnectionAccessService;
import com.datamine.analysis.core.service.ConnectionService;
import com.datamine.analysis.core.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;
    private final ConnectionAccessService connectionAccessService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<List<Connection>> getAllConnections() {
        currentUserService.checkAdmin();
        return ResponseEntity.ok(connectionService.getAllConnections());
    }

    @GetMapping("/accessible")
    public ResponseEntity<List<Connection>> getAccessibleConnections() {
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        return ResponseEntity.ok(connectionAccessService.listAccessibleConnections(userId, admin));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Connection> getConnectionById(@PathVariable Long id) {
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        connectionAccessService.checkConnectionAccessible(userId, admin, id);
        return ResponseEntity.ok(connectionService.getConnectionOrThrow(id));
    }

    @PostMapping
    public ResponseEntity<Connection> createConnection(@RequestBody Connection connection) {
        currentUserService.checkAdmin();
        Long userId = currentUserService.getRequiredUserId();
        return ResponseEntity.ok(connectionService.saveConnection(connection, userId));
    }

    @PostMapping("/test")
    public ResponseEntity<Boolean> testConnection(@RequestBody Connection connection) {
        currentUserService.checkAdmin();
        return ResponseEntity.ok(connectionService.testConnection(connection));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Boolean> testSavedConnection(@PathVariable Long id) {
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        connectionAccessService.checkConnectionAccessible(userId, admin, id);
        return ResponseEntity.ok(connectionService.testSavedConnection(id));
    }

    @PostMapping("/test-and-save")
    public ResponseEntity<Connection> testAndSaveConnection(@RequestBody Connection connection) {
        currentUserService.checkAdmin();
        Long userId = currentUserService.getRequiredUserId();
        return ResponseEntity.ok(connectionService.testAndSave(connection, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Connection> updateConnection(@PathVariable Long id, @RequestBody Connection connection) {
        currentUserService.checkAdmin();
        Long userId = currentUserService.getRequiredUserId();
        return ResponseEntity.ok(connectionService.updateConnection(id, connection, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConnection(@PathVariable Long id) {
        currentUserService.checkAdmin();
        connectionService.deleteConnection(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/access-users")
    public ResponseEntity<ConnectionAccessDTO> getAccessUsers(@PathVariable Long id) {
        currentUserService.checkAdmin();
        return ResponseEntity.ok(new ConnectionAccessDTO(id, connectionAccessService.getAuthorizedUserIds(id)));
    }

    @PutMapping("/{id}/access-users")
    public ResponseEntity<ConnectionAccessDTO> updateAccessUsers(@PathVariable Long id,
                                                                 @RequestBody List<Long> userIds) {
        currentUserService.checkAdmin();
        Long operatorId = currentUserService.getRequiredUserId();
        return ResponseEntity.ok(connectionAccessService.updateAuthorizedUsers(id, userIds, operatorId));
    }
}
