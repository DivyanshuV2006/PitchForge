#!/usr/bin/env python3
"""Render per-note instrument samples into the app's asset convention:

    app/src/main/assets/samples/<timbre>/{octave}_{token}.wav

Timbres are rendered from the royalty-free GeneralUser GS SoundFont (S. Christian
Collins), which is licensed for use in any project. Rendering uses FluidSynth
(reverb/chorus off) + ffmpeg (mono / 44.1 kHz / s16, trim, fade, loudness-normalize).

Instrument set covers Study A / Van Hedger et al. 2019 acoustic timbres (piano is
shipped separately; square is synthesized in-app) plus violin/trumpet for variety.

Prerequisites:
    - fluidsynth, ffmpeg, python3 on PATH
    - Download the SoundFont, e.g.:
        curl -L -o GeneralUser-GS.sf2 \
          https://raw.githubusercontent.com/mrbumpy409/GeneralUser-GS/main/GeneralUser-GS.sf2

Usage:
    python3 gen_samples.py --sf2 GeneralUser-GS.sf2 --assets ../app/src/main/assets/samples
    python3 gen_samples.py --sf2 GeneralUser-GS.sf2 --assets ../app/src/main/assets/samples \\
        --only cello,clarinet,guitar,harpsichord
"""
import argparse
import os
import struct
import subprocess
import sys

# GM program numbers (0-based). Piano samples are shipped separately; square is synthesized.
INSTRUMENTS = {
    "violin": 40,
    "flute": 73,
    "trumpet": 56,
    "cello": 42,
    "clarinet": 71,
    "guitar": 25,       # Acoustic Guitar (steel)
    "harpsichord": 6,
}

# token order aligned with NoteName semitone index (C=0 .. B=11); sharps use 's'
TOKENS = ["C", "Cs", "D", "Ds", "E", "F", "Fs", "G", "Gs", "A", "As", "B"]
OCTAVES = [3, 4, 5]

TPQ = 480          # ticks per quarter note
NOTE_TICKS = 1920  # ~2.0 s at 120 bpm
VELOCITY = 100


def vlq(n):
    out = bytearray([n & 0x7F])
    n >>= 7
    while n:
        out.insert(0, (n & 0x7F) | 0x80)
        n >>= 7
    return bytes(out)


def write_midi(path, program, note):
    t = bytearray()
    t += vlq(0) + bytes([0xFF, 0x51, 0x03]) + struct.pack(">I", 500000)[1:]  # 120 bpm
    t += vlq(0) + bytes([0xC0, program & 0x7F])                              # program change
    t += vlq(0) + bytes([0x90, note & 0x7F, VELOCITY])                       # note on
    t += vlq(NOTE_TICKS) + bytes([0x80, note & 0x7F, 0])                     # note off
    t += vlq(0) + bytes([0xFF, 0x2F, 0x00])                                  # end of track
    header = b"MThd" + struct.pack(">IHHH", 6, 0, 1, TPQ)
    with open(path, "wb") as f:
        f.write(header + b"MTrk" + struct.pack(">I", len(t)) + bytes(t))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sf2", required=True, help="path to GeneralUser-GS.sf2")
    ap.add_argument("--assets", required=True, help="path to assets/samples dir")
    ap.add_argument("--tmp", default="/tmp/pitchforge_midi")
    ap.add_argument("--only", default="", help="comma-separated timbres to render (default: all)")
    args = ap.parse_args()

    only = {t.strip() for t in args.only.split(",") if t.strip()}
    instruments = {k: v for k, v in INSTRUMENTS.items() if not only or k in only}
    if only - instruments.keys():
        unknown = ", ".join(sorted(only - INSTRUMENTS.keys()))
        print(f"Unknown timbre(s): {unknown}", file=sys.stderr)
        return 1

    os.makedirs(args.tmp, exist_ok=True)
    total = fail = 0
    for timbre, program in instruments.items():
        outdir = os.path.join(args.assets, timbre)
        os.makedirs(outdir, exist_ok=True)
        for octave in OCTAVES:
            for semitone, token in enumerate(TOKENS):
                midi_note = (octave + 1) * 12 + semitone
                mid = os.path.join(args.tmp, f"{timbre}_{octave}_{token}.mid")
                raw = os.path.join(args.tmp, f"{timbre}_{octave}_{token}_raw.wav")
                final = os.path.join(outdir, f"{octave}_{token}.wav")
                write_midi(mid, program, midi_note)

                if subprocess.run(
                    ["fluidsynth", "-F", raw, "-r", "44100", "-g", "0.9", "-q",
                     "-o", "synth.reverb.active=0", "-o", "synth.chorus.active=0",
                     args.sf2, mid],
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                ).returncode or not os.path.exists(raw):
                    print(f"FLUIDSYNTH FAIL {timbre} {octave} {token}"); fail += 1; continue

                if subprocess.run(
                    ["ffmpeg", "-y", "-i", raw, "-ac", "1", "-ar", "44100", "-sample_fmt", "s16",
                     "-af", "apad=pad_dur=3,atrim=0:2.6,afade=t=out:st=2.3:d=0.3,"
                            "loudnorm=I=-18:TP=-1.5:LRA=11", final],
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                ).returncode or not os.path.exists(final):
                    print(f"FFMPEG FAIL {timbre} {octave} {token}"); fail += 1; continue
                total += 1
                print(f"OK {timbre}/{octave}_{token}.wav")
    print(f"Rendered {total} samples, {fail} failures")
    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main())
