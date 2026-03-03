package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {

    List<ExamQuestion> findAllByExam_Id(Long examId);

    void deleteAllByExamId(Long examId);

    @Query("""
        select eq.question.id
        from ExamQuestion eq
        where eq.exam.id = :examId
    """)
    List<Long> findQuestionIdsByExamId(@Param("examId") Long examId);
    @Query("""
    select eq
    from ExamQuestion eq
    join fetch eq.question q
    where eq.exam.id = :examId
    order by eq.id
""")
    List<ExamQuestion> findAllByExamIdFetchQuestion(@Param("examId") Long examId);

}
