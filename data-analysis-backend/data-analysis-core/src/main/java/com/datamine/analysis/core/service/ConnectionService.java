package com.datamine.analysis.core.service;

import com.datamine.analysis.common.entity.Connection;
import com.datamine.analysis.common.entity.SysUser;
import com.datamine.analysis.common.repository.ConnectionRepository;
import com.datamine.analysis.common.repository.ConnectionUserAccessRepository;
import com.datamine.analysis.common.repository.SysUserRepository;
import com.datamine.analysis.core.datasource.DynamicDataSource;
import com.datamine.analysis.core.util.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final ConnectionUserAccessRepository connectionUserAccessRepository;
    private final SysUserRepository sysUserRepository;
    private final DatabaseConnectionTester databaseConnectionTester;
    private final DynamicDataSource dynamicDataSource;
    private final PasswordEncoder passwordEncoder;

    public List<Connection> getAllConnections() {
        return connectionRepository.findAllByOrderByIdAsc();
    }

    public Optional<Connection> getConnectionById(Long id) {
        return connectionRepository.findById(id);
    }

    public Connection getConnectionOrThrow(Long id) {
        return getConnectionById(id)
                .orElseThrow(() -> new IllegalArgumentException("连接不存在: " + id));
    }

    public boolean testConnection(Connection connection) {
        Connection prepared = prepareForTesting(connection);
        return databaseConnectionTester.testRawConnection(prepared);
    }

    @Transactional
    public boolean testSavedConnection(Long id) {
        Connection connection = getConnectionOrThrow(id);
        boolean success = databaseConnectionTester.testEncryptedConnection(connection);
        updateStatus(connection, success ? "connected" : "error");
        return success;
    }

    @Transactional
    public Connection saveConnection(Connection connection, Long operatorId) {
        Connection entity = new Connection();
        copyEditableFields(connection, entity, true);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setStatus(defaultStatus(entity.getStatus()));
        return connectionRepository.save(entity);
    }

    @Transactional
    public Connection testAndSave(Connection connection, Long operatorId) {
        if (!testConnection(connection)) {
            throw new IllegalStateException("数据库连接测试失败，请检查连接信息");
        }
        Connection saved = saveConnection(connection, operatorId);
        updateStatus(saved, "connected");
        return getConnectionOrThrow(saved.getId());
    }

    @Transactional
    public Connection updateConnection(Long id, Connection connection, Long operatorId) {
        Connection existing = getConnectionOrThrow(id);
        copyEditableFields(connection, existing, false);
        existing.setUpdatedBy(operatorId);
        existing.setStatus(defaultStatus(existing.getStatus()));
        Connection saved = connectionRepository.save(existing);
        dynamicDataSource.removeDataSource(id);
        return saved;
    }

    @Transactional
    public void deleteConnection(Long id) {
        Connection existing = getConnectionOrThrow(id);
        connectionRepository.delete(existing);
        connectionUserAccessRepository.deleteByConnectionId(id);
        clearLastConnectionSelection(id);
        dynamicDataSource.removeDataSource(id);
    }

    private void clearLastConnectionSelection(Long connectionId) {
        List<SysUser> users = sysUserRepository.findByLastConnectionId(connectionId);
        if (users.isEmpty()) {
            return;
        }
        for (SysUser user : users) {
            user.setLastConnectionId(null);
        }
        sysUserRepository.saveAll(users);
    }

    private void updateStatus(Connection connection, String status) {
        connection.setStatus(status);
        connectionRepository.save(connection);
        if (!"connected".equalsIgnoreCase(status)) {
            dynamicDataSource.removeDataSource(connection.getId());
        }
    }

    private Connection prepareForTesting(Connection source) {
        validateRequiredFields(source, true);
        Connection prepared = new Connection();
        prepared.setId(source.getId());
        prepared.setName(source.getName().trim());
        prepared.setType(source.getType().trim());
        prepared.setHost(source.getHost().trim());
        prepared.setPort(source.getPort());
        prepared.setDatabase(source.getDatabase().trim());
        prepared.setUsername(source.getUsername().trim());
        prepared.setPassword(passwordEncoder.encode(source.getPassword().trim()));
        prepared.setStatus(defaultStatus(source.getStatus()));
        return prepared;
    }

    private void copyEditableFields(Connection source, Connection target, boolean creating) {
        validateRequiredFields(source, creating);
        target.setName(source.getName().trim());
        target.setType(source.getType().trim());
        target.setHost(source.getHost().trim());
        target.setPort(source.getPort());
        target.setDatabase(source.getDatabase().trim());
        target.setUsername(source.getUsername().trim());
        if (StringUtils.hasText(source.getPassword())) {
            target.setPassword(passwordEncoder.encode(source.getPassword().trim()));
        } else if (creating) {
            throw new IllegalArgumentException("数据库密码不能为空");
        }
        target.setStatus(defaultStatus(source.getStatus()));
    }

    private void validateRequiredFields(Connection connection, boolean requirePassword) {
        if (connection == null) {
            throw new IllegalArgumentException("连接信息不能为空");
        }
        if (!StringUtils.hasText(connection.getName())
                || !StringUtils.hasText(connection.getType())
                || !StringUtils.hasText(connection.getHost())
                || connection.getPort() == null
                || !StringUtils.hasText(connection.getDatabase())
                || !StringUtils.hasText(connection.getUsername())) {
            throw new IllegalArgumentException("连接名称、类型、主机、端口、库名和用户名不能为空");
        }
        if (requirePassword && !StringUtils.hasText(connection.getPassword())) {
            throw new IllegalArgumentException("数据库密码不能为空");
        }
    }

    private String defaultStatus(String status) {
        return StringUtils.hasText(status) ? status.trim() : "disconnected";
    }
}
