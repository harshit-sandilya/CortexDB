package com.aditya.hello.route.insert.service;

import com.aditya.hello.config.PostgresNotificationEvent;
import com.aditya.hello.entity.Context;
import com.aditya.hello.entity.RagEntity;
import com.aditya.hello.entity.Relation;
import com.aditya.hello.repository.ContextRepository;
import com.aditya.hello.repository.RagEntityRepository;
import com.aditya.hello.repository.RelationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EntityExtractionService {

    private final ContextRepository contextRepo;
    private final RagEntityRepository entityRepo;
    private final RelationRepository relationRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Reusing the record definitions
    record ExtractedData(List<String> entities, List<RelationTriple> relations) {
    }

    record RelationTriple(String source, String target, String type) {
    }

    @EventListener
    @Transactional
    public void handleContextCreated(PostgresNotificationEvent event) {
        if (!"new_context_entry".equals(event.getChannel()))
            return;

        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            String contextIdStr = payload.get("uniqueContextId").asText();
            UUID contextId = UUID.fromString(contextIdStr);
            String contextData = payload.get("contextData").asText();

            Context context = contextRepo.findById(contextId).orElseThrow();

            // Extract Entities & Relations
            ExtractedData data = extractEntitiesAndRelations(contextData);

            // Process Entities
            for (String entityName : data.entities()) {
                RagEntity entity = entityRepo.findByEntityName(entityName)
                        .orElseGet(() -> entityRepo.save(RagEntity.builder()
                                .entityName(entityName)
                                .vectorEmbedding(new float[1536])
                                .metadata("{}")
                                .build()));

                if (entity.getContexts() == null)
                    entity.setContexts(new ArrayList<>());
                entity.getContexts().add(context);
                entityRepo.save(entity);
            }

            // Process Relations
            for (RelationTriple triple : data.relations()) {
                RagEntity source = entityRepo.findByEntityName(triple.source()).orElse(null);
                RagEntity target = entityRepo.findByEntityName(triple.target()).orElse(null);

                if (source != null && target != null) {
                    Optional<Relation> existingRel = relationRepo
                            .findBySourceEntityAndTargetEntityAndRelationType(source, target, triple.type());

                    if (existingRel.isPresent()) {
                        Relation rel = existingRel.get();
                        rel.setEdgeWeight(rel.getEdgeWeight() + 1);
                        relationRepo.save(rel);
                    } else {
                        Relation rel = Relation.builder()
                                .sourceEntity(source)
                                .targetEntity(target)
                                .relationType(triple.type())
                                .edgeWeight(1)
                                .metadata("{}")
                                .build();
                        relationRepo.save(rel);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ExtractedData extractEntitiesAndRelations(String contextData) {
        // TODO: Call LLM for entity extraction and relation extraction
        return new ExtractedData(new ArrayList<>(), new ArrayList<>());
    }
}
