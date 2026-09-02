import { Controller } from "@hotwired/stimulus"

// Opens a <dialog> that lives elsewhere in the document (usually in the layout),
// addressed by id.
export default class extends Controller {
  static values = { target: String }

  open(event) {
    event.preventDefault()
    document.getElementById(this.targetValue)?.showModal()
  }
}
