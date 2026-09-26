"""Local, plan-driven capture. Platform-independent PNGs feed Koubou templates."""

import argparse
import contextlib
import fcntl
import hashlib
import html
import json
import os
import re
import signal
import socket
import struct
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import URLError
from urllib.request import urlopen

from .photos import generate

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "screenshots"
OUTPUT = SOURCE / "output"
STATE = ROOT / "storage/screenshots"
KOUBOU_VERSION = "0.20.0"


def read_json(path):
    return json.loads(Path(path).read_text())


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(data, indent=2) + "\n")
    temporary.replace(path)


def run(*args, env=None, check=True, cwd=ROOT):
    result = subprocess.run([str(a) for a in args], cwd=cwd, env=env, text=True,
                            capture_output=True, check=False)
    if check and result.returncode:
        raise RuntimeError(f"Command failed: {' '.join(str(a) for a in args)}\n"
                           f"{result.stdout}\n{result.stderr}")
    return result.stdout.strip()


def backend_env():
    return {**os.environ, "RAILS_ENV": "development", "MAINCOURSE_SCREENSHOTS": "1",
            "DATABASE_URL": f"sqlite3:{STATE / 'database.sqlite3'}", "WEB_CONCURRENCY": "0"}


def seed():
    STATE.mkdir(parents=True, exist_ok=True)
    print("Preparing the dedicated screenshot database…", flush=True)
    run(ROOT / "bin/rails", "db:migrate", env=backend_env())
    print(run(ROOT / "bin/rails", "runner", "scripts/screenshots/seed.rb", env=backend_env()), flush=True)
    return read_json(STATE / "seed.json")


@contextlib.contextmanager
def backend(log):
    with socket.socket() as probe:
        if probe.connect_ex(("127.0.0.1", 3100)) == 0:
            raise RuntimeError("Port 3100 is occupied. Stop that server before capturing.")
    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open("w") as output:
        process = subprocess.Popen(
            [str(ROOT / "bin/rails"), "server", "-b", "127.0.0.1", "-p", "3100",
             "--pid", str(STATE / "server.pid")],
            cwd=ROOT, env=backend_env(), stdout=output, stderr=subprocess.STDOUT,
            start_new_session=True,
        )
        try:
            deadline = time.monotonic() + 60
            while True:
                if process.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError(f"Screenshot backend failed to start; see {log}")
                try:
                    with urlopen("http://127.0.0.1:3100/up", timeout=1) as response:
                        if response.status == 200:
                            break
                except (URLError, TimeoutError):
                    time.sleep(0.2)
            yield
        finally:
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()


def selection(value, available, label):
    selected = list(available) if value == "all" else value.split(",")
    if not selected or len(set(selected)) != len(selected) or any(s not in available for s in selected):
        raise ValueError(f"Invalid {label}: {value}. Choose from {', '.join(available)}.")
    return selected


def simulator(config, runtime):
    devices = json.loads(run("xcrun", "simctl", "list", "devices", "available", "--json"))["devices"]
    if runtime not in devices:
        raise RuntimeError(f"Install {runtime} in Xcode, or change screenshots/catalog.json runtime.")
    candidates = [d for d in devices[runtime] if d["name"] == config["simulator_name"]]
    if len(candidates) > 1:
        raise RuntimeError(f'Duplicate screenshot simulators named {config["simulator_name"]}')
    if candidates:
        device = candidates[0]
        if device["deviceTypeIdentifier"] != config["device_type"]:
            raise RuntimeError("Screenshot simulator type differs from catalog; rename or remove it in Xcode.")
        udid = device["udid"]
        booted = device["state"] == "Booted"
    else:
        udid = run("xcrun", "simctl", "create", config["simulator_name"], config["device_type"], runtime)
        booted = False
    if not booted:
        run("xcrun", "simctl", "boot", udid)
    run("xcrun", "simctl", "bootstatus", udid, "-b")
    run("xcrun", "simctl", "ui", udid, "appearance", "light")
    run("xcrun", "simctl", "status_bar", udid, "override", "--time", "9:41",
        "--dataNetwork", "wifi", "--wifiMode", "active", "--wifiBars", "3",
        "--cellularMode", "active", "--cellularBars", "4",
        "--batteryState", "discharging", "--batteryLevel", "100")
    return udid


