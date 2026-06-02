package com.datamine.analysis.core.service;

import cn.dev33.satoken.stp.StpUtil;
import com.datamine.analysis.common.dto.auth.UserProfileDTO;
import com.datamine.analysis.common.entity.SysUser;
import com.datamine.analysis.common.repository.SysUserRepository;
import com.datamine.analysis.core.auth.RoleConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final SysUserRepository sysUserRepository;

    public Long getRequiredUserId() {
        StpUtil.checkLogin();
        return Long.valueOf(String.valueOf(StpUtil.getLoginId()));
    }

    public SysUser getRequiredUser() {
        Long userId = getRequiredUserId();
        return sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("当前登录用户不存在: " + userId));
    }

    public boolean isAdmin() {
        return RoleConstants.ADMIN.equalsIgnoreCase(getRequiredUser().getRole());
    }

    public void checkAdmin() {
        if (!isAdmin()) {
            throw new IllegalStateException("当前账号没有管理员权限");
        }
    }

    public UserProfileDTO updateLastConnection(Long connectionId) {
        SysUser user = getRequiredUser();
        user.setLastConnectionId(connectionId);
        return toProfile(sysUserRepository.save(user));
    }

    public UserProfileDTO getCurrentProfile() {
        return toProfile(getRequiredUser());
    }

    public UserProfileDTO toProfile(SysUser user) {
        return new UserProfileDTO(
                user.getId(),
                user.getUsername(),
                StringUtils.hasText(user.getNickname()) ? user.getNickname().trim() : user.getUsername(),
                user.getRole(),
                user.getStatus(),
                user.getLastConnectionId()
        );
    }
}
