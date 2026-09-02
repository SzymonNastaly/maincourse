import { writeFileSync } from 'node:fs';
import {
  T, MONO, ic, icFill, rail, desktop, mobile, appBar, backBar, artboard,
  btn, searchField, chips, card, grid, RECIPES, PHOTOS, heartBadge,
} from './parts.mjs';

const w = (s) => writeFileSync(s.file, s.html);

const sectionLabel = (text, right = '') =>
  `<div style="display: flex; align-items: center; gap: 8px; margin-bottom: 12px;">
      <span style="font-size: 9.5px; letter-spacing: 1.1px; text-transform: uppercase; color: ${T.muted}; font-weight: 500;">${text}</span>
      <div style="flex-grow: 1;"></div>${right}
    </div>`;

// ============================================================ 1. RECIPES

const recipesDesktop = desktop(`${rail('recipes')}
  <main style="flex-grow: 1; display: flex; flex-direction: column; min-width: 0;">
    <div style="padding: 26px 30px 0 30px; display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; flex-shrink: 0;">
      <div>
        <h1 style="font-size: 29px; font-weight: 600; margin: 0 0 5px 0; letter-spacing: -0.7px;">All Recipes</h1>
        <div style="font-size: 12.5px; color: ${T.muted}; font-weight: 300;"><span style="font-family: ${MONO}; font-size: 12px;">128</span> recipes in Our Kitchen</div>
      </div>
      <div style="display: flex; align-items: center; gap: 10px; padding-top: 5px;">
        ${searchField({})}
        ${btn('Add recipe', { icon: 'plus' })}
      </div>
    </div>
    <div style="padding: 20px 30px 16px 30px; border-bottom: 1px solid ${T.line}; flex-shrink: 0;">
      ${chips(['All', 'Favorites', 'Weeknight', 'Vegetarian', 'Baking'])}
    </div>
    <div style="padding: 24px 30px; flex-grow: 1; overflow: hidden;">
      ${grid(RECIPES.map(r => card({ ...r })))}
    </div>
  </main>`);

const recipesMobile = mobile(`${appBar()}
  <div style="padding: 18px 16px 0 16px; display: flex; align-items: flex-start; justify-content: space-between; flex-shrink: 0;">
    <div>
      <h1 style="font-size: 23px; font-weight: 600; margin: 0 0 4px 0; letter-spacing: -0.5px;">All Recipes</h1>
      <div style="display: flex; align-items: center; gap: 5px;">
        <span style="font-size: 11.5px; color: ${T.muted}; font-weight: 300;">Our Kitchen &middot;</span>
        <span style="font-family: ${MONO}; font-size: 11px; color: ${T.muted};">128</span>
        ${ic('chevD', 11, T.muted, 2.4)}
      </div>
    </div>
    <div style="width: 40px; height: 40px; border-radius: 8px; background: ${T.green}; display: flex; align-items: center; justify-content: center;">${ic('plus', 19, '#FFFFFF', 2.4)}</div>
  </div>
  <div style="padding: 16px 16px 0 16px; flex-shrink: 0;">
    <div style="display: flex; align-items: center; gap: 9px; background: ${T.sunken}; border: 1px solid ${T.border}; border-radius: 6px; padding: 11px 12px;">
      ${ic('search', 15, T.muted, 2.2)}<span style="font-size: 13px; color: ${T.muted}; font-weight: 300;">Search recipes</span>
    </div>
  </div>
  <div style="padding: 14px 16px; flex-shrink: 0;">${chips(['All', 'Favorites', 'Weeknight'], { sort: null, size: 'sm' })}</div>
  <div style="flex-grow: 1; overflow: hidden; padding: 0 16px;">
    ${grid(RECIPES.slice(0, 6).map(r => card({ ...r, imgH: 112, fs: 13.5, metaFs: 10 })), { cols: 2, gap: '18px 14px' })}
  </div>`);

