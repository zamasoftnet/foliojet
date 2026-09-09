## メッセージハンドラから取得できる情報

### <a id="appx-messages">2.0以前(CTIP 1.0)</a>

メッセージハンドラに渡される、<b>コード4の処理情報</b>(サーバー製品の説明書)から得ることができる情報の一覧です。

| カテゴリ | 値の形式 | 説明 |
| --- | --- | --- |
| page-number | 数値 | これから生成されるページの番号です。最終的なページ番号が総ページ数となります。 |
| heading-title | 文字列 | 見出し(h1〜h6)として認識された文字列です。 |
| broken-image-uri | 文字列 | 表示できない画像のURIです。 |
| pass-count | 数値 | 残りパス数です。 |
| <a id="appx-messages-annot"></a>annot | 文字列 | cssj:annot属性で任意の要素に指定された注釈です。 |

### <a id="appx-ctip2-messages">2.1以降(CTIP 2.0)</a>

新しいプログラム・インターフェースでは<b>メッセージコード</b>(サーバー製品の説明書)により、さらに詳細な情報を得ることができます。

**情報**

| コード | 値 | 説明 |
| --- | --- | --- |
| 1001 |  | abort等により、正常に処理が中断された。 |
| 1801 | ページ番号(int) | 現在処理を開始したページ。 |
| 1802 | 見出し(string) | 現在出力した見出し。<span class="ioprop">output.pdf.bookmarks</span>か<span class="ioprop">processing.page-references</span>が有効なときだけ通知します(見出しの走査自体をその場合にしか行わないため)。 |
| 1803 | パス番号(int) | 現在処理を開始した処理のパス。 |
| 1804 | 注釈(string) | 現在出力した注釈。 |
| 1805 | タイトル(string) | ドキュメントのタイトル。 |
| 18FF | プラグイン名(string)<br />メッセージ(string) | プラグインからの情報メッセージ。 |
| 1806<span class="since">2.1.2</span> | ページの高さ(double) | pt単位のページの高さです。 <span class="ioprop">output.auto-height</span>がtrueのときだけ通知します。 |

**警告**

| コード | 値 | 説明 |
| --- | --- | --- |
| 2001 | リソースのURI(string) | ドキュメントから参照されたリソースURIの形式の不正。 |
| 2002 | ベースURI(string) | 文書のベースURIの形式の不正。 |
| 2801 | CSSファイルのURI(string) エラーメッセージ(string) | 形式が不正なCSSがあった。 |
| 2802 | CSSプロパティ名(string) | サポートされないCSSプロパティがあった。 |
| 2803 | CSSファイルのURI(string) | CSSファイルが存在しない。 |
| 2804 | プロパティ名(string) プロパティ値(string) | プロパティの値の形式が不正。 |
| 2805 | 処理命令名(string) 処理命令の値(string) | 不正な形式の処理命令があった。 |
| 2806 | CSSファイルのURI(string) 深さの限界値(string) | CSSの@importが深すぎる。 |
| 2807 | 参照元CSSのURI(string) 参照先CSSのURI(string) | CSSの@importがループしている。 |
| 2808 | HTML要素名(string) 属性名(string) 属性地(string) | HTMLの属性名の形式に不正がある。 |
| 280A | cssj:header属性の値(string) | cssj:header属性の値の形式に不正がある。 |
| 280B | リソースのURI(string) | リソースのURIの形式に不正がある。 |
| 280C | リンクのURI(string) | リンクのURIの形式に不正がある。 |
| 280D | SVGファイルのURI(string) エラーメッセージ(string) | SVGの形式に不正がある。 |
| 280E | XSLTファイルのURI(string) | XSLTファイルが存在しない。 |
| 280F |  | PIによる入出力プロパティの上書きが禁止されている。 |
| 2810 | 添付ファイルのURI(string) | PDFに添付しようとしたファイルが存在しない。 |
| 2811 | 画像ファイルのURI(string)<br />段階(string)<span class="since">4.0.0</span> | 画像を読み込めない。段階は<tt>resolve</tt>(参照を解決できない)・<tt>fetch</tt>(取得に失敗。HTTPの状態が分かるときは<tt>fetch: HTTP 404</tt>のように付ける)・<tt>decode</tt>(対応していない形式か壊れた画像)のいずれか。URIの認証情報は除いて出す。 |
| 2812 | PDFバージョン(string) 設定名(string) 設定値(string) | 現在のPDFバージョンで利用できない機能を使おうとした。 |
| 2813 | エラーメッセージ(string) | インラインオブジェクトの形式に不正がある。 |
| 2814 | リソースのURI(string) | リソースへのアクセスが許可されていない。 |
| 2816 | CSSプロパティ名(string) 値(string) エラーメッセージ(string) | CSSプロパティの値の形式に不正がある。 |
| 2817 | インラインスタイル(string) エラーメッセージ(string) | インラインCSSの形式に不正がある。 |
| 2818 | プロパティ名(string) | サポートされない入出力プロパティがある。 |
| 281C | プロパティ設定ファイルのURI(string) | プロパティ設定ファイルを読み込むことができない。 |
| 281D | 文字エンコーディング名(string) | サポートされない文字エンコーディング名を使おうとした。 |
| 281E | フォントファイルのURI(string) | フォントファイルを読み込むことができない。 |
| 281F | 対象のテキスト(string) | 使用可能なフォントがない。 |
| 28FF | プラグイン名(string)<br />メッセージ(string) | プラグインからの警告。 |
| 2820<span class="since">3.2.16</span> | 対象のテキスト(string) | CID-Keyedフォントまたは絵文字は<span class="cssdecl">background-clip: text;</span>で使えない。 |
| 2821<span class="since">4.0.0</span> | プロパティ名(string) | 静的な組版に意味がないため意図的に無視するCSSプロパティ(<span class="cssprop">cursor</span>、<span class="cssprop">transition</span>、<span class="cssprop">animation</span>等)。未対応(2802)とは区別される。 |
| 2822<span class="since">4.0.0</span> | プロパティ名(string)<br />出力形式(string)<br />近似の内容(string) | 対応しているが、その出力形式では厳密に描けず近似で描いた(PDF/A-1・PDF/Xでの<span class="cssprop">box-shadow</span>/<span class="cssprop">text-shadow</span>のぼかし——通常のPDFでは影を画素にして厳密に描きます<span class="since">4.0.0</span>——・SVGでの<span class="cssdecl">conic-gradient()</span>(PDFはメッシュシェーディングで厳密<span class="since">4.0.0</span>)・<span class="cssprop">mix-blend-mode</span>等)。PDFの<span class="cssprop">filter</span>は要素を画像にして厳密に描くが、その要素の文字が選択・検索できなくなるため、その旨をこの警告(内容 filter-rasterized)で知らせる<span class="since">4.0.0</span>。画像出力(PNG/JPEG)やSVG系出力では厳密に描けるものが多く、そのときは出ない。文書ごと・プロパティごとに1回。 |
| 2823<span class="since">4.0.0</span> | プロパティ名(string)<br />効かない理由(string) | 宣言は解釈できたが、その組み合わせでは効かない指定。浮動体・絶対配置の<span class="cssdecl">display: flex</span>/<span class="cssdecl">display: grid</span>は通常のブロックへ落ちる(itemは縦に積まれる)。未対応(2802)や意図的な無視(2821)と違い、<b>単体なら効くのに文脈のせいで落ちる</b>ものを知らせる。種類ごとに1回。 |
| 2824<span class="since">4.0.0</span> | 出力形式(string) | <span class="ioprop">output.image.transparent</span>が指定されたが、その出力形式は透明を保てないため背景を白のまま描いた。PNG・GIF・TIFFは保てる。JPEG・BMP・WBMPは保てない。文書ごとに1回。 |

