import { writeFileSync } from 'node:fs';
import {
  T, MONO, SANS, ic, icFill, mark, rail, drawerNav, mobile, appBar, artboard,
  btn, chips, card, grid, RECIPES, DESKTOP_W, DESKTOP_H,
} from './parts.mjs';

const w = (s) => writeFileSync(s.file, s.html);

const ALERT = `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="${T.danger}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg>`;
const SPINNER = `<svg width="16" height="16" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="${T.line}" stroke-width="3"/><path d="M12 3a9 9 0 0 1 9 9" fill="none" stroke="${T.green}" stroke-width="3" stroke-linecap="round"/></svg>`;

const field = (labelText, value, { mono = false, muted = false, trail = '' } = {}) => `
      <div style="margin-bottom: 14px;">
        <div style="font-size: 11.5px; font-weight: 500; color: ${T.body}; margin-bottom: 6px;">${labelText}</div>
        <div style="display: flex; align-items: center; gap: 8px; background: ${T.surface}; border: 1px solid ${T.border}; border-radius: 6px; padding: 11px 12px;">
          <span style="flex-grow: 1; font-size: 13px; font-family: ${mono ? MONO : SANS}; color: ${muted ? T.muted : T.ink}; font-weight: ${muted ? 300 : 400}; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${value}</span>${trail}
        </div>
      </div>`;

const wideBtn = (labelText, { kind = 'primary', icon = null } = {}) => {
  const styles = {
    primary: `background: ${T.green}; color: #FFFFFF; border: 1px solid ${T.green};`,
    dark:    `background: ${T.ink}; color: #FFFFFF; border: 1px solid ${T.ink};`,
    outline: `background: ${T.surface}; color: ${T.ink}; border: 1px solid ${T.border};`,
    quiet:   `background: transparent; color: ${T.body}; border: 1px solid transparent;`,
  };
  return `<div style="display: flex; align-items: center; justify-content: center; gap: 8px; ${styles[kind]} border-radius: 6px; padding: 12px; font-size: 13.5px; font-weight: 500;">${icon ? ic(icon, 15, 'currentColor', 2.2) : ''}<span>${labelText}</span></div>`;
};

// ============================================================ 7. IMPORT

const recipesBehind = `${rail('recipes')}
  <main style="flex-grow: 1; display: flex; flex-direction: column; min-width: 0;">
    <div style="padding: 26px 30px 0 30px; display: flex; align-items: flex-start; justify-content: space-between; gap: 20px;">
      <div>
        <h1 style="font-size: 29px; font-weight: 600; margin: 0 0 5px 0; letter-spacing: -0.7px;">All Recipes</h1>
        <div style="font-size: 12.5px; color: ${T.muted}; font-weight: 300;"><span style="font-family: ${MONO}; font-size: 12px;">128</span> recipes in Our Kitchen</div>
      </div>
    </div>
    <div style="padding: 20px 30px 16px 30px; border-bottom: 1px solid ${T.line};">${chips(['All', 'Favorites', 'Weeknight', 'Vegetarian', 'Baking'])}</div>
    <div style="padding: 24px 30px; flex-grow: 1; overflow: hidden;">${grid(RECIPES.map(r => card({ ...r })))}</div>
  </main>`;

const optionTile = (icon, title, sub) => `
        <div style="flex: 1; border: 1px solid ${T.border}; border-radius: 7px; padding: 15px 13px; display: flex; flex-direction: column; gap: 8px;">
          <div style="width: 32px; height: 32px; border-radius: 7px; background: ${T.sunken}; display: flex; align-items: center; justify-content: center;">${ic(icon, 16, T.body, 1.9)}</div>
          <div>
            <div style="font-size: 13px; font-weight: 500; margin-bottom: 3px;">${title}</div>
            <div style="font-size: 11px; color: ${T.muted}; font-weight: 300; line-height: 1.4;">${sub}</div>
          </div>
        </div>`;

