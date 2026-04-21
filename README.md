# AutoVideoProducer

A fully automated, multi-channel YouTube Shorts pipeline. Scrapes trending
stories with GPT-4o, narrates them with OpenAI TTS, auto-syncs background
gameplay footage via FFmpeg, renders with Remotion (React), and uploads to
five YouTube channels on a rolling schedule — all driven from a single
`.env`.

> Independent project, originally built June 2025.

---

## Architecture

```text
          ┌────────────────────────── uploadManager ──────────────────────────┐
          │                                                                   │
          │   ScheduledExecutorService ─► ChannelScheduler ─► YouTubeUploader │
          │           ▲                         │                             │
          │           │ fills pending/ when     │ moves to archive/           │
          │           │ queue < 5 clips         ▼                             │
          │           │                    Channels/<name>/{pending,archive}  │
          └───────────┼───────────────────────────────────────────────────────┘
                      │ invokes
                      ▼
          ┌─────────────────────────── videoBuilder ──────────────────────────┐
          │   AIscraper (GPT-4o)  ─►  VidBuilder  ─► BackgroundGenerator      │
          │                           │                (FFmpeg)               │
          │                           ├─► WhisperTranscriber                  │
          │                           └─► Renderer ─► npx remotion render ───►│ .mp4
          └───────────────────────────────────────────────────────────────────┘
                                       ▲
                                       │ reads composition + captions
                                       │
                                    vidRenderer (Remotion / React)
```

## Features

- Five YouTube channels driven in parallel with independent OAuth clients
- GPT-4o script generation with channel-specific prompts and
  history-aware de-duplication
- OpenAI `shimmer` TTS → MP3
- Whisper verbose-JSON transcription → word-timed captions
- FFmpeg-trimmed gameplay background at random start offsets
- Remotion (React) composition renders vertical 1080×1920 MP4
- 5-hour spacing per channel with automatic quota-rescheduling
- All secrets & paths read from a single project-root `.env`

## Tech stack

| Layer           | Tool                                               |
| --------------- | -------------------------------------------------- |
| AI script       | OpenAI GPT-4o Responses API (`AIscraper.java`)     |
| TTS + captions  | OpenAI TTS + Whisper (`whisper-1`)                 |
| Video mixing    | FFmpeg / ffprobe                                   |
| Rendering       | Remotion + React + Node.js                         |
| Upload          | YouTube Data API v3 (OAuth2 desktop-app flow)      |
| Scheduling      | Java 17 `ScheduledExecutorService`                 |
| Build           | Maven (multi-module)                               |
| Config          | `dotenv-java` (`.env` in repo root)                |

## Project structure

```text
AutoVideoProducer/
├── pom.xml                         # Maven parent
├── .env / .env.example             # Secrets and path overrides
├── uploadManager/                  # Scheduler + YouTube upload
│   └── src/main/java/vid/manager/
│       ├── Main.java               # Entry point for the uploader loop
│       ├── ChannelScheduler.java   # Per-channel 5-hour scheduler
│       └── YouTubeUploader.java    # OAuth + resumable upload
├── videoBuilder/                   # Content generation
│   └── src/main/java/vid/builder/
│       ├── Main.java               # CLI: java vid.builder.Main <channel> <n>
│       ├── AIscraper.java          # GPT-4o + web_search_preview
│       ├── VidBuilder.java         # Orchestrates the whole pipeline
│       ├── WhisperTranscriber.java # speech.mp3 → timestamped segments
│       ├── BackgroundGenerator.java# FFmpeg clip + re-encode
│       ├── Renderer.java           # npx remotion render
│       ├── SearchResult.java       # Record (title, text)
│       ├── Env.java                # dotenv-java wrapper
│       └── AppPaths.java           # Centralised paths from .env
├── vidRenderer/                    # Remotion (React / TS) project
│   └── src/
│       ├── CaptionedShort.tsx      # The 1080×1920 composition
│       └── Root.tsx
└── Channels/<name>/                # Created at runtime per channel
    ├── pending/                    # Rendered MP4s awaiting upload
    ├── archive/                    # Uploaded MP4s
    └── upload_history.txt          # Timestamped log
```

## Getting started

### Prerequisites

- Java 17+ and Maven
- Node.js 18+ (for Remotion)
- FFmpeg + ffprobe on `PATH`
- An OpenAI API key
- YouTube Data API OAuth clients (one per channel) from the
  [Google Cloud Console](https://console.cloud.google.com/)

### Install

```bash
# 1. Clone
git clone https://github.com/JustinV200/AutoVideoProducer.git
cd AutoVideoProducer

# 2. Configure secrets
cp .env.example .env        # then fill in OPENAI_API_KEY and channel creds

# 3. Remotion deps
cd vidRenderer && npm install && cd ..

# 4. Build Java
mvn -q package
```

### Run

Generate 3 videos for one channel:

```bash
java -cp videoBuilder/target/classes:$(mvn -q -f videoBuilder/pom.xml dependency:build-classpath -Dmdep.outputFile=/dev/stdout) \
     vid.builder.Main Channel_1 3
```

Start the long-running uploader loop (generates + schedules + uploads):

```bash
java -cp uploadManager/target/classes:$(mvn -q -f uploadManager/pom.xml dependency:build-classpath -Dmdep.outputFile=/dev/stdout) \
     vid.manager.Main
```

On Windows PowerShell, replace the classpath separator `:` with `;`.

## Configuration reference

All configuration lives in `.env` (see `.env.example`). Keys:

| Key                    | Default                                                 | Purpose                              |
| ---------------------- | ------------------------------------------------------- | ------------------------------------ |
| `OPENAI_API_KEY`       | *(required)*                                            | Script, TTS, and Whisper calls       |
| `CHANNELS_ROOT`        | `Channels`                                              | Per-channel output folders           |
| `VIDRENDERER_DIR`      | `vidRenderer`                                           | Remotion project                     |
| `GAMEPLAY_DIR`         | `videoBuilder/src/main/resources/Gameplay_stores`       | Long gameplay source clips           |
| `NPX_PATH`             | `npx`                                                   | Override on Windows if needed        |
| `CHANNEL_<N>_CLIENT_ID` / `_SECRET` / `_EMAIL` | *(required)*                            | OAuth credentials per channel        |

Real process environment variables override `.env` entries.

## License

[MIT](LICENSE)
