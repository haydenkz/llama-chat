# LlamaChat

A native Android client for [llama.cpp](https://github.com/ggml-org/llama.cpp) `llama-server`, styled to match the bundled web UI. Chat with local models over your LAN — streaming responses, reasoning, tool calling, image input, and multi-server support.

[![Android CI](https://github.com/haydenkz/llama-chat/actions/workflows/android.yml/badge.svg)](https://github.com/haydenkz/llama-chat/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/haydenkz/llama-chat)](https://github.com/haydenkz/llama-chat/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## Features

- **Streaming chat** with markdown rendering, syntax-highlighted code blocks, and copy/edit/regenerate per message.
- **Work-mode UI** — a live "Thinking" indicator (elapsed time, expandable summary) while the model reasons or runs tools, and compact collapsible **tool chips**, so tool use never hijacks the reply.
- **Model management** — lists the models exposed by `llama-server` (router mode) with size / parameters / context / quantization, **auto-loads** the model you pick, and can **hide** models per server.
- **Model-generated chat titles**.
- **Tools** — web search (DuckDuckGo, with result favicons), weather (Open-Meteo), Wikipedia, calculator, date & time, **Python** (run in a self-hosted Piston sandbox), and **file creation** (downloadable). Enable or disable each in **Settings → Tools**.
- **Per-message stats** like the web UI: model, token count, duration and tokens/second.
- **Reasoning / thinking** for reasoning models (`--reasoning-format`), with a one-line summary.
- **Multi-server** — add, edit, switch and remove servers with optional API keys and health checks; first-run setup gets you connected.
- **Sampler controls** — temperature, top-k/p, min-p, penalties, max tokens, seed, stop sequences and system prompt; defaults come from the model and are saved per model.
- **Conversation management** — search, and long-press to delete.
- **Image input** for vision-capable models.
- **Local history** — conversations and messages persist in a local Room database.
- **Dark / light theme** matching the llama.cpp web UI's neutral shadcn palette (an exact `oklch` → Compose Color port).
- **Subtle haptics** while generating (toggleable).

## Requirements

- Android 10 (API 29) or newer.
- A reachable `llama-server` instance (llama.cpp build with the router/multi-model mode is supported; single-model builds work too).

## Quick start

1. Start `llama-server` on your machine (see [Server setup](#server-setup)).
2. Install the app from [Releases](https://github.com/haydenkz/llama-chat/releases).
3. On first launch, enter your server address and pick a model.
4. Chat. To change servers, open the drawer → **Settings → Manage servers**. The app ships with a default server of `http://<your-server>:8080`.

## Server setup

The app talks to llama.cpp's OpenAI-compatible API. Router (multi-model) mode is supported directly:

```bash
llama-server --host 0.0.0.0 --port 8080 --models-dir /path/to/gguf --jinja
```

Notes:

- **Tool calling** requires the model to be served with `--jinja` so the chat template can emit tool calls. Without it, the model simply won't call tools.
- `--reasoning-format deepseek` (or similar) enables the separate reasoning channel used by the collapsible "thinking" block.
- The app works against a single loaded model too; it will just show one entry in the model list.
- Cleartext HTTP is allowed by the app for LAN addresses.

### Python sandbox (optional)

The `run_python` tool executes code in a self-hosted [Piston](https://github.com/engineer-man/piston) instance — an isolated container with timeouts. Run Piston with Docker on the server host and install a Python runtime:

```bash
docker run -d --name piston -p 2000:2000 ghcr.io/engineer-man/piston
docker exec piston piston ppman install python   # add packages if you like
```

The app defaults to `http://<your-server>:2000`; override it in **Settings → Tools**.

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
User=llama
Group=llama
WorkingDirectory=/home/llama

ExecStart=/usr/bin/llama-server \
    --models-dir /home/llama/Models/gguf \
    --models-preset /home/llama/.config/llama-server/models.ini \
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
  [my-model]
  model = /home/llama/Models/gguf/model.gguf
  jinja = true
  ctx-size = 65536
  ```
- `--metrics` enables the `/metrics` endpoint shown on the app's **Server info** screen.
- Adjust the user, paths and binary path to match your setup.

## Contributing

Issues and pull requests are welcome. Please run `./gradlew testDebugUnitTest` before opening a PR.
