# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.2] - 2026-09-22

### Added

- New tools: **weather** (Open-Meteo), **Wikipedia**, **calculator** and
  **date & time**.
- **Settings → Tools** page to enable or disable each tool (all on by default).

### Changed

- Tool enable/disable moved out of the sampling sheet into the Tools page.
- Release notes are now taken from this changelog.

## [0.1.1] - 2026-09-22

### Added

- Selecting a model now loads it automatically; the manual load/unload buttons
  are gone.

### Changed

- Assistant turns render as one unit: a single merged **thinking** block, then
  **web search** cards, then the answer. Thinking is no longer split across tool
  iterations, and tool activity is visually separated from the response.
- Per-message stats and actions appear only once the answer is complete.
- Web search: multiple searches in one response are grouped into a single card.
  Collapsed, it shows the result favicons (preloaded with a staggered animation);
  expanded, it lists structured results (favicon, title, host, snippet) and grows
  downward with an animation.
- Markdown headings (H1–H6) are scaled down to fit a message bubble.

### Removed

- The per-message delete button.

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

[Unreleased]: https://github.com/haydenkz/llama-chat/compare/v0.1.2...HEAD
[0.1.2]: https://github.com/haydenkz/llama-chat/releases/tag/v0.1.2
[0.1.1]: https://github.com/haydenkz/llama-chat/releases/tag/v0.1.1
[0.1.0]: https://github.com/haydenkz/llama-chat/releases/tag/v0.1.0