**エラー**

| コード | 値 | 説明 |
| --- | --- | --- |
| 3001 | ドキュメントのURI(string) | メインドキュメントのURIの形式に不正がある。 |
| 3002 | エラーメッセージ(string) | 入出力エラー。 |
| 3801 | XSLTファイルのURI(string) | XSLTファイルの形式に不正がある。 |
| 3802 | 制限値(string) 設定値(double) | ページサイズの設定が制限を超えている。 |
| 3803 | エラーメッセージ(string) | XMLの形式に不正がある。 |
| 3804 | バイト数(long) | 出力ファイルの大きさが制限値を超えている。 |
| 3805 | 制限ページ数(int) | 出力ページ数が制限を超えている。 |
| 3806 | ドキュメントのURI(string) | サーバー側のメインドキュメントが存在しない。 |
| 3808 | XSLTファイルのURI(string) メッセージ(string) | XSLTプロセッサの警告メッセージ。 |
| 3809 | XSLTファイルのURI(string) メッセージ(string) | XSLTプロセッサのエラーメッセージ。 |
| 38FF | プラグイン名(string)<br />メッセージ(string) | プラグインからのエラー。 |
| 380D<span class="since">3.0.0</span> | ドキュメントの内容が空なのでページを生成できない。 |  |
| 380E<span class="since">4.0.0</span> | プロパティ名(string) 値(string) 理由(string) | PDF/Xの出力インテント指定が不正(ICCプロファイルを読めない・出力用でない・CMYKでない・識別名が空)。 |
| 380F<span class="since">4.0.0</span> | 要素名(string) 上限(int) 到達値(int) | 寸法が決まるまで中身を溜めておく要素(表・浮動体など)の内容が<span class="ioprop">processing.retained-text-limit</span>を超えた。変換は失敗する。 |

**深刻なエラー**

| コード | 値 | 説明 |
| --- | --- | --- |
| 4001 | エラーメッセージ(string) | 予期しないエラー。 |
| 4801 | XSLTファイルのURI(string) メッセージ(string) | XSLTプロセッサの致命的エラーメッセージ。 |
| 48FF | プラグイン名(string)<br />メッセージ(string) | プラグインからの致命的エラー。 |

#### メッセージコードのフィルタリング

ドライバには全てのメッセージが送られます。
必要なメッセージだけを扱いたい場合は、**受け取り側で絞り込んでください**。
メッセージハンドラにはメッセージコードが渡されるので、
コードの範囲(例: 3000〜3FFFは警告)で振り分けることができます。
実装方法は**開発マニュアル**(サーバー製品の説明書)の各言語の項を参照してください。
