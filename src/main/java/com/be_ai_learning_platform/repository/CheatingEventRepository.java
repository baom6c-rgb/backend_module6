package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.CheatingEvent;
import com.be_ai_learning_platform.entity.enums.CheatingEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface CheatingEventRepository extends JpaRepository<CheatingEvent, Long> {

    List<CheatingEvent> findAllByAssignmentIdOrderByCreatedAtDesc(Long assignmentId);

    @Query("""
            select ce
            from CheatingEvent ce
            join fetch ce.assignment a
            join fetch a.exam e
            join fetch ce.student s
            where e.id = :examId
            order by ce.createdAt desc
            """)
    List<CheatingEvent> findAllByExamIdFetch(@Param("examId") Long examId);

    long countByAssignmentIdAndTypeAndCreatedAtAfter(Long assignmentId, CheatingEventType type, LocalDateTime after);

    long countByAssignmentIdAndCreatedAtAfter(Long assignmentId, LocalDateTime after);

    void deleteAllByAssignmentId(Long assignmentId);

    @Modifying
    @Transactional
    @Query("""
            delete from CheatingEvent ce
            where ce.assignment.id in :assignmentIds
            """)
    int deleteAllByAssignmentIds(@Param("assignmentIds") List<Long> assignmentIds);
}
