package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.ExamAssignment;
import com.be_ai_learning_platform.entity.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExamAssignmentRepository extends JpaRepository<ExamAssignment, Long> {

    Optional<ExamAssignment> findByIdAndStudentId(Long id, Long studentId);

    boolean existsByExamIdAndStudentId(Long examId, Long studentId);

    List<ExamAssignment> findAllByExamId(Long examId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ExamAssignment a set a.attempt = null where a.exam.id = :examId")
    int detachAttemptsByExamId(@Param("examId") Long examId);

    @Query("""
            select ea
            from ExamAssignment ea
            join fetch ea.exam e
            where ea.student.id = :studentId
            order by ea.createdAt desc
            """)
    List<ExamAssignment> findAllByStudentFetchExam(@Param("studentId") Long studentId);

    @Query("""
            select ea
            from ExamAssignment ea
            join fetch ea.exam e
            where ea.student.id = :studentId
              and ea.status <> com.be_ai_learning_platform.entity.enums.AssignmentStatus.CANCELED
              and (ea.openAt is null or ea.openAt <= :now)
              and (ea.dueAt is null or ea.dueAt >= :now)
            order by ea.dueAt asc nulls last, ea.createdAt desc
            """)
    List<ExamAssignment> findActiveAssignments(@Param("studentId") Long studentId, @Param("now") LocalDateTime now);

    long countByAttemptId(Long attemptId);

    void deleteAllByExamId(Long examId);

    // ✅ ADMIN list: assignedCount + openAt(min) + dueAt(max)
    @Query("""
        select a.exam.id, count(a.id), min(a.openAt), max(a.dueAt)
        from ExamAssignment a
        where a.exam.id in :examIds
        group by a.exam.id
    """)
    List<Object[]> aggByExamIds(@Param("examIds") List<Long> examIds);

    // =========================================================
    // ✅ FIX LazyInitialization for admin detail:
    // fetch student + attempt (and optionally assignedBy) in ONE query
    // =========================================================

    @Query("""
        select ea
        from ExamAssignment ea
        join fetch ea.student s
        left join fetch ea.attempt at
        where ea.exam.id = :examId
        order by ea.createdAt desc
    """)
    List<ExamAssignment> findAllByExamIdFetchStudentAndAttempt(@Param("examId") Long examId);

    // Nếu admin detail cần luôn assignedBy (hiện tại getDetail chưa dùng)
    @Query("""
        select ea
        from ExamAssignment ea
        join fetch ea.student s
        join fetch ea.assignedBy ab
        left join fetch ea.attempt at
        where ea.exam.id = :examId
        order by ea.createdAt desc
    """)
    List<ExamAssignment> findAllByExamIdFetchUsersAndAttempt(@Param("examId") Long examId);
}