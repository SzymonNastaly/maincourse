import { Controller } from "@hotwired/stimulus"

// The add-to-shopping-list review step: server-rendered policy picks the initial
// selection, and the button says how many will actually be added.
export default class extends Controller {
  static targets = ["row", "count", "submit", "review", "confirmation", "confirmationHeading"]
  static values = { oldestCreatedAt: String }

  connect() {
    this.confirmed = false
    this.update()
  }

  submit(event) {
    if (!this.needsReview() || this.confirmed) return

    event.preventDefault()
    this.reviewTarget.classList.add("hidden")
    this.confirmationTarget.classList.remove("hidden")
    this.confirmationHeadingTarget.focus()
  }

  confirm() {
    this.confirmed = true
  }

  returnToReview() {
    this.confirmationTarget.classList.add("hidden")
    this.reviewTarget.classList.remove("hidden")
    this.submitTarget.focus()
  }

  needsReview() {
    if (!this.hasOldestCreatedAtValue) return false

    const oldestCreatedAt = Date.parse(this.oldestCreatedAtValue)
    const reviewAge = 36 * 60 * 60 * 1000
    return oldestCreatedAt < Date.now() - reviewAge
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
