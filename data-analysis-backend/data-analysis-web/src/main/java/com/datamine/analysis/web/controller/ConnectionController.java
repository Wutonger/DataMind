package com.datamine.analysis.web.controller;

import com.datamine.analysis.common.entity.Connection;
import com.datamine.analysis.core.service.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    @GetMapping
    public ResponseEntity<List<Connection>> getAllConnections() {
        return ResponseEntity.ok(connectionService.getAllConnections());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Connection> getConnectionById(@PathVariable Long id) {
        return connectionService.getConnectionById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    public ResponseEntity<List<Connection>> getActiveConnections() {
        return ResponseEntity.ok(connectionService.getActiveConnections());
    }

    @PostMapping
    public ResponseEntity<Connection> createConnection(@RequestBody Connection connection) {
        Connection saved = connectionService.saveConnection(connection);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/test")
    public ResponseEntity<Boolean> testConnection(@RequestBody Connection connection) {
        boolean success = connectionService.testConnection(connection);
        return ResponseEntity.ok(success);
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Boolean> testSavedConnection(@PathVariable Long id) {
        boolean success = connectionService.testSavedConnection(id);
        return ResponseEntity.ok(success);
    }

    @PostMapping("/test-and-save")
    public ResponseEntity<Connection> testAndSaveConnection(@RequestBody Connection connection) {
        Connection saved = connectionService.testAndSave(connection);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Connection> updateConnection(@PathVariable Long id, @RequestBody Connection connection) {
        Connection updated = connectionService.updateConnection(id, connection);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConnection(@PathVariable Long id) {
        connectionService.deleteConnection(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Connection> activateConnection(@PathVariable Long id) {
        Connection activated = connectionService.activateConnection(id);
        return ResponseEntity.ok(activated);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateConnection(@PathVariable Long id) {
        connectionService.deactivateConnection(id);
        return ResponseEntity.ok().build();
    }
}
