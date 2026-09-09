## 拡張機能

この組版エンジンには独自の処理命令、CSSプロパティ、CSS関数、XML要素、XML属性があります。 また、CSS
2.1ではサポートされず、CSS 3で追加されるプロパティを先行して実装したものがあります。

### 処理命令の拡張

**処理命令一覧**

| 名前 | バージョン | 説明 |
| --- | --- | --- |
| <a id="appx-pi-jp.cssj.base-uri"></a>jp.cssj.base-uri | 1.1.0 | これはHTMLのbase要素の代替機能を提供するものです。 文書中の相対URIを解決する基準を指定します。<br /> ストリームで文書を渡していて基準が決まらない場合や、基準を文書の位置と別にしたい場合に使います。 |
| jp.cssj.default-encoding | 1.1.0 | これはHTMLの<![CDATA[<meta http-equiv="Content-Type" content="text/html; charset=...">]]>要素の代替機能を提供するものです。 エンコーディング名を値に使用します。 |
| jp.cssj.default-style-type | 1.1.0 | これはHTMLの<![CDATA[<meta name="content-style-type" content="...">]]>要素の代替機能を提供するものです。 MIME型を値に使用します。 |
| jp.cssj.document-info | 1.1.0 | これはHTMLの<![CDATA[<meta name="..." content="...">]]>要素の代替機能を提供するものです。 name,content属性に対して、name,value擬似属性が用意されています。 |
| <a id="appx-pi-jp.cssj.property"></a>jp.cssj.property | 2.0.0 | <span class="ioprop">input.property-pi</span>がtrueのときだけ利用可能です。<br /> 入出力プロパティをドキュメント中で再設定します。 name,value擬似属性が用意されています。 valueを省略すると、デフォルト値に設定されます。 |
| <a id="appx-pi-jp.cssj.stylesheet"></a>jp.cssj.stylesheet | 1.1.0 | これはHTMLのstyle要素の代替機能を提供するものです。 type,media属性に対して、同名の擬似属性が用意されています。 スタイルシートは'[]'で囲って記述します。 |

### <a id="appx-cssprop-ext">CSSプロパティの拡張</a>

#### CSSプロパティ

<div class="note">

**標準の名前があるものは、標準の名前で指定してください。**
下の表で複数の名前が並んでいる場合、**先頭が推奨する名前**です。
`-cssj-`で始まる名前は、標準の名前が定まる前から提供しているもので、
互換のために受け付け続けます。

`-cssj-`でしか指定できないものもあります(圏点・袋文字・縦中横・ルビ・
禁則文字・フォントの種類など)。これらは対応する標準の名前が無いか、
あっても実装が受け付けません。

</div>


**CSSプロパティ一覧**

