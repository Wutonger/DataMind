package com.datamine.analysis.core.service;

import com.datamine.analysis.common.entity.SysUser;
import com.datamine.analysis.common.repository.SysUserRepository;
import com.datamine.analysis.core.auth.RoleConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemBootstrapService implements CommandLineRunner {

    private final SysUserRepository sysUserRepository;
    private final PasswordHashService passwordHashService;

    @Override
    public void run(String... args) {
        if (sysUserRepository.count() > 0) {
            return;
        }

        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordHashService.hash("Admin@123456"));
        admin.setNickname("系统管理员");
        admin.setRole(RoleConstants.ADMIN);
        admin.setStatus(RoleConstants.ACTIVE);
        sysUserRepository.save(admin);
        log.info("Initialized default admin account. username=admin");
    }
}
