RAG_DB Schema - https://drive.google.com/file/d/1NGJTDMsEvNg7aNtKiYArZg2BVi2-E8Hf/view?usp=drive_link
queries - https://drive.google.com/file/d/1LQnRicFwOWWarIMGQ0naBOoW7kHSb7N2/view?usp=drive_link

1. Table Creation Scripts

-- 1. The Raw Input (User Queries/Docs)
CREATE TABLE knowledge_base (
id SERIAL PRIMARY KEY,
user_id INT NOT NULL, -- Managed externally by developers
content TEXT,
vector vector(1536),
metadata JSONB,
created_at TIMESTAMPTZ DEFAULT NOW()
);
-- 2. The Chunks (Splitting the input)
CREATE TABLE contexts (
id SERIAL PRIMARY KEY,
kb_id INT REFERENCES knowledge_base(id) ON DELETE CASCADE,
context_data TEXT,
vector vector(1536),
metadata JSONB,
created_at TIMESTAMPTZ DEFAULT NOW()
);
-- 3. The Concepts (Extracted Entities)
CREATE TABLE entities (
id SERIAL PRIMARY KEY,
entity_name TEXT,
type TEXT, -- Recommended for polysemy (e.g. Apple:Company vs Apple:Fruit)
vector vector(1536),
metadata JSONB,
created_at TIMESTAMPTZ DEFAULT NOW(),
UNIQUE (entity_name, type) -- Prevent duplicate concepts
);
-- 4. The Junction (Many-to-Many: Which entities appear in which chunk?)
CREATE TABLE entity_contexts (
entity_id INT REFERENCES entities(id) ON DELETE CASCADE,
context_id INT REFERENCES contexts(id) ON DELETE CASCADE,
PRIMARY KEY (entity_id, context_id)
);
-- 5. The Graph (How entities relate)
CREATE TABLE relations (
source_id INT REFERENCES entities(id),
target_id INT REFERENCES entities(id),
relation_type TEXT,
edge_weight INT DEFAULT 1,
metadata JSONB,
created_at TIMESTAMPTZ DEFAULT NOW(),
PRIMARY KEY (source_id, target_id, relation_type)
);

2. Knowledge Base Operations (The Entry Point)

INSERT QUERY - Stores the initial user query or document. Returns ID for the next steps.
Query - INSERT INTO
knowledge_base (user_id,
content, vector, metadata)
VALUES (42, 'How does
Java handle memory?',
'[0.1, ...]', '{"source":
"chat"}') RETURNING id;

FETCH BY USER - Retrieves all history for a specific external user.
Query - SELECT * FROM
knowledge_base WHERE
user_id = 42 ORDER BY
created_at DESC;

SEMANTIC SEARCH - Finds similar past queries (e.g., "Has anyone asked this before?").
Query - SELECT content, user_id
FROM knowledge_base
ORDER BY vector <=> '[Query_Vector]' LIMIT 5;

FETCH BY TIME - Gets queries from the last 24 hours.
Query - SELECT * FROM
knowledge_base WHERE
created_at > NOW() -
INTERVAL '24 hours';

DELETE USER DATA - "Right to be Forgotten" Deletes everything. Due to ON DELETE CASCADE, this will automatically wipe linked Contexts (but not Entities).
Query - DELETE FROM knowledge_base WHERE user_id = 42;

3. Context Operations (The Chunks)
Queries for managing the split parts of the main query.