| 名前 | バージョン | 継承 | デフォルト値 | 適用対象 | 説明 |
| --- | --- | --- | --- | --- | --- |
| <a id="appx-cssprop--cssj-font-policy"></a>-cssj-font-policy | 2.0.0 |  | cid-keyed | すべての要素 | 独自プロパティです<br /> 使用するフォントの種類を指定します。 指定できる値はcid-keyed, cid-identity, embedded, outlines<span class="since">3.1.1</span>のいずれかです。 詳細は<b>フォントの設定</b>(pdfg2d の説明書)の章を参照してください。 <br /> 旧い名前のgeneric(=cid-keyed)、external(=cid-identity)、embed(=embedded)も受け付けます。 <br /> 2.0.1以降では、複数の値を指定可能になりました。 例えば"embedded cid-keyed"という指定をすると、埋め込みフォントが見つからない場合はCID Keyedフォントを使用します。 <br /> コアフォントは常に使われます。ただし、-coreという指定をすると除外されます<span class="since">3.0.0</span>。 <br /> <a href="#style-pdf-profiles">PDF/AまたはPDF/X</a>を出力する場合、この設定は無視され、常に埋め込みフォントだけが使われます。 |
| background-size<br />-cssj-background-size | 2.0.8 |  | auto | すべての要素 | CSS Backgrounds and Borders Module Level 3 に沿った実装です。<br /> 背景画像のサイズを指定します。1つめの値は画像の幅で、2つめの値は高さです。 %指定は、要素の幅または高さに対する割合です。backgroundでも指定可能です<span class="since">3.2.16</span>。 |
| text-align-last<br />-cssj-text-align-last<br />-epub-text-align-last | 2.0.8 |  | start | すべての要素 | CSS Text Module Level 3 に沿った実装です。<br /> 段落末のテキストの合わせ方です。 値はstart, end, left, right, center, justify のいずれかです。<br /> プロパティ名<span class="cssprop">-epub-text-align-last</span>も使えます。 |
| writing-mode<br />-cssj-writing-mode<br />-epub-writing-mode | 3.0.0 |  | horizontal-tb | テーブル行グループ、テーブルカラムグループ、テーブル行、テーブルカラム以外の要素 | CSS Writing Modes Level 3 に沿った実装です。<br /> 縦書き、横書きを設定します。 値はhorizontal-tb(横書き), vertical-rl(縦書き), vertical-lr(縦書き・左から右)<span class="since">4.0.0</span>のいずれかです。 Internet Explorer/SVG 1.1との互換性のため、lr, lr-tb, rl, tb, tb-rlも値として設定可能です。<br /> プロパティ名<span class="cssprop">-epub-writing-mode</span>も使えます。 |
| container-type | 4.0.0 |  | normal | すべての要素 | CSS Containment Module Level 3 のクエリコンテナ種別です。normal, inline-size, sizeを受理しますが、実際のコンテナクエリはinline-sizeだけに対応します。sizeはクエリコンテナとして動作しません。 |
| container-name | 4.0.0 |  | none | すべての要素 | クエリコンテナへ1個以上の名前を付けます。@containerの名前付き条件から参照できます。 |
| container | 4.0.0 |  |  | すべての要素 | <span class="cssprop">container-name</span>と<span class="cssprop">container-type</span>のショートハンドです。`名前 / inline-size`の形で指定します。 |
| column-width<br />-cssj-column-width | 3.0.0 |  | auto | 置換不可能なブロックレベル要素（テーブルを除く）、テーブルセル、インラインブロック | CSS Multi-column Layout Module Level 1 に沿った実装です。<br /> 段組の幅を設定します。 値はautoまたは長さです。 |
| columns<br />-cssj-columns | 3.0.0 |  |  | 置換不可能なブロックレベル要素（テーブルを除く）、テーブルセル、インラインブロック | CSS Multi-column Layout Module Level 1 に沿った実装です。<br /> <span class="cssprop">column-width</span>, <span class="cssprop">column-count</span> の一方の値か、あるいは両方の値をまとめて設定することができます。 |
| column-count<br />-cssj-column-count<br />oeb-column-number | 3.0.0 |  | auto | 置換不可能なブロックレベル要素（テーブルを除く）、テーブルセル、インラインブロック | CSS Multi-column Layout Module Level 1 に沿った実装です。<br /> 段組の数を設定します。 値はautoまたはカラムの数です。<br /> プロパティ名<span class="cssprop">oeb-column-number</span>も使えます。 |
| column-gap<br />-cssj-column-gap | 3.0.0 |  | normal | 段組された要素 | CSS Multi-column Layout Module Level 1 に沿った実装です。<br /> 段組の間の幅を設定します。 値はnormalまたは長さです。 |
| column-rule-color<br />-cssj-column-rule-color | 3.0.0 |  | 色 | 段組された要素 | CSS Multi-column Layout Module Level 1 に沿った実装です。<br /> 段組の間に入る罫線の色を設定します。 |
| column-rule-style<br />-cssj-column-rule-style | 3.0.0 |  | none | 段組された要素 | CSS Multi-column Layout Module Level 1 に沿った実装です。<br /> 段組の間に入る罫線のスタイルを設定します。 値は<span class="cssprop">border-*-style</span>の値と同じです。 |
| column-rule-width<br />-cssj-column-rule-width | 3.0.0 |  | medium | 段組された要素 | CSS Multi-column Layout Module Level 1 に沿った実装です。<br /> 段組の間に入る罫線の幅を設定します。 値は<span class="cssprop">border-*-width</span>の値と同じです。 |
| column-rule<br />-cssj-column-rule | 3.0.0 |  |  | 段組された要素 | CSS Multi-column Layout Module Level 1 に沿った実装です。<br /> 段組の間に入る罫線の色、スタイル、幅をまとめて設定します。 値は<span class="cssprop">border-*</span>の値と同じです。 |
| column-span<br />-cssj-column-span | 3.0.0 |  | 1 | 静的な、浮動体以外の要素 | CSS Multi-column Layout Module Level 1 に沿った実装です。<br /> 段落のブチヌキを設定します。 値は1またはallです。 |
| -cssj-text-combine<br />-epub-text-combine<br />text-combine-upright | 3.0.4 |  | none | すべての要素 | 縦中横を実現するためのものです。<br /> <tt>horizontal</tt>は横に組んだ内容を<strong>自然な幅のまま</strong>置きます(<span class="cssdecl">writing-mode: horizontal-tb;</span>を指定するのと変わりません)。桁数が多いと行からはみ出します。<br /> 標準名の<span class="cssprop">text-combine-upright</span>で<tt>all</tt>を指定すると、内容を<strong>1文字分(1em)の幅に収め</strong>、収まらない場合は水平方向に縮小します<span class="since">4.0.0</span>。<tt>digits</tt>には対応していません。 |
| -cssj-text-emphasis<br />-epub-text-emphasis<br />text-emphasis<br />text-emphasis-style<br />text-emphasis-color | 3.0.4<br />標準名は4.0.0 |  | none | すべての要素 | CSS Text Module Level 3 に沿った実装です。標準名と従来の-cssj-名は同じ実装を使用します。<br /> <span class="cssprop">text-emphasis-style</span>, <span class="cssprop">text-emphasis-color</span> をまとめて指定します。 詳細は<a href="#style-text-emphasis" class="pageref">圏点</a>を参照してください。 |
| src | 3.0.0 |  |  | @font-faceルール | CSS Fonts Module Level 3 に沿った実装です。<br /> フォントの位置を示します。 詳細は<a href="#style-webfont" class="pageref">WebFont</a>を参照してください。 |
| unicode-range | 3.0.0 |  | U+0-10FFFF | @font-faceルール | CSS Fonts Module Level 3 に沿った実装です。<br /> フォントのコード範囲です。 詳細は<a href="#style-webfont" class="pageref">WebFont</a>を参照してください。 |
| word-wrap | 3.0.0 |  | normal | すべての要素 | CSS Text Module Level 3 に沿った実装です。<br /> 英単語の中での折り返しを許可するかどうかの設定です。 値はnormal, break-wordのいずれかです。 |
| word-break | 3.2.2 |  | normal | すべての要素 | CSS Text Module Level 3 に沿った実装です。<br /> 禁則処理の設定です。 値はnormal, break-all, keep-allのいずれかです。 |
| page | 4.0.0 |  | auto | ブロックレベル要素 | CSS Paged Media Module Level 3 に沿った実装です。<br /> 名前付きページを適用します。詳細は<a href="#style-named-pages">名前付きページ</a>を参照してください。 |
| size | 4.0.0 |  | auto | @pageルール | CSS Paged Media Module Level 3 に沿った実装です。<br /> ページの寸法を指定します。入出力プロパティ<span class="ioprop">output.page-width</span>・<span class="ioprop">output.page-height</span>より優先されます。 |
| text-autospace | 4.0.0 | する | normal | すべての要素 | CSS Text Module Level 4 に沿った実装です。<br /> 和文と欧文・数字の境界のアキを制御します。値はnormal, no-autospace, またはideograph-alpha/ideograph-numericの組み合わせです。<strong>4.0.0から既定値がnormal(アキを入れる)になりました。</strong>従来の見た目に戻すにはno-autospaceを指定してください。 |
| text-spacing-trim | 4.0.0 | する | normal | すべての要素 | CSS Text Module Level 4 に沿った実装です。<br /> 連続する約物(括弧・句読点・中点類)の間を、スタイルランの境界をまたいで詰めます。中点類の後ろは均等割付でも伸ばしません。値はnormal, space-allのいずれかです。 |
| hanging-punctuation | 4.0.0 | する | none | すべての要素 | CSS Text Module Level 3 のサブセットです。<br /> noneまたはallow-endを指定します。allow-endでは通常位置に収まらない行末句読点を追込み・ぶら下げします。first, last, force-endは未対応です。 |
| initial-letter | 4.0.0 |  | normal | ::first-letter | CSS Inline Layout Module Level 3 のドロップキャップです。`initial-letter: 行数`または`行数 沈み行数`を指定します。drop/raiseも受理します。実フォントのcap heightを使い、floatによる回り込みへ展開します。 |
| font-feature-settings | 4.0.0 | する | normal | すべての要素 | CSS Fonts Module Level 3 に沿った実装です。<br /> OpenTypeフォントの字形置換・詰め機能(palt, jp78, pwid等)を直接指定します。 |
| font-variation-settings | 4.0.0 |  | normal | @font-faceルール | CSS Fonts Module Level 4 のサブセットです。<br /> `@font-face`のディスクリプタとして、可変フォントを指定軸座標の静的インスタンスへ変換します。要素ごとのプロパティとしては適用されません。要素ごとの太さは<span class="cssprop">font-weight</span>を使ってください。 |
| font-variant-east-asian | 4.0.0 | する | normal | すべての要素 | CSS Fonts Module Level 3 に沿った実装です。<br /> 和文の異体字形(jis78, jis83, full-width, proportional-width等)を指定します。 |
| grid-template-columns<br />grid-template-rows | 4.0.0 |  | none | グリッドコンテナ | CSS Grid Layout Module Level 1 に沿った実装です。<br /> グリッドのトラック(列・行)を定義します。<span class="cssdecl">display: grid;</span>と併用します。 |
| grid-column-start<br />grid-column-end<br />grid-row-start<br />grid-row-end | 4.0.0 |  | auto | グリッドアイテム | CSS Grid Layout Module Level 1 に沿った実装です。<br /> グリッドアイテムの配置位置を指定します。 |
| grid-column<br />grid-row | 4.0.0 |  |  | グリッドアイテム | CSS Grid Layout Module Level 1 に沿った実装です。<br /> 配置の開始/終了をまとめて指定するショートハンドです。 |
| flex-direction<br />flex-wrap<br />flex-flow | 4.0.0 |  | row / nowrap | フレックスコンテナ | CSS Flexible Box Layout Module Level 1 に沿った実装です。<br /> 主軸方向(row / row-reverse / column / column-reverse)と折り返し(nowrap / wrap / wrap-reverse)を指定します。flex-flowはショートハンドです。<span class="cssdecl">display: flex;</span>と併用します。 |
| flex-grow<br />flex-shrink<br />flex-basis<br />flex | 4.0.0 |  | 0 / 1 / auto | フレックスアイテム | CSS Flexible Box Layout Module Level 1 に沿った実装です。<br /> アイテムの伸長・収縮係数と基準寸法を指定します。flexショートハンドの省略値は仕様どおりgrow=1・shrink=1・basis=0です(各プロパティの初期値と異なります)。 |
| order | 4.0.0 |  | 0 | フレックスアイテム | CSS Flexible Box Layout Module Level 1 に沿った実装です。<br /> アイテムの視覚上の並び順を変更します。タグ付きPDFの読み上げ順は文書の記述順のまま維持されます。 |
| row-gap<br />gap | 4.0.0 |  | normal | グリッドコンテナ、フレックスコンテナ、段組された要素 | CSS Box Alignment Module Level 3 に沿った実装です。<br /> トラック間・アイテム間・段間の間隔を指定します。gapはrow-gapとcolumn-gapのショートハンドです。 |
| justify-items<br />align-items<br />justify-self<br />align-self<br />justify-content<br />align-content | 4.0.0 |  |  | グリッド/フレックスのコンテナとアイテム。align-contentは通常のブロックコンテナおよび表セルにも適用 | CSS Box Alignment Module Level 3 に沿った実装です。<br /> グリッド・フレックスコンテナ内での配置・整列を指定します。通常のブロックコンテナおよび表セルでは、align-contentのstart / center / endと、内容が一つの整列対象になる場合のspace-* / stretchのフォールバックをブロック軸へ適用します。フレックスコンテナのjustify-content / align-contentではspace-between / space-around / space-evenlyも使用できます。 |
| column-fill | 4.0.0 |  | balance | 段組された要素 | CSS Multi-column Layout Module Level 1 に沿った実装です。<br /> 段の高さを揃える(balance)か順に埋める(auto)かを指定します。 |
| string-set | 4.0.0 |  | none | すべての要素 | CSS Generated Content for Paged Media Module に沿った実装です。<br /> 柱(ランニングヘッダー)のために、要素の内容を名前付き文字列へ取り込みます。詳細は<a href="#style-running-heading">柱</a>を参照してください。 |
| hyphens | 4.0.0 | する | manual | すべての要素 | CSS Text Module Level 3 に沿った実装です。<br /> 英単語のハイフネーションを制御します。値はnone, manual, autoのいずれかです。 |
| counter-set | 4.0.0 |  | none | すべての要素 | CSS Lists Module Level 3 に沿った実装です。既存の最も内側のカウンタを指定値へ設定し、存在しなければその要素に作ります。値を省略すると0です。 |
| text-wrap-style | 4.0.0 | する | auto | ブロックレベル要素 | CSS Text Module Level 4 に沿った実装です。<br /> 行分割の品質(balance, pretty)を指定します。詳細は<a href="#style-line-breaking">行分割の品質</a>を参照してください。 |
| opacity | 3.0.6 |  | 1 | すべての要素 | CSS Color Module Level 3 に沿った実装です。<br /> 要素の透明度を指定します。 詳細は<a href="#style-opacity" class="pageref">透明化</a>を参照してください。 |
| border-top-left-radius | 3.0.6 |  | 0 | すべての要素 | CSS Backgrounds and Borders Module Level 3 に沿った実装です。<br /> 境界線の左上の半径を指定します。 詳細は<a href="#style-border-radius" class="pageref">角丸境界</a>を参照してください。 |
| border-top-right-radius | 3.0.6 |  | 0 | すべての要素 | CSS Backgrounds and Borders Module Level 3 に沿った実装です。<br /> 境界線の右上の半径を指定します。 詳細は<a href="#style-border-radius" class="pageref">角丸境界</a>を参照してください。 |
| border-bottom-left-radius | 3.0.6 |  | 0 | すべての要素 | CSS Backgrounds and Borders Module Level 3 に沿った実装です。<br /> 境界線の左下の半径を指定します。 詳細は<a href="#style-border-radius" class="pageref">角丸境界</a>を参照してください。 |
| border-bottom-right-radius | 3.0.6 |  | 0 | すべての要素 | CSS Backgrounds and Borders Module Level 3 に沿った実装です。<br /> 境界線の右下の半径を指定します。 詳細は<a href="#style-border-radius" class="pageref">角丸境界</a>を参照してください。 |
| border-radius | 3.0.6 |  |  | すべての要素 | CSS Backgrounds and Borders Module Level 3 に沿った実装です。<br /> 境界線の半径をまとめて指定します。 詳細は<a href="#style-border-radius" class="pageref">角丸境界</a>を参照してください。 |
| transform<span class="since">3.2.16</span><br />-cssj-transform<br />-webkit-transform<br />-moz-transform | 3.0.8 |  | none | ブロックレベル要素 | CSS Transforms Module Level 1 に沿った実装です。<br /> ２次元のアフィン変換を指定します。 ３次元変換には対応していません。 詳細は<a href="#style-transform" class="pageref">回転・縮小・変形</a>を参照してください。 |
| transform-origin<span class="since">3.2.16</span><br />-cssj-transform-origin<br />-webkit-transform-origin<br />-moz-transform-origin | 3.0.8 |  | 50% 50% | ブロックレベル要素 | CSS Transforms Module Level 1 に沿った実装です。<br /> transformプロパティによる変換の基点を指定します。 詳細は<a href="#style-transform" class="pageref">回転・縮小・変形</a>を参照してください。 |
| -cssj-text-fill-color<br />-webkit-text-fill-color | 3.0.8 |  |  | すべての要素 | Chrome/Safariとの互換性のための独自プロパティです<br /> テキストの塗りつぶし色を指定します。 詳細は<a href="#style-text-stroke" class="pageref">袋文字</a>を参照してください。 |
| -cssj-text-stroke-color<br />-webkit-text-stroke-color | 3.0.8 |  |  | すべての要素 | Chrome/Safariとの互換性のための独自プロパティです<br /> テキストの枠線の色を指定します。 詳細は<a href="#style-text-stroke" class="pageref">袋文字</a>を参照してください。 |
| -cssj-text-stroke-width<br />-webkit-text-stroke-width | 3.0.8 |  | 0 | すべての要素 | Chrome/Safariとの互換性のための独自プロパティです<br /> テキストの枠線の太さを指定します。 詳細は<a href="#style-text-stroke" class="pageref">袋文字</a>を参照してください。 |
| -cssj-text-stroke<br />-webkit-text-stroke | 3.0.8 |  |  | すべての要素 | Chrome/Safariとの互換性のための独自プロパティです<br /> テキストの枠線の太さと色を指定します。 詳細は<a href="#style-text-stroke" class="pageref">袋文字</a>を参照してください。 |
| text-shadow | 3.0.8 |  | none | すべての要素 | CSS Text Module Level 3 に沿った実装です。<br /> テキストの影を指定します。 詳細は<a href="#style-text-shadow" class="pageref">文字の影</a>を参照してください。 |
| -cssj-no-break-characters | 3.0.6 |  | none | すべての要素 | 独自プロパティです<br /> 禁則文字を追加します。 詳細は<a href="#style-no-break" class="pageref">禁則処理</a>を参照してください。 |
| -cssj-break-characters | 3.0.6 |  | none | すべての要素 | 独自プロパティです<br /> 禁則文字を解除します。 詳細は<a href="#style-no-break" class="pageref">禁則処理</a>を参照してください。 |
| background-clip | 3.2.16 |  | border-box | すべての要素。 | CSS Backgrounds and Borders Module Level 4 に沿った実装です。 値がtextの場合、テーブルと複数カラムには未対応であり、CID-Keyedフォントまたは絵文字以外のフォントに対してのみ有効です。 |
| box-sizing | 3.1.10 |  | content-box | <span class="cssprop">width</span>, <span class="cssprop">height</span>を指定可能な要素 | CSS Basic User Interface Module Level 3 に沿った実装です。 |
| -cssj-ruby | 3.0.0 |  | none | すべての要素 | 独自プロパティです<br /> ルビの役割(親文字・ふりがな・注釈コンテナ)を指定します。 値はnone, ruby, rb, rt, rtcのいずれかです。 通常はHTMLのruby/rb/rt/rtc要素に対して既定のスタイルシートが設定するため、指定する必要はありません。 詳細は<a href="#style-xml-ruby" class="pageref">ルビ</a>を参照してください。 |
| -cssj-warichu | 4.0.0 | する | none | インライン要素 | JLREQの割注を指定する独自プロパティです。値はnone, autoです。autoでは半サイズの2段とし、長文は禁則を守る断片として本文行をまたぎます。詳細は<a href="#style-autospace" class="pageref">和文詰め</a>を参照してください。 |
| word-wrap<br />-cssj-word-wrap | 3.0.0 |  | normal | すべての要素 | CSS Text Module Level 3 に沿った実装です。<br /> 行に収まらない長い単語を途中で折り返すかどうかです。 値はnormal, break-wordのいずれかです。 |
| block-flow<br />-cssj-block-flow | 3.0.0 |  | tb | すべての要素 | Internet Explorer 互換の書字方向指定です。 値はtb, rl, lrのいずれかです。 新しく書く文書では<span class="cssprop">writing-mode</span>を使用してください。 |
| hyphens | 4.0.0 |  | manual | すべての要素 | 欧文をハイフンで分割するかどうかです。 値はnone, manual, autoのいずれかです。 autoが働くのはlang属性がenの範囲だけです。 詳細は<a href="#style-hyphens" class="pageref">ハイフネーション</a>を参照してください。 |
| text-wrap-style<br />text-wrap | 4.0.0 |  | auto | すべての要素 | 行の分割方法です。 値はauto, prettyのいずれかです(balance, stableは受理しますがauto扱いです)。 詳細は<a href="#style-line-breaking" class="pageref">行分割の品質</a>を参照してください。 |
| string-set | 4.0.0 |  | none | すべての要素 | CSS Generated Content for Paged Media の実装です。<br /> 要素の内容を名前付き文字列へ取り込みます。 <span class="cssprop">content</span>の中でstring()関数により参照します。 詳細は<a href="#style-running-heading" class="pageref">柱(ランニングヘッダー)</a>を参照してください。 |

