## <a id="style-performance">速度とメモリ</a>

大量の帳票を安定して出し続けるために作られています。
この章では、**組版そのものがどこにメモリと時間を使うか**を説明します。
サーバーとして動かすときの設定は
<b>サーバーの速度とメモリ</b>(サーバー製品の説明書)を見てください。

### <a id="admin-perf-memory">メモリは文書の大きさで決まりません</a>

文書を最初から最後まで読み込んでから組むのではなく、
**流れてきた分から順にページへ落として捨てていきます**。

そのため必要なメモリは、文書の総ページ数ではなく
<a href="#style-page-layout" class="pageref">組んでいる最中のページの大きさ</a>で決まります。
10万ページの帳票でも、1ページの帳票とほぼ同じメモリで処理できます。

このことから、次のように見積もれます。

<dl>

<dt>1件あたりのメモリはほぼ一定</dt>
<dd>
	同時に走らせる本数を、実測した1件分から掛け算で見積もれます。
	「大きい文書のときだけ落ちる」という事故が起きにくい作りです。
</dd>

<dt>メモリを増やしても速くなりません</dt>
<dd>
	使い切っていないメモリを増やしても効果はありません。
	速度を上げたいときは<b>同時実行数</b>(サーバー製品の説明書)を見てください。
</dd>

</dl>

ただし、次の場合はページ1枚に載る量が増えるため、メモリも増えます。

- 1ページに極端に多くの要素を置いている(数万行の表を1ページに詰めるなど)
- 巨大な画像を原寸で貼っている
- 段組の段数が多く、段のバランスを取るために全段を保持する必要がある


### <a id="admin-perf-passes">パス数は処理時間に直接効きます</a>

<a href="#style-multipass" class="pageref">2パス以上の変換</a>を指定すると、
**文書をその回数だけ読み直します**。2パスなら、おおよそ時間も2倍です。

<span class="ioprop">processing.pass-count</span> の既定は1です。
目次のページ番号や総ページ数など、
<a href="#style-multipass-required" class="pageref">2パスが必要な機能</a>を
使っていない文書に2を指定しても、結果は変わらず時間だけが倍になります。

**必要な文書にだけ指定してください。**


### <a id="admin-perf-resources">外部リソースの取得</a>

文書から参照している画像・スタイルシート・フォントは、
変換のたびに取りに行きます。**ここが遅いと変換全体が待たされます。**

とくに、外部のサイトを参照していて、そのサイトが応答しない場合、
接続が切れるまで変換が止まります。

<dl>

<dt>取りに行く先を絞る</dt>
<dd>
	<span class="ioprop">input.include</span> /
	<span class="ioprop">input.exclude</span> で、
	取得を許すURIを限定してください。
	→ <a href="#prog-input-restriction" class="pageref">読み込むリソースの制限</a>
</dd>

<dt>手元に置く</dt>
<dd>
	繰り返し使う画像やフォントは、外部から取るのではなく
	サーバーのローカルに置いてください。
</dd>

</dl>


### <a id="admin-perf-images">画像</a>

画像はPDFの大きさと処理時間の両方に効きます。

<dl>

<dt>原寸が大きすぎる画像を縮める</dt>
<dd>
	<span class="ioprop">output.pdf.image.max-width</span> /
	<span class="ioprop">output.pdf.image.max-height</span> を指定すると、
	これを超える画像を縮小してから埋め込みます。
	紙面で数センチにしか出ない画像に、数千ピクセルの原寸は不要です。
</dd>

<dt>圧縮方法を選ぶ</dt>
<dd>
	<span class="ioprop">output.pdf.image.compression</span> の既定は
	可逆圧縮(flate)です。写真が多い文書では jpeg にするとPDFが小さくなります。
	図やスクリーンショットでは、jpegにすると汚くなるうえ大きくなることがあります。
</dd>

<dt>すでにJPEGの画像を再圧縮しない</dt>
<dd>
	<span class="ioprop">output.pdf.jpeg-image</span> の既定(raw)では、
	JPEG画像を展開せずそのまま埋め込みます。
	recompressにすると画質が落ちるうえ遅くなります。**通常は変更しないでください。**
</dd>

</dl>


### <a id="style-perf-where">遅いときに見る順番</a>

1. **パス数**。<span class="ioprop">processing.pass-count</span> に2以上を
   指定していませんか。必要な文書だけに絞ってください。
2. **外部リソース**。応答しないサイトを参照していませんか。
   ログに <a href="#appx-messages" class="pageref">2811(画像が読めない)</a> や
   2814(取得を拒否した)が出ていませんか。
3. **画像**。原寸の大きい画像を大量に貼っていませんか。

それでも原因がつかめない場合は、
<a href="#prog-troubleshooting" class="pageref">うまくいかないとき</a>も
参照してください。
