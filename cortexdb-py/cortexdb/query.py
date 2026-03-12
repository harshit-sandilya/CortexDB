"""Query API wrapper for CortexDB."""

from __future__ import annotations

from typing import TYPE_CHECKING, Any
from uuid import UUID

from cortexdb.exceptions import NotFoundError
from cortexdb.models import Entity, QueryRequest, QueryResponse, Relation

if TYPE_CHECKING:
    import httpx


class QueryAPI:
    """Wraps the ``/api/query`` endpoints."""

    def __init__(self, http: httpx.Client) -> None:
        self._http = http

    # ── Context endpoints ────────────────────────────────────────────

    def search_contexts(
        self,
        query: str,
        limit: int = 5,
        min_relevance: float = 0.7,
        filters: dict[str, Any] | None = None,
    ) -> QueryResponse:
        """Semantic search on contexts.

        Args:
            query: Search query text.
            limit: Maximum number of results.
            min_relevance: Minimum relevance score (0-1).
            filters: Optional filters dict.

        Returns:
            QueryResponse with matching contexts.
        """
        return self._post_query("/api/v1/memory/query/contexts", query, limit, min_relevance, filters)

    def get_contexts_by_kb(self, kb_id: UUID | str) -> QueryResponse:
        """Get all contexts for a knowledge base."""
        resp = self._http.get(f"/api/v1/memory/query/contexts/kb/{kb_id}")
        resp.raise_for_status()
        return QueryResponse.model_validate(resp.json())

    def get_recent_contexts(self, days: int = 7) -> QueryResponse:
        """Get recent contexts from the last N days."""
        resp = self._http.get("/api/v1/memory/query/contexts/recent", params={"days": days})
        resp.raise_for_status()
        return QueryResponse.model_validate(resp.json())

    def search_recent_contexts(
        self,
        query: str,
        days: int = 7,
        limit: int = 5,
        min_relevance: float = 0.7,
    ) -> QueryResponse:
        """Search recent contexts with vector similarity."""
        return self._post_query(
            "/api/v1/memory/query/contexts/recent/search",
            query, limit, min_relevance,
            params={"days": days},
        )

    def get_sibling_contexts(self, context_id: UUID | str) -> QueryResponse:
        """Get sibling contexts (other chunks from same document)."""
        resp = self._http.get(f"/api/v1/memory/query/contexts/{context_id}/siblings")
        resp.raise_for_status()
        return QueryResponse.model_validate(resp.json())

    # ── Entity endpoints ─────────────────────────────────────────────

    def search_entities(
        self,
        query: str,
        limit: int = 5,
        min_relevance: float = 0.7,
    ) -> QueryResponse:
        """Semantic search on entities."""
        return self._post_query("/api/v1/memory/query/entities", query, limit, min_relevance)

    def get_entity_by_name(self, name: str) -> Entity | None:
        """Find entity by exact name. Returns None if not found."""
        resp = self._http.get(f"/api/v1/memory/query/entities/name/{name}")
        if resp.status_code == 404:
            return None
        resp.raise_for_status()
        return Entity.model_validate(resp.json())

    def get_entity_by_name_ignore_case(self, name: str) -> Entity | None:
        """Find entity by name (case-insensitive). Returns None if not found."""
        resp = self._http.get(f"/api/v1/memory/query/entities/name/{name}/ignorecase")
        if resp.status_code == 404:
            return None
        resp.raise_for_status()
        return Entity.model_validate(resp.json())

    def get_entity_id_by_name(self, name: str) -> UUID | None:
        """Get entity ID by name. Returns None if not found."""
        resp = self._http.get(f"/api/v1/memory/query/entities/id/{name}")
        if resp.status_code == 404:
            return None
        resp.raise_for_status()
        data = resp.json()
        return UUID(str(data.get("id")))

    def disambiguate_entity(self, entity_name: str, context_text: str) -> Entity | None:
        """Disambiguate entity using vector similarity with context."""
        resp = self._http.post(
            "/api/v1/memory/query/entities/disambiguate",
            params={"entityName": entity_name},
            content=context_text,
            headers={"Content-Type": "text/plain"},
        )
        if resp.status_code == 404:
            return None
        resp.raise_for_status()
        return Entity.model_validate(resp.json())

    def get_contexts_for_entity(self, entity_id: UUID | str) -> list[Any]:
        """Get all contexts where an entity is mentioned."""
        resp = self._http.get(f"/api/v1/memory/query/entities/{entity_id}/contexts")
        resp.raise_for_status()
        return resp.json()

    def get_entities_for_context(self, context_id: UUID | str) -> list[Entity]:
        """Get all entities mentioned in a context."""
        resp = self._http.get(f"/api/v1/memory/query/contexts/{context_id}/entities")
        resp.raise_for_status()
        return [Entity.model_validate(e) for e in resp.json()]

    def merge_entities(self, source_entity_id: UUID | str, target_entity_id: UUID | str) -> None:
        """Merge two entities (source into target)."""
        resp = self._http.post(
            "/api/v1/memory/query/entities/merge",
            params={
                "sourceEntityId": str(source_entity_id),
                "targetEntityId": str(target_entity_id),
            },
        )
        resp.raise_for_status()

    # ── History endpoints ────────────────────────────────────────────

    def search_history(
        self,
        query: str,
        limit: int = 5,
        min_relevance: float = 0.7,
    ) -> QueryResponse:
        """Semantic search on knowledge bases (history)."""
        return self._post_query("/api/v1/memory/query/history", query, limit, min_relevance)

    def get_history_by_user(self, uid: str) -> QueryResponse:
        """Get all history for a user."""
        resp = self._http.get(f"/api/v1/memory/query/history/user/{uid}")
        resp.raise_for_status()
        return QueryResponse.model_validate(resp.json())

    def get_recent_kbs(self, hours: int = 24) -> QueryResponse:
        """Get recent knowledge bases from the last N hours."""
        resp = self._http.get("/api/v1/memory/query/history/recent", params={"hours": hours})
        resp.raise_for_status()
        return QueryResponse.model_validate(resp.json())

    def get_kbs_since(self, since: str) -> QueryResponse:
        """Get knowledge bases since a timestamp (ISO-8601)."""
        resp = self._http.get("/api/v1/memory/query/history/since", params={"since": since})
        resp.raise_for_status()
        return QueryResponse.model_validate(resp.json())

    # ── User data ────────────────────────────────────────────────────

    def delete_user_data(self, uid: str) -> None:
        """Delete all data for a user (GDPR compliance)."""
        resp = self._http.delete(f"/api/v1/memory/query/user/{uid}")
        resp.raise_for_status()

    # ── Graph endpoints ──────────────────────────────────────────────

    def get_outgoing_connections(self, entity_id: UUID | str) -> QueryResponse:
        """Get outgoing relations for an entity."""
        resp = self._http.get(f"/api/v1/memory/query/graph/outgoing/{entity_id}")
        resp.raise_for_status()
        return QueryResponse.model_validate(resp.json())

    def get_incoming_connections(self, entity_id: UUID | str) -> QueryResponse:
        """Get incoming relations for an entity."""
        resp = self._http.get(f"/api/v1/memory/query/graph/incoming/{entity_id}")
        resp.raise_for_status()
        return QueryResponse.model_validate(resp.json())

    def get_two_hop_connections(self, entity_id: UUID | str) -> list[str]:
        """Get 2-hop connections (entity names reachable in 2 hops)."""
        resp = self._http.get(f"/api/v1/memory/query/graph/two-hop/{entity_id}")
        resp.raise_for_status()
        return resp.json()

    def get_top_relations(self, limit: int = 10) -> list[Relation]:
        """Get top/strongest relations."""
        resp = self._http.get("/api/v1/memory/query/graph/top-relations", params={"limit": limit})
        resp.raise_for_status()
        return [Relation.model_validate(r) for r in resp.json()]

    def get_relations_by_source(self, source_id: UUID | str) -> list[Relation]:
        """Get relations by source entity."""
        resp = self._http.get(f"/api/v1/memory/query/graph/relations/source/{source_id}")
        resp.raise_for_status()
        return [Relation.model_validate(r) for r in resp.json()]

    def get_relations_by_target(self, target_id: UUID | str) -> list[Relation]:
        """Get relations by target entity."""
        resp = self._http.get(f"/api/v1/memory/query/graph/relations/target/{target_id}")
        resp.raise_for_status()
        return [Relation.model_validate(r) for r in resp.json()]

    def get_relations_by_type(self, relation_type: str) -> list[Relation]:
        """Get relations by type."""
        resp = self._http.get(f"/api/v1/memory/query/graph/relations/type/{relation_type}")
        resp.raise_for_status()
        return [Relation.model_validate(r) for r in resp.json()]

    # ── Hybrid search ────────────────────────────────────────────────

    def hybrid_search(
        self,
        query: str,
        limit: int = 5,
        min_relevance: float = 0.7,
    ) -> QueryResponse:
        """Hybrid search combining vector and graph results."""
        return self._post_query("/api/v1/memory/query/hybrid", query, limit, min_relevance)

    # ── Pre-Routing / Agentic endpoints ──────────────────────────────

    def route_query(self, query: str) -> QueryResponse:
        """Route a query intelligently based on intent (PROMPT vs DOCUMENT)."""
        resp = self._http.post("/api/v1/memory/query/route", json={"query": query, "limit": 5, "minRelevance": 0.7})
        resp.raise_for_status()
        return QueryResponse.model_validate(resp.json())

    # ── Internal helpers ─────────────────────────────────────────────

    def _post_query(
        self,
        url: str,
        query: str,
        limit: int = 5,
        min_relevance: float = 0.7,
        filters: dict[str, Any] | None = None,
        params: dict[str, Any] | None = None,
    ) -> QueryResponse:
        """Send a POST query request and parse the response."""
        request = QueryRequest(
            query=query,
            limit=limit,
            minRelevance=min_relevance,
            filters=filters,
        )
        resp = self._http.post(
            url,
            json=request.model_dump(by_alias=True, exclude_none=True),
            params=params,
        )
        resp.raise_for_status()
        return QueryResponse.model_validate(resp.json())
