import { Composition } from 'remotion';
import { CaptionedShort } from './CaptionedShort';
import captions from './remotion-captions.json';
import React from 'react';

const fps = 30;

// Find the end time of the last caption (in seconds):
const lastCaptionEnd = captions[captions.length - 1]?.end ?? 0;
// Add a small buffer (e.g. 1 second) so it doesn’t cut off exactly at the last word:
const durationInSeconds = lastCaptionEnd + 1;

// Convert to whole frames:
const durationInFrames = Math.ceil(durationInSeconds * fps);

export const RemotionRoot = () => (
  <Composition
    id="CaptionedShort"
    component={CaptionedShort}
    durationInFrames={durationInFrames}
    fps={fps}
    width={1080}
    height={1920}
    defaultProps={{
      videoSrc: 'backgroundclip.mp4',
      audioSrc: 'speech.mp3',
      captions: captions,
    }}
  />
);