w({
  file: 'Main.dc.html',
  html: artboard({
    title: 'Recipes',
    sub: 'The home screen. On mobile the rail collapses behind the menu button — no bottom tab bar.',
    frames: [recipesDesktop, recipesMobile],
  }),
});

// ============================================================ 2. RECIPE DETAIL

const INGREDIENTS = [
  ['1', 'whole chicken, about 1.6 kg'],
  ['60 g', 'unsalted butter, softened'],
  ['2 tbsp', 'white miso paste'],
  ['4', 'garlic cloves, crushed'],
  ['1', 'lemon, halved'],
  ['2 tbsp', 'olive oil'],
  ['1 bunch', 'spring onions'],
  ['', 'Flaky sea salt'],
];

const STEPS = [
  'Heat the oven to 200&deg;C fan. Pat the chicken dry inside and out and leave it at room temperature for 30 minutes.',
  'Mash the softened butter with the miso and crushed garlic until smooth and evenly coloured.',
  'Loosen the skin over the breast with your fingers and push two thirds of the miso butter underneath. Rub the rest over the outside.',
  'Halve the lemon and tuck it into the cavity with the spring onion tops. Set the bird on a bed of the remaining spring onions.',
  'Roast for 55 minutes, basting once at the halfway mark, until the juices from the thigh run clear.',
];

const ingredientRow = ([qty, name], compact = false) => `
        <div style="display: flex; align-items: baseline; gap: 10px; padding: ${compact ? '7px' : '8px'} 0; border-bottom: 1px solid ${T.line};">
          <div style="width: 54px; flex-shrink: 0; font-family: ${MONO}; font-size: ${compact ? 11 : 11.5}px; color: ${T.green};">${qty}</div>
          <div style="font-size: ${compact ? 12.5 : 13}px; line-height: 1.4; color: ${T.ink};">${name}</div>
        </div>`;

// Servings stepper — scales the quantities above.
const servings = (n, compact = false) => `
        <div style="display: inline-flex; align-items: center; gap: ${compact ? 9 : 10}px; border: 1px solid ${T.border}; border-radius: 6px; padding: 3px 4px; background: ${T.surface};">
          <div style="width: 21px; height: 21px; border-radius: 4px; background: ${T.sunken}; display: flex; align-items: center; justify-content: center;">${ic('minus', 12, T.body, 2.4)}</div>
          <div style="display: flex; align-items: baseline; gap: 4px;">
            <span style="font-family: ${MONO}; font-size: 12px; color: ${T.ink};">${n}</span>
            <span style="font-size: 10.5px; color: ${T.muted}; font-weight: 300;">servings</span>
          </div>
          <div style="width: 21px; height: 21px; border-radius: 4px; background: ${T.sunken}; display: flex; align-items: center; justify-content: center;">${ic('plus', 12, T.body, 2.4)}</div>
        </div>`;

const stepRow = (text, i, compact = false) => `
        <div style="display: flex; gap: 12px; margin-bottom: ${compact ? 14 : 16}px;">
          <div style="width: 22px; height: 22px; border-radius: 5px; background: ${T.greenBg}; color: ${T.green}; font-family: ${MONO}; font-size: 11px; font-weight: 500; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">${i + 1}</div>
          <div style="font-size: ${compact ? 12.5 : 13}px; line-height: 1.62; color: ${T.body}; font-weight: 300;">${text}</div>
        </div>`;

const metaStat = (icon, label, value) => `
        <div style="display: flex; align-items: center; gap: 7px;">
          ${ic(icon, 14, T.muted, 1.9)}
          <span style="font-size: 12px; color: ${T.muted}; font-weight: 300;">${label}</span>
          <span style="font-family: ${MONO}; font-size: 12px; color: ${T.ink};">${value}</span>
        </div>`;

