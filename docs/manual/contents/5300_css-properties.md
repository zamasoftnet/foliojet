## <a id="appx-css">CSSプロパティのサポート状況</a>

以下の表は、実装の基礎となるW3C CSS 2.1の各プロパティについてサポート状況を示すものです。
全体の準拠レベルをCSS 2.1に限定する表ではありません。
CSS3以降のモジュール(段組・縦書き・Gridレイアウト・論理プロパティ・
日本語組版拡張など)については、このページ末尾の
<a href="#appx-css3">CSS3以降のモジュール</a>と
<a href="#appx-cssprop-ext">CSS拡張</a>の一覧を参照してください。

<p>
	<div
		style="display: inline-block; width: 1em; height: 1em; border: 1pt solid Black;">
	</div>
	…対応<br />
	<div class="notice"
		style="display: inline-block; width: 1em; height: 1em; border: 1pt solid Black;">
	</div>
	…一部対応<br />
	<div class="negative"
		style="display: inline-block; width: 1em; height: 1em; border: 1pt solid Black;">
	</div>
	…未対応<br />
</p>

**HTML/XML要素に対するCSSプロパティ**

| 特性 | サポート | 備考 |
| --- | --- | --- |
| <span class="negative">azimuth</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| background-attachment | する | |
| background-color | する | |
| background-image | する | |
| background-position | する | |
| background-position-x<span class="since">4.0.0</span> | する | |
| background-position-y<span class="since">4.0.0</span> | する | |
| background-origin<span class="since">4.0.0</span> | する | 背景画像の配置の基準を<tt>border-box</tt>・<tt>padding-box</tt>・<tt>content-box</tt>から選びます。 |
| background-blend-mode<span class="since">4.0.0</span> | する | 多層背景のレイヤ同士の混色です。 |
| hyphenate-character<span class="since">4.0.0</span> | する | 語を分割したときに置く文字です。<tt>auto</tt>のほか、任意の文字列を指定できます。 |
| background-repeat | する | |
| background | する | |
| border-collapse | する | |
| border-color | する | |
| border-spacing | する | |
| border-style | する | |
| border-top | する | |
| border-right | する | |
| border-bottom | する | |
| border-left | する | |
| border-top-color | する | |
| border-right-color | する | |
| border-bottom-color | する | |
| border-left-color | する | |
| border-top-style | する | |
| border-right-style | する | |
| border-bottom-style | する | |
| border-left-style | する | |
| border-top-width | する | |
| border-right-width | する | |
| border-bottom-width | する | |
| border-left-width | する | |
| border-width | する | |
| border | する | |
| bottom | する | |
| caption-side | する | |
| clear | する | |
| border-image<span class="since">4.0.0</span> | 一部 | <span class="cssprop">border-image-source</span>・<span class="cssprop">border-image-slice</span>・<span class="cssprop">border-image-width</span>・<span class="cssprop">border-image-outset</span>・<span class="cssprop">border-image-repeat</span>と短縮形に対応し、画像を9つに切って枠を描きます。<span class="cssprop">border-image-repeat</span>の<tt>repeat</tt>・<tt>round</tt>・<tt>space</tt>は、実際に画像を並べて描きます<span class="since">4.0.0</span>。<tt>repeat</tt>は中央を基準に並べて両端を切り、<tt>round</tt>は整数個に収まるよう大きさを調節し、<tt>space</tt>は整数個だけ置いて余りを均等な隙間に配ります(1個も入らない辺は描きません)。画像のかわりにグラデーションを指定した場合は、仕様どおり枠の外縁の矩形を画像の大きさとみなして9つに切り分け、各部分を画像と同じ規則(伸縮・並べ方・<span class="cssprop">border-image-slice</span>・<tt>fill</tt>)で描きます<span class="since">4.0.0</span>。<span class="cssprop">border-image-width</span>が<tt>auto</tt>のときは境界の幅を使います。 |
| image-orientation<span class="since">4.0.0</span> | する | <tt>from-image</tt>(初期値)と<tt>none</tt>に対応します。写真のEXIFに記録された向きは既定で反映し、<tt>none</tt>を指定すると反映しません。現在の仕様に角度の指定はないため受け付けません。 |
| clip-path<span class="since">4.0.0</span> | 一部 | 基本形状の<tt>inset()</tt>(round対応、角の半径は縦横同値)・<tt>rect()</tt>・<tt>xywh()</tt>・<tt>circle()</tt>・<tt>ellipse()</tt>・<tt>polygon()</tt>・<tt>path()</tt>(SVGパスデータ、座標はpx、fill-rule任意)と参照ボックス(border-box等)に対応します。<tt>url()</tt>参照には対応していません(宣言ごと無視されます)。ページをまたいで分割されたボックスは断片ごとに切り抜かれます。 |
| clip | する | `rect(上 右 下 左)`で切り抜く範囲を指定します。各値は長さかautoです。 |
| color | する | |
| content | する | |
| counter-increment | する | |
| counter-reset | する | |
| <span class="negative">cue-after</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| <span class="negative">cue-before</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| <span class="negative">cue</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| <span class="negative">cursor</span> | しない | インタラクティブスタイルのため、印刷には無関係です。 |
| direction | する<span class="since">4.0.0</span> | 右から左へ書く言語(アラビア語・ヘブライ語など)に対応しています。段落単位のUnicode双方向アルゴリズム(UAX #9)で、ブロックの方向を基底に行内を視覚順に並べ替えます。括弧類は鏡像化し、右から左の行は<tt>text-align: start</tt>が右端になります。 |
| display | する | `contents`<span class="since">4.0.0</span>(要素自身のボックスを作らず子を親へ流す)に対応します。`run-in`と`inline-flex`/`inline-grid`には対応していません。 |
| <span class="negative">elevation</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| empty-cells | する | |
| float | する | |
| font-family | する | |
| font-size | する | |
| font-style | する | |
| font-variant | 一部 | 短縮形として<span class="cssprop">font-variant-caps</span>・<span class="cssprop">font-variant-ligatures</span>・<span class="cssprop">font-variant-alternates</span>・<span class="cssprop">font-variant-east-asian</span>・<span class="cssprop">font-variant-numeric</span>へ展開します。 |
| <span class="notice">font-variant-caps</span><span class="since">4.0.0</span> | 一部 | 値はすべて受理し、対応するOpenType機能(smcp・c2sc・pcap・c2pc・unic・titl)をフォントへ渡します。フォントがその機能を持たない場合に字形を合成することはしません。 |
| <span class="notice">font-variant-ligatures</span><span class="since">4.0.0</span> | 一部 | 値はすべて受理し、対応するOpenType機能(liga・clig・dlig・hlig・calt)をフォントへ渡します。標準合字(liga)と文脈合字(clig)は既定で有効、任意合字(dlig)と歴史的合字(hlig)は指定したときだけ有効になります(<tt>none</tt>・<tt>no-common-ligatures</tt>で標準合字を止められます)<span class="since">4.0.0</span>。合字は2字ずつ順に結合するため、3字以上の合字は中間の合字を持つフォントでだけ成立します。<tt>calt</tt>(文脈依存の字形置換)には対応していません。 |
| <span class="notice">font-variant-alternates</span><span class="since">4.0.0</span> | 一部 | <tt>historical-forms</tt>はOpenType機能(hist)として適用します。<tt>stylistic()</tt>などの関数形式は<tt>@font-feature-values</tt>で定義した名前を解決し、対応するOpenType機能(<tt>salt</tt>・<tt>ss01</tt>〜<tt>ss20</tt>・<tt>cv01</tt>〜<tt>cv99</tt>・<tt>swsh</tt>・<tt>ornm</tt>・<tt>nalt</tt>)へ落とします<span class="since">4.0.0</span>。定義されていない名前は、その関数だけを無視します。 |
| <span class="notice">font-palette</span><span class="since">4.0.0</span> | 一部 | 値は受理しますが、カラーフォントのパレット切り替えには対応していません。 |
| font-weight | する | |
| font-stretch<span class="since">4.0.0</span> | する | <span class="cssprop">font-width</span>も同じ意味です。キーワード(condensed等)と割合を幅級に丸め、同じファミリにitalic/weightが同じ面が複数あるとき幅級の近い面を選びます。@font-faceの<span class="cssprop">font-stretch</span>ディスクリプタで面の幅級を宣言できます。字形の伸縮は行わないため、幅の違う面が無い場合は見た目は変わりません。 |
| font-synthesis<span class="since">4.0.0</span> | する | <span class="cssprop">font-synthesis-weight</span> / <span class="cssprop">font-synthesis-style</span>も指定できます。noneで疑似ボールド(輪郭の太らせ)・疑似イタリック(機械的な傾き)を抑止します。small-caps/positionの値は受理しますが対応する合成機構はありません。 |
| font | する | |
| height | する | |
| left | する | |
| letter-spacing | する | |
| line-height | する | |
| list-style-image | する | |
| list-style-position | する | |
| <span class="notice">list-style-type</span> | する | <tt>cjk-decimal</tt><span class="since">4.0.0</span>に対応します。hebrew, armenian, georgianは字形を持たず算用数字で表します(必要な記号は<tt>@counter-style</tt><span class="since">4.0.0</span>で定義できます)。 |
| list-style | する | |
| margin-right | する | |
| margin-left | する | |
| margin-top | する | |
| margin-bottom | する | |
| margin | する | |
| max-height | する | |
| max-width | する | |
| min-height | する | |
| min-width | する | |
| orphans | する | |
| outline-color | する<span class="since">4.0.0</span> | <tt>invert</tt>は文字色として描画します。 |
| outline-style | する<span class="since">4.0.0</span> | <tt>auto</tt>は<tt>solid</tt>として描画します。 |
| outline-width | する<span class="since">4.0.0</span> | |
| outline | する<span class="since">4.0.0</span> | <span class="cssprop">outline-offset</span>(負の値も可)にも対応します。アウトラインはレイアウトに影響せず、境界の直後に描かれます(要素の内容より上には重なりません)。 |
| overflow | する | <tt>scroll</tt>と<tt>auto</tt>は印刷ではブラウザの見た目どおり、指定寸法からはみ出す描画をクリップします(スクロールで到達できる範囲は印刷されません)<span class="since">4.0.0</span>。<tt>clip</tt>は<tt>hidden</tt>と同じです。2値の指定(<tt>overflow: hidden auto</tt>等)にも対応します<span class="since">4.0.0</span>。 |
| overflow-x | する | 印刷では軸別に「クリップする/しない」を混在できないため(CSS Overflow 3の計算規則どおり)、両軸が<tt>visible</tt>のときだけはみ出しが描かれ、それ以外は両軸ともクリップされます<span class="since">4.0.0</span>。 |
| overflow-y | する | <tt>overflow-x</tt>と同じです<span class="since">4.0.0</span>。 |
| padding-top | する | |
| padding-right | する | |
| padding-bottom | する | |
| padding-left | する | |
| padding | する | |
| page-break-after | する | |
| page-break-before | する | css-break-3の正式名<span class="cssprop">break-before</span>/<span class="cssprop">break-after</span>/<span class="cssprop">break-inside</span>も同じ機構へのエイリアスとして指定できます<span class="since">4.0.0</span>。 |
| page-break-inside | する | |
| <span class="negative">pause-after</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| <span class="negative">pause-before</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| <span class="negative">pause</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| <span class="negative">pitch-range</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| <span class="negative">pitch</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| <span class="negative">play-during</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| position | する | |
| quotes | する | |
| <span class="negative">richness</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| right | する | |
| <span class="negative">speak-header</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| <span class="negative">speak-numeral</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| <span class="negative">speak-punctuation</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| <span class="negative">speak</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| <span class="negative">speech-rate</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| <span class="negative">stress</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| table-layout | する | |
| text-align | する | <tt>match-parent</tt><span class="since">4.0.0</span>にも対応します(親の値を継ぎ、親の<tt>start</tt>/<tt>end</tt>は親の<span class="cssprop">direction</span>で左右に確定します)。<span class="cssprop">text-align-last</span>の<tt>match-parent</tt>も同じです。 |
| text-decoration | する | |
| text-indent | する | |
| text-transform | する | |
| top | する | |
| unicode-bidi | する<span class="since">4.0.0</span> | normal, embed, bidi-override, isolate, isolate-override, plaintext のすべてに対応します(isolateは周囲へ影響しない真の隔離、plaintextは最初の強い文字で方向を決めます)。HTMLのdir属性(isolate)、dir=autoとbdi要素(plaintext)、bdo要素(isolate-override)からも設定されます。 |
| vertical-align | する | |
| visibility | する | |
| <span class="negative">voice-family</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| <span class="negative">volume</span> | しない | 音声スタイルのため、印刷には無関係です。 |
| white-space | する | インライン境界は両側の最も近い共通祖先で折返し可否を判定 |
| widows | する | |
| width | する | |
| word-spacing | する | |
| z-index | する | |

**ページに対するCSSプロパティ**

| 特性 | サポート | 備考 |
| --- | --- | --- |
| margin-top | する | |
| margin-right | する | |
| margin-bottom | する | |
| margin-left | する | |
| margin | する | |
| size | する<span class="since">4.0.0</span> | 入出力プロパティ<span class="ioprop">output.page-width</span>・<span class="ioprop">output.page-height</span>より優先されます。 |
| border-*、padding-*、background | する<span class="since">4.0.0</span> | |
| counter-reset、counter-increment | する<span class="since">4.0.0</span> | ページカウンタの操作に使用します。 |

### <a id="appx-css3">CSS3以降のモジュール</a>

CSS3以降のモジュールのうち、対応している主なものは次のとおりです。
各プロパティの詳細は<a href="#appx-cssprop-ext">CSS拡張</a>の一覧を参照してください。

| モジュール | 対応 | 備考 |
| --- | --- | --- |
| Multi-column Layout(段組) | する | <span class="cssprop">columns</span>、<span class="cssprop">column-span</span>、<span class="cssprop">column-fill</span>等。 |
| Writing Modes(縦書き) | 一部 | <span class="cssprop">writing-mode</span>(horizontal-tb / vertical-rl / vertical-lr<span class="since">4.0.0</span>。SVG互換の別名 lr / lr-tb / rl / rl-tb / tb / tb-rl も受理)、縦中横、<span class="cssprop">text-orientation</span>(mixed / upright / sideways<span class="since">4.0.0</span>)。writing-modeは<span class="cssprop">direction</span>を変えません(vertical-lrのinline-startは上。旧来の「vertical-lrで方向がrtlになる」展開は<span class="cssprop">-cssj-writing-mode</span>にだけ残っています)。sideways-rl / sideways-lr<span class="since">4.0.0</span>は横組みの行を90°回して縦に置きます(sideways-rlは時計回りで上から下へ、sideways-lrは反時計回りで下から上へ。画像やインラインブロックは回しません)。 |
| Logical Properties(論理プロパティ)<span class="since">4.0.0</span> | する | <span class="cssprop">margin-block-start</span>、<span class="cssprop">inline-size</span>等。<span class="cssprop">margin-block</span>/<span class="cssprop">margin-inline</span>/<span class="cssprop">padding-block</span>/<span class="cssprop">padding-inline</span>/<span class="cssprop">inset-block</span>/<span class="cssprop">inset-inline</span>の短縮形と、<span class="cssprop">border-inline-start</span>等の論理境界短縮形、論理角丸(<span class="cssprop">border-start-start-radius</span>等。横書き・左横書きの物理角へ写す近似)にも対応します。 |
| Grid Layout<span class="since">4.0.0</span> | 一部 | <span class="cssdecl">display: grid;</span>とトラック定義・アイテム配置・整列(<span class="cssprop">place-items</span>/<span class="cssprop">place-self</span>/<span class="cssprop">place-content</span>ショートハンド含む)に対応します。トラックでは<tt>repeat()</tt>と<tt>minmax()</tt>を使えますが、minmax()はminmax(0,&lt;fr&gt;)以外では最大値だけを採用する近似です。<span class="cssprop">grid-template-columns</span>を省略した場合は暗黙の単一カラムとして扱います。グリッド内の改ページ(断片化)は未対応で、inline-gridはブロックレベルgridとして近似します。<span class="cssdecl">position: absolute</span>/<tt>fixed</tt>のグリッドコンテナ(用紙の中に版面を絶対配置する定型)にも対応します<span class="since">4.0.0</span>——絶対配置の箱の中にグリッドを組みます。floatのグリッドコンテナは通常のブロックへ落ちます(警告2823)。 <span class="cssprop">grid-area</span>と<span class="cssprop">grid-template-areas</span>(領域名による配置)、<tt>[名前]</tt>付きの線、<span class="cssprop">grid-template-rows</span>、<span class="cssprop">grid-auto-rows</span>/<span class="cssprop">grid-auto-columns</span>、<span class="cssprop">grid-auto-flow</span>(<tt>column</tt>/<tt>dense</tt>)、<tt>%</tt>・<tt>min-content</tt>・<tt>repeat(auto-fill, minmax(…))</tt>のトラック指定、旧別名の<span class="cssprop">grid-gap</span>系にも対応します<span class="since">4.0.0</span>。<tt>minmax()</tt>は最小値・最大値の両方をCSS Grid仕様のトラックサイズ決定手順どおりに扱います。<tt>subgrid</tt>はグリッドアイテム直下のグリッドで列軸に対応し、親グリッドの列線に揃えます。行軸(<span class="cssdecl">grid-template-rows: subgrid</span>)は、アイテムがまたぐ親の行がすべて確定した長さ(絶対長、または親の高さが指定されているときの%)で、アイテムが<span class="cssdecl">align-self: stretch</span>(既定)かつ高さ未指定のときに親の行線・行の間隔・線名を継ぎます<span class="since">4.0.0</span>。親の行に<tt>auto</tt>や<tt>fr</tt>が混ざるときは行の間隔だけ継いで行は内容高になり、警告2823を出します。<span class="cssprop">grid-template</span>と<span class="cssprop">grid</span>のショートハンドにも対応します。 アイテムは既定(<span class="cssdecl">align-items: stretch</span>)で<b>行の高さ(<span class="cssprop">grid-row</span>で複数行にまたがるときはその範囲)いっぱいに伸びます</b><span class="since">4.0.0</span>——背景や枠も一緒に伸びます。<span class="cssprop">align-self</span>に<tt>start</tt>/<tt>center</tt>/<tt>end</tt>を指定するか、高さを明示すればその値が優先されます。 |
| Flexible Box Layout(Flexbox)<span class="since">4.0.0</span> | 一部 | <span class="cssdecl">display: flex;</span>、<span class="cssprop">flex</span>系プロパティ、<span class="cssprop">gap</span>、<span class="cssprop">justify-content</span>/<span class="cssprop">align-*</span>、<span class="cssprop">order</span>、折り返し・逆順・縦書きに対応します。inline-flexはブロックレベルflexとして近似します。フレックスコンテナ内の改ページ(断片化)、真のインライン配置、column方向で高さ未指定のアイテムの内容による伸縮には対応していません。 |
| Paged Media | する | マージンボックス<span class="since">4.0.0</span>、名前付きページ<span class="since">4.0.0</span>、<span class="cssprop">size</span><span class="since">4.0.0</span>、<span class="cssprop">bleed</span>(塗り足し)<span class="since">4.0.0</span>、<span class="cssprop">marks</span>(トンボ)<span class="since">4.0.0</span>、総ページ数カウンタ。入稿用の設定は<a href="#prepress-terms" class="pageref">印刷所へ入稿する</a>を参照してください。 |
| Generated Content for Paged Media(GCPM) | 一部 | <span class="cssprop">string-set</span>/<tt>string()</tt><span class="since">4.0.0</span>、<tt>target-counter()</tt>/<tt>target-text()</tt>、<tt>leader()</tt><span class="since">4.0.0</span>、脚注<span class="since">4.0.0</span>。 |
| Text Level 3/4 | 一部 | <span class="cssprop">text-autospace</span>・<span class="cssprop">text-spacing-trim</span>・<span class="cssprop">hanging-punctuation</span>・JLREQ優先度付き行調整・<span class="cssprop">hyphens</span>・<span class="cssprop">text-wrap-style</span>・<span class="cssprop">text-justify</span>(<tt>auto</tt>は言語で決め、韓国語は語間だけを伸ばす)<span class="since">いずれも4.0.0</span>。 |
| Fonts Level 3 | 一部 | <span class="cssprop">font-feature-settings</span>・<span class="cssprop">font-variant-east-asian</span>・<span class="cssprop">font-variant-numeric</span><span class="since">4.0.0</span>、@font-face、unicode-range。Level 4の<span class="cssprop">font-synthesis</span>・<span class="cssprop">font-variation-settings</span>(@font-faceディスクリプタ)<span class="since">4.0.0</span>にも対応します。 |
| Cascading Level 5 | する | カスケードレイヤー(@layer)<span class="since">4.0.0</span>。 |
| Conditional Rules | する | @supports・@mediaの特性クエリ<span class="since">4.0.0</span>。 |
| Container Queries<span class="since">4.0.0</span> | 一部 | <span class="cssprop">container-type: inline-size</span>、<span class="cssprop">container-name</span>、@containerのwidth/inline-size条件(and/not)、cqw/cqi。前パスの実測幅を使うため<span class="ioprop">processing.pass-count</span>を2以上にします。cqwとcqiは同じinline-sizeへ解決します。container-type:size、or、style query、block軸条件は未対応です。 |
| CSS Inline Layout<span class="since">4.0.0</span> | 一部 | ::first-letterの<span class="cssprop">initial-letter</span>をドロップキャップへ展開します。実フォントのcap heightを使い、既存のfloatで回り込みます。 |
| Custom Properties(CSS変数) | する | <tt>var()</tt>・<tt>calc()</tt>・<tt>min()</tt>/<tt>max()</tt>/<tt>clamp()</tt><span class="since">4.0.0</span>。数学関数<tt>sqrt()</tt>・<tt>exp()</tt>・<tt>pow()</tt>・<tt>log()</tt>・<tt>hypot()</tt>・<tt>sin()</tt>・<tt>cos()</tt>・<tt>tan()</tt>・<tt>asin()</tt>・<tt>acos()</tt>・<tt>atan()</tt>・<tt>atan2()</tt>にも対応します<span class="since">4.0.0</span>。角度を取る関数には<tt>deg</tt>・<tt>rad</tt>・<tt>turn</tt>と裸の数値(ラジアン)を指定でき、定義域を外れた指定は無効になります。 |
| Color Level 3/4/5<span class="since">4.0.0</span> | 一部 | <tt>hsl()</tt>/<tt>hsla()</tt>、<tt>oklch()</tt>/<tt>oklab()</tt>(sRGBへ変換)、<tt>color-mix()</tt>(in srgb / oklab / oklch)、<tt>light-dark()</tt>(印刷は常にライト側)、<tt>lab()</tt>/<tt>lch()</tt>、<tt>hwb()</tt>、<tt>color()</tt>(srgb・srgb-linear・display-p3・a98-rgb・prophoto-rgb・rec2020・xyz・xyz-d50・xyz-d65)。いずれもsRGBへ変換し、sRGBの色域を超える色は各成分を0〜1へ丸めます。 |
| Selectors Level 4 | 一部 | <tt>:has()</tt>・<tt>:is()</tt>・<tt>:nth-child()</tt>系・<tt>:any-link</tt><span class="since">4.0.0</span>等。一部は2パス以上の変換処理が必要です。 |
| Containment Level 2<span class="since">4.0.0</span> | 一部 | <span class="cssprop">content-visibility</span>に対応します。<tt>hidden</tt>は要素のボックスは残して内容をレイアウトから省きます(ウェブページのオフキャンバスメニュー等が印刷で巨大な空白になるのを防ぎます)。<tt>auto</tt>は画面表示での遅延レンダリングの指示のため、印刷では<tt>visible</tt>と同じ扱いです(実ブラウザの印刷と同じ)。<span class="cssprop">contain</span>・<span class="cssprop">contain-intrinsic-size</span>には対応していません。 |
| Masking<span class="since">4.0.0</span> | 一部 | グラデーションの<span class="cssprop">mask-image</span>(<span class="cssprop">-webkit-mask-image</span>も同じ)を、ボックスからはみ出す描画のクリップとして近似します。本文の抜粋を<span class="cssprop">max-height</span>とフェードアウトで打ち切るよくある書き方で、はみ出した本文が後続へ重なるのを防ぐためのものです。フェードの濃淡自体は再現しません。<tt>url()</tt>のマスクは単色SVGのアイコン型抜きとして近似します。<span class="cssprop">mask-size</span>・<span class="cssprop">mask-position</span>・<span class="cssprop">mask-repeat</span>・<span class="cssprop">mask-origin</span>・<span class="cssprop">mask-clip</span>と<span class="cssprop">mask</span>短縮形(<span class="cssprop">-webkit-mask</span>も同じ)を指定できます。<span class="cssprop">mask-mode</span>と<span class="cssprop">mask-composite</span>は受理するだけで、描画には反映しません。 |
| Counter Styles Level 3<span class="since">4.0.0</span> | 一部 | <tt>@counter-style</tt>で番号の記号を定義できます(<tt>system</tt>はcyclic / fixed / symbolic / alphabetic / numeric / additive / extends、<tt>symbols</tt>・<tt>additive-symbols</tt>・<tt>prefix</tt>・<tt>suffix</tt>・<tt>negative</tt>・<tt>range</tt>・<tt>pad</tt>・<tt>fallback</tt>)。定義した名前は<span class="cssprop">list-style-type</span>と<tt>counter()</tt>/<tt>counters()</tt>・<tt>target-counter()</tt>で使えます。<tt>speak-as</tt>(音声用)と<tt>symbols()</tt>関数記法には対応していません。 |
| ページフロート<span class="since">4.0.0</span> | 一部 | <span class="cssdecl">float: bottom;</span>で版面の下端(脚注があればその上)へ、<span class="cssdecl">float: top;</span>でそのページの先頭(本文と合わせて収まらなければ次のページの先頭)へ図表を寄せられます。既に組んだ本文は図表の高さのぶんだけ送られます。図表と並ぶ行だけが図表を避けて短くなります(通常のフロートと同じ回り込み)。本文の後に書いた上端フロートは、幅が狭くても帯として置かれ脇には回り込みません。表・段組の内側や前ページから続く段落の途中に書いた上端フロートは次のページの先頭になることがあります(既に組んだ行を組み直さないため)。 |
| Backgrounds and Borders Level 3<span class="since">4.0.0</span> | 一部 | <span class="cssprop">box-shadow</span>(外側・内側(inset)・複数指定・広がり・border-radiusへの追随)に対応します。ぼかし(blur-radius)は、画像出力(PNG/JPEG)とSVG系の出力では本物のガウスぼかしで描きます<span class="since">4.0.0</span>。PDFにはぼかしの描画命令が無いため、PDF出力でだけ12段の同心の半透明塗りで近似し、警告2822で知らせます。一般的な薄い影では画面表示と区別がつきませんが、濃い色で大きくぼかした影を強く拡大すると段階状の階調が見えることがあります。<span class="cssprop">background</span>短縮形の3〜4値の位置指定(<tt>right 10px bottom 20px</tt>)にも対応します。 |
| Box Sizing Level 3<span class="since">4.0.0</span> | 一部 | <span class="cssprop">width</span>・<span class="cssprop">height</span>・<span class="cssprop">min-width</span>・<span class="cssprop">max-width</span>・<span class="cssprop">min-height</span>・<span class="cssprop">max-height</span>(および<span class="cssprop">inline-size</span>・<span class="cssprop">block-size</span>などの論理プロパティ)に固有寸法キーワード<tt>max-content</tt>(折り返さずに並べた内容の幅)、<tt>min-content</tt>(分割できない最長の語の幅)、<tt>fit-content</tt>(内容幅と利用可能幅の小さい方)、<tt>fit-content(長さ)</tt>を指定できます。通常フローのブロックに<tt>width: max-content</tt>を書くと箱は内容の幅になり、<tt>margin: 0 auto</tt>で中央に寄せられます。行方向(横書きなら幅)にだけ意味があり、ブロック方向に書いた場合は<tt>auto</tt>と同じです。表には適用されません。<span class="cssprop">aspect-ratio</span>(<tt>16 / 9</tt>のように書くと、幅が決まっているボックス(通常のブロック、フロート、flex/gridアイテム、画像など)の高さを比率から決めます。内容が多いときは<tt>overflow: visible</tt>なら内容に合わせて伸び、それ以外は比率の高さで切り取られます。画像では指定した比率が本来の比率より優先され、<tt>auto 1</tt>のように<tt>auto</tt>を併記すると本来の比率がある画像はそれを優先します)にも対応します。 |
| Shapes Level 1<span class="since">4.0.0</span> | 一部 | 左右に浮動させた要素に<span class="cssprop">shape-outside</span>を指定すると、周囲のテキストが矩形ではなく指定した形状に沿って回り込みます。<tt>circle()</tt>・<tt>ellipse()</tt>・<tt>inset()</tt>・<tt>polygon()</tt>・<tt>path()</tt>の基本形状と<tt>margin-box</tt>などの参照ボックス、<tt>url()</tt>による画像指定(<span class="cssprop">shape-image-threshold</span>で不透明度の閾値)に対応し、<span class="cssprop">shape-margin</span>で形状の外側に余白を足せます。影響を受けるのはテキストなどインライン内容の折り返しだけで、後続の浮動体や表・画像などブロックレベルの箱は従来どおりマージンボックスを避けます。制限: <tt>url()</tt>画像は複数パス変換の計測パスではマージンボックスとして扱われます(最終出力には反映)。SVGなどラスタでない画像、グラデーションは未対応です。浮動体が改ページで分割された場合、続きのページでは矩形として扱われることがあります。 |
| Overflow Level 3/4<span class="since">4.0.0</span> | 一部 | <span class="cssprop">text-overflow</span>の<tt>clip</tt>と<tt>ellipsis</tt>に対応します。<span class="cssprop">overflow</span>が<tt>visible</tt>以外のブロックで、行の内容が幅に収まらない行(<tt>white-space: nowrap</tt>や分割できない長い語)の末尾を切り詰め、省略記号「…」(フォントに無ければ「...」)を置きます。右横書き、2値の指定、文字列の指定には対応していません。<span class="cssprop">-webkit-line-clamp</span>/<span class="cssprop">line-clamp</span>は指定した行数でインライン内容を打ち切り、続きがあれば最後の行の末尾に省略記号「…」を置きます。打ち切った行は高さに含まれないため後続の内容はブラウザと同じ位置に詰まります。入れ子のブロックの行も数えます。右横書きには対応していません。 |
| Compositing Level 1<span class="since">4.0.0</span> | 一部 | <span class="cssprop">mix-blend-mode</span>の<tt>normal</tt>〜<tt>luminosity</tt>の16種に対応し、PDFのブレンドモードとして出力します。画像出力(PNG/JPEG)とSVG系の出力では、要素全体を1つの層にしてから合成します<span class="since">4.0.0</span>。PDF出力では、背景・境界・文字・画像の描画ごとにモードを適用する近似になり、警告2822で知らせます。<span class="cssprop">isolation</span>は受理しますが効果はありません。 |
| Values Level 4<span class="since">4.0.0</span> | 一部 | ビューポート単位<tt>vw</tt>/<tt>vh</tt>/<tt>vmin</tt>/<tt>vmax</tt>(<tt>svw</tt>/<tt>lvh</tt>/<tt>dvh</tt>/<tt>vi</tt>/<tt>vb</tt>等の変種も同じ値)を、既定ページ寸法(<span class="ioprop">output.page-width</span>・<span class="ioprop">output.page-height</span>)から<span class="ioprop">output.page-margins</span>を除いた版面の1%として解決します(文書の<tt>@page</tt>で版面を変えた場合は反映されません)。<tt>env(safe-area-inset-*)</tt>は0、それ以外の<tt>env()</tt>はフォールバック値を使います。<tt>currentColor</tt>はどの色指定でも使えます。フォント相対単位は<tt>em</tt>/<tt>ex</tt>/<tt>rem</tt>/<tt>ch</tt>/<tt>lh</tt>に加えて<tt>cap</tt>(先頭フォントのキャップハイト)・<tt>rlh</tt>(ルート要素の行の高さ)・<tt>ic</tt>/<tt>ric</tt>に対応します。<tt>ic</tt>/<tt>ric</tt>は仕様の代替値どおり<tt>em</tt>/<tt>rem</tt>と同じ値になります。絶対長さの<tt>Q</tt>(1/40センチメートル)にも対応します<span class="since">4.0.0</span>。 |
| Transforms Level 1<span class="since">4.0.0</span> | 一部 | <span class="cssprop">transform</span>で割合の<tt>translate()</tt>を<tt>scale()</tt>や<tt>rotate()</tt>と組み合わせて使えます(<tt>translate(-50%, -50%) scale(1.1)</tt>など)。<tt>translate3d()</tt>・<tt>scale3d()</tt>・<tt>rotateZ()</tt>・<tt>matrix3d()</tt>は2Dへ縮退し、<tt>translateZ()</tt>・<tt>perspective()</tt>・<tt>rotateX()</tt>/<tt>rotateY()</tt>は無視されます。 |
| Images Level 3/4<span class="since">4.0.0</span> | 一部 | <span class="cssprop">background-image</span>のグラデーション関数に対応します。<tt>linear-gradient()</tt>、<tt>radial-gradient()</tt>(円・楕円、closest-side等の寸法キーワード、明示寸法、<tt>at</tt>による位置)、<tt>conic-gradient()</tt>、<tt>repeating-linear/radial/conic-gradient()</tt>、および<tt>-webkit-</tt>等の接頭辞つき旧構文と<tt>-webkit-gradient()</tt>を受理します。線形・放射はPDFのシェーディングとして出力し、楕円は円のシェーディングを縦横比で変形して描きます。円錐グラデーションは、画像出力(PNG/JPEG)では厳密に描きます<span class="since">4.0.0</span>。PDF出力では自由形状のメッシュシェーディング(ShadingType 4、2°以下の三角形で色を補間)として出力し、透明な色停止は輝度ソフトマスクで表します<span class="since">4.0.0</span>(PDF/A-1やPDF/X-1aでは透明度が落ちます)。SVGには円錐のシェーディングが無いため、SVG系の出力では2°以下の扇形の塗り分けで近似し、警告2822で知らせます。繰り返しグラデーションは、画像出力とSVG系の出力では1周期を繰り返す指定として厳密に描き<span class="since">4.0.0</span>、PDF出力では周期を展開して1つのシェーディングにします(64周期まで。打ち切ったときは警告2822)。コンマ区切りの多層背景は先頭のレイヤを最前面として重ね、半透明のグラデーションの下に背景色や画像が透けます。ただし<span class="cssprop">background-repeat</span>/<span class="cssprop">background-position</span>/<span class="cssprop">background-size</span>はレイヤごとに指定できず、先頭レイヤの値を全レイヤで共有します。<tt>image-set()</tt>(<tt>-webkit-image-set()</tt>)は出力解像度(<span class="ioprop">output.resolution</span>)を超えない最も高い解像度の候補を選びます。 |
| Filter Effects Level 1<span class="since">4.0.0</span> | 一部 | <span class="cssprop">filter</span>(<tt>-webkit-filter</tt>も可)に対応します。<tt>grayscale()</tt> <tt>sepia()</tt> <tt>saturate()</tt> <tt>hue-rotate()</tt> <tt>invert()</tt> <tt>brightness()</tt> <tt>contrast()</tt>は、背景・境界・文字などの単色とグラデーションの色、およびラスタ画像の画素に適用します(SVG画像の画素には適用されません)。<tt>opacity()</tt>は<span class="cssprop">opacity</span>と同じ不透明度として働きます。画像出力(PNG/JPEG)とSVG系の出力では、仕様どおり要素全体を1枚の層にしてから、色の変換・<tt>blur()</tt>・<tt>drop-shadow()</tt>を掛けます<span class="since">4.0.0</span>。PDF出力でも、色の変換・<tt>blur()</tt>・<tt>drop-shadow()</tt>を持つ要素は<b>その要素と子孫をまとめて1枚の画像にして</b>仕様どおりの効果を掛けます<span class="since">4.0.0</span>(解像度は<span class="ioprop">output.pdf.filter-resolution</span>、既定300dpi)。<span class="cssprop">filter</span>を持つ要素は仕様どおりstacking contextになります(<span class="cssprop">z-index</span>が<tt>auto</tt>なら0の層に描きます)。効果は仕様どおり要素の座標系で掛けてから<span class="cssprop">transform</span>を適用します(拡大した要素ではぼかしも拡大され、回転した要素では影も一緒に回ります)。画像出力では非一様な拡大(<tt>scale(2,1)</tt>など)のぼかしは等方の近似です。<tt>&lt;col&gt;</tt>と<tt>::first-line</tt>の<span class="cssprop">filter</span>は描画要素ごとの近似です。その要素の文字は画像の一部になるため選択・検索できません(タグ付きPDFでは画像がその要素の内容として構造に入ります)——警告2822(内容 filter-rasterized)で知らせます。<tt>opacity()</tt>だけの要素はベクタのままです。透明を使えないPDF/A-1・PDF/X-1aでは従来どおり描画要素ごとに効果を掛ける近似になり(<tt>blur()</tt>はラスタ画像に対してだけ働き、<tt>drop-shadow()</tt>はボックスでは境界の形の影、画像では不透明部分の形の影、文字には効かないので<span class="cssprop">text-shadow</span>を使ってください)、警告2822で知らせます。<tt>url()</tt>によるSVGフィルタと<span class="cssprop">backdrop-filter</span>は受理しますが効果はありません。 |
| Text Level 3/4(補足)<span class="since">4.0.0</span> | 一部 | <span class="cssprop">line-break</span>で日本語の禁則の強さを変えられます。既定(<tt>auto</tt>)は<tt>strict</tt>と同じで拗促音や長音を行頭に置きません。<tt>normal</tt>では拗促音・長音が行頭に来られ、<tt>loose</tt>ではさらに中点や繰返し記号なども行頭に置けます。<tt>anywhere</tt>はどの文字の間でも改行します。句読点はどの値でも行頭には来ません。<tt>&lt;pre&gt;</tt>などで保持されるタブ文字の幅は<span class="cssprop">tab-size</span>で指定できます(数値は空白文字の幅の倍数、既定8)。 |
| Transforms Level 2(補足)<span class="since">4.0.0</span> | 一部 | 個別プロパティ<span class="cssprop">translate</span>・<span class="cssprop">rotate</span>・<span class="cssprop">scale</span>を指定できます。複数指定した場合は translate→rotate→scale→transform の順に合成されます。<span class="cssprop">rotate</span>で有効なのは紙面上の回転(z軸)だけです。<span class="cssprop">zoom</span>は指定した要素とその中身を左上を基準に拡大して描きますが、周囲のレイアウトは変わりません。 |
| Cascade Level 4/5<span class="since">4.0.0</span> | 一部 | <span class="cssprop">all</span>(全体キーワードのみ)と、<tt>inherit</tt>/<tt>initial</tt>/<tt>unset</tt>のショートハンドへの指定(<tt>padding: inherit</tt>等)に対応します。<tt>revert</tt>/<tt>revert-layer</tt>はその宣言が無いものとして扱います。 |
| Fonts Level 4<span class="since">4.0.0</span> | 一部 | <span class="cssprop">font-kerning</span>(<tt>none</tt>でカーニングを止める)に対応します。<tt>@font-face</tt>の<tt>font-display</tt>・<tt>size-adjust</tt>・<tt>ascent-override</tt>等の記述子は受理して無視します。 |
| Nesting(入れ子記法)<span class="since">4.0.0</span> | 一部 | 規則の中に規則を書けます。<tt>&amp;</tt>による親参照(先頭・非先頭・複合)、<tt>&amp;</tt>省略時の子孫結合、<tt>&gt; .x</tt>等の相対セレクタ、セレクタリストの親、多段の入れ子に対応します。入れ子セレクタの詳細度はSass等のプリプロセッサと同じく展開後のセレクタごとに決まります(<tt>:is()</tt>による詳細度の揃えは行いません)。規則の中に入れ子にした<tt>@media</tt>等の条件規則には対応していません。 |
