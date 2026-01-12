package com.aditya.hello.route.insert.service;

import com.aditya.hello.dto.IngestionRequest;
import com.aditya.hello.entity.KnowledgeBase;
import com.aditya.hello.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InsertService {

    private final KnowledgeBaseRepository kbRepo;

    @Transactional
    public String ingestData(IngestionRequest request) {
        // 1. Store in KnowledgeBase
        // This insert triggers 'after_kb_insert', which notifies AsyncIngestionService
        KnowledgeBase kb = KnowledgeBase.builder()
                .userId(request.getUserId())
                .queryText(request.getText())
                // vectorEmbedding can be null initially or generated here if fast enough
                .metadata(request.getMetadata())
                .build();
        kb = kbRepo.save(kb);

        return kb.getId().toString();
    }
}
