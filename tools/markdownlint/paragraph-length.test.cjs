"use strict";

const assert = require("node:assert/strict");
const { test } = require("node:test");
const { lint } = require("markdownlint/sync");
const paragraphLength = require("./paragraph-length.cjs");

function errors(markdown, maxWords = 10) {
  const results = lint({
    strings: { input: markdown },
    customRules: [paragraphLength],
    config: { default: false, "paragraph-length": { max_words: maxWords } },
  });
  return results.input.map((error) => ({ line: error.lineNumber, detail: error.errorDetail }));
}

function words(count) {
  return Array.from({ length: count }, (_, i) => `w${i}`).join(" ");
}

// Asserts that the single paragraph in `markdown` counts exactly `count` words.
function assertWordCount(markdown, count) {
  assert.deepEqual(errors(markdown, count), [], `more than ${count} words`);
  assert.equal(errors(markdown, count - 1).length, 1, `fewer than ${count} words`);
}

test("accepts a paragraph at the limit", () => {
  assert.deepEqual(errors(words(10)), []);
});

test("reports a paragraph over the limit with its word count", () => {
  const [error, ...rest] = errors(`# Title\n\n${words(11)}\n`);
  assert.deepEqual(rest, []);
  assert.equal(error.line, 3);
  assert.match(error.detail, /^11 words, limit is 10\./);
});

test("uses the configured limit", () => {
  assert.equal(errors(words(21), 20).length, 1);
  assert.deepEqual(errors(words(20), 20), []);
});

test("fails when max_words is not configured", () => {
  assert.throws(
    () =>
      lint({
        strings: { input: words(1) },
        customRules: [paragraphLength],
        config: { default: false, "paragraph-length": true },
      }),
    /max_words is not configured/,
  );
});

test("checks paragraphs in list items, blockquotes, and footnote definitions", () => {
  const markdown = [`- ${words(11)}`, "", `> ${words(11)}`, "", "Text[^1].", "", `[^1]: ${words(11)}`, ""].join("\n");
  assert.deepEqual(
    errors(markdown).map((error) => error.line),
    [1, 3, 7],
  );
});

test("checks a list inside a blockquote", () => {
  assertWordCount(`> - ${words(6)}\n>   ${words(5)}\n`, 11);
});

test("counts text over line breaks", () => {
  assert.equal(errors(`${words(6)}\n${words(5)}\n`).length, 1);
});

test("counts inline code, link text, and image alt text, but not URLs or inline HTML", () => {
  const markdown =
    'one `two three` [four five](https://example.com/a/b "six seven") ![eight](image.png) ' +
    "[nine][ref] <span>ten</span> **eleven**twelve AT&amp;T -- *\n\n[ref]: https://example.com\n";
  assertWordCount(markdown, 10);
});

test("counts the text of collapsed and shortcut reference links once", () => {
  assertWordCount("one [two three][] four [five] six\n\n[two three]: https://example.com\n[five]: https://example.com\n", 6);
});

test("does not count footnote markers", () => {
  assertWordCount("one two[^note] three [^other]\n\n[^note]: Note.\n\n[^other]: Other.\n", 3);
});

test("does not count inline HTML comments", () => {
  assertWordCount("one <!-- two three four --> five\n", 2);
});

test("counts inline math as text", () => {
  assertWordCount("Set $HOME and $PATH now\n", 5);
});

test("checks Markdown paragraphs inside an HTML block after a blank line", () => {
  assert.deepEqual(
    errors(`<div>\n\n${words(11)}\n\n</div>\n`).map((error) => error.line),
    [3],
  );
});

test("ignores code blocks, math blocks, tables, headings, HTML blocks, and HTML comments", () => {
  const markdown = [
    `# ${words(20)}`,
    "",
    "```text",
    words(20),
    "```",
    "",
    "$$",
    words(20),
    "$$",
    "",
    `| ${words(20)} |`,
    "| --- |",
    `| ${words(20)} |`,
    "",
    `<p>${words(20)}</p>`,
    "",
    `<!-- ${words(20)} -->`,
    "",
  ].join("\n");
  assert.deepEqual(errors(markdown), []);
});
