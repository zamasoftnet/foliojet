## 印刷所へ入稿する

紙の印刷物を作るときに必要な設定を、はじめから終わりまで1本の手順として
まとめます。ここに書いてあることは、すべて実際に変換して確かめた値です。

### <a id="prepress-terms">仕上り・塗り足し・トンボ・用紙</a>

まず言葉を合わせます。印刷所の用語とここでの用語は次のように対応します。

| 印刷所の言い方 | ここでの言い方 | 指定 |
| --- | --- | --- |
| 仕上りサイズ(断裁後の寸法) | 印刷面 | <span class="cssprop">size</span>(<tt>@page</tt>)、<span class="ioprop">output.page-width</span>・<span class="ioprop">output.page-height</span> |
| 塗り足し(断裁位置の外へ伸ばす色) | ドブへ伸ばした内容 | <span class="cssprop">bleed</span>(<tt>@page</tt>)、<span class="ioprop">output.htrim</span>・<span class="ioprop">output.vtrim</span> |
| トンボ | トンボ | <span class="cssprop">marks</span>(<tt>@page</tt>)、<span class="ioprop">output.marks</span> |
| 用紙 | 用紙 | 指定しなければ<b>印刷面＋裁ち口</b>で自動 |

<div title="ページのレイアウト" class="figure">
	<object data="images/page-layout.svg" type="image/svg+xml"
		style="width: 120mm;" />
</div>

<div class="note">
<p>
<b>いちばん多い間違い。</b>
塗り足し込みの寸法(A4なら216mm×303mm)を<span class="cssprop">size</span>に書かないでください。
<span class="cssprop">size</span>は<b>仕上りサイズ</b>として扱うので、
トンボが塗り足しの線に打たれてしまい、印刷所はそこで断裁します。
<b>仕上りサイズを書き、塗り足しは<span class="cssprop">bleed</span>で足す</b>のが正解です。
</p>
</div>

### <a id="prepress-recipe">A4・塗り足し3mm・トンボ付き・CMYK</a>

いちばん使う設定です。CSSはこれだけで足ります。

```css
@page {
	size: 210mm 297mm;   /* 仕上りサイズ。塗り足しは足さない */
	bleed: 3mm;          /* 塗り足し。用紙は216mm×303mmになる */
	marks: crop cross;   /* コーナートンボとセンタートンボ */
	margin: 15mm;        /* 版面の余白 */
}
```

入出力プロパティは3つです。

| プロパティ | 値 | 意味 |
| --- | --- | --- |
| <span class="ioprop">output.color</span> | <tt>cmyk</tt> | すべての色をCMYKへ変換します |
| <span class="ioprop">output.pdf.version</span> | <tt>1.4</tt>など | 印刷所の指定に合わせます |

この設定でできる用紙は<b>230mm×317mm</b>です。仕上り(210×297mm)の外側に
1cmの裁ち口があり、その内側3mmが塗り足し、残りにトンボが引かれます。
塗り足しだけでトンボが要らない場合(<span class="cssprop">marks</span>を書かない場合)は、
用紙は<b>216mm×303mm</b>(仕上り＋塗り足しだけ)になります。

CSSを触れない既存データでは、<tt>@page</tt>の代わりに入出力プロパティでも同じことができます
(<span class="ioprop">output.marks</span>=<tt>both</tt>、
<span class="ioprop">output.htrim</span>・<span class="ioprop">output.vtrim</span>=<tt>3mm</tt>)。
<span class="cssprop">bleed</span>が<tt>auto</tt>(既定)のときは、これらの入出力プロパティに従います。

### <a id="prepress-color">色の変換と出力インテント<span class="since">4.0.0</span></a>

