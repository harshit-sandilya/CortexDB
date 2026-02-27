"""Shared fixtures for CortexDB tests."""

import pytest
import httpx

from cortexdb.client import CortexDB


@pytest.fixture
def mock_transport():
    """A no-op transport that can be overridden by respx."""
    return httpx.MockTransport(lambda req: httpx.Response(200, json={}))


@pytest.fixture
def db(mock_transport):
    """CortexDB client wired to a mock transport."""
    client = CortexDB.__new__(CortexDB)
    client._http = httpx.Client(
        base_url="http://testserver",
        transport=mock_transport,
    )
    from cortexdb.setup import SetupAPI
    from cortexdb.ingest import IngestAPI
    from cortexdb.query import QueryAPI

    client.setup = SetupAPI(client._http)
    client.ingest = IngestAPI(client._http)
    client.query = QueryAPI(client._http)
    yield client
    client.close()
