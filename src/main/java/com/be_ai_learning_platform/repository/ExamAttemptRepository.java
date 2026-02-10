package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    // ===== Dashboard =====

    // Tính điểm trung bình cho dashboard (lọc score != null)
    @Query("SELECT AVG(ea.score) FROM ExamAttempt ea WHERE ea.user.id = :userId AND ea.score IS NOT NULL")
    Double getAverageScoreByUserId(@Param("userId") Long userId);

    // Đếm số bài đạt yêu cầu (score >= passScore của Exam) - FIX @Param
    @Query("SELECT COUNT(ea) FROM ExamAttempt ea WHERE ea.user.id = :userId AND ea.score IS NOT NULL AND ea.score >= ea.exam.passScore")
    Long countPassedTests(@Param("userId") Long userId);

    // Sử dụng Native Query để ép buộc query vào bảng vật lý
    @Query(value = "SELECT * FROM exam_attempt WHERE user_id = :userId ORDER BY submit_time DESC", nativeQuery = true)
    List<ExamAttempt> findByUserIdNative(@Param("userId") Long userId);

    @Query("""
        SELECT ea FROM ExamAttempt ea
            JOIN FETCH ea.user
            JOIN FETCH ea.exam
            LEFT JOIN FETCH ea.learningModule
            LEFT JOIN FETCH ea.classroom
        ORDER BY ea.submitTime DESC
    """)
    List<ExamAttempt> findAllWithUserDetails();

    // ===================== Monthly report (US21) =====================

    /**
     * Tổng số bài làm trong khoảng thời gian: attempt đã nộp (submit_time != null)
     */
    @Query(value = """
        SELECT COUNT(*)
        FROM exam_attempt ea
        WHERE ea.submit_time IS NOT NULL
          AND ea.submit_time >= :start
          AND ea.submit_time <= :end
        """, nativeQuery = true)
    long countSubmittedInRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Học viên làm nhiều nhất trong tháng (theo số attempt submit)
     * return: [user_id, email, full_name, attempt_count]
     */
    @Query(value = """
        SELECT u.id AS user_id, u.email AS email, u.full_name AS full_name, COUNT(*) AS attempt_count
        FROM exam_attempt ea
        JOIN `user` u ON u.id = ea.user_id
        WHERE ea.submit_time IS NOT NULL
          AND ea.submit_time >= :start
          AND ea.submit_time <= :end
        GROUP BY u.id, u.email, u.full_name
        ORDER BY attempt_count DESC
        LIMIT 1
        """, nativeQuery = true)
    List<Object[]> findTopStudentByAttemptCountInRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Học viên có kết quả tốt nhất (AVG(score) cao nhất) trong tháng
     * return: [user_id, email, full_name, avg_score]
     */
    @Query(value = """
        SELECT u.id AS user_id, u.email AS email, u.full_name AS full_name, AVG(ea.score) AS avg_score
        FROM exam_attempt ea
        JOIN `user` u ON u.id = ea.user_id
        WHERE ea.submit_time IS NOT NULL
          AND ea.score IS NOT NULL
          AND ea.submit_time >= :start
          AND ea.submit_time <= :end
        GROUP BY u.id, u.email, u.full_name
        ORDER BY avg_score DESC
        LIMIT 1
        """, nativeQuery = true)
    List<Object[]> findBestStudentByAvgScoreInRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Học viên có kết quả kém nhất (AVG(score) thấp nhất) trong tháng
     * return: [user_id, email, full_name, avg_score]
     */
    @Query(value = """
        SELECT u.id AS user_id, u.email AS email, u.full_name AS full_name, AVG(ea.score) AS avg_score
        FROM exam_attempt ea
        JOIN `user` u ON u.id = ea.user_id
        WHERE ea.submit_time IS NOT NULL
          AND ea.score IS NOT NULL
          AND ea.submit_time >= :start
          AND ea.submit_time <= :end
        GROUP BY u.id, u.email, u.full_name
        ORDER BY avg_score ASC
        LIMIT 1
        """, nativeQuery = true)
    List<Object[]> findWorstStudentByAvgScoreInRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Danh sách attempt đã nộp trong range, fetch đủ dữ liệu để export excel:
     * user + exam + module + classroom
     */
    @Query("""
        SELECT ea FROM ExamAttempt ea
            JOIN FETCH ea.user
            JOIN FETCH ea.exam
            LEFT JOIN FETCH ea.learningModule
            LEFT JOIN FETCH ea.classroom
        WHERE ea.submitTime IS NOT NULL
          AND ea.submitTime >= :start
          AND ea.submitTime <= :end
        ORDER BY ea.submitTime DESC
    """)
    List<ExamAttempt> findSubmittedAttemptsInRangeWithDetails(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // ===================== Dashboard: Passed & Failed Lessons =====================

    /**
     * Đếm số bài ĐẠT yêu cầu (score >= passScore của Exam)
     * Chỉ đếm những bài đã có điểm (score IS NOT NULL)
     */
    @Query(value = """
        SELECT COUNT(*)
        FROM exam_attempt ea
        JOIN exam e ON ea.exam_id = e.id
        WHERE ea.user_id = :userId
          AND ea.score IS NOT NULL
          AND ea.score >= 80
        
        """, nativeQuery = true)
    long countPassedLessonsByUser(@Param("userId") Long userId);

    /**
     * Đếm số bài CHƯA ĐẠT (score < passScore của Exam)
     * Chỉ đếm những bài đã có điểm (score IS NOT NULL)
     */
    @Query(value = """
        SELECT COUNT(*)
        FROM exam_attempt ea
        JOIN exam e ON ea.exam_id = e.id
        WHERE ea.user_id = :userId
          AND ea.score IS NOT NULL
          AND ea.score < 80
        """, nativeQuery = true)
    long countFailedLessonsByUser(@Param("userId") Long userId);
}