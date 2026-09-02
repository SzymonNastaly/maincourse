// Shared vocabulary for the MainCourse web mockups.
// Palette + type from Direction B; layout from Direction A.

export const T = {
  canvas:  '#EEF0F2',
  surface: '#FFFFFF',
  rail:    '#F8F9FA',
  sunken:  '#F5F7F8',
  ink:     '#14171C',
  body:    '#5B6570',
  muted:   '#9AA3AE',
  line:    '#E3E6EA',
  border:  '#DCE0E6',
  green:   '#16624B',
  greenDk: '#0F4736',
  greenBg: '#F1F7F4',
  lime:    '#CDEB7A',
  amber:   '#B07D12',
  danger:  '#B42318',
  dangerBg:'#FDF3F2',
};

export const SANS = "'IBM Plex Sans', system-ui, sans-serif";
export const MONO = "'IBM Plex Mono', ui-monospace, monospace";

const P = {
  fork: '<path d="M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2"/><path d="M7 2v20"/><path d="M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Zm0 0v7"/>',
  forkMark: '<path d="M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2"/><path d="M7 2v20"/>',
  search: '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
  cart: '<circle cx="8" cy="21" r="1"/><circle cx="19" cy="21" r="1"/><path d="M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12"/>',
  book: '<path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/>',
  plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
  minus: '<path d="M5 12h14"/>',
  chevR: '<path d="m9 18 6-6-6-6"/>',
  chevD: '<path d="m6 9 6 6 6-6"/>',
  chevUD: '<path d="m7 15 5 5 5-5"/><path d="m7 9 5-5 5 5"/>',
  heart: '<path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/>',
  crown: '<path d="M11.56 3.27a.5.5 0 0 1 .88 0l2.95 5.6a1 1 0 0 0 1.51.3l4.28-3.67a.5.5 0 0 1 .8.52l-2.84 10.25a1 1 0 0 1-.95.73H5.81a1 1 0 0 1-.96-.73L2.02 6.02a.5.5 0 0 1 .8-.52L7.1 9.17a1 1 0 0 0 1.51-.3z"/><path d="M5 21h14"/>',
  user: '<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
  users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  link: '<path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>',
  trash: '<path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>',
  menu: '<path d="M4 6h16"/><path d="M4 12h16"/><path d="M4 18h16"/>',
  back: '<path d="m12 19-7-7 7-7"/><path d="M19 12H5"/>',
  dots: '<circle cx="12" cy="12" r="1.4"/><circle cx="19" cy="12" r="1.4"/><circle cx="5" cy="12" r="1.4"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  camera: '<path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z"/><circle cx="12" cy="13" r="3"/>',
  image: '<rect width="18" height="18" x="3" y="3" rx="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.09-3.09a2 2 0 0 0-2.83 0L6 21"/>',
  clip: '<rect width="8" height="4" x="8" y="2" rx="1"/><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>',
  bell: '<path d="M10.27 21a2 2 0 0 0 3.46 0"/><path d="M3.26 15.33A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.67C19.41 13.96 18 12.5 18 8A6 6 0 0 0 6 8c0 4.5-1.41 5.96-2.74 7.33"/>',
  x: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
  ext: '<path d="M15 3h6v6"/><path d="M10 14 21 3"/><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>',
  clock: '<circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>',
  flame: '<path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.07-2.14-.22-4.05 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.15.43-2.29 1-3a2.5 2.5 0 0 0 2.5 2.5z"/>',
  logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/>',
  mail: '<rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>',
  spark: '<path d="M9.94 15.5A2 2 0 0 0 8.5 14.06l-6.14-1.58a.5.5 0 0 1 0-.96L8.5 9.94A2 2 0 0 0 9.94 8.5l1.58-6.14a.5.5 0 0 1 .96 0L14.06 8.5a2 2 0 0 0 1.44 1.44l6.14 1.58a.5.5 0 0 1 0 .96l-6.14 1.58a2 2 0 0 0-1.44 1.44l-1.58 6.14a.5.5 0 0 1-.96 0z"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6h.09A1.65 1.65 0 0 0 10.6 3.09V3a2 2 0 1 1 4 0v.09A1.65 1.65 0 0 0 16.11 4.6a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v.09a1.65 1.65 0 0 0 1.51 1.01H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>',
};

