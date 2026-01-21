package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
}
