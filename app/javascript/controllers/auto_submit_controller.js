import { Controller } from "@hotwired/stimulus"

// Picking a file is the confirmation — there is no second "upload" button.
export default class extends Controller {
  submit() {
    this.element.requestSubmit()
  }
}
