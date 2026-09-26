"""Koubou device frames plus reusable, local-font HTML marketing templates."""

import html
import json
import re
from datetime import datetime, timezone
from pathlib import Path

from .main import (
    KOUBOU_VERSION,
    OUTPUT,
    ROOT,
    SOURCE,
    digest,
    koubou,
    png_size,
    read_json,
    run,
    write_json,
)


def theme_variables():
    css = (ROOT / "app/assets/tailwind/application.css").read_text()
    return {name: re.search(rf"--color-{name}:\s*(#[0-9A-Fa-f]+)", css).group(1)
            for name in ("canvas", "accent", "lime", "ink", "body")}


def metadata_path(image):
    # ASC treats every file in an upload directory as an image, including JSON.
    return (OUTPUT / "metadata" / image.relative_to(OUTPUT / "rendered")).with_suffix(".json")


def produce(config, work, name, destination):
    config["project"]["output_dir"] = str(work / name)
    koubou(config, work / f"{name}.json")
    images = list((work / name).rglob("*.png"))
    if len(images) != 1:
        raise RuntimeError(f"Expected one rendered PNG, found {len(images)} in {work / name}")
    image = images[0]
    if png_size(image) != config["project"]["output_size"]:
        raise RuntimeError(f"Wrong rendered dimensions in {image}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    layout = image.with_suffix(".layout.json")
    if layout.exists():
        layout_destination = metadata_path(destination).with_suffix(".layout.json")
        layout_destination.parent.mkdir(parents=True, exist_ok=True)
        layout.replace(layout_destination)
    image.replace(destination)


def render_artwork(args, catalog, devices, screens):
    copy = read_json(SOURCE / "copy" / f"{args.locale}.json")
    if args.preset == "website" and set(devices) != {"iphone", "ipad"}:
        raise ValueError("The website composition uses both devices; pass --devices iphone,ipad.")
    # Validate every input before starting a batch. Never fall back to another locale/device.
    sources = {}
    for device in devices:
        for screen in screens:
            raw = OUTPUT / "raw" / args.platform / args.locale / device / f"{screen}.png"
            metadata = read_json(raw.with_suffix(".json"))
            if metadata["sha256"] != digest(raw) or png_size(raw) != catalog["devices"][device]["size"]:
                raise ValueError(f"Raw capture changed or has wrong dimensions: {raw}")
            sources[device, screen] = raw
            if screen not in copy:
                raise ValueError(f"Missing {args.locale} marketing copy for {screen}")
            if not copy[screen].get("headline"):
                raise ValueError(f"Missing feature headline for {screen}")

    work = OUTPUT / "runs" / ("render-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%f"))
    frames = {}
    for device in devices:
        profile = catalog["devices"][device]
        geometry = json.loads(run("kou", "inspect-frame", profile["frame"], "--output", "json"))
        if [geometry["screen_bounds"][key] for key in ("width", "height")] != profile["size"]:
            raise ValueError(f"Device frame doesn't match raw screen dimensions: {profile['frame']}")
        for screen in screens:
            raw = sources[device, screen]
            framed = OUTPUT / "rendered/framed" / args.platform / args.locale / device / f"{screen}.png"
            config = {
                "project": {"name": "MainCourse", "device": profile["frame"],
                            "output_size": [geometry["frame_size"][k] for k in ("width", "height")]},
                "defaults": {"background": {"type": "transparent"}},
                "screenshots": {screen: {"content": [
                    {"type": "image", "asset": str(raw), "position": ["50%", "50%"], "frame": True, "scale": 1}
                ]}},
            }
            produce(config, work, f"frame-{device}-{screen}", framed)
            write_json(metadata_path(framed), {
                "renderer": f"koubou {KOUBOU_VERSION}", "frame": profile["frame"],
                "source": str(raw.relative_to(OUTPUT)), "source_sha256": digest(raw), "sha256": digest(framed),
            })
            frames[device, screen] = framed
    if args.preset == "framed":
        return

    font_assets = {"font_regular": str(ROOT / "app/assets/fonts/IBMPlexSans-400.woff2"),
                   "font_medium": str(ROOT / "app/assets/fonts/IBMPlexSans-500.woff2")}
    for screen in screens:
        for device in (["both"] if args.preset == "website" else devices):
            template_name = "website.html" if device == "both" else "feature.html"
            if args.preset == "app-store":
                template_name = "app-store.html"
            template = SOURCE / "templates" / template_name
            if device == "both":
                size = [2400, 1800]
                assets = {"phone": str(frames["iphone", screen]), "tablet": str(frames["ipad", screen])}
                profile = catalog["devices"]["iphone"]
            else:
                profile = catalog["devices"][device]
                size = {"app-store": profile["size"], "social": [1080, 1350], "story": [1080, 1920]}[args.preset]
                assets = {"screen": str(frames[device, screen])}
                if args.preset in ("social", "story"):
                    assets["logo"] = str(ROOT / "app/assets/images/logo.png")
            name = f'{catalog["screens"][screen]["order"]:02d}-{screen}'
            destination = OUTPUT / "rendered" / args.preset / args.platform / args.locale / device / f"{name}.png"
            variables = {**theme_variables(), **{k: html.escape(v) for k, v in copy[screen].items()},
                         "preset": args.preset, "device": device}
            config = {
                "project": {"name": "MainCourse", "device": profile["frame"], "output_size": size},
                "screenshots": {name: {"template": str(template), "frame": False,
                                       "variables": variables, "assets": {**assets, **font_assets}}},
            }
            produce(config, work, f"{args.preset}-{device}-{screen}", destination)
            write_json(metadata_path(destination), {
                "renderer": f"koubou {KOUBOU_VERSION}", "preset": args.preset,
                "locale": args.locale, "screen": screen, "device": device,
                "template_sha256": digest(template), "variables": variables,
                "sources": {key: {"path": path, "sha256": digest(Path(path))}
                            for key, path in assets.items()},
                "sha256": digest(destination),
            })
            print(f"Rendered {destination.relative_to(ROOT)}", flush=True)
    if args.preset == "app-store":
        for device in devices:
            directory = OUTPUT / "rendered/app-store" / args.platform / args.locale / device
            print(run("asc", "screenshots", "validate", "--path", directory,
                      "--device-type", catalog["devices"][device]["display_type"], "--output", "json"))
