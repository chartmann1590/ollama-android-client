#!/usr/bin/env python3
"""
Enhanced script to create promo video with:
- Transitions between clips
- Text overlays (using PIL)
- Duration matching to audio
"""

import os
from PIL import Image, ImageDraw, ImageFont
import numpy as np
from moviepy.editor import (
    ImageClip, CompositeVideoClip, 
    concatenate_videoclips, AudioFileClip, VideoFileClip
)
import sys

# Directories
SCREENSHOTS_DIR = "screenshots"
IMAGES_DIR = "images"
PROMO_DIR = "promo"
CROPPED_DIR = os.path.join(PROMO_DIR, "cropped")
TEXT_OVERLAY_DIR = os.path.join(PROMO_DIR, "text_overlays")

# Create directories
os.makedirs(PROMO_DIR, exist_ok=True)
os.makedirs(CROPPED_DIR, exist_ok=True)
os.makedirs(TEXT_OVERLAY_DIR, exist_ok=True)

# Video settings
VIDEO_WIDTH = 1080
VIDEO_HEIGHT = 1920  # Portrait orientation
FPS = 30
TRANSITION_DURATION = 0.8  # seconds for fade transitions

def crop_screenshot(image_path, output_path, crop_top=0.1, crop_bottom=0.15):
    """Crop screenshot to remove top and bottom navbar areas"""
    img = Image.open(image_path)
    width, height = img.size
    
    top_crop = int(height * crop_top)
    bottom_crop = int(height * crop_bottom)
    
    cropped = img.crop((0, top_crop, width, height - bottom_crop))
    cropped.thumbnail((VIDEO_WIDTH, VIDEO_HEIGHT), Image.Resampling.LANCZOS)
    
    final_img = Image.new('RGB', (VIDEO_WIDTH, VIDEO_HEIGHT), color='black')
    paste_x = (VIDEO_WIDTH - cropped.width) // 2
    paste_y = (VIDEO_HEIGHT - cropped.height) // 2
    final_img.paste(cropped, (paste_x, paste_y))
    
    final_img.save(output_path, 'PNG')
    return output_path

def create_text_overlay_image(base_image_path, text, output_path, position='bottom'):
    """Create an image with text overlay using PIL"""
    img = Image.open(base_image_path).copy()
    draw = ImageDraw.Draw(img)
    
    # Try to use a nice font, fallback to default if not available
    try:
        # Try different font paths for Windows
        font_paths = [
            "C:/Windows/Fonts/arialbd.ttf",  # Arial Bold
            "C:/Windows/Fonts/arial.ttf",     # Arial
            "C:/Windows/Fonts/calibrib.ttf",  # Calibri Bold
        ]
        font = None
        for path in font_paths:
            if os.path.exists(path):
                font = ImageFont.truetype(path, 60)
                break
        if font is None:
            font = ImageFont.load_default()
    except:
        font = ImageFont.load_default()
    
    # Calculate text position
    bbox = draw.textbbox((0, 0), text, font=font)
    text_width = bbox[2] - bbox[0]
    text_height = bbox[3] - bbox[1]
    
    if position == 'bottom':
        x = (VIDEO_WIDTH - text_width) // 2
        y = VIDEO_HEIGHT - text_height - 100
    elif position == 'top':
        x = (VIDEO_WIDTH - text_width) // 2
        y = 100
    else:  # center
        x = (VIDEO_WIDTH - text_width) // 2
        y = (VIDEO_HEIGHT - text_height) // 2
    
    # Draw text with outline (shadow effect)
    outline_color = (0, 0, 0)  # Black outline
    fill_color = (255, 255, 255)  # White text
    
    # Draw outline
    for adj in range(-3, 4):
        for adj2 in range(-3, 4):
            draw.text((x + adj, y + adj2), text, font=font, fill=outline_color)
    
    # Draw main text
    draw.text((x, y), text, font=font, fill=fill_color)
    
    img.save(output_path, 'PNG')
    return output_path

