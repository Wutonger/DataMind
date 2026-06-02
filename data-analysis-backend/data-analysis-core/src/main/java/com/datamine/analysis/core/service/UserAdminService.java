package com.datamine.analysis.core.service;

import com.datamine.analysis.common.dto.PageResponseDTO;
import com.datamine.analysis.common.dto.auth.UserProfileDTO;
import com.datamine.analysis.common.dto.auth.UserStatusUpdateRequest;
import com.datamine.analysis.common.dto.auth.UserUpsertRequest;
import com.datamine.analysis.common.entity.SysUser;
import com.datamine.analysis.common.repository.ConnectionUserAccessRepository;
import com.datamine.analysis.common.repository.SysUserRepository;
import com.datamine.analysis.core.auth.RoleConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final SysUserRepository sysUserRepository;
    private final PasswordHashService passwordHashService;
    private final CurrentUserService currentUserService;
    private final ConnectionUserAccessRepository connectionUserAccessRepository;

    public PageResponseDTO<UserProfileDTO> listUsers(int page, int pageSize, String username, String status) {
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        String normalizedUsername = normalizeKeyword(username);
        String normalizedStatus = normalizeFilterStatus(status);
        PageRequest pageable = PageRequest.of(
                normalizedPage - 1,
                normalizedPageSize,
                Sort.by(Sort.Direction.ASC, "id")
        );
        return PageResponseDTO.from(
                sysUserRepository.searchPageByUsernameAndStatus(
                                RoleConstants.ADMIN,
                                normalizedUsername,
                                normalizedStatus,
                                pageable
                        )
                        .map(currentUserService::toProfile)
        );
    }

    @Transactional
    public UserProfileDTO createUser(UserUpsertRequest request) {
        String username = normalizeRequired(request.username(), "用户名不能为空");
        if (sysUserRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }

        String password = normalizeRequired(request.password(), "新建用户时必须设置密码");
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordHashService.hash(password));
        user.setNickname(normalizeNickname(request.nickname(), username));
        user.setRole(RoleConstants.USER);
        user.setStatus(normalizeStatus(request.status()));
        return currentUserService.toProfile(sysUserRepository.save(user));
    }

    @Transactional
    public UserProfileDTO updateUser(Long userId, UserUpsertRequest request) {
        SysUser user = getUserEntity(userId);
        rejectAdminManagement(user);
        user.setNickname(normalizeNickname(request.nickname(), user.getUsername()));
        user.setRole(RoleConstants.USER);
        user.setStatus(normalizeStatus(request.status()));
        if (StringUtils.hasText(request.password())) {
            user.setPassword(passwordHashService.hash(request.password().trim()));
        }
        return currentUserService.toProfile(sysUserRepository.save(user));
    }

    @Transactional
    public UserProfileDTO updateStatus(Long userId, UserStatusUpdateRequest request) {
        SysUser user = getUserEntity(userId);
        rejectAdminManagement(user);
        user.setStatus(normalizeStatus(request.status()));
        return currentUserService.toProfile(sysUserRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long userId) {
        SysUser user = getUserEntity(userId);
        rejectAdminManagement(user);
        connectionUserAccessRepository.findByUserIdOrderByConnectionIdAsc(userId)
                .forEach(access -> connectionUserAccessRepository.deleteById(access.getId()));
        sysUserRepository.delete(user);
    }

    private SysUser getUserEntity(Long userId) {
        return sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeNickname(String nickname, String fallback) {
        return StringUtils.hasText(nickname) ? nickname.trim() : fallback;
    }

    private void rejectAdminManagement(SysUser user) {
        if (RoleConstants.ADMIN.equalsIgnoreCase(user.getRole())) {
            throw new IllegalStateException("管理员账号不在用户管理范围内");
        }
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : RoleConstants.ACTIVE;
        return RoleConstants.DISABLED.equals(normalized) ? RoleConstants.DISABLED : RoleConstants.ACTIVE;
    }

    private int normalizePage(int page) {
        return page > 0 ? page : DEFAULT_PAGE;
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private String normalizeFilterStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (RoleConstants.ACTIVE.equals(normalized) || RoleConstants.DISABLED.equals(normalized)) {
            return normalized;
        }
        return null;
    }
}
