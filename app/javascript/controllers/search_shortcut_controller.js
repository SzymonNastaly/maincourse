import { Controller } from "@hotwired/stimulus"

// ⌘K / Ctrl-K focuses the search field in the recipes header.
export default class extends Controller {
  static targets = ["input"]

  connect() {
    this.handler = (event) => {
      if (event.key !== "k" || !(event.metaKey || event.ctrlKey)) return

      event.preventDefault()
      const input = this.inputTargets.find((candidate) => candidate.offsetParent !== null)
      input?.focus()
      input?.select()
    }

    document.addEventListener("keydown", this.handler)
  }

  disconnect() {
    document.removeEventListener("keydown", this.handler)
  }
}
