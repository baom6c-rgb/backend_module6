package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.ExamAttempt;
import com.be_ai_learning_platform.entity.enums.ExamResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    // Khớp với Service: countCompletedByUser
    @Query("SELECT COUNT(ea) FROM ExamAttempt ea WHERE ea.exam.user.id = :userId AND ea.status = 'PASSED'")
    long countCompletedByUser(@Param("userId") Long userId);

    // Khớp với Service: getAvgScoreByUser
    @Query("SELECT COALESCE(AVG(ea.score), 0.0) FROM ExamAttempt ea WHERE ea.exam.user.id = :userId")
    Double getAvgScoreByUser(@Param("userId") Long userId);

    // Khớp với Service: sumDurationByUser
    @Query(value = "SELECT COALESCE(SUM(TIMESTAMPDIFF(SECOND, ea.start_time, ea.submit_time)), 0) " +
            "FROM exam_attempt ea " +
            "JOIN exam e ON ea.exam_id = e.id " +
            "WHERE e.user_id = :userId", nativeQuery = true)
    Long sumDurationByUser(@Param("userId") Long userId);

    // Khớp với Service: getRankByUser
    @Query(value = """
    SELECT rank_table.final_rank FROM (
        SELECT 
            u.id as user_id, 
            RANK() OVER (ORDER BY AVG(ea.score) DESC) as final_rank 
        FROM `user` u 
        JOIN user_role ur ON u.id = ur.user_id 
        JOIN role r ON ur.role_id = r.id 
        JOIN exam_attempts ea ON u.id = ea.user_id 
        WHERE r.name = 'STUDENT' 
        GROUP BY u.id
    ) as rank_table 
    WHERE rank_table.user_id = :userId
    """, nativeQuery = true)
    Integer getRankAmongStudentsByUser(@Param("userId") Long userId);
}
