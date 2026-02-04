package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.ExamAttempt;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminAnalyticsRepository extends Repository<ExamAttempt, Long> {

    // ===== Projections =====
    interface OverviewRow {
        Long getTotalAttempts();
        Long getTotalStudents();
        Double getAvgScore();
        Long getFailedAttempts();
    }

    interface StudentAggRow {
        Long getUserId();
        String getFullName();
        String getEmail();
        Long getAttemptsCount();
        Double getAvgScore();
        Long getFailedCount();
        LocalDateTime getLastAttemptAt();
    }

    interface StudentAggByUserRow {
        Long getUserId();
        String getFullName();
        String getEmail();
        Long getAttemptsCount();
        Double getAvgScore();
        Long getFailedCount();
        LocalDateTime getLastAttemptAt();
    }

    interface TimeSeriesRow {
        String getD(); // yyyy-MM-dd
        Long getAttempts();
        Double getAvgScore();
        Long getFailed();
    }

    interface FeedbackSampleRow {
        Long getUserId();
        String getAiFeedback();
        LocalDateTime getSubmitTime();
    }

    interface LatestFeedbackRow {
        Long getUserId();
        String getAiFeedback();
        Integer getScore();
        LocalDateTime getSubmitTime();
    }

    // ===== Overview =====
    @Query(value = """
        SELECT
            COUNT(*) AS totalAttempts,
            COUNT(DISTINCT ea.user_id) AS totalStudents,
            AVG(ea.score) AS avgScore,
            SUM(CASE WHEN ea.status = 'FAILED' THEN 1 ELSE 0 END) AS failedAttempts
        FROM exam_attempt ea
        JOIN `user` u ON u.id = ea.user_id
        WHERE ea.score IS NOT NULL
          AND ea.submit_time IS NOT NULL
          AND (u.status IS NULL OR u.status <> 'BLOCKED')
          AND (:classId IS NULL OR ea.class_id = :classId)
          AND (:moduleId IS NULL OR ea.module_id = :moduleId)
          AND (:fromTs IS NULL OR ea.submit_time >= :fromTs)
          AND (:toTs IS NULL OR ea.submit_time <= :toTs)
          AND (:scoreMin IS NULL OR ea.score >= :scoreMin)
          AND (:scoreMax IS NULL OR ea.score <= :scoreMax)
          AND (
              :keyword IS NULL
              OR LOWER(u.email) LIKE CONCAT('%', LOWER(:keyword), '%')
              OR LOWER(u.full_name) LIKE CONCAT('%', LOWER(:keyword), '%')
          )
        """, nativeQuery = true)
    OverviewRow getOverview(
            @Param("classId") Long classId,
            @Param("moduleId") Long moduleId,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs,
            @Param("scoreMin") Integer scoreMin,
            @Param("scoreMax") Integer scoreMax,
            @Param("keyword") String keyword
    );

    // ===== Student aggregates (for at-risk list, sorting by avgScore ASC) =====
    @Query(value = """
        SELECT
            u.id AS userId,
            u.full_name AS fullName,
            u.email AS email,
            COUNT(*) AS attemptsCount,
            AVG(ea.score) AS avgScore,
            SUM(CASE WHEN ea.status = 'FAILED' THEN 1 ELSE 0 END) AS failedCount,
            MAX(ea.submit_time) AS lastAttemptAt
        FROM exam_attempt ea
        JOIN `user` u ON u.id = ea.user_id
        WHERE ea.score IS NOT NULL
          AND ea.submit_time IS NOT NULL
          AND (u.status IS NULL OR u.status <> 'BLOCKED')
          AND (:classId IS NULL OR ea.class_id = :classId)
          AND (:moduleId IS NULL OR ea.module_id = :moduleId)
          AND (:fromTs IS NULL OR ea.submit_time >= :fromTs)
          AND (:toTs IS NULL OR ea.submit_time <= :toTs)
          AND (:scoreMin IS NULL OR ea.score >= :scoreMin)
          AND (:scoreMax IS NULL OR ea.score <= :scoreMax)
          AND (
              :keyword IS NULL
              OR LOWER(u.email) LIKE CONCAT('%', LOWER(:keyword), '%')
              OR LOWER(u.full_name) LIKE CONCAT('%', LOWER(:keyword), '%')
          )
        GROUP BY u.id, u.full_name, u.email
        ORDER BY AVG(ea.score) ASC
        """, nativeQuery = true)
    List<StudentAggRow> getStudentAgg(
            @Param("classId") Long classId,
            @Param("moduleId") Long moduleId,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs,
            @Param("scoreMin") Integer scoreMin,
            @Param("scoreMax") Integer scoreMax,
            @Param("keyword") String keyword
    );

    // ===== Student aggregates (for single-student insight; not requiring at-risk) =====
    @Query(value = """
        SELECT
            u.id AS userId,
            u.full_name AS fullName,
            u.email AS email,
            COUNT(*) AS attemptsCount,
            AVG(ea.score) AS avgScore,
            SUM(CASE WHEN ea.status = 'FAILED' THEN 1 ELSE 0 END) AS failedCount,
            MAX(ea.submit_time) AS lastAttemptAt
        FROM exam_attempt ea
        JOIN `user` u ON u.id = ea.user_id
        WHERE ea.user_id = :userId
          AND ea.score IS NOT NULL
          AND ea.submit_time IS NOT NULL
          AND (u.status IS NULL OR u.status <> 'BLOCKED')
          AND (:classId IS NULL OR ea.class_id = :classId)
          AND (:moduleId IS NULL OR ea.module_id = :moduleId)
          AND (:fromTs IS NULL OR ea.submit_time >= :fromTs)
          AND (:toTs IS NULL OR ea.submit_time <= :toTs)
        GROUP BY u.id, u.full_name, u.email
        """, nativeQuery = true)
    StudentAggByUserRow getStudentAggByUser(
            @Param("userId") Long userId,
            @Param("classId") Long classId,
            @Param("moduleId") Long moduleId,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs
    );

    // ===== Time series =====
    @Query(value = """
        SELECT
            DATE_FORMAT(ea.submit_time, '%Y-%m-%d') AS d,
            COUNT(*) AS attempts,
            AVG(ea.score) AS avgScore,
            SUM(CASE WHEN ea.status = 'FAILED' THEN 1 ELSE 0 END) AS failed
        FROM exam_attempt ea
        JOIN `user` u ON u.id = ea.user_id
        WHERE ea.score IS NOT NULL
          AND ea.submit_time IS NOT NULL
          AND (u.status IS NULL OR u.status <> 'BLOCKED')
          AND (:classId IS NULL OR ea.class_id = :classId)
          AND (:moduleId IS NULL OR ea.module_id = :moduleId)
          AND (:fromTs IS NULL OR ea.submit_time >= :fromTs)
          AND (:toTs IS NULL OR ea.submit_time <= :toTs)
        GROUP BY DATE_FORMAT(ea.submit_time, '%Y-%m-%d')
        ORDER BY d ASC
        """, nativeQuery = true)
    List<TimeSeriesRow> getTimeSeries(
            @Param("classId") Long classId,
            @Param("moduleId") Long moduleId,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs
    );

    // ===== AI helpers =====

    // Sample feedback for multiple users (AI insights list)
    @Query(value = """
        SELECT
            ea.user_id AS userId,
            ea.ai_feedback AS aiFeedback,
            ea.submit_time AS submitTime
        FROM exam_attempt ea
        JOIN `user` u ON u.id = ea.user_id
        WHERE ea.ai_feedback IS NOT NULL
          AND ea.submit_time IS NOT NULL
          AND (u.status IS NULL OR u.status <> 'BLOCKED')
          AND (:classId IS NULL OR ea.class_id = :classId)
          AND (:moduleId IS NULL OR ea.module_id = :moduleId)
          AND (:fromTs IS NULL OR ea.submit_time >= :fromTs)
          AND (:toTs IS NULL OR ea.submit_time <= :toTs)
          AND (:scoreMin IS NULL OR ea.score >= :scoreMin)
          AND (:scoreMax IS NULL OR ea.score <= :scoreMax)
          AND (
              :keyword IS NULL
              OR LOWER(u.email) LIKE CONCAT('%', LOWER(:keyword), '%')
              OR LOWER(u.full_name) LIKE CONCAT('%', LOWER(:keyword), '%')
          )
          AND ea.user_id IN (:userIds)
        ORDER BY ea.submit_time DESC
        """, nativeQuery = true)
    List<FeedbackSampleRow> getFeedbackSamplesForUsers(
            @Param("classId") Long classId,
            @Param("moduleId") Long moduleId,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs,
            @Param("scoreMin") Integer scoreMin,
            @Param("scoreMax") Integer scoreMax,
            @Param("keyword") String keyword,
            @Param("userIds") List<Long> userIds
    );

    // Latest feedback for one user (AI single-student)
    @Query(value = """
        SELECT
            ea.user_id AS userId,
            ea.ai_feedback AS aiFeedback,
            ea.score AS score,
            ea.submit_time AS submitTime
        FROM exam_attempt ea
        JOIN `user` u ON u.id = ea.user_id
        WHERE ea.user_id = :userId
          AND ea.ai_feedback IS NOT NULL
          AND ea.submit_time IS NOT NULL
          AND (u.status IS NULL OR u.status <> 'BLOCKED')
          AND (:classId IS NULL OR ea.class_id = :classId)
          AND (:moduleId IS NULL OR ea.module_id = :moduleId)
          AND (:fromTs IS NULL OR ea.submit_time >= :fromTs)
          AND (:toTs IS NULL OR ea.submit_time <= :toTs)
        ORDER BY ea.submit_time DESC
        LIMIT 3
        """, nativeQuery = true)
    List<LatestFeedbackRow> getLatestFeedbackByUser(
            @Param("userId") Long userId,
            @Param("classId") Long classId,
            @Param("moduleId") Long moduleId,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs
    );
}
