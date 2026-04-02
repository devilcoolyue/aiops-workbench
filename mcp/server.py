"""AIOps Workbench MCP Server

Wraps the workbench REST API (todo-tasks, focus-events) as MCP tools,
so that CoPaw agent can query and manage operational data via tool calls.

Usage:
    python server.py                          # default http://localhost:8089
    python server.py --base-url http://10.0.0.1:8089
    WORKBENCH_BASE_URL=http://10.0.0.1:8089 python server.py
"""

from __future__ import annotations

import json
import os
from typing import Any

import httpx
from mcp.server.fastmcp import FastMCP

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

BASE_URL = os.environ.get("WORKBENCH_BASE_URL", "http://localhost:8089")
API_PREFIX = "/api/workbench"
DEFAULT_USER_ID = "default"
REQUEST_TIMEOUT = 10.0

MCP_HOST = os.environ.get("MCP_HOST", "0.0.0.0")
MCP_PORT = int(os.environ.get("MCP_PORT", "8090"))

mcp = FastMCP(
    "aiops-workbench",
    instructions="AIOps Workbench: todo tasks and focus events management",
    host=MCP_HOST,
    port=MCP_PORT,
)

# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------

_http_client: httpx.AsyncClient | None = None


async def _client() -> httpx.AsyncClient:
    global _http_client
    if _http_client is None or _http_client.is_closed:
        _http_client = httpx.AsyncClient(
            base_url=BASE_URL,
            timeout=REQUEST_TIMEOUT,
        )
    return _http_client


