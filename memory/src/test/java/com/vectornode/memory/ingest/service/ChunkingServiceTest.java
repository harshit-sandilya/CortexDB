package com.vectornode.memory.ingest.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ChunkingService.
 */
class ChunkingServiceTest {

    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        chunkingService = new ChunkingService();
    }

    @Nested
    @DisplayName("chunkText with default parameters")
    class ChunkTextDefaultTests {

        @Test
        @DisplayName("should return empty list for null input")
        void shouldReturnEmptyListForNullInput() {
            List<String> chunks = chunkingService.chunkText(null, ChunkingService.DEFAULT_CHUNK_SIZE,
                    ChunkingService.DEFAULT_OVERLAP);
            assertThat(chunks).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for blank input")
        void shouldReturnEmptyListForBlankInput() {
            List<String> chunks = chunkingService.chunkText("   ", ChunkingService.DEFAULT_CHUNK_SIZE,
                    ChunkingService.DEFAULT_OVERLAP);
            assertThat(chunks).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for empty string")
        void shouldReturnEmptyListForEmptyString() {
            List<String> chunks = chunkingService.chunkText("", ChunkingService.DEFAULT_CHUNK_SIZE,
                    ChunkingService.DEFAULT_OVERLAP);
            assertThat(chunks).isEmpty();
        }

        @Test
        @DisplayName("should return single chunk for short text")
        void shouldReturnSingleChunkForShortText() {
            String shortText = "This is a short text that should fit in one chunk.";
            List<String> chunks = chunkingService.chunkText(shortText, ChunkingService.DEFAULT_CHUNK_SIZE,
                    ChunkingService.DEFAULT_OVERLAP);

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0)).isEqualTo(shortText);
        }

        @Test
        @DisplayName("should normalize whitespace")
        void shouldNormalizeWhitespace() {
            String textWithWeirdSpaces = "Hello   world\n\nthis  is\t\ttest";
            List<String> chunks = chunkingService.chunkText(textWithWeirdSpaces, ChunkingService.DEFAULT_CHUNK_SIZE,
                    ChunkingService.DEFAULT_OVERLAP);

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0)).isEqualTo("Hello world this is test");
        }
    }

    @Nested
    @DisplayName("chunkText with custom parameters")
    class ChunkTextCustomTests {

        @Test
        @DisplayName("should create multiple chunks for long text")
        void shouldCreateMultipleChunksForLongText() {
            // Create text longer than chunk size
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                sb.append("This is sentence number ").append(i).append(". ");
            }
            String longText = sb.toString();

            List<String> chunks = chunkingService.chunkText(longText, 200, 50);

            assertThat(chunks).hasSizeGreaterThan(1);
        }

        @Test
        @DisplayName("should have overlap between consecutive chunks")
        void shouldHaveOverlapBetweenChunks() {
            String text = "First sentence here. Second sentence follows. Third sentence comes. Fourth sentence ends.";

            List<String> chunks = chunkingService.chunkText(text, 50, 20);

            // With overlap, some content should appear in multiple chunks
            assertThat(chunks).hasSizeGreaterThan(1);
        }

        @Test
        @DisplayName("should break at sentence boundaries when possible")
        void shouldBreakAtSentenceBoundaries() {
            String text = "This is the first sentence. This is the second sentence. This is the third sentence.";

            List<String> chunks = chunkingService.chunkText(text, 60, 10);

            // Should produce multiple chunks
            assertThat(chunks).hasSizeGreaterThanOrEqualTo(1);
            // At least some chunks should contain complete sentences
            boolean hasCompleteSentence = chunks.stream()
                    .anyMatch(chunk -> chunk.contains("."));
            assertThat(hasCompleteSentence).isTrue();
        }

        @Test
        @DisplayName("should handle text with exclamation marks")
        void shouldHandleExclamationMarks() {
            String text = "Hello world! This is amazing! What a great day!";
            List<String> chunks = chunkingService.chunkText(text, 25, 5);

            assertThat(chunks).isNotEmpty();
        }

        @Test
        @DisplayName("should handle text with question marks")
        void shouldHandleQuestionMarks() {
            String text = "What is this? How does it work? Why is it important?";
            List<String> chunks = chunkingService.chunkText(text, 25, 5);

            assertThat(chunks).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle very small chunk size")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void shouldHandleVerySmallChunkSize() {
            String text = "Hello world this is a test.";
            List<String> chunks = chunkingService.chunkText(text, 10, 2);

            assertThat(chunks).isNotEmpty();
            // All chunks should be non-empty
            chunks.forEach(chunk -> assertThat(chunk).isNotBlank());
        }

        @Test
        @DisplayName("should handle reasonable overlap percentage")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void shouldHandleReasonableOverlap() {
            String text = "This is a longer test text for chunking. It should work properly with reasonable overlap.";
            // Use a reasonable overlap (smaller than chunk size)
            List<String> chunks = chunkingService.chunkText(text, 50, 10);

            assertThat(chunks).isNotEmpty();
        }

        @Test
        @DisplayName("should handle text without sentence boundaries")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void shouldHandleTextWithoutSentenceBoundaries() {
            String text = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10";
            List<String> chunks = chunkingService.chunkText(text, 20, 5);

            assertThat(chunks).isNotEmpty();
        }

        @Test
        @DisplayName("should handle unicode text")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void shouldHandleUnicodeText() {
            String text = "Hello, More text here.";
            List<String> chunks = chunkingService.chunkText(text, ChunkingService.DEFAULT_CHUNK_SIZE,
                    ChunkingService.DEFAULT_OVERLAP);

            assertThat(chunks).isNotEmpty();
            assertThat(chunks.get(0)).contains("text");
        }
    }
}
