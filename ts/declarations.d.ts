// declarations.d.ts
import type { Parinfer } from "parinfer"

declare global {
  interface Window {
    parinfer?: Parinfer
  }
}
