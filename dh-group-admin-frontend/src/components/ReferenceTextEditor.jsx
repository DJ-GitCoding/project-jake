/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { forwardRef, useCallback, useEffect, useImperativeHandle, useRef } from 'react';
import { REFERENCE_PATTERN } from '../constants/legalSections';

/*
 * A clause editor that shows cross-references by name.
 *
 * Clause text is stored with programmatic markers, `{{ref:permitted-uses}}`,
 * because section numbers shift whenever sections are added, moved or removed.
 * Editors are lawyers, not programmers, so the markers themselves are never put
 * in front of them: this is a contenteditable surface that paints each marker as
 * a read-only chip carrying the section's number and title, while the value
 * handed back to `onChange` stays the raw text with the markers intact.
 */

const BLOCK_TAGS = /^(DIV|P|LI|SECTION|BLOCKQUOTE)$/;

const refToken = (refKey) => `{{ref:${refKey}}}`;

/*
 * DOM → text. `trimFillerBr` drops the trailing <br> browsers park at the end of
 * a block to keep it selectable; it is an artefact, not a line the editor typed.
 * Caret arithmetic works on an untrimmed walk so that offsets line up with what
 * is actually on screen.
 */
const serialize = (root, { trimFillerBr = true } = {}) => {
  let out = '';
  const walk = (node) => {
    const children = Array.from(node.childNodes);
    children.forEach((child, i) => {
      if (child.nodeType === Node.TEXT_NODE) {
        out += child.nodeValue.replace(/ /g, ' ');
        return;
      }
      if (child.nodeType !== Node.ELEMENT_NODE) return;
      const refKey = child.dataset?.refKey;
      if (refKey) {
        out += refToken(refKey);
        return;
      }
      if (child.tagName === 'BR') {
        if (trimFillerBr && i === children.length - 1) return;
        out += '\n';
        return;
      }
      if (BLOCK_TAGS.test(child.tagName) && out !== '' && !out.endsWith('\n')) out += '\n';
      walk(child);
    });
  };
  walk(root);
  return out;
};

const chipNode = (refKey, labels, fallbacks) => {
  const label = labels?.get(refKey);
  const chip = document.createElement('span');
  chip.className = `reference-chip${label ? '' : ' is-unknown'}`;
  chip.setAttribute('contenteditable', 'false');
  chip.dataset.refKey = refKey;
  chip.setAttribute('title', label
    ? `${fallbacks.tooltip} ${label.number}${label.detail ? `\n\n${label.detail}` : ''}`
    : `${fallbacks.unknownTooltip} ${refKey}`);

  const number = document.createElement('span');
  number.className = 'reference-chip-number';
  number.textContent = label ? label.number : '!';
  chip.appendChild(number);

  const name = document.createElement('span');
  name.className = 'reference-chip-name';
  name.textContent = label
    ? (label.name || fallbacks.untitled)
    : fallbacks.unknown;
  chip.appendChild(name);
  return chip;
};

/* text → DOM, in the canonical shape this component serializes back losslessly. */
const paint = (root, value, labels, fallbacks) => {
  root.textContent = '';
  const frag = document.createDocumentFragment();
  let lastNode = null;
  const append = (node) => { frag.appendChild(node); lastNode = node; };

  String(value || '').split('\n').forEach((line, lineIndex) => {
    if (lineIndex > 0) append(document.createElement('br'));
    const pattern = new RegExp(REFERENCE_PATTERN.source, 'g');
    let at = 0;
    let match = pattern.exec(line);
    while (match) {
      if (match.index > at) append(document.createTextNode(line.slice(at, match.index)));
      append(chipNode(match[1], labels, fallbacks));
      at = match.index + match[0].length;
      match = pattern.exec(line);
    }
    if (at < line.length) append(document.createTextNode(line.slice(at)));
  });

  /* Mirror the browser's own filler <br> so a trailing newline survives a round trip. */
  if (lastNode && lastNode.nodeName === 'BR') frag.appendChild(document.createElement('br'));
  root.appendChild(frag);
};

/* Caret position as an offset into the serialized text. */
const caretOffset = (root) => {
  if (typeof window === 'undefined') return null;
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0) return null;
  const range = selection.getRangeAt(0);
  if (!root.contains(range.endContainer)) return null;
  const upToCaret = range.cloneRange();
  upToCaret.selectNodeContents(root);
  upToCaret.setEnd(range.endContainer, range.endOffset);
  const scratch = document.createElement('div');
  scratch.appendChild(upToCaret.cloneContents());
  return serialize(scratch, { trimFillerBr: false }).length;
};

