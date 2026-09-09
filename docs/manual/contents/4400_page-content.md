## ページ処理機能

### <a id="style-page-layout">ページのレイアウト</a>

生成されるページは以下の部分から構成されています。

<dl>

<dt>用紙</dt>
<dd>ドキュメントが印刷される用紙です。</dd>
<dt>印刷面</dt>
<dd>ドキュメントの内容が印刷される部分です。</dd>
<dt>マージン</dt>
<dd>通常は内容がみだすことのない、ページの余白部分です。</dd>
<dt>トンボ</dt>
<dd>製本する際、断裁の目印となる印です。</dd>
<dt>裁ち口</dt>
<dd>断裁されて切り落とされる部分です。</dd>
<dt>ドブ</dt>
<dd>断裁される可能性のある範囲です。</dd>

</dl>

<div title="ページのレイアウト" class="figure">
	<object data="images/page-layout.svg" type="image/svg+xml"
		style="width: 120mm;" />
</div>

ページのレイアウトは入出力プロパティにより設定されます。 対応する入出力プロパティは次の通りです。

<dl>

<dt>用紙のサイズ</dt>
<dd>
	<span class="ioprop">output.paper-width</span>, <span class="ioprop">output.paper-height</span>
</dd>
<dt>印刷面のサイズ</dt>
<dd>
	<span class="ioprop">output.page-width</span>, <span class="ioprop">output.page-height</span>
</dd>
<dt>マージン</dt>
<dd>
	<span class="ioprop">output.page-margins</span>
</dd>
<dt>トンボ</dt>
<dd>
	<span class="ioprop">output.marks</span>
</dd>
<dt>裁ち口</dt>
<dd>
	<span class="ioprop">output.htrim</span>, <span class="ioprop">output.vtrim</span>
</dd>

</dl>

用紙のサイズの指定がない場合、用紙の幅と高さは、 それぞれページの幅と高さに断ち口の幅を足したものに自動的に設定されます。

用紙のサイズを指定して印刷面と大きさが違うとき、どう配置するかは
<span class="ioprop">output.fit-to-paper</span> で決まります。

<dl>

<dt>false(既定)</dt>
<dd>印刷面をそのままの大きさで用紙の中央に置きます。印刷面のほうが大きければ、はみ出した部分は用紙に入りません。</dd>
<dt>true</dt>
<dd>印刷面を用紙いっぱいに合わせます。<b>倍率は「用紙÷印刷面」なので、印刷面のほうが大きければ縮小になります。</b>
	縦横で倍率が異なると縦横比が変わります。</dd>
<dt>preserve-aspect-ratio</dt>
<dd>縦横比を保ったまま用紙に合わせます。こちらも<b>拡大にも縮小にもなります。</b></dd>

</dl>

`@page`規則では、余白のほかに<span class="cssprop">border</span>と
<span class="cssprop">padding</span>も指定できます <span class="since">4.0.0</span>。
要素の箱と同じ規則で余白の内側に枠線と内側余白を取り、本文の版面はその内側になります。
`@page`の<span class="cssprop">background</span>は用紙全面(塗り足しがあればその帯まで)に描かれます。

```css
@page {
  margin: 15mm;
  border: 0.5pt solid #888;
  padding: 3mm;
}
```

#### <a id="style-fit-wide-page">紙より広く作られたページを用紙に収める</a>

Webページには「幅980ピクセル」のように、紙の幅を考えずに固定幅で作られたものが多くあります。
そのまま変換すると、指定どおりに組まれた結果として右側が用紙からはみ出します。

このようなときは、<b>内容が収まる大きさの印刷面で組み、実際の用紙へ縮めて収めます。</b>
印刷面と用紙を別々に指定できるのはこのためです。

```
output.page-width   = 280mm    # 980ピクセルが収まる広さで組む
output.page-height  = 396mm    # 用紙と同じ縦横比にしておく
output.paper-width  = 210mm    # 実際の用紙(A4)
output.paper-height = 297mm
output.fit-to-paper = preserve-aspect-ratio
```

印刷面の大きさは「収めたい内容の幅」から決めます。ピクセル指定の内容であれば、
<span class="ioprop">output.resolution</span>(既定96)で1インチあたりのピクセル数が決まるので、
980ピクセルは 980÷96=10.2インチ=259mm です。マージンのぶんを足して280mmとしています。
印刷面の縦横比を用紙と揃えておくと、縮めたときに余白が偏りません。

<span class="ioprop">output.resolution</span> を大きくして、
ピクセルあたりの長さを縮める方法もあります。ただしこちらは<b>行の折り返しが組み直される</b>ため、
内容が用紙に収まる保証はありません。確実に収めたい場合は上の方法を使ってください。
文字の大きさだけを変えたい場合は <span class="ioprop">output.text-size</span> があります。

<span class="ioprop">output.page-margins</span>
の設定はページマージンのデフォルト値として使用されるものです。 ページのマージンは、CSSの <span class="cssprop">margin-top</span>,
<span class="cssprop">margin-right</span>, <span class="cssprop">margin-bottom</span>,
<span class="cssprop">margin-left</span>
プロパティにより@pageルール内で上書きすることができます。

### <a id="style-page-size">ページの大きさの制約と切り落とし</a>

既定では、ページの高さは297mm、ページの幅は210mm(A4サイズ)です。
上下左右のマージンはいずれも12.7mm(3pc)です。

用紙の縦横の長さはいずれも1ptから14400pt(5080mm)の 間である必要があります。
用紙サイズがこの範囲を超えた場合は、超えた部分は切り落とされます。 このサイズ制限はPDFの仕様によるものです。

