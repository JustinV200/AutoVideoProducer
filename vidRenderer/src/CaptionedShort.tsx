import React from 'react';
import {
  AbsoluteFill,
  Video,
  Audio,
  staticFile,
  useCurrentFrame,
} from 'remotion';

interface Caption {
  start: number;
  end: number;
  text: string;
}

export const CaptionedShort: React.FC<{
  videoSrc?: string;
  audioSrc?: string;
  captions: Caption[];
}> = ({
  videoSrc = 'backgroundclip.mp4',
  audioSrc = 'speech.mp3',
  captions,
}) => {
  const frame = useCurrentFrame();
  const timeInSeconds = frame / 30;

  const current = captions.find(
    (c) => timeInSeconds >= c.start && timeInSeconds < c.end
  );

  return (
    <AbsoluteFill style={{ backgroundColor: 'black' }}>
      <Video
        src={staticFile(videoSrc)}
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%',
          height: '100%',
          objectFit: 'cover',
          objectPosition: 'center',
        }}
      />
      <Audio src={staticFile(audioSrc)} />
      {current && (
        <div
          style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            maxWidth: '90%',
            padding: '0 20px',
            textAlign: 'center',
            fontSize: 80,
            fontWeight: 900,
            color: 'white',
            textShadow: `
              -2px -2px 0 #000,
               2px -2px 0 #000,
              -2px  2px 0 #000,
               2px  2px 0 #000
            `,
          }}
        >
          {current.text}
        </div>
      )}
    </AbsoluteFill>
  );
};
