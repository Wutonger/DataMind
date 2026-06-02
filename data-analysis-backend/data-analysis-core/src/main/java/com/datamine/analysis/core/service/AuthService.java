package com.datamine.analysis.core.service;

import cn.dev33.satoken.stp.StpUtil;
import com.datamine.analysis.common.dto.auth.AuthLoginRequest;
import com.datamine.analysis.common.dto.auth.AuthLoginResponse;
import com.datamine.analysis.common.dto.auth.UserProfileDTO;
import com.datamine.analysis.common.entity.SysUser;
import com.datamine.analysis.common.repository.SysUserRepository;
import com.datamine.analysis.core.auth.RoleConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserRepository sysUserRepository;
    private final PasswordHashService passwordHashService;
    private final CurrentUserService currentUserService;

    @Transactional
    public AuthLoginResponse login(AuthLoginRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        String password = request.password() == null ? "" : request.password();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }

        SysUser user = sysUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!RoleConstants.ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new IllegalStateException("当前账号已被禁用");
        }
        if (!passwordHashService.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        StpUtil.login(user.getId());
        user.setLastLoginAt(LocalDateTime.now());
        sysUserRepository.save(user);
        UserProfileDTO profile = currentUserService.toProfile(user);
        return new AuthLoginResponse(StpUtil.getTokenValue(), profile);
    }

    public void logout() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
    }

    public UserProfileDTO currentUser() {
        return currentUserService.getCurrentProfile();
    }
}
