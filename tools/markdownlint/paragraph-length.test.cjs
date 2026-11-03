"use strict";

const assert = require("node:assert/strict");
const { test } = require("node:test");
const { lint } = require("markdownlint/sync");
const paragraphLength = require("./paragraph-length.cjs");

function errors(markdown, ruleConfig = true) {
  const results = lint({
    strings: { input: markdown },
    customRules: [paragraphLength],
    config: { default: false, "paragraph-length": ruleConfig },
  });
  return results.input.map((error) => ({ line: error.lineNumber, detail: error.errorDetail }));
}

function words(count) {
  return Array.from({ length: count }, (_, i) => `w${i}`).join(" ");
}

test("accepts a paragraph at the limit", () => {
  assert.deepEqual(errors(words(150)), []);
});

test("reports a paragraph over the limit with its word count", () => {
  const [error, ...rest] = errors(`# Title\n\n${words(151)}\n`);
  assert.deepEqual(rest, []);
  assert.equal(error.line, 3);
  assert.match(error.detail, /^151 words, limit is 150\./);
});

test("uses the configured limit", () => {
  assert.equal(errors(words(11), { max_words: 10 }).length, 1);
  assert.deepEqual(errors(words(10), { max_words: 10 }), []);
});

test("checks paragraphs in list items and blockquotes", () => {
  assert.deepEqual(
    errors(`- ${words(11)}\n\n> ${words(11)}\n`, { max_words: 10 }).map((error) => error.line),
    [1, 3],
  );
});

test("counts text over line breaks", () => {
  assert.equal(errors(`${words(6)}\n${words(5)}\n`, { max_words: 10 }).length, 1);
});

test("counts inline code, link text, and image alt text, but not URLs or inline HTML", () => {
  const markdown =
    'one `two three` [four five](https://example.com/a/b "six seven") ![eight](image.png) ' +
    "[nine][ref] <span>ten</span> **eleven**twelve AT&amp;T -- *\n\n[ref]: https://example.com\n";
  assert.deepEqual(errors(markdown, { max_words: 10 }), []);
  assert.equal(errors(markdown, { max_words: 9 }).length, 1);
});

test("ignores code blocks, tables, headings, and HTML blocks", () => {
  const markdown = [
    `# ${words(20)}`,
    "",
    "```text",
    words(20),
    "```",
    "",
    `| ${words(20)} |`,
    "| --- |",
    `| ${words(20)} |`,
    "",
    `<p>${words(20)}</p>`,
    "",
  ].join("\n");
  assert.deepEqual(errors(markdown, { max_words: 10 }), []);
});
