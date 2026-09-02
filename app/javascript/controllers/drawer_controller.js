import { Controller } from "@hotwired/stimulus"

// The left rail collapses behind the app bar's menu button on small screens.
export default class extends Controller {
  static targets = ["overlay", "backdrop", "panel"]

  connect() {
    this.escapeHandler = (event) => {
      if (event.key === "Escape") this.close()
    }
  }

  disconnect() {
    document.removeEventListener("keydown", this.escapeHandler)
    document.body.classList.remove("overflow-hidden")
  }

  open() {
    this.overlayTarget.classList.remove("hidden")
    document.body.classList.add("overflow-hidden")
    document.addEventListener("keydown", this.escapeHandler)

    requestAnimationFrame(() => {
      this.backdropTarget.classList.remove("opacity-0")
      this.panelTarget.classList.remove("-translate-x-full")
    })
  }

  close() {
    if (this.overlayTarget.classList.contains("hidden")) return

    this.backdropTarget.classList.add("opacity-0")
    this.panelTarget.classList.add("-translate-x-full")
    document.body.classList.remove("overflow-hidden")
    document.removeEventListener("keydown", this.escapeHandler)

    setTimeout(() => this.overlayTarget.classList.add("hidden"), 200)
  }

  // Following a link or submitting from inside the drawer should close it, so
  // the panel is not left hanging over the page we just navigated to.
  closeOnNavigation(event) {
    if (event.target.closest("a, button[type=submit]")) this.close()
  }
}
