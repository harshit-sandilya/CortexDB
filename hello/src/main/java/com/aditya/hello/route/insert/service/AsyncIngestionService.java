package com.aditya.hello.route.insert.service;

import com.aditya.hello.config.PostgresNotificationEvent;
import com.aditya.hello.entity.Context;
import com.aditya.hello.entity.KnowledgeBase;
import com.aditya.hello.repository.ContextRepository;
import com.aditya.hello.repository.KnowledgeBaseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AsyncIngestionService {

    private final KnowledgeBaseRepository kbRepo;
    private final ContextRepository contextRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @EventListener
    @Transactional
    public void handleKbCreated(PostgresNotificationEvent event) {
        if (!"new_kb_entry".equals(event.getChannel()))
            return;

        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            String kbIdStr = payload.get("id").asText();
            UUID kbId = UUID.fromString(kbIdStr);
            String queryText = payload.get("queryText").asText();

            KnowledgeBase kb = kbRepo.findById(kbId).orElseThrow();

            // Perform Chunking
            List<String> chunks = splitIntoChunks(queryText);

            for (String chunkText : chunks) {
                Context context = Context.builder()
                        .knowledgeBase(kb)
                        .contextData(chunkText)
                        .vectorEmbedding(generateEmbedding(chunkText))
                        .metadata("{}")
                        .build();
                contextRepo.save(context);
                // This save triggers 'after_context_insert' which signals the next step
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        int chunkSize = 200;
        if (text == null)
            return chunks;
        for (int i = 0; i < text.length(); i += chunkSize) {
            chunks.add(text.substring(i, Math.min(i + chunkSize, text.length())));
        }
        return chunks;
    }

    private float[] generateEmbedding(String text) {
        return new float[1536];
    }
}
