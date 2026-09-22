# LlamaChat

A native Android client for [llama.cpp](https://github.com/ggml-org/llama.cpp) `llama-server`, styled to match the bundled web UI. Chat with local models over your LAN — streaming responses, per-message stats, reasoning, image input, tool calling with built-in web search, and multi-server support.

[![Android CI](https://github.com/haydenkz/llama-chat/actions/workflows/android.yml/badge.svg)](https://github.com/haydenkz/llama-chat/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/haydenkz/llama-chat)](https://github.com/haydenkz/llama-chat/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## Features

- **Streaming chat** with markdown rendering, syntax-highlighted code blocks, and copy/edit/regenerate/delete per message.
- **Model management** — lists the models exposed by `llama-server` (router mode), shows size / parameters / context / quantization, and **auto-loads** the model you pick.
- **Per-message stats** like the web UI: model, token count, duration and tokens/second.
- **Reasoning / thinking** — collapsible "thinking" block for reasoning models (`--reasoning-format`); auto-expands while thinking, expandable afterwards.
- **Tool calling with built-in web search** — advertises a `web_search` function (DuckDuckGo, no API key), executes it, feeds results back, and renders tool-call/result cards.
- **Multi-server** — add, edit, switch and remove servers with optional API keys and health checks.
- **Sampler controls** — temperature, top-k/p, min-p, penalties, max tokens, seed, stop sequences, system prompt, web-search toggle and more, saved per model.
- **Image input** for vision-capable models.
- **Local history** — conversations and messages persist in a local Room database.
- **Dark / light theme** matching the llama.cpp web UI's neutral shadcn palette (an exact `oklch` → Compose Color port).
- **ChatGPT-style haptics** while generating (toggleable).

## Requirements

- Android 10 (API 29) or newer.
- A reachable `llama-server` instance (llama.cpp build with the router/multi-model mode is supported; single-model builds work too).

## Quick start

1. Start `llama-server` on your machine (see [Server setup](#server-setup)).
2. Install the app (see [Releases](https://github.com/haydenkz/llama-chat/releases) or [build from source](#building)).
3. Open the app, tap the **model selector** in the header, and choose a model — it loads automatically.
4. Chat. To point at a different server, open the drawer → **Settings → Manage servers**.

The app ships with a default server of `http://192.168.2.3:8080`; change or add servers freely.

## Server setup

The app talks to llama.cpp's OpenAI-compatible API. Router (multi-model) mode is supported directly:

```bash
llama-server --host 0.0.0.0 --port 8080 --models-dir /path/to/gguf --jinja
```

Notes:

- **Tool calling / web search** requires the model to be served with `--jinja` so the chat template can emit tool calls. Without it, the model simply won't call tools.
- `--reasoning-format deepseek` (or similar) enables the separate reasoning channel used by the collapsible "thinking" block.
- The app works against a single loaded model too; it will just show one entry in the model list.
- Cleartext HTTP is allowed by the app for LAN addresses.

## Run as a systemd service

To keep `llama-server` running in the background, install it as a systemd service.

Create `/etc/systemd/system/llama-server.service`:

```ini
[Unit]
Description=llama.cpp Model Router
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=hayden
Group=users
WorkingDirectory=/home/hayden

ExecStart=/usr/bin/llama-server \
    --models-dir /home/hayden/Models/gguf \
    --models-preset /home/hayden/.config/llama-server/models.ini \
    --models-max 1 \
    --models-autoload \
    --host 0.0.0.0 \
    --port 8080 \
    --metrics

Restart=on-failure
RestartSec=3

[Install]
WantedBy=multi-user.target
```

Then enable and start it:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now llama-server
systemctl status llama-server          # check it is running
journalctl -u llama-server -f          # follow logs
```

Notes:

- `--host 0.0.0.0` is required for the phone to reach the server; `127.0.0.1` is only reachable from the same machine.
- `--models-preset` points at an INI file describing each model; `--models-max 1` keeps a single model resident and `--models-autoload` loads it on demand. Example preset entry:

  ```ini
  [Granite-4.1-8B-FAST]
  model = /home/hayden/Models/gguf/granite-4.1-8b-Q4_K_M.gguf
  jinja = true
  ctx-size = 65536
  ```
- `--metrics` enables the `/metrics` endpoint shown on the app's **Server info** screen.
- Add `--jinja` (globally or per model) to enable the chat template's tool-calling, which the app's web-search feature relies on.

## Configuration

- **Servers** — drawer → Settings → Manage servers. Each server has a name, base URL and optional bearer token.
- **Sampling** — the sliders icon in the chat header. Settings are stored per model.
- **Web search** — enabled by default; toggle it in the sampling sheet.

## Building

Requirements: JDK 21 and the Android SDK (compile SDK 37, build-tools 36.0.0).

```bash
git clone https://github.com/haydenkz/llama-chat.git
cd llama-chat
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
./gradlew installDebug         # install on a connected device
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Create `local.properties` with your SDK path if it isn't picked up automatically:

```properties
sdk.dir=/path/to/Android/Sdk
```

## Architecture

The app is a single-module Kotlin/Compose application using manual dependency injection (`AppContainer`).

```
app/src/main/java/com/llamacpp/mobile/
├── data/
│   ├── remote/        OkHttp + SSE client for the llama-server API, DTOs
│   ├── local/         Room database (conversations, messages)
│   ├── repo/          Server, settings and chat repositories
│   └── tools/         Client-side tool implementations (web search)
├── domain/model/      Domain models (servers, models, messages, samplers)
├── di/                AppContainer (manual DI)
└── ui/
    ├── theme/         oklch → Compose Color theme (dark/light)
    ├── chat/          Chat screen, model picker, sampler sheet, message bubbles
    ├── servers/       Server manager
    ├── serverinfo/    Server props / slots / metrics
    └── navigation/    Navigation graph
```

### Tech stack

| Concern | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (Material 3) |
| Networking | OkHttp + `okhttp-sse`, kotlinx-serialization |
| Persistence | Room, DataStore (Preferences) |
| Markdown | `mikepenz/multiplatform-markdown-renderer` |
| Build | Gradle 9, AGP 9 (built-in Kotlin) |

## Versioning & releases

- Versions follow [Semantic Versioning](https://semver.org/); the Android `versionName` mirrors the tag and `versionCode` increments per release.
- Every push to `main` runs the [Android CI](.github/workflows/android.yml) workflow (unit tests + debug build).
- Pushing a tag like `v0.1.0` builds a signed release APK and publishes a GitHub Release with the APK attached (and auto-generated release notes). See [CHANGELOG.md](CHANGELOG.md).

```bash
git tag v0.1.0
git push origin v0.1.0
```

Release builds are signed with the standard Android debug key so they can be sideloaded; set up a real signing key before publishing to a store.

## Contributing

Issues and pull requests are welcome. Please run `./gradlew testDebugUnitTest` before opening a PR.

## License

[MIT](LICENSE)