def built_app():
    print("Building the iOS app with bin/ios-build…", flush=True)
    output = run(ROOT / "bin/ios-build")
    print(output, flush=True)
    match = re.search(r"Using simulator: .*\(([A-Fa-f0-9-]{36})\)", output)
    if not match:
        raise RuntimeError("Cannot resolve bin/ios-build's simulator. Pass the built app with --app.")
    result = json.loads(run(
        "xcodebuildmcp", "simulator", "get-app-path",
        "--project-path", ROOT / "hauptgang-ios/Hauptgang.xcodeproj",
        "--scheme", "Hauptgang", "--configuration", "Debug", "--platform", "iOS Simulator",
        "--simulator-id", match[1], "--output", "json", cwd=ROOT / "hauptgang-ios",
    ))
    if result.get("didError"):
        raise RuntimeError(f"Cannot locate built app: {result}")
    return Path(result["data"]["artifacts"]["appPath"])


def resolve_plan(path, recipe_ids, locale):
    text = path.read_text()
    for slug, recipe_id in recipe_ids.items():
        text = text.replace("{{" + slug + "}}", str(recipe_id))
    if "{{" in text:
        raise ValueError(f"Unresolved recipe selector in {path}")
    plan = json.loads(text)
    plan["app"]["launch_arguments"] += ["-AppleLanguages", f"({locale.split('-')[0]})",
                                        "-AppleLocale", locale.replace("-", "_")]
    return plan


def wait_for_images(path, timeout=60):
    deadline = time.monotonic() + timeout
    earliest = time.monotonic() + 1.5  # Allow the final navigation animation to settle.
    while time.monotonic() < deadline:
        if path.exists():
            status = read_json(path)
            if status["failures"]:
                raise RuntimeError("Screenshot app failed: " + "; ".join(status["failures"]))
            if (status["stage"] == "ready" and status["pending_images"] == 0
                    and time.time() - status["updated_at"] >= 1
                    and time.monotonic() >= earliest):
                return
        time.sleep(0.2)
    raise RuntimeError(f"App/images never became ready; inspect {path}")


def navigate(plan, path, udid):
    """Run ASC segments, using AXe's typed selector for ambiguous SwiftUI nodes."""
    pending = []
    segment = 0

    def flush():
        nonlocal segment
        if not pending:
            return
        segment += 1
        segment_path = path.with_name(f"{path.stem}-{segment}.json")
        write_json(segment_path, {**plan, "steps": list(pending)})
        result = run("asc", "screenshots", "run", "--plan", segment_path, "--udid", udid,
                     "--output-dir", path.parent, "--output", "json")
        segment_path.with_suffix(".log").write_text(result + "\n")
        pending.clear()

    for step in plan["steps"]:
        if step["action"] == "tap" and "element_type" in step:
            flush()
            selector = "id" if "id" in step else "label"
            result = typed_tap(step, selector, udid)
            segment += 1
            path.with_name(f"{path.stem}-{segment}.log").write_text(result + "\n")
        else:
            pending.append(step)
    flush()


def typed_tap(step, selector, udid):
    try:
        return run("axe", "tap", f"--{selector}", step[selector],
                   "--element-type", step["element_type"], "--wait-timeout", "15", "--udid", udid)
    except RuntimeError as error:
        if "Multiple (" not in str(error):
            raise
        # iPad UIKit can expose the exact same tab twice. Resolve only duplicate
        # nodes at one identical rectangle; distinct targets must still fail.
        tree = json.loads(run("axe", "describe-ui", "--udid", udid))
        frame = duplicate_target_frame(tree, step, selector)
        if frame is None:
            raise
        x, y, width, height = frame
        return run("axe", "tap", "-x", x + width / 2, "-y", y + height / 2, "--udid", udid)


def duplicate_target_frame(tree, step, selector):
    frames = []
    key = "AXUniqueId" if selector == "id" else "AXLabel"

    def visit(node):
        if isinstance(node, list):
            for child in node:
                visit(child)
        elif isinstance(node, dict):
            if node.get(key) == step[selector] and node.get("type") == step["element_type"] and node.get("enabled"):
                rectangle = node.get("frame", {})
                if rectangle.get("width", 0) > 0 and rectangle.get("height", 0) > 0:
                    frames.append(tuple(rectangle[k] for k in ("x", "y", "width", "height")))
            visit(node.get("children", []))

    visit(tree)
    return frames[0] if len(frames) > 1 and len(set(frames)) == 1 else None