また、<span class="ioprop">output.auto-height</span>をtrueに設定することで、
用紙の高さは固定されずに、内容に合わせて拡張されるようになります。 この場合、改ページが発生することはありませんが、
前記の用紙サイズの制限により切り落とされることがあります。

用紙サイズを固定し、かつ改ページが行われないようにするためには <span class="ioprop">output.no-page-break</span>をtrueに設定してください
<span class="since">2.0.3</span>。 この場合、固定された用紙サイズをはみ出した部分は切り落とされます。

印刷内容の切り落としは、デフォルトでは<b>仕上り線＋塗り足し(断ち代)</b>の位置で行われます。 塗り足しの幅は<tt>@page</tt>の<span class="cssprop">bleed</span>、または<span class="ioprop">output.marks</span>を指定したときの既定値(3mm)です<span class="since">4.0.0</span>。 トンボのさらに外側、用紙の端まで内容を印刷する場合は、 <span class="ioprop">output.clip</span>をfalseに設定してください <span class="since">2.0.3</span>。 入稿用の設定は<a href="#prepress-terms" class="pageref">印刷所へ入稿する</a>にまとめてあります。

#### <a id="style-auto-rotate">縦長・横長が合わないとき</a>

用紙と内容の向きが合わない場合(A4縦の用紙に横長の表を入れるなど)は、
<span class="ioprop">output.auto-rotate</span>で自動的に回転させることができます。

<dl>

<dt>none</dt>
<dd>回転しません(既定)。</dd>
<dt>content</dt>
<dd><b>内容を90度回転</b>して用紙に合わせます。用紙の向きは変わりません。
	用紙のサイズを変えられない(決まった用紙に印刷する)場合に使います。</dd>
<dt>paper</dt>
<dd><b>用紙の向きを入れ替えて</b>内容に合わせます。内容は回転しません。</dd>

</dl>

#### <a id="style-expand-with-content">内容の分だけ紙を伸ばす</a>

<span class="ioprop">output.expand-with-content</span>をtrueにすると、
内容がページに収まらない場合に**ページがその分だけ広がります**。
帳票の明細行が何行になるか分からない場合など、
紙の大きさを内容に合わせたいときに使います。

<span class="ioprop">output.auto-height</span>との違いは、
<span class="ioprop">output.auto-height</span>が
**最初から高さを固定しない**のに対し、
<span class="ioprop">output.expand-with-content</span>は
**指定した大きさを下限として、はみ出した分だけ広げる**点です。

### <a id="style-imposition">面付け<span class="since">4.0.0</span></a>

**面付け**は、1枚の用紙に複数の論理ページを並べて配置することです。
用紙を節約する、あるいは印刷後に断裁して小冊子や伝票にする、といった用途に使います。

<span class="ioprop">output.n-up</span>に、1枚に並べる論理ページ数を指定します。
1(既定)なら面付けを行いません。

```
session.property("output.n-up", "4");
```

#### 用紙と縮小率

用紙サイズを指定していない場合、用紙は「論理ページ + 断ち代」と同じ大きさになり、
各ページが1/Nに縮小されて並びます。一般的なプリンタのN-up印刷と同じ動作です。

用紙サイズを指定した場合は、その断ち代の内側に格子を組みます。

**格子の行数と列数は自動的に決まります。**
そのシートの先頭ページの縦横比を見て、いちばん大きく配置できる組み合わせが選ばれます。
行数・列数を直接指定することはできません。

#### 並べる順序

<span class="ioprop">output.n-up.order</span>で並び順を指定します。

<dl>

<dt>horizontal(既定)</dt>
<dd>左から右へ並べ、右端まで行ったら次の行の左端へ移ります。</dd>
<dt>horizontal-reverse</dt>
<dd>右から左へ並べます。右綴じの文書に使います。</dd>
<dt>vertical</dt>
<dd>上から下へ並べ、下端まで行ったら次の列の上端へ移ります。</dd>
<dt>vertical-reverse</dt>
<dd>上から下へ並べ、列は右から左へ進みます。</dd>

</dl>

6ページを3列2行に並べた場合、番号の入り方は次のようになります。

<div title="面付けの並び順" class="figure">
<object data="images/nup-order.svg" type="image/svg+xml"
	width="480" height="264">面付けの並び順</object>
</div>

#### トンボと断ち代

面付けした用紙にもトンボを付けられます。
<span class="ioprop">output.marks</span>で種類を指定します。

| 値 | 内容 |
| --- | --- |
| none | 付けません(既定) |
| crop | コーナートンボ(隅の断裁位置) |
| cross | センタートンボ(辺の中央) |
| both | 両方 |
| hidden | 位置は確保するが描きません |

断裁で切り落とす幅は<span class="ioprop">output.htrim</span>(左右)と
<span class="ioprop">output.vtrim</span>(上下)で指定します。
辺ごとに変える場合は<span class="ioprop">output.trims</span>を使います。