const importDesktop = `<div style="position: relative; width: ${DESKTOP_W}px; height: ${DESKTOP_H}px; border: 1px solid ${T.border}; border-radius: 8px; overflow: hidden; box-shadow: 0 10px 26px -18px rgba(20,23,28,0.4);">
    <div style="display: flex; width: 100%; height: 100%; background: ${T.surface};">${recipesBehind}</div>
    <div style="position: absolute; inset: 0; background: rgba(20,23,28,0.42);"></div>
    <div style="position: absolute; top: 132px; left: 226px; width: 420px; background: ${T.surface}; border-radius: 10px; box-shadow: 0 24px 60px -20px rgba(20,23,28,0.5); overflow: hidden;">
      <div style="display: flex; align-items: center; padding: 17px 20px; border-bottom: 1px solid ${T.line};">
        <span style="font-size: 16px; font-weight: 600; flex-grow: 1; letter-spacing: -0.3px;">Add a recipe</span>
        ${ic('x', 17, T.muted, 2.2)}
      </div>
      <div style="padding: 20px;">
        ${field('Paste a link', 'https://smittenkitchen.com/2026/miso-butter-roast-chicken')}
        ${wideBtn('Import recipe', { icon: 'plus' })}
        <div style="display: flex; align-items: center; gap: 12px; margin: 18px 0;">
          <div style="flex-grow: 1; height: 1px; background: ${T.line};"></div>
          <span style="font-size: 11px; color: ${T.muted}; font-weight: 300;">or</span>
          <div style="flex-grow: 1; height: 1px; background: ${T.line};"></div>
        </div>
        <div style="display: flex; gap: 10px;">
          ${optionTile('image', 'Upload a photo', 'A cookbook page or a screenshot')}
          ${optionTile('camera', 'Take a photo', 'Straight from your camera')}
        </div>
        <div style="font-size: 11.5px; color: ${T.muted}; font-weight: 300; line-height: 1.55; margin-top: 16px;">We read the page or the photo and pull out the ingredients, steps and timings. You can fix anything afterwards.</div>
      </div>
    </div>
  </div>`;

const importMobile = mobile(`${appBar()}
  <div style="padding: 18px 16px 0 16px; flex-shrink: 0;">
    <h1 style="font-size: 23px; font-weight: 600; margin: 0 0 4px 0; letter-spacing: -0.5px;">All Recipes</h1>
    <div style="font-size: 11.5px; color: ${T.muted}; font-weight: 300;">Our Kitchen &middot; <span style="font-family: ${MONO}; font-size: 11px;">128</span></div>
  </div>
  <div style="padding: 16px 16px 0 16px; display: flex; flex-direction: column; gap: 10px; flex-shrink: 0;">
    <div style="display: flex; align-items: center; gap: 11px; background: ${T.surface}; border: 1px solid ${T.border}; border-radius: 7px; padding: 12px 13px;">
      ${SPINNER}
      <div style="flex-grow: 1; min-width: 0;">
        <div style="font-size: 12.5px; font-weight: 500;">Importing recipe&hellip;</div>
        <div style="font-family: ${MONO}; font-size: 10px; color: ${T.muted}; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">smittenkitchen.com/2026/miso-butter&hellip;</div>
      </div>
    </div>
    <div style="background: ${T.dangerBg}; border: 1px solid #EFD5D3; border-radius: 7px; padding: 12px 13px;">
      <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 7px;">
        ${ALERT}
        <span style="font-size: 12.5px; font-weight: 500; color: ${T.danger}; flex-grow: 1;">Couldn&rsquo;t read that page</span>
      </div>
      <div style="font-size: 11.5px; color: ${T.body}; font-weight: 300; line-height: 1.5; margin-bottom: 10px;">The site blocked our reader. Paste the text or a photo instead.</div>
      <div style="display: flex; gap: 8px;">
        ${btn('Try again', { kind: 'outline', size: 'sm' })}
        ${btn('Dismiss', { kind: 'quiet', size: 'sm' })}
      </div>
    </div>
  </div>
  <div style="padding: 18px 16px 0 16px; flex-grow: 1; overflow: hidden;">
    ${grid(RECIPES.slice(0, 4).map(r => card({ ...r, imgH: 112, fs: 13.5, metaFs: 10 })), { cols: 2, gap: '18px 14px' })}
  </div>`);

w({
  file: 'Import.dc.html',
  html: artboard({
    title: 'Importing a recipe',
    sub: 'Link, upload or camera on the left; the in-progress and failed states on the right.',
    frames: [importDesktop, importMobile],
  }),
});

// ============================================================ 8. ACCOUNT

const authPanel = (inner) =>
  `<div style="width: 420px; height: 780px; box-sizing: border-box; background: ${T.surface}; border: 1px solid ${T.border}; border-radius: 8px; box-shadow: 0 10px 26px -18px rgba(20,23,28,0.4); padding: 40px 36px; display: flex; flex-direction: column;">${inner}</div>`;

