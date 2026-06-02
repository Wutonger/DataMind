package com.datamine.analysis.web.config;

import cn.dev33.satoken.stp.StpInterface;
import com.datamine.analysis.common.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SaTokenRoleProvider implements StpInterface {

    private final SysUserRepository sysUserRepository;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.valueOf(String.valueOf(loginId));
        return sysUserRepository.findById(userId)
                .map(user -> List.of(user.getRole()))
                .orElseGet(Collections::emptyList);
    }
}
