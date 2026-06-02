package com.datamine.analysis.common.repository;

import com.datamine.analysis.common.entity.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    List<SysUser> findAllByOrderByIdAsc();

    List<SysUser> findByLastConnectionId(Long lastConnectionId);

    @Query("""
            select u
            from SysUser u
            where upper(u.role) <> upper(:role)
              and (:username is null
                or lower(u.username) like lower(concat('%', :username, '%')))
              and (:status is null or upper(u.status) = upper(:status))
            """)
    Page<SysUser> searchPageByUsernameAndStatus(@Param("role") String role,
                                                @Param("username") String username,
                                                @Param("status") String status,
                                                Pageable pageable);
}
