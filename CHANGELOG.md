# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-09-22

### Added

- Streaming chat against llama.cpp `llama-server` (OpenAI-compatible API) with
  markdown rendering and syntax-highlighted code blocks.
- Model manager for router (multi-model) servers: lists models with size,
  parameter count, context size and quantization, and auto-loads the selected
  model.
- Per-message stats (model, tokens, duration, tokens/second).
- Collapsible reasoning / "thinking" block for reasoning models.
- Tool calling with a built-in `web_search` tool (DuckDuckGo, no API key).
- Multi-server support with health checks and optional bearer tokens.
- Per-model sampler settings (temperature, top-k/p, min-p, penalties, max
  tokens, seed, stop sequences, system prompt and more).
- Image input for vision-capable models.
- Local conversation history (Room) and settings (DataStore).
- Dark/light theme ported from the llama.cpp web UI (`oklch` → Compose colors).
- ChatGPT-style haptics while generating (toggleable).
- GitHub Actions CI and tag-based release workflow.

[Unreleased]: https://github.com/haydenkz/llama-chat/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/haydenkz/llama-chat/releases/tag/v0.1.0
