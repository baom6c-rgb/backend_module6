package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByStatus(UserStatus status);

    Optional<User> findByApproveToken(String approveToken);

    boolean existsByEmail(String email);

    Optional<User> findByResetPasswordToken(String token);

    /**
     * ✅ HÀM MỚI: Đếm tổng số lượng User dựa trên tên Role (Ví dụ: 'STUDENT')
     * JPQL tự động hiểu việc join từ User -> UserRole -> Role
     */
    @Query("SELECT COUNT(u) FROM User u JOIN u.userRoles ur JOIN ur.role r WHERE r.name = :roleName AND u.isDeleted = false")
    long countByRoleName(@Param("roleName") String roleName);

    /**
     * Tìm danh sách User dựa trên trạng thái và tên Role
     */
    @Query("""
        select distinct u
        from User u
        join u.userRoles ur
        join ur.role r
        where u.status = :status
          and r.name = :roleName
          and u.isDeleted = false
    """)
    List<User> findByStatusAndRoleName(
            @Param("status") UserStatus status,
            @Param("roleName") String roleName
    );
}