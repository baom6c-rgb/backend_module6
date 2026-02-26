package com.be_ai_learning_platform.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PromptBuilderTest {

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilder();
    }

    @Nested
    @DisplayName("buildPrompt (Normal practice)")
    class BuildPromptTests {

        @Test
        @DisplayName("Phải chứa các section quan trọng và embed đúng material")
        void shouldContainCoreSectionsAndMaterial() {
            // given
            String material = "Nội dung bài học về Java";
            int mcq = 3;
            int essay = 2;

            // when
            String result = promptBuilder.buildPrompt(material, mcq, essay);

            // then
            assertThat(result).contains("Bạn là hệ thống tạo đề luyện tập");
            assertThat(result).contains("NHIỆM VỤ:");
            assertThat(result).contains("YÊU CẦU OUTPUT:");
            assertThat(result).contains("SCHEMA JSON");
            assertThat(result).contains("TÀI LIỆU:");
            assertThat(result).contains(material);

            // counts
            assertThat(result).contains("Tạo CHÍNH XÁC 5 câu hỏi");
            assertThat(result).contains("3 câu TRẮC NGHIỆM");
            assertThat(result).contains("2 câu TỰ LUẬN NGẮN");
        }

        @Test
        @DisplayName("Backward-compatible: buildPrompt(material, mcq, essay) không có FOCUS block")
        void backwardCompatibleShouldNotIncludeFocusBlock() {
            // given
            String material = "Material";

            // when
            String result = promptBuilder.buildPrompt(material, 2, 1);

            // then
            assertThat(result).doesNotContain("FOCUS AREAS (ƯU TIÊN BẮT BUỘC):");
        }

        @Test
        @DisplayName("Có focusText: phải render FOCUS AREAS block và embed focus text")
        void withFocus_shouldIncludeFocusBlock() {
            // given
            String material = "Material";
            String focus = "Chỉ tập trung vào vòng lặp for và while";

            // when
            String result = promptBuilder.buildPrompt(material, 4, 2, focus);

            // then
            assertThat(result).contains("FOCUS AREAS (ƯU TIÊN BẮT BUỘC):");
            assertThat(result).contains(focus);
        }

        @Test
        @DisplayName("focusText null/blank: không render FOCUS block")
        void nullOrBlankFocus_shouldNotIncludeFocusBlock() {
            // given
            String material = "Material";

            // when
            String r1 = promptBuilder.buildPrompt(material, 1, 1, null);
            String r2 = promptBuilder.buildPrompt(material, 1, 1, "   ");

            // then
            assertThat(r1).doesNotContain("FOCUS AREAS (ƯU TIÊN BẮT BUỘC):");
            assertThat(r2).doesNotContain("FOCUS AREAS (ƯU TIÊN BẮT BUỘC):");
        }

        @ParameterizedTest(name = "mcq={0}, essay={1} => total={2}")
        @DisplayName("Tổng câu hỏi phải = mcq + essay; count âm phải clamp về 0")
        @CsvSource({
                "0, 0, 0",
                "1, 0, 1",
                "0, 2, 2",
                "3, 2, 5",
                "-2, 4, 4",
                "5, -7, 5",
                "-3, -9, 0"
        })
        void shouldComputeTotalAndClampNegativeCounts(int mcq, int essay, int expectedTotal) {
            // given
            String material = "Material";

            // when
            String result = promptBuilder.buildPrompt(material, mcq, essay);

            // then
            assertThat(result).contains("Tạo CHÍNH XÁC " + expectedTotal + " câu hỏi");

            int expectedMcq = Math.max(0, mcq);
            int expectedEssay = Math.max(0, essay);

            assertThat(result).contains(expectedMcq + " câu TRẮC NGHIỆM");
            assertThat(result).contains(expectedEssay + " câu TỰ LUẬN NGẮN");
        }

        @Test
        @DisplayName("Material > 6000 ký tự: phải bị cắt đúng 6000 (không chứa ký tự thứ 6001)")
        void materialShouldBeTruncatedTo6000() {
            // given
            String material = "A".repeat(6500);

            // when
            String result = promptBuilder.buildPrompt(material, 1, 0);

            // then
            String expected6000 = "A".repeat(6000);
            assertThat(result).contains(expected6000);
            assertThat(result).doesNotContain("A".repeat(6001));
        }

        @Test
        @DisplayName("Focus > 2500 ký tự: phải bị cắt đúng 2500")
        void focusShouldBeTruncatedTo2500() {
            // given
            String material = "Material";
            String focus = "B".repeat(3000);

            // when
            String result = promptBuilder.buildPrompt(material, 1, 0, focus);

            // then
            String expected2500 = "B".repeat(2500);
            assertThat(result).contains("FOCUS AREAS (ƯU TIÊN BẮT BUỘC):");
            assertThat(result).contains(expected2500);
            assertThat(result).doesNotContain("B".repeat(2501));
        }
    }

    @Nested
    @DisplayName("buildRetestPrompt (Retest)")
    class BuildRetestPromptTests {

        @Test
        @DisplayName("Phải chứa MỤC TIÊU RETEST + WEAK AREAS và embed focusText")
        void shouldContainRetestGoalAndWeakAreas() {
            // given
            String material = "Tài liệu";
            String weakAreas = "Sai phần @Transactional và propagation";

            // when
            String result = promptBuilder.buildRetestPrompt(material, 3, 2, weakAreas);

            // then
            assertThat(result).contains("Bạn là hệ thống tạo đề THI LẠI (RETEST)");
            assertThat(result).contains("MỤC TIÊU RETEST:");
            assertThat(result).contains("WEAK AREAS (BẮT BUỘC bám sát):");
            assertThat(result).contains(weakAreas);

            // counts
            assertThat(result).contains("Tạo CHÍNH XÁC 5 câu hỏi");
            assertThat(result).contains("3 câu TRẮC NGHIỆM");
            assertThat(result).contains("2 câu TỰ LUẬN NGẮN");
        }

        @Test
        @DisplayName("focusText null: vẫn phải render WEAK AREAS block (nhưng rỗng)")
        void nullFocus_shouldStillRenderWeakAreasBlock() {
            // given
            String material = "Material";

            // when
            String result = promptBuilder.buildRetestPrompt(material, 1, 1, null);

            // then
            assertThat(result).contains("WEAK AREAS (BẮT BUỘC bám sát):");
        }

        @Test
        @DisplayName("Focus > 2500 ký tự: WEAK AREAS phải bị cắt đúng 2500")
        void weakAreasShouldBeTruncatedTo2500() {
            // given
            String material = "Material";
            String focus = "C".repeat(3000);

            // when
            String result = promptBuilder.buildRetestPrompt(material, 2, 0, focus);

            // then
            String expected2500 = "C".repeat(2500);
            assertThat(result).contains(expected2500);
            assertThat(result).doesNotContain("C".repeat(2501));
        }

        @Test
        @DisplayName("Material > 6000 ký tự: phải bị cắt đúng 6000")
        void retestMaterialShouldBeTruncatedTo6000() {
            // given
            String material = "D".repeat(9000);

            // when
            String result = promptBuilder.buildRetestPrompt(material, 1, 0, "focus");

            // then
            String expected6000 = "D".repeat(6000);
            assertThat(result).contains(expected6000);
            assertThat(result).doesNotContain("D".repeat(6001));
        }
    }
}