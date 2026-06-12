#!/usr/bin/env python3
"""Generate the XMLTV fixture for Play Store screenshot capture.

XMLTV carries absolute timestamps, so a committed snapshot goes stale —
regenerate right before capturing so the guide shows a populated now/next:

    python3 docs/screenshots/generate-epg.py docs/screenshots-epg.xml

Programmes cover now-6h .. now+18h in blocks per channel. Titles are
generic / public-domain (Blender films) per the store-listing positioning
rules: no broadcaster names, no real shows, no sports leagues.
"""
import sys
from datetime import datetime, timedelta, timezone
from xml.sax.saxutils import escape

# (tvg-id, display name, [(title, desc, minutes), ...] cycled)
CHANNELS = [
    ("demo.news", "Demo News", [
        ("Morning Headlines", "A round-up of demo headlines to start the day.", 60),
        ("Newsroom Live", "Rolling demo coverage from the fixture newsroom.", 120),
        ("World Report", "Demo correspondents report from nowhere in particular.", 60),
        ("Evening Bulletin", "The day's demo events, summarised.", 60),
    ]),
    ("demo.weather", "Public Weather Feed", [
        ("Weather Update", "Always sunny in the fixture. Updated hourly.", 60),
    ]),
    ("demo.sports", "Demo Sports", [
        ("Matchday Preview", "Build-up to a match that will never kick off.", 60),
        ("Live: Stadium Cam", "Uninterrupted views of an empty demo stadium.", 150),
        ("Sports Tonight", "Highlights of the day's demo fixtures.", 90),
    ]),
    ("demo.sports2", "Test Pattern Sports", [
        ("Test Pattern Marathon", "Colour bars, but make it competitive.", 180),
    ]),
    ("demo.movies", "Public Domain Movie", [
        ("Big Buck Bunny", "A giant rabbit takes gentle revenge. Blender Foundation, 2008.", 90),
        ("Sintel", "A girl searches for her dragon. Blender Foundation, 2010.", 90),
        ("Tears of Steel", "Robots and regret in old Amsterdam. Blender Foundation, 2012.", 90),
    ]),
    ("demo.movies2", "Open Cinema", [
        ("Elephants Dream", "Two characters explore a strange machine. Blender Foundation, 2006.", 90),
        ("Cosmos Laundromat", "A sheep gets a second chance. Blender Foundation, 2015.", 90),
    ]),
    ("demo.music", "Demo Music Channel", [
        ("Morning Mix", "Easy listening for fixture mornings.", 120),
        ("Afternoon Sessions", "Live-in-the-studio demo performances.", 120),
        ("Late Night Loops", "Ambient loops until the small hours.", 180),
    ]),
    ("demo.kids", "Demo Kids", [
        ("Cartoon Block", "Back-to-back demo cartoons.", 90),
        ("Story Time", "A narrator reads public-domain tales.", 60),
        ("Puzzle Hour", "Interactive-ish puzzles for patient children.", 60),
    ]),
    ("demo.docs", "Open Documentary", [
        ("Ocean Wonders", "Demo footage of very calm water.", 90),
        ("Mountain Worlds", "Peaks, valleys, and royalty-free vistas.", 90),
        ("City Stories", "A wander through an unnamed demo city.", 90),
    ]),
]

WINDOW_BACK_H, WINDOW_FWD_H = 6, 18
FMT = "%Y%m%d%H%M%S +0000"


def main(out_path: str) -> None:
    now = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    start_window = now - timedelta(hours=WINDOW_BACK_H)
    end_window = now + timedelta(hours=WINDOW_FWD_H)

    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<tv generator-info-name="GoorTV screenshot fixture">',
    ]
    for tvg_id, name, _ in CHANNELS:
        lines.append(f'  <channel id="{tvg_id}"><display-name>{escape(name)}</display-name></channel>')
    for tvg_id, _, schedule in CHANNELS:
        t, i = start_window, 0
        while t < end_window:
            title, desc, minutes = schedule[i % len(schedule)]
            stop = t + timedelta(minutes=minutes)
            lines.append(
                f'  <programme start="{t.strftime(FMT)}" stop="{stop.strftime(FMT)}" channel="{tvg_id}">'
                f'<title>{escape(title)}</title><desc>{escape(desc)}</desc></programme>'
            )
            t, i = stop, i + 1
    lines.append('</tv>')

    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"wrote {out_path} ({len(lines)} lines, window {start_window:%Y-%m-%d %H:%M} .. {end_window:%H:%M} UTC)")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "screenshots-epg.xml")
