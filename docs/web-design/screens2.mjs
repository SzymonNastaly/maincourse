import { writeFileSync } from 'node:fs';
import {
  T, MONO, ic, icFill, mark, rail, drawerNav, desktop, mobile, appBar, backBar, artboard,
  btn, card, grid, RECIPES, PHOTOS, MOBILE_W, MOBILE_H,
} from './parts.mjs';

const w = (s) => writeFileSync(s.file, s.html);

const label = (text, right = '') =>
  `<div style="display: flex; align-items: center; gap: 8px; margin-bottom: 11px;">
      <span style="font-size: 9.5px; letter-spacing: 1.1px; text-transform: uppercase; color: ${T.muted}; font-weight: 500;">${text}</span>
      <div style="flex-grow: 1;"></div>${right}
    </div>`;

const panel = (inner, extra = '') =>
  `<div style="background: ${T.surface}; border: 1px solid ${T.border}; border-radius: 8px; overflow: hidden; ${extra}">${inner}</div>`;

const row = ({ icon, iconBg = T.sunken, iconFg = T.body, title, sub, trail = '', pad = '13px 15px', last = false, titleFs = 13.5 }) =>
  `<div style="display: flex; align-items: center; gap: 12px; padding: ${pad}; ${last ? '' : `border-bottom: 1px solid ${T.line};`}">
      ${icon ? `<div style="width: 30px; height: 30px; border-radius: 6px; background: ${iconBg}; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">${ic(icon, 15, iconFg, 1.9)}</div>` : ''}
      <div style="flex-grow: 1; min-width: 0;">
        <div style="font-size: ${titleFs}px; font-weight: 500; letter-spacing: -0.15px;">${title}</div>
        ${sub ? `<div style="font-size: 11.5px; color: ${T.muted}; font-weight: 300; margin-top: 2px;">${sub}</div>` : ''}
      </div>
      ${trail}
    </div>`;

const toggle = (on = true) =>
  `<div style="width: 38px; height: 22px; border-radius: 11px; background: ${on ? T.green : T.border}; padding: 2px; box-sizing: border-box; display: flex; justify-content: ${on ? 'flex-end' : 'flex-start'};">
      <div style="width: 18px; height: 18px; border-radius: 50%; background: #FFFFFF;"></div>
    </div>`;

const pageHead = (title, sub, right = '') =>
  `<div style="padding: 26px 30px 20px 30px; display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; flex-shrink: 0;">
      <div>
        <h1 style="font-size: 29px; font-weight: 600; margin: 0 0 5px 0; letter-spacing: -0.7px;">${title}</h1>
        <div style="font-size: 12.5px; color: ${T.muted}; font-weight: 300;">${sub}</div>
      </div>
      ${right ? `<div style="padding-top: 5px;">${right}</div>` : ''}
    </div>`;

// ============================================================ 5. COOKBOOKS

const memberRow = (email, role, owner) => `
      <div style="display: flex; align-items: center; gap: 10px; padding: 10px 15px 10px 57px; border-bottom: 1px solid ${T.line};">
        ${owner ? icFill('crown', 13, T.amber) : ic('user', 13, T.muted, 2)}
        <span style="font-size: 12.5px; flex-grow: 1;">${email}</span>
        <span style="font-size: 11px; color: ${T.muted}; font-weight: 300;">${role}</span>
      </div>`;

