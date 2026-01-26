package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {
    Optional<ExamAttempt> findByIdAndUserId(Long id, Long userId);
    // ✅ Completed = attempt có score (đã chấm)
    @Query(value = """
        SELECT COUNT(*)
        FROM exam_attempt ea
        WHERE ea.user_id = :userId
          AND ea.score IS NOT NULL
        """, nativeQuery = true)
    long countCompletedByUser(@Param("userId") Long userId);

    // ✅ Avg score = average trên các attempt có score
    @Query(value = """
        SELECT AVG(ea.score)
        FROM exam_attempt ea
        WHERE ea.user_id = :userId
          AND ea.score IS NOT NULL
        """, nativeQuery = true)
    Double getAvgScoreByUser(@Param("userId") Long userId);

    // ✅ Online time: nếu thiếu submit_time thì bỏ qua record đó
    @Query(value = """
        SELECT COALESCE(
          SUM(TIMESTAMPDIFF(SECOND, ea.start_time, ea.submit_time)),
          0
        )
        FROM exam_attempt ea
        WHERE ea.user_id = :userId
          AND ea.start_time IS NOT NULL
          AND ea.submit_time IS NOT NULL
        """, nativeQuery = true)
    Long sumDurationByUser(@Param("userId") Long userId);

    // ✅ Rank: dùng AVG(score) để xếp hạng
    @Query(value = """
        SELECT t.final_rank
        FROM (
            SELECT
                u.id AS user_id,
                RANK() OVER (
                    ORDER BY COALESCE(AVG(ea.score), 0) DESC
                ) AS final_rank
            FROM `user` u
            JOIN user_role ur ON u.id = ur.user_id
            JOIN role r ON ur.role_id = r.id
            LEFT JOIN exam_attempt ea
                ON ea.user_id = u.id
               AND ea.score IS NOT NULL
            WHERE r.name = 'STUDENT'
            GROUP BY u.id
        ) t
        WHERE t.user_id = :userId
        """, nativeQuery = true)
    Integer getRankAmongStudentsByUser(@Param("userId") Long userId);

    @Query("""
    select ea
    from ExamAttempt ea
    join fetch ea.exam e
    where ea.id = :id and ea.user.id = :userId
""")
    Optional<ExamAttempt> findByIdAndUserIdFetchExam(@Param("id") Long id, @Param("userId") Long userId);

}
