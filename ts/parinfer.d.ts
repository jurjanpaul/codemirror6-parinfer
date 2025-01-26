declare module 'parinfer' {
  export type ParinferError = {
    name: 'quote-danger' | 'eol-backslash' | 'unclosed-quote' | 'unclosed-paren' | 'unmatched-close-paren' | 'unhandled'
    message: string
    lineNo: number
    x: number
    extra?: ParinferError
  };

  export type TabStop = {
    x: number
    argX: number
    lineNo: number
    ch: string
  };

  export type ParenTrail = {
    lineNo: number
    startX: number
    endX: number
  };

  export type ParinferChange = {
    lineNo: number
    x: number
    oldText: string
    newText: string
  };

  export type ParinferOptions = {
    commentChars?: string | string[]
    openParenChars?: string[]
    closeParenChars?: string[]
    cursorLine?: number
    cursorX?: number
    prevCursorLine?: number
    prevCursorX?: number
    selectionStartLine?: number
    changes?: ParinferChange[]
    forceBalance?: boolean
    partialResult?: boolean
  }

  export type ParinferResult = {
    success: boolean
    text: string
    cursorX: number
    cursorLine: number
    error?: ParinferError
    tabStops?: TabStop[]
    parenTrails?: ParenTrail[]
  }

  export type Parinfer = {
    smartMode(text: string, options?: ParinferOptions): ParinferResult
    indentMode(text: string, options?: ParinferOptions): ParinferResult
    parenMode(text: string, options?: ParinferOptions): ParinferResult
  }
}
