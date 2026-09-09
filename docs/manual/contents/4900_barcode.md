## <a id="style-barcode">バーコード・QRコード</a>

文書の中に要素を書くだけで、バーコードやQRコードを描けます。
外部の画像を用意する必要はありません。線は文字と同じベクタで描かれるので、
拡大しても粗くならず、印刷しても読み取れます。

生成には [OkapiBarcode](https://github.com/woo-j/OkapiBarcode) を使っています。

<div class="note">
バージョン4.0.0で、生成エンジンをBarcode4Jおよび
「QRコードクラスライブラリ for Java」からOkapiBarcodeへ置き換えました。
要素の書き方は互換ですが、<strong>効かなくなったパラメータがあります</strong>。
<a href="#style-barcode-ignored" class="pageref">読まれないパラメータ</a>を確認してください。
</div>

### <a id="style-barcode-write">書き方</a>

`http://barcode4j.krysalis.org/ns` 名前空間の `barcode` 要素が、
バーコードの図に置き換わります。符号化する文字列は `message` 属性に書きます。

`barcode` の直下に**種類を表す要素**を1つ置き、そのさらに直下に
パラメータを並べます。

```xml
<bc:barcode xmlns:bc="http://barcode4j.krysalis.org/ns"
 message="200123456789">
  <bc:ean-13>
    <bc:height>15</bc:height>
    <bc:module-width>0.33</bc:module-width>
  </bc:ean-13>
</bc:barcode>
```

<div class="figure" title="バーコード(表示結果)">
<bc:barcode xmlns:bc="http://barcode4j.krysalis.org/ns"
 message="200123456789">
  <bc:ean-13>
    <bc:height>15</bc:height>
    <bc:module-width>0.33</bc:module-width>
  </bc:ean-13>
</bc:barcode>
</div>

図はインライン画像として扱われます。`width` / `height` などのCSSプロパティで
大きさを整えたり、`vertical-align` で行の中の位置を合わせたりできます。

### <a id="style-barcode-types">書ける種類</a>

種類を表す要素の名前は、次のとおりです。
同じ行に並んだ名前はどれを書いても同じ結果になります。
大文字・小文字とハイフンは無視されるので、`ean-13` と `EAN13` は同じです。

| 要素名 | 記号の種類 |
| --- | --- |
| `bc:code128` | Code 128。**種類を書かなかったときもこれになります** |
| `bc:code39` `bc:code3of9` | Code 39 |
| `bc:codabar` | Codabar(NW-7)。`message` にスタート・ストップ文字(A〜D)が無いときは A が自動で補われます<span class="since">4.0.0</span> |
| `bc:interleaved2of5` `bc:intl2of5` `bc:int2of5` `bc:itf` | Interleaved 2 of 5(ITF) |
| `bc:ean-13` | 一般商品用のEAN-13(JAN-13)<span class="since">4.0.0</span> |
| `bc:isbn` | 日本の書籍JAN用EAN-13。等高バーと13桁連続のOCR行で描きます。**チェックディジット込みの13桁を渡してもよく**、その場合チェックディジットは規格どおり計算し直されます<span class="since">4.0.0</span> |
| `bc:ean-8` | EAN-8(JAN-8)<span class="since">4.0.0</span> |
| `bc:ean` | EAN。`message` が8桁以下ならEAN-8、それ以外はEAN-13になります |
| `bc:upc-a` | UPC-A<span class="since">4.0.0</span> |
| `bc:upc-e` | UPC-E<span class="since">4.0.0</span> |
| `bc:ean-128` `bc:gs1-128` | **素のCode 128として描かれます**。GS1のAI構文(FNC1)には対応していません |
| `bc:postnet` | POSTNET<span class="since">4.0.0</span> |
| `bc:planet` | PLANET<span class="since">4.0.0</span> |
| `bc:royal-mail-cbc` `bc:royalmail` `bc:rm4scc` | Royal Mail 4-State(CBC)<span class="since">4.0.0</span> |
| `bc:usps4cbc` `bc:usps4cb` `bc:uspsonecode` `bc:uspsintelligentmail` | USPS Intelligent Mail。`message` は追跡コードとルーティングコードを連結した数字列(20・25・29・31桁)で構いません<span class="since">4.0.0</span> |
| `bc:japanpost` `bc:jp4scc` | 郵便カスタマーバーコード |
| `bc:qrcode` `bc:qr` | QRコード |
| `bc:datamatrix` | Data Matrix |
| `bc:pdf417` | PDF417 |
| `bc:aztec` `bc:azteccode` | Aztec Code |

**知らない名前を書いてもエラーになりません。** Code 128として描かれます。
綴りを間違えたときは、想定と違う記号が黙って出るので注意してください。

### <a id="style-barcode-params">効くパラメータ</a>

種類を表す要素の直下に書きます。次の6つだけが読まれます。

| 要素名 | 意味 |
| --- | --- |
| `bc:module-width` | 最小単位(バー1本、セル1個)の幅 |
| `bc:height` `bc:bar-height` | バーの高さ。一次元バーコードのみ |
| `bc:quiet-zone` `bc:quiet-zone-horizontal` | 左右の余白。QRなど面で読む記号では上下にも効きます <span class="since">4.0.0</span> |
| `bc:quiet-zone-vertical` | 上下の余白。書けばこちらが優先されます |
| `bc:human-readable` `bc:human-readable-placement` | 数字の表示位置。`top` `bottom` `none`(`hidden`も可) |
| `bc:font-name` `bc:font-size` | 数字を描く書体と大きさ |

#### <a id="style-barcode-unit">数値の単位</a>

長さの値には単位を書けます<span class="since">4.0.0</span>。
`mm`(単位なしも同じ)・`cm`・`in`・`pt`・`px` が読まれます。

```xml
<bc:module-width>0.21mm</bc:module-width>
<bc:module-width>0.02in</bc:module-width>
<bc:height>15mm</bc:height>
```

余白(`bc:quiet-zone` `bc:quiet-zone-vertical`)には、加えて
`mw`(モジュール幅の倍数)が書けます。`10mw` はモジュール幅の10倍です。

```xml
<bc:quiet-zone>10mw</bc:quiet-zone>
```

<div class="note">
QRコード・Data Matrix・Aztec Code・PDF417では、<code>bc:quiet-zone</code>が
<strong>四方に効きます</strong><span class="since">4.0.0</span>。これらの規格は
四辺に同じ余白を要求するためです。上下だけ変えたいときは
<code>bc:quiet-zone-vertical</code>を書いてください。
</div>

`bc:font-size` の単位なしはポイント(pt)として読まれます。

### <a id="style-barcode-book-jan">書籍JANコード</a>

日本国内で流通する本の2段バーコードには、一般商品用の`bc:ean-13`ではなく
`bc:isbn`を使います。`bc:isbn`は符号化自体はEAN-13ですが、ガードバーを
延長せず、バー下の13桁を分割せずに並べる書籍用の表示になります。

```xml
<bc:barcode xmlns:bc="http://barcode4j.krysalis.org/ns"
 message="9784908348143" style="height:11mm">
  <bc:isbn>
    <bc:height>7.6mm</bc:height>
    <bc:module-width>0.33mm</bc:module-width>
    <bc:quiet-zone>0mm</bc:quiet-zone>
    <bc:human-readable>
      <bc:placement>bottom</bc:placement>
      <bc:font-name>OCRB</bc:font-name>
      <bc:font-size>7.5pt</bc:font-size>
    </bc:human-readable>
  </bc:isbn>
</bc:barcode>
```

標準倍率100%では、バー本体の幅は95モジュール×0.33mm=31.35mmです。
1段の目視文字込み全高は11mmです。これは日本国内の書籍JAN専用寸法で、
一般商品用EAN-13の標準全高25.93mmとは異なります。
13桁は指定したOCR-Bの実字幅を基に、31.35mmのバー幅全体へ均等配置されます。
上段にISBN、下段に分類・税抜価格の13桁を同じ形式で置きます。白地・余白・
2段の間隔・表4上の位置も流通規定に従ってください。バー下の数字はOCR-B、
右側に別記する`ISBN...`と`C...`はOCR-B以外の視認できる書体を使います。
詳細は[日本図書コード管理センター「ISBNコード／日本図書コード／書籍JANコード利用の手引き2025年版」](https://isbn.jpo.or.jp/doc/ISBN_jp2025.pdf)を参照してください。

4.0.0 以降の`bc:height`はバー部分だけの高さです。旧3系では
目視文字込みの全高だったため、同じ値を指定すると縦寸法が一致しません。
3.2用の指定を移すときは上の11mm用例に置き換えてください。

### <a id="style-barcode-ignored">読まれないパラメータ</a>

次の要素は、書いても**何も起きません**。警告も出ません。
Barcode4J時代の文書やサンプルに載っているので、残っていたら削除してください。

| 要素名 | かつての意味 | 現在 |
| --- | --- | --- |
| `bc:checksum` | チェックディジットの付与方法 | 記号ごとの規格どおりに自動計算されます |
| `bc:ecc` | QRコードの誤り訂正レベル | 内容量に応じて自動で決まります |
| `bc:encmode` | QRコードの符号化モード | 内容から自動で決まります |
| `bc:version` | QRコードの型番 | 内容量に応じて自動で決まります |

#### 入れ子の書き方も読まれます

Barcode4J時代の入れ子の書き方も、そのまま意図どおりに読まれます
<span class="since">4.0.0</span>。

```xml
<bc:human-readable>
  <bc:placement>bottom</bc:placement>
  <bc:font-size>8pt</bc:font-size>
</bc:human-readable>
```

入れ子の中の要素(`bc:placement` `bc:font-size` など)は、
平らに書いたときと同じパラメータとして扱われます。

### <a id="style-barcode-qr">QRコード</a>

QRコードは内容から型番・誤り訂正レベル・符号化モードが自動で決まります。
指定できるのはセルの大きさと余白だけです。

```xml
<bc:barcode xmlns:bc="http://barcode4j.krysalis.org/ns"
 message="https://copper-pdf.com/">
  <bc:qrcode>
    <bc:module-width>1</bc:module-width>
    <bc:quiet-zone>2</bc:quiet-zone>
  </bc:qrcode>
</bc:barcode>
```

<div class="figure" title="QRコード(表示結果)">
<bc:barcode xmlns:bc="http://barcode4j.krysalis.org/ns"
 message="https://copper-pdf.com/">
  <bc:qrcode>
    <bc:module-width>1</bc:module-width>
    <bc:quiet-zone>2</bc:quiet-zone>
  </bc:qrcode>
</bc:barcode>
</div>

QRコードの詳細は株式会社デンソーウェーブのサイト
[https://www.qrcode.com/](https://www.qrcode.com/) を参照してください。

### <a id="style-barcode-post">郵便カスタマーバーコード</a>

郵便番号と住所表示番号から、郵便物の区分用バーコードを描きます。

```xml
<bc:barcode xmlns:bc="http://barcode4j.krysalis.org/ns"
 message="1008798 1-3-2">
  <bc:japanpost>
    <bc:module-width>0.6</bc:module-width>
  </bc:japanpost>
</bc:barcode>
```

<div class="figure" title="カスタマーバーコード(表示結果)">
<bc:barcode xmlns:bc="http://barcode4j.krysalis.org/ns"
 message="1008798 1-3-2">
  <bc:japanpost>
    <bc:module-width>0.6</bc:module-width>
  </bc:japanpost>
</bc:barcode>
</div>

`message` の中の、数字・アルファベット・ハイフン以外の文字は取り除かれます。
上の例のように空白を入れて読みやすく書いても構いません。

バーの太さは0.6が既定で、0.48から0.69の範囲が推奨されています。
仕様は日本郵便のバーコードマニュアル
[https://www.post.japanpost.jp/zipcode/zipmanual/](https://www.post.japanpost.jp/zipcode/zipmanual/)
を参照してください。

### <a id="style-barcode-error">読み取れないときは</a>

印刷したバーコードが読み取れない原因は、たいてい次のどれかです。

<dl>

<dt>モジュール幅が小さすぎる</dt>
<dd>
	`bc:module-width` を大きくしてください。一次元バーコードでは0.33以上、
	QRコードでは0.5以上を目安にします。プリンタの解像度より細いバーは
	正しく印刷されません。
</dd>

<dt>余白が足りない</dt>
<dd>
	記号の周囲には無地の余白が必要です。`bc:quiet-zone` を広げるか、
	CSSの `margin` で周囲を空けてください。背景色や罫線が接していると
	読み取れなくなります。
</dd>

<dt>拡大・縮小している</dt>
<dd>
	CSSの `width` / `height` で図を変形させると、バーの比率が崩れます。
	大きさは `bc:module-width` と `bc:height` で決めてください。
</dd>

<dt>`message` の内容が記号の規格に合わない</dt>
<dd>
	EANは13桁または8桁の数字、Code 39は英大文字と数字といった制約があります。
	規格に合わない内容を渡すと、バーコードの代わりに
	エラーの文言が図の中に描かれます。
</dd>

</dl>