const detailDesktop = desktop(`${rail('recipes')}
  <main style="flex-grow: 1; display: flex; flex-direction: column; min-width: 0;">
    <div style="padding: 16px 30px; border-bottom: 1px solid ${T.line}; display: flex; align-items: center; gap: 10px; flex-shrink: 0;">
      <div style="display: flex; align-items: center; gap: 7px; font-size: 12.5px; color: ${T.body};">${ic('back', 15, T.body, 2)}<span>All Recipes</span></div>
      <div style="flex-grow: 1;"></div>
      <div style="width: 32px; height: 32px; border-radius: 6px; border: 1px solid ${T.border}; display: flex; align-items: center; justify-content: center;">${icFill('heart', 14, T.green)}</div>
      <div style="width: 32px; height: 32px; border-radius: 6px; border: 1px solid ${T.border}; display: flex; align-items: center; justify-content: center;">${ic('dots', 15, T.body, 2)}</div>
    </div>
    <div style="flex-grow: 1; overflow: hidden; padding: 22px 30px 0 30px;">
      <div style="height: 168px; border-radius: 8px; background: ${PHOTOS.chicken}; margin-bottom: 18px;"></div>
      <div style="display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 14px;">
        <div style="min-width: 0;">
          <h1 style="font-size: 25px; font-weight: 600; margin: 0 0 8px 0; letter-spacing: -0.6px;">Miso Butter Roast Chicken</h1>
          <div style="display: flex; align-items: center; gap: 6px;">
            <span style="font-size: 11px; padding: 3px 9px; border-radius: 4px; background: ${T.sunken}; color: ${T.body};">Weeknight</span>
            <span style="font-size: 11px; padding: 3px 9px; border-radius: 4px; background: ${T.sunken}; color: ${T.body};">Chicken</span>
            <span style="display: inline-flex; align-items: center; gap: 5px; font-size: 11.5px; color: ${T.green}; margin-left: 4px;">${ic('ext', 12, T.green, 2)}smittenkitchen.com</span>
          </div>
        </div>
        ${btn('Add all to shopping list', { icon: 'cart' })}
      </div>
      <div style="display: flex; align-items: center; gap: 22px; padding: 12px 0 20px 0; border-bottom: 1px solid ${T.line};">
        ${metaStat('clock', 'Prep', '20m')}${metaStat('flame', 'Cook', '1h 5m')}
      </div>
      <div style="display: flex; gap: 34px; padding-top: 20px;">
        <div style="width: 248px; flex-shrink: 0;">
          ${sectionLabel('Ingredients', servings(4))}
          ${INGREDIENTS.map(i => ingredientRow(i)).join('')}
        </div>
        <div style="flex-grow: 1; min-width: 0;">
          ${sectionLabel('Method')}
          ${STEPS.map((s, i) => stepRow(s, i)).join('')}
        </div>
      </div>
    </div>
  </main>`);

const detailMobile = mobile(`${backBar('All Recipes', `<div style="display: flex; gap: 6px;">
    <div style="width: 32px; height: 32px; border-radius: 6px; border: 1px solid ${T.border}; display: flex; align-items: center; justify-content: center;">${icFill('heart', 14, T.green)}</div>
    <div style="width: 32px; height: 32px; border-radius: 6px; border: 1px solid ${T.border}; display: flex; align-items: center; justify-content: center;">${ic('dots', 15, T.body, 2)}</div>
  </div>`)}
  <div style="flex-grow: 1; overflow: hidden;">
    <div style="height: 150px; background: ${PHOTOS.chicken};"></div>
    <div style="padding: 16px 16px 0 16px;">
      <h1 style="font-size: 20px; font-weight: 600; margin: 0 0 8px 0; letter-spacing: -0.45px; line-height: 1.22;">Miso Butter Roast Chicken</h1>
      <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 14px;">
        <span style="font-size: 10.5px; padding: 3px 8px; border-radius: 4px; background: ${T.sunken}; color: ${T.body};">Weeknight</span>
        <span style="font-size: 10.5px; padding: 3px 8px; border-radius: 4px; background: ${T.sunken}; color: ${T.body};">Chicken</span>
        <span style="display: inline-flex; align-items: center; gap: 4px; font-size: 11px; color: ${T.green};">${ic('ext', 11, T.green, 2)}Source</span>
      </div>
      <div style="display: flex; align-items: center; gap: 16px; padding-bottom: 14px; border-bottom: 1px solid ${T.line};">
        ${metaStat('clock', 'Prep', '20m')}${metaStat('flame', 'Cook', '1h 5m')}
      </div>
      <div style="padding: 14px 0;">
        <div style="display: flex; align-items: center; justify-content: center; gap: 7px; background: ${T.green}; color: #FFFFFF; border-radius: 6px; padding: 12px; font-size: 13px; font-weight: 500;">${ic('cart', 15, '#FFFFFF', 2.2)}<span>Add all to shopping list</span></div>
      </div>
      ${sectionLabel('Ingredients', servings(4))}
      ${INGREDIENTS.slice(0, 6).map(i => ingredientRow(i, true)).join('')}
      <div style="height: 22px;"></div>
      ${sectionLabel('Method')}
      ${STEPS.slice(0, 3).map((s, i) => stepRow(s, i, true)).join('')}
    </div>
  </div>`);