def png_size(path):
    with path.open("rb") as image:
        header = image.read(24)
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"Not a PNG: {path}")
    return list(struct.unpack(">II", header[16:24]))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def capture(args, catalog):
    devices = selection(args.devices, catalog["devices"], "devices")
    screens = selection(args.screen, catalog["screens"], "screens")
    selection(args.locale, catalog["locales"], "locale")
    app = Path(args.app).resolve() if args.app else built_app()
    if not (app / "Hauptgang").is_file():
        raise ValueError(f"Not a built Hauptgang simulator app: {app}")
    fixture = seed()
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S")
    work = OUTPUT / "runs" / run_id
    work.mkdir(parents=True, exist_ok=True)
    manifest = {"run": run_id, "status": "running", "captures": []}
    write_json(work / "manifest.json", manifest)
    try:
        with backend(work / "backend.log"):
            for device_name in devices:
                config = catalog["devices"][device_name]
                udid = simulator(config, catalog["runtime"])
                print(f"Capturing {device_name} ({udid})…", flush=True)
                run("xcrun", "simctl", "install", udid, app)
                container = Path(run("xcrun", "simctl", "get_app_container", udid, catalog["bundle_id"], "data"))
                status = container / "Documents/screenshot-status.json"
                for screen in screens:
                    run("xcrun", "simctl", "terminate", udid, catalog["bundle_id"], check=False)
                    status.unlink(missing_ok=True)
                    scene = catalog["screens"][screen]
                    plan = resolve_plan(SOURCE / "plans" / f'{scene["plan"]}.json', fixture["recipes"], args.locale)
                    plan_file = work / device_name / f"{screen}.json"
                    write_json(plan_file, plan)
                    if plan["steps"][0]["action"] != "launch":
                        raise ValueError(f"Capture plan must start with launch: {plan_file}")
                    launch_file = plan_file.with_name(f"{screen}-launch.json")
                    write_json(launch_file, {**plan, "steps": plan["steps"][:1]})
                    run("asc", "screenshots", "run", "--plan", launch_file, "--udid", udid,
                        "--output-dir", plan_file.parent, "--output", "json")
                    # ASC wait_for aborts on a transient AXe query error. Wait for
                    # native startup before asking AXe for the first UI hierarchy.
                    wait_for_images(status)
                    navigation_file = plan_file.with_name(f"{screen}-navigation.json")
                    write_json(navigation_file, {**plan, "steps": plan["steps"][1:]})
                    navigate(read_json(navigation_file), navigation_file, udid)
                    wait_for_images(status)
                    shot_plan = {"version": 1, "app": plan["app"],
                                 "steps": [{"action": "screenshot", "name": screen}]}
                    shot_file = plan_file.with_name(f"{screen}-capture.json")
                    write_json(shot_file, shot_plan)
                    run("asc", "screenshots", "run", "--plan", shot_file, "--udid", udid,
                        "--output-dir", plan_file.parent, "--output", "json")
                    raw = plan_file.parent / f"{screen}.png"
                    if png_size(raw) != config["size"]:
                        raise RuntimeError(f"Unexpected {device_name} screenshot dimensions: {png_size(raw)}")
                    destination = OUTPUT / "raw/ios" / args.locale / device_name / raw.name
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    raw.replace(destination)
                    metadata = {
                        "screen": screen, "platform": "ios", "locale": args.locale, "device": device_name,
                        "runtime": catalog["runtime"], "udid": udid, "size": config["size"],
                        "captured_at": datetime.now(timezone.utc).isoformat(), "run": run_id,
                        "git_commit": run("git", "rev-parse", "HEAD"),
                        "git_dirty": bool(run("git", "status", "--porcelain")),
                        "app_sha256": digest(app / "Hauptgang"), "sha256": digest(destination),
                        "plan": plan, "recipes_sha256": digest(SOURCE / "recipes.json"),
                        "photos": {p.name: digest(p) for p in sorted((SOURCE / "photos").glob("*.png"))},
                    }
                    write_json(destination.with_suffix(".json"), metadata)
                    manifest["captures"].append(str(destination.relative_to(OUTPUT)))
                    write_json(work / "manifest.json", manifest)
                    print(f"Saved {destination.relative_to(ROOT)}", flush=True)
                run("xcrun", "simctl", "terminate", udid, catalog["bundle_id"], check=False)
        manifest["status"] = "complete"
    except BaseException as error:
        manifest["status"] = "failed"
        manifest["error"] = str(error)
        raise
    finally:
        write_json(work / "manifest.json", manifest)
    gallery()