const signIn = authPanel(`
    <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 44px;">
      ${mark(28)}
      <span style="font-size: 17px; font-weight: 600; letter-spacing: -0.3px;">MainCourse</span>
    </div>
    <h1 style="font-size: 26px; font-weight: 600; margin: 0 0 8px 0; letter-spacing: -0.7px;">Sign in to your kitchen</h1>
    <div style="font-size: 13px; color: ${T.muted}; font-weight: 300; margin-bottom: 28px;">Your recipes and shopping list, on every device.</div>
    ${field('Email', 'szymon@example.com')}
    ${field('Password', '&bull;&bull;&bull;&bull;&bull;&bull;&bull;&bull;&bull;&bull;&bull;&bull;')}
    <div style="text-align: right; margin: -6px 0 20px 0;"><span style="font-size: 11.5px; color: ${T.green}; font-weight: 500;">Forgot password?</span></div>
    ${wideBtn('Sign in')}
    <div style="display: flex; align-items: center; gap: 12px; margin: 22px 0;">
      <div style="flex-grow: 1; height: 1px; background: ${T.line};"></div>
      <span style="font-size: 11px; color: ${T.muted}; font-weight: 300;">or</span>
      <div style="flex-grow: 1; height: 1px; background: ${T.line};"></div>
    </div>
    <div style="display: flex; flex-direction: column; gap: 10px;">
      ${wideBtn('Continue with Apple', { kind: 'dark' })}
      ${wideBtn('Continue with Google', { kind: 'outline' })}
    </div>
    <div style="flex-grow: 1;"></div>
    <div style="font-size: 12.5px; color: ${T.muted}; font-weight: 300; text-align: center;">New to MainCourse? <span style="color: ${T.green}; font-weight: 500;">Create an account</span></div>`);

const proFeature = (text) => `
      <div style="display: flex; align-items: center; gap: 10px; padding: 7px 0;">
        <div style="width: 18px; height: 18px; border-radius: 50%; background: ${T.greenBg}; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">${ic('check', 11, T.green, 3)}</div>
        <span style="font-size: 13px; color: ${T.body};">${text}</span>
      </div>`;

const plan = (name, price, per, note, selected) => `
      <div style="flex: 1; border: ${selected ? `1.5px solid ${T.green}` : `1px solid ${T.border}`}; background: ${selected ? T.greenBg : T.surface}; border-radius: 7px; padding: 14px 13px; position: relative;">
        ${note ? `<div style="position: absolute; top: -9px; right: 10px; font-size: 9.5px; font-weight: 600; letter-spacing: 0.3px; background: ${T.lime}; color: ${T.ink}; border-radius: 4px; padding: 2px 7px;">${note}</div>` : ''}
        <div style="font-size: 11.5px; color: ${T.body}; margin-bottom: 6px;">${name}</div>
        <div style="font-family: ${MONO}; font-size: 19px; font-weight: 500; letter-spacing: -0.5px;">${price}</div>
        <div style="font-size: 11px; color: ${T.muted}; font-weight: 300; margin-top: 3px;">${per}</div>
      </div>`;

const paywall = authPanel(`
    <div style="width: 46px; height: 46px; border-radius: 11px; background: #FBF3E0; display: flex; align-items: center; justify-content: center; margin-bottom: 20px;">${icFill('crown', 22, T.amber)}</div>
    <h1 style="font-size: 26px; font-weight: 600; margin: 0 0 8px 0; letter-spacing: -0.7px;">MainCourse Pro</h1>
    <div style="font-size: 13px; color: ${T.muted}; font-weight: 300; line-height: 1.55; margin-bottom: 22px;">Unlimited imports and a kitchen you can share with the people you cook for.</div>
    <div style="border-top: 1px solid ${T.line}; border-bottom: 1px solid ${T.line}; padding: 8px 0; margin-bottom: 24px;">
      ${proFeature('Unlimited recipe imports')}
      ${proFeature('Shared cookbooks for your household')}
      ${proFeature('Shared shopping lists, synced live')}
      ${proFeature('Recipe reminders')}
    </div>
    <div style="display: flex; gap: 11px; margin-bottom: 20px;">
      ${plan('Yearly', 'CHF 29', 'CHF 2.42 per month', '2 months free', true)}
      ${plan('Monthly', 'CHF 3.90', 'billed every month', null, false)}
    </div>
    ${wideBtn('Start 7-day free trial')}
    <div style="text-align: center; margin-top: 14px;"><span style="font-size: 12px; color: ${T.body}; font-weight: 400;">Restore purchases</span></div>
    <div style="flex-grow: 1;"></div>
    <div style="font-size: 11px; color: ${T.muted}; font-weight: 300; line-height: 1.55; text-align: center;">Renews automatically after the trial. Cancel any time from Settings.</div>`);

