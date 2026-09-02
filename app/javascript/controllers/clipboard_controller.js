import { Controller } from "@hotwired/stimulus"

export default class extends Controller {
  static targets = ["label"]
  static values = { text: String }

  async copy() {
    try {
      await navigator.clipboard.writeText(this.textValue)
      this.#flash("Copied")
    } catch {
      this.#flash("Press ⌘C")
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
