## <a id="style-legacy">一般的なブラウザとの互換性</a>

一般的なブラウザと同じ考え方でHTMLとCSSを解釈しますが、
印刷を目的としているため、画面表示を目的としたブラウザとは異なる点があります。
このセクションでは、その違いと既知の制限事項を説明します。

### 標準モードと互換モード

DOCTYPE宣言によって、次の2つのモードのいずれかで処理されます。

<dl>

<dt>標準モード</dt>
<dd><tt>&lt;!DOCTYPE html&gt;</tt>(HTML5の簡易DOCTYPE)、または
	HTML 4.01/XHTML 1.0/XHTML 1.1 のDOCTYPE宣言がある文書に適用されます。</dd>
<dt>互換モード</dt>
<dd>DOCTYPE宣言がない文書、および上記以外のDOCTYPE宣言がある文書に適用されます。</dd>

</dl>

両モードの違いは次の2点だけです。

- **タグの入れ子の解釈**。標準モードではHTML5の要素(video、source、trackなど)を
	含む入れ子規則を適用します。互換モードでは旧来の規則を適用するため、
	HTML5固有の要素は未知の要素として扱われます。
- **表のフォントサイズ**。互換モードでは、table要素でフォントサイズが
	既定値(medium)に戻ります。標準モードでは継承します。

**HTML5の文書には`<!DOCTYPE html>`を付けてください。**
DOCTYPE宣言がないとvideo、source、track等のHTML5固有の要素が未知の要素として
扱われ、入れ子構造が正しく解釈されません。

### 既知の制限事項

#### ＭＳ明朝系フォントの文字幅について

ＭＳ明朝、ＭＳゴシックのフォントを使用する場合、一般的なブラウザでは小さなフォントサイズが指定された場合、
一番近いビットマップフォントが使用されるため、指定された文字サイズと実際に使われる文字サイズが異なることがありますが、Copper
PDFでは全てアウトラインフォントを使用するため、指定したとおりの文字サイズとなります。
そのため、文字幅が一般的なブラウザで表示する場合と異なり、文字列の折り返し位置等が異なることがあります。

#### サポートしていないCSSプロパティ

Internet Explorer独自のCSSプロパティで、以下のものはサポートしていません。

- <span class="cssprop">accelerator</span>
- <span class="cssprop">behavior</span>
- <span class="cssprop">block-progression</span>
- <span class="cssprop">filter</span>
- <span class="cssprop">layout-grid</span>
- <span class="cssprop">layout-grid-*</span>
- <span class="cssprop">interpolation-mode</span>
- <span class="cssprop">overflow-*</span>
- <span class="cssprop">scrollbar-*</span>
- <span class="cssprop">text-justify</span>
- <span class="cssprop">text-kashida-space</span>
- <span class="cssprop">text-overflow</span>
- <span class="cssprop">text-underline-position</span>
- <span class="cssprop">zoom</span>

<div class="note">

<span class="cssprop">text-autospace</span>は4.0.0でCSS標準
(CSS Text Module Level 4)としてサポートされました。
**既定値はnormal(和文と欧文・数字の境界にアキを入れる)です。**
旧バージョンの見た目に戻すには<span class="cssdecl">text-autospace: no-autospace;</span>を
指定してください。

</div>

<div class="note">

<span class="cssprop">word-break</span>,
<span class="cssprop">word-wrap</span>,
<span class="cssprop">writing-mode</span>
は、Internet Explorer独自の名前として導入されたものですが、
現在はCSS標準またはそれに準じるものとしてサポートしています<span class="since">4.0.0</span>。

</div>

### 自動レイアウトテーブル

CSS 2.1では自動レイアウトテーブル(デフォルト、あるいは<span class="cssdecl">table-layout:
	auto;</span>が指定されたテーブル)のレイアウトの調整方法について、
明確な仕様が定められていないため、どうしても一般的なブラウザと若干のレイアウトの違いが生じます。
なるべく以下のいずれかの条件の下で使用してください。

- <span class="cssdecl">table-layout: fixed;</span>が指定されたテーブルを使用する
- 自動レイアウトテーブルで、横方向に連結されたセルが存在する場合は、％による幅指定をしない

### 印刷向けの違い

画面表示を目的としたブラウザとは、次の点で扱いが異なります。
いずれも紙に出力するという目的から来るものです。

#### 紙面に収まらない内容は機械的に切って続ける

紙面より大きく、かつ分割できない内容(画像、大きなフォントの1文字など)は、
縮小せず、紙面の下端で**幾何学的に切断して残りを次のページへ送ります**
(<a href="#pagebreak-rescue" class="pageref">分割できないものは、切って続ける</a>)。
他のブラウザの印刷機能ははみ出した部分を捨てますが、こちらは紙に出力する
という目的から、内容を失わないことを優先します。
切断を避けるには、内容の大きさを紙面の内容領域より小さくしてください。

#### 表のセルの高さにパーセントを指定できない

指定は無視され、内容に応じた高さになります。行の高さは行(tr要素)に対する
<span class="cssprop">height</span>で指定してください。