export function ic(name, size = 16, color = 'currentColor', sw = 1.9) {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round">${P[name]}</svg>`;
}
export function icFill(name, size = 16, color = 'currentColor') {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="${color}" stroke="${color}" stroke-width="1.6" stroke-linejoin="round">${P[name]}</svg>`;
}

// ---------- brand mark ----------

export function mark(size = 22) {
  const g = Math.round(size * 0.58);
  return `<div style="width: ${size}px; height: ${size}px; border-radius: 5px; background: ${T.green}; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">${ic('forkMark', g, T.lime, 2.4)}</div>`;
}

// ---------- desktop left rail ----------

const NAV = [
  { k: 'recipes',   label: 'Recipes',       icon: 'fork',   count: '128' },
  { k: 'search',    label: 'Search',        icon: 'search' },
  { k: 'shopping',  label: 'Shopping List', icon: 'cart',   badge: '7' },
  { k: 'cookbooks', label: 'Cookbooks',     icon: 'book' },
];

function navRow(item, active, dense = false) {
  const on = item.k === active;
  const fg = on ? '#FFFFFF' : T.body;
  const bg = on ? `background: ${T.green}; ` : '';
  const weight = on ? 500 : 400;
  let trail = '';
  if (item.badge) {
    const bBg = on ? 'rgba(255,255,255,0.2)' : T.green;
    trail = `<span style="font-family: ${MONO}; font-size: 10px; color: #FFFFFF; background: ${bBg}; border-radius: 4px; padding: 1px 5px; font-weight: 500;">${item.badge}</span>`;
  } else if (item.count) {
    trail = `<span style="font-family: ${MONO}; font-size: 10.5px; color: ${on ? 'rgba(255,255,255,0.75)' : T.muted};">${item.count}</span>`;
  }
  return `<div style="display: flex; align-items: center; gap: 10px; padding: ${dense ? '9px 11px' : '8px 11px'}; border-radius: 5px; ${bg}color: ${fg}; font-size: 13px; font-weight: ${weight};">
            ${ic(item.icon, 15, 'currentColor')}
            <span style="flex-grow: 1;">${item.label}</span>${trail}
          </div>`;
}

/** The persistent desktop sidebar. `active` is a NAV key, or 'settings' for the footer row. */
export function rail(active) {
  return `<aside style="width: 196px; flex-shrink: 0; background: ${T.rail}; border-right: 1px solid ${T.line}; display: flex; flex-direction: column; padding: 18px 0;">
  <div style="padding: 0 16px 18px 16px; display: flex; align-items: center; gap: 9px;">
    ${mark(22)}
    <span style="font-size: 14.5px; font-weight: 600; color: ${T.ink}; letter-spacing: -0.25px;">MainCourse</span>
  </div>

  <div style="margin: 0 12px 20px 12px; padding: 10px 11px; background: ${T.surface}; border: 1px solid ${T.border}; border-radius: 6px; display: flex; align-items: center; gap: 9px;">
    <div style="width: 24px; height: 24px; border-radius: 5px; background: ${T.green}; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">${ic('book', 12, T.lime, 2)}</div>
    <div style="flex-grow: 1; min-width: 0;">
      <div style="font-size: 12.5px; font-weight: 500; color: ${T.ink}; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">Our Kitchen</div>
      <div style="font-size: 10px; color: ${T.muted}; margin-top: 1px;">Shared &middot; 2 people</div>
    </div>
    ${ic('chevUD', 11, T.muted, 2.4)}
  </div>

  <nav style="display: flex; flex-direction: column; gap: 2px; padding: 0 10px;">
    ${NAV.map(i => navRow(i, active)).join('\n    ')}
  </nav>

  <div style="flex-grow: 1;"></div>

  <div style="padding: 0 20px 18px 20px;">
    <div style="font-size: 9.5px; letter-spacing: 1.1px; text-transform: uppercase; color: ${T.muted}; margin-bottom: 10px; font-weight: 500;">Collections</div>
    <div style="display: flex; flex-direction: column; gap: 9px;">
      ${[['Favorites', '24'], ['Weeknight', '41'], ['Baking', '18']].map(([n, c]) =>
        `<div style="display: flex; justify-content: space-between; align-items: center;"><span style="font-size: 12.5px; color: ${T.body};">${n}</span><span style="font-family: ${MONO}; font-size: 10.5px; color: ${T.muted};">${c}</span></div>`).join('\n      ')}
    </div>
  </div>

  <div style="border-top: 1px solid ${T.line}; margin: 0 12px; padding: 12px 0 0 0;">
    <div style="display: flex; align-items: center; gap: 9px; padding: 6px 8px; border-radius: 5px; ${active === 'settings' ? `background: ${T.green};` : ''}">
      <div style="width: 26px; height: 26px; border-radius: 5px; background: ${active === 'settings' ? 'rgba(255,255,255,0.18)' : T.line}; color: ${active === 'settings' ? '#FFFFFF' : T.body}; font-size: 10.5px; font-weight: 600; display: flex; align-items: center; justify-content: center;">SN</div>
      <div style="flex-grow: 1; min-width: 0;">
        <div style="font-size: 11.5px; color: ${active === 'settings' ? '#FFFFFF' : T.body}; font-weight: ${active === 'settings' ? 500 : 400};">Settings</div>
      </div>
      ${ic('chevR', 12, active === 'settings' ? 'rgba(255,255,255,0.7)' : T.muted, 2.2)}
    </div>
  </div>
</aside>`;
}

/** The rail's contents again, for the mobile drawer. */
export function drawerNav(active) {
  return `<div style="display: flex; flex-direction: column; gap: 3px;">
    ${NAV.map(i => navRow(i, active, true)).join('\n    ')}
  </div>`;
}

// ---------- shells ----------

export const DESKTOP_W = 872, DESKTOP_H = 780, MOBILE_W = 360, MOBILE_H = 780;

export function desktop(inner) {
  return `<div style="width: ${DESKTOP_W}px; height: ${DESKTOP_H}px; display: flex; background: ${T.surface}; border: 1px solid ${T.border}; border-radius: 8px; overflow: hidden; box-shadow: 0 10px 26px -18px rgba(20,23,28,0.4);">
${inner}
</div>`;
}

export function mobile(inner) {
  return `<div style="width: ${MOBILE_W}px; height: ${MOBILE_H}px; background: ${T.surface}; border: 1px solid ${T.border}; border-radius: 26px; overflow: hidden; display: flex; flex-direction: column; position: relative; box-shadow: 0 10px 26px -18px rgba(20,23,28,0.4);">
${inner}
</div>`;
}

/** Mobile top app bar: the rail lives behind the menu button on small screens. */
export function appBar({ right = '' } = {}) {
  return `<header style="flex-shrink: 0; display: flex; align-items: center; gap: 11px; padding: 13px 14px; border-bottom: 1px solid ${T.line}; background: ${T.surface};">
  <div style="width: 34px; height: 34px; border-radius: 6px; display: flex; align-items: center; justify-content: center; margin-left: -4px;">${ic('menu', 19, T.ink, 2)}</div>
  <div style="display: flex; align-items: center; gap: 8px; flex-grow: 1;">
    ${mark(20)}
    <span style="font-size: 14px; font-weight: 600; letter-spacing: -0.25px;">MainCourse</span>
  </div>
  ${right || `<div style="width: 28px; height: 28px; border-radius: 5px; background: ${T.line}; color: ${T.body}; font-size: 10px; font-weight: 600; display: flex; align-items: center; justify-content: center;">SN</div>`}
</header>`;
}

/** Mobile sub-page bar with a browser-style back affordance. */
export function backBar(label, right = '') {
  return `<header style="flex-shrink: 0; display: flex; align-items: center; gap: 10px; padding: 13px 14px; border-bottom: 1px solid ${T.line}; background: ${T.surface};">
  <div style="display: flex; align-items: center; gap: 7px; flex-grow: 1; min-width: 0; margin-left: -2px;">
    ${ic('back', 18, T.ink, 2)}
    <span style="font-size: 13.5px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${label}</span>
  </div>
  ${right}
</header>`;
}

// ---------- artboard wrapper ----------

export function artboard({ title, sub, frames, w = 1340, h = 920 }) {
  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500&family=IBM+Plex+Sans:wght@300;400;500;600&display=swap">
  <style>
    body { margin: 0; }
    a { color: ${T.green}; text-decoration: none; }
    a:hover { color: ${T.greenDk}; }
  </style>
</helmet>

<div style="width: ${w}px; height: ${h}px; box-sizing: border-box; padding: 28px; background: ${T.canvas}; font-family: ${SANS}; color: ${T.ink}; display: flex; flex-direction: column; gap: 20px;">
  <div style="display: flex; align-items: baseline; gap: 14px; border-bottom: 1px solid ${T.border}; padding-bottom: 14px; flex-shrink: 0;">
    <span style="font-size: 22px; font-weight: 600; letter-spacing: -0.4px;">${title}</span>
    <span style="font-size: 13px; color: #6B7280; font-weight: 300;">${sub}</span>
  </div>
  <div style="display: flex; gap: 34px; flex-grow: 1; min-height: 0;">
${frames.join('\n')}
  </div>
</div>
</x-dc>
</body>
</html>
`;
}