w({
  file: 'RecipeDetail.dc.html',
  html: artboard({
    title: 'Recipe detail',
    sub: 'One Add-all button per screen; the servings stepper scales the quantities. Ingredients are a reference list, not a checklist.',
    frames: [detailDesktop, detailMobile],
  }),
});

// ============================================================ 3. SEARCH

const menuItem = (label, icon, danger = false) => `
      <div style="display: flex; align-items: center; gap: 10px; padding: 8px 11px; font-size: 12.5px; color: ${danger ? T.danger : T.ink};">${ic(icon, 14, 'currentColor', 2)}<span>${label}</span></div>`;

const searchDesktop = desktop(`${rail('search')}
  <main style="flex-grow: 1; display: flex; flex-direction: column; min-width: 0; position: relative;">
    <div style="padding: 26px 30px 0 30px; flex-shrink: 0;">
      <div style="display: flex; align-items: center; gap: 11px; background: ${T.surface}; border: 1.5px solid ${T.green}; border-radius: 6px; padding: 12px 14px; box-shadow: 0 0 0 3px ${T.greenBg};">
        ${ic('search', 17, T.green, 2.2)}
        <span style="font-size: 15px; color: ${T.ink}; flex-grow: 1;">labneh</span>
        ${ic('x', 15, T.muted, 2.2)}
      </div>
      <div style="display: flex; align-items: center; gap: 8px; padding: 16px 0 14px 0;">
        <span style="font-size: 12.5px; color: ${T.muted}; font-weight: 300;"><span style="font-family: ${MONO}; font-size: 12px; color: ${T.ink};">3</span> results across Our Kitchen and My Recipes</span>
      </div>
    </div>
    <div style="padding: 6px 30px; flex-grow: 1; overflow: hidden;">
      ${grid([
        card({ name: 'Roasted Beetroot &amp; Labneh', meta: '55m &middot; serves 4', photo: PHOTOS.beetroot }),
        card({ name: 'Green Shakshuka with Labneh', meta: '30m &middot; serves 2', photo: PHOTOS.shakshuka }),
        card({ name: 'Za&rsquo;atar Flatbread, Labneh', meta: '2h &middot; serves 6', photo: PHOTOS.focaccia }),
      ])}
    </div>
    <div style="position: absolute; top: 220px; left: 246px; width: 186px; background: ${T.surface}; border: 1px solid ${T.border}; border-radius: 7px; padding: 5px; box-shadow: 0 12px 28px -10px rgba(20,23,28,0.28);">
      ${menuItem('Open recipe', 'ext')}
      ${menuItem('Move to My Recipes', 'book')}
      <div style="height: 1px; background: ${T.line}; margin: 4px 6px;"></div>
      ${menuItem('Delete', 'trash', true)}
    </div>
  </main>`);

