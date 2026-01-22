package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByStatus(UserStatus status);

    Optional<User> findByApproveToken(String approveToken);

    boolean existsByEmail(String email);

    @Query("""
        select distinct u
        from User u
        join u.userRoles ur
        join ur.role r
        where u.status = :status
          and r.name = :roleName
    """)
    List<User> findByStatusAndRoleName(
            @Param("status") UserStatus status,
            @Param("roleName") String roleName
    );
}
