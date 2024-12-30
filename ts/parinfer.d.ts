declare module 'parinfer' {
  export type ParinferError = {
    name: 'quote-danger' | 'eol-backslash' | 'unclosed-quote' | 'unclosed-paren' | 'unmatched-close-paren' | 'unhandled';
    message: string;
    lineNo: number;
    x: number;
    extra?: ParinferError;
  };

  export type TabStop = {
    x: number;
    argX: number;
    lineNo: number;
    ch: string;
  };

  export type ParenTrail = {
    lineNo: number;
    startX: number;
    endX: number;
  };

  export type Change = {
    lineNo: number;
    x: number;
    oldText: string;
    newText: string;
  };

  export interface ParinferOptions {
    commentChars?: string | string[];
    openParenChars?: string[];
    closeParenChars?: string[];
    cursorLine?: number;
    cursorX?: number;
    prevCursorLine?: number;
    prevCursorX?: number;
    selectionStartLine?: number;
    changes?: Change[];
    forceBalance?: boolean;
    partialResult?: boolean;
  }

  export interface ParinferResult {
    success: boolean;
    text: string;
    cursorX: number;
    cursorLine: number;
    error?: ParinferError;
    tabStops?: TabStop[];
    parenTrails?: ParenTrail[];
  }

  export function smartMode(text: string, options?: ParinferOptions): ParinferResult;
  export function indentMode(text: string, options?: ParinferOptions): ParinferResult;
  export function parenMode(text: string, options?: ParinferOptions): ParinferResult;
}