const searchMobile = mobile(`${appBar()}
  <div style="padding: 16px 16px 0 16px; flex-shrink: 0;">
    <div style="display: flex; align-items: center; gap: 10px; background: ${T.surface}; border: 1.5px solid ${T.green}; border-radius: 6px; padding: 11px 12px;">
      ${ic('search', 16, T.green, 2.2)}<span style="font-size: 14px; color: ${T.ink}; flex-grow: 1;">labneeh</span>${ic('x', 15, T.muted, 2.2)}
    </div>
  </div>
  <div style="flex-grow: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; padding: 0 40px 60px 40px; text-align: center;">
    <div style="width: 52px; height: 52px; border-radius: 12px; background: ${T.sunken}; display: flex; align-items: center; justify-content: center;">${ic('search', 24, T.muted, 1.7)}</div>
    <div>
      <div style="font-size: 15px; font-weight: 500; margin-bottom: 5px;">No results</div>
      <div style="font-size: 12.5px; color: ${T.muted}; font-weight: 300; line-height: 1.5;">Try a different search term.</div>
    </div>
  </div>`);

w({
  file: 'Search.dc.html',
  html: artboard({
    title: 'Search',
    sub: 'Results with the per-recipe menu open on the left; the empty state on the right.',
    frames: [searchDesktop, searchMobile],
  }),
});

// ============================================================ 4. SHOPPING LIST

const TO_BUY = [
  ['Whole chicken', '1.6 kg &middot; Miso Butter Roast Chicken'],
  ['White miso paste', '2 tbsp'],
  ['Spring onions', '1 bunch'],
  ['Celeriac', '1 small &middot; Lentil Soup'],
  ['Puy lentils', '250 g'],
  ['Greek yoghurt', ''],
  ['Flaky sea salt', ''],
];
const GOT = [['Unsalted butter', '60 g'], ['Garlic', '1 head'], ['Lemons', '2'], ['Olive oil', '']];

const tile = (name, detail, done, { fs = 13, dfs = 10.5, pad = 12 } = {}) => `
        <div style="aspect-ratio: 1; box-sizing: border-box; padding: ${pad}px; border-radius: 6px; background: ${done ? T.sunken : T.surface}; border: 1px solid ${done ? 'transparent' : T.border}; ${done ? '' : 'box-shadow: 0 1px 2px rgba(20,23,28,0.05);'} display: flex; flex-direction: column;">
          <div style="display: flex; align-items: flex-start; justify-content: space-between; gap: 6px;">
            <div style="font-size: ${fs}px; font-weight: 500; line-height: 1.3; color: ${done ? T.muted : T.ink}; letter-spacing: -0.15px;">${name}</div>
            <div style="width: 16px; height: 16px; border-radius: 50%; flex-shrink: 0; ${done ? `background: ${T.green}; display: flex; align-items: center; justify-content: center;` : `border: 1.5px solid ${T.border};`}">${done ? ic('check', 10, '#FFFFFF', 3) : ''}</div>
          </div>
          <div style="flex-grow: 1;"></div>
          ${detail ? `<div style="font-size: ${dfs}px; font-style: italic; color: ${T.muted}; font-weight: 300; line-height: 1.35;">${detail}</div>` : ''}
        </div>`;

