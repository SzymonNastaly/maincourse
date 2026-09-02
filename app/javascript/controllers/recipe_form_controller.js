import { Controller } from "@hotwired/stimulus"

// Dynamic ingredient/step rows, a cover-image preview, and a guard against
// losing a half-written recipe.
export default class extends Controller {
  static targets = [
    "ingredientList", "instructionList",
    "ingredientTemplate", "instructionTemplate",
    "imagePreview"
  ]

  connect() {
    this.dirty = false
    this.element.addEventListener("input", this.markDirty)
    this.element.addEventListener("submit", this.allowLeaving)

    window.addEventListener("beforeunload", this.warnBeforeUnload)
    document.addEventListener("turbo:before-visit", this.confirmLeaving)
  }

  disconnect() {
    window.removeEventListener("beforeunload", this.warnBeforeUnload)
    document.removeEventListener("turbo:before-visit", this.confirmLeaving)
  }

  markDirty = () => {
    this.dirty = true
  }

  allowLeaving = () => {
    this.dirty = false
  }

  warnBeforeUnload = (event) => {
    if (!this.dirty) return
    event.preventDefault()
    event.returnValue = ""
  }

  confirmLeaving = (event) => {
    if (!this.dirty) return
    if (!window.confirm("You have unsaved changes. Leave without saving?")) event.preventDefault()
  }

  addIngredient() {
    this.#append(this.ingredientTemplateTarget, this.ingredientListTarget)
  }

  addInstruction() {
    this.#append(this.instructionTemplateTarget, this.instructionListTarget)
  }

  removeRow(event) {
    const row = event.target.closest("[data-recipe-form-target='row']")
    const list = row?.parentElement
    if (!row || !list) return

    // Always leave one row behind so the field is still submitted when emptied.
    if (list.querySelectorAll("[data-recipe-form-target='row']").length === 1) {
      row.querySelector("input, textarea").value = ""
    } else {
      row.remove()
    }

    this.markDirty()
  }

  previewImage(event) {
    const file = event.target.files?.[0]
    if (!file || !this.hasImagePreviewTarget) return

    const reader = new FileReader()
    reader.onload = () => {
      this.imagePreviewTarget.src = reader.result
      this.imagePreviewTarget.classList.remove("hidden")
    }
    reader.readAsDataURL(file)
    this.markDirty()
  }

  #append(template, list) {
    const row = template.content.cloneNode(true)
    list.appendChild(row)
    list.lastElementChild.querySelector("input, textarea")?.focus()
    this.markDirty()
  }
}
