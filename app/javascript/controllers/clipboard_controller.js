import { Controller } from "@hotwired/stimulus"

export default class extends Controller {
  static targets = ["label"]
  static values = { text: String, copied: String, failed: String }

  async copy() {
    try {
      await navigator.clipboard.writeText(this.textValue)
      this.#flash(this.copiedValue)
    } catch {
      this.#flash(this.failedValue)
    }
  }

  #flash(message) {
    if (!this.hasLabelTarget) return

    const original = this.labelTarget.textContent
    this.labelTarget.textContent = message
    setTimeout(() => {
      this.labelTarget.textContent = original
    }, 1600)
  }
}
