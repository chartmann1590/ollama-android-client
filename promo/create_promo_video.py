#!/usr/bin/env python3
"""
Script to create a promo video for Ollama Android Client
- Crops screenshots to remove top/bottom navbars
- Creates a video with transitions
- Adds voiceover and includes icon/feature images
"""

import os
from PIL import Image
import numpy as np
from moviepy.editor import (
    ImageClip, CompositeVideoClip, TextClip, 
    concatenate_videoclips, AudioFileClip
)
import subprocess
import sys

# Directories
SCREENSHOTS_DIR = "screenshots"
IMAGES_DIR = "images"
PROMO_DIR = "promo"
CROPPED_DIR = os.path.join(PROMO_DIR, "cropped")

# Create directories
os.makedirs(PROMO_DIR, exist_ok=True)
os.makedirs(CROPPED_DIR, exist_ok=True)

# Video settings
VIDEO_WIDTH = 1080
VIDEO_HEIGHT = 1920  # Portrait orientation for mobile screenshots
FPS = 30
SCREEN_DURATION = 3  # seconds per screenshot
TRANSITION_DURATION = 0.5  # seconds for transitions

def crop_screenshot(image_path, output_path, crop_top=0.1, crop_bottom=0.15):
    """
    Crop screenshot to remove top and bottom navbar areas
    crop_top: percentage to crop from top (default 10%)
    crop_bottom: percentage to crop from bottom (default 15%)
    """
    img = Image.open(image_path)
    width, height = img.size
    
    # Calculate crop dimensions
    top_crop = int(height * crop_top)
    bottom_crop = int(height * crop_bottom)
    
    # Crop: (left, top, right, bottom)
    cropped = img.crop((0, top_crop, width, height - bottom_crop))
    
    # Resize to video dimensions while maintaining aspect ratio
    cropped.thumbnail((VIDEO_WIDTH, VIDEO_HEIGHT), Image.Resampling.LANCZOS)
    
    # Create new image with video dimensions (centered)
    final_img = Image.new('RGB', (VIDEO_WIDTH, VIDEO_HEIGHT), color='black')
    paste_x = (VIDEO_WIDTH - cropped.width) // 2
    paste_y = (VIDEO_HEIGHT - cropped.height) // 2
    final_img.paste(cropped, (paste_x, paste_y))
    
    final_img.save(output_path, 'PNG')
    print(f"Cropped: {os.path.basename(image_path)} -> {os.path.basename(output_path)}")
    return output_path

def create_text_overlay(text, duration, position='bottom', fontsize=40):
    """Create a text overlay clip"""
    try:
        txt_clip = TextClip(
            text,
            fontsize=fontsize,
            color='white',
            font='Arial-Bold',
            stroke_color='black',
            stroke_width=2
        ).set_duration(duration).set_position(position)
        return txt_clip
    except Exception as e:
        print(f"Warning: Could not create text overlay ({e}). Continuing without text.")
        return None

def create_video_clip(image_path, duration, text=None):
    """Create a video clip from an image"""
    clip = ImageClip(image_path).set_duration(duration).resize((VIDEO_WIDTH, VIDEO_HEIGHT))
    
    if text:
        txt_clip = create_text_overlay(text, duration, position=('center', 'bottom'))
        if txt_clip:
            return CompositeVideoClip([clip, txt_clip])
    return clip

def main():
    print("=" * 60)
    print("Creating Promo Video for Ollama Android Client")
    print("=" * 60)
    
    # Step 1: Crop all screenshots
    print("\n[1/4] Cropping screenshots...")
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
            output_name = f"cropped_{screenshot}"
            output_path = os.path.join(CROPPED_DIR, output_name)
            crop_screenshot(input_path, output_path)
            cropped_files.append((output_path, screenshot))
        else:
            print(f"Warning: {input_path} not found, skipping...")
    
    if not cropped_files:
        print("Error: No screenshots found to process!")
        return
    
    # Step 2: Create video clips with descriptions
    print("\n[2/4] Creating video clips...")
    
    # Descriptions for each screen
    descriptions = {
        "chat_threads_screen.png": "Manage all your chat conversations",
        "chat_thinking_thread.png": "Real-time AI responses",
        "chat_thread_settings.png": "Customize your chat experience",
        "chat_model_selection.png": "Choose from multiple AI models",
        "chat_text_only.png": "Clean and intuitive chat interface",
        "chat_image_thread.png": "Send images and get AI insights",
        "server_screen.png": "Connect to your Ollama server",
        "add_server_screen.png": "Easy server configuration"
    }
    
    clips = []
    
    # Add intro with icon
    icon_path = os.path.join(IMAGES_DIR, "icon.png")
    if os.path.exists(icon_path):
        print("Adding icon intro...")
        icon_clip = create_video_clip(icon_path, 2, "Ollama Android Client")
        clips.append(icon_clip)
    
    # Add feature image
    feature_path = os.path.join(IMAGES_DIR, "feature.png")
    if os.path.exists(feature_path):
        print("Adding feature image...")
        feature_clip = create_video_clip(feature_path, 2, "AI-Powered Conversations")
        clips.append(feature_clip)
    
    # Add all cropped screenshots
    for cropped_path, original_name in cropped_files:
        description = descriptions.get(original_name, "")
        clip = create_video_clip(cropped_path, SCREEN_DURATION, description)
        clips.append(clip)
        print(f"Added clip: {original_name}")
    
    # Step 3: Concatenate clips
    print("\n[3/4] Concatenating video clips...")
    final_video = concatenate_videoclips(clips, method="compose")
    
    # Step 4: Export video
    print("\n[4/4] Exporting video...")
    output_path = os.path.join(PROMO_DIR, "promo_video.mp4")
    
    # Write video file
    final_video.write_videofile(
        output_path,
        fps=FPS,
        codec='libx264',
        audio_codec='aac',
        preset='medium',
        bitrate='8000k'
    )
    
    print(f"\n[SUCCESS] Promo video created successfully!")
    print(f"[INFO] Location: {output_path}")
    print(f"[INFO] Duration: {final_video.duration:.1f} seconds")
    
    # Cleanup
    final_video.close()
    for clip in clips:
        clip.close()
    
    # Create voiceover script
    voiceover_script = os.path.join(PROMO_DIR, "voiceover_script.txt")
    with open(voiceover_script, 'w') as f:
        f.write("Ollama Android Client - Voiceover Script\n")
        f.write("=" * 60 + "\n\n")
        f.write("Welcome to Ollama Android Client.\n\n")
        f.write("An intuitive Android app for interacting with Ollama AI models.\n\n")
        f.write("Manage all your chat conversations in one place.\n\n")
        f.write("Experience real-time AI responses with streaming support.\n\n")
        f.write("Customize your chat experience with flexible settings.\n\n")
        f.write("Choose from multiple AI models to suit your needs.\n\n")
        f.write("Enjoy a clean and intuitive chat interface.\n\n")
        f.write("Send images and get AI insights instantly.\n\n")
        f.write("Connect easily to your Ollama server.\n\n")
        f.write("Configure servers with just a few taps.\n\n")
        f.write("Download Ollama Android Client today!\n")
    
    print(f"\n[INFO] Voiceover script saved to: {voiceover_script}")
    print("\n[TIP] To add voiceover:")
    print("   1. Record audio using the script above")
    print("   2. Save as promo/voiceover.mp3")
    print("   3. Run: python add_voiceover.py")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\n[ERROR] {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

