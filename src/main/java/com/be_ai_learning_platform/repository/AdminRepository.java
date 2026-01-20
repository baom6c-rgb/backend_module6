package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface AdminRepository extends JpaRepository<User, Long> {
    List<User> findByStatus(UserStatus status);
}