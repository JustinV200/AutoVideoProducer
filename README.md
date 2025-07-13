# AutoVideoProducer

Personal project I made in June 2025 and uploaded July 2025 to git for archival purposes 

**AutoVideoProducer** is a scalable, multi-channel, AI-driven YouTube Shorts generation and publishing system.

Built in Java + Node.js, it:
- Uses GPT-4o to scrape and summarize trending web content
- Narrates it using OpenAI TTS and transcribes with Whisper
- Clips and syncs background gameplay using FFmpeg
- Renders final videos with Remotion + React
- Uploads to YouTube automatically on a timed loop

## Features include:

- Concurrent channel management (5+ channels)
-  GPT-4o-based story/script generation per channel prompt
-  OpenAI TTS (`shimmer` voice) → MP3
-  Whisper → accurate timestamped transcription for captions
- Remotion (React) → final rendered .mp4
- Upload rotation every 3 hours per channel
- All credentials loaded via environment variables

---

## Tech Stack

| Layer         | Tech Used |
|---------------|-----------|
| AI Content    | OpenAI GPT-4o (via `AIscraper.java`)  
| TTS + Captions| OpenAI TTS + Whisper API  
| Video Syncing | FFmpeg shell integration (Java)  
| Rendering     | Remotion (React + Node.js)  
| Upload        | YouTube Data API (OAuth2 via Google SDK)  
| Scheduling    | Java `ScheduledExecutorService`  

---

## Environment Setup

### 1. Java Setup
- Java 17+
- Maven

### 2. Node Setup (for rendering)
```bash
cd vidRenderer
npm install