def create_video_clip_with_text(image_path, duration, text=None, fade_in=0, fade_out=0):
    """Create a video clip from an image with optional text overlay"""
    if text:
        # Create text overlay image
        overlay_name = os.path.basename(image_path).replace('.png', '_text.png')
        overlay_path = os.path.join(TEXT_OVERLAY_DIR, overlay_name)
        create_text_overlay_image(image_path, text, overlay_path)
        clip = ImageClip(overlay_path).set_duration(duration).resize((VIDEO_WIDTH, VIDEO_HEIGHT))
    else:
        clip = ImageClip(image_path).set_duration(duration).resize((VIDEO_WIDTH, VIDEO_HEIGHT))
    
    # Add fade effects
    if fade_in > 0:
        clip = clip.fadein(fade_in)
    if fade_out > 0:
        clip = clip.fadeout(fade_out)
    
    return clip

def main():
    print("=" * 60)
    print("Creating Enhanced Promo Video with Transitions & Text Overlays")
    print("=" * 60)
    
    # Check if audio exists to determine target duration
    audio_path = os.path.join(PROMO_DIR, "voiceover.mp3")
    target_duration = None
    if os.path.exists(audio_path):
        audio = AudioFileClip(audio_path)
        target_duration = audio.duration
        audio.close()
        print(f"\n[INFO] Audio duration: {target_duration:.2f} seconds")
        print(f"[INFO] Video will be extended to match audio duration")
    else:
        print(f"\n[WARNING] Audio file not found. Using default clip durations.")
    
    # Step 1: Crop screenshots
    print("\n[1/5] Cropping screenshots...")
    screenshot_files = [
        "chat_threads_screen.png",
        "chat_thinking_thread.png",
        "chat_thread_settings.png",
        "chat_model_selection.png",
        "chat_text_only.png",
        "chat_image_thread.png",
        "server_screen.png",
        "add_server_screen.png"
    ]
    
    cropped_files = []
    for screenshot in screenshot_files:
        input_path = os.path.join(SCREENSHOTS_DIR, screenshot)
        if os.path.exists(input_path):
            output_name = f"cropped_{screenshot}.png"
            output_path = os.path.join(CROPPED_DIR, output_name)
            crop_screenshot(input_path, output_path)
            cropped_files.append((output_path, screenshot))
        else:
            print(f"Warning: {input_path} not found, skipping...")
    
    if not cropped_files:
        print("Error: No screenshots found!")
        return
    
    # Step 2: Prepare clips with descriptions and timing
    print("\n[2/5] Preparing video clips with text overlays...")
    
    descriptions = {
        "chat_threads_screen": "Manage All Your Chat Conversations",
        "chat_thinking_thread": "Real-Time AI Responses",
        "chat_thread_settings": "Customize Your Experience",
        "chat_model_selection": "Choose From Multiple AI Models",
        "chat_text_only": "Clean & Intuitive Interface",
        "chat_image_thread": "Send Images & Get AI Insights",
        "server_screen": "Connect to Your Ollama Server",
        "add_server_screen": "Easy Server Configuration"
    }
    
    # Calculate clip durations if we have target duration
    num_clips = 2 + len(cropped_files)  # icon + feature + screenshots
    base_duration_per_clip = 3.0  # Base duration per clip
    
    if target_duration:
        # Account for transitions
        total_transition_time = (num_clips - 1) * TRANSITION_DURATION
        available_time = target_duration - total_transition_time
        duration_per_clip = max(base_duration_per_clip, available_time / num_clips)
        print(f"[INFO] Adjusted clip duration: {duration_per_clip:.2f} seconds per clip")
    else:
        duration_per_clip = base_duration_per_clip
    
    clips = []
    
    # Add intro with icon
    icon_path = os.path.join(IMAGES_DIR, "icon.png")
    if os.path.exists(icon_path):
        print("Adding icon intro...")
        icon_clip = create_video_clip_with_text(
            icon_path, 
            duration_per_clip, 
            "Ollama Android Client",
            fade_in=TRANSITION_DURATION,
            fade_out=TRANSITION_DURATION
        )
        clips.append(icon_clip)
    
    # Add feature image
    feature_path = os.path.join(IMAGES_DIR, "feature.png")
    if os.path.exists(feature_path):
        print("Adding feature image...")
        feature_clip = create_video_clip_with_text(
            feature_path,
            duration_per_clip,
            "AI-Powered Conversations",
            fade_in=TRANSITION_DURATION,
            fade_out=TRANSITION_DURATION
        )
        clips.append(feature_clip)
    
    # Add all cropped screenshots
    for i, (cropped_path, original_name) in enumerate(cropped_files):
        description = descriptions.get(original_name, "")
        print(f"Adding clip: {original_name}")
        
        # Add fade transitions
        fade_in = TRANSITION_DURATION if i > 0 or len(clips) > 0 else 0
        fade_out = TRANSITION_DURATION if i < len(cropped_files) - 1 else TRANSITION_DURATION
        
        clip = create_video_clip_with_text(
            cropped_path,
            duration_per_clip,
            description,
            fade_in=fade_in,
            fade_out=fade_out
        )
        clips.append(clip)
    
    # Step 3: Concatenate with crossfade transitions
    print("\n[3/5] Concatenating clips with transitions...")
    
    # All clips already have fade in/out applied, just concatenate
    final_video = concatenate_videoclips(clips, method="compose")
    
    # If we have target duration, extend the last clip if needed
    if target_duration and final_video.duration < target_duration:
        print(f"\n[INFO] Extending video from {final_video.duration:.2f}s to {target_duration:.2f}s")
        extension_time = target_duration - final_video.duration
        # Extend the last clip
        last_clip = clips[-1]
        extended_clip = last_clip.set_duration(last_clip.duration + extension_time)
        # Rebuild final clips
        final_clips_extended = clips[:-1] + [extended_clip]
        final_video = concatenate_videoclips(
            final_clips_extended,
            method="compose"
        )
    
    # Step 4: Add audio if available
    print("\n[4/5] Adding audio...")
    if os.path.exists(audio_path):
        audio = AudioFileClip(audio_path)
        # Match audio to video duration
        if audio.duration > final_video.duration:
            audio = audio.subclip(0, final_video.duration)
        elif audio.duration < final_video.duration:
            # Extend audio with silence or loop
            from moviepy.audio.AudioClip import CompositeAudioClip
            silence_duration = final_video.duration - audio.duration
            from moviepy.audio.AudioClip import AudioClip
            # Create silent audio for the gap
            silence = AudioClip(lambda t: [0, 0], duration=silence_duration, fps=audio.fps)
            audio = CompositeAudioClip([audio, silence.set_start(audio.duration)])
        
        audio = audio.volumex(0.85)
        final_video = final_video.set_audio(audio)
        print(f"[INFO] Audio synchronized with video")
    else:
        print(f"[WARNING] Audio file not found, creating video without audio")
    
    # Step 5: Export video
    print("\n[5/5] Exporting final video...")
    output_path = os.path.join(PROMO_DIR, "promo_video_enhanced.mp4")
    
    final_video.write_videofile(
        output_path,
        fps=FPS,
        codec='libx264',
        audio_codec='aac' if os.path.exists(audio_path) else None,
        preset='medium',
        bitrate='8000k',
        threads=4
    )
    
    print(f"\n[SUCCESS] Enhanced promo video created!")
    print(f"[INFO] Location: {output_path}")
    print(f"[INFO] Duration: {final_video.duration:.2f} seconds")
    print(f"[INFO] Clips: {len(clips)}")
    print(f"[INFO] Transitions: {TRANSITION_DURATION}s fade/crossfade")
    
    # Cleanup
    final_video.close()
    for clip in clips:
        clip.close()
    if os.path.exists(audio_path):
        audio.close()

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\n[ERROR] {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

