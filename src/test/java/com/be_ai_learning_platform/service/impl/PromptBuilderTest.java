package com.be_ai_learning_platform.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilder();
    }

    @Test
    @DisplayName("buildPrompt: Phải chứa đầy đủ các thông tin quan trọng")
    void buildPrompt_ShouldContainBasicInfo() {
        String material = "Nội dung bài học về Java";
        int totalQuestions = 5;

        String result = promptBuilder.buildPrompt(material, totalQuestions);

        assertThat(result).contains("Hệ thống tạo đề luyện tập");
        assertThat(result).contains(material);
        assertThat(result).contains("MCQ");
        assertThat(result).contains("ESSAY");
    }

    @ParameterizedTest
    @DisplayName("Kiểm tra logic tính toán số lượng MCQ và Essay theo tỉ lệ 30%")
    @CsvSource({
            "1, 0, 1",   // 1 câu: 0 MCQ, 1 Essay (vì min Essay = 1)
            "3, 2, 1",   // 3 câu: 3*0.3=0.9 -> làm tròn 1 Essay, 2 MCQ
            "10, 7, 3",  // 10 câu: 3 Essay, 7 MCQ
            "5, 3, 2"    // 5 câu: 5*0.3=1.5 -> làm tròn 2 Essay, 3 MCQ
    })
    void buildPrompt_ShouldCalculateCorrectRatios(int total, int expectedMcq, int expectedEssay) {
        String result = promptBuilder.buildPrompt("Material", total);

        // Kiểm tra xem chuỗi có chứa text xác nhận số lượng câu hỏi không
        assertThat(result).contains(String.format("Tạo CHÍNH XÁC %d câu hỏi", total));
        assertThat(result).contains(String.format("%d câu TRẮC NGHIỆM", expectedMcq));
        assertThat(result).contains(String.format("%d câu TỰ LUẬN", expectedEssay));
    }

    @Test
    @DisplayName("buildRetestPrompt: Phải chứa phần WEAK AREAS")
    void buildRetestPrompt_ShouldIncludeFocusText() {
        String material = "Văn học Việt Nam";
        String weakAreas = "Học sinh yếu phần tác giả";

        String result = promptBuilder.buildRetestPrompt(material, 5, weakAreas);

        assertThat(result).contains("MỤC TIÊU RETEST");
        assertThat(result).contains("WEAK AREAS");
        assertThat(result).contains(weakAreas);
    }

    @Test
    @DisplayName("buildRetestPrompt: Phải cắt ngắn focusText nếu quá 2500 ký tự")
    void buildRetestPrompt_ShouldTruncateFocusText() {
        String longFocus = "A".repeat(3000);
        String material = "Material";

        String result = promptBuilder.buildRetestPrompt(material, 5, longFocus);

        // Kiểm tra xem chuỗi có chứa đúng 2500 ký tự A từ focusText không
        String expectedSnippet = "A".repeat(2500);
        assertThat(result).contains(expectedSnippet);
        assertThat(result).doesNotContain("A".repeat(2501));
    }

    @Test
    @DisplayName("buildRetestPrompt: Xử lý focusText null bằng chuỗi rỗng")
    void buildRetestPrompt_ShouldHandleNullFocus() {
        String result = promptBuilder.buildRetestPrompt("Material", 5, null);

        assertThat(result).isNotNull();
        assertThat(result).contains("WEAK AREAS");
    }
}