<span class="ioprop">output.color</span>=<tt>cmyk</tt>とPDF/X-1a(<tt>1.4X-1</tt>)では、
RGBの色を**出力インテントのICCプロファイルで**CMYKへ変換します(3.xの計算式から変更)。
印刷所の指定するプロファイル(日本ではJapan Color 2001 Coatedが普通)を
<span class="ioprop">output.pdf.output-intent.icc-profile</span>で渡してください。
指定が無いと同梱のISO Coated v2 300% (ECI)を使います。詳しくは
[出力インテント](#style-pdf-output-intent)と[RGBからCMYKへの変換](#style-pdf-cmyk-conversion)。

覚えておくこと:

- 文字や罫線の黒(R=G=B)はKだけになります。**ぼかし(<span class="cssprop">box-shadow</span>・
  <span class="cssprop">filter</span>)やSVGの効果の中の黒は4色(リッチブラック)**になります。
  入稿先が嫌う場合はぼかしを使わないでください。
- 写真をCMYKで支給されたら(4成分JPEG)、そのまま埋め込まれます。再変換はしません。
- RGBのまま渡してよい印刷所なら<tt>1.6X-4</tt>を<span class="ioprop">output.color</span>なしで。
  RGBはICC付き(sRGB)で残り、印刷所側で変換されます。

### <a id="prepress-bleed">塗り足しまで背景を届かせる</a>

**塗り足しは「用紙を大きくすること」ではなく「内容を仕上り線の外まで描くこと」です。**
<span class="cssprop">bleed</span>を書けば、その幅だけ仕上り線の外まで描けるように
なります<span class="since">4.0.0</span>。あとは<b>内容を自分で仕上り線の外へ出す</b>だけです。

絶対配置の基準は<b>版面</b>(<tt>@page</tt>のマージンの内側)なので、
マージンのぶんも戻します。マージン15mm・塗り足し3mmなら<tt>-18mm</tt>です。

```css
@page { size: 210mm 297mm; bleed: 3mm; marks: crop cross; margin: 15mm; }
body { margin: 0 }

/* 天面いっぱいの帯。左右と上を塗り足しまで伸ばす */
.fullbleed-top {
	position: absolute;
	left: -18mm;         /* マージン15mm + 塗り足し3mm */
	top: -18mm;
	width: 216mm;        /* 210mm + 3mm + 3mm */
	height: 53mm;        /* 見せたい高さ + 3mm */
	background: #003366;
}
```

実測(A4・塗り足し3mm・トンボあり、用紙230mm×317mm。画素を測定):

| 位置 | 実測 |
| --- | --- |
| 仕上り線 | 用紙の端から10.0mm |
| 帯の左端 | <b>7.15mm</b>(仕上り線の2.85mm外=塗り足し) |
| 帯の右端 | <b>222.85mm</b>(同じく2.85mm外) |
| トンボ | 塗り足しのさらに外の白い帯に引かれる |

<tt>@page</tt>へ指定した背景は<b>仕上りサイズいっぱい</b>——マージンの内側だけでなく
用紙の端まで——塗りますが、<b>塗り足しには届きません</b>。
<tt>body</tt>や<tt>html</tt>の背景は<b>版面まで</b>です(ブラウザの印刷と同じ)。
塗り足しへ届かせたい要素は、上のように<b>自分で仕上り線の外へ出す</b>必要があります。

<div class="note">
<p>
内容をトンボのさらに外側(用紙の端)まで描きたいときだけ、
<span class="ioprop">output.clip</span>を<tt>false</tt>にしてください。
既定(<tt>true</tt>)では<b>仕上り線＋塗り足し</b>で切り落とします。
</p>
</div>

### <a id="prepress-existing">塗り足し込みで作られた既存データ</a>

他のツールで作った・以前作ったデータが、<b>塗り足しを含んだ大きさ</b>で
組まれていることがあります。A4の仕上りなのにページが216mm×303mmで、
外周3mmが塗り足し——というかたちです。

このときCSSを書き直す必要はありません。
<span class="ioprop">output.trim-inset</span>に<b>外周の帯の幅</b>を
渡してください<span class="since">4.0.0</span>。仕上り線は印刷面の外周から
その幅だけ内側にあるものとして扱われ、トンボはそこに引かれます。

```
output.trim-inset: 3mm
output.marks: crop cross
output.color: cmyk
```

| | 指定なし | <tt>output.trim-inset: 3mm</tt> |
| --- | --- | --- |
| 仕上りサイズ | 216mm×303mm(塗り足し込みのまま) | <b>210mm×297mm</b> |
| トンボの位置 | 印刷面の外周 | 印刷面から3mm内側 |
| 塗り足し | 無し(外周がそのまま仕上り) | 外周の3mm |
| 用紙(トンボなし) | 216mm×303mm | <b>210mm×297mm</b>(塗り足しは断ち落とし) |

トンボを付けない(<span class="ioprop">output.marks</span>が<tt>none</tt>)ときは、
用紙が仕上りサイズちょうどになり、塗り足しは切り落とされます——
つまり<b>断裁後の見た目</b>がそのまま出ます。校正に使えます。

<div class="note">
<p>
<span class="ioprop">output.trim-inset</span>を指定したときは、
CSSの<span class="cssprop">bleed</span>は無視します。塗り足しの実体は
すでに印刷面の中にあり、両方を効かせると二重になるためです。
</p>
</div>

### <a id="prepress-check">入稿前の確認</a>

出来上がったPDFは、次の3点を機械的に確かめられます。

<dl>

<dt>用紙の寸法</dt>
<dd>
	PDFの<tt>MediaBox</tt>が「仕上り＋裁ち口×2」になっているか。
	A4・塗り足し3mm・トンボありなら<tt>[0 0 651.97 898.58]</tt>(230mm×317mm)、
	トンボなしなら<tt>[0 0 612.28 858.90]</tt>(216mm×303mm)です。
</dd>

<dt>色</dt>
<dd>
	<span class="ioprop">output.color</span>=<tt>cmyk</tt>で出したPDFの
	コンテンツストリームには、RGBの<tt>rg</tt>演算子が現れず、
	CMYKの<tt>k</tt>演算子だけが現れます。画像も<tt>/DeviceCMYK</tt>です。
	PDF/X(<tt>1.4X-1</tt>・<tt>1.6X-4</tt>)の適合は、Acrobat Proのプリフライトなど
	外部の検査器で最終確認してください。
</dd>

<dt>塗り足し</dt>
<dd>
	画像出力(<span class="ioprop">output.type</span>=<tt>image/png</tt>)で
	同じ文書を出し、<b>仕上り線のすぐ外側</b>の画素が白でないことを見ます。
	白ければ塗り足しが届いていません。
</dd>

</dl>

### <a id="prepress-imposition">面付け</a>

複数ページを1枚の用紙へ並べる面付けは<a href="#style-imposition" class="pageref">面付け</a>を
参照してください。
面付けした用紙にもトンボを付けられます。