INSERT CHUNK - Links a text chunk to the Knowledge Base ID. Explicitly handles created_at if you need to backfill old data (otherwise defaults to NOW).
Query - INSERT INTO contexts
(kb_id, context_data,
vector, metadata,
created_at) VALUES (101,
'Java uses GC...', '[...]',
'{"index": 0}', '2025-01-10
10:00:00') RETURNING id;

FETCH BY TIME (New) - Retrieves chunks created within a specific time window (e.g., "Last 7 Days"). Critical for Recency Bias.
Query - SELECT context_data,
created_at FROM contexts
WHERE created_at >=
NOW() - INTERVAL '7 days'
ORDER BY created_at
DESC;

TIME RANGE - Retrieves chunks from a specific date range (e.g., "What did we discuss in December?").
Query - SELECT context_data
FROM contexts WHERE
created_at BETWEEN
'2023-12-01' AND
'2023-12-31';

FETCH PARENT - Finds the original user query that generated this chunk.
Query - SELECT kb.content FROM
knowledge_base kb JOIN
contexts c ON kb.id =
c.kb_id WHERE c.id = 500;

VECTOR SEARCH (Core RAG) - Finds the most relevant chunks for a new question.
Query - SELECT context_data,
metadata FROM contexts
ORDER BY vector <=>
'[New_Question_Vector]'
LIMIT 5;

RECENT VECTORS (Hybrid) - Finds semantically similar chunks, but only if they are recent (e.g., ignoring obsolete data).
Query - SELECT context_data
FROM contexts WHERE
created_at > NOW() -
INTERVAL '30 days'
ORDER BY vector <=>
'[Query_Vector]' LIMIT 5;

FETCH SIBLINGS - Gets other chunks from the same document/query (e.g., previous/next sentence).
Query - SELECT * FROM contexts
WHERE kb_id = (SELECT
kb_id FROM contexts
WHERE id = 500) ORDER
BY id ASC;

4. Entity Operations (The Nodes)

UPSERT ENTITY (Crucial) - Inserts a new entity or returns the ID if it already exists.
Query - INSERT INTO entities
(entity_name, type, vector)
VALUES ('Java',
'Technology', '[...]') ON
CONFLICT (entity_name,
type) DO UPDATE SET
metadata = EXCLUDED.metadata
RETURNING id;

DISAMBIGUATE - Finds the correct Entity ID for "Apple" based on vector similarity to the current conversation context.
Query - SELECT id, entity_name,
type FROM entities WHERE
entity_name = 'Apple'
ORDER BY vector <=>
'[Current_Context_Vector]'
LIMIT 1;

FETCH ID - Simple lookup to get an ID by name.
Query - SELECT id FROM entities
WHERE entity_name =
'Java' AND type =
'Technology';

MERGE ENTITIES - If you find "Javascript" and "JS" are the same, you update references (complex logic
usually handled in code, but here is a simple update).
Query - UPDATE entity_contexts
SET entity_id = 101 WHERE
entity_id = 105; -- Then
delete 105

5. Junction Operations (Connecting Entities to Contexts) Queries for the Many-to-Many mapping.

LINK ENTITY - Connects an extracted entity (Java) to the chunk (Context) where it was found.
Query - INSERT INTO
entity_contexts (entity_id,
context_id) VALUES (101,500) ON CONFLICT 
DO NOTHING;

GET CONTEXTS (Recall) - "Show me every time we mentioned Java."
Query - SELECT c.context_data
FROM contexts c JOIN
entity_contexts ec ON c.id
= ec.context_id WHERE
ec.entity_id = 101;

GET ENTITIES - "What concepts are mentioned in this specific chunk?"
Query - SELECT e.entity_name
FROM entities e JOIN
entity_contexts ec ON e.id
= ec.entity_id WHERE
ec.context_id = 500;

6. Relation Operations (The Graph)
Queries for managing the connections and edge weights.

UPSERT RELATION - Inserts a relation. If it exists, increments the weight (Frequency Counter).
Query - INSERT INTO relations
(source_id, target_id,
relation_type, edge_weight)
VALUES (101, 202,'written_in', 1) ON
CONFLICT (source_id,target_id, relation_type) 
DO UPDATE SET edge_weight = relations.edge_weight + 1;

FROM (Outgoing) - Finds what Java connects to (e.g., Java -> uses -> GC).
Query - SELECT r.relation_type, t.entity_name, r.edge_weight FROM
relations r JOIN entities t
ON r.target_id = t.id WHERE
r.source_id = 101 ORDER BY r.edge_weight DESC;

TO (Incoming) - Finds what connects to Java (e.g., SpringBoot -> uses -> Java).
Query - SELECT s.entity_name, r.relation_type, r.edge_weight FROM
relations r JOIN entities s
ON r.source_id = s.id
WHERE r.target_id = 101;

TRAVERSAL (Graph RAG) - Finds "2-hop" connections(Friends of Friends).
Query - SELECT t.entity_name
FROM relations r1 JOIN
relations r2 ON r1.target_id = r2.source_id JOIN entities
t ON r2.target_id = t.id WHERE r1.source_id = 101;

TOP RELATIONS - Finds the strongest connections in your entire database.
Query - SELECT s.entity_name, r.relation_type, t.entity_name, r.edge_weight FROM
relations r JOIN entities s ON r.source_id = s.id JOIN
entities t ON r.target_id = t.id ORDER BY
r.edge_weight DESC LIMIT 10;