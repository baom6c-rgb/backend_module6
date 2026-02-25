package com.be_ai_learning_platform.dto.response;

import com.be_ai_learning_platform.entity.enums.ExamResult;

public class SubmitPracticeResponse {
    private Integer score;          // % 0..100
    private Integer earnedPoints;   // tổng điểm đạt
    private Integer totalPoints;    // tổng điểm tối đa
    private ExamResult status;      // PASSED / FAILED
    private Boolean timedOut;       // nộp do hết giờ hay không

    private String feedback;        // rule-based summary
    private String aiFeedback;
    private String studyGuide;      // ✅ NEW: gợi ý ôn tập (AI request riêng)      // AI nhận xét tổng (sai ở đâu, gợi ý học lại)

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public Integer getEarnedPoints() { return earnedPoints; }
    public void setEarnedPoints(Integer earnedPoints) { this.earnedPoints = earnedPoints; }

    public Integer getTotalPoints() { return totalPoints; }
    public void setTotalPoints(Integer totalPoints) { this.totalPoints = totalPoints; }

    public ExamResult getStatus() { return status; }
    public void setStatus(ExamResult status) { this.status = status; }

    public Boolean getTimedOut() { return timedOut; }
    public void setTimedOut(Boolean timedOut) { this.timedOut = timedOut; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public String getAiFeedback() { return aiFeedback; }
    public void setAiFeedback(String aiFeedback) { this.aiFeedback = aiFeedback; }

    public String getStudyGuide() { return studyGuide; }
    public void setStudyGuide(String studyGuide) { this.studyGuide = studyGuide; }

}
