package com.datamine.analysis.core.service;

import com.datamine.analysis.common.dto.auth.UserConnectionAccessDTO;
import com.datamine.analysis.common.dto.connection.ConnectionAccessDTO;
import com.datamine.analysis.common.entity.Connection;
import com.datamine.analysis.common.entity.ConnectionUserAccess;
import com.datamine.analysis.common.entity.SysUser;
import com.datamine.analysis.common.repository.ConnectionRepository;
import com.datamine.analysis.common.repository.ConnectionUserAccessRepository;
import com.datamine.analysis.common.repository.SysUserRepository;
import com.datamine.analysis.core.auth.RoleConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConnectionAccessService {

    private final ConnectionRepository connectionRepository;
    private final ConnectionUserAccessRepository connectionUserAccessRepository;
    private final SysUserRepository sysUserRepository;

    public List<Connection> listAccessibleConnections(Long userId, boolean admin) {
        if (admin) {
            return connectionRepository.findAllByOrderByIdAsc();
        }
        List<Long> connectionIds = connectionUserAccessRepository.findByUserIdOrderByConnectionIdAsc(userId)
                .stream()
                .map(ConnectionUserAccess::getConnectionId)
                .distinct()
                .toList();
        if (connectionIds.isEmpty()) {
            return List.of();
        }
        return connectionRepository.findByIdInOrderByIdAsc(connectionIds);
    }

    public void checkConnectionAccessible(Long userId, boolean admin, Long connectionId) {
        if (connectionId == null) {
            throw new IllegalArgumentException("connectionId 不能为空");
        }
        if (admin) {
            ensureConnectionExists(connectionId);
            return;
        }
        boolean allowed = connectionUserAccessRepository.existsByConnectionIdAndUserId(connectionId, userId);
        if (!allowed) {
            throw new IllegalStateException("当前用户无权访问该数据库连接");
        }
    }

    public List<Long> getAuthorizedUserIds(Long connectionId) {
        ensureConnectionExists(connectionId);
        return connectionUserAccessRepository.findByConnectionIdOrderByUserIdAsc(connectionId)
                .stream()
                .map(ConnectionUserAccess::getUserId)
                .filter(this::isGrantableUser)
                .toList();
    }

    public List<Long> getAuthorizedConnectionIds(Long userId) {
        ensureGrantableUser(userId);
        return connectionUserAccessRepository.findByUserIdOrderByConnectionIdAsc(userId)
                .stream()
                .map(ConnectionUserAccess::getConnectionId)
                .distinct()
                .toList();
    }

    @Transactional
    public ConnectionAccessDTO updateAuthorizedUsers(Long connectionId, List<Long> userIds, Long operatorId) {
        ensureConnectionExists(connectionId);
        Set<Long> normalizedUserIds = new LinkedHashSet<>();
        if (userIds != null) {
            for (Long userId : userIds) {
                if (userId == null) {
                    continue;
                }
                if (isGrantableUser(userId)) {
                    normalizedUserIds.add(userId);
                }
            }
        }

        List<ConnectionUserAccess> existing = new ArrayList<>(connectionUserAccessRepository.findByConnectionIdOrderByUserIdAsc(connectionId));
        Set<Long> removedUserIds = new LinkedHashSet<>();
        for (ConnectionUserAccess access : existing) {
            if (!normalizedUserIds.contains(access.getUserId())) {
                removedUserIds.add(access.getUserId());
                connectionUserAccessRepository.deleteById(access.getId());
            }
        }
        clearLastConnectionSelection(connectionId, removedUserIds);

        Set<Long> existingUserIds = existing.stream()
                .map(ConnectionUserAccess::getUserId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (Long userId : normalizedUserIds) {
            if (existingUserIds.contains(userId)) {
                continue;
            }
            ConnectionUserAccess access = new ConnectionUserAccess();
            access.setConnectionId(connectionId);
            access.setUserId(userId);
            access.setCreatedBy(operatorId);
            connectionUserAccessRepository.save(access);
        }

        return new ConnectionAccessDTO(connectionId, getAuthorizedUserIds(connectionId));
    }

    @Transactional
    public UserConnectionAccessDTO updateAuthorizedConnections(Long userId, List<Long> connectionIds, Long operatorId) {
        SysUser user = ensureGrantableUser(userId);
        Set<Long> normalizedConnectionIds = normalizeConnectionIds(connectionIds);
        ensureConnectionsExist(normalizedConnectionIds);

        List<ConnectionUserAccess> existing = new ArrayList<>(connectionUserAccessRepository.findByUserIdOrderByConnectionIdAsc(userId));
        Set<Long> removedConnectionIds = new LinkedHashSet<>();
        for (ConnectionUserAccess access : existing) {
            if (!normalizedConnectionIds.contains(access.getConnectionId())) {
                removedConnectionIds.add(access.getConnectionId());
                connectionUserAccessRepository.deleteById(access.getId());
            }
        }
        clearLastConnectionSelectionForUser(user, removedConnectionIds);

        Set<Long> existingConnectionIds = existing.stream()
                .map(ConnectionUserAccess::getConnectionId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (Long connectionId : normalizedConnectionIds) {
            if (existingConnectionIds.contains(connectionId)) {
                continue;
            }
            ConnectionUserAccess access = new ConnectionUserAccess();
            access.setConnectionId(connectionId);
            access.setUserId(userId);
            access.setCreatedBy(operatorId);
            connectionUserAccessRepository.save(access);
        }

        return new UserConnectionAccessDTO(userId, getAuthorizedConnectionIds(userId));
    }

    @Transactional
    public void clearLastConnectionSelection(Long connectionId, Set<Long> userIds) {
        if (connectionId == null || userIds == null || userIds.isEmpty()) {
            return;
        }
        List<SysUser> users = sysUserRepository.findAllById(userIds);
        boolean changed = false;
        for (SysUser user : users) {
            if (connectionId.equals(user.getLastConnectionId())) {
                user.setLastConnectionId(null);
                changed = true;
            }
        }
        if (changed) {
            sysUserRepository.saveAll(users);
        }
    }

    private Set<Long> normalizeConnectionIds(List<Long> connectionIds) {
        Set<Long> normalizedConnectionIds = new LinkedHashSet<>();
        if (connectionIds == null) {
            return normalizedConnectionIds;
        }
        for (Long connectionId : connectionIds) {
            if (connectionId != null) {
                normalizedConnectionIds.add(connectionId);
            }
        }
        return normalizedConnectionIds;
    }

    private void ensureConnectionsExist(Set<Long> connectionIds) {
        if (connectionIds.isEmpty()) {
            return;
        }
        Set<Long> existingIds = connectionRepository.findByIdInOrderByIdAsc(new ArrayList<>(connectionIds))
                .stream()
                .map(Connection::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (existingIds.size() == connectionIds.size()) {
            return;
        }
        Set<Long> missingIds = new LinkedHashSet<>(connectionIds);
        missingIds.removeAll(existingIds);
        throw new IllegalArgumentException("连接不存在: " + missingIds);
    }

    private void clearLastConnectionSelectionForUser(SysUser user, Set<Long> removedConnectionIds) {
        if (user == null || user.getLastConnectionId() == null || removedConnectionIds.isEmpty()) {
            return;
        }
        if (removedConnectionIds.contains(user.getLastConnectionId())) {
            user.setLastConnectionId(null);
            sysUserRepository.save(user);
        }
    }

    private void ensureConnectionExists(Long connectionId) {
        connectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("连接不存在: " + connectionId));
    }

    private SysUser ensureGrantableUser(Long userId) {
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        if (RoleConstants.ADMIN.equalsIgnoreCase(user.getRole())) {
            throw new IllegalStateException("管理员账号无需单独授权连接");
        }
        return user;
    }

    private boolean isGrantableUser(Long userId) {
        return sysUserRepository.findById(userId)
                .map(user -> !RoleConstants.ADMIN.equalsIgnoreCase(user.getRole()))
                .orElse(false);
    }
}
