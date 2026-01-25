package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.UserRole;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository extends CrudRepository<UserRole, Long> {

    @Query("select ur.role.name from UserRole ur where ur.user.id = :userId")
    List<String> findRoleNamesByUserId(@Param("userId") Long userId);
}
