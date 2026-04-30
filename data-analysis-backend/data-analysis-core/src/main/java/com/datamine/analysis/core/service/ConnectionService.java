package com.datamine.analysis.core.service;

import com.datamine.analysis.common.entity.Connection;
import com.datamine.analysis.common.repository.ConnectionRepository;
import com.datamine.analysis.core.util.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final DatabaseConnectionTester connectionTester;
    private final PasswordEncoder passwordEncoder;

    public List<Connection> getAllConnections() {
        return connectionRepository.findAll();
    }

    public Optional<Connection> getConnectionById(Long id) {
        return connectionRepository.findById(id);
    }

    public List<Connection> getActiveConnections() {
        return connectionRepository.findByStatus("active");
    }

    public Connection saveConnection(Connection connection) {
        connection.setStatus("active");
        return connectionRepository.save(connection);
    }

    public Connection updateConnection(Long id, Connection connection) {
        Optional<Connection> existing = connectionRepository.findById(id);
        if (existing.isPresent()) {
            Connection c = existing.get();
            c.setName(connection.getName());
            c.setType(connection.getType());
            c.setHost(connection.getHost());
            c.setPort(connection.getPort());
            c.setDatabase(connection.getDatabase());
            c.setUsername(connection.getUsername());
            if (connection.getPassword() != null && !connection.getPassword().isEmpty()) {
                c.setPassword(connection.getPassword());
            }
            return connectionRepository.save(c);
        }
        throw new RuntimeException("Connection not found: " + id);
    }

    @Transactional
    public void deleteConnection(Long id) {
        connectionRepository.deleteById(id);
    }

    public boolean testConnection(Connection connection) {
        return connectionTester.testRawConnection(connection);
    }

    public boolean testSavedConnection(Long id) {
        Optional<Connection> conn = connectionRepository.findById(id);
        if (conn.isPresent()) {
            return connectionTester.testEncryptedConnection(conn.get());
        }
        return false;
    }

    public Connection testAndSave(Connection connection) {
        if (testConnection(connection)) {
            return saveConnection(connection);
        }
        throw new RuntimeException("数据库连接测试失败");
    }

    @Transactional
    public Connection activateConnection(Long id) {
        connectionRepository.updateAllStatus("inactive");
        Connection c = connectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Connection not found: " + id));
        c.setStatus("active");
        return connectionRepository.save(c);
    }

    @Transactional
    public void deactivateConnection(Long id) {
        Optional<Connection> conn = connectionRepository.findById(id);
        if (conn.isPresent()) {
            Connection c = conn.get();
            c.setStatus("inactive");
            connectionRepository.save(c);
        }
    }
}
