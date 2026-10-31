# Responsibilities

Backend owns SDK, parsing, source validation, policy/execution boundaries, provenance, replay and measured reports. Model extracts labels and writes draft text only. UI displays provider/model/mode and must not hide replay status; App.tsx is owned by Opus. Actions workflow injects GEMINI_API_KEY only in opted-in live steps and stores evidence artifacts. Deployment integrator injects the key into backend runtime; browser never receives it.
