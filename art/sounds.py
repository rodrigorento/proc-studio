"""Make the farm's sounds from code, as farmkit makes its art.

    python3 -I art/sounds.py

Writes short mono WAVs to app/src/main/res/raw/: a soft pop for a harvest, water pouring for a watering, seeds rattling
in their packet for a planting. Seeded, so every run writes the same files. Standard library only.
"""
import math
import random
import struct
import wave
from pathlib import Path

RAW = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"
RATE = 22050
PEAK = 0.5  # -6 dBFS: the app plays them quietly on top of that


def seconds(n):
    return int(n * RATE)


def lowpass(samples, cutoff):
    """One-pole low-pass at about [cutoff] Hz."""
    a = 1 - math.exp(-2 * math.pi * cutoff / RATE)
    out, y = [], 0.0
    for x in samples:
        y += a * (x - y)
        out.append(y)
    return out


def highpass(samples, cutoff):
    return [x - y for x, y in zip(samples, lowpass(samples, cutoff))]


def pop(rng):
    """A cork-like pop: a quick falling tone over a soft click."""
    n = seconds(0.16)
    out, phase = [], 0.0
    for i in range(n):
        t = i / RATE
        freq = 180 + 340 * math.exp(-t / 0.025)
        phase += 2 * math.pi * freq / RATE
        env = min(1.0, t / 0.003) * math.exp(-t / 0.035)
        out.append(env * (math.sin(phase) + 0.25 * math.sin(2 * phase)))
    click = lowpass([rng.uniform(-1, 1) * math.exp(-i / seconds(0.004)) for i in range(n)], 3000)
    return [s + 0.6 * c for s, c in zip(out, click)]


def pour(rng):
    """Water from a can: a soft rush with bubbles rising through it, swelling in and trailing off."""
    n = seconds(1.1)
    rush = highpass(lowpass([rng.uniform(-1, 1) for _ in range(n)], 2200), 300)
    # The stream wavers.
    wobble, level, out = 0.0, 0.0, []
    for i in range(n):
        if i % seconds(0.05) == 0:
            wobble = rng.uniform(0.55, 1.0)
        level += (wobble - level) * 0.002
        out.append(rush[i] * level * 0.9)
    # Bubbles: short rising chirps.
    for _ in range(26):
        start = rng.randrange(seconds(0.08), n - seconds(0.06))
        length = seconds(rng.uniform(0.015, 0.035))
        base = rng.uniform(800, 1800)
        amp = rng.uniform(0.15, 0.4)
        phase = 0.0
        for j in range(length):
            k = j / length
            phase += 2 * math.pi * base * (1 + 0.8 * k) / RATE
            out[start + j] += amp * math.sin(phase) * math.sin(math.pi * k)
    for i in range(n):
        t, left = i / RATE, (n - i) / RATE
        out[i] *= min(1.0, t / 0.12) * min(1.0, left / 0.3)
    return out


def seeds(rng):
    """Seeds shaken in a paper packet: a few shakes, each a spray of tiny ticks, fading."""
    n = seconds(0.85)
    out = [0.0] * n
    for shake in range(6):
        at = seconds(0.03 + shake * 0.13)
        strength = 0.9 ** shake
        for _ in range(40):
            start = at + seconds(rng.uniform(0, 0.07))
            length = seconds(0.002)
            amp = strength * rng.uniform(0.3, 1.0)
            for j in range(length):
                if start + j < n:
                    out[start + j] += amp * rng.uniform(-1, 1) * (1 - j / length)
    return highpass(lowpass(out, 7000), 1500)


def write(name, samples):
    top = max(abs(s) for s in samples) or 1.0
    frames = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s / top * PEAK)) * 32767)) for s in samples)
    RAW.mkdir(parents=True, exist_ok=True)
    with wave.open(str(RAW / f"{name}.wav"), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(frames)
    print(f"{name}.wav  {len(samples) / RATE:.2f} s  {(RAW / f'{name}.wav').stat().st_size // 1024} KB")


if __name__ == "__main__":
    for name, make in [("sound_pop", pop), ("sound_pour", pour), ("sound_seeds", seeds)]:
        write(name, make(random.Random(name)))
