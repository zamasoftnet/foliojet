## 対応する入力ファイル

### <a id="style-xml">HTML/XMLの処理</a>

#### <a id="style-input-filters">入力フィルタ</a>

読み込んだ文書へ順に適用する前処理を
<span class="ioprop">input.filters</span>で指定します。
フィルタ名をスペース区切りで、適用する順に並べます。

<dl>

<dt>xslt</dt>
<dd>`<?xml-stylesheet ...?>`処理命令で指定されたXSL変換を適用します。</dd>
<dt>default-to-xhtml</dt>
<dd>XMLで名前空間が指定されていない要素を、XHTMLの名前空間へ移します。</dd>
<dt>loose-html</dt>
<dd>一般的なHTML(閉じ忘れなどを含むもの)を解釈できるようにします。</dd>

</dl>

既定は`xslt default-to-xhtml loose-html`です。

#### <a id="style-input-mime">指定できるMIME型</a>

ドライバから文書を送るときは、内容に応じたMIME型を指定します。
次の型を受け付けます。

| MIME型 | 内容 |
| --- | --- |
| `text/html` | HTML(緩い記述を許します) |
| `application/xhtml+xml`、`text/xhtml`、`application/xhtml` | XHTML |
| `application/xml`、`text/xml` | XML(XSLTやデフォルト名前空間の変換を適用します) |
| `text/markdown`、`text/x-markdown` | [Markdown](#style-markdown) |
| `application/epub+zip` | [EPUB](#style-epub) |
| `application/epub+directory` | 展開済みEPUBディレクトリ(内部・開発用途) |
| `image/jpeg`、`image/png` など | [画像](#style-image)(そのままPDFへ変換します) |

MIME型を省略した場合は、内容から推測します。

#### ドキュメントの判別

ドキュメントがHTMLかXMLであるかは、以下の情報をもとに判別します。

1. プログラマが指定したドキュメントのMIMEタイプ
2. HTTPのContent-Typeヘッダ

1の情報は、2より優先されます。 上記の情報以外でドキュメントの型を判別することはありません。
HTMLとして判別されたドキュメントは、
<tt>&lt;?xml～</tt>
で開始していても、HTMLとして認識します。 ただし、HTMLと認識されても、XMLやXHTMLがサポートしている名前空間は認識されます。

HTMLと認識された文書は、ゆるやかに解釈されるため、文法ミスが許容されます。
デフォルトの名前空間（xmlns="～"で指定される名前空間）はXHTMLの名前空間（http://www.w3.org/1999/xhtml）に常に固定されます。
<span class="ioprop">input.html.change-default-namespace</span>をtrueに設定するとデフォルトの名前空間を変更できるようになります。<span class="since">3.2.12</span>。

一方、XMLと認識された文書は厳密に解釈されます。 XHTMLを記述する場合、全ての要素名と属性名は小文字で記述してください。
文法エラーがあった場合、処理が停止します。

##### キャラクタ・エンコーディング

認識できるキャラクタ・エンコーディングはJava実行環境に依存します。 エンコーディング名のリストは [Java実行環境の「サポートされているエンコーディング」ドキュメント](https://docs.oracle.com/javase/jp/8/docs/technotes/guides/intl/encoding.doc.html) を参照してください。

XMLドキュメントのキャラクタ・エンコーディングは、次の優先順位で判別します。

1. プログラマが指定したドキュメントのキャラクタ・エンコーディング
2. BOM(Byte Order Mark)
3. XML宣言のencoding属性

デフォルトのエンコーディングは、XMLの仕様に従い、UTF-8またはUTF-16が自動判別されます。

HTMLドキュメントのキャラクタ・エンコーディングは、次の優先順位で判別します。

1. プログラマが指定したドキュメントのキャラクタ・エンコーディング
2. BOM(Byte Order Mark)
3. <tt>&lt;meta http-equiv="Content-Type" content="text/html; charset=エンコーディング名"&gt;</tt>
4. XML宣言のencoding属性
5. <span class="ioprop">input.default-encoding</span>によるエンコーディング

<span class="ioprop">input.default-encoding</span>はデフォルトではJISAutoDetect(ISO-2022-JP,
Shift_JIS, EUC_JPの自動判別)ではなく、JISUniAutoDetect(ISO-2022-JP, UTF-8,
Windows-31J, EUC_JP_Solarisの自動判別) という独自のエンコーディング名です。

#### <a id="style-xml-meta">文書情報</a>

HTMLのmeta要素から取得した情報を、PDFの文書情報として使用します。 文書情報はHTMLの
<tt>&lt;meta name="名前" content="値"&gt;</tt>
要素によって設定することができます。 ただし、TITLEはHTMLのtitle要素の内容も使われます。

**meta要素による文書情報の設定**

| 名前 | PDFの属性名 | 説明 |
| --- | --- | --- |
| TITLE | Title/タイトル | 文書の表題。 |
| DESCRIPTION<br />SUBJECT | Subject/サブタイトル | 文書に内容についての簡潔な説明。 |
| KEYWORDS | Keywords/キーワード | スペースまたはカンマ区切りで羅列した、文書の内容に関連するキーワード。 |
| AUTHOR | Author/作成者 | 文書の作成者。 |
| PRODUCER | Producer/PDF変換 | PDFを生成したプログラム。 省略した場合は変換したプログラムの名前とバージョンが入ります。 |
| GENERATOR<br />CREATOR | Creator/アプリケーション | HTML文書を生成したプログラム、エディタ、オーサリングツールなど。 |

なお、meta要素のname属性は大文字小文字を区別しません。
<tt>&lt;meta name="AUTHOR" content="作者名"&gt;</tt>
としても、<br />
<tt>&lt;meta name="Author" content="作者名"&gt;</tt>
としても、<br /> PDF文書に作者名が設定されます。

HTMLのmeta, title要素による文書情報の設定は、<span class="ioprop">output.use-meta-info</span>に"false"を設定することにより無効化することができます <span class="since">3.1.8</span>。その場合、次の方法が唯一の文書情報の設定手段となります。

文書情報は、入出力プロパティによっても設定することができます <span class="since">2.0.3</span>。

- <span class="ioprop">output.meta.<i>n</i>.name</span>
- <span class="ioprop">output.meta.<i>n</i>.value</span>

により、名前と値を設定することができます。 <i>n</i>は0から始まる連番で、複数の文書情報を設定することができます。
この設定は、前記のmeta要素で上書きされます。

#### <a id="style-xml-xslt">XSLTスタイルシートの適用</a>

XSLTスタイルシートはCSS同様にxml-stylesheet処理命令により適用されます。
xml-stylesheet処理命令についての詳細はCSSの[xml-stylesheet処理命令の節](#style-xml-stylesheet)を参照してください。

CSS同様に、デフォルトのXSLTスタイルシートを設定することができます。 <span class="ioprop">input.xslt.default-stylesheet</span>により指定されたスタイルシートが最初に適用されます。

<span class="ioprop">input.xslt.default-stylesheet</span>またはxml-stylesheet処理命令によって、
ドキュメントに複数のXSLTスタイルシートが指定されているとき、実際に適用するのは最初に指定されたスタイルシートです。
他のスタイルシートは無視されます。

### <a id="style-markdown">Markdown<span class="since">4.0.0</span></a>

Markdownで書いた文書をそのまま変換できます。
MIME型に`text/markdown`(または`text/x-markdown`)を指定してください。

```java
CTISessionHelper.transcodeFile(session, new File("readme.md"), "text/markdown", null);
```

内部では**MarkdownをHTMLへ変換してから**通常のHTMLとして組みます。
したがって、スタイルの当て方・セレクタ・ページ処理はHTMLの場合とまったく同じです。
[デフォルトのスタイルシート](#style-css-default-stylesheet)や
`jp.cssj.stylesheet`処理命令でCSSを適用してください。

#### 既定のスタイル

Markdownには表示のされ方の標準がないため、
**A4のレポートとして印刷する**ことを想定した既定スタイルを与えます。
本文は明朝(serif)の両端揃え、見出しは太字と余白のみで階層を示し、
表は上下の太罫とヘッダ下の細罫だけ(縦罫なし)、各ページ下端の中央に
ページ番号が入ります。色や囲み罫は使わないので、白黒印刷でもそのまま
使えます。

この既定スタイルは文書内に直接書いたHTMLの`<style>`要素で個別に
上書きできます。用紙サイズや余白を変えるには`@page`規則を書いてください。
文書内の`<style>`要素は、生成されるHTMLでは`<head>`内(既定スタイルの後)へ
移動されるため、`body`や`html`自身に効くプロパティ
(縦書きにするための`writing-mode: vertical-rl`など)も確実に適用されます。

デザイン全体を自分で設計する場合は、
<span class="ioprop">input.default-stylesheet</span>で自前のスタイルシートを
指定してください。このとき既定スタイルは**まったく適用されません**。
既定スタイルを下敷きにしたまま部分的に上書きしていくより、
意図しない規則(段落の行送りやページ番号など)が紛れ込まず確実です。

#### 対応する記法

[CommonMark](https://commonmark.org/)に準拠し、
これに**表**(GitHub Flavored Markdownのtables拡張)を加えたものに対応します。

CommonMarkはHTMLの直接記述を許すので、Markdownの中にHTMLを混ぜられます。
Markdownでは表現できない指定(`class`属性を付ける、`<div>`で囲む、
拡張CSSを使うなど)は、HTMLを直接書いてください。

<div class="note">

見出し・段落・リスト・表・コードブロック・リンク・画像・引用など、
CommonMarkの範囲は一通り使えます。
脚注・定義リスト・自動見出しIDといった、CommonMarkに無い拡張には対応していません。

</div>

<div class="note">

CommonMarkの規格どおりでは、`**強調**`の閉じ記号の直前が全角句読点
(「精密に**：**」のようにコロン・句点などで終わる強調)だと強調として
解釈されず、`**`がそのまま出力されます(GitHub等でも同様に壊れる、
日本語Markdownの既知問題です)。このパターンを強調として
解釈します。規格からの意図的な拡張です。

</div>

#### <a id="style-markdown-html">生成されるHTML<span class="since">4.0.0</span></a>

ユーザーCSSのセレクタを書くために、各記法がどのHTMLになるかを示します。
文書全体は次の形に包まれます(既定スタイルはhead内の`style`要素として
入るため、後から読まれるCSSで上書きできます)。

```html
<html>
  <head><meta charset="UTF-8"/><style>…既定スタイル…</style></head>
  <body>…変換されたMarkdown…</body>
</html>
```

| 記法 | 生成されるHTML |
| --- | --- |
| `# 見出し` 〜 `###### 見出し` | `h1`〜`h6`(`id`属性は付きません) |
| 段落 | `p` |
| `**強調**` | `strong` |
| `*斜体*` | `em` |
| `` `コード` `` | `code` |
| フェンス付きコードブロック | `pre`の中に`code`。言語指定(フェンス開始に続けて<tt>java</tt>等)は`code`の`class="language-java"`になります |
| 字下げコードブロック | `pre`の中に`code`(クラスなし) |
| `> 引用` | `blockquote`(中に`p`) |
| 箇条書きリスト | `ul`と`li` |
| 番号付きリスト | `ol`と`li`。開始番号が1以外なら`ol`に`start`属性 |
| `[リンク](URL "題名")` | `a href="URL" title="題名"` |
| `![代替文字](URL "題名")` | `img src="URL" alt="代替文字" title="題名"` |
| 表(tables拡張) | `table`の中に`thead`/`tbody`、`tr`、`th`/`td`。`:---:`等の列揃えは各セルの`align`属性(`left`/`center`/`right`)になります |
| `---`(区切り線) | `hr` |
| 行末の空白2つ、または行末の`\` | `br` |
| `狼狽《ろうばい》`(青空文庫式ルビ)<span class="since">4.0.0</span> | `ruby`と`rt` |
| `｜生前退位《せいぜんたいい》`(親文字を明示) | `ruby`と`rt` |

**ルビ**(振り仮名)は青空文庫と同じ書き方ができます<span class="since">4.0.0</span>。
`《`と`》`の直前が漢字の並びであれば、そこが親文字になります
(`狼狽《ろうばい》`)。漢字以外や範囲を明示したいときは`｜`(全角の縦棒)を
親文字の先頭に置きます(`｜1970年《いちきゅうななまるねん》`)。
読みが仮名でない`《...》`は書名などの括弧と見なしてそのまま出力します
——`｜`を書けば仮名以外の読みも付けられます。
ルビの見た目は`ruby > rt { font-size: 0.5em; }`のようにCSSで調整します。

表の列揃えを上書きするときは属性セレクタが使えます
(例: `td[align="right"] { color: red; }`)。
コードブロックの言語ごとの装飾は`pre > code.language-java`のように
選択できます。

#### <a id="style-markdown-css">スタイルの変更<span class="since">4.0.0</span></a>

既定スタイルを変えるには、Markdown文書の中に生HTMLで`style`要素を
書くのが簡単です。既定スタイルより後に読まれるため、同じ詳細度なら
文書内の指定が勝ちます。場所はどこでも構いませんが、先頭に置くのが
読みやすいでしょう。

```markdown
<style>
@page { size: B5; margin: 15mm; }        /* 用紙と余白 */
body { font-family: sans-serif; }        /* 本文をゴシックへ */
h1 { border-bottom: 1pt solid #000000; } /* 見出しに下線 */
table { font-size: 0.9em; }
</style>

# ここから通常のMarkdown
```

文書に手を入れずに変えるには、
[デフォルトのスタイルシート](#style-css-default-stylesheet)
(<span class="ioprop">input.default-stylesheet</span>入出力プロパティ)で
全文書共通のCSSを適用できます。ただしこのスタイルシートは
**Markdownの既定スタイルより先に**適用されるため、同じ指定同士では
既定スタイルが勝ちます。既定スタイルと同じプロパティを上書きするときは
`!important`を付けてください。

```css
/* デフォルトのスタイルシートからMarkdown既定を上書きする例 */
@page { size: B5 !important; margin: 15mm !important; }
body { font-family: sans-serif !important; }
```

### <a id="style-image">画像</a>

#### サポートする画像

JPEG, GIF, PNG, WebP, SVG形式の画像を標準でサポートしています。 各画像は通常のブラウザ同様にimg,
object, embed要素によってドキュメント中に含めることができます。 CSSの<span class="cssprop">background-image</span>プロパティ、
あるいは<span class="cssprop">content</span>プロパティによっても画像の表示が可能です。

また、画像を直接読み込んでPDFに変換することができます。 この場合、全体が画像となっている１ページだけのPDFが生成されます。

置換要素(img等)の内容をボックスへ収める方法は、CSSの
<span class="cssprop">object-fit</span>プロパティ<span class="since">4.0.0</span>
(fill / contain / cover / none / scale-down)と
<span class="cssprop">object-position</span>プロパティ<span class="since">4.0.0</span>で
ブラウザ同様に制御できます。ボックスからはみ出す部分はクリップされます。

#### <a id="style-image-jai">読み書きできる画像形式</a>

そのまま使えるのは次の形式です。

| 形式 | 読み込み | 出力 |
| --- | --- | --- |
| PNG | ○ | ○ |
| JPEG | ○ | ○ |
| GIF | ○ | ○ |
| WebP<span class="since">4.0.0</span> | ○ | — |
| BMP | ○ | ○ |
| TIFF | ○ | ○ |
| WBMP | ○ | ○ |
| SVG | ○ | — |

出力側の詳細は<a href="#style-output-image" class="pageref">画像形式での出力</a>を
参照してください。

<div class="note">

<strong>JPEG 2000は同梱していません。</strong>
入力に使うにはJava Image I/OのJPEG 2000リーダーを、
<a href="#style-pdf-image" class="pageref">JPEG 2000での圧縮</a>には
JDeliを、それぞれ別途クラスパスに追加する必要があります。

</div>

画像の読み込みはJava Image I/Oを通しているため、
対応するリーダーをクラスパスに追加すれば、上の表以外の形式も扱えるようになります。
追加した形式の扱いは、下の「その他の画像」に従います。

#### <a id="style-image-raster">ラスター(ビットマップ/ピクセルマップ)画像</a>

##### GIF / PNG画像

GIFおよびPNG画像の透明化効果はPDF内でも有効です。 ただし、PDF 1.3以前のバージョンのPDFを出力する場合、
PNG画像の半透明化効果が正確に表現されず、透明・不透明だけとなります。

アニメーションGIFの場合、最初のコマだけが使われ、アニメーションしません。

##### <a id="style-image-jpeg">JPEG画像</a>

JPEG画像は処理を加えずに、そのままPDFに含めることができます。 入出力プロパティ<span class="ioprop">output.pdf.jpeg-image</span>
にto-flateを設定することで画像を展開してからPDFに含むこともできますが、PDFのサイズは大きくなります。

CMYKやYCCKで記録されたJPEG(印刷用の画像に多い形式)も読み込めます。

##### その他の画像

Java Image I/Oのリーダーを追加して他の画像形式を使えるようにした場合、
それらの扱いはGIF / PNG画像に準じます。
半透明効果をサポートする画像形式の場合はPDF内でも再現され、アニメーションする画像では最初のコマだけが使われます。

##### 画像の解像度

レイアウトされるときの、埋め込まれた画像の大きさは <span class="ioprop">output.resolution</span>
によります。デフォルトでは96dpiです。 つまりデフォルトでは、例えばimgタグでheightが100と設定された場合、
あるいはimgタグでサイズが指定されずに、元の画像の大きさが100の場合、 実際のレイアウト結果では75ptの高さになります。

#### <a id="style-image-svg">SVG画像</a>

##### SVG画像ファイルの参照

SVG画像ファイル(.svg)またはGZIPで圧縮されたSVG画像ファイル(.svgz)をサポートしています。
ただし、**PDF出力の場合、SVG埋め込みフォントと、
透明度を含むグラデーション塗り(linearGradient, radialGradientでstop-opacityの使用)
には対応していません。**

一般的なブラウザではobject要素によりSVG画像がサポートされていますので、同様の方法を推奨します。

```html
<object data="image.svg" type="image/svg+xml">
SVGをサポートしないブラウザで表示されるテキスト。
</object>
```

SVG画像は他の画像と同様に扱うことができます。 img要素を利用することができ、<span class="cssprop">background-image</span>プロパティにより背景に設定することもできます。

##### <a id="style-image-inline-svg">インラインSVG</a>

SVGを別ファイルに分けずに、文章中に直接記述することができます。
グラフ等、動的に変化する画像をドキュメントに含める場合に適しています。
インラインSVGは、ドキュメント中でhttp://www.w3.org/2000/svgと名前空間宣言したSVGを記述するだけです。

```html
<html>
<head>
  <title>インラインSVGを含むドキュメント</title>
</head>
<body>
<p>すぐ下に○が表示されます。</p>
<svg:svg xmlns:svg="http://www.w3.org/2000/svg"
         xmlns:xlink="http://www.w3.org/1999/xlink"
         preserveAspectRatio="none"
         width="100" height="100"
         viewBox="0 0 100 100"
         xml:space="preserve">
	<svg:g>
		<svg:circle cx="50" cy="50" r="45" stroke="Blue" fill="White" stroke-width="10"/>
	</svg:g>
</svg:svg>
</body>
</html>
```

HTML文書のスタイルシートに書かれたSVG向けの宣言
(fill・stroke・stop-color・opacityなどのプレゼンテーションプロパティ)は、
インラインSVGの中の要素にも適用されます<span class="since">4.0.0</span>。
CSSクラスでアイコンの色を制御する一般的な書き方がそのまま組版され、
値のvar()参照(カスタムプロパティ)はそのSVGの位置で解決されます。
fill: currentColor のための文字色(color)も、HTML側のカスケードで
決まった計算値がSVGルートへ引き継がれます。
ただしセレクタのうちインラインSVGへ持ち込まれるのはタグ名・クラス・id・
子孫・子(&gt;)の組み合わせだけで、SVGの外側にあるHTML要素を含む
セレクタや擬似クラスは適用されません。fill・stroke・stop-colorの
rgba()等のアルファ成分はfill-opacity等の対応プロパティへ変換されます。

#### <a id="style-image-broken">画像を読み込めない場合</a>

画像ファイルが存在しない、データの破損、サポートしない画像形式といった原因で
画像を読み込めない場合は、通常はalt属性で指定された代替テキストが表示されます。 入出力プロパティ<span class="ioprop">output.broken-image</span>
の設定により、画像の形に×印を表示する(cross)か、空白を空ける(hidden)ことができます。

### <a id="style-epub">EPUB電子書籍</a>

EPUB 2.0またはEPUB 3.0ファイルを変換することができます<span class="since">3.1.0</span>。

EPUBファイルは、コンテンツのMIME方が application/epub+zip であるか、拡張子が.epubであることで判別されます。
