"""Generate the checked-in recipe artwork via OpenAI's Images API."""

import base64
import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

MODEL = "gpt-image-2.5-sunburst"


def generate(root: Path, recipe_slug: str | None = None):
    key = os.environ.get("OPENAI_API_KEY")
    if not key:
        raise RuntimeError("Set OPENAI_API_KEY to generate recipe photos.")
    recipes = json.loads((root / "screenshots/recipes.json").read_text())["recipes"]
    if recipe_slug:
        recipes = [r for r in recipes if r["slug"] == recipe_slug]
        if not recipes:
            raise ValueError(f"Unknown recipe: {recipe_slug}")
    style = (root / "screenshots/photo-style.txt").read_text()
    directory = root / "screenshots/photos"
    directory.mkdir(parents=True, exist_ok=True)
    for recipe in recipes:
        destination = directory / f'{recipe["slug"]}.png'
        if destination.exists():
            print(f"Keeping existing {destination.name}", flush=True)
            continue
        prompt = style + "\n" + recipe["photo"]
        parameters = {
            "model": MODEL, "prompt": prompt, "n": 1,
            "size": "1536x1024", "quality": "high", "output_format": "png",
        }
        print(f'Generating {recipe["slug"]} with {MODEL}…', flush=True)
        request = Request(
            "https://api.openai.com/v1/images/generations",
            data=json.dumps(parameters).encode(),
            headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
        )
        try:
            with urlopen(request, timeout=600) as response:
                result = json.load(response)
        except HTTPError as error:
            # Print the API error, never request headers or credentials.
            raise RuntimeError(f"Image API {error.code}: {error.read().decode()}") from None
        image = base64.b64decode(result["data"][0]["b64_json"], validate=True)
        if not image.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("The Image API did not return a PNG.")
        temporary = destination.with_suffix(".tmp")
        temporary.write_bytes(image)
        temporary.replace(destination)
        provenance = {
            **parameters, "generated_at": datetime.now(timezone.utc).isoformat(),
            "sha256": hashlib.sha256(image).hexdigest(),
            "usage": result.get("usage"),
            "revised_prompt": result["data"][0].get("revised_prompt"),
        }
        destination.with_suffix(".json").write_text(json.dumps(provenance, indent=2) + "\n")
        print(f"Saved {destination}", flush=True)
