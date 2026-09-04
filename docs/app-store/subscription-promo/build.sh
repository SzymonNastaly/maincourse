set -euo pipefail
SP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SP/../../.." && pwd)"
FONTS="$ROOT/app/assets/fonts"
LOGO="$ROOT/app/assets/images/logo.png"

F400=$(base64 -i "$FONTS/IBMPlexSans-400.woff2")
F500=$(base64 -i "$FONTS/IBMPlexSans-500.woff2")
F600=$(base64 -i "$FONTS/IBMPlexSans-600.woff2")
LOGO_B64=$(base64 -i "$LOGO")

emit () {
  variant="$1"; out="$2"
  if [ "$variant" = "monthly" ]; then
    BG="background: linear-gradient(155deg, #16624B 0%, #0F4736 100%);"
    NAME_COLOR="#FFFFFF"; SUB_COLOR="rgba(255,255,255,0.62)"
    RULE="rgba(255,255,255,0.18)"
    TERM="Monthly"; TERM_COLOR="#CDEB7A"
    BADGE_BG="rgba(255,255,255,0.12)"; BADGE_FG="#FBF3E0"; BADGE_BD="rgba(251,243,224,0.35)"
  else
    BG="background: #EEF0F2;"
    NAME_COLOR="#14171C"; SUB_COLOR="#5B6570"
    RULE="#DCE0E6"
    TERM="Yearly"; TERM_COLOR="#16624B"
    BADGE_BG="#FBF3E0"; BADGE_FG="#B07D12"; BADGE_BD="#E8D9B4"
  fi

  cat > "$SP/$out.html" <<HTML
<!doctype html><html><head><meta charset="utf-8"><style>
@font-face{font-family:'IBM Plex Sans';font-weight:400;font-display:block;src:url(data:font/woff2;base64,$F400) format('woff2');}
@font-face{font-family:'IBM Plex Sans';font-weight:500;font-display:block;src:url(data:font/woff2;base64,$F500) format('woff2');}
@font-face{font-family:'IBM Plex Sans';font-weight:600;font-display:block;src:url(data:font/woff2;base64,$F600) format('woff2');}
*{margin:0;padding:0;box-sizing:border-box;}
html,body{width:1024px;height:1024px;}
body{$BG font-family:'IBM Plex Sans',sans-serif;
  display:flex;flex-direction:column;align-items:center;justify-content:center;
  -webkit-font-smoothing:antialiased;overflow:hidden;}
/* Cream ground is the app icon's own, sampled from AppIcon 1024 — keeps the
   leather mark reading as the mark rather than a brown smudge on green. */
.tile{width:256px;height:256px;border-radius:58px;
  background:linear-gradient(160deg,#F1DFA9 0%,#E6D097 100%);
  display:flex;align-items:center;justify-content:center;margin-bottom:72px;}
.tile img{height:186px;width:auto;display:block;}
.name{font-size:92px;font-weight:600;letter-spacing:-3.2px;color:$NAME_COLOR;line-height:1;}
.badge{margin-top:30px;display:inline-flex;align-items:center;gap:13px;
  padding:13px 30px 14px;border-radius:999px;background:$BADGE_BG;border:2px solid $BADGE_BD;}
.badge svg{width:29px;height:29px;stroke:$BADGE_FG;}
.badge span{font-size:30px;font-weight:600;letter-spacing:5px;color:$BADGE_FG;}
.rule{width:300px;height:2px;background:$RULE;margin:62px 0 42px;}
.term{font-size:62px;font-weight:500;letter-spacing:-1.1px;color:$TERM_COLOR;line-height:1;}
.line{margin-top:26px;font-size:31px;font-weight:400;color:$SUB_COLOR;letter-spacing:-0.3px;}
</style></head><body>
  <div class="tile"><img src="data:image/png;base64,$LOGO_B64" alt=""></div>
  <div class="name">Hauptgang</div>
  <div class="badge">
    <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11.562 3.266a.5.5 0 0 1 .876 0L15.39 8.87a1 1 0 0 0 1.516.294L21.183 5.5a.5.5 0 0 1 .798.519l-2.834 10.246a1 1 0 0 1-.956.734H5.81a1 1 0 0 1-.957-.734L2.02 6.02a.5.5 0 0 1 .798-.519l4.276 3.664a1 1 0 0 0 1.516-.294z"></path><path d="M5 21h14"></path></svg>
    <span>PRO</span>
  </div>
  <div class="rule"></div>
  <div class="term">$TERM</div>
  <div class="line">Unlimited recipe imports</div>
</body></html>
HTML

  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
    --headless --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --screenshot="$SP/$out-raw.png" --window-size=1024,1024 \
    "$SP/$out.html" >/dev/null 2>&1

  magick "$SP/$out-raw.png" -background white -alpha remove -alpha off \
    -colorspace sRGB -depth 8 -strip PNG24:"$SP/$out.png"
}

emit monthly hauptgang-pro-monthly
emit yearly  hauptgang-pro-yearly
for f in hauptgang-pro-monthly hauptgang-pro-yearly; do
  echo "  $f.png -> $(magick identify -format '%wx%h %[channels] %[depth]-bit %b' "$SP/$f.png")"
done
