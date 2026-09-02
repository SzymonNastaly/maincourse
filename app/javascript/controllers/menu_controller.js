import { Controller } from "@hotwired/stimulus"

// A small popover: the per-recipe dots menu and the sort picker.
export default class extends Controller {
  static targets = ["menu"]

  connect() {
    this.outsideHandler = (event) => {
      if (!this.element.contains(event.target)) this.hide()
    }
    this.escapeHandler = (event) => {
      if (event.key === "Escape") this.hide()
    }
  }

  disconnect() {
    this.stopListening()
  }

  toggle(event) {
    event.preventDefault()
    event.stopPropagation()

    if (this.menuTarget.classList.contains("hidden")) {
      this.show()
    } else {
      this.hide()
    }
  }

  show() {
    // Only one menu open at a time.
    document.querySelectorAll("[data-menu-target='menu']").forEach((menu) => menu.classList.add("hidden"))

    this.menuTarget.classList.remove("hidden")
    document.addEventListener("click", this.outsideHandler)
    document.addEventListener("keydown", this.escapeHandler)
  }

  hide() {
    this.menuTarget.classList.add("hidden")
    this.stopListening()
  }

  stopListening() {
    document.removeEventListener("click", this.outsideHandler)
    document.removeEventListener("keydown", this.escapeHandler)
  }
}
