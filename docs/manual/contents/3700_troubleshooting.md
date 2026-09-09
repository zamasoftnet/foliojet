## <a id="prog-troubleshooting">うまくいかないとき</a>

思ったとおりのPDFが出ないときの、症状からの探し方です。

**まずメッセージを見てください。** 変換中に起きたことは
メッセージハンドラへ渡します。多くの症状は、対応するメッセージが出ています。
受け取り方は<b>メッセージハンドラの設定</b>(サーバー製品の説明書)を、
コードの一覧は<a href="#appx-messages" class="pageref">メッセージ一覧</a>を
参照してください。

### <a id="prog-trouble-text">文字が出ない・四角(□)になる</a>

**フォントがありません。** メッセージ `281F`(`Missing proper font for:`)が
出ていれば確定です。

<dl>

<dt>そのフォントがインストールされていない</dt>
<dd>
	`fonts.xml` に書かれた場所にフォントファイルがあるか確かめてください。
	Linuxでは日本語フォントが入っていないことがよくあります。
</dd>

<dt>CSSで指定した書体名が見つからない</dt>
<dd>
	`font-family` に書いた名前と、フォントが持つ名前が一致していますか。
	最後に `serif` / `sans-serif` を書いておくと、
	見つからないときに代替が使われます。
</dd>

<dt>その文字がフォントに入っていない</dt>
<dd>
	書体はあっても、その文字の字形を持っていない場合があります。
	絵文字、旧字体、小書きのカナなどで起こります。
	その文字を含む別のフォントを `font-family` に追加してください。
</dd>

</dl>

### <a id="prog-trouble-image">画像が出ない</a>

出ない場所に代替テキストの枠が描かれていれば、画像を取得できなかったか、
読めなかったかのどちらかです。

<dl>

<dt>取得を拒否した(`2814`)</dt>
<dd>
	<b>いちばん多い原因です。</b>
	<span class="ioprop">input.include</span> /
	<span class="ioprop">input.exclude</span> の設定で、
	その画像のURIが許可されていません。
	→ <a href="#prog-input-restriction" class="pageref">読み込むリソースの制限</a>
</dd>

<dt>読めなかった(`2811`)</dt>
<dd>
	ファイルが無い、壊れている、対応していない形式のいずれかです。
	対応する形式は<a href="#style-image" class="pageref">対応する入力ファイル</a>を
	確認してください。<b>JPEG 2000は同梱していません。</b>
</dd>

<dt>相対URIの基準が違う</dt>
<dd>
	文書をストリームで渡した場合、相対URIの基準が決まりません。
	ドライバに文書のURIを教えるか、
	`<?jp.cssj.base-uri ?>` 処理命令で基準を指定してください。
</dd>

</dl>

### <a id="prog-trouble-style">スタイルが効かない</a>

<dl>

<dt>スタイルシートを読めていない(`2803`)</dt>
<dd>
	画像と同じく、取得の制限で弾かれていることがあります。
</dd>

<dt>対応していないプロパティ(`2802`)</dt>
<dd>
	<a href="#appx-css" class="pageref">CSSプロパティのサポート状況</a>で
	確認してください。ブラウザで効くものがすべて使えるわけではありません。
</dd>

<dt>1パスでは決まらないスタイル</dt>
<dd>
	`:last-child` や `:has()` のように、後ろまで読まないと決まらない
	セレクタは、1パスでは効きません。
	→ <a href="#style-multipass-required" class="pageref">2パスが必要な機能</a>
</dd>

<dt>body内に書かれたスタイルシート</dt>
<dd>
	body内の&lt;style&gt;は、1パスではそれ以降の要素にしか適用されません。
	SSRフレームワークの出力によくある形です。
	<span class="ioprop">processing.pass-count</span>を2以上にすると
	文書全体へ適用されます。
	→ <a href="#style-multipass-required" class="pageref">2パスが必要な機能</a>
</dd>

</dl>

### <a id="prog-trouble-page-number">ページ番号や目次が合わない</a>

**ほぼ確実に、パスが足りていません。**

総ページ数、目次のページ番号、後ろの内容を参照する表示は、
文書を最後まで読まないと決まりません。
<span class="ioprop">processing.pass-count</span> に2以上を指定してください。

```
processing.pass-count = 2
```

2パスにしても合わない場合は、
<b>1回目と2回目で組版結果が変わって、番号が揺れている</b>可能性があります。
番号の桁数が増えて行が折り返し、ページが増える、といった場合です。
パス数を3以上にすると落ち着くことがあります。

詳しくは<a href="#style-multipass" class="pageref">2パス以上の変換処理</a>を
参照してください。

### <a id="prog-trouble-break">改ページが思ったところで起きない</a>

<dl>

<dt>指定した場所に余白が残って次のページへ送られる</dt>
<dd>
	`page-break-inside: avoid` や `orphans` / `widows` が働いています。
	まとまりを保とうとして、入りきらない要素を次のページへ送っています。
</dd>

<dt>表の途中で切れてほしくない</dt>
<dd>
	→ <a href="#style-page-break-in-table" class="pageref">テーブル内での改ページ</a>
</dd>

