import { Controller } from "@hotwired/stimulus"

// Behaviour shared by every <dialog> in the app: close on backdrop click and
// give callers a `close` action. `showModal` handles Escape and focus for us.
export default class extends Controller {
  open() {
    if (!this.element.open) this.element.showModal()
  }

  close() {
    this.element.close()
  }

  closeOnBackdrop(event) {
    if (event.target === this.element) this.element.close()
  }
}