const cookbooksDesktop = desktop(`${rail('cookbooks')}
  <main style="flex-grow: 1; display: flex; flex-direction: column; min-width: 0;">
    ${pageHead('Cookbooks', 'Keep recipes to yourself, or share a kitchen.')}
    <div style="padding: 0 30px; flex-grow: 1; overflow: hidden;">
      ${label('Personal')}
      ${panel(row({ icon: 'user', title: 'My Recipes', sub: '42 recipes', trail: ic('chevR', 14, T.muted, 2.2), last: true }))}
      <div style="height: 22px;"></div>
      ${label('Shared')}
      ${panel(`
      ${row({ icon: 'users', iconBg: T.greenBg, iconFg: T.green, title: 'Our Kitchen', sub: '86 recipes &middot; 2 members', trail: `<span style="font-size: 10.5px; padding: 3px 8px; border-radius: 4px; background: ${T.greenBg}; color: ${T.green}; font-weight: 500;">Active</span>` })}
      ${memberRow('szymon@example.com', 'Owner', true)}
      ${memberRow('mara@example.com', 'Member', false)}
      <div style="display: flex; align-items: center; gap: 9px; padding: 13px 15px;">
        ${btn('Generate invite link', { kind: 'outline', icon: 'link', size: 'sm' })}
        <div style="flex-grow: 1;"></div>
        ${btn('Delete cookbook', { kind: 'danger', icon: 'trash', size: 'sm' })}
      </div>`)}
      <div style="height: 22px;"></div>
      <div style="background: ${T.greenBg}; border: 1px solid #D6E7DF; border-radius: 8px; padding: 16px 18px;">
        <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 10px;">
          ${ic('link', 15, T.green, 2)}
          <span style="font-size: 13.5px; font-weight: 500; color: ${T.green};">Invite link created</span>
        </div>
        <div style="display: flex; align-items: center; gap: 9px;">
          <div style="flex-grow: 1; background: ${T.surface}; border: 1px solid #D6E7DF; border-radius: 6px; padding: 9px 12px; font-family: ${MONO}; font-size: 11.5px; color: ${T.body}; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">maincourse.app/join/7fQ2-mK9v-Lz</div>
          ${btn('Copy link', { size: 'sm' })}
        </div>
        <div style="font-size: 11.5px; color: ${T.body}; font-weight: 300; margin-top: 10px;">Anyone with this link can join Our Kitchen. It expires in 7 days.</div>
      </div>
    </div>
  </main>`);

const cookbooksMobile = mobile(`${backBar('Settings')}
  <div style="padding: 18px 16px 0 16px; flex-shrink: 0;">
    <h1 style="font-size: 23px; font-weight: 600; margin: 0 0 4px 0; letter-spacing: -0.5px;">Cookbooks</h1>
    <div style="font-size: 11.5px; color: ${T.muted}; font-weight: 300;">Keep recipes to yourself, or share a kitchen.</div>
  </div>
  <div style="padding: 20px 16px; flex-grow: 1; overflow: hidden;">
    ${label('Personal')}
    ${panel(row({ icon: 'user', title: 'My Recipes', sub: '128 recipes', trail: ic('chevR', 14, T.muted, 2.2), last: true }))}
    <div style="height: 22px;"></div>
    ${label('Shared')}
    <div style="border: 1.5px dashed ${T.border}; border-radius: 8px; padding: 24px 20px; display: flex; flex-direction: column; align-items: center; text-align: center; gap: 10px;">
      <div style="width: 40px; height: 40px; border-radius: 9px; background: ${T.greenBg}; display: flex; align-items: center; justify-content: center;">${ic('users', 19, T.green, 1.9)}</div>
      <div style="font-size: 14px; font-weight: 500;">Create a shared cookbook</div>
      <div style="font-size: 12px; color: ${T.muted}; font-weight: 300; line-height: 1.5;">Share recipes and shopping lists with your partner or family.</div>
      <div style="height: 2px;"></div>
      ${btn('Create shared cookbook', { icon: 'plus', size: 'sm' })}
    </div>
    <div style="height: 20px;"></div>
    <div style="font-size: 11.5px; color: ${T.muted}; font-weight: 300; line-height: 1.55;">Your existing recipes can move across when you create it, or stay where they are.</div>
  </div>`);

w({
  file: 'Cookbooks.dc.html',
  html: artboard({
    title: 'Cookbooks',
    sub: 'Members, roles and invite links on the left; the before-you-have-one state on the right.',
    frames: [cookbooksDesktop, cookbooksMobile],
  }),
});

