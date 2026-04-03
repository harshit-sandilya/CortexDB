package com.vectornode.memory.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectornode.memory.config.LLMProvider;
import com.vectornode.memory.entity.Context;
import com.vectornode.memory.entity.KnowledgeBase;
import com.vectornode.memory.entity.RagEntity;
import com.vectornode.memory.entity.Relation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coordinates the RAG ingestion pipeline.
 * Handles chunking, embedding generation, and entity/relation extraction.
 * Persists contexts, entities, and relations to database.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionWorker {

        private final ChunkingService chunkingService;
        private final ExtractionService extractionService;
        private final PageIndexService pageIndexService;
        private final ContradictionDetector contradictionDetector;
        private final com.vectornode.memory.query.repository.ContextRepository contextRepository;
        private final ObjectMapper objectMapper;

        @PersistenceContext
        private EntityManager entityManager;

        /**
         * Processes content from a prompt (SimpleMem pipeline).
         * Compresses text, checks for similar existing memories, and synthesis/inserts.
         */
        @Async
        @Transactional
        public void processKnowledgeBase(UUID kbId, String content) {
                log.info("Processing Prompt KB_CREATED for id: {}", kbId);
                long startTime = System.currentTimeMillis();

                if (content == null || content.isBlank()) {
                        log.warn("Empty content for KB: {}", kbId);
                        return;
                }

                // Get KnowledgeBase reference
                KnowledgeBase kb = entityManager.getReference(KnowledgeBase.class, kbId);

                // 1. SimpleMEM: Compress the prompt
                long chunkingStart = System.currentTimeMillis();
                ChunkingService.CompressedChunk compressed = chunkingService.compressPrompt(content);
                long chunkingTime = System.currentTimeMillis() - chunkingStart;

                // Embed the restatement
                long embeddingStart = System.currentTimeMillis();
                float[] embedding = LLMProvider.getEmbedding(compressed.restatement());

                // Convert embedding to string format for postgres vector
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < embedding.length; i++) {
                        sb.append(embedding[i]);
                        if (i < embedding.length - 1)
                                sb.append(",");
                }
                sb.append("]");
                String vectorStr = sb.toString();

                // 2. Online Semantic Synthesis: Check if a highly similar chunk exists
                List<Object[]> similar = contextRepository.findHighlySimilar(vectorStr, 0.85);

                if (!similar.isEmpty()) {
                        // MERGE FLOW (Synthesis)
                        Object[] match = similar.get(0);
                        UUID existingId = (UUID) match[0];
                        String existingText = (String) match[1];

                        log.info("SYNTHESIS TRIGGERED | matched_context_id={} | new_fact='{}' | existing_fact='{}'",
                                        existingId, compressed.restatement(), existingText);

                        String mergePrompt = """
                                        Merge the following two episodic facts into a single, concise, logically consistent factual restatement.
                                        Resolve any contradictions by preferring the Newer Fact (it is more recent).

                                        Existing Fact: "%s"
                                        Newer Fact: "%s"

                                        Output ONLY the text of the single merged fact.
                                        """
                                        .formatted(existingText, compressed.restatement());

                        String mergedText = LLMProvider.callLLM(mergePrompt).trim();
                        float[] mergedEmbedding = LLMProvider.getEmbedding(mergedText);

                        Context existingContext = entityManager.find(Context.class, existingId);
                        existingContext.setTextChunk(mergedText);
                        existingContext.setVectorEmbedding(mergedEmbedding);

                        // Update metadata with new keywords
                        com.fasterxml.jackson.databind.node.ObjectNode meta = (com.fasterxml.jackson.databind.node.ObjectNode) existingContext
                                        .getMetadata();
                        if (meta == null)
                                meta = objectMapper.createObjectNode();

                        com.fasterxml.jackson.databind.node.ArrayNode keywordsNode = meta.putArray("keywords");
                        compressed.keywords().forEach(keywordsNode::add);

                        meta.put("topic", compressed.topic());
                        meta.put("lastMergedTimestamp", compressed.timestamp());
                        meta.put("synthesisCount",
                                        meta.has("synthesisCount") ? meta.get("synthesisCount").asInt() + 1 : 1);

                        existingContext.setMetadata(meta);
                        entityManager.merge(existingContext);
                        entityManager.flush();

                        log.info("CONTEXT_MERGED | id={} | new_text_length={} | synthesis_count={}",
                                        existingContext.getId(), mergedText.length(),
                                        meta.get("synthesisCount").asInt());

                        // Contradiction detection: merged text might contradict something the original
                        // didn't
                        contradictionDetector.checkAndPersistAsync(
                                        mergedText, mergedEmbedding, existingContext.getId());

                        // Mark old contradictions on this context as resolved (merge is the resolution)
                        contradictionDetector.markResolvedAsync(
                                        existingContext.getId(), existingContext.getId());

                        // Dispatch for re-extraction of entities on the merged text
                        processContext(existingId, kbId, mergedText);

                } else {
                        // INSERT FLOW
                        Context context = Context.builder()
                                        .knowledgeBase(kb)
                                        .textChunk(compressed.restatement())
                                        .vectorEmbedding(embedding)
                                        .chunkIndex(0)
                                        .build();

                        // Add metadata for SimpleMem
                        context.setMetadata(objectMapper.createObjectNode()
                                        .put("topic", compressed.topic())
                                        .put("timestamp", compressed.timestamp())
                                        .put("chunkLength", compressed.restatement().length())
                                        .putPOJO("keywords", compressed.keywords()));

                        entityManager.persist(context);
                        entityManager.flush(); // ensure ID is generated

                        log.info("CONTEXT_INSERTED | id={} | kb_id={} | topic={} | keywords={}",
                                        context.getId(),
                                        context.getKnowledgeBase().getId(),
                                        compressed.topic(),
                                        compressed.keywords());

                        // Contradiction detection: fire-and-forget with real ID
                        contradictionDetector.checkAndPersistAsync(
                                        compressed.restatement(), embedding, context.getId());

                        // Dispatch for entity extraction
                        processContext(context.getId(), kbId, compressed.restatement());
                }

                long totalTime = System.currentTimeMillis() - startTime;
                log.info("Prompt KB {} processing complete: totalTime={}ms", kbId, totalTime);
        }

        /**
         * Processes a context chunk for entity/relation extraction.
         * Extracts and persists entities and relations.
         */
        @Async
        @Transactional
        public void processContext(UUID contextId, UUID kbId, String textChunk) {
                log.info("Processing CONTEXT_CREATED for id: {}, kbId: {}", contextId, kbId);
                long startTime = System.currentTimeMillis();

                if (textChunk == null || textChunk.isBlank()) {
                        log.warn("Empty text chunk for context: {}", contextId);
                        return;
                }

                // Get Context reference
                Context context = entityManager.getReference(Context.class, contextId);

                // 1. Extract entities & relations via LLM
                ExtractionService.ExtractionResult result = extractionService.extractFromText(textChunk);

                // 2. Persist extracted entities
                Map<String, RagEntity> entityMap = new HashMap<>();
                for (ExtractionService.ExtractedEntity extractedEntity : result.getEntities()) {
                        float[] embedding = LLMProvider.getEmbedding(
                                        extractedEntity.getName() + " " + extractedEntity.getDescription());

                        // Check if entity already exists by name
                        List<RagEntity> existing = entityManager
                                        .createQuery("SELECT e FROM RagEntity e WHERE e.name = :name", RagEntity.class)
                                        .setParameter("name", extractedEntity.getName())
                                        .setMaxResults(1)
                                        .getResultList();

                        RagEntity entity;
                        if (!existing.isEmpty()) {
                                entity = existing.get(0);
                                log.info("ENTITY_EXISTS | id={} | name={}", entity.getId(), entity.getName());
                        } else {
                                entity = RagEntity.builder()
                                                .name(extractedEntity.getName())
                                                .type(extractedEntity.getType())
                                                .description(extractedEntity.getDescription())
                                                .vectorEmbedding(embedding)
                                                .build();

                                // Add metadata
                                entity.setMetadata(objectMapper.createObjectNode()
                                                .put("extractedFrom", "context")
                                                .put("contextId", contextId.toString())
                                                .put("embeddingDimensions", embedding.length)
                                                .put("descriptionLength",
                                                                extractedEntity.getDescription() != null
                                                                                ? extractedEntity.getDescription()
                                                                                                .length()
                                                                                : 0));

                                entityManager.persist(entity);

                                // Log the complete persisted row
                                log.info("ENTITY_ROW | id={} | name={} | type={} | description_length={} | vector_dims={} | metadata={} | created_at={}",
                                                entity.getId(),
                                                entity.getName(),
                                                entity.getType(),
                                                entity.getDescription() != null ? entity.getDescription().length() : 0,
                                                entity.getVectorEmbedding().length,
                                                entity.getMetadata(),
                                                entity.getCreatedAt());
                        }

                        // Link entity to context (persists to entity_context_junction table)
                        entity.getContexts().add(context);
                        entityManager.merge(entity);

                        log.info("JUNCTION_ROW | entity_id={} | context_id={}", entity.getId(), context.getId());
                        entityMap.put(entity.getName(), entity);
                }

                // 3. Persist extracted relations (with upsert logic for edge weight)
                for (ExtractionService.ExtractedRelation extractedRelation : result.getRelations()) {
                        RagEntity sourceEntity = entityMap.get(extractedRelation.getSourceName());
                        RagEntity targetEntity = entityMap.get(extractedRelation.getTargetName());

                        if (sourceEntity != null && targetEntity != null) {
                                // Check if relation already exists
                                List<Relation> existingRelations = entityManager.createQuery(
                                                "SELECT r FROM Relation r WHERE r.sourceEntity.id = :sourceId " +
                                                                "AND r.targetEntity.id = :targetId " +
                                                                "AND r.relationType = :relationType",
                                                Relation.class)
                                                .setParameter("sourceId", sourceEntity.getId())
                                                .setParameter("targetId", targetEntity.getId())
                                                .setParameter("relationType", extractedRelation.getRelationType())
                                                .getResultList();

                                Relation relation;
                                boolean isNew = existingRelations.isEmpty();

                                if (isNew) {
                                        // Create new relation with edge_weight = 1
                                        relation = Relation.builder()
                                                        .sourceEntity(sourceEntity)
                                                        .targetEntity(targetEntity)
                                                        .relationType(extractedRelation.getRelationType())
                                                        .edgeWeight(1)
                                                        .build();

                                        relation.setMetadata(objectMapper.createObjectNode()
                                                        .put("extractedFrom", "context")
                                                        .put("contextId", contextId.toString())
                                                        .put("edgeWeight", 1));

                                        entityManager.persist(relation);

                                        log.info("RELATION_NEW | id={} | source={} | target={} | type={} | edge_weight=1",
                                                        relation.getId(),
                                                        sourceEntity.getName(),
                                                        targetEntity.getName(),
                                                        relation.getRelationType());
                                } else {
                                        // Increment edge_weight on existing relation
                                        relation = existingRelations.get(0);
                                        int newWeight = relation.getEdgeWeight() + 1;
                                        relation.setEdgeWeight(newWeight);
                                        entityManager.merge(relation);

                                        log.info("RELATION_INCREMENT | id={} | source={} | target={} | type={} | edge_weight={}",
                                                        relation.getId(),
                                                        sourceEntity.getName(),
                                                        targetEntity.getName(),
                                                        relation.getRelationType(),
                                                        newWeight);
                                }
                        } else {
                                log.warn("RELATION_SKIPPED | relation_type={} | reason=source_or_target_not_found",
                                                extractedRelation.getRelationType());
                        }
                }

                entityManager.flush();

                entityManager.flush();

                // ExtractionService.ExtractedMetadata metadata = result.getMetadata();
                // log.info("Ingestion completed");
        }

        /**
         * Processes a large document: generates a hierarchical tree,
         * persists each node as a Context, and links them via relations.
         */
        @Async
        @Transactional
        public void processDocumentTree(UUID kbId, String documentText) {
                log.info("Processing DOCUMENT for KB id: {}", kbId);
                long startTime = System.currentTimeMillis();

                if (documentText == null || documentText.isBlank()) {
                        log.warn("Empty document text for KB: {}", kbId);
                        return;
                }

                // Get KnowledgeBase reference
                KnowledgeBase kb = entityManager.getReference(KnowledgeBase.class, kbId);

                // 1. Generate Document Tree
                PageIndexService.DocumentNode rootNode = pageIndexService.generateDocumentTree(documentText);

                // 2. Recursively save the tree
                saveDocumentNode(rootNode, kb, null, 0);

                long totalTime = System.currentTimeMillis() - startTime;
                log.info("DOCUMENT {} processing complete: totalTime={}ms", kbId, totalTime);
        }

        /**
         * Recursively saves a DocumentNode and its children.
         * Creates "HAS_SUBSECTION" relations between parent and child contexts.
         */
        private Context saveDocumentNode(PageIndexService.DocumentNode node, KnowledgeBase kb, Context parentContext,
                        int depth) {
                // Generate embedding for the node's content
                float[] embedding = LLMProvider.getEmbedding(node.getContent());

                // Create Context for this node
                Context context = Context.builder()
                                .knowledgeBase(kb)
                                .textChunk(node.getContent())
                                .vectorEmbedding(embedding)
                                .chunkIndex(depth) // using chunkIndex to represent depth here
                                .build();

                // Add metadata for PageIndex
                context.setMetadata(objectMapper.createObjectNode()
                                .put("title", node.getTitle())
                                .put("summary", node.getSummary())
                                .put("depth", depth)
                                .put("type", "page_index_node"));

                entityManager.persist(context);
                entityManager.flush(); // Need ID for relations

                log.info("PAGE_INDEX_NODE | id={} | title={} | depth={}", context.getId(), node.getTitle(), depth);

                // Create relationship if there is a parent
                if (parentContext != null) {
                        // In the document pipeline, we map relations between contexts directly or
                        // between placeholder entities representing the contexts.
                        // For simplicity in CortexDB's entity-relation model, we create dummy
                        // RagEntities
                        // to represent the sections, or directly link them if the schema allows.
                        // Currently Relation links RagEntity to RagEntity.
                        // Let's create an entity representing this document section to link.

                        RagEntity parentEntity = getOrCreateSectionEntity(parentContext, kb.getUid());
                        RagEntity childEntity = getOrCreateSectionEntity(context, kb.getUid());

                        Relation relation = Relation.builder()
                                        .sourceEntity(parentEntity)
                                        .targetEntity(childEntity)
                                        .relationType("HAS_SUBSECTION")
                                        .edgeWeight(1)
                                        .build();

                        relation.setMetadata(objectMapper.createObjectNode()
                                        .put("extractedFrom", "page_index")
                                        .put("parentContextId", parentContext.getId().toString())
                                        .put("childContextId", context.getId().toString()));

                        entityManager.persist(relation);
                        log.info("RELATION_NEW | parent={} | child={} | type=HAS_SUBSECTION", parentEntity.getName(),
                                        childEntity.getName());
                }

                // Recursively process children
                if (node.getChildren() != null) {
                        for (PageIndexService.DocumentNode child : node.getChildren()) {
                                saveDocumentNode(child, kb, context, depth + 1);
                        }
                }

                return context;
        }

        private RagEntity getOrCreateSectionEntity(Context context, String kbUid) {
                String sectionName = "Section_" + context.getId().toString().substring(0, 8);
                String title = context.getMetadata().has("title") ? context.getMetadata().get("title").asText()
                                : sectionName;

                // Check if exists
                List<RagEntity> existing = entityManager
                                .createQuery("SELECT e FROM RagEntity e WHERE e.name = :name", RagEntity.class)
                                .setParameter("name", title)
                                .setMaxResults(1)
                                .getResultList();

                if (!existing.isEmpty()) {
                        return existing.get(0);
                }

                RagEntity entity = RagEntity.builder()
                                .name(title)
                                .type("DOCUMENT_SECTION")
                                .description("Section from document " + kbUid)
                                .vectorEmbedding(context.getVectorEmbedding())
                                .build();

                entity.setMetadata(objectMapper.createObjectNode()
                                .put("isSectionEntity", true)
                                .put("contextId", context.getId().toString()));

                entityManager.persist(entity);

                // Link to context
                entity.getContexts().add(context);
                entityManager.merge(entity);

                return entity;
        }
}
