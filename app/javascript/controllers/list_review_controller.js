import { Controller } from "@hotwired/stimulus"

// The add-to-shopping-list review step: everything starts ticked, unticking an
// ingredient excludes it, and the button says how many will actually be added.
export default class extends Controller {
  static targets = ["row", "count", "submit"]

  connect() {
    this.update()

    // Controllers load as separate modules, so a click can land before this one
    // is listening — the change event is then lost. System tests wait on this.
    this.element.dataset.listReviewReady = "true"
  }

  update() {
    let included = 0

    this.rowTargets.forEach((row) => {
      const checked = row.querySelector("input[type=checkbox]")?.checked ?? false
      if (checked) included += 1

      // Disabled fields are not submitted, which keeps name/details paired up.
      row.querySelectorAll("input[type=hidden]").forEach((field) => {
        field.disabled = !checked
      })

      row.classList.toggle("opacity-45", !checked)
    })

    if (this.hasCountTarget) this.countTarget.textContent = String(included)
    if (this.hasSubmitTarget) this.submitTarget.disabled = included === 0
  }
}
