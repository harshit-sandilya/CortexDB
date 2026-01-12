package com.aditya.hello.route.query.service;

import com.aditya.hello.dto.QueryRequest;
import com.aditya.hello.entity.Context;
import com.aditya.hello.entity.KnowledgeBase;
import com.aditya.hello.repository.ContextRepository;
import com.aditya.hello.repository.KnowledgeBaseRepository;
import com.aditya.hello.repository.RelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueryService {

    private final KnowledgeBaseRepository kbRepo;
    private final ContextRepository contextRepo;
    private final RelationRepository relationRepo;

    public List<Context> findSimilarContexts(float[] vector, int limit) {
        // defined in PGVector as [1,2,3], we need to format it as string for the native
        // query cast
        String vectorString = Arrays.toString(vector);
        return contextRepo.findSimilarContexts(vectorString, limit);
    }

    public List<KnowledgeBase> fetchByUserId(String userId) {
        return kbRepo.findByUserId(userId);
    }

    public List<KnowledgeBase> fetchByTime(Instant start, Instant end) {
        return kbRepo.findByTimestampBetween(start, end);
    }

    public List<UUID> findRelatedEntities(UUID startEntityId, int depth) {
        return relationRepo.findConnectedEntityIds(startEntityId, depth);
    }
}
