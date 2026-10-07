## CSS extensions

### Namespaces

<div class="notice">

**Version 4.0.0 does not interpret `@namespace`.** Declarations are skipped,
and selectors with prefixes do not match. Apply styles by element name alone,
without distinguishing namespaces. The following description applies to version 3.

</div>

The CSS namespace extensions[ (CSS Namespace Enhancements)](https://www.w3.org/TR/css3-namespace/) are supported, allowing you to style XML that mixes multiple namespaces.

To specify a namespace prefix and URI in a CSS stylesheet,
use an @namespace directive as follows.

```css
/* Set the default namespace URI to http://www.w3.org/1999/xhtml. */
@namespace "http://www.w3.org/1999/xhtml";

/* Set the URI for the rdf prefix to http://www.w3.org/1999/02/22-rdf-syntax-ns#. */
@namespace rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
```

For compatibility, you can also write the URI as url(http://www.w3.org/1999/xhtml).

When you use a prefix in a stylesheet selector, separate it with '|'. (Note that this is not ':'.)

```css
/* Specify the style of the <pdf:Description> element. */
rdf|Description { display: block; }

/* Specify the style of item elements whose ref:about attribute is http://foo.com/bar. */
item[rdf|about=http://foo.com/bar] { color: Red; }
```

The selector syntax and its meaning are as follows.

<dl>

<dt>prefix|ELEMENT</dt>
<dd>ELEMENT in the namespace indicated by prefix</dd>
<dt>|ELEMENT</dt>
<dd>ELEMENT in no namespace</dd>
<dt>*|ELEMENT</dt>
<dd>ELEMENT in any namespace or in no namespace</dd>
<dt>ELEMENT</dt>
<dd>ELEMENT in the default namespace</dd>

</dl>

### <a id="style-page-counter">Page counters</a>

Page counters are processed each time a page is generated, to number the pages.
Declare a page counter with the <span class="cssprop">counter-increment</span>
property in an @page rule, as follows.

```css
@page {
	counter-increment: page;
}
```

Page counters are processed immediately before the page content is processed. Like ordinary counters, you can reference a page counter with the counter function in the <span class="cssprop">content</span> property.
With the declaration above, processing <span class="cssdecl">content: counter(page);</span> on the first page therefore outputs 1.

To reset a page counter partway through a document (for example, to restart numbering for the body text after the table of contents), use <span class="cssprop">counter-reset</span>, as you would for an ordinary counter.
For example, to reset a counter named page to 1 on the page where an element appears, declare <span class="cssdecl">counter-reset: page 1;</span> on that element.

### <a id="style-list-style-type">List markers and numbering formats</a>

You can specify the following formats for <span class="cssprop">list-style-type</span>
and the second argument of `counter()`.

| Category | Values |
| --- | --- |
| Symbols | disc square circle none |
| Arabic numerals | decimal decimal-leading-zero |
| Latin letters | lower-alpha lower-latin upper-alpha upper-latin |
| Roman numerals | lower-roman upper-roman |
| Greek letters | lower-greek |
| Other writing systems | armenian georgian hebrew (all displayed as Arabic numerals) |
| Japanese and Chinese | hiragana hiragana-iroha katakana katakana-iroha cjk-ideographic |
| Proprietary extensions | -cssj-cjk-decimal -cssj-full-width-decimal |

`hiragana` follows the order "あ い う…", and `hiragana-iroha` follows "い ろ は…".
The same applies to katakana. `cjk-ideographic` uses kanji numerals such as "一 二 三…".
For positional kanji numerals, use the proprietary extension [-cssj-cjk-decimal](#style-cjk-decimal)
instead.

### <a id="style-counter-style">Custom numbering formats (@counter-style)<span class="since">4.0.0</span></a>

You can define symbols and sequences that are not available in the built-in formats with an `@counter-style` rule.
You can use the name you define in <span class="cssprop">list-style-type</span> and
as an argument to `counter()`, `counters()`, and `target-counter()`.

```css
@counter-style maru {
  system: fixed;              /* Use symbols in order, then fall back to decimal when they run out */
  symbols: "①" "②" "③" "④" "⑤";
  suffix: " ";
}
@counter-style kakko-kansuji {
  system: extends cjk-ideographic;  /* Base this on a built-in format and change only the suffix */
  prefix: "(";
  suffix: ") ";
}
ol.maru { list-style-type: maru; }
ol.kansuji { list-style-type: kakko-kansuji; }
```

You can specify the following values for `system`.

| system | Sequence |
| --- | --- |
| `cyclic` | Repeat the symbols from the beginning |
| `fixed` `fixed start-value` | Use each symbol once, then switch to `fallback` when they run out |
| `symbolic` | Repeat the same symbol an additional time after each cycle (*, **, ***) |
| `alphabetic` | Add positions as with letters (a, b, …, aa) |
| `numeric` | Use positional notation, with the first symbol representing 0 |
| `additive` | Add symbols in descending order of weight, as with Roman numerals |
| `extends name` | Inherit an existing format and change only the specified descriptors |

The descriptors are `symbols`, `additive-symbols`, `prefix`, `suffix`, `negative`
(before and after negative numbers), `range` (the range of use), `pad` (padding), and `fallback`
(the format to use when a number cannot be represented). If you specify an undefined name,
or a number that the format cannot represent, `decimal` (Arabic numerals) is used.

<div class="note">

`prefix` and `suffix` are added only to list markers. They are not added when you
retrieve a number as a string with `counter()` (as specified by CSS).

The `speak-as` descriptor for speech and the `symbols()` functional notation are not supported.
If you specify multiple ranges in `range`, only the first range is used.

</div>

### <a id="style-cjk-decimal">List numbering with full-width and kanji numerals</a>

The formats for marker characters at the start of list items are extended. You can use the following keywords
with <span class="cssprop">list-style-type</span>.

<dl>

<dt>-cssj-full-width-decimal<span class="since">3.0.0</span></dt>
<dd>Use full-width numerals for markers.</dd>
<dt>-cssj-cjk-decimal<span class="since">3.0.0</span></dt>
<dd>Use positional kanji numerals for markers.</dd>
<dt>-cssj-decimal-full-width<span class="since">2.1.2</span></dt>
<dd>The same as -cssj-full-width-decimal.</dd>

</dl>

```html
<html>
  <head>
    <style type="text/css">
    #a {
      list-style-type: -cssj-full-width-decimal;
    }
    #b {
      list-style-type: -cssj-cjk-decimal;
    }
    </style>
  </head>
  <body>
    <ol id="a">
      <li>Candied sardines</li>
      <li>Black soybeans</li>
      <li>Sweet potato and chestnut paste</li>
    </ol>
    <ol id="b">
      <li>Fish cake</li>
      <li>Sweet rolled omelet</li>
      <li>Herring roe</li>
    </ol>
  </body>
</html>
```

<div title="Marker formats (rendered output)" class="example">
	<ol style="list-style-type: -cssj-full-width-decimal;">
		<li>Candied sardines</li>
		<li>Black soybeans</li>
		<li>Sweet potato and chestnut paste</li>
	</ol>
	<ol style="list-style-type: -cssj-cjk-decimal;">
		<li>Fish cake</li>
		<li>Sweet rolled omelet</li>
		<li>Herring roe</li>
	</ol>
</div>

You can use the same keywords with the counter function in <span class="cssprop">content</span>.

```html
<html>
  <head>
    <style type="text/css">
    #a:before {
      counter-increment: a;
      content: counter(a, -cssj-full-width-decimal);
    }
    #b:after {
      counter-increment: b;
      content: counter(b, -cssj-cjk-decimal);
    }
    </style>
  </head>
  <body>
    <div id="a">Candied sardines</div>
    <div id="a">Black soybeans</div>
    <div id="a">Sweet potato and chestnut paste</div>
    <div id="b">Fish cake</div>
    <div id="b">Sweet rolled omelet</div>
    <div id="b">Herring roe</div>
  </body>
</html>
```

<div title="counter formats (rendered output)" class="example">
	<div>１Candied sardines</div>
	<div>２Black soybeans</div>
	<div>３Sweet potato and chestnut paste</div>
	<div>Fish cake一</div>
	<div>Sweet rolled omelet二</div>
	<div>Herring roe三</div>
</div>

### <a id="style-no-break">Kinsoku (line-breaking rules)</a>

Characters prohibited at the start of a line (characters immediately before which a line cannot break) are the full-width space and the following JLREQ classes:
closing brackets (cl-02), hyphens (cl-03), dividing punctuation marks (cl-04), middle dots (cl-05),
full stops (cl-06), commas (cl-07), iteration marks (cl-09), prolonged sound marks (cl-10),
and small kana (cl-11). The main punctuation characters are as follows.

<pre style="border: 1pt solid Black;">’ ” ） 〕 ］ ｝ 〉 》 」 』 】 ⦆ 〙 〗 » 〟
‐ 〜 ゠ – ！ ？ ‼ ⁇ ⁈ ⁉ ・ ： ； 。 ． 、 ，</pre>

Unicode END_PUNCTUATION (closing brackets), OTHER_PUNCTUATION (other brackets),
MODIFIER_LETTER (modifier letters), and MODIFIER_SYMBOL (modifier symbols) are also prohibited at line starts as supplementary rules.
However, the vertical kana repeat marks in the inseparable characters class (cl-08) are excluded from this blanket rule.

The following characters are prohibited at the end of a line (a line cannot break immediately after them).

<pre style="border: 1pt solid Black;">‘ “ （ 〔 ［ ｛ 〈 《 「 『 【 ⦅ 〘 〖 « 〝</pre>

These explicitly list the JLREQ opening brackets (cl-01). They include quotation marks such as `‘`, `“`, and `«`
that are not in Unicode START_PUNCTUATION. In addition,
Unicode START_PUNCTUATION (opening brackets) is prohibited at line ends as a supplementary rule.

For paired em dashes, paired ellipses, and `〳〵` and `〴〵` in the inseparable characters class (cl-08),
a line cannot break within each pair. A single dash or ellipsis, and the first character of a vertical kana repeat mark,
can appear at the start of a line. `〵` is not joined to an unrelated preceding character.

Line breaks are also prohibited between half-width letters and digits. However, breaks are allowed at half-width spaces and the following characters.

<pre style="border: 1pt solid Black;">-!?</pre>

#### word-wrap<span class="since">3.0.0</span>

The implementation of <span class="cssprop">word-wrap</span> follows CSS Text Module Level 3. The specification is as follows.

<dl>

<dt>Value</dt>
<dd>normal | break-word</dd>
<dt>Initial value</dt>
<dd>normal</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>Yes</dd>

</dl>

Setting break-word allows breaks at otherwise prohibited positions as needed,
to prevent the content from exceeding the line width.

```html
<html>
  <head>
    <style type="text/css">
    div {
      width: 6ex;
      border: 1pt solid Red;
    }
    #a {
      word-wrap: normal;
    }
    #b {
      word-wrap: break-word;
    }
    </style>
  </head>
  <body>
    <div id="a">Distance lends enchantment to the view.</div>
    <div id="b">Distance lends enchantment to the view.</div>
  </body>
</html>
```

<div title="word-wrap example (rendered output)" class="example">
	<div style="width: 6ex; border: 1pt solid Red; word-wrap: normal;">Distance
		lends enchantment to the view.</div>
	<div style="width: 6ex; border: 1pt solid Red; word-wrap: break-word;">Distance
		lends enchantment to the view.</div>
</div>

The proprietary CSS properties <span class="cssprop">-cssj-no-break-characters</span> and
<span class="cssprop">-cssj-break-characters</span> let you add or remove characters subject to kinsoku.

#### -cssj-no-break-characters<span class="since">3.0.6</span>

<dl>

<dt>Value</dt>
<dd>none | &lt;string&gt;{1,2}</dd>
<dt>Initial value</dt>
<dd>none</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>Yes</dd>

</dl>

Add characters subject to kinsoku. The first &lt;string&gt; specifies characters prohibited at line starts, and the second &lt;string&gt; specifies characters prohibited at line ends.
If you provide only one &lt;string&gt;, only characters prohibited at line starts are added.

For example, suppose you have the following text.

```html
<div style="border:1px solid; width: 7em;">
今日の相場は１＄がロンドンで９８円５５銭－先月と比べて２㌫上昇しました。
</div>
```

<div title="Before applying -cssj-no-break-characters (rendered output)" class="example">
	<div style="border: 1px solid; width: 7em;">
		今日の相場は１＄がロンドンで９８円５５銭－先月と比べて２㌫上昇しました。</div>
</div>

To prevent '㌫' and '＄' from appearing at the start of a line, and '－' from appearing at the end of a line, specify the following.

```html
<div style="border:1px solid; width: 7em; -cssj-no-break-characters: '㌫＄' '－';">
今日の相場は１＄がロンドンで９８円５５銭－先月と比べて２㌫上昇しました。
</div>
```

<div title="After applying -cssj-no-break-characters (rendered output)" class="example">
	<div
		style="border: 1px solid; width: 7em; -cssj-no-break-characters: '㌫＄' '－';">
		今日の相場は１＄がロンドンで９８円５５銭－先月と比べて２㌫上昇しました。</div>
</div>

#### -cssj-break-characters<span class="since">3.0.6</span>

<dl>

<dt>Value</dt>
<dd>none | &lt;string&gt;{1,2}</dd>
<dt>Initial value</dt>
<dd>none</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>Yes</dd>

</dl>

Remove characters from kinsoku restrictions. The first &lt;string&gt; specifies characters prohibited at line starts, and the second &lt;string&gt; specifies characters prohibited at line ends.
If you provide only one &lt;string&gt;, only characters prohibited at line starts are removed.

For example, the following text is adjusted so that small kana do not appear at the start of a line.

```html
<div style="border:1px solid; width: 10em;">
「トロメライ、ロマチックシューマン作曲。」猫は口を拭いて済まして云いました。
</div>
```

<div title="Before applying -cssj-break-characters (rendered output)" class="example">
	<div style="border: 1px solid; width: 10em;">
		「トロメライ、ロマチックシューマン作曲。」猫は口を拭いて済まして云いました。</div>
	<p>In practice, however, books often allow small kana at the start of a line. To follow this practice, use the following.</p>
</div>

```html
<div style="border:1px solid; width: 10em; -cssj-break-characters: 'ァィゥェォッャュョヮヵヶぁぃぅぇぉっゃゅょゎゕゖㇰㇱㇲㇳㇴㇵㇶㇷㇸㇹㇺㇻㇼㇽㇾㇿ';">
「トロメライ、ロマチックシューマン作曲。」猫は口を拭いて済まして云いました。
</div>
```

<div title="After applying -cssj-break-characters (rendered output)" class="example">
	<div
		style="border: 1px solid; width: 10em; -cssj-break-characters: 'ァィゥェォッャュョヮヵヶぁぃぅぇぉっゃゅょゎゕゖㇰㇱㇲㇳㇴㇵㇶㇷㇸㇹㇺㇻㇼㇽㇾㇿ';">
		「トロメライ、ロマチックシューマン作曲。」猫は口を拭いて済まして云いました。</div>
</div>

### <a id="style-autospace">Japanese text spacing<span class="since">4.0.0</span></a>

You can control character spacing in Japanese typesetting with properties from CSS Text Module Level 4.
The implementation refers to the W3C [Requirements for Japanese Text Layout (JLREQ)](https://www.w3.org/International/jlreq/?lang=ja),
but JLREQ is not a normative conformance test, and this implementation
does not claim full conformance. The subset described here defines the implementation scope.

<dl>
<dt><span class="cssprop">text-autospace</span></dt>
<dd>Insert quarter-em spacing between Japanese text and Latin text or digits.
	<b>The default is normal (spacing is inserted).</b>
	Full-width commas and full stops (、。，．) are excluded because their glyphs already have trailing space.
	When they are proportional (with an advance of 0.75 em or less, as in IPA P fonts or with <span class="cssprop">font-feature-settings</span>
	set to <tt>palt</tt>), quarter-em spacing is also inserted between them and following Latin text or digits<span class="since">4.0.0</span>.
	To restore the appearance of older versions, specify
	<span class="cssdecl">text-autospace: no-autospace;</span>.</dd>
<dt><span class="cssprop">text-spacing-trim</span></dt>
<dd>Reduce spacing between consecutive punctuation characters (brackets, commas, full stops, and middle dots). This covers the cl-01/cl-02
characters listed in the annex and also handles boundaries between
adjacent inline elements with different styles. Middle dots have the equivalent of quarter-em spacing before them and solid setting after them;
the space after them does not expand even with justification. The values are normal, space-all, space-first, trim-start,
trim-both, and auto. The default normal trims spacing within a line but leaves opening brackets at the line start at full width.
trim-start sets the line start flush, and trim-both trims both line starts and line ends. auto is a high-quality setting
equivalent to trim-both. space-first retains space at the line start only on the first line and immediately after a forced line break,
and space-all disables punctuation spacing reduction. Specify the indentation of the first line of a paragraph
with <span class="cssprop">text-indent</span>. A fixed reduction of 0.5 em is not applied
to proportional punctuation.</dd>
<dt><span class="cssprop">hanging-punctuation</span></dt>
<dd>You can specify none, first, allow-end, and force-end. first hangs the leading punctuation on the first formatted line;
allow-end hangs commas and full stops at line ends when they do not fit in the normal position; force-end hangs all
eligible commas and full stops at line ends. You can combine first with a line-end value in either order.</dd>
</dl>

Layout also handles kinsoku at line starts and line ends, inseparable character pairs, and flush placement
of full-width opening brackets at line starts in horizontal and vertical writing. Proportional opening brackets are not shifted by a fixed 0.5 em.

When a line slightly exceeds the available width, spacing is reduced in JLREQ order: between Latin words, at line-end punctuation, at line-end middle dots,
at middle dots within the line, at brackets and commas, and between Japanese and Latin text (from a quarter em to a minimum of an eighth em). If the line still
does not fit after all available reduction, it breaks at the preceding valid break point. Expansion for justification also follows an order: between Latin words,
between Japanese and Latin text, at other separable character boundaries, and finally by uniform distribution.
These JLREQ stages do not apply to lines containing only Latin text, with no Japanese text, full-width punctuation, ruby, or warichu.
Instead, word spacing is expanded as in Latin typesetting.

```css
/* Restore the previous layout (without added spacing) */
body {
	text-autospace: no-autospace;
	text-spacing-trim: space-all;
}
```

#### Warichu<span class="since">4.0.0</span>

An inline element with <span class="cssdecl">-cssj-warichu: auto;</span>
becomes two-line warichu at half the font size of the body text. The two lines are stacked vertically in horizontal writing and
side by side in vertical writing. The shorter line is centered, and kinsoku is respected within each line and at fragment boundaries in long warichu.
Long warichu can wrap across multiple body text lines as small fragments.

```html
本文<span style="-cssj-warichu:auto">この部分を割注として二段に組む</span>本文
```

Warichu content is limited to text. Images, inline blocks, and absolutely positioned content
are not laid out as warichu annotation text.

#### Subscripts, superscripts, and stacked alternatives<span class="since">4.0.0</span>

You can typeset subscripts, superscripts, and stacked alternatives with standard HTML/CSS. Set the group of base characters for subscripts or superscripts to
`inline-block` and `white-space: nowrap` to prevent line breaks and justification within it.
You can specify the font size in the document; JLREQ gives approximately 60% of the base character size as a general guideline.

For stacked alternatives, place each alternative in a row of an `inline-table` and specify `vertical-align: middle`.
The longest alternative determines the width, and the entire group acts as a single inline element without splitting across multiple body text lines.
You can use this structure in both horizontal and vertical writing.

```css
.scripted { display: inline-block; white-space: nowrap; }
.scripted > sup, .scripted > sub { font-size: 60%; }
.furiwake { display: inline-table; vertical-align: middle;
             line-height: 1; white-space: nowrap; }
.furiwake-row { display: table-row; }
.furiwake-cell { display: table-cell; }
```

The annotation glyph bounds (ink) of ruby and warichu do not increase the body text line height. They are drawn between lines while maintaining
the reference line positions. Use `line-height` to provide enough space between lines to avoid collisions.

#### Sidenotes and headnotes<span class="since">4.0.0</span>

You can place JLREQ parallel notes on the logical line-start or line-end side of the body text with proprietary float values.
These sides correspond to sidenotes in horizontal writing and headnotes or footnotes in vertical writing. Notes on the same side
are placed in order of proximity to their body text positions, without overlapping one another.

```css
@page { margin-inline: 48pt; }
.note-start { float: -cssj-note-start; inline-size: 36pt; }
.note-end   { float: -cssj-note-end;   inline-size: 36pt; }
```

Reserve space for notes by specifying the `@page` margins and the notes' own inline dimensions,
so they do not overlap the body text. These two values are proprietary extensions
because there is no standard CSS syntax for parallel notes.

### <a id="style-line-breaking">Line-breaking quality<span class="since">4.0.0</span></a>

You can choose the line-breaking method with the <span class="cssprop">text-wrap-style</span> property
(or the <span class="cssprop">text-wrap</span> shorthand).

<dl>

<dt>auto</dt>
<dd>The initial value. Fills each line with as much content as possible, one line at a time.
	This is the same method used by typical browsers and is fast.</dd>
<dt>pretty</dt>
<dd>Finds optimal breaks across the entire paragraph (the Knuth-Plass algorithm).
	This gives lines more consistent spacing and reduces consecutive lines ending in hyphens.
	Processing takes longer as a result.</dd>

</dl>

### <a id="style-text-justify">Distributing space for justification<span class="since">4.0.0</span></a>

With <span class="cssdecl">text-align: justify</span>, you can choose where to distribute the remaining space in a line
with the <span class="cssprop">text-justify</span> property.

<dl>

<dt>auto</dt>
<dd>The initial value. The language determines the behavior. Japanese lines use JLREQ's staged distribution
	(between Latin words → between Japanese and Latin text → between characters). Korean (<tt>lang="ko"</tt>) uses <b>word spacing only</b>,
	just as browsers do, without changing syllable advances. Other languages retain the previous behavior:
	space is distributed evenly at separable boundaries.</dd>
<dt>inter-word</dt>
<dd>Expand only word spaces (whitespace). Lines without word spaces remain unchanged.</dd>
<dt>inter-character</dt>
<dd>Distribute space between characters as well (<tt>distribute</tt> has the same meaning).</dd>
<dt>none</dt>
<dd>Do not justify.</dd>

</dl>

```css
p {
	text-wrap: pretty;
}
```

<div class="note">

`balance` and `stable` are accepted without an error but are treated as `auto`.

`pretty` takes effect only when specified on **the block that forms the paragraph**.
Specifying it on an inline element or `::first-line` does not change the line-breaking method.

The following paragraphs are processed with `auto` even if you specify `pretty`:
paragraphs with vertical writing; `white-space` set to `pre` or `pre-wrap`;
<span class="cssprop">-cssj-word-wrap</span> set to `break-word`;
ruby, inline replaced elements, inline blocks, or absolutely positioned inline content;
tabs; actual `text-autospace` insertion between Japanese and Latin text; floating boxes within the paragraph;
or `::first-line`.

</div>

### <a id="style-hyphens">Hyphenation<span class="since">4.0.0</span></a>

Use the <span class="cssprop">hyphens</span> property to specify
whether Latin words can be split with hyphens.

<dl>

<dt>manual</dt>
<dd>The initial value. Break only at possible break points (`&amp;shy;`) in the document.</dd>
<dt>auto</dt>
<dd>Break automatically based on a dictionary.</dd>
<dt>none</dt>
<dd>Do not break.</dd>

</dl>

```css
p {
	hyphens: auto;
}
```

<div class="note">

`hyphens: auto` works only in ranges where the <tt>lang</tt> attribute is `en` (English).
For other languages, it behaves the same as `manual`.

</div>

### <a id="style-word-wrap">Wrapping within long words</a>

Setting <span class="cssprop">word-wrap</span> to `break-word`
allows a long word that does not fit on a line to break within the word. The initial value is `normal`,
which lets the word overflow without breaking.

### <a id="style-text-emphasis">Emphasis marks<span class="since">3.0.4</span></a>

You can add emphasis marks, which are mainly used to emphasize parts of Japanese text.
In new documents, use the standard CSS Text Decoration Module Level 3 names
<span class="cssprop">text-emphasis-style</span>,
<span class="cssprop">text-emphasis-color</span>, and
<span class="cssprop">text-emphasis</span>.

The older <span class="cssprop">-cssj-</span> prefix and the EPUB-compatible
<span class="cssprop">-epub-</span> prefix are also available as aliases with the same meaning.

Emphasis mark symbols are displayed with the body text font. For more attractive emphasis marks, using [Kenten Generic OpenType Font](https://github.com/adobe-fonts/kenten-generic)
as an embedded font is recommended. Specify the body text font family as <span class="cssdecl">font-family: 'Kenten Generic' body-text-font...;</span>, for example.

#### text-emphasis-style

<dl>

<dt>Value</dt>
<dd>none | [ [ filled | open ] || [ dot | circle | double-circle | triangle | sesame ] ] | &lt;string&gt;</dd>
<dt>Initial value</dt>
<dd>none</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>Yes</dd>

</dl>

When none is set, no emphasis marks are added.

For other values, the emphasis mark types (Unicode characters) are as follows.

<dl>

<dt>filled dot</dt>
<dd>U+2022 ‘•’</dd>
<dt>open dot</dt>
<dd>U+25E6 ‘◦’</dd>
<dt>filled circle</dt>
<dd>U+25CF ‘●’</dd>
<dt>open circle</dt>
<dd>U+25CB ‘○’</dd>
<dt>filled double-circle</dt>
<dd>U+25C9 ‘◉’</dd>
<dt>open double-circle</dt>
<dd>U+25CE ‘◎’</dd>
<dt>filled triangle</dt>
<dd>U+25B2 ‘▲’</dd>
<dt>open triangle</dt>
<dd>U+25B3 ‘△’</dd>
<dt>filled sesame</dt>
<dd>
	U+FE45 <span class="for-screen">‘﹅’</span><span class="for-print">‘<img
		src="images/filled-sesame.svg" style="width: 6pt; margin: 4pt;" />’
	</span>
</dd>
<dt>open sesame</dt>
<dd>
	U+FE46 <span class="for-screen">‘﹆’</span><span class="for-print">‘<img
		src="images/open-sesame.svg" style="width: 6pt; margin: 4pt;" />’
	</span>
</dd>

</dl>

If you specify only filled or open, it is equivalent to filled circle or open circle, respectively, in horizontal writing,
and filled sesame or open sesame, respectively, in vertical writing.

If you specify a string, its first character becomes the emphasis mark.

```html
<html>
  <head>
    <style type="text/css">
    #a {
      text-emphasis-style: filled;
    }
    #b {
      text-emphasis-style: open triangle;
    }
    #c {
      text-emphasis-style: '※';
    }
    </style>
  </head>
  <body>
<p><span id="a">ここ</span>に丸い圏点を打ちます</p>
<p><span id="b">ここ</span>に三角の圏点を打ちます</p>
<p><span id="c">ここ</span>に米印の圏点を打ちます</p>
  </body>
</html>
```

<div title="text-emphasis-style example (rendered output)" class="example">
	<p>
		<span style="text-emphasis-style: filled;">ここ</span>に丸い圏点を打ちます
	</p>
	<p>
		<span style="text-emphasis-style: open triangle;">ここ</span>に三角の圏点を打ちます
	</p>
	<p>
		<span style="text-emphasis-style: '※';">ここ</span>に米印の圏点を打ちます
	</p>
</div>

#### text-emphasis-color

<dl>

<dt>Value</dt>
<dd>&lt;color&gt;</dd>
<dt>Initial value</dt>
<dd>The same as the text color</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>Yes</dd>

</dl>

Specify the emphasis mark color. Specify the color in the same way as for the <span class="cssprop">color</span> property and similar properties.

```html
<html>
  <head>
    <style type="text/css">
    span {
      text-emphasis-style: filled;
    }
    #a {
      text-emphasis-color: Red;
    }
    #b {
      color: Red;
    }
    </style>
  </head>
  <body>
<p><span id="a">この</span>圏点は赤いです</p>
<p><span id="b">この</span>圏点も文字も赤です</p>
  </body>
</html>
```

<div title="text-emphasis-color example (rendered output)" class="example">
	<p>
		<span style="text-emphasis-style: filled; text-emphasis-color: Red;">この</span>圏点は赤いです
	</p>
	<p>
		<span style="text-emphasis-style: filled; color: Red;">この</span>圏点も文字も赤です
	</p>
</div>

#### text-emphasis

This property specifies <span class="cssprop">text-emphasis-style</span> and <span class="cssprop">text-emphasis-color</span> together.
Specify the emphasis mark style first, followed by the color.

```html
<html>
  <head>
    <style type="text/css">
    #a {
      text-emphasis: filled triangle Red;
    }
    #b {
      text-emphasis: '※' Pink;
    }
    </style>
  </head>
  <body>
<p><span id="a">この</span>圏点は赤い三角です</p>
<p><span id="b">この</span>圏点はピンクの米印です</p>
  </body>
</html>
```

<div title="text-emphasis example (rendered output)" class="example">
	<p>
		<span style="text-emphasis: filled triangle Red;">この</span>圏点は赤い三角です
	</p>
	<p>
		<span style="text-emphasis: '※' Pink;">この</span>圏点はピンクの米印です
	</p>
</div>

### <a id="style-text-shadow">Text shadows<span class="since">3.0.8</span></a>

Text drop shadows, as supported by typical browsers, are available.
Shadow blur is also supported<span class="since">4.0.0</span>. Image output (PNG/JPEG) and SVG-based output
use true Gaussian blur. PDF output also uses true blur by rasterizing only the shadow and placing it as an image with transparency<span class="since">4.0.0</span> (text and body content remain vector-based; the resolution is set by <span class="ioprop">output.pdf.blur-resolution</span>). For PDF/A-1 and PDF/X, where transparency is unavailable, blur is approximated by overlaying 12 levels of outlines, and warning 2822 is issued.

#### text-shadow

<dl>

<dt>Value</dt>
<dd>none | [ &lt;length&gt;{2,3} &amp;&amp; &lt;color&gt;? ]#</dd>
<dt>Initial value</dt>
<dd>none</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>Yes</dd>

</dl>

Specify the <span class="cssprop">text-shadow</span> values in this order: the shadow's x offset, y offset, blur radius (optional), and color.
You can also create multiple shadows by separating them with commas. The first shadow is created behind the text, with each subsequent shadow further behind.

Shadows are drawn as <b>glyph outlines</b> rather than text<span class="since">4.0.0</span>, so extracting text from the PDF does not produce duplicates for the shadows. However, fonts whose glyph data is not available locally (non-embedded CID-keyed fonts and the 14 standard PDF fonts) cannot be converted to outlines, so their shadows are also output as text. To keep extracted PDF text clean, embed fonts by setting <span class="ioprop">output.pdf.fonts.policy</span> with <tt>embedded</tt> first.

```html
<html>
  <head>
    <style type="text/css">
    * {
      font-size: 32pt;
    }
    #a {
      text-shadow: 4pt 8pt Gray;
    }
    #b {
      text-shadow: 8pt 8pt Gray, 16pt 16pt LightGray;
    }
    </style>
  </head>
  <body>
<p id="a">Text with a gray shadow</p>
<p id="b">A lighter gray shadow behind a gray shadow</p>
  </body>
</html>
```

<div title="text-shadow example (rendered output)" class="example">
	<p style="font-size: 32pt; text-shadow: 4pt 8pt Gray;">Text with a gray shadow</p>
	<p style="font-size: 32pt; text-shadow: 8pt 8pt Gray, 16pt 16pt LightGray;">A lighter gray shadow behind a gray shadow</p>
</div>

### <a id="style-text-stroke">Outlined text<span class="since">3.0.8</span></a>

Properties that specify text outlines and fills separately are provided for compatibility with browsers that use the WebKit rendering engine (such as Google Chrome and Safari).
You can use them to create an outlined text effect.

The four proprietary properties <span class="cssprop">-cssj-text-fill-color</span>,
<span class="cssprop">-cssj-text-stroke-width</span>,
<span class="cssprop">-cssj-text-stroke-color</span>,
<span class="cssprop">-cssj-text-stroke</span>
are available. For compatibility with WebKit, you can also use the respective property names
<span class="cssprop">-webkit-text-fill-color</span>,
<span class="cssprop">-webkit-text-stroke-width</span>,
<span class="cssprop">-webkit-text-stroke-color</span>,
<span class="cssprop">-webkit-text-stroke</span>
as aliases.

By default, the stroke is drawn over the fill, so a thick stroke makes the filled text appear thinner.
Set the <span class="cssprop">paint-order</span> property, as in SVG, to
<tt>stroke fill</tt> to draw the stroke first and then the fill over it.
This adds an outline only on the outside while preserving the thickness of the glyphs<span class="since">4.0.0</span>.
You can use this for effects such as a white outline around a title placed over an illustration.

```css
h1.title {
	-cssj-text-stroke: 2pt white;
	paint-order: stroke fill;
}
```

#### -cssj-text-fill-color

<dl>

<dt>Value</dt>
<dd>&lt;color&gt; | currentcolor</dd>
<dt>Initial value</dt>
<dd>currentcolor</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>Yes</dd>

</dl>

Set the text fill color.
If you do not specify it, it is the same as the color specified by <span class="cssprop">color</span>.

#### -cssj-text-stroke-width

<dl>

<dt>Value</dt>
<dd>&lt;width&gt; | medium | thick | thin</dd>
<dt>Initial value</dt>
<dd>0</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>Yes</dd>

</dl>

Specify the text stroke width.
Setting a value other than 0 causes the stroke to be drawn.

#### -cssj-text-stroke-color

<dl>

<dt>Value</dt>
<dd>&lt;color&gt; | currentcolor</dd>
<dt>Initial value</dt>
<dd>currentcolor</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>Yes</dd>

</dl>

Set the text stroke color.
If you do not specify it, it is the same as the color specified by <span class="cssprop">color</span>.

#### -cssj-text-stroke

<dl>

<dt>Value</dt>
<dd>&lt;width&gt; &lt;color&gt;</dd>
<dt>Initial value</dt>
<dd>none</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>Yes</dd>

</dl>

Set the text stroke width and color together.

```html
<html>
  <head>
    <style type="text/css">
    * {
      font-size: 32pt;
    }
    #a {
      -cssj-text-stroke-width: 2pt;
    }
    #b {
      -cssj-text-stroke: 1pt Black;
      -cssj-text-fill-color: White;
    }
    </style>
  </head>
  <body>
<p id="a">Text with a thicker outline</p>
<p>In this text, <span id="b">this part</span> has a white fill</p>
  </body>
</html>
```

<div title="Outlined text (rendered output)" class="example">
<p style="font-size: 32pt; -cssj-text-stroke-width: 2pt;">Text with a thicker outline</p>
<p style="font-size: 32pt;">In this sentence, <span style="-cssj-text-stroke: 1pt Black; -cssj-text-fill-color: White;">this part</span> has a white fill</p>
</div>

### <a id="style-opacity">Transparency<span class="since">3.0.6</span></a>

#### opacity

<dl>

<dt>Value</dt>
<dd>&lt;alphavalue&gt;</dd>
<dt>Initial value</dt>
<dd>1</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

Set the opacity of an element. The value is a decimal number from 0 (transparent) to 1 (opaque).
For example, a value of 0.5 gives the element 50% transparency.

```html
<html>
  <head>
    <style type="text/css">
    * {
      font-size: 32pt;
    }
    #a {
      position: relative;
      top: -32pt;
      font-size: 32pt;
      border: 5pt solid Red;
      background-color: Yellow;
      opacity: 0.7;
    }
    </style>
  </head>
  <body>
<div>Text with a thicker outline</div>
<div id="a">A box with transparency</div>
  </body>
</html>
```

<div title="opacity example (rendered output)" class="example">
<div style="font-size: 32pt;">Text in the background</div>
<div style="position: relative; top: -32pt; font-size: 32pt; border: 5pt solid Red; background-color: Yellow; opacity: .7;">A semitransparent box</div>
</div>

### <a id="style-alpha">Transparent colors<span class="since">3.0.8</span></a>

The <span class="cssprop">opacity</span> property described above makes the entire element semitransparent. Separately, the rgba function lets you add transparency to each specified color.
For example, this lets you specify different transparency levels for text and borders.
You can use the rgba function in color properties such as <span class="cssprop">color</span>, <span class="cssprop">background-color</span>, <span class="cssprop">border-color</span>
and other properties that accept colors.
The numbers in the rgba function specify red, green, blue, and opacity, in that order. Specify opacity as a decimal number from 0 to 1.

```html
<html>
  <head>
    <style type="text/css">
    * {
      font-size: 32pt;
    }
    #a {
      position: relative;
      top: -32pt;
      font-size: 32pt;
      border: 5pt solid Red;
      background-color: rgba(255,255,0,0.5);
    }
    </style>
  </head>
  <body>
<div>Text with a thicker outline</div>
<div id="a">A box with transparency</div>
  </body>
</html>
```

<div title="rgba example (rendered output)" class="example">
<div style="font-size: 32pt;">Text in the background</div>
<div style="position: relative; top: -32pt; font-size: 32pt; border: 5pt solid Red; background-color: rgba(255,255,0,0.5);">Only the background is semitransparent</div>
</div>

### <a id="style-color-functions">New color notations<span class="since">4.0.0</span></a>

In addition to `rgb()` and `rgba()`, you can use the following color functions. They are widely used in recent web
stylesheets, such as for default colors in CSS frameworks.

| Function | Meaning |
| --- | --- |
| `hsl()` `hsla()` | Specify hue, saturation, and lightness |
| `oklch()` | Specify lightness, chroma, and hue (in a perceptual color space) |
| `oklab()` | Specify the same color space with Cartesian coordinates |
| `color-mix()` | Mix two colors. Supports `in srgb`, `in oklab`, and `in oklch` |
| `light-dark()` | Specify separate colors for light and dark color schemes |

```css
.a { color: oklch(0.63 0.26 29); }            /* Red */
.b { background-color: hsl(210 80% 50% / 0.5); }
.c { border-color: color-mix(in oklab, Blue 30%, White); }
.d { color: light-dark(Black, White); }
```

<div class="note">

Colors are ultimately converted to sRGB for storage. Colors outside the sRGB range
are clamped to that range.

`light-dark()` **always uses the first color (for a light color scheme)**,
because a printed page does not switch between light and dark modes.

The `lab()`, `lch()`, `hwb()`, and `color()` functions<span class="since">4.0.0</span> are also supported.
With `color()`, you can specify the `srgb`, `srgb-linear`, `display-p3`, `a98-rgb`, `prophoto-rgb`,
`rec2020`, `xyz`, `xyz-d50`, and `xyz-d65` color spaces.
All are converted to sRGB, and colors outside the sRGB gamut have each component clamped to 0–1.

Relative color syntax that derives colors from existing colors (such as `rgb(from ...)`) is not supported.

</div>

### <a id="style-border-radius">Rounded borders<span class="since">3.0.6</span></a>

You can round the four corners of a box's border.

The four corners of the background are also rounded.
There is a known issue where the background may extend beyond the border if the four borders have different colors, widths, styles, or corner radii.

Use the four properties of the form `border-*-*-radius` to specify corner radii.
These properties take one or two lengths or percentages. One value specifies the corner radius; two values create an elliptical arc, specifying the horizontal and vertical radii, respectively.

#### border-top-left-radius

<dl>

<dt>Value</dt>
<dd>[ &lt;length&gt; | &lt;percentage&gt; ]{1,2}</dd>
<dt>Initial value</dt>
<dd>0</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

#### border-top-right-radius

<dl>

<dt>Value</dt>
<dd>[ &lt;length&gt; | &lt;percentage&gt; ]{1,2}</dd>
<dt>Initial value</dt>
<dd>0</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

#### border-bottom-left-radius

<dl>

<dt>Value</dt>
<dd>[ &lt;length&gt; | &lt;percentage&gt; ]{1,2}</dd>
<dt>Initial value</dt>
<dd>0</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

#### border-bottom-right-radius

<dl>

<dt>Value</dt>
<dd>[ &lt;length&gt; | &lt;percentage&gt; ]{1,2}</dd>
<dt>Initial value</dt>
<dd>0</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

<span class="cssprop">border-radius</span> specifies the properties above together.
Specify the values in this order:
<span class="cssprop">border-top-left-radius</span>,
<span class="cssprop">border-top-right-radius</span>,
<span class="cssprop">border-bottom-right-radius</span>,
<span class="cssprop">border-bottom-left-radius</span>
for the four corners, respectively.
If you omit the fourth value, <span class="cssprop">border-bottom-left-radius</span> has
the same value as <span class="cssprop">border-top-right-radius</span>.
If you omit the third value, <span class="cssprop">border-bottom-right-radius</span> has
the same value as <span class="cssprop">border-top-left-radius</span>.
If you omit the second value, the same value applies to all corners.

Specify the horizontal radii as one group and the vertical radii as another, separated by '/' (a slash).

#### border-radius

<dl>

<dt>Value</dt>
<dd>[ &lt;length&gt; | &lt;percentage&gt; ]{1,4} [ / [ &lt;length&gt; | &lt;percentage&gt; ]{1,4} ]?</dd>
<dt>Applies to</dt>
<dd>All elements</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

```html
<html>
  <head>
    <style type="text/css">
    div {
      border: 1pt solid Red;
      background-color: Yellow;
      height: 50pt;
    }
    #a {
      border-radius: 5pt;
    }
    #b {
      border-radius: 10pt 20pt / 20pt 10pt;
      border-top-left-radius: 30pt;
    }
    </style>
  </head>
  <body>
<div id="a">A box with rounded corners</div>
<div id="b">A box with irregular corners</div>
  </body>
</html>
```

<div title="Rounded borders (rendered output)" class="example">
<div style="border-radius: 5pt; height: 50pt;border: 1pt solid Red; background-color: Yellow;">A box with rounded corners</div>
<div style="border-radius: 10pt 20pt / 20pt 10pt; border-top-left-radius: 30pt; height: 50pt; border: 1pt solid Red; background-color: Yellow;">A box with irregular corners</div>
</div>

### <a id="style-transform">Rotation, scaling, and transformation<span class="since">3.0.8</span></a>

You can rotate, scale, and otherwise transform boxes with two-dimensional transformations.
Three-dimensional transformations are not supported.

Use the standard <span class="cssprop">transform</span> and
<span class="cssprop">transform-origin</span> properties.
For compatibility, <span class="cssprop">-cssj-transform</span>,
<span class="cssprop">-webkit-transform</span>,
<span class="cssprop">-moz-transform</span> (and the corresponding
<span class="cssprop">*-transform-origin</span> properties) are also accepted.

#### transform

<dl>

<dt>Value</dt>
<dd>none | &lt;transform-function&gt; [ &lt;transform-function&gt; ]*</dd>
<dt>Initial value</dt>
<dd>none</dd>
<dt>Applies to</dt>
<dd>Block-level elements</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

Specify the transformation matrix for the box.
You can specify none or multiple transformation functions.
The available transformation functions are as follows.

<dl>

<dt>matrix(a,b,c,d,e,f)</dt>
<dd>Specify the transformation matrix directly.</dd>

</dl>

The transformation matrix is specified as follows.

<div><img src="images/3x3matrix.png" width="82" height="79"/></div>

a is the horizontal scale factor, b the vertical skew factor, c the horizontal skew factor, d the vertical scale factor, e the horizontal translation distance, and f the vertical translation distance.
You can use length units such as pt and em for e and f. Values without a unit are in pt. Percentages are not supported.

<dl>

<dt>translate(x, y)</dt>
<dd>Specify the horizontal and vertical translation distances.
If you omit y, only the horizontal distance is specified.
You can use length units. Values without a unit are in pt.
</dd>

</dl>

<dl>

<dt>translateX(x)</dt>
<dd>Specify the horizontal translation distance.
You can use length units. Values without a unit are in pt.</dd>

</dl>

<dl>

<dt>translateY(y)</dt>
<dd>Specify the vertical translation distance.
You can use length units. Values without a unit are in pt.</dd>

</dl>

<dl>

<dt>scale(x, y)</dt>
<dd>Specify the horizontal and vertical scale factors.</dd>

</dl>

<dl>

<dt>scaleX(x)</dt>
<dd>Specify the horizontal scale factor.</dd>

</dl>

<dl>

<dt>scaleY(y)</dt>
<dd>Specify the vertical scale factor.</dd>

</dl>

<dl>

<dt>rotate(theta)</dt>
<dd>Rotate the box. theta is an angle in degrees when followed by deg, or in radians when no unit is given.</dd>

</dl>

<dl>

<dt>skew(xtheta, ytheta)</dt>
<dd>Specify the horizontal and vertical skew angles.
Each value is an angle in degrees when followed by deg, or in radians when no unit is given.</dd>

</dl>

<dl>

<dt>skewX(xtheta)</dt>
<dd>Specify the horizontal skew angle.
xtheta is an angle in degrees when followed by deg, or in radians when no unit is given.</dd>

</dl>

<dl>

<dt>skewY(ytheta)</dt>
<dd>Specify the vertical skew angle.
ytheta is an angle in degrees when followed by deg, or in radians when no unit is given.</dd>

</dl>

#### transform-origin

<dl>

<dt>Value</dt>
<dd>&lt;position&gt; [ , &lt;position&gt; ]*</dd>
<dt>Initial value</dt>
<dd>50% 50%</dd>
<dt>Applies to</dt>
<dd>Block-level elements</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

Specify the center point of the transformation.
The default is the center of the box. For example, the rotate function uses the center of the box as the center of rotation.

```html
<html>
  <head>
    <style type="text/css">
    body {
      margin: 40pt;
    }
    div {
      font-size: 32pt;
      position: absolute;
      transform-origin: 5pt 5pt;
    }
    #a {
      transform: rotate(0deg);
    }
    #b {
      transform: rotate(90deg);
    }
    #c {
      transform: rotate(180deg);
    }
    #d {
      transform: rotate(270deg);
    }
    </style>
  </head>
  <body>
<div id="a">▼</div>
<div id="b">▼</div>
<div id="c">▼</div>
<div id="d">▼</div>
  </body>
</html>
```

<div title="transform, transform-origin example (rendered output)" class="example">
<div style="margin: 40pt">
<div style="font-size: 32pt; position: absolute; transform-origin: 0 0;">▼</div>
<div style="font-size: 32pt; position: absolute; transform-origin: 0 0;transform: rotate(90deg);">▼</div>
<div style="font-size: 32pt; position: absolute; transform-origin: 0 0;transform: rotate(180deg);">▼</div>
<div style="font-size: 32pt; position: absolute; transform-origin: 0 0;transform: rotate(270deg);">▼</div>
</div>
</div>