const invite = mobile(`
  <div style="flex-shrink: 0; display: flex; align-items: center; gap: 9px; padding: 16px; border-bottom: 1px solid ${T.line};">
    ${mark(20)}<span style="font-size: 14px; font-weight: 600; letter-spacing: -0.25px;">MainCourse</span>
  </div>
  <div style="flex-grow: 1; display: flex; flex-direction: column; justify-content: center; padding: 0 22px 40px 22px;">
    <div style="display: flex; flex-direction: column; align-items: center; text-align: center; gap: 14px; margin-bottom: 26px;">
      <div style="width: 54px; height: 54px; border-radius: 50%; background: ${T.greenBg}; color: ${T.green}; font-size: 20px; font-weight: 600; display: flex; align-items: center; justify-content: center;">M</div>
      <div>
        <h1 style="font-size: 20px; font-weight: 600; margin: 0 0 7px 0; letter-spacing: -0.45px; line-height: 1.3;">Mara invited you to a shared cookbook</h1>
        <div style="font-size: 12.5px; color: ${T.muted}; font-weight: 300; line-height: 1.55;">You&rsquo;ll be able to see, add and edit everything in it.</div>
      </div>
    </div>
    <div style="background: ${T.surface}; border: 1px solid ${T.border}; border-radius: 8px; padding: 15px; display: flex; align-items: center; gap: 12px; margin-bottom: 22px;">
      <div style="width: 38px; height: 38px; border-radius: 8px; background: ${T.green}; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">${ic('book', 18, T.lime, 2)}</div>
      <div style="flex-grow: 1; min-width: 0;">
        <div style="font-size: 14px; font-weight: 500;">Our Kitchen</div>
        <div style="font-size: 11.5px; color: ${T.muted}; font-weight: 300; margin-top: 2px;"><span style="font-family: ${MONO}; font-size: 11px;">86</span> recipes &middot; 2 members</div>
      </div>
    </div>
    <div style="display: flex; flex-direction: column; gap: 9px;">
      ${wideBtn('Accept invitation')}
      ${wideBtn('Not now', { kind: 'quiet' })}
    </div>
  </div>`);

w({
  file: 'Account.dc.html',
  html: artboard({
    title: 'Getting in',
    sub: 'Sign in, the Pro upgrade, and what someone sees when they open an invite link.',
    frames: [signIn, paywall, invite],
  }),
});

// ============================================================ 9. MOBILE NAVIGATION

const recipesBase = `${appBar()}
  <div style="padding: 18px 16px 0 16px; display: flex; align-items: flex-start; justify-content: space-between;">
    <div>
      <h1 style="font-size: 23px; font-weight: 600; margin: 0 0 4px 0; letter-spacing: -0.5px;">All Recipes</h1>
      <div style="font-size: 11.5px; color: ${T.muted}; font-weight: 300;">Our Kitchen &middot; <span style="font-family: ${MONO}; font-size: 11px;">128</span></div>
    </div>
    <div style="width: 40px; height: 40px; border-radius: 8px; background: ${T.green}; display: flex; align-items: center; justify-content: center;">${ic('plus', 19, '#FFFFFF', 2.4)}</div>
  </div>
  <div style="padding: 18px 16px 0 16px; flex-grow: 1; overflow: hidden;">
    ${grid(RECIPES.slice(0, 6).map(r => card({ ...r, imgH: 112, fs: 13.5, metaFs: 10 })), { cols: 2, gap: '18px 14px' })}
  </div>`;

const accountRow = `<div style="border-top: 1px solid ${T.line}; margin: 0 12px; padding: 12px 0 0 0;">
      <div style="display: flex; align-items: center; gap: 9px; padding: 6px 8px;">
        <div style="width: 28px; height: 28px; border-radius: 5px; background: ${T.line}; color: ${T.body}; font-size: 11px; font-weight: 600; display: flex; align-items: center; justify-content: center;">SN</div>
        <div style="flex-grow: 1;"><div style="font-size: 12.5px; color: ${T.body};">Settings</div></div>
        ${ic('chevR', 12, T.muted, 2.2)}
      </div>
    </div>`;