#### <a id="appx-css-func">CSS関数</a>

**CSS関数一覧**

| 名前 | バージョン | 引数の数 | 引数の型 | 適用プロパティ | 説明 |
| --- | --- | --- | --- | --- | --- |
| string | 4.0.0 | 1,2 | 文字列[, 識別子] | content | <span class="cssprop">string-set</span>で取り込んだ名前付き文字列を出力します。 2つめの引数はそのページのどの値を使うかで、first(既定)、last、start、first-exceptのいずれかです。 詳細は<a href="#style-running-heading" class="pageref">柱(ランニングヘッダー)</a>を参照してください。 |
| target-counter | 4.0.0 | 2,3 | 参照先[, カウンタ名, 数字タイプ] | content | 参照先の要素が現れるページなどのカウンタの値を出力します。 参照先は識別子・文字列・url()・attr()で指定します。 3つめの引数は数字のタイプ(<span class="cssprop">list-style-type</span>と同じ名前)で、省略するとdecimalです。 |
| target-counters | 4.0.0 | 3,4 | 参照先, カウンタ名, 区切り文字[, 数字タイプ] | content | 入れ子になったカウンタの値を区切り文字でつないで出力します。 引数の意味はtarget-counterと同じで、3つめが区切り文字です。 |
| target-text | 4.0.0 | 1,2 | 参照先[, content] | content | 参照先の要素の内容を出力します。 2つめの引数はcontentのみ指定できます(省略可)。 |
| -cssj-page-ref | 2.0.0 | 2,3,4 | 文字列[, 文字列, 文字列] | content | 指定したドキュメントフラグメントでのカウンタの値を出力します。 詳細は[リンクとフラグメント](#style-cssj-page-ref)の節を参照してください。 |
| -cssj-cmyk | 1.0.0<br />オーバープリントの指定は3.1.0 | 3,4 | 整数<br />小数<br />パーセント値 | color<br />他、色を指定するプロパティ | CSSのrgbカラーの代わりに、CMYKで色を指定します。 引数の値はそれぞれCyan, Magenta, Yellow, Black, オーバプリントモードの順です。<br />オーバープリントモードはstandard, illustratorのいずれかです。 standardの場合はインクを重ねあわせしませんが、illustratorはインクを重ねます。デフォルトはstandardです。 オーバープリントモードの指定はPDF出力のみ有効です。 |
| -cssj-spot | 4.0.0 | 2,3,4 | 文字列<br />色<br />整数<br />小数<br />パーセント値 | color<br />他、色を指定するプロパティ | 特色(スポットカラー)で色を指定します。 引数の値はそれぞれ色名、代替色、濃度、オーバープリントモードの順です。<br />代替色は特色を扱えない環境で使われる色で、rgbや-cssj-cmykなどで指定します。 濃度は0〜1の小数またはパーセント値で、省略時は100%です。 オーバープリントモードはstandard, illustratorのいずれかです。<br />引数をregistrationの1つだけにすると、トンボ等に使うレジストレーションカラーになります。 PDF出力のみ有効です。 |
| -cssj-gray | 2.0.0 | 1 | 整数<br />小数<br />パーセント値 | color<br />他、色を指定するプロパティ | CSSのrgbカラーの代わりに、グレイスケールで色を指定します。 引数の値は黒味の強さです。 |
| linear-gradient | 3.2.16 | - | 角度<br />色<br />パーセント値 | background | グラデーション塗りを適用します。色停止点はいくつでも指定できます<span class="since">4.0.0</span>。<tt>radial-gradient</tt>・<tt>conic-gradient</tt>と繰り返し版(<tt>repeating-linear-gradient</tt>等)にも対応します。 |
| rgba | 3.0.8 | 4 | 整数<br />小数<br />パーセント値 | color<br />他、色を指定するプロパティ | CSS Color Module Level 3 に沿った実装です。<br />rgbカラーに加えて不透明度(Alpha)を指定します。 引数の値はそれぞれRed, Green, Blue, Alphaの順です。 詳細は<a href="#style-alpha" class="pageref">透明色</a>を参照してください。 |

#### CSS識別子

**CSS識別子一覧**

| 名前 | バージョン | 適用対象 | 説明 |
| --- | --- | --- | --- |
| <a id="appx-cssprop--cssj-decimal-full-width"></a><s>-cssj-decimal-full-width</s><br /> -cssj-full-width-decimal<span class="since">3.0.0</span> | 2.1.2 | list-style-typeプロパティ<br />counter関数 | decimal同様に番号を出力しますが、全角文字を用います。 |
| -cssj-cjk-decimal | 3.0.0 | list-style-typeプロパティ<br />counter関数 | 位取り漢数字を出力します。 |
| pages | 3.1.4 | counter関数 | CSS Paged Media Module Level 3 に沿った実装です。<br /> 文書の総ページ数を記録するカウンタです。<a href="#style-multipass">2パス以降の処理</a>で有効です。 |
| page | 3.0.0 | page-break-beforeプロパティ<br />page-break-afterプロパティ | CSS Paged Media Module Level 3 に沿った実装です。<br /> alwaysと同じ意味です。名前付きページを適用する<span class="cssprop">page</span>プロパティ<span class="since">4.0.0</span>とは別のものです。 |
| column | 3.0.0 | page-break-beforeプロパティ<br />page-break-afterプロパティ | CSS Multi-column Layout Module Level 1 に沿った実装です。<br /> 強制的に改段します。 |
| transparent | 3.2.16 | color等の色指定全般 | rgba(0, 0, 0, 0)と同値です。 |

#### CSSルール

**CSSルール一覧**

| 名前 | バージョン | 説明 |
| --- | --- | --- |
| @pageマージンボックス(@top-center等) | 4.0.0 | @pageルール内で、ノンブルや柱を配置する16個のマージンボックスを定義します。詳細は<a href="#style-page-margin-boxes">ページのマージンボックス</a>を参照してください。 |
| @layer | 4.0.0 | CSS Cascading and Inheritance Level 5 に沿った実装です。カスケードレイヤーを定義します。 |
| @supports | 4.0.0 | CSS Conditional Rules Module Level 3 に沿った実装です。プロパティの対応状況による条件付き規則です。 |
| @media(特性クエリ) | 4.0.0 | メディアタイプに加え、width/height/orientation等の特性クエリに対応しました。 |
| @container | 4.0.0 | 名前付きまたは無名の`container-type: inline-size`コンテナに対して、width/inline-sizeのmin/max/exact条件、and、単一条件のnotを使えます。前パスの実測幅を使うため<span class="ioprop">processing.pass-count</span>を2以上にします。or、style query、block軸条件には対応しません。 |

#### CSS擬似クラス

**CSS擬似クラス一覧**

| 名前 | バージョン | 説明 |
| --- | --- | --- |
| root | 3.2.16 | 文書のbody要素に対応します。 |
| has() / is() / not() / where() | 4.0.0 | Selectors Level 4 に沿った実装です。:has()等の一部のセレクタは<a href="#style-multipass">2パス以上の変換処理</a>が必要です。 |
| nth-child()系 / last-child系 / empty | 4.0.0 | Selectors Level 4 に沿った実装です。後方の兄弟に依存するものは2パス以上が必要です。 |
| dir() / scope | 4.0.0 | Selectors Level 4 に沿った実装です。 |
| ::marker | 4.0.0 | リストマーカーのスタイル指定です。 |
| ::footnote-call / ::footnote-marker | 4.0.0 | <a href="#style-footnotes">脚注</a>の呼び出し番号・本文先頭番号です。 |

#### CSS単位

**CSS単位一覧**

| 単位 | バージョン | 説明 |
| --- | --- | --- |
| rem | 3.1.9 | ルート要素(HTMLまたはBODY)のフォントサイズです。 |
| ch | 3.1.9 | CSS3の仕様上は数字の0の幅ですが、現状はexと同じです。 |

### <a id="appx-xml">XMLの拡張</a>

XML中で特別の意味をもつ要素、属性を処理します。

以降の表では、便宜上以下のように接頭辞と名前空間が対応しているものとして記述します。
当然、実際のドキュメント中では別の接頭辞を使うことができます(xmlで始まる接頭辞を除く)。
なお、接頭辞のないものは、任意の名前空間に属することを意味します。

| 接頭辞 | 名前空間 |
| --- | --- |
| cssj | http://www.cssj.jp/ns/cssjml |
| html | http://www.w3.org/1999/xhtml |
| svg | http://www.w3.org/2000/svg |

#### XML要素

**XML要素一覧**

| 名前 | バージョン | 属性 | 説明 |
| --- | --- | --- | --- |
| cssj:make-toc | 2.0.0 | counter, type | 目次を生成します。 counterはページ番号付けに使用するページカウンタの名前で、 typeはページ番号のスタイルです。 詳細は[目次](#style-xml-toc)の章を参照してください。 |
| html:img | 1.0.0 | alt, src, width, height | HTMLのimg要素と同等の働きをします。 |
| html:a | 1.0.0 | href,name | HTMLのa要素と同等の働きをします。 |
| html:br | 2.0.0 |  | HTMLのbr要素と同等の働きをします。 |
| html:h1～html:h6 | 1.0.0 |  | HTMLのh1～h6要素と同等の働きをします。 |
| svg:svg | 1.2.0 |  | SVG画像として処理します。 詳細は[インラインSVG](#style-image-inline-svg)の節を参照してください。 |

#### XML属性

<table class="spec">
<caption>XML属性一覧</caption>
<thead>
<tr>
<th>名前</th>
<th>バージョン</th>
<th>説明</th>
</tr>
</thead>
<tbody>
<tr>
<td class="nowrap">cssj:annot</td>
<td class="nowrap">1.2.0</td>
<td>処理中に注釈メッセージを出力します。 設定された値が<a href="#appx-messages-annot">annot</a>メッセージとしてドライバに送り返されます。
</td>
</tr>
<tr id="appx-xml-cssj:header">
<td class="nowrap">cssj:header</td>
<td class="nowrap">1.2.0</td>
<td>一般的な要素にHTMLのh1～h6と同じ意味を持たせ、見出しとして目次やブックマークの生成に使います。
値は見出しのレベルです。</td>
</tr>
<tr>
<td class="nowrap">html:style</td>
<td class="nowrap">1.0.0</td>
<td>HTMLのstyle属性と同等の働きをします。</td>
</tr>
<tr>
<td class="nowrap">html:class</td>
<td class="nowrap">1.0.0</td>
<td>HTMLのclass属性と同等の働きをします。</td>
</tr>
<tr>
<td class="nowrap">html:colspan</td>
<td class="nowrap">1.0.0</td>
<td>HTMLのcolspan属性と同等の働きをします。</td>
</tr>
<tr>
<td class="nowrap">html:rowspan</td>
<td class="nowrap">1.0.0</td>
<td>HTMLのrowspan属性と同等の働きをします。</td>
</tr>
<tr>
<td class="nowrap">xml:lang</td>
<td class="nowrap">2.0.0</td>
<td>HTMLのlang属性と同等の働きをします。</td>
</tr>
<tr>
<td class="nowrap">id</td>
<td class="nowrap">1.0.0</td>
<td>HTMLのid属性と同等の働きをします。</td>
</tr>
</tbody>
</table>
