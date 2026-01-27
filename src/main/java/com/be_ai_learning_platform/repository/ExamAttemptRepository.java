package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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
    List<ExamAttempt> findByUserIdOrderBySubmitTimeDesc(Long userId);

    // Tính điểm trung bình cho dashboard
    @Query("SELECT AVG(ea.score) FROM ExamAttempt ea WHERE ea.user.id = :userId")
    Double getAverageScoreByUserId(Long userId);

    // Đếm số bài đạt yêu cầu (score >= passScore của Exam)
    @Query("SELECT COUNT(ea) FROM ExamAttempt ea WHERE ea.user.id = :userId AND ea.score >= ea.exam.passScore")
    Long countPassedTests(Long userId);

    // Sử dụng Native Query để ép buộc query vào bảng vật lý
    @Query(value = "SELECT * FROM exam_attempt WHERE user_id = :userId ORDER BY submit_time DESC", nativeQuery = true)
    List<ExamAttempt> findByUserIdNative(@Param("userId") Long userId);

    @Query("SELECT ea FROM ExamAttempt ea " +
            "JOIN FETCH ea.user " + // Lấy luôn thông tin User
            "JOIN FETCH ea.exam " + // Lấy luôn thông tin Exam
            "LEFT JOIN FETCH ea.learningModule " +
            "LEFT JOIN FETCH ea.classroom " +
            "ORDER BY ea.submitTime DESC")
    List<ExamAttempt> findAllWithUserDetails();
}