<dt>指定したのに切れない</dt>
<dd>
	`page-break-before` / `page-break-after` は、
	<b>ブロックレベルの要素にしか効きません</b>。
	インライン要素や表のセルに指定しても無視されます。
</dd>

<dt>空白のページができる</dt>
<dd>
	`left` / `right` を指定すると、偶数・奇数ページに合わせるために
	白いページが1枚入ります。これは仕様どおりの動きです。
</dd>

</dl>

### <a id="prog-trouble-overflow">紙からはみ出す・切れる</a>

<dl>

<dt>横に長い表やコードがはみ出す</dt>
<dd>
	内容が版面より広い場合、縮めません。はみ出したまま組みます。
	自動レイアウトの表では、折り返せない内容の幅(列の最小幅の合計)が
	利用可能な幅をわずかに超えるだけなら列を縮めて収めますが、
	大きく超える場合は列幅を保ったまま表ごとはみ出します
	(縮めると内容が隣の列に重なるため。Chrome等のブラウザと同じ挙動です)。
	`table-layout: fixed` を指定する、`word-break` で折り返す、
	フォントを小さくする、のいずれかで収めてください。
</dd>

<dt>紙の大きさが指定と違う</dt>
<dd>
	プリンタや出力先の制約で、指定した紙より小さくなることがあります(`3802`)。
	→ <a href="#style-page-size" class="pageref">ページの大きさの制約と切り落とし</a>
</dd>

<dt>縦長・横長が逆になる</dt>
<dd>
	→ <a href="#style-auto-rotate" class="pageref">縦長・横長が合わないとき</a>
</dd>

</dl>

### <a id="prog-trouble-output">出力が空・途中で終わる</a>

<dl>

<dt>内容がない(`380D`)</dt>
<dd>
	文書の解析に失敗しているか、すべての内容が
	`display: none` になっています。
	入力がHTMLとして解釈できているか、
	<a href="#style-input-mime" class="pageref">MIME型</a>の指定を確かめてください。
</dd>

<dt>ページ数の上限に達した(`3805`)</dt>
<dd>
	→ <a href="#prog-page-limit" class="pageref">ページ数の制限</a>
</dd>

<dt>データサイズの上限に達した(`3804`)</dt>
<dd>
	→ <a href="#prog-size-limit" class="pageref">データサイズの制限</a>
</dd>

<dt>接続が切れた</dt>
<dd>
	サーバー経由の場合、`jp.cssj.cssjd.timeout`(既定180秒)を
	超えてデータのやりとりが無いと切断されます。
	長い文書では値を大きくしてください。
</dd>

</dl>

### <a id="prog-trouble-pdf">できたPDFが開けない・警告が出る</a>

<dl>

<dt>PDFのバージョンと機能が合っていない(`2812`)</dt>
<dd>
	古いPDFバージョンでは使えない機能を指定しています。
	<span class="ioprop">output.pdf.version</span> を上げるか、
	その機能をやめてください。
	→ <a href="#style-pdf-version" class="pageref">PDFのバージョン</a>
</dd>

<dt>出力を途中で捨てている</dt>
<dd>
	変換が最後まで終わる前にストリームを閉じると、
	PDFの索引が書かれず開けなくなります。
	ドライバの終了処理を最後まで呼んでいるか確かめてください。
</dd>

<dt>入稿用の規格に通らない</dt>
<dd>
	PDF/X や PDF/A には、フォントの埋め込みや色空間の制約があります。
	→ <a href="#style-pdf-profiles" class="pageref">PDFのプロファイル</a>
</dd>

</dl>

### <a id="prog-trouble-slow">遅い</a>

→ <b>速度とメモリ</b>(サーバー製品の説明書)

とくに次の2つが効きます。

- <span class="ioprop">processing.pass-count</span> に不要な2以上を指定していないか
- 応答しない外部サイトの画像やスタイルシートを参照していないか

### <a id="prog-trouble-network">サーバーの内側の資源が取れない</a>

リモートのサーバーで変換していて、`http://192.168.…` や `http://127.0.0.1:…` のような
アドレスの画像・スタイルシートが**取得を拒まれる**(警告 `2814`)ことがあります。

4.0.0 から、**内側のネットワークへの取得を許されていない**呼び出しでは、
サーバー自身とその隣のネットワークへ取りに行きません。サーバーを踏み台にして
内側へ届いてしまうためです。判定は名前を解決した結果で行い、ループバック・
リンクローカル・私設アドレスが対象です。**公開されているアドレスは
今までどおり取得できます。**

<a href="#appx-ioprop-input.include" class="pageref">input.include</a> で許した範囲の外へ
HTTP のリダイレクトで連れ出された場合も、転送先で取得を止めます。

内側の資源を意図して使うのであれば、その呼び出しに内側への取得を許すか、
クライアントから資源を送ってください。許し方はサーバー製品の設定
(<b>conf</b>(サーバー製品の説明書) の `local-access.txt`)で決めます。

### <a id="prog-trouble-report">それでも解決しないとき</a>

問い合わせの際は、次の情報があると原因を特定しやすくなります。

1. **メッセージの全文**(コードを含む)
2. **再現する最小の入力**。1ページに縮めた文書とスタイルシート
3. **入出力プロパティの設定**
4. **版数とJavaの版数**