// ---------- small shared pieces ----------

export function btn(label, { kind = 'primary', icon = null, size = 'md' } = {}) {
  const pad = size === 'sm' ? '7px 12px' : '9px 14px';
  const fs = size === 'sm' ? '12px' : '12.5px';
  const styles = {
    primary: `background: ${T.green}; color: #FFFFFF; border: 1px solid ${T.green};`,
    outline: `background: ${T.surface}; color: ${T.ink}; border: 1px solid ${T.border};`,
    quiet:   `background: transparent; color: ${T.body}; border: 1px solid transparent;`,
    danger:  `background: ${T.surface}; color: ${T.danger}; border: 1px solid #EFD5D3;`,
  };
  return `<div style="display: inline-flex; align-items: center; gap: 7px; ${styles[kind]} border-radius: 6px; padding: ${pad}; font-size: ${fs}; font-weight: 500; white-space: nowrap;">${icon ? ic(icon, 14, 'currentColor', 2.2) : ''}<span>${label}</span></div>`;
}

export function searchField({ w = '172px', placeholder = 'Search recipes', kbd = true, value = null } = {}) {
  return `<div style="display: flex; align-items: center; gap: 8px; background: ${T.surface}; border: 1px solid ${T.border}; border-radius: 6px; padding: 8px 11px; width: ${w}; box-sizing: border-box;">
    ${ic('search', 14, T.muted, 2.2)}
    <span style="font-size: 12.5px; color: ${value ? T.ink : T.muted}; font-weight: ${value ? 400 : 300}; flex-grow: 1;">${value || placeholder}</span>
    ${kbd ? `<span style="font-family: ${MONO}; font-size: 9.5px; color: ${T.muted}; border: 1px solid ${T.line}; border-radius: 3px; padding: 1px 4px;">&#8984;K</span>` : ''}
  </div>`;
}

export function chips(items, { active = 0, sort = 'Recently updated', size = 'md' } = {}) {
  const fs = size === 'sm' ? '11.5px' : '12px';
  const pad = size === 'sm' ? '5px 11px' : '5px 12px';
  const list = items.map((label, i) => i === active
    ? `<span style="font-size: ${fs}; padding: ${pad}; border-radius: 5px; background: ${T.ink}; color: #FFFFFF; font-weight: 500;">${label}</span>`
    : `<span style="font-size: ${fs}; padding: ${pad}; border-radius: 5px; border: 1px solid ${T.border}; color: ${T.body};">${label}</span>`).join('\n    ');
  const tail = sort ? `<div style="flex-grow: 1;"></div>
    <div style="display: flex; align-items: center; gap: 5px; font-size: 11.5px; color: ${T.muted}; font-weight: 300;"><span>${sort}</span>${ic('chevD', 11, 'currentColor', 2.4)}</div>` : '';
  return `<div style="display: flex; align-items: center; gap: 7px; flex-shrink: 0;">
    ${list}${tail}
  </div>`;
}

export const PHOTOS = {
  chicken:  'linear-gradient(150deg, #C9B08F 0%, #A2794F 100%)',
  fritters: 'linear-gradient(150deg, #BFCFA8 0%, #7E9560 100%)',
  galette:  'linear-gradient(150deg, #DEC0A0 0%, #B8834F 100%)',
  lentil:   'linear-gradient(150deg, #C8BBA6 0%, #94795C 100%)',
  beans:    'linear-gradient(150deg, #DCBCAD 0%, #A96450 100%)',
  focaccia: 'linear-gradient(150deg, #D0CABB 0%, #99907C 100%)',
  cacio:    'linear-gradient(150deg, #DED6C2 0%, #ADA283 100%)',
  beetroot: 'linear-gradient(150deg, #C6A8B4 0%, #8E5F72 100%)',
  risotto:  'linear-gradient(150deg, #E3CE9C 0%, #C09A44 100%)',
  shakshuka:'linear-gradient(150deg, #B7C9A4 0%, #6F8757 100%)',
  ragu:     'linear-gradient(150deg, #D2AE9A 0%, #9C6248 100%)',
  pancakes: 'linear-gradient(150deg, #E0CDAC 0%, #BE9A62 100%)',
};

export function heartBadge(size = 25) {
  const r = size >= 24 ? 6 : 5;
  return `<div style="position: absolute; top: ${size >= 24 ? 8 : 6}px; right: ${size >= 24 ? 8 : 6}px; width: ${size}px; height: ${size}px; border-radius: ${r}px; background: rgba(255,255,255,0.92); display: flex; align-items: center; justify-content: center;">${icFill('heart', size >= 24 ? 12 : 11, T.green)}</div>`;
}

/** Image-topped recipe card (Direction A's card, in B's radii + mono meta). */
export function card({ name, meta, photo, fav = false, imgH = 128, fs = 14.5, metaFs = 10.5 }) {
  return `<div style="display: flex; flex-direction: column; gap: 10px;">
        <div style="position: relative; height: ${imgH}px; border-radius: 6px; background: ${photo};">${fav ? heartBadge(imgH > 118 ? 25 : 22) : ''}</div>
        <div>
          <div style="font-size: ${fs}px; font-weight: 500; line-height: 1.3; margin-bottom: 5px; letter-spacing: -0.2px;">${name}</div>
          <div style="font-family: ${MONO}; font-size: ${metaFs}px; color: ${T.muted};">${meta}</div>
        </div>
      </div>`;
}

export function grid(cards, { cols = 3, gap = '24px 20px' } = {}) {
  return `<div style="display: grid; grid-template-columns: repeat(${cols}, minmax(0, 1fr)); gap: ${gap};">
      ${cards.join('\n      ')}
    </div>`;
}

export const RECIPES = [
  { name: 'Miso Butter Roast Chicken',      meta: '1h 25m &middot; serves 4', photo: PHOTOS.chicken,  fav: true },
  { name: 'Zucchini Fritters, Dill Yoghurt', meta: '35m &middot; serves 2',    photo: PHOTOS.fritters },
  { name: 'Brown Butter Apple Galette',      meta: '1h 10m &middot; serves 6', photo: PHOTOS.galette },
  { name: 'Lentil Soup with Celeriac',       meta: '50m &middot; serves 4',    photo: PHOTOS.lentil },
  { name: 'Harissa Tomato Braised Beans',    meta: '45m &middot; serves 4',    photo: PHOTOS.beans,    fav: true },
  { name: 'Sourdough Focaccia',              meta: '4h 30m &middot; serves 8', photo: PHOTOS.focaccia },
  { name: 'Cacio e Pepe',                    meta: '20m &middot; serves 2',    photo: PHOTOS.cacio },
  { name: 'Roasted Beetroot &amp; Labneh',   meta: '55m &middot; serves 4',    photo: PHOTOS.beetroot },
  { name: 'Saffron Risotto Milanese',        meta: '40m &middot; serves 4',    photo: PHOTOS.risotto,  fav: true },
];
