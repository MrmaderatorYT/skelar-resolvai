#!/usr/bin/env python3
"""Account-specific API probe. Never print the API key or include it in a URL.
Official references: https://ai.google.dev/api/models
https://ai.google.dev/gemma/docs/core/gemma_on_gemini_api
https://ai.google.dev/gemini-api/docs/rate-limits
"""
import datetime
import json
import math
import os
import re
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

KEY = os.environ.get("GEMINI_API_KEY", "")
BASE = "https://generativelanguage.googleapis.com/v1beta/"
REPORT = Path("gemma_api_probe.json")
report = {"generated_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
          "provider": "gemma", "status": "UNVERIFIED", "checks": {}}


def request(path, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    for attempt in range(3):
        req = urllib.request.Request(BASE + path, data=data,
                headers={"x-goog-api-key": KEY, "Content-Type": "application/json"})
        start = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=20) as response:
                return 200, json.load(response), (time.perf_counter() - start) * 1000
        except urllib.error.HTTPError as error:
            if (error.code == 429 or error.code >= 500) and attempt < 2:
                time.sleep(min(10, 2 ** (attempt + 1)))
                continue
            # Only the status is retained; backend error messages could contain request details.
            return error.code, {}, (time.perf_counter() - start) * 1000
    raise RuntimeError("unreachable")


def generation(model, extra=None):
    body = {"contents": [{"role": "user", "parts": [{"text": 'Return exactly this JSON object: {"ok":true}'}]}],
            "generationConfig": {"temperature": 0, "maxOutputTokens": 128}}
    if extra:
        if "generationConfig" in extra:
            body["generationConfig"].update(extra["generationConfig"])
        else:
            body.update(extra)
    code, response, latency = request("models/" + urllib.parse.quote(model, safe="") + ":generateContent", body)
    text = "".join(part.get("text", "") for candidate in response.get("candidates", [])
                   for part in candidate.get("content", {}).get("parts", []) if not part.get("thought", False))
    try:
        output = json.loads(text)
        valid = isinstance(output, dict) and output.get("ok") is True
    except (ValueError, TypeError):
        valid = False
    return {"http_status": code, "accepted": code == 200, "json_ok": valid, "latency_ms": round(latency, 2)}


def main():
    if not KEY:
        report["reason"] = "GEMINI_API_KEY is absent; no API calls made"
        return 2
    models = []
    token = None
    for _ in range(20):
        path = "models?pageSize=1000" + ("&pageToken=" + urllib.parse.quote(token) if token else "")
        code, body, _ = request(path)
        if code != 200:
            report["reason"] = "models.list failed, HTTP " + str(code)
            return 1
        models.extend(body.get("models", []))
        token = body.get("nextPageToken")
        if not token:
            break
    if token:
        report["reason"] = "models.list pagination incomplete"
        return 1
    available = [m["name"].removeprefix("models/") for m in models
                 if "gemma" in m.get("name", "").lower() and "-it" in m.get("name", "")
                 and "generateContent" in m.get("supportedGenerationMethods", [])]
    report["available_instruction_tuned_gemma"] = available
    if not available:
        report["reason"] = "No instruction-tuned Gemma with generateContent was listed for this project"
        return 1
    def rank(name):
        version = re.search(r"gemma-(\d+)", name)
        size = re.search(r"-(\d+)b", name)
        return (int(version.group(1)) if version else 0, int(size.group(1)) if size else 0, name)
    requested = os.environ.get("DEFUSE_LLM_MODEL")
    model = requested or max(available, key=rank)
    if model not in available:
        report["reason"] = "Configured model was not returned by models.list"
        return 1
    report["model"] = model
    report["selection_rule"] = "explicit configured ID, otherwise highest Gemma generation then largest listed IT model"
    checks = report["checks"]
    checks["plain_generation"] = generation(model)
    checks["system_instruction"] = generation(model, {"systemInstruction": {"parts": [{"text": "Return a JSON object with ok=true."}]}})
    checks["json_mode"] = generation(model, {"generationConfig": {"responseMimeType": "application/json"}})
    checks["response_schema"] = generation(model, {"generationConfig": {"responseMimeType": "application/json",
            "responseSchema": {"type": "OBJECT", "properties": {"ok": {"type": "BOOLEAN"}}, "required": ["ok"]}}})
    latency_runs = [generation(model) for _ in range(5)]
    report["latency_runs"] = latency_runs
    successful = [run["latency_ms"] for run in latency_runs if run["accepted"]]
    report["latency_p50_ms"] = statistics.median(successful) if len(successful) == 5 else None
    report["rpm_tpm"] = "UNVERIFIED: project/model quota must be read in AI Studio; five sequential calls do not establish a limit"
    report["quota_reference"] = "https://ai.google.dev/gemini-api/docs/rate-limits"
    if not checks["plain_generation"]["accepted"] or len(successful) != 5:
        report["reason"] = "Plain generation or five-call latency measurement incomplete"
        return 1
    report["status"] = "MEASURED"
    github_env = os.environ.get("GITHUB_ENV")
    if github_env:
        if not re.fullmatch(r"gemma[-a-zA-Z0-9_.]+", model):
            raise ValueError("Invalid model ID")
        with open(github_env, "a") as env:
            env.write("DEFUSE_LLM_MODEL=" + model + "\n")
            env.write("DEFUSE_LLM_JSON_MODE=" + str(checks["json_mode"]["accepted"] and checks["json_mode"]["json_ok"]).lower() + "\n")
            env.write("DEFUSE_LLM_RESPONSE_SCHEMA=" + str(checks["response_schema"]["accepted"] and checks["response_schema"]["json_ok"]).lower() + "\n")
    return 0


if __name__ == "__main__":
    try:
        code = main()
    except Exception as error:
        report["reason"] = "Probe failed: " + type(error).__name__
        code = 1
    REPORT.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    sys.exit(code)