const placeCaret = (root, offset) => {
  const range = document.createRange();
  let remaining = Math.max(0, offset);
  let placed = false;

  const walk = (node) => {
    const children = Array.from(node.childNodes);
    for (const child of children) {
      if (placed) return;
      if (child.nodeType === Node.TEXT_NODE) {
        const len = child.nodeValue.length;
        if (remaining <= len) { range.setStart(child, remaining); placed = true; return; }
        remaining -= len;
      } else if (child.nodeType === Node.ELEMENT_NODE) {
        const refKey = child.dataset?.refKey;
        if (refKey) {
          const len = refToken(refKey).length;
          if (remaining <= 0) { range.setStartBefore(child); placed = true; return; }
          if (remaining <= len) { range.setStartAfter(child); placed = true; return; }
          remaining -= len;
        } else if (child.tagName === 'BR') {
          if (remaining <= 0) { range.setStartBefore(child); placed = true; return; }
          remaining -= 1;
          if (remaining <= 0) { range.setStartAfter(child); placed = true; return; }
        } else {
          walk(child);
        }
      }
    }
  };

  walk(root);
  if (!placed) {
    range.selectNodeContents(root);
    range.collapse(false);
  }
  range.collapse(true);
  const selection = window.getSelection();
  selection.removeAllRanges();
  selection.addRange(range);
};

const ReferenceTextEditor = forwardRef(({
  value,
  onChange,
  labels,
  placeholder = '',
  minRows = 2,
  ariaLabel,
  strings = {},
  className = '',
  disabled = false,
}, ref) => {
  const rootRef = useRef(null);
  const valueRef = useRef(value || '');
  const caretRef = useRef(null);
  const pendingCaretRef = useRef(null);
  valueRef.current = value || '';

  const fallbacks = {
    untitled: strings.untitled || 'Untitled section',
    unknown: strings.unknown || 'Unknown reference',
    tooltip: strings.tooltip || 'Inserted into the agreement as section number',
    unknownTooltip: strings.unknownTooltip || 'This reference no longer exists:',
  };

  /*
   * Chips are painted from numbering that moves with the sections (and from
   * translated wording), so a repaint is due whenever either changes, even though
   * the stored text has not.
   */
  const labelSignature = [
    labels ? Array.from(labels.entries()).map(([key, l]) => `${key}:${l.number}:${l.name}`).join('|') : '',
    fallbacks.untitled, fallbacks.unknown, fallbacks.tooltip, fallbacks.unknownTooltip,
  ].join('~');

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    const current = serialize(root);
    const stale = current !== (value || '') || root.dataset.labelSignature !== labelSignature;
    if (!stale) return;

    const hadFocus = typeof document !== 'undefined' && document.activeElement === root;
    const caret = pendingCaretRef.current != null
      ? pendingCaretRef.current
      : (hadFocus ? caretOffset(root) : null);
    paint(root, value || '', labels, fallbacks);
    root.dataset.labelSignature = labelSignature;
    if (pendingCaretRef.current != null) {
      root.focus();
      placeCaret(root, pendingCaretRef.current);
      caretRef.current = pendingCaretRef.current;
      pendingCaretRef.current = null;
    } else if (hadFocus && caret != null) {
      placeCaret(root, caret);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, labelSignature]);

  const captureCaret = useCallback(() => {
    if (rootRef.current) caretRef.current = caretOffset(rootRef.current);
  }, []);

  const handleInput = useCallback(() => {
    if (!rootRef.current) return;
    const text = serialize(rootRef.current);
    caretRef.current = caretOffset(rootRef.current);
    if (text !== valueRef.current) onChange(text);
  }, [onChange]);

  /* Paste as plain text: pasted markup would be dropped on the next repaint anyway. */
  const handlePaste = useCallback((e) => {
    e.preventDefault();
    const text = e.clipboardData.getData('text/plain');
    if (text) document.execCommand('insertText', false, text);
  }, []);

  useImperativeHandle(ref, () => ({
    insertReference: (refKey) => {
      if (!refKey || !rootRef.current) return;
      const text = valueRef.current;
      const at = Math.min(caretRef.current == null ? text.length : caretRef.current, text.length);
      const token = refToken(refKey);
      pendingCaretRef.current = at + token.length;
      onChange(`${text.slice(0, at)}${token}${text.slice(at)}`);
    },
    focus: () => rootRef.current?.focus(),
  }), [onChange]);

  return (
    <div
      ref={rootRef}
      className={`form-control form-control-sm reference-editor ${className}`.trim()}
      style={{ minHeight: `${Math.max(1, minRows) * 1.5 + 0.75}rem` }}
      contentEditable={!disabled}
      suppressContentEditableWarning
      role="textbox"
      aria-multiline="true"
      aria-label={ariaLabel}
      aria-disabled={disabled || undefined}
      data-placeholder={placeholder}
      onInput={handleInput}
      onPaste={handlePaste}
      onKeyUp={captureCaret}
      onMouseUp={captureCaret}
      onFocus={captureCaret}
      onBlur={captureCaret}
    />
  );
});

ReferenceTextEditor.displayName = 'ReferenceTextEditor';

export default ReferenceTextEditor;