def koubou(config, config_path):
    write_json(config_path, config)  # JSON is a YAML subset; no Python YAML dependency needed.
    result = run("kou", "generate", config_path, "--output", "json")
    config_path.with_suffix(".log").write_text(result + "\n")


def render(args, catalog):
    from .render import render_artwork
    if KOUBOU_VERSION not in run("kou", "--version"):
        raise RuntimeError(f"Install the pinned renderer: uv tool install koubou=={KOUBOU_VERSION}")
    devices = selection(args.devices, catalog["devices"], "devices")
    screens = selection(args.screen, catalog["screens"], "screens")
    selection(args.locale, catalog["locales"], "locale")
    render_artwork(args, catalog, devices, screens)
    gallery()


def gallery():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    cards = []
    for directory in ("raw", "rendered"):
        for path in sorted((OUTPUT / directory).rglob("*.png")):
            relative = path.relative_to(OUTPUT).as_posix()
            safe = html.escape(relative, quote=True)
            cards.append(f'<figure><a href="{safe}"><img loading="lazy" src="{safe}"></a>'
                         f'<figcaption>{safe}</figcaption></figure>')
    document = '<!doctype html><html lang="en"><meta charset="utf-8"><title>MainCourse screenshots</title>'
    document += '<style>body{background:#EEF0F2;color:#14171C;font:16px system-ui;padding:24px}'
    document += 'main{display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:24px}'
    document += 'figure{margin:0}img{width:100%;height:400px;object-fit:contain}figcaption{overflow-wrap:anywhere}</style>'
    document += '<h1>MainCourse screenshot library</h1><main>' + ''.join(cards) + '</main></html>'
    (OUTPUT / "index.html").write_text(document)
    print(f"Gallery: {OUTPUT / 'index.html'}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("doctor", help="Check local tool versions and artwork")
    commands.add_parser("seed", help="Reset the dedicated local showcase data")
    commands.add_parser("gallery", help="Rebuild the local image contact sheet")
    commands.add_parser("list", help="List named screens and devices")
    photos = commands.add_parser("photos", help="Generate missing recipe photos with GPT Image 2.5 Sunburst")
    photos.add_argument("--recipe", help="Generate one recipe slug; existing photos are kept")
    for name in ("capture", "render"):
        command = commands.add_parser(name)
        command.add_argument("--platform", choices=["ios"], default="ios")
        command.add_argument("--devices", default="iphone,ipad", help="Comma-separated devices, or all")
        command.add_argument("--screen", default="all", help="One or more comma-separated named screens, or all")
        command.add_argument("--locale", default="en-US")
        if name == "capture":
            command.add_argument("--app", help="Use an already built Debug simulator .app (skip build)")
        else:
            command.add_argument("--preset", choices=["app-store", "social", "story", "website", "framed"],
                                 default="app-store")
    args = parser.parse_args()
    catalog = read_json(SOURCE / "catalog.json")
    try:
        if args.command == "photos":
            generate(ROOT, args.recipe)
        elif args.command == "list":
            print(json.dumps(catalog, indent=2))
        elif args.command == "gallery":
            gallery()
        elif args.command == "doctor":
            for command in [("asc", "version"), ("axe", "--version"), ("kou", "--version"), ("xcodebuild", "-version")]:
                print(run(*command))
            for recipe in read_json(SOURCE / "recipes.json")["recipes"]:
                path = SOURCE / "photos" / f'{recipe["slug"]}.png'
                print(f'{recipe["slug"]}: {"ready" if path.exists() else "missing photo"}')
            print(f"Capture requires the runtime in screenshots/catalog.json; render requires Koubou {KOUBOU_VERSION}.")
        else:
            STATE.mkdir(parents=True, exist_ok=True)
            with (STATE / "operation.lock").open("w") as lock:
                try:
                    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                except BlockingIOError:
                    raise RuntimeError("Another screenshot seed/capture/render command is running.") from None
                if args.command == "seed":
                    seed()
                elif args.command == "capture":
                    capture(args, catalog)
                elif args.command == "render":
                    render(args, catalog)
    except (RuntimeError, ValueError, OSError) as error:
        parser.exit(1, f"Error: {error}\n")