const shoppingDesktop = desktop(`${rail('shopping')}
  <main style="flex-grow: 1; display: flex; flex-direction: column; min-width: 0;">
    <div style="padding: 26px 30px 0 30px; display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; flex-shrink: 0;">
      <div>
        <h1 style="font-size: 29px; font-weight: 600; margin: 0 0 5px 0; letter-spacing: -0.7px;">Shopping List</h1>
        <div style="font-size: 12.5px; color: ${T.muted}; font-weight: 300;"><span style="font-family: ${MONO}; font-size: 12px;">7</span> to buy in Our Kitchen</div>
      </div>
      <div style="padding-top: 5px;">${btn('Remove all', { kind: 'danger', icon: 'trash' })}</div>
    </div>
    <div style="padding: 20px 30px 18px 30px; display: flex; gap: 10px; flex-shrink: 0;">
      <div style="flex-grow: 1; display: flex; align-items: center; gap: 9px; background: ${T.surface}; border: 1px solid ${T.border}; border-radius: 6px; padding: 10px 12px;">
        ${ic('plus', 15, T.muted, 2.2)}<span style="font-size: 13px; color: ${T.muted}; font-weight: 300;">Add an item</span>
      </div>
      ${btn('Add')}
    </div>
    <div style="padding: 0 30px; flex-grow: 1; overflow: hidden;">
      ${sectionLabel('To buy', `<span style="font-family: ${MONO}; font-size: 10.5px; color: ${T.muted};">7</span>`)}
      <div style="display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px;">
        ${TO_BUY.map(([n, d]) => tile(n, d, false)).join('')}
      </div>
      <div style="height: 24px;"></div>
      ${sectionLabel(`Already got`, `<div style="display: flex; align-items: center; gap: 6px;"><span style="font-family: ${MONO}; font-size: 10.5px; color: ${T.muted};">4</span>${ic('chevD', 12, T.muted, 2.2)}</div>`)}
      <div style="display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px;">
        ${GOT.map(([n, d]) => tile(n, d, true)).join('')}
      </div>
    </div>
  </main>`);

const shoppingMobile = mobile(`${appBar()}
  <div style="padding: 18px 16px 0 16px; display: flex; align-items: flex-start; justify-content: space-between; flex-shrink: 0;">
    <div>
      <h1 style="font-size: 23px; font-weight: 600; margin: 0 0 4px 0; letter-spacing: -0.5px;">Shopping List</h1>
      <div style="font-size: 11.5px; color: ${T.muted}; font-weight: 300;"><span style="font-family: ${MONO}; font-size: 11px;">7</span> to buy &middot; Our Kitchen</div>
    </div>
    <span style="font-size: 12px; color: ${T.danger}; font-weight: 500; padding-top: 6px;">Remove all</span>
  </div>
  <div style="padding: 16px 16px 18px 16px; display: flex; gap: 8px; flex-shrink: 0;">
    <div style="flex-grow: 1; display: flex; align-items: center; gap: 8px; background: ${T.sunken}; border: 1px solid ${T.border}; border-radius: 6px; padding: 10px 11px;">
      ${ic('plus', 15, T.muted, 2.2)}<span style="font-size: 12.5px; color: ${T.muted}; font-weight: 300;">Add an item</span>
    </div>
    ${btn('Add', { size: 'sm' })}
  </div>
  <div style="padding: 0 16px; flex-grow: 1; overflow: hidden;">
    ${sectionLabel('To buy', `<span style="font-family: ${MONO}; font-size: 10.5px; color: ${T.muted};">7</span>`)}
    <div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px;">
      ${TO_BUY.slice(0, 6).map(([n, d]) => tile(n, d, false, { fs: 11.5, dfs: 9, pad: 9 })).join('')}
    </div>
    <div style="height: 20px;"></div>
    ${sectionLabel('Already got', `<div style="display: flex; align-items: center; gap: 6px;"><span style="font-family: ${MONO}; font-size: 10.5px; color: ${T.muted};">4</span>${ic('chevD', 12, T.muted, 2.2)}</div>`)}
    <div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px;">
      ${GOT.slice(0, 3).map(([n, d]) => tile(n, d, true, { fs: 11.5, dfs: 9, pad: 9 })).join('')}
    </div>
  </div>`);

w({
  file: 'ShoppingList.dc.html',
  html: artboard({
    title: 'Shopping list',
    sub: 'Square tiles carried over from iOS. Tap to tick; ticked items drop into a collapsible Already got.',
    frames: [shoppingDesktop, shoppingMobile],
  }),
});

console.log('screens1: Main, RecipeDetail, Search, ShoppingList');