入力データに既に塗り足しが含まれている場合は、
<span class="ioprop">output.trim-inset</span>で仕上り線の位置を教えます
<span class="since">4.0.0</span>。詳細は[印刷所へ入稿する](#prepress-existing)を
参照してください。

<span class="ioprop">output.marks.spine-width</span>に背表紙の幅を指定すると、
背表紙の位置を示す線が引かれます。

#### 向きが合わないとき

用紙と論理ページの向きが合わない場合は、
<span class="ioprop">output.auto-rotate</span>で回転させられます。
詳細は[ページのレイアウト](#style-page-layout)を参照してください。

<div class="note">

いわゆる**カット&スタック面付け**(断裁後に重ねると順番が揃う並べ方)には
対応していません。指定できるのは上の4種類の順序だけです。

</div>

### <a id="style-multipass">2パス以上の変換処理</a>

#### <a id="style-multipass-why">なぜ複数回処理する必要があるのか</a>

**文書を先頭から終わりまで一度だけ読み、読みながら組んでいきます**。
読み終えた分から順にページを組み上げ、組み上がったページから順に出力します。
文書全体をメモリに載せてから考える、という作り方はしていません。

この作り方には理由があります。

<dl>

<dt>大きな文書を扱える</dt>
<dd>必要なメモリが、文書の大きさではなく<b>組んでいるページの大きさ</b>で
	決まります。10万ページの帳票でも、1ページの帳票と同じくらいのメモリで処理できます。</dd>
<dt>最初のページがすぐ出る</dt>
<dd>1ページ目が組み上がった時点で送り出せます。
	利用者は文書全体の処理を待たずに読み始められます。</dd>
<dt>サーバーが同時に多くの変換を捌ける</dt>
<dd>1件あたりのメモリが小さく一定なので、同時に走らせる本数を見積もれます。</dd>

</dl>

そのかわり、**避けられない制約**が生まれます。

> **まだ読んでいない部分に答えがあることは、その場では決められない。**

いくつか例を挙げます。

- 「この文書は全部で何ページか」——最後まで組まないと分かりません
- 「この見出しは何ページ目に出るか」——目次を組む時点では、まだ本文を
	読んでいません
- 「この要素は親の最後の子か」(`:last-child`)——後ろにまだ兄弟が
	現れるかもしれません
- 「この要素は子孫にリンクを含むか」(`:has(a)`)——その要素を閉じるまで
	分かりません

文書全体をメモリに溜めてから組めばこれらは解決しますが、
上に挙げた3つの利点をすべて失います。
**大きな文書が扱えなくなり、最初のページが出るまで待たされ、
サーバーの同時処理数が読めなくなります。**

そこで、溜め込むかわりに**同じ文書をもう一度読む**という
方法をとります。

表や浮動体のように、寸法が決まるまで中身を溜めておく要素はあります。
その溜め込みには要素1つあたりの上限(<span class="ioprop">processing.retained-text-limit</span>、既定8MB)があり、
超えると変換は失敗します。1件の変換が使うメモリに天井を置くためのもので、出力の内容は変わりません。

#### <a id="style-multipass-what">パスとは何か</a>

**文書を頭から終わりまで一度読み切ることを「パス」と呼びます。**

複数パスの考え方は単純です。

1. **1回目**は、結果を出さずに最後まで組みます。
	組んでいく途中で「この見出しは3ページ目に出た」「この要素には子が無かった」
	といった**事実を記録**します
2. **2回目**は、同じ文書をもう一度頭から読みます。
	今度は1回目で記録した事実が手元にあるので、
	目次にページ番号を入れたり、`:last-child`を正しく判定したりできます。
	この回の結果が出力されます

<div title="2パスの流れ" class="figure">
<object data="images/two-pass.svg" type="image/svg+xml"
	width="440" height="275">2パスの流れ</object>
</div>


一度読みの利点(一定のメモリ・逐次処理)は各パスの中では保たれたまま、
「後の情報」だけを前の回から持ち越す、という形です。

回数は<span class="ioprop">processing.pass-count</span>で指定します(既定は1)。

```
session.property("processing.pass-count", "2");
```

##### 目次を例に

文書の先頭に目次を置く場合を追ってみます。

<dl>

<dt>1パスだけの場合</dt>
<dd>目次を組む時点では本文をまだ1文字も読んでいません。
	どの見出しが何ページに出るかを知りようがないので、
	<b>ページ番号を入れられません</b>。</dd>
<dt>2パスの場合</dt>
<dd>1回目で本文を最後まで組み、各見出しが出たページ番号を記録します。
	2回目では目次を組む時点でその表が手元にあるので、番号を埋められます。</dd>

</dl>

ここで**もう一段の問題**が起きます。目次にページ番号を入れると、
目次自体の行数が変わることがあります。目次が1行増えれば本文の開始位置が
1行ずれ、ずれた結果、見出しのページ番号がまた変わってしまいます。
このときは3回以上にして、数字が落ち着くまで繰り返します。
文書の**末尾**に目次を置く場合は、目次より前の本文はもう組み終わっているので
この問題は起きません。

<div class="note">

**自動では切り替わりません。**
2パスが必要な機能を使っていても、それを検出して勝手に
パス数を増やしたりはしません。**設定していなければ、警告も出ないまま
その機能が働かない**だけです。これは仕様です。

理由は上の説明そのものです。「2パスが必要かどうか」の判定にも文書全体を
読む必要があり、それを自動でやろうとすると、結局は文書を溜め込むことに
なってしまいます。

</div>

#### <a id="style-multipass-required">2パスが必要な機能</a>

次の機能は<span class="ioprop">processing.pass-count</span>に2以上を
設定しないと働きません。

**セレクタ**

| セレクタ | 理由 |
| --- | --- |
| `:last-child` `:only-child` | 後ろに兄弟がもう無いことを確かめる必要がある |
| `:last-of-type` `:only-of-type` | 同上 |
| `:nth-last-child()` `:nth-last-of-type()` | 末尾から数える |
| `:empty` | 子が1つも無いことを、閉じるまで確かめる必要がある |
| `:has()` | 子孫に該当するものがあるかを、部分木を読み終えるまで確かめる必要がある |

`:not()`の中にこれらを書いた場合も同じです。
これらのセレクタは、設定が足りないと**「一致しないセレクタ」として
静かに無視されます**。スタイルが当たらないときは、まずパス数を疑ってください。

**カウンタと参照**

| 機能 | 理由 |
| --- | --- |
| `counter(pages)` | 総ページ数は最後まで組まないと決まらない |
| [目次の生成](#style-xml-toc)(cssj:make-toc要素) | 各見出しのページ番号が必要 |
| [ページの参照](#style-page-references)(-cssj-page-ref関数) | 参照先のページ番号が必要 |

目次とページ参照については、あわせて
<span class="ioprop">processing.page-references</span>をtrueにしてください。

**本文の後方に現れるスタイルシート**<span class="since">4.0.0</span>

| 機能 | 理由 |
| --- | --- |
| body内の後方の`<style>`要素の全体への適用 | 読んだ時点より前の要素はもう組み終わっており、遡って適用できない |

1パスでは、body内に書かれた`<style>`はそれ以降の要素にしか適用されません
(headのスタイルシートは常に全体へ適用されます)。
2パス以上では、前のパスで集めたスタイルシートを次のパスへ持ち越すため、
文書のどこに書かれたスタイルシートも文書全体へ適用されます。
SSRフレームワークが生成する、本文中に`<style>`が散らばったHTMLは
こちらの動作でないと正しく組めないことがあります。

#### 何回にすればよいか

**2回で足りることがほとんどです。**

3回以上が要るのは、[目次を例に](#style-multipass-what)で説明した
「入れた番号のせいで番号が変わる」場合です。
文書の先頭や途中に目次を置くときに起こります。
番号が落ち着くまで回数を増やしてください。

回数を増やせばそのぶん処理時間もほぼ比例して増えます。

#### 2パスにすると何が変わるか

**文書が一時ファイルに保存されます。** 入力は一度しか読めないため、
1回目の読み込みでテンポラリファイルへ写し取り、以降のパスはそこから読みます。
ディスクの空き容量と、一時ファイルを作れる権限が必要です。

**実際には指定した回数より1回多く読みます。**
2パス以上を指定すると、レイアウトを一切行わない軽量な事前走査が先に1回入ります。
上の表のセレクタはここで解決します。この走査は
<span class="ioprop">processing.pass-count</span>の回数には数えません。

**最初のページが出るまでの時間が延びます。** 最後のパスまで結果を出さないため、
1パスのときのように先頭ページから順次出力することはできません。

#### <a id="style-multipass-middle-pass">複数の文書にまたがる複数パス</a>

<span class="ioprop">processing.middle-pass</span>
<span class="since">3.0.4</span>は、上の
<span class="ioprop">processing.pass-count</span>とは**別の仕組み**です。

<span class="ioprop">processing.pass-count</span>が
「1つの文書を内部で複数回処理する」のに対し、
<span class="ioprop">processing.middle-pass</span>は
**アプリケーション側が文書を複数回送る**ためのものです。
複数の文書を1つのPDFにまとめる場合など、
アプリケーションがパスの制御を持ちたいときに使います。

結果を出力しない回(最初と中間)は
<span class="ioprop">processing.middle-pass</span>をtrueにし、
最後の回だけfalseにしてから文書を送ってください。

<div class="note">

2つの仕組みは名前が似ていますが混同しないでください。
1つの文書を扱うだけなら<span class="ioprop">processing.pass-count</span>を
使います。<span class="ioprop">processing.middle-pass</span>が要るのは、
**アプリケーションが文書を送る回数を自分で決める**場合だけです。

</div>

### <a id="style-page-references">ページの参照</a>

[目次をつくる機能](#style-xml-toc)(cssj:make-toc要素)と、
ある内容が印刷される[ページ番号を表示する機能](#style-cssj-page-ref)(-cssj-page-ref関数)があります。

これらの機能を利用するためには、 <span class="ioprop">processing.page-references</span>
をtrueに設定し、ページ参照情報を収集する機能を有効にしてください。 また、必要に応じて[2パス以上の変換処理](#style-multipass)を行ってください。

### <a id="style-gray">グレイスケール印刷</a>

原則として結果をカラーで出力しますが、
グレイスケールで印刷した状態をプレビューするために、グレイスケールに変換する機能を持っています。

グレイスケールの出力結果を得るためには、 <span class="ioprop">output.color</span>をgrayに設定してください

### <a id="style-print-mode">片面印刷と両面印刷</a>

既定ではページ生成は横書き・両面印刷として行われます。
従って、CSSの@pageルールにおいて、最初のページは:firstまたは:right擬似要素として扱われ、 以降は:left,
:right擬似要素のページが交互に現れます。

<span class="ioprop">output.print-mode</span>
にsingle-sideを設定することにより、片面印刷に切り替えることができます。
片面印刷では最初のページは:first擬似要素として扱われ、以降はどの擬似要素にも属さないページが生成されます。

### <a id="style-page-margin-boxes">ページのマージンボックス<span class="since">4.0.0</span></a>

`@page`規則の中に**マージンボックス**を書くと、紙面の余白に内容を配置できます。
ノンブル(ページ番号)や柱(ランニングヘッダー)を出すための標準的な方法です。

次の16個のボックスを使用できます。

| 位置 | ボックス |
| --- | --- |
| 上 | `@top-left-corner` `@top-left` `@top-center` `@top-right` `@top-right-corner` |
| 下 | `@bottom-left-corner` `@bottom-left` `@bottom-center` `@bottom-right` `@bottom-right-corner` |
| 左 | `@left-top` `@left-middle` `@left-bottom` |
| 右 | `@right-top` `@right-middle` `@right-bottom` |

紙の上での位置は次のとおりです。

<div title="マージンボックスの配置" class="figure">
<object data="images/margin-boxes.svg" type="image/svg+xml"
	width="330" height="390">マージンボックスの配置</object>
</div>

```css
@page {
	margin: 2cm;
	@bottom-center {
		content: counter(page);
	}
}
```

マージンボックスの中では次のものが使用できます。

- `content`の文字列、`counter()`・`counters()`(ページレベルのカウンタ、
	すなわち`page`・`pages`と`@page`の`counter-*`で作ったもの)、
	`string()`(下記)
- フォント、色、`text-align`、`vertical-align`(top、middle、bottom)
- `margin`、`border`、`padding`、`background`
- `writing-mode`と`text-orientation`(縦書きの柱。[下記](#style-vertical-side-boxes))

`background`と`border`はボックスに割り当てられた領域全体(余白の帯いっぱい)に描かれます。内容の大きさには合わせません。
`vertical-align`と`text-align`は内容だけを動かします。

次のものには対応していません。`url()`による画像、引用符、`attr()`、
`-cssj-page-ref()`、`leader()`、および`width`・`height`による大きさの指定です
(ボックスの大きさは内容と余白から決まります)。

#### <a id="style-running-heading">柱(ランニングヘッダー)</a>

`string-set`プロパティで見出しの内容を名前付き文字列へ取り込み、
`string()`関数でマージンボックスへ出力します。

```css
h1 { string-set: chapter content(); }

@page {
	margin: 2cm;
	@top-center {
		content: string(chapter, first);
	}
	@bottom-center {
		content: counter(page) ' / ' counter(pages);
	}
}
```

`string()`の2つめの引数は、そのページのどの値を使うかを指定します。

<dl>

<dt>first</dt>
<dd>そのページで最初に設定された値です。省略時はこれになります。</dd>
<dt>last</dt>
<dd>そのページで最後に設定された値です。</dd>
<dt>start</dt>
<dd>そのページの開始時点の値です。</dd>
<dt>first-except</dt>
<dd>firstと同じですが、その値が設定されたページでは空になります。
	章の1ページ目には柱を出さない、という用途に使います。</dd>

</dl>

そのページで一度も設定されなければ、いずれも前のページから引き継いだ値になります。

#### <a id="style-vertical-side-boxes">縦組みの柱を小口に置く<span class="since">4.0.0</span></a>

縦組みの本では、柱を左右の余白(小口)に縦書きで置きます。左右のマージンボックスに
`writing-mode: vertical-rl`を指定すると、そのボックスの中だけ縦書きになります。
ノンブルは横書きのままにできます。

```css
html { writing-mode: vertical-rl; }
h2 { string-set: chapter content(); }

@page :left {
	margin: 12mm 15mm 11mm 14mm;   /* 右がのど、左が小口 */
	@left-top {
		content: string(chapter);
		writing-mode: vertical-rl;
		text-orientation: upright;
		font-size: 7.5pt;
	}
	@left-bottom { content: counter(page); }
}
@page :right {
	margin: 12mm 14mm 11mm 15mm;
	@right-top {
		content: string(chapter);
		writing-mode: vertical-rl;
		text-orientation: upright;
		font-size: 7.5pt;
	}
	@right-bottom { content: counter(page); }
}
```

縦書きのボックスの中では、次のように配置されます。

- 行は余白の高さいっぱいに使えます(上・中・下の3つのボックスで分け合います)。
	1行に収まらないときだけ、隣の行へ折り返します。
- `vertical-align`が**天地**の寄せです。`@left-top`・`@right-top`は天付き(top)、
	`@left-bottom`・`@right-bottom`は地付き(bottom)、`@left-middle`・`@right-middle`は
	天地中央(middle)が既定です。
- 左右方向には、余白の幅の中央に置かれます。`padding`と`margin`で内容の箱を狭めると、
	その箱の中央へ動きます。紙の端や本文に寄せたいときは、寄せたくない側に`padding`を入れてください。
- `text-align`は縦書きのボックスでは効きません(天地の寄せは`vertical-align`で行います)。
- 天地方向(`margin-top`・`margin-bottom`)の`margin`は絶対長で指定してください。百分率は0として扱われます。

<div class="note">

`counter(pages)`(総ページ数)は文書全体を組み終わらないと決まらないため、
<span class="ioprop">processing.pass-count</span>に2以上を設定してください。
<a href="#style-multipass" class="pageref">2パス以上の変換処理</a>を参照してください。

title要素は表示されないため、`string-set`の対象にできません。

</div>

#### <a id="style-running-elements">要素をページごとに繰り返す(running element)<span class="since">4.0.0</span></a>

柱に**要素そのもの**(表・画像・複数行の見出しなど、文字列に潰せないもの)を
出したいときは、CSS GCPM の running element を使います。
要素に<span class="cssdecl">position: running(名前)</span>を指定すると、その要素は本文から取り除かれて
名前付きのテンプレートになり、マージンボックスの<span class="cssdecl">content: element(名前)</span>で
ページごとに描かれます。

```css
.chapter-head { position: running(chapter); }

@page {
	@top-center { content: element(chapter); }
}
```

`element()`の2つめの引数は`string()`と同じ`first`/`last`/`start`/`first-except`で、
そのページのどの要素を使うかを選びます(省略時は`first`)。
同じ名前を同じページの複数のマージンボックスから参照できます。

<dl>

<dt>スタイルは元の位置で決まります</dt>
<dd>テンプレートは本文の中でカスケードされたスタイルを持ち、ページごとに再計算はしません。
	`@page :left`と`@page :right`で見た目を変えたいときは、左右のマージンボックスで別の名前を参照するか、
	左右別の要素にしてください。</dd>
<dt>生成内容は表示されるページの値で評価します</dt>
<dd>テンプレートの中の`counter(page)`・`string()`・`target-counter()`は、テンプレートを描くページの値になります
	(他の組版エンジンは取り込んだ時点の値で固定するものがあります。独自の拡張です)。
	`counter-increment`など本文のカウンタを変える宣言は、テンプレートの中では実行しません。</dd>
<dt>収まらない内容ははみ出します</dt>
<dd>マージンボックスより大きいテンプレートは切り落とさず、縮めもしません。</dd>
<dt>取り込める量には上限があります</dt>
<dd>1つのテンプレートにつき10,000要素・100KBの文字・50個の画像参照までです。
	超えると警告を出して取り込みません。</dd>

</dl>

<div class="note">

`element()`はマージンボックスの`content`でだけ使えます。通常の要素や`::before`/`::after`に書くと
警告を出して無視します。テンプレートの中のリンク・しおり・フォーム・id参照は、繰り返し描かれる側では登録しません。

</div>

### <a id="style-nombre">ノンブル(ページ番号)の付け方</a>

「ノンブル」は紙面に振るページ番号のことです。
ここでは、よくある付け方をそのまま使える形で並べます。
仕組みの詳細は[ページのマージンボックス](#style-page-margin-boxes)と
[ページカウンタ](#style-page-counter)を参照してください。

#### いちばん単純な例

紙面の下中央に番号だけを振ります。

```css
@page {
	margin: 2cm;
	counter-increment: page;
	@bottom-center {
		content: counter(page);
	}
}
```

`counter-increment: page;`はページごとに番号を1つ進める宣言です。
`counter(page)`が現在のページ番号になります。

#### 「1 / 20」のように総ページ数も出す

```css
@page {
	counter-increment: page;
	@bottom-center {
		content: counter(page) ' / ' counter(pages);
	}
}
```

<div class="note">

`counter(pages)`(総ページ数)は、**文書を最後まで組まないと決まりません**。
<span class="ioprop">processing.pass-count</span>に2以上を設定してください。
設定しないと正しい数になりません。
[2パス以上の変換処理](#style-multipass)を参照してください。

</div>

#### ページの外側に振る(左右で位置を変える)

冊子に綴じる場合、ノンブルは綴じ側ではなく外側(小口側)に置きます。
左ページなら左下、右ページなら右下です。

```css
@page :left {
	@bottom-left { content: counter(page); }
}
@page :right {
	@bottom-right { content: counter(page); }
}
```

`:left`と`:right`は、両面印刷したときの左ページ・右ページを指します。
片面印刷の設定では使われません。
[片面印刷と両面印刷](#style-print-mode)を参照してください。

#### 表紙には振らない

最初のページだけ別扱いにします。

```css
@page {
	counter-increment: page;
	@bottom-center { content: counter(page); }
}
@page :first {
	@bottom-center { content: none; }
}
```

#### 前付と本文で番号を振り分ける

目次などの前付をローマ数字、本文をアラビア数字にして、
本文の先頭で番号を1に戻す例です。

```css
@page {
	counter-increment: page;
	@bottom-center { content: counter(page, lower-roman); }
}
```

```css
/* 本文の先頭でページ番号を振り直す */
#body {
	counter-reset: page 1;
}
```

本文側のページを算用数字にするには、本文のページだけ別の@page規則を
当てます。[名前付きページ](#style-named-pages)<span class="since">4.0.0</span>を使うと、
1つの文書の中で前付と本文に別々のページスタイルを適用できます。

#### 柱(見出し)も一緒に出す

ノンブルと並べて、そのページの見出しを出すこともできます。
[柱(ランニングヘッダー)](#style-running-heading)を参照してください。

<div class="note">

用紙サイズは<span class="ioprop">output.page-width</span>・
<span class="ioprop">output.page-height</span>入出力プロパティのほか、
`@page`の<span class="cssprop">size</span>プロパティ<span class="since">4.0.0</span>でも
指定できます。両方が指定された場合は`size`が優先されます。
余白は`@page`の<span class="cssprop">margin</span>で指定します。
マージンボックスはこの余白の中に置かれるので、
**余白がゼロだとノンブルを置く場所がありません**。

</div>

### <a id="style-named-pages">名前付きページ<span class="since">4.0.0</span></a>

要素に<span class="cssprop">page</span>プロパティで名前を与えると、
その要素が始まるページから、同じ名前の`@page`規則が適用されます。
名前の異なるページの境界では自動的に改ページされます。

```css
/* 前付はローマ数字・本文は算用数字のノンブル */
@page front {
  @bottom-center { content: counter(page, lower-roman); }
}
@page main {
  @bottom-center { content: counter(page); }
}
#front { page: front; }
#body  { page: main; counter-reset: page 1; }
```

`@page front:first`のように、名前と擬似クラス(:first、:left、:right)を
組み合わせることもできます。

<div class="note">

旧バージョンの独自機能`-cssj-page-content`(ページごとに生成される
コンテンツ)は4.0.0で廃止されました。ノンブルや柱は`@page`の
[マージンボックス](#style-page-margin-boxes)と
[柱(ランニングヘッダー)](#style-running-heading)で実現してください。
**廃止された機能**(サーバー製品の説明書)も参照してください。

</div>

### <a id="style-footnotes">脚注<span class="since">4.0.0</span></a>

<span class="cssdecl">float: footnote;</span>を指定した要素は、
そのページの下端に脚注として移動します。元の位置には脚注番号
(呼び出し)が残ります。

```css
.fn {
  float: footnote;
}
.fn::footnote-call {
  content: counter(footnote);
  vertical-align: super;
  font-size: smaller;
}
.fn::footnote-marker {
  content: counter(footnote) " ";
}
```

- 脚注番号は組み込みのカウンタ<span class="cssprop">footnote</span>で
  自動的に進みます。番号は**ページごとに1から**振り直します
  (呼び出しが載ったページの文書順)。文書を通した通し番号には
  現在対応していません。
- <span class="cssprop">::footnote-call</span>擬似要素が呼び出し位置の
  番号、<span class="cssprop">::footnote-marker</span>擬似要素が
  脚注本文の先頭の番号です。
- 番号のラベルの<span class="cssprop">content</span>に書けるのは文字列と
  <span class="cssdecl">counter(footnote)</span>だけです。それ以外
  (<span class="cssdecl">counter(footnote, lower-roman)</span>など)は
  警告を出したうえで無視し、番号と文字列だけで組みます。
- 脚注は**呼び出しが載ったページ**に置きます。呼び出しを含む
  ブロック(改ページを避ける図など)が次のページへ送られれば、
  脚注も一緒に移ります。呼び出しのページに脚注の場所が取れなかった
  ときは、次のページの脚注領域の先頭に置きます(番号は呼び出しの
  ページのものを保ちます)。
- 脚注の本文は、元の位置の書字方向ではなく**ページの書字方向**で
  組みます。縦組みの本文に横組みの図(<span class="cssdecl">writing-mode:
  horizontal-tb</span>)があり、その説明文に脚注があっても、脚注は
  ほかの脚注と同じく縦組みでページの脚注領域に入ります。脚注要素に
  別の<span class="cssprop">writing-mode</span>を指定しても無視します。
- **段組の中の脚注は、呼び出しがある段の末尾(ブロック方向の終端。
  縦組みなら段の左端)に、その段の行長で置きます**(横 2 段組みの本で
  一般的な置き方です)。段の本文はその段の脚注の分だけ短くなり、ほかの段や
  段組全体の寸法は変わりません。段に収まらない脚注は次の段(ページ末なら
  次のページ)へ送ります(番号は呼び出しのページの通し番号を保ちます)。
  段組がページの途中で終わるページでは、脚注はページの脚注領域(段組の後)に
  置きます。高さを指定した段組(<span class="cssprop">height</span>や
  縦組みの<span class="cssprop">width</span>が固定)と入れ子の段組の中の脚注は
  この対象外で、ページの脚注領域に置きます(段の高さが不揃いになることが
  あり、警告メッセージが出力されます)。地に横組みで置きたいときは
  <span class="cssdecl">float: bottom</span>の帯を使ってください。
- **版面より大きい脚注も変換を失敗させません。**
  空のページの脚注領域にも収まらない脚注は、警告を出したうえで
  溢れさせて配置します(版面は崩れますが、出力は得られます)。
- 呼び出しが見つからない脚注も、警告を出したうえで文書順の番号を
  振って配置します。

#### 脚注領域の位置と書字方向(<span class="cssprop">@footnote</span>)<span class="since">4.0.0</span>

縦組みの本文でも、脚注を用紙の**地**(下端)に**横書き**で置けます。
<span class="cssprop">@page</span>の中の<span class="cssprop">@footnote</span>規則で
指定します(CSS Generated Content for Paged Media Level 3 の書き方です)。

```css
@page {
  @footnote {
    float: bottom;               /* 脚注領域を用紙の下端に */
    writing-mode: horizontal-tb; /* 脚注の本文を横書きに */
  }
}
```

- <span class="cssprop">float</span>に書けるのは
  <span class="cssdecl">bottom</span>(用紙の下端。縦組みでは行の下端側の帯)と
  <span class="cssdecl">block-end</span>(本文のブロック方向の終端。縦組みでは
  左端(vertical-lr では右端)の帯)です。横組みでは両者は同じ位置になります。
  ほかの値は警告を出したうえで<span class="cssdecl">block-end</span>として扱います。
- 既定は<span class="cssdecl">block-end</span>です(仕様の既定は
  <span class="cssdecl">bottom</span>ですが、これまでの縦組み文書の出力を
  変えないために据え置いています)。
- <span class="cssprop">writing-mode</span>は脚注領域の書字方向です。省略すると
  ページの書字方向になります。脚注要素自身に指定した
  <span class="cssprop">writing-mode</span>は無視します(前項のとおり)。
- <span class="cssprop">height</span>は帯の固定寸法です。既定の
  <span class="cssdecl">auto</span>では注の量に合わせます。
  <span class="cssdecl">height: 40pt</span>のように長さを指定すると、
  **注の無いページも含めて毎ページ同じ帯を先に確保**します。
  寸法には本文との間隙6ptを含み、注は帯の本文側から呼び出し順に並べます。
  帯に収まらない注は次ページへ丸ごと送り、呼び出しページの番号を保ちます。
  単独でも帯に入らない注は、呼び出しを確定した次ページで警告して溢れさせます。
  区切り罫線は注を置いたページだけに引きます。
- <span class="cssprop">min-height</span>は帯の下限です(既定は0)。
  注が少ないページや注の無いページでもこの寸法を確保し、注が増えれば帯を伸ばします。
  <span class="cssprop">height</span>も固定した場合は両者の大きい方を使います。
  <span class="cssdecl">bottom</span>の縦組みでは物理的な高さ、
  <span class="cssdecl">block-end</span>では本文のブロック方向の寸法です。
  負の長さと百分率は警告して無視します。フォント相対の長さはUAの既定フォントを基準にします。
  <span class="cssprop">max-height</span>は未対応で、指定しても警告して無視します。
  固定寸法・下限とも、縦組みの地の帯は行長の6割、ブロック方向の帯は
  本文に20ptを残す寸法までに制限し、超過時は文書中で一度警告します。
- <span class="cssdecl">float: bottom</span>の縦組みでは、脚注の帯の分だけ
  そのページの**行が短く**なります。帯の高さは包含領域の寸法から一度だけ
  引きます。帯は版面の内側に取り、用紙の余白と
  マージンボックス(柱・ノンブル)は変わりません。帯の上限は行長の6割です。
- 脚注は**呼び出しと同じページ**の帯に置きます。そのために、
  <span class="cssdecl">float: bottom</span>の縦組みで
  <span class="cssdecl">height: auto</span>の場合に限り、ページを
  組む前に同じ内容を一度仮に組んで帯の大きさを決めます(仮組みは本番より
  約2ページ分先を進むので、最初のページの出力もその分だけ遅れて始まります。
  既定の脚注領域や横組みではこの仮組みは行いません)。
  名前付きページでも、仮組みがページ名ごとの寸法で組むため、通常は呼び出しと
  同じページの帯に載ります。
  仮組みと本番で行の折り返しが違って呼び出しが次のページへ移った場合や、
  呼び出しの後ろ遠くに脚注本文がある場合、仮組みの報告が間に合わない場合、
  仮組みと本番でページの寸法が一致しない場合(白紙ページの扱いが違って
  左右のページがずれたときなど)、
  注の実際の高さが予約した帯を超える場合は、脚注が**次のページの帯**に
  置かれることがあります(番号は呼び出しのページのものを保ちます)。
- **短い注を一定の帯に流し込む本には、固定の<span class="cssprop">height</span>を推奨します。**
  帯の寸法が最初から決まるため確実で速く、仮組みとそれに伴う出力の遅れがありません。
  <span class="cssdecl">min-height</span>だけの指定では、縦組みの地の帯は仮組みを行います。

  ```css
  @page {
    @footnote {
      float: bottom;
      writing-mode: horizontal-tb;
      height: 40pt;
    }
  }
  ```
- <span class="cssdecl">float: bottom</span>の縦組みでは、段組でも帯は
  **ページに1つ**で、段の下を横に通ります。各段の行長(物理的な高さ)は、
  帯を引いた包含領域の寸法から段間を除いて等分するので揃います。
  <span class="cssdecl">block-end</span>(既定)では、脚注は呼び出しがある
  段の末尾に置かれます(前節)。
- 浮動体や絶対配置の中の呼び出しは、次のページの帯に置かれることがあります。
  特にページ浮動体・絶対配置・並列注の中の呼び出しは仮組みの報告に含まれません。
  通常の浮動体や表セルの中の呼び出しは仮組みでも数えます。
- 横書きの脚注の幅は、呼び出しのページの版面の幅です(脚注要素の
  <span class="cssprop">padding</span>と<span class="cssprop">border</span>を
  含み、左右の<span class="cssprop">margin</span>は無視します)。名前付きページで
  幅の違うページへ送られた脚注も、呼び出しのページの幅のまま左端に揃えて置きます。

### <a id="style-page-floats">図表をページの端へ寄せる<span class="since">4.0.0</span></a>

<span class="cssdecl">float: bottom;</span>を指定した要素は、
そのページの版面の下端(脚注があればその上)へ移動します。
<span class="cssdecl">float: top;</span>を指定した要素は、
**そのページの先頭**へ移動します。そのページに既に組んだ本文は、
図表の高さのぶんだけ下へ送られます(行の折り返しは変わりません)。
本文と図表を合わせてそのページに収まらない場合は、図表だけが
**次のページの先頭**へ移り、本文はそのページに残ります。
本文の途中に置いた大きな図表を、読みやすい位置へ寄せるための機能です。

```css
figure.wide {
  float: bottom;      /* このページの下端へ */
  width: 100%;
}
figure.head {
  float: top;         /* このページの先頭へ(収まらなければ次のページ) */
  width: 100%;
}
```

- 入り切らない場合は、後続のページへ順に送られます(書いた順は
  保たれます)。
- 図表の幅が版面より狭い場合、図表と並ぶ行だけが図表を避けて短くなり、
  それ以外の行は全幅のままです(通常のフロートと同じ回り込み)。縦組みで
  版面の幅いっぱいの図表を置いた場合も、図表の下の残りに本文が流れます。
  ただし**本文の後に書いた図表**は、既に組んだ行を図表の脇へ流し直せない
  ため、幅が狭くても図表の高さぶんの帯としてページの先頭に置き、本文は
  その下から続きます(脇には回り込みません)。
- 図表がページの縦横ともに版面を超える場合だけ、その図表だけのページに
  なり、本文は次のページから続きます(重ねて描くことはありません)。
- 脚注と同じページに置いた場合、ページフロートは脚注より上になります。

<div class="note">

文書を先頭から一度だけ読んで組み立てるため、本文の後に
書いた<span class="cssdecl">float: top;</span>は、既に組んだ内容を図表の
高さのぶんだけ送ることで同じページの先頭へ置きます。組み直しはしない
ので、表・段組・flex/gridの内側に書いた図表や、前のページから続いている
段落の途中に書いた図表は、次のページの先頭になることがあります。
確実に同じページの先頭へ入れたい場合は、ページの先頭(改ページ直後)に
書いてください。

`float-reference`プロパティによる左右のページフロートと、段組の中での
ページフロートには対応していません。

</div>
