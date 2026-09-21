import { Controller } from "@hotwired/stimulus"

// Keep a visible recipe awake when the browser permits it. Power saving and
// permission decisions belong to the browser; never retry a revoked lock here.
export default class extends Controller {
  connect() {
    this.resume()
  }

  disconnect() {
    this.suspend()
  }

  resume() {
    this.active = true
    this.acquire()
  }

  suspend() {
    this.active = false
    this.clear()
  }

  visibilityChanged() {
    if (document.visibilityState === "visible") this.acquire()
    else this.clear()
  }

  get eligible() {
    return this.active && this.element.isConnected &&
      document.visibilityState === "visible" && window.isSecureContext &&
      !document.documentElement.hasAttribute("data-turbo-preview") &&
      navigator.wakeLock?.request
  }

  async acquire() {
    if (!this.eligible || this.lock || this.request) return

    const request = Symbol()
    this.request = request
    try {
      const lock = await navigator.wakeLock.request("screen")
      // The page may have hidden, disconnected or reconnected during the request.
      if (this.request !== request || !this.eligible) {
        await this.release(lock)
        return
      }
      if (lock.released) return

      this.lock = lock
      lock.addEventListener("release", () => {
        if (this.lock === lock) this.lock = null
      }, { once: true })
    } catch {
      // Unsupported settings, low battery and power saver are normal refusals.
    } finally {
      if (this.request === request) this.request = null
    }
  }

  clear() {
    this.request = null
    const lock = this.lock
    this.lock = null
    if (lock) this.release(lock)
  }

  async release(lock) {
    try {
      await lock.release()
    } catch {
      // A browser may already have invalidated this sentinel during navigation.
    }
  }
}
