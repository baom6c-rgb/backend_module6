package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    @Transactional
    void deleteAllByUser_Id(Long userId);

    boolean existsByUser_IdAndRole_Id(Long userId, Long roleId);
}