const drawerOpen = mobile(`${recipesBase}
  <div style="position: absolute; inset: 0; background: rgba(20,23,28,0.45);"></div>
  <div style="position: absolute; top: 0; left: 0; bottom: 0; width: 286px; background: ${T.rail}; border-right: 1px solid ${T.line}; display: flex; flex-direction: column; padding: 18px 0; box-shadow: 12px 0 32px -12px rgba(20,23,28,0.4);">
    <div style="padding: 0 16px 20px 16px; display: flex; align-items: center; gap: 9px;">
      ${mark(22)}
      <span style="font-size: 14.5px; font-weight: 600; letter-spacing: -0.25px; flex-grow: 1;">MainCourse</span>
      ${ic('x', 17, T.muted, 2.2)}
    </div>
    <div style="margin: 0 12px 20px 12px; padding: 11px 12px; background: ${T.surface}; border: 1px solid ${T.border}; border-radius: 6px; display: flex; align-items: center; gap: 10px;">
      <div style="width: 26px; height: 26px; border-radius: 5px; background: ${T.green}; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">${ic('book', 13, T.lime, 2)}</div>
      <div style="flex-grow: 1; min-width: 0;">
        <div style="font-size: 13px; font-weight: 500;">Our Kitchen</div>
        <div style="font-size: 10.5px; color: ${T.muted}; margin-top: 1px;">Shared &middot; 2 people</div>
      </div>
      ${ic('chevUD', 12, T.muted, 2.4)}
    </div>
    <div style="padding: 0 10px;">${drawerNav('recipes')}</div>
    <div style="flex-grow: 1;"></div>
    <div style="padding: 0 20px 18px 20px;">
      <div style="font-size: 9.5px; letter-spacing: 1.1px; text-transform: uppercase; color: ${T.muted}; margin-bottom: 11px; font-weight: 500;">Collections</div>
      <div style="display: flex; flex-direction: column; gap: 11px;">
        ${[['Favorites', '24'], ['Weeknight', '41'], ['Baking', '18']].map(([n, c]) =>
          `<div style="display: flex; justify-content: space-between; align-items: center;"><span style="font-size: 13px; color: ${T.body};">${n}</span><span style="font-family: ${MONO}; font-size: 11px; color: ${T.muted};">${c}</span></div>`).join('')}
      </div>
    </div>
    ${accountRow}
  </div>`);

const switcherRow = (icon, name, sub, on) => `
      <div style="display: flex; align-items: center; gap: 12px; padding: 13px 4px; border-bottom: 1px solid ${T.line};">
        <div style="width: 34px; height: 34px; border-radius: 7px; background: ${on ? T.green : T.sunken}; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">${ic(icon, 16, on ? T.lime : T.body, 1.9)}</div>
        <div style="flex-grow: 1; min-width: 0;">
          <div style="font-size: 14px; font-weight: 500;">${name}</div>
          <div style="font-size: 11.5px; color: ${T.muted}; font-weight: 300; margin-top: 2px;">${sub}</div>
        </div>
        ${on ? `<div style="width: 20px; height: 20px; border-radius: 50%; background: ${T.green}; display: flex; align-items: center; justify-content: center;">${ic('check', 12, '#FFFFFF', 3)}</div>`
             : `<div style="width: 20px; height: 20px; border-radius: 50%; border: 1.5px solid ${T.border};"></div>`}
      </div>`;

const switcherSheet = mobile(`${recipesBase}
  <div style="position: absolute; inset: 0; background: rgba(20,23,28,0.45);"></div>
  <div style="position: absolute; left: 0; right: 0; bottom: 0; background: ${T.surface}; border-radius: 14px 14px 0 0; padding: 10px 18px 26px 18px; box-shadow: 0 -12px 32px -12px rgba(20,23,28,0.35);">
    <div style="width: 36px; height: 4px; border-radius: 2px; background: ${T.border}; margin: 0 auto 16px auto;"></div>
    <div style="font-size: 15px; font-weight: 600; margin-bottom: 8px; letter-spacing: -0.3px;">Switch cookbook</div>
    ${switcherRow('users', 'Our Kitchen', 'Shared &middot; 86 recipes', true)}
    ${switcherRow('user', 'My Recipes', 'Personal &middot; 42 recipes', false)}
    <div style="display: flex; align-items: center; gap: 10px; padding: 15px 4px 4px 4px;">
      ${ic('gear', 15, T.body, 1.9)}
      <span style="font-size: 13px; color: ${T.body}; flex-grow: 1;">Manage cookbooks</span>
      ${ic('chevR', 13, T.muted, 2.2)}
    </div>
  </div>`);

w({
  file: 'MobileNav.dc.html',
  html: artboard({
    title: 'Mobile navigation',
    sub: 'What replaces the tab bar: the rail slides in from the menu button; the cookbook switcher is a sheet.',
    frames: [drawerOpen, switcherSheet],
    w: 820,
  }),
});

console.log('screens3: Import, Account, MobileNav');
