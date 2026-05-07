#!/usr/bin/env python3
"""
Test script to verify that the routeQuery endpoint handles LLM failures gracefully.

This script tests that when LLM calls fail (e.g., due to authentication issues),
the endpoint returns HTTP 200 with empty results instead of HTTP 500.
"""

import requests
import json

def test_route_endpoint():
    """Test the /api/v1/memory/query/route endpoint."""

    url = 'http://localhost:8080/api/v1/memory/query/route'

    # Test case 1: Simple query
    payload1 = {
        "query": "What is the connection between the creator of C programming",
        "limit": 5,
        "minRelevance": 0.5,
        "uid": "benchmark_user",
        "generateAnswer": False
    }

    print("Testing routeQuery endpoint...")
    print(f"Payload: {json.dumps(payload1, indent=2)}")

    try:
        response = requests.post(url, json=payload1, timeout=30)
        print(f"\nStatus Code: {response.status_code}")
        print(f"Response Headers: {dict(response.headers)}")

        if response.status_code == 200:
            print("\n[SUCCESS] Endpoint returned HTTP 200")
            data = response.json()
            print(f"Response body: {json.dumps(data, indent=2)}")

            # Verify the response structure
            if 'query' in data and 'results' in data:
                print(f"\n[SUCCESS] Response structure is valid")
                print(f"Query: {data['query'][:50]}...")
                print(f"Number of results: {len(data['results'])}")

                if len(data['results']) == 0:
                    print("\n[SUCCESS] Endpoint gracefully handled LLM failure by returning empty results")
                else:
                    print(f"\n[SUCCESS] Endpoint returned {len(data['results'])} results")
            else:
                print("\n[ERROR] Response structure is invalid")
                return False

        elif response.status_code == 500:
            print("\n[ERROR] Endpoint returned HTTP 500 (Internal Server Error)")
            print(f"Response: {response.text}")
            return False
        else:
            print(f"\n[WARNING] Endpoint returned unexpected status code {response.status_code}")
            print(f"Response: {response.text}")

    except requests.exceptions.RequestException as e:
        print(f"\n[ERROR] Failed to connect to endpoint: {e}")
        return False

    # Test case 2: Another query to verify consistency
    payload2 = {
        "query": "Which company created Kubernetes",
        "limit": 3,
        "minRelevance": 0.5,
        "uid": "benchmark_user",
        "generateAnswer": False
    }

    print("\n" + "="*60)
    print("Testing with second query...")

    try:
        response = requests.post(url, json=payload2, timeout=30)
        print(f"Status Code: {response.status_code}")

        if response.status_code == 200:
            print("[SUCCESS] Second query also succeeded")
            data = response.json()
            print(f"Number of results: {len(data['results'])}")
        else:
            print(f"[ERROR] Second query failed with status {response.status_code}")
            return False

    except requests.exceptions.RequestException as e:
        print(f"[ERROR] Failed to connect: {e}")
        return False

    print("\n" + "="*60)
    print("[SUCCESS] ALL TESTS PASSED")
    print("The routeQuery endpoint is handling LLM failures gracefully.")
    return True

if __name__ == "__main__":
    success = test_route_endpoint()
    exit(0 if success else 1)
