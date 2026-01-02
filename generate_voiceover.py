#!/usr/bin/env python3
"""
Generate voiceover using edge-tts and add it to the promo video
"""

import os
import asyncio
import edge_tts

PROMO_DIR = "promo"
SCRIPT_PATH = os.path.join(PROMO_DIR, "voiceover_script.txt")
AUDIO_PATH = os.path.join(PROMO_DIR, "voiceover.mp3")

async def generate_voiceover():
    """Generate voiceover audio from script"""
    # Read the script
    with open(SCRIPT_PATH, 'r', encoding='utf-8') as f:
        script = f.read()
    
    # Clean up the script - remove headers and extra whitespace
    lines = [line.strip() for line in script.split('\n') if line.strip()]
    # Skip the header lines
    content_lines = []
    skip_next = False
    for line in lines:
        if '=' in line or 'Voiceover Script' in line:
            continue
        if line:
            content_lines.append(line)
    
    # Join into a single text
    text = ' '.join(content_lines)
    
    print(f"Generating voiceover for {len(text)} characters...")
    print(f"Text preview: {text[:100]}...")
    
    # Use a natural-sounding voice (you can change this)
    # Available voices: en-US-AriaNeural, en-US-JennyNeural, en-GB-SoniaNeural, etc.
    voice = "en-US-AriaNeural"  # Natural female voice
    
    communicate = edge_tts.Communicate(text, voice)
    await communicate.save(AUDIO_PATH)
    
    print(f"[SUCCESS] Voiceover saved to: {AUDIO_PATH}")

if __name__ == "__main__":
    asyncio.run(generate_voiceover())

