#!/usr/bin/env python3
"""
Script to add voiceover to the promo video
"""

import os
from moviepy.editor import VideoFileClip, AudioFileClip, CompositeAudioClip

PROMO_DIR = "promo"
VIDEO_PATH = os.path.join(PROMO_DIR, "promo_video.mp4")
AUDIO_PATH = os.path.join(PROMO_DIR, "voiceover.mp3")
OUTPUT_PATH = os.path.join(PROMO_DIR, "promo_video_with_voiceover.mp4")

def main():
    if not os.path.exists(VIDEO_PATH):
        print(f"Error: Video not found at {VIDEO_PATH}")
        print("Please run create_promo_video.py first")
        return
    
    if not os.path.exists(AUDIO_PATH):
        print(f"Error: Audio file not found at {AUDIO_PATH}")
        print("Please record voiceover and save as voiceover.mp3")
        return
    
    print("Adding voiceover to video...")
    
    # Load video and audio
    video = VideoFileClip(VIDEO_PATH)
    audio = AudioFileClip(AUDIO_PATH)
    
    # Adjust audio to match video duration
    if audio.duration > video.duration:
        audio = audio.subclip(0, video.duration)
    elif audio.duration < video.duration:
        # Loop audio if shorter than video
        loops_needed = int(video.duration / audio.duration) + 1
        audio = CompositeAudioClip([audio] * loops_needed).subclip(0, video.duration)
    
    # Set audio volume (adjust as needed)
    audio = audio.volumex(0.8)
    
    # Combine video and audio
    final_video = video.set_audio(audio)
    
    # Export
    print("Exporting final video...")
    final_video.write_videofile(
        OUTPUT_PATH,
        codec='libx264',
        audio_codec='aac',
        preset='medium',
        bitrate='8000k'
    )
    
    print(f"\n[SUCCESS] Video with voiceover created!")
    print(f"[INFO] Location: {OUTPUT_PATH}")
    
    # Cleanup
    video.close()
    audio.close()
    final_video.close()

if __name__ == "__main__":
    main()