async def _request(
    method: str,
    path: str,
    *,
    params: dict[str, Any] | None = None,
    json_body: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Send request and return parsed Result<T> response."""
    client = await _client()
    resp = await client.request(
        method,
        f"{API_PREFIX}{path}",
        params=params,
        json=json_body,
    )
    resp.raise_for_status()
    return resp.json()


def _format_result(result: dict[str, Any]) -> str:
    """Format API Result<T> as readable string for the LLM."""
    if result.get("code") != 200:
        return f"Error: {result.get('message', 'unknown error')}"
    data = result.get("data")
    if data is None:
        return "OK (no data)"
    return json.dumps(data, ensure_ascii=False, indent=2)


# ---------------------------------------------------------------------------
# Todo Task tools
# ---------------------------------------------------------------------------


@mcp.tool()
async def list_todo_tasks(
    user_id: str = DEFAULT_USER_ID,
    system_code: str | None = None,
) -> str:
    """List todo tasks for the user, sorted by priority (P1 > P2 > P3).

    Use this to understand what tasks the user needs to handle.
    Returns task name, level, detail, suggestion, and estimated time.

    Args:
        user_id: User identifier, defaults to "default".
        system_code: Optional system code filter (e.g. "CRM", "OSS").
    """
    params: dict[str, str] = {"userId": user_id}
    if system_code:
        params["systemCode"] = system_code
    result = await _request("GET", "/todo-tasks", params=params)
    return _format_result(result)


@mcp.tool()
async def get_todo_task(task_id: int) -> str:
    """Get details of a specific todo task by ID.

    Args:
        task_id: The task ID.
    """
    result = await _request("GET", f"/todo-tasks/{task_id}")
    return _format_result(result)


@mcp.tool()
async def create_todo_task(
    task_name: str,
    task_level: str,
    system_code: str,
    user_id: str = DEFAULT_USER_ID,
    task_detail: str | None = None,
    task_link: str | None = None,
    task_suggestion: str | None = None,
    estimated_time: str | None = None,
) -> str:
    """Create a new todo task.

    Args:
        task_name: Name of the task.
        task_level: Priority level, must be one of "P1", "P2", "P3".
        system_code: System code (e.g. "CRM", "OSS").
        user_id: User identifier, defaults to "default".
        task_detail: Detailed description of the task.
        task_link: Link related to the task.
        task_suggestion: Suggested action for the task.
        estimated_time: Estimated time to complete (e.g. "10 minutes").
    """
    body: dict[str, Any] = {
        "userId": user_id,
        "systemCode": system_code,
        "taskName": task_name,
        "taskLevel": task_level,
    }
    if task_detail is not None:
        body["taskDetail"] = task_detail
    if task_link is not None:
        body["taskLink"] = task_link
    if task_suggestion is not None:
        body["taskSuggestion"] = task_suggestion
    if estimated_time is not None:
        body["estimatedTime"] = estimated_time

    result = await _request("POST", "/todo-tasks", json_body=body)
    return _format_result(result)


@mcp.tool()
async def update_todo_task(
    task_id: int,
    task_name: str | None = None,
    task_level: str | None = None,
    system_code: str | None = None,
    task_detail: str | None = None,
    task_link: str | None = None,
    task_suggestion: str | None = None,
    estimated_time: str | None = None,
) -> str:
    """Update an existing todo task. Only non-null fields will be updated.

    Args:
        task_id: The task ID to update.
        task_name: New task name.
        task_level: New priority level ("P1", "P2", "P3").
        system_code: New system code.
        task_detail: New task detail.
        task_link: New task link.
        task_suggestion: New suggestion.
        estimated_time: New estimated time.
    """
    body: dict[str, Any] = {}
    if system_code is not None:
        body["systemCode"] = system_code
    if task_name is not None:
        body["taskName"] = task_name
    if task_level is not None:
        body["taskLevel"] = task_level
    if task_detail is not None:
        body["taskDetail"] = task_detail
    if task_link is not None:
        body["taskLink"] = task_link
    if task_suggestion is not None:
        body["taskSuggestion"] = task_suggestion
    if estimated_time is not None:
        body["estimatedTime"] = estimated_time

    result = await _request("PUT", f"/todo-tasks/{task_id}", json_body=body)
    return _format_result(result)


@mcp.tool()
async def delete_todo_task(task_id: int) -> str:
    """Delete a todo task by ID.

    Args:
        task_id: The task ID to delete.
    """
    result = await _request("DELETE", f"/todo-tasks/{task_id}")
    return _format_result(result)


# ---------------------------------------------------------------------------
# Focus Event tools
# ---------------------------------------------------------------------------


@mcp.tool()
async def list_focus_events(
    user_id: str = DEFAULT_USER_ID,
    system_code: str | None = None,
) -> str:
    """List focus events (key items to watch), sorted by event type priority.

    Order: pending_upgrade > completed > not_started > to_be_determined.
    Use this to understand the current operational status and alerts.

    Args:
        user_id: User identifier, defaults to "default".
        system_code: Optional system code filter.
    """
    params: dict[str, str] = {"userId": user_id}
    if system_code:
        params["systemCode"] = system_code
    result = await _request("GET", "/focus-events", params=params)
    return _format_result(result)


@mcp.tool()
async def get_focus_event(event_id: int) -> str:
    """Get details of a specific focus event by ID.

    Args:
        event_id: The focus event ID.
    """
    result = await _request("GET", f"/focus-events/{event_id}")
    return _format_result(result)


@mcp.tool()
async def create_focus_event(
    event_type: str,
    event_count: int,
    system_code: str,
    user_id: str = DEFAULT_USER_ID,
    ratio: str | None = None,
    event_link: str | None = None,
) -> str:
    """Create a new focus event.

    Args:
        event_type: Type of event, one of "pending_upgrade", "completed",
                    "not_started", "to_be_determined".
        event_count: Number of events.
        system_code: System code (e.g. "ALL", "CRM").
        user_id: User identifier, defaults to "default".
        ratio: Change ratio (e.g. "up 20%").
        event_link: Link for the event.
    """
    body: dict[str, Any] = {
        "userId": user_id,
        "systemCode": system_code,
        "eventType": event_type,
        "eventCount": event_count,
    }
    if ratio is not None:
        body["ratio"] = ratio
    if event_link is not None:
        body["eventLink"] = event_link

    result = await _request("POST", "/focus-events", json_body=body)
    return _format_result(result)


@mcp.tool()
async def update_focus_event(
    event_id: int,
    event_type: str | None = None,
    event_count: int | None = None,
    system_code: str | None = None,
    ratio: str | None = None,
    event_link: str | None = None,
) -> str:
    """Update an existing focus event. Only non-null fields will be updated.

    Args:
        event_id: The focus event ID to update.
        event_type: New event type.
        event_count: New event count.
        system_code: New system code.
        ratio: New change ratio.
        event_link: New event link.
    """
    body: dict[str, Any] = {}
    if system_code is not None:
        body["systemCode"] = system_code
    if event_type is not None:
        body["eventType"] = event_type
    if event_count is not None:
        body["eventCount"] = event_count
    if ratio is not None:
        body["ratio"] = ratio
    if event_link is not None:
        body["eventLink"] = event_link

    result = await _request("PUT", f"/focus-events/{event_id}", json_body=body)
    return _format_result(result)


@mcp.tool()
async def delete_focus_event(event_id: int) -> str:
    """Delete a focus event by ID.

    Args:
        event_id: The focus event ID to delete.
    """
    result = await _request("DELETE", f"/focus-events/{event_id}")
    return _format_result(result)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="AIOps Workbench MCP Server")
    parser.add_argument(
        "--base-url",
        default=None,
        help=f"Workbench API base URL (default: {BASE_URL})",
    )
    parser.add_argument(
        "--transport",
        default="streamable-http",
        choices=["stdio", "streamable-http", "sse"],
        help="MCP transport mode (default: streamable-http)",
    )
    args = parser.parse_args()

    if args.base_url:
        BASE_URL = args.base_url

    mcp.run(transport=args.transport)
