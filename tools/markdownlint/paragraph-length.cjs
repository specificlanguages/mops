// markdownlint custom rule: reports paragraphs with more words than `max_words`, which the configuration must set.
//
// Counted: the visible text of a paragraph, including paragraphs in list items and blockquotes. Inline code, link
// text, image alt text, inline math, and autolinks count as words. Link destinations, link titles, reference labels,
// footnote markers, and inline HTML tags and comments do not. A word is a run of non-whitespace characters that
// contains at least one letter or digit.
//
// Headings, code blocks, math blocks, tables, and HTML blocks are not paragraphs and are not checked.

"use strict";

// Subtrees that hold no counted words.
const HIDDEN_TYPES = new Set(["resource", "reference", "htmlText", "gfmFootnoteCall"]);

// Leaf tokens that hold visible text.
const TEXT_TYPES = new Set([
  "data",
  "codeTextData",
  "mathTextData",
  "characterEscapeValue",
  "autolinkProtocol",
  "autolinkEmail",
  "literalAutolinkHttp",
  "literalAutolinkWww",
  "literalAutolinkEmail",
]);

function collectParagraphs(tokens, result) {
  for (const token of tokens) {
    if (token.type === "paragraph") {
      result.push(token);
    } else if (token.type !== "htmlFlow") {
      // markdownlint parses the content of HTML blocks as Markdown, but that content is part of the HTML block.
      collectParagraphs(token.children, result);
    }
  }
  return result;
}

function visibleText(token) {
  if (HIDDEN_TYPES.has(token.type)) {
    return " ";
  }
  if (TEXT_TYPES.has(token.type)) {
    return token.text;
  }
  if (token.children.length === 0) {
    // Line endings and indentation separate words; markers such as `*` or `[` and character references do not.
    return /^\s*$/u.test(token.text) ? " " : "";
  }
  return token.children.map(visibleText).join("");
}

function countWords(paragraph) {
  return visibleText(paragraph)
    .split(/\s+/u)
    .filter((word) => /[\p{L}\p{N}]/u.test(word)).length;
}

/** @type {import("markdownlint").Rule} */
module.exports = {
  names: ["paragraph-length"],
  description: "Paragraph is too long",
  tags: ["paragraphs"],
  parser: "micromark",
  function: (params, onError) => {
    const maxWords = params.config.max_words;
    for (const paragraph of collectParagraphs(params.parsers.micromark.tokens, [])) {
      const words = countWords(paragraph);
      if (words <= maxWords) {
        continue;
      }
      onError({
        lineNumber: paragraph.startLine,
        detail:
          `${words} words, limit is ${maxWords}. Do not add more sentences to this paragraph. Reread it, cut what ` +
          "repeats or no longer matters, and split it only where the topic really changes, not in the middle. A list or " +
          "table may fit enumerations better.",
      });
    }
  },
};
