## <a id="appx-glossary">用語集</a>

この説明書で使っている言葉です。印刷やDTPの用語と、
固有の言葉が混ざっているので、まとめて説明します。

### <a id="appx-glossary-page">紙面まわり</a>

<dl>

<dt><a id="glossary-nombre">ノンブル</a></dt>
<dd>
	紙面に印刷されるページ番号のことです。フランス語の <i>nombre</i>(数)から来ています。
	CSSでは `counter(page)` で取り出し、
	<a href="#style-page-margin-boxes" class="pageref">マージンボックス</a>に置きます。
	→ <a href="#style-nombre" class="pageref">ノンブルの付け方</a>
</dd>

<dt><a id="glossary-running-head">柱(はしら)</a></dt>
<dd>
	各ページの上下に繰り返し出る、章や節の見出しです。
	英語では running head / running header と呼びます。
	その時点で有効な見出しをページごとに拾う必要があるため、
	CSSでは `string-set` と `string()` を組み合わせて作ります。
	→ <a href="#style-running-heading" class="pageref">柱(ランニングヘッダー)</a>
</dd>

<dt><a id="glossary-margin-box">マージンボックス</a></dt>
<dd>
	版面の外側、紙の余白の部分に置ける16個の小さな箱です。
	`@top-center` のように `@page` の中で指定します。
	ノンブルや柱はここに置きます。
	→ <a href="#style-page-margin-boxes" class="pageref">ページのマージンボックス</a>
</dd>

<dt><a id="glossary-hanmen">版面(はんづら)</a></dt>
<dd>
	紙のうち、本文が入る長方形の領域です。
	紙の大きさから余白(`margin`)を除いた部分にあたります。
</dd>

<dt><a id="glossary-recto-verso">ノド・小口 / 見開き / recto・verso</a></dt>
<dd>
	綴じ側を<b>ノド</b>、その反対側を<b>小口</b>と呼びます。
	両面印刷では左右のページで余白の付け方を変える必要があるため、
	`@page :left` / `@page :right` で分けて指定します。
	rectoが奇数ページ(右側)、versoが偶数ページ(左側)です。
</dd>

</dl>

### <a id="appx-glossary-print">印刷・製本まわり</a>

<dl>

<dt><a id="glossary-imposition">面付け(めんつけ)</a></dt>
<dd>
	1枚の大きな紙に複数のページを並べることです。
	折って断裁すると正しい順序の冊子になるよう並べます。
	→ <a href="#style-imposition" class="pageref">面付け</a>
</dd>

<dt><a id="glossary-tombo">トンボ(トリムマーク)</a></dt>
<dd>
	断裁する位置を示すために紙の四隅と辺の中央に付ける印です。
	印刷所はこの印に合わせて紙を切ります。
</dd>

<dt><a id="glossary-bleed">断ち代(たちしろ) / 塗り足し</a></dt>
<dd>
	断裁の位置より少し外側まで色や画像を伸ばしておく領域です。
	断裁が数ミリずれても、紙の端に白いすき間ができません。
	CSSでは `bleed` で指定します。
</dd>

<dt><a id="glossary-cut-stack">カット&amp;スタック</a></dt>
<dd>
	同じ位置に連番のページが積み重なるように面付けする方法です。
	断裁したあと重ねるだけで順番が揃います。大量の連番印刷で使います。
</dd>

</dl>

### <a id="appx-glossary-process">変換のしくみ</a>

<dl>

<dt><a id="glossary-pass">パス</a></dt>
<dd>
	文書を最初から最後まで1回読み通すことです。
	総ページ数や目次のページ番号は、1回読んだだけでは決まらないため、
	2回以上読む必要があります。
	→ <a href="#style-multipass" class="pageref">2パス以上の変換処理</a>
</dd>

<dt><a id="glossary-stream">ストリーム処理</a></dt>
<dd>
	文書全体をメモリに載せず、流れてきた分から順に組んで出力する方式です。
	この方式なので、必要なメモリが文書の大きさで増えません。
	→ <a href="#admin-perf-memory" class="pageref">メモリは文書の大きさで決まりません</a>
</dd>

<dt><a id="glossary-ioprop">入出力プロパティ</a></dt>
<dd>
	変換のしかたを指定する名前と値の組です。
	`output.pdf.version` のように、`input.` / `output.` / `processing.` で始まります。
	CSSが<b>紙面の見た目</b>を決めるのに対し、
	入出力プロパティは<b>変換そのもの</b>を決めます。
	→ <a href="#appx-ioprops" class="pageref">入出力プロパティ一覧</a>
</dd>

<dt><a id="glossary-driver">ドライバ</a></dt>
<dd>
	各プログラミング言語からを呼ぶためのライブラリです。
	Java / Perl / PHP / .NET / Ruby / Python / Node.js 向けがあります。
	→ <b>プログラムインターフェース</b>(サーバー製品の説明書)
</dd>

<dt><a id="glossary-ctip">CTIP</a></dt>
<dd>
	ドライバとサーバーの間でやりとりする通信手順です。
	現在使うのはCTIP 2.0です。
	→ <b>CTIP 2.0 インターフェース</b>(サーバー製品の説明書)
</dd>

<dt><a id="glossary-profile">プロファイル</a></dt>
<dd>
	フォントの設定や既定の入出力プロパティをまとめた設定一式です。
	`conf/profiles/` に置きます。用途ごとに切り替えられます。
	<b>PDFのプロファイル(PDF/Xなど)とは別のものです。</b>
</dd>

<dt><a id="glossary-user-agent">ユーザーエージェント</a></dt>
<dd>
	組み上がった紙面を、実際の出力形式(PDF、画像、SVG)へ描く部分です。
	<span class="ioprop">output.type</span> によって選ばれます。
</dd>

</dl>

### <a id="appx-glossary-typography">組版まわり</a>

<dl>

<dt><a id="glossary-kinsoku">禁則処理(きんそくしょり)</a></dt>
<dd>
	行頭に句読点や閉じ括弧が来ない、行末に開き括弧が来ない、
	といった日本語の組版規則です。
	→ <a href="#style-line-breaking" class="pageref">行の分割</a>
</dd>

<dt><a id="glossary-ruby">ルビ</a></dt>
<dd>
	漢字などに付ける読み仮名です。HTMLの `ruby` 要素で書きます。
	→ <a href="#style-xml-ruby" class="pageref">ルビ</a>
</dd>

<dt><a id="glossary-kenten">圏点(けんてん)</a></dt>
<dd>
	強調のために文字の脇に打つ点です。傍点ともいいます。
	CSSの `text-emphasis` で指定します。
</dd>

<dt><a id="glossary-tatechuyoko">縦中横(たてちゅうよこ)</a></dt>
<dd>
	縦書きの中で、数字などを横に倒さず並べて組むことです。
	CSSの `text-combine-upright` で指定します。
</dd>

<dt><a id="glossary-orphans-widows">オーファン / ウィドウ</a></dt>
<dd>
	段落がページをまたぐとき、ページの最後や最初に1行だけ残ってしまうものです。
	CSSの `orphans` / `widows` で、最低何行残すかを指定します。
</dd>

<dt><a id="glossary-kumimoji">組み文字</a></dt>
<dd>
	複数の文字を1文字分の枠に詰めて組むことです。
	「株式会社」を1マスに収めるといった使い方をします。
</dd>

</dl>