// ============================================================ 6. SETTINGS

const settingsBody = (pad, small = false) => `
    ${label('Account')}
    ${panel(`
      <div style="display: flex; align-items: center; gap: 12px; padding: 15px;">
        <div style="width: 40px; height: 40px; border-radius: 8px; background: ${T.line}; color: ${T.body}; font-size: 14px; font-weight: 600; display: flex; align-items: center; justify-content: center;">SN</div>
        <div style="flex-grow: 1; min-width: 0;">
          <div style="font-size: 14px; font-weight: 500;">Szymon Nastaly</div>
          <div style="font-size: 11.5px; color: ${T.muted}; font-weight: 300; margin-top: 2px;">szymon@example.com</div>
        </div>
        ${btn('Manage account', { kind: 'outline', size: 'sm' })}
      </div>
      <div style="border-top: 1px solid ${T.line};">
        ${row({ title: 'Display name', trail: `<span style="font-size: 12.5px; color: ${T.muted}; font-weight: 300; margin-right: 8px;">Szymon</span>${ic('chevR', 14, T.muted, 2.2)}`, last: true, pad: '12px 15px' })}
      </div>`)}
    <div style="height: ${pad}px;"></div>
    ${label('Notifications')}
    ${panel(row({ icon: 'bell', title: 'Recipe reminders', sub: 'A nudge when something you saved is worth cooking.', trail: toggle(true), last: true }))}
    <div style="height: ${pad}px;"></div>
    ${label('Cookbooks')}
    ${panel(row({ icon: 'book', title: 'Manage cookbooks', sub: '1 personal &middot; 1 shared', trail: ic('chevR', 14, T.muted, 2.2), last: true }))}
    <div style="height: ${pad}px;"></div>
    ${label('Subscription')}
    ${panel(row({
      icon: 'crown', iconBg: '#FBF3E0', iconFg: T.amber,
      title: 'MainCourse Pro',
      sub: 'Active &middot; renews 12 September 2026',
      trail: btn('Manage', { kind: 'outline', size: 'sm' }), last: true,
    }))}
    <div style="height: ${pad}px;"></div>
    ${panel(`
      ${row({ icon: 'logout', title: 'Sign out', trail: ic('chevR', 14, T.muted, 2.2), pad: '12px 15px' })}
      ${row({ icon: 'trash', iconBg: T.dangerBg, iconFg: T.danger, title: `<span style="color: ${T.danger};">Delete account</span>`, sub: 'Removes your recipes and cookbooks for good.', last: true, pad: '12px 15px' })}`)}`;

const settingsDesktop = desktop(`${rail('settings')}
  <main style="flex-grow: 1; display: flex; flex-direction: column; min-width: 0;">
    ${pageHead('Settings', 'Signed in as szymon@example.com')}
    <div style="padding: 0 30px 24px 30px; flex-grow: 1; overflow: hidden;">
      <div style="max-width: 520px;">${settingsBody(20)}</div>
    </div>
  </main>`);

const settingsMobile = mobile(`${appBar()}
  <div style="padding: 18px 16px 0 16px; flex-shrink: 0;">
    <h1 style="font-size: 23px; font-weight: 600; margin: 0 0 4px 0; letter-spacing: -0.5px;">Settings</h1>
    <div style="font-size: 11.5px; color: ${T.muted}; font-weight: 300;">Signed in as szymon@example.com</div>
  </div>
  <div style="padding: 20px 16px; flex-grow: 1; overflow: hidden;">${settingsBody(18, true)}</div>`);

w({
  file: 'Settings.dc.html',
  html: artboard({
    title: 'Settings',
    sub: 'Account, reminders, cookbooks, subscription and the two account actions that need care.',
    frames: [settingsDesktop, settingsMobile],
  }),
});

console.log('screens2a: Cookbooks, Settings');
