import { Controller } from "@hotwired/stimulus"

// Scales ingredient quantities in place when the servings stepper changes.
// Pure DOM, no fetch, and nothing is persisted — leaving the page resets it,
// exactly like the iOS detail screen.
//
// Rows without a `data-base-amount` (e.g. "flaky sea salt") are left alone.
export default class extends Controller {
  static targets = ["quantity", "detail", "servings", "label", "decrement", "increment"]
  static values = { baseServings: Number, servings: Number, locale: String, labels: Object, scaledLabel: String }

  static MIN = 1
  static MAX = 64

  connect() {
    if (!this.hasBaseServingsValue || this.baseServingsValue <= 0) this.baseServingsValue = 0
    if (!this.hasServingsValue || this.servingsValue <= 0) this.servingsValue = this.baseServingsValue
    this.render()
  }

  increment() {
    this.servingsValue = Math.min(this.constructor.MAX, this.servingsValue + 1)
    this.render()
  }

  decrement() {
    this.servingsValue = Math.max(this.constructor.MIN, this.servingsValue - 1)
    this.render()
  }

  get factor() {
    if (!this.baseServingsValue || this.baseServingsValue <= 0) return 1
    return this.servingsValue / this.baseServingsValue
  }

  render() {
    const factor = this.factor

    this.servingsTargets.forEach((element) => {
      element.textContent = String(this.servingsValue)
    })

    this.labelTargets.forEach((element) => {
      const category = new Intl.PluralRules(this.localeValue || "en").select(this.servingsValue)
      const label = this.labelsValue[category] || this.labelsValue.other
      element.textContent = factor === 1 ? label : this.scaledLabelValue
        .replace("%{servings}", label).replace("%{factor}", this.#formatFactor(factor))
    })

    if (this.hasDecrementTarget) this.decrementTarget.disabled = this.servingsValue <= this.constructor.MIN
    if (this.hasIncrementTarget) this.incrementTarget.disabled = this.servingsValue >= this.constructor.MAX

    this.quantityTargets.forEach((element) => {
      const quantity = this.#scaledQuantity(element.dataset, factor)
      if (quantity !== null) element.textContent = quantity
    })

    // The add-to-list dialog submits the quantities the user is actually cooking for.
    this.detailTargets.forEach((element) => {
      const quantity = this.#scaledQuantity(element.dataset, factor, "en")
      const note = element.dataset.note || ""
      element.value = [quantity, note].filter(Boolean).join(", ")

      const preview = element.parentElement?.querySelector("[data-detail-preview]")
      if (preview) preview.textContent = [this.#scaledQuantity(element.dataset, factor), note].filter(Boolean).join(", ")
    })
  }

  #scaledQuantity(dataset, factor, locale = this.localeValue) {
    const base = parseFloat(dataset.baseAmount)
    const unit = dataset.unit || ""

    if (!Number.isFinite(base)) return unit || null

    const max = parseFloat(dataset.baseAmountMax)
    let quantity = this.#formatNumber(base * factor, locale)
    if (Number.isFinite(max)) quantity = `${quantity}\u2013${this.#formatNumber(max * factor, locale)}`

    return unit ? `${quantity} ${unit}` : quantity
  }

  #formatFactor(factor) {
    return this.#decimal(factor, this.localeValue)
  }

  // Mirrors RecipesHelper#format_amount so server and client render alike.
  #formatNumber(value, locale) {
    const rounded = Math.round(value * 100) / 100
    const fraction = this.#unicodeFraction(rounded)
    if (fraction !== null) return fraction

    return this.#decimal(rounded, locale)
  }

  #decimal(value, locale) {
    return new Intl.NumberFormat(locale || "en", { maximumFractionDigits: 2, useGrouping: false }).format(value)
  }

  #unicodeFraction(value) {
    const map = [
      [0.25, "\u00BC"], [0.5, "\u00BD"], [0.75, "\u00BE"],
      [0.3333, "\u2153"], [0.6667, "\u2154"],
      [0.125, "\u215B"], [0.375, "\u215C"], [0.625, "\u215D"], [0.875, "\u215E"]
    ]

    for (const [key, glyph] of map) {
      if (Math.abs(value - key) < 0.005) return glyph
    }
    return null
  }
}
