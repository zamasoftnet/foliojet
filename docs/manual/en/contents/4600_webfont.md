## <a id="style-webfont">WebFont</a>

You can use WebFont.
WebFont lets you specify fonts on the file system or network through CSS and load them when laying out a document.
Major browsers such as Chrome, Edge, Safari, and Firefox support WebFont.
Documents designed for these browsers to display the same fonts regardless of the viewing environment
can display those same fonts here.

Although WebFont is very easy to use, it slows down processing because font files are loaded each time a document is laid out.
We recommend limiting WebFont use to development or cases where it is essential, and using <b>system font configuration</b> (pdfg2d manual) whenever possible.

### @font-face rule

Use the CSS @font-face rule to load font files from a document. The following example loads a font for Latin text.

```xml
<html>
<head>
  <style type="text/css">
    @font-face {
      font-family: "VeraSerif";
      src: url("http://dl.cssj.jp/docs/copper/misc/bitstream-vera/Vera.ttf");
    }
    @font-face {
      font-family: "VeraSerif";
      font-weight: bold;
      src: url("http://dl.cssj.jp/docs/copper/misc/bitstream-vera/VeraBd.ttf");
    }
    @font-face {
      font-family: "VeraSerif";
      font-style: italic;
      src: url("http://dl.cssj.jp/docs/copper/misc/bitstream-vera/VeraIt.ttf");
    }
    body {
      font-family: "VeraSerif"
    }
    .bold {
      font-weight: bold;
    }
    .italic {
      font-style: italic;
    }
  </style>
</head>
<body>
  <p>This is Bitstream Vera Serif.</p>
  <p class="bold">This is Bitstream Vera Serif Bold.</p>
  <p class="italic">This is Bitstream Vera Serif Italic.</p>
</body>
</html>
```

<div class="figure" title="Loading fonts from the network (rendered output)">
  <img src="images/webfont-1.png" style="width: 120mm;" alt="Loading fonts from the network (rendered output)" />
</div>

Within @font-face, you can set the <span class="cssprop">font-family</span>, <span class="cssprop">font-style</span>, <span class="cssprop">font-weight</span>,
<span class="cssprop">unicode-range</span>, and <span class="cssprop">src</span>
properties. Of these, <span class="cssprop">font-family</span> and <span class="cssprop">src</span> are required.

<span class="cssprop">font-family</span>, <span class="cssprop">font-style</span>,
and <span class="cssprop">font-weight</span> become attributes of the loaded font.
They are used to select an appropriate font in the document. For information on font selection,
see <a href="#admin-config-fonts-select" class="pageref">Using fonts in a document</a>.

#### font-family

The font family name.

#### font-style

The font style: normal, italic, or oblique. The default is normal.

#### font-weight

The font weight: normal, bold, or a number from 1 to 1000<span class="since">4.0.0</span>. A value that is not a multiple of 100 is treated as the multiple of 100 that leaves font matching unchanged. The default is normal.

#### unicode-range

The range of character codes for which the font is available.
A character is available if its code is in the specified range and it is defined in the font file.
The default is U+0-10FFFF.

Examples follow.

<dl>
  <dt>unicode-range: U+A5;</dt>
  <dd>Applies only to the yen sign (¥). The full-width "￥" (U+FFE5) is not included.</dd>
  <dt>unicode-range: U+0-7F;</dt>
  <dd>Applies only to ASCII characters (character codes 0 through 127).</dd>
  <dt>unicode-range: U+30??;</dt>
  <dd>Applies only to Japanese symbols (punctuation, brackets, and so on), hiragana, and katakana (U+3000 through U+30FF).</dd>
  <dt>unicode-range: U+A5, U+0-7F, U+30??;</dt>
  <dd>Combines the three code ranges above.</dd>
</dl>

#### src

The location of the font file. Examples follow.

<dl>
  <dt>src: url(fonts/IPAMincho.otf);</dt>
  <dd>Loads the font file at the path fonts/IPAMincho.otf.</dd>
  <dt>src: local(ＭＳ明朝)</dt>
  <dd>Loads the font named ＭＳ明朝 installed on the operating system where layout runs.</dd>
  <dt>src: local(ＭＳ明朝), url(fonts/IPAMincho.otf)</dt>
  <dd>Uses ＭＳ明朝 if it is installed on the operating system and available;
    otherwise, loads fonts/IPAMincho.otf.</dd>
</dl>

The supported font formats are TrueType, OTF, and WOFF<span class="since">3.1.0</span>. SVG fonts and similar formats are not supported.

Fonts loaded with @font-face are used and embedded in the PDF whatever the font policy (<span class="ioprop">output.pdf.fonts.policy</span>)<span class="since">4.0.0</span>. Even with the default policy (core and CID-keyed fonts), a font the document names is not replaced.

An @font-face font is fetched and read when a style that names its family first appears<span class="since">4.0.0</span>. A family the document declares but never uses (such as a Japanese web font that a site-wide style sheet imports) is not fetched. The warning that none of the src entries can be read also comes only when the family is used.

The following example uses [IPA P Gothic](https://ipafont.ipa.go.jp/) for kanji and [Kiloji](http://ola.kironono.com/entry/fonts-kiloji) for hiragana, Latin letters, and digits.

```xml
<html>
<head>
  <style type="text/css">
    @font-face {
      font-family: "MyFont";
      src: url("http://dl.cssj.jp/docs/copper/misc/ipagp.otf");
      unicode-range: U+4E00-9FFF;
    }
    @font-face {
      font-family: "MyFont";
      src: url("http://dl.cssj.jp/docs/copper/misc/kiloji.ttf");
      unicode-range: U+A5, U+0-7F, U+30??;
    }
    body {
      font-family: "MyFont";
    }
  </style>
</head>
<body>
  <p>目に青葉／山ほととぎす／初がつお</p>
</body>
</html>
```

<div class="figure" title="Combining multiple fonts (rendered output)">
  <img src="images/webfont-2.png" style="width: 120mm;" alt="Combining multiple fonts (rendered output)" />
</div>
