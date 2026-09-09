## <a id="appx-ioprops">入出力プロパティ一覧</a>

以下は、各種プログラミング言語からこの組版エンジンにアクセスする際に設定できるプロパティの一覧です。 プロパティの設定方法の詳細は**開発マニュアル**(サーバー製品の説明書)をご参照ください。

**入力関連プロパティ**

| 名前 | デフォルト | バージョン | 説明 |
| --- | --- | --- | --- |
| <a id="appx-ioprop-input.default-encoding"></a>input.default-encoding | JISAutoDetect<br /> 3.0.1ではJISUniAutoDetectがデフォルト | 1.0.0 | HTMLのMETA要素でキャラクタ・エンコーディングを判断できない場合に使用するキャラクタ・エンコーディング名です。<br /> 独自のエンコーディング名JISUniAutoDetectを設定すると、 ISO-2022-JP, UTF-8, Windows-31J, EUC_JP_Solaris を自動判別するため、JISAutoDetectと違ってUTF-8を認識し、 機種依存文字が文字化けしにくくなります。<span class="since">3.0.1</span> |
| <a id="appx-ioprop-input.include"></a>input.include | - | 3.0.0 | 読み込みを許可するリソースのURIパターンです。 このプロパティを一度でも設定すると、パターンに一致しないリソース (スタイルシート、画像など)は読み込まれなくなります。 '*'は'/'以外の任意の文字列に、'**'は'/'を含む任意の文字列にマッチします。<br /> このプロパティは何度でも設定でき、設定した順に評価されて<b>最初に一致したものが適用されます</b>。許可と禁止を併用するときは、<b>禁止を先に書いてください</b>。<br /> <span class="since">3.5.0</span>プログラムから独自のリソース取得手段(ソースリゾルバ)を設定した場合も、この制限が先に適用されます。以前はローカルファイル(file:)がその取得手段で解決され、制限を通りませんでした。<br /> <span class="since">4.0.0</span>SVGの中からの取得(CSSの<tt>@import</tt>と<tt>&lt;?xml-stylesheet?&gt;</tt>、色プロファイル、外部文書)にも、この制限が適用されます。以前はSVG処理系自身の判定(取得先のホストが文書と同じなら許す)しか通らず、文書自体をHTTPで配信していると、そのホストの任意のパスから取得できました。<br /> <span class="since">4.0.0</span>XSLTの<tt>unparsed-text()</tt>にも適用されます。これまではこの関数は<b>内部エラーで使えませんでした</b>が、同時に使えるようにしています。<br /> <span class="since">4.0.0</span>HTTPの<b>リダイレクト先にも適用されます</b>。許可した範囲の外へ転送されると、そこで取得を止めます。HTTPSからHTTPへの格下げも追いません。 |
| <a id="appx-ioprop-input.exclude"></a>input.exclude | - | 3.0.0 | 読み込みを禁止するリソースのURIパターンです。 パターンの書き方は<span class="ioprop">input.include</span>と同じです。 |
| <a id="appx-ioprop-input.size-limit"></a>input.size-limit | - | 4.0.0 | 主文書1件の最大入力サイズ(バイト)です。既定では無制限です。EPUBではEPUBファイル全体を数えます。詳細は<a href="#prog-input-size-limit" class="pageref">入力容量と外部資源の制限</a>を参照してください。 |
| <a id="appx-ioprop-input.resource-size-limit"></a>input.resource-size-limit | - | 4.0.0 | 主文書から解決した外部資源の累積最大入力サイズ(バイト)です。既定では無制限です。 |
| <a id="appx-ioprop-input.resource-count-limit"></a>input.resource-count-limit | - | 4.0.0 | 主文書から解決する相異なる外部資源URIの最大数です。既定では無制限です。 |
| <a id="appx-ioprop-input.default-stylesheet"></a>input.default-stylesheet | - | 1.0.0 | デフォルトのCSSスタイルシートのURIです。 このプロパティが指定されている場合、最初にデフォルトのスタイルシートが読み込まれます。 |
| <a id="appx-ioprop-input.image-metrics"></a>input.image-metrics | - | 4.0.0 | 画像の寸法をあらかじめ記したJSON(またはXML)のURIです。 記録は出力単位(pt)で<span class="ioprop">output.resolution</span>に依存し、依拠した解像度が違う寸法表は捨てて測り直します。 寸法しか要らないパス(多パス処理の最終パス以外)で画像資源を開かずに済むので、 リモート資源では取得の往復がそのまま無くなります。 ページ分割SVGが出力する`metrics.json`をそのまま渡せます。 4.0.0の開発中に出力していたXML形式も読めます。 4.0.0からは`metrics.json`に出力済み資源の同一性(内容ハッシュ・MIME型・画素数)も記録されるため、 <span class="ioprop">output.paged-svg.resources</span>=omitの再変換では**描画するパスでも画像を一度も開きません**。 読めない場合は警告を出して実測に戻ります。 詳細は<a href="#style-output-paged-svg" class="pageref">ページ分割SVGの出力</a>を参照してください。 |
| <a id="appx-ioprop-input.epub.spine"></a>input.epub.spine | - | 4.0.0 | EPUBのどのspine項目を組むかです。空(既定)なら全項目。値は空白または`,`で区切った並びで、各要素はOPFの`idref`、項目のパス(`OEBPS/ch3.xhtml`または`ch3.xhtml`)、1起点の番号、番号の範囲(`3-5`)のどれかです。どれにも当たらない要素は警告して無視します。電子書籍の読み器が文字サイズを変えたとき、**いま読んでいる章だけを組み直す**ための入口です。項目は互いに独立に組まれるので、1章だけ組んだ結果は全体を通したときのその章と同一になり、ページ分割SVGでは項目の番号がspine内の位置で固定されている(`items/0003/`)ので部分の出力を全体の出力へそのまま重ねられます。 |
| <a id="appx-ioprop-input.viewport"></a>input.viewport | false | 3.0.0 | trueにすると、HTMLの&lt;meta name="viewport" ...&gt;で指定された大きさを ページの大きさとして扱います。<br /> <span class="ioprop">output.page-width</span>等による指定より優先されます。 |
| <a id="appx-ioprop-input.filters"></a>input.filters | xslt<br />default-to-xhtml<br />loose-html | 1.0.0 | 入力文書へ適用する前処理を、適用する順にスペース区切りで並べます。xslt, default-to-xhtml, loose-html が指定できます。<br />詳細は<a href="#style-input-filters" class="pageref">入力フィルタ</a>を参照してください。 |
| <a id="appx-ioprop-input.normalize-text"></a>input.normalize-text | false | 3.2.15 | "true"を設定すると、全てのテキストをNFC（正規化形式C）に正規化します。 |
| <a id="appx-ioprop-input.property-pi"></a>input.property-pi | false | 2.0.0 | trueを設定するとドキュメント中でjp.cssj.property-pi処理命令を使うことができるようになります。 |
| <a id="appx-ioprop-input.stylesheet.titles"></a>input.stylesheet.titles | - | 1.0.0 | 適用するCSSスタイルシートのタイトルをスペース区切りで並べます。 link要素またはxml-stylesheet処理命令で関連付けられたスタイルシートについて、 デフォルトでは代替スタイル以外が全て適用されますが、 このプロパティを用いて適用するスタイルシートを指定することができます。 |
| <a id="appx-ioprop-input.xslt.default-stylesheet"></a>input.xslt.default-stylesheet | - | 1.2.0 | デフォルトのXSLTスタイルシートのURIです。 このプロパティが指定されている場合、最初にデフォルトのスタイルシートが読み込まれます。 input.filtersにxsltフィルタが存在するとき場合のみ有効です。 |

**HTTPアクセス関連プロパティ**

| 名前 | デフォルト | バージョン | 説明 |
| --- | --- | --- | --- |
| <a id="appx-ioprop-input.html.change-default-namespace"></a>input.html.change-default-namespace | false | 3.2.12 | falseの場合は文書のデフォルトの名前空間はXHTMLに強制されます。つまりxmlns="～"という指定は無視されます。 trueであればxmlns="～"という指定が有効となります。 |
| <a id="appx-ioprop-input.http.referer"></a>input.http.referer | true | 1.0.1 | HTTP通信でサーバー側のデータを取得するときにRefererヘッダを送るかどうかの指定です。 trueまたはfalseで指定します。<br /> falseを指定すると、Refererを用いて画像などのリソースへの直接アクセスを規制しているサイトでリソースにアクセスできなくなります。 |
| <a id="appx-ioprop-input.http.proxy.host"></a>input.http.proxy.host | - | 1.2.6 | プロクシのホスト名です。<br /> この設定するとHTTP通信でプロクシを用います。 |
| <a id="appx-ioprop-input.http.proxy.port"></a>input.http.proxy.port | 8080 | 1.2.6 | プロクシを使う際のポート番号です。<br /> この設定はinput.http.proxy.hostが設定されている場合のみ有効です。 |
| <a id="appx-ioprop-input.http.proxy.authentication.user"></a>input.http.proxy.authentication.user<br /> input.http.proxy.authentication.password<br /> | - | 1.2.6 | 認証が必要なプロクシサーバーでの認証情報(user,password)です。 この設定は<span class="ioprop">input.http.proxy.host</span>が設定されている場合のみ有効です。 |
| <a id="appx-ioprop-input.http.header."></a>input.http.header.<i>n</i>.name<br /> input.http.header.<i>n</i>.value<br /> | - | 2.0.0 | HTTP接続で送信するヘッダです。<br /> nは0から始まる通し番号で、nが同じ2つのプロパティで一組です。 nameはヘッダ名でvalueはヘッダの値です。<br /> 通し番号は0から開始してカウントしていき、必要な情報(name)が欠けていた時点で以降のパラメータは無効となります。 |
| <a id="appx-ioprop-input.http.authentication.preemptive"></a>input.http.authentication.preemptive | false | 1.2.6 | HTTP通信で認証を行う場合に、最初から認証情報を送るかどうかの設定です。 trueまたはfalseで指定します。<br /> trueを指定すると、Authorizationヘッダ等の認証情報を最初の接続で送ります。<br /> falseを指定すると、最初にサーバーから401レスポンスを受け取ってレルムや認証スキーマ等の情報を取得します。<br /> trueを指定した場合、Digest認証や複数のレルムが存在するサーバーで認証が行われなくなります。 |
| <a id="appx-ioprop-input.http.proxy.authentication.password"></a>input.http.proxy.authentication.password | - | 2.0.0 | プロクシの認証に使うパスワードです。 <span class="ioprop">input.http.proxy.authentication.user</span>と組で指定します。 |
| <a id="appx-ioprop-input.http.authentication."></a>input.http.authentication.<i>n</i>.host<br /> input.http.authentication.<i>n</i>.user<br /> input.http.authentication.<i>n</i>.password<br /> input.http.authentication.<i>n</i>.port<br /> input.http.authentication.<i>n</i>.realm<br /> input.http.authentication.<i>n</i>.schema<br /> | - | 1.2.6 | HTTP認証の設定です。<i>n</i>は0から始まる連番で、<tt>.host</tt>(必須)、<tt>.port</tt>、<tt>.user</tt>(必須)、<tt>.password</tt>を指定します。<br />詳細は<b>BASIC認証またはDigest認証</b>(サーバー製品の説明書)を参照してください。 |
| <a id="appx-ioprop-input.http.cookie."></a>input.http.cookie.<i>n</i>.domain<br /> input.http.cookie.<i>n</i>.name<br /> input.http.cookie.<i>n</i>.value<br /> input.http.cookie.<i>n</i>.path<br /> | - | 1.2.6 | 送信するクッキーです。 nは通し番号で、nが同じ4つのプロパティで一組です。 domain,name,valueはそれぞれクッキーのドメインと名前、値です。 pathはクッキーのパスで、省略した場合はルート(/)となります。<br /> 通し番号は0から開始してカウントしていき、必要な情報(domainおよびname)が欠けていた時点で以降のパラメータは無効となります。 |
| <a id="appx-ioprop-input.http.connection.timeout"></a>input.http.connection.timeout<br /> | 60000 | 2.0.7 | HTTP接続の接続タイムアウト(ミリ秒)です。 設定した時間内に接続が確立されない場合は、接続エラーとします。 0の場合はタイムアウトなしです。 4.0.0からデフォルトが0(タイムアウトなし)から60000(60秒)に変わりました。 |
| <a id="appx-ioprop-input.http.cache"></a>input.http.cache | true | 4.0.0 | HTTP応答を変換をまたいでメモリ上にキャッシュするかどうかの指定です。 trueまたはfalseで指定します。<br /> 認証情報やCookieを伴う取得はキャッシュされません。 詳細は<b>応答キャッシュ</b>(サーバー製品の説明書)を参照してください。 |
| <a id="appx-ioprop-input.http.cache.ttl"></a>input.http.cache.ttl | 600 | 4.0.0 | HTTP応答キャッシュの保持期間(秒)です。 応答のCache-Controlヘッダにmax-ageがある場合は短い方が使われます。 0を指定するとキャッシュしません。 |
| <a id="appx-ioprop-input.http.socket.timeout"></a>input.http.socket.timeout<br /> | 60000 | 2.0.7 | HTTP接続のソケット通信タイムアウト(ミリ秒)です。 応答の開始または読み取りの継続が設定した時間以上停止した場合は、通信エラーとします。 0の場合はタイムアウトなしです。 4.0.0からデフォルトが0(タイムアウトなし)から60000(60秒)に変わりました。 |
| <a id="appx-ioprop-input.prefetch"></a>input.prefetch | true | 4.0.0 | 主文書から発見した外部リソース(スタイルシート・画像)を非同期に先読みするかどうかの指定です。 trueまたはfalseで指定します。<br /> 通常の変換ではリソースは必要になった時点で順番に取得されるため、リソースの多いウェブページの変換はHTTPの待ち時間が積み上がります。 既定で有効です。主文書の読み取りと並行してリソースを並列取得し、変換時間を大幅に短縮します(ブラウザの先読みと同様の動作です)。 falseにすると従来どおり必要になった時点で1つずつ取得します。<br /> 先読みするのはhttp/httpsのリソースのうち<span class="ioprop">input.include</span>/<span class="ioprop">input.exclude</span>の制限を通過するものだけです。 認証情報を送る要求は先読みしません。 詳細は<b>リソースの先読み</b>(サーバー製品の説明書)を参照してください。 |
| input.viewport | false | 3.1.0 | trueにすると、&lt;meta name="viewport"〜タグによりページサイズが設定されるようになります。 |

**出力関連プロパティ**

| 名前 | デフォルト | バージョン | 説明 |
| --- | --- | --- | --- |
| <a id="appx-ioprop-output.auto-height"></a>output.auto-height | false | 1.0.0 | 自動高さの指定です。falseまたはtrueで指定します。<br /> trueにすると、自動改ページをせず、ページの高さを文書の内容の高さに合わせます。 このとき、<span class="ioprop">output.page-height</span>（縦書きでは<span class="ioprop">output.page-width</span>）プロパティは無効になります。<br /> <a href="#style-page-layout">出力可能なページのサイズには制限があります。</a> |
| <a id="appx-ioprop-output.auto-rotate"></a>output.auto-rotate | none | 2.1.9 | 用紙と内容の向きが合わない場合の自動回転です。none(回転しない)、content(内容を回す)、paper(用紙の向きを入れ替える)のいずれかです。<br />詳細は<a href="#style-auto-rotate" class="pageref">縦長・横長が合わないとき</a>を参照してください。 |
| <a id="appx-ioprop-output.broken-image"></a>output.broken-image | none | 1.2.2<br />noneは2.0.0<br />annotationは2.1.2 | 画像を読み込めなかった場合の表示です。none, hidden, cross, annotation のいずれかです。<br />詳細は<a href="#style-image-broken" class="pageref">画像を読み込めない場合</a>を参照してください。 |
| <a id="appx-ioprop-output.clip"></a>output.clip | true | 2.0.3 | trueに設定した場合、印刷面の外側(トンボのドブの外側、あるいはページの外側)を描画しません。 falseに設定した場合、印刷面の外側を描画します。 |
| <a id="appx-ioprop-output.color"></a>output.color | rgb | 1.2.1<br />cmykは3.1.0 | 出力結果のカラー・タイプです。rgb,cmyk,grayで指定します。 rgbでは、指定通りのカラーで出力されます。 cmykでは全てCMYKカラーに変換されます(4.0.0から出力インテントのICCプロファイルによる変換。無彩色の単色はK単色、画像は全画素変換)。 grayでは、全てグレイスケールに変換されます。 |
| <a id="appx-ioprop-output.default-font-family"></a>output.default-font-family | serif | 2.0.0 | デフォルトのフォントファミリです。 ドキュメント中でフォントが指定されていない場合、 あるいは該当するフォントが見つからない場合、このフォントを使用します。<br /> CSSの<span class="cssprop">font-family</span>と同じ形式で複数のフォントを指定することができます。 空白を含むフォント名はクウォート('または")で囲うことに注意してください。 |
| <a id="appx-ioprop-output.expand-with-content"></a>output.expand-with-content | false | 3.2.1 | 内容がページに収まらない場合に、収まらない分だけページを広げます。<br />詳細は<a href="#style-expand-with-content" class="pageref">内容の分だけ紙を伸ばす</a>を参照してください。 |
| <a id="appx-ioprop-output.fit-to-paper"></a>output.fit-to-paper | false | 2.0.0<br /> preserve-aspect-ratioは2.1.9 | 用紙と印刷面の大きさが違うときの配置です。 trueに設定した場合、印刷面を用紙いっぱいに合わせます。 falseに設定した場合、中央寄せされます。 preserve-aspect-ratioを設定すると、縦横比を保ったまま用紙に合わせます。<br /> <b>倍率は「用紙÷印刷面」なので、印刷面のほうが大きければ縮小になります。</b> 紙より広く作られたページを用紙に収める手順は<a href="#style-fit-wide-page" class="pageref">紙より広く作られたページを用紙に収める</a>を参照してください。 |
| <a id="appx-ioprop-output.marks"></a>output.marks | none | 1.0.0<br />hiddenは1.2.1 | トンボおよび裁ち口の表示です。none,crop,cross,both,hiddenのいずれかを指定します。<br /> それぞれ、トンボ・裁ち口なし、コーナートンボを表示、センタートンボを表示、両方のトンボを表示、裁ち口だけを表示する、という意味になります。 |
| <a id="appx-ioprop-output.media_types"></a>output.media_types | all print paged visual bitmap static | 2.0.0 | 適用するスタイルシートのメディアタイプです。 |
| <a id="appx-ioprop-output.meta."></a>output.meta.<i>n</i>.name<br /> output.meta.<i>n</i>.value<br /> | - | 2.0.3 | 文書情報をあらかじめ設定します。 <i>n</i>は0から始まる通し番号で、<i>n</i>が同じ2つのプロパティで一組です。 <br /> 文書情報はドキュメント内の<tt>&lt;meta name="名前" content="値"&gt;</tt>要素によって上書きされます。 詳細は<a href="#style-xml-meta" class="pageref">文書情報</a>の節を参照してください。 |
| <a id="appx-ioprop-output.no-page-break"></a>output.no-page-break | false | 2.0.3 | trueに設定すると改ページを全くしなくなります。 <span class="ioprop">output.auto-height</span> をtrueに設定するのと異なり、ページの高さを内容に合わせて拡大しません。 |
| <a id="appx-ioprop-output.page-height"></a>output.page-height | 297mm | 1.0.0 | ページの高さです。デフォルトはA4の高さです。<br /> CSSの長さの単位(mm,cm,in,pt,pc,px)を使ってください。<br /> <a href="#style-page-layout">出力可能なページのサイズには制限があります。</a> |
| <a id="appx-ioprop-output.page-limit"></a>output.page-limit | - | 1.2.0 | 最大ページ数です。ページ数が限界に達すると、処理が中断されます。 デフォルトでは無制限です。 詳細は<a href="#prog-page-limit" class="pageref">ページ数の制限</a>の節を参照してください。 |
| <a id="appx-ioprop-output.page-limit.abort"></a>output.page-limit.abort | force | 3.0.11 | forceを設定すると、ページ数の限界に達した場合に結果を破棄します。normalを設定すると、できる限り途中までのファイルを出力します。 詳細は<a href="#prog-page-limit" class="pageref">ページ数の制限</a>の節を参照してください。 |
| <a id="appx-ioprop-output.page-margins"></a>output.page-margins | 12.7mm | 2.0.0 | ページの余白です。 CSSの<span class="cssprop">margin</span>プロパティと同じ形式で記述します。 長さの単位は(mm,cm,in,pt,pc,px)が使用可能です。 この設定は文書中の@pageルール内で上書きできます。 |
| <a id="appx-ioprop-output.type"></a>output.type | application/pdf | 2.0.3 | 出力するファイル形式のMIME型です。<br /> PDFは"application/pdf"、画像は"image/jpeg"、"image/png"、ページ分割SVGは"application/vnd.copper.paged-svg"、それを1本のZIPにまとめる場合は"application/vnd.copper.paged-svg+zip"を指定します。 詳細は<a href="#style-output" class="pageref">出力するファイル形式</a>を参照してください。 |
| <a id="appx-ioprop-output.page-width"></a>output.page-width | 210mm | 1.0.0 | ページの幅です。デフォルトはA4の横幅です。<br /> CSSの長さの単位(mm,cm,in,pt,pc,px)を使ってください。<br /> <a href="#style-page-layout">出力可能なページのサイズには制限があります。</a> |
| <a id="appx-ioprop-output.paper-height"></a>output.paper-height | output.page-heightの値 | 2.0.0 | 用紙の高さです。デフォルトはページの高さです。 用紙とページの大きさが異なる場合の動作は<span class="ioprop">output.fit-to-paper</span>の設定によります。<br /> CSSの長さの単位(mm,cm,in,pt,pc,px)を使ってください。<br /> <a href="#style-page-layout">出力可能なページのサイズには制限があります。</a> |
| <a id="appx-ioprop-output.paper-width"></a>output.paper-width | output.paper-widthの値 | 2.0.0 | 用紙の幅です。デフォルトはページの横幅です。<br /> 用紙とページの大きさが異なる場合の動作は<span class="ioprop">output.fit-to-paper</span>の設定によります。<br /> CSSの長さの単位(mm,cm,in,pt,pc,px)を使ってください。<br /> <a href="#style-page-layout">出力可能なページのサイズには制限があります。</a> |
| <a id="appx-ioprop-output.n-up"></a>output.n-up | 1 | 4.0.0 | 1枚の用紙に面付けする論理ページ数です。1で面付けを行いません。 指定した枚数が1枚の用紙に並べて配置されます。 |
| <a id="appx-ioprop-output.n-up.order"></a>output.n-up.order | horizontal | 4.0.0 | 面付けしたページの並び順です。 horizontal(行方向)、vertical(列方向)、 horizontal-reverse、vertical-reverseのいずれかです。 reverseを付けると逆順に並べます。 |
| <a id="appx-ioprop-output.marks.spine-width"></a>output.marks.spine-width | - | 4.0.0 | 背表紙の幅です。長さで指定します。 設定すると、トンボに背表紙の位置を示す線が引かれます。 |
| <a id="appx-ioprop-output.print-mode"></a>output.print-mode | double-side | 2.0.0<br /> left-side, right-sideは3.0.0 | 印刷モードです。single-side, double-side, left-side, right-sideのいずれかを指定します。<br /> single-sideでは片面印刷となり、@pageルールの:left, :right擬似クラスは適用されなくなります。<br /> left-side, right-sideでは、文書の横書き、縦書きに関わらず綴じ方向がどちらかに固定されます。 |
| <a id="appx-ioprop-output.resolution"></a>output.resolution | 96 | 2.0.0 | px単位の基準となる解像度です。<br /> ppi(1インチあたりのピクセル数)を指定します。<br /> 一般的なブラウザでは96という値が使われます。 72を指定すると1pt(PDFの基本単位)と1pxの長さが同じになります。 |
| <a id="appx-ioprop-output.size-limit"></a>output.size-limit | - | 1.2.0 | 出力データの最大サイズ(バイト)です。サイズが限界に達すると、処理が中断されます。 デフォルトでは無制限です。 |
| <a id="appx-ioprop-output.htrim"></a>output.htrim | 1cm | 2.0.0 | 左右の裁ち口の幅です。<br /> CSSの長さの単位(mm,cm,in,pt,pc,px)を使ってください。 |
| <a id="appx-ioprop-output.vtrim"></a>output.vtrim | 1cm | 2.0.0 | 上下の裁ち口の幅です。<br /> CSSの長さの単位(mm,cm,in,pt,pc,px)を使ってください。 |
| <a id="appx-ioprop-output.text-size"></a>output.text-size | 1.0 | 2.1.9 | 文字のサイズの拡大率（実数）です。<br /> 例えば 0.5 を設定すると、文字サイズが通常の半分になり、 2.0 を設定すると、2倍になります。 |
| output.type | application/pdf | 1.0.0 | 出力(MIME)形式です。 "application/pdf"(PDFファイル)は必ず利用することができます。<br /> 2.0.3から画像の出力に対応しました。画像の出力はJava Image I/Oに依存しており、 Java実行環境がサポートする画像形式("image/png"など)を利用することができます。 また、<a href="#style-image-jai">JAI-ImageI/O</a>等のプラグインをJava実行環境にインストールすることで、 利用可能な画像形式を追加することができます。<br /> 通常の画像出力では最後のページだけが出力されます。ページごとのSVGと共有資源を出力する場合は"application/vnd.copper.paged-svg"を指定します。コア14フォントとフォント設定ファイルのcid-keyed-font要素によるCID-Keyedフォントは通常の画像出力では正確に描画できません。 |
| <a id="appx-ioprop-output.svg.text"></a>output.svg.text | outline | 4.0.0 | 単一SVG出力(`image/svg+xml`)で文字をどう書くかです。<br/>`outline`(既定)は字形をアウトライン(path)にします。`keep`は`&lt;text&gt;`のまま残し、サブセットしたWOFF2と画像を`data:`でSVGへ埋め込みます。 |
| <a id="appx-ioprop-output.trim-inset"></a>output.trim-inset | - | 4.0.0 | 印刷面の外周のうち<b>塗り足しとして扱う帯の幅</b>です(仕上り線は印刷面の外周からこの幅だけ内側にあるとみなします)。<br/>塗り足し込みで作られた既存のデータを、CSSを書き換えずに正しい仕上りサイズで出力するために使います。長さの単位は(mm,cm,in,pt,pc,px)が使用可能です。 |
| <a id="appx-ioprop-output.trims"></a>output.trims | 1cm | 3.1.6 | 裁ち口の幅です。<br/>CSSの<span class="cssprop">margin</span>プロパティと同じ形式で記述します。長さの単位は(mm,cm,in,pt,pc,px)が使用可能です。 |

**画像出力関連プロパティ**

| 名前 | デフォルト | バージョン | 説明 |
| --- | --- | --- | --- |
| <a id="appx-ioprop-output.image.resolution"></a>output.image.resolution | 96 | 2.0.4 | <span class="ioprop">output.type</span>の設定によりラスター画像を出力する際の解像度(dpi)です。<br /> <span class="notice">なお、2.0.8以前ではデフォルト値が72となっており、解像度が正しく反映されないバグがありました。 2.0.9以降では 以前の設定 × <span class="ioprop">output.resolution</span> / 72) で換算した値を設定してください。 </span> |
| <a id="appx-ioprop-output.image.antialias"></a>output.image.antialias | true | 3.0.1 | ラスター画像出力の際のアンチエイリアスの設定です。 trueを設定するとアンチエイリアスを有効にします、 falseを設定するとアンチエイリアスを無効にします。 |
| <a id="appx-ioprop-output.image.transparent"></a>output.image.transparent | false | 4.0.0 | 画像出力で背景を塗らずに描くかどうかです。trueにすると、何も描かれなかったところは透明のまま残ります。<b>透明を保てる形式(PNG・GIF・TIFF)でだけ効きます。</b>保てない形式(JPEG・BMP・WBMP)で指定した場合は白のまま描き、<a href="#appx-messages" class="pageref">2824</a>で知らせます。 |
| <a id="appx-ioprop-output.use-meta-info"></a>output.use-meta-info | true | 3.1.8 | HTMLのMETA, TITLE要素により文書情報を設定します。 "false"にすると、この機能は無効になります。 |
| <a id="appx-ioprop-output.paged-svg.font-scope"></a>output.paged-svg.font-scope | document | 4.0.0 | フォントのサブセットを文書全体で1つにするか、ページごとに作るかです。EPUBでは`document`は**spine項目(含まれるXHTML)ごと**に1つになり、項目を組み終えるたびに出ます。 既定の`document`は総量が最小ですが、どの字形が要るかは全ページを組み終えるまで確定しないので**サブセットは最後にしか出せず、受け手は変換が終わるまで1文字も描けません**。 `page`はページごとに作って**そのページのSVGより先に**出すので、1ページ目が届いた時点で描けます。 実測(和文350ページ): 出力全体が9.2MB→16.1MB(1.75倍)、変換時間+14%。ただし3ページだけ読むなら340KB→150KBで逆転します(分岐点は12〜13ページ)。 |
| <a id="appx-ioprop-output.paged-svg.base-uri"></a>output.paged-svg.base-uri | ../ | 4.0.0 | ページSVGから共有資源(フォントのサブセットと画像)を指すときの前置きです。 既定の`../`は`pages/`から見た相対で、1ページを1文書として開く場合に解決します。 **複数のページSVGを1つのHTML文書へ取り込む読み器では基底が変わって解決に失敗し、本文が私用領域の文字なのでページが丸ごと空白に見えます。** 絶対URLの前置き(`https://example.com/book/`)を与えれば取り込み先がどこでも解決します。 末尾の`/`は無ければ補います。フォントにも画像にも同じ前置きが付きます。 |
| <a id="appx-ioprop-output.paged-svg.compression"></a>output.paged-svg.compression | gzip | 4.0.0 | ページSVGとページJSONをgzipで縮めて返すかどうかです。 既定のgzipではページSVGは`.svgz`、ページJSONは`.json.gz`という名前になり、314ページの縦組み書籍の実測で出力全体が15.0MBから6.6MBへ56%減ります。変換時間はほとんど変わりません。 共有WOFF2とPNG/JPEGは既に圧縮済みなので触りません。`manifest.json`は読み口なのでそのままです。 manifestの`sha256`は**縮めた後のバイト**に対する値です。 |
| <a id="appx-ioprop-output.paged-svg.resources"></a>output.paged-svg.resources | reference | 4.0.0 | ページ分割SVGの共有資源(フォントのサブセットと画像)の渡し方です。**フォントと画像はまとめて決まります。** referenceは別ファイルにして`../assets/…`で参照し、同じ画像の実体は1つで済みます。 embedは`data:`で埋め込み、別ファイルを出しません——相対URIを保てない送り方のためのもので、同じ画像がページごとに複製されるため全体の容量は増えます。フォントはembedでも共有WOFF2への参照のままです。 omitは参照だけ書いて実体を返しません。manifest.jsonの記載は残るので、同じ本を組み直すとき受信側が前回の資源を再利用できます。 sourceは**ウェブ上の画像を複写せず、取得元のURLをそのまま参照**します<span class="since">4.0.0</span>——ウェブの内容をSVGにして同じウェブで見せる用途のためのもので、取得元が`http:`/`https:`/`file:`のラスタ画像はページSVGが`<image href="取得元のURL">`と書き、manifest.jsonの`images[]`に`source`が付きます。取得元の無い画像(`data:`・生成した絵・SVGをラスタ化したもの)とフォントはreferenceと同じく共有資源に出ます。元のサーバーへの直接参照になるので、非公開のURLや認証付きの資源は読み器から取れません。**通信量と保管量のための指定で、速さのための指定ではありません**——314ページの書籍(1パス)の実測で121ms(6%)しか変わらず、出力は15.0MBから10.9MBへ27%減ります。 |
| <a id="appx-ioprop-output.paged-svg.image.compression"></a>output.paged-svg.image.compression | none | 4.0.0 | ページ分割SVGの共有画像(`assets/images/`)の圧縮方針です。既定の`none`は取ってきた画像をそのまま出します(JPEGはJPEGのまま、それ以外はPNG)。 `jpeg`は透明部分の無いラスタ画像をJPEG(品質0.8)に再圧縮します。既にJPEGの画像は縮小しない限り触りません。 小さい画像(<span class="ioprop">output.paged-svg.image.compression.lossless</span>の閾値以下)と透明部分のある画像は可逆(PNG)のままです。SVG画像はベクタのままなので対象外です。 実測(Wikipedia記事、Wikimediaの画像入り215頁、画像76件): 元がJPEGのサムネイル(最大500px)ばかりなので`jpeg`だけでは画像の合計が2.09MB→2.01MB(4%減)ですが、幅・高さの上限250pxと組み合わせると1.24MB(41%減)になります。 共有画像のURIは**出力したバイト**のSHA-256なので、方針を変えるとURIも変わります。 |
| <a id="appx-ioprop-output.paged-svg.image.compression.lossless"></a>output.paged-svg.image.compression.lossless | 200 | 4.0.0 | <span class="ioprop">output.paged-svg.image.compression</span>=jpegのとき、非可逆圧縮を適用する画像サイズの閾値です。指定されたサイズ(縦のピクセル数と横のピクセル数を足したもの)より小さければ可逆(PNG)のままにします。<span class="ioprop">output.pdf.image.compression.lossless</span>と同じ意味です。 |
| <a id="appx-ioprop-output.paged-svg.image.max-width"></a>output.paged-svg.image.max-width | 無制限 | 4.0.0 | ページ分割SVGの共有画像の横方向の最大ピクセル数(整数)です。アスペクト比を維持して、画像の幅がこのピクセル数に収まるように縮小します。 解像度を制限するためのもので、表示上の大きさは変わりません。縮小した画像は元がJPEGならJPEG、それ以外はPNGで出します。<span class="ioprop">output.pdf.image.max-width</span>と同じ意味です。 |
| <a id="appx-ioprop-output.paged-svg.image.max-height"></a>output.paged-svg.image.max-height | 無制限 | 4.0.0 | ページ分割SVGの共有画像の縦方向の最大ピクセル数(整数)です。<span class="ioprop">output.paged-svg.image.max-width</span>と同様です。 |
| <a id="appx-ioprop-output.paged-svg.page-checksums"></a>output.paged-svg.page-checksums | true | 4.0.0 | `manifest.json`の`pages[]`に各ページのSHA-256(`svgSha256`・`dataSha256`)を書くかです。受け手が完全性の確認や変わったページの検出に使わないなら`false`にすると`manifest.json`が縮みます(1ページあたり64桁のハッシュ2本≒150バイト。310ページで143KBのmanifestの大半がこれです)。共有資源(フォント・画像)の`sha256`はURIと同一性の鍵なので常に書きます。 |
| <a id="appx-ioprop-output.paged-svg.pdf"></a>output.paged-svg.pdf | false | 4.0.0 | ページ分割SVGと**同じ組版からPDFも出す**かです。`true`にすると結果集合に`document.pdf`が加わり(ZIPで返すときはZIPの中に)、`manifest.json`に`pdf`が付きます。 組版は1回で、各ページの描画をページSVGとPDFの両方へ流すので、別々に2回変換するより速く、頁割りは必ず一致します。 PDFの書き方は`output.pdf.*`(<span class="ioprop">output.pdf.fonts.policy</span>など)に従います。フォントの方針を指定しなければページSVGと同じ埋め込み(`core,embedded`)で、PDFは文字として(輪郭にせず)書かれます。 PDFは変換の最後に1件で出ます(ページSVGは従来どおり逐次)。EPUB(項目ごとのバンドル)では効きません。 |

<table class="spec">
		<caption>PDF出力関連プロパティ</caption>
		<thead>
			<tr>
				<th>名前</th>
				<th>デフォルト</th>
				<th>バージョン</th>
				<th>説明</th>
			</tr>
		</thead>
		<tbody>
			<tr id="appx-ioprop-output.pdf.attachments.">
				<td class="nowrap">output.pdf.attachments.<i>n</i>.name<br />
					output.pdf.attachments.<i>n</i>.description<br />
					output.pdf.attachments.<i>n</i>.mime-type<br />
					output.pdf.attachments.<i>n</i>.uri<br />
					output.pdf.attachments.<i>n</i>.relationship<span class="since">4.0.0</span><br />
				</td>
				<td>なし</td>
				<td class="nowrap">1.2.0<br />(PDF 1.4)
				</td>
				<td>PDFへ添付するファイルの設定です。<i>n</i>は0から始まる連番で、<tt>.uri</tt>(必須)、<tt>.name</tt>、<tt>.mime-type</tt>、<tt>.description</tt>を指定します。<br /><tt>.relationship</tt>は添付と文書の関係(PDF/A-3のAFRelationship)で、alternative, data, source, supplement, unspecifiedのいずれかです。電子インボイスの請求書XMLにはalternativeを指定します。<br />詳細は<a href="#style-pdf-attachments" class="pageref">ファイルの添付</a>を参照してください。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.facturx">
				<td class="nowrap"><span id="appx-ioprop-output.pdf.facturx.conformance-level">output.pdf.facturx.conformance-level</span><br />
					<span id="appx-ioprop-output.pdf.facturx.document-type">output.pdf.facturx.document-type</span><br />
					<span id="appx-ioprop-output.pdf.facturx.document-file-name">output.pdf.facturx.document-file-name</span><br />
					<span id="appx-ioprop-output.pdf.facturx.version">output.pdf.facturx.version</span><br />
				</td>
				<td>なし<br />INVOICE<br />factur-x.xml<br />1.0</td>
				<td class="nowrap">4.0.0<br />(PDF/A-3)
				</td>
				<td>電子インボイス(Factur-X / ZUGFeRD)のメタデータ設定です。<tt>.conformance-level</tt>(MINIMUM, BASIC WL, BASIC, EN 16931, EXTENDEDのいずれか)を設定すると、電子インボイス検証器が要求するXMP拡張スキーマが出力されます。<br />請求書XML自体は<tt>output.pdf.attachments.<i>n</i>.*</tt>で<tt>relationship=alternative</tt>・<tt>name</tt>を<tt>.document-file-name</tt>と同名にして添付してください。<tt>output.pdf.version</tt>は1.7A-3(PDF/A-3)を推奨します。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.output-intent">
				<td class="nowrap"><span id="appx-ioprop-output.pdf.output-intent.identifier">output.pdf.output-intent.identifier</span><br />
					<span id="appx-ioprop-output.pdf.output-intent.condition">output.pdf.output-intent.condition</span><br />
					<span id="appx-ioprop-output.pdf.output-intent.registry">output.pdf.output-intent.registry</span><br />
					<span id="appx-ioprop-output.pdf.output-intent.info">output.pdf.output-intent.info</span><br />
					<span id="appx-ioprop-output.pdf.output-intent.icc-profile">output.pdf.output-intent.icc-profile</span><br />
				</td>
				<td>なし<br />なし<br />http://www.color.org<br />なし<br />なし</td>
				<td class="nowrap">4.0.0<br />(PDF 1.4)
				</td>
				<td>出力インテント(/OutputIntents——想定する印刷条件)の設定です。<tt>.identifier</tt>(JC200103、FOGRA39などICCレジストリの特性化識別名)を設定すると出力されます。印刷所の指定に合わせてください。<br /><tt>.icc-profile</tt>にICCプロファイルのURIを指定すると、DestOutputProfileとして埋め込まれます(色成分数はプロファイルヘッダから自動判別)。未登録の印刷条件では<tt>.info</tt>の指定を推奨します。<br />未指定のとき、PDF/X(全版)とoutput.color=cmykでは同梱のISO Coated v2 300% (ECI)(FOGRA39、CMYK)が、それ以外ではsRGBが使われます。PDF/XではICCプロファイルを完全に検証し、読めない・出力用(prtr)でない・CMYKでない・識別名が空ならエラー380Eになります(PDF/X以外は警告)。RGB→CMYKの変換にもこのプロファイルが使われます。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.rendering-intent">
				<td class="nowrap">output.pdf.rendering-intent</td>
				<td>なし</td>
				<td class="nowrap">4.0.0<br />(PDF 1.4)
				</td>
				<td>既定のレンダリングインテントです。perceptual, relative-colorimetric, saturation, absolute-colorimetricのいずれかを指定すると、各ページのコンテンツストリーム先頭でri演算子として出力されます。output.color=cmykやPDF/X-1aでのRGB→CMYK変換はperceptual固定で、この設定には影響されません。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.bookmarks">
				<td class="nowrap">output.pdf.bookmarks</td>
				<td>false</td>
				<td class="nowrap">1.0.0<br />(PDF 1.2)
				</td>
				<td>ブックマーク機能です。falseまたはtrueで指定します。<br />
					trueにすると、H1〜H6要素をもとにブックマーク(アウトライン)を生成します。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.compression">
				<td class="nowrap">output.pdf.compression</td>
				<td>binary</td>
				<td class="nowrap">1.0.0<br />(PDF 1.2)
				</td>
				<td>圧縮方法です。none,ascii,binaryで指定します。<br /> 後者ほど圧縮効率がよくなります。
					noneでは画像以外は圧縮せず、asciiでは画像以外の内容も圧縮されますが、生成されるPDFはテキストファイルとなります。
					binaryの場合、生成されるPDFは圧縮され、かつバイナリ形式となります。<br />
					ただし、暗号化を行う場合は、結果的に全てバイナリとなることに注意してください。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption">
				<td class="nowrap">output.pdf.encryption</td>
				<td>none</td>
				<td class="nowrap">1.2.0<br /> (PDF 1.2)<br /> (v2はPDF 1.3)
				</td>
				<td>暗号化の方式です。none, v1, v2, v4, v5 のいずれかです。新しく作るなら v5(AES-256、PDF 1.7以降)を選んでください。<br />詳細は<a href="#style-pdf-encryption" class="pageref">暗号化</a>を参照してください。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.tagged">
				<td class="nowrap">output.pdf.tagged</td>
				<td>false</td>
				<td class="nowrap">4.0.0</td>
				<td>タグ付きPDF(論理構造)を出力するかどうかです。<br />
					trueで有効になり、テキスト・画像・図形に構造とマークコンテンツが付与されます(アクセシビリティの基盤)。<br />
					PDF/A level A(1.7A-2a/1.7A-3a)およびPDF/UA(1.7UA-1/2.0UA-2)では自動的に有効になります。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.tagged.lang">
				<td class="nowrap">output.pdf.tagged.lang</td>
				<td>(なし)</td>
				<td class="nowrap">4.0.0</td>
				<td>タグ付きPDF / PDF/UAの文書言語(BCP 47、例 "ja")です。<br />
					PDF/UA(1.7UA-1/2.0UA-2)では言語の指定が必須です。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.bidi.actual-text">
				<td class="nowrap">output.pdf.bidi.actual-text</td>
				<td>false</td>
				<td class="nowrap">4.0.0</td>
				<td>右横書きを含み並べ替えた行に、論理順の文字列をActualTextとして付け、鏡像化した括弧類に論理文字のToUnicodeを持つ別CIDを使うかどうかです。<br />
					既定では付けません(ChromeやEdgeのPDF閲覧は付けない方が正しく論理順を復元します)。
					Acrobatのようにアクティブに ActualText を尊重する抽出器向けの設定で、PDF 1.5以上でのみ有効です。<br />
					詳細は<a href="#style-pdf-bidi" class="pageref">双方向テキストの抽出</a>を参照してください。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.forms">
				<td class="nowrap">output.pdf.forms</td>
				<td>false</td>
				<td class="nowrap">4.0.0</td>
				<td>HTMLのフォーム部品を、PDF上で入力できるフォームフィールド(AcroForm)として出力するかどうかです。<br />詳細は<a href="#style-pdf-forms" class="pageref">入力できるPDFフォーム</a>を参照してください。</td>
			</tr>
			<tr>
				<td class="nowrap">output.pdf.encryption.length</td>
				<td>128</td>
				<td class="nowrap">1.2.0<br />(PDF 1.3)
				</td>
				<td>暗号化キーの長さ(ビット)です。<br /> output.pdf.encryption=v1では40で固定です。
					v2では40から128の間で、8ビット刻みで指定可能です。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.user-password">
				<td class="nowrap">output.pdf.encryption.user-password</td>
				<td>空</td>
				<td class="nowrap">1.2.0<br />(PDF 1.2)
				</td>
				<td>文書を開くためのパスワードです。
					このパスワードを使って文書を閲覧する場合は、文書に設定されたパーミッションによる制限がかかります。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.owner-password">
				<td class="nowrap">output.pdf.encryption.owner-password</td>
				<td>ユーザーのパスワード</td>
				<td class="nowrap">1.2.0<br />(PDF 1.2)
				</td>
				<td>文書の権限を変更するためのパスワード(マスタパスワード)です。 文書に対するあらゆる操作を可能にします。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.print">
				<td class="nowrap">output.pdf.encryption.permissions.<br />print
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.2)
				</td>
				<td>文書を印刷する権限です。<br /> true=許可,false=禁止 です。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.modify">
				<td class="nowrap">output.pdf.encryption.permissions.<br />modify
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.2)
				</td>
				<td>文書中の内容を変更をする権限です。<br /> true=許可,false=禁止 です。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.copy">
				<td class="nowrap">output.pdf.encryption.permissions.<br />copy
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.2)
				</td>
				<td>文書中のテキストや画像をコピーする権限です。<br /> true=許可,false=禁止 です。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.add">
				<td class="nowrap">output.pdf.encryption.permissions.<br />add
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.2)
				</td>
				<td>注釈を追加・変更する、あるいはフォームに入力する権限です。
					output.pdf.encryption.permissions.modify=trueであればフォームの追加・変更も許可されます。<br />
					true=許可,false=禁止 です。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.fill">
				<td class="nowrap">output.pdf.encryption.permissions.<br />fill
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.3)
				</td>
				<td>フォームに入力する権限です。<br /> output.pdf.encryptionがv2のときだけ有効です<br />
					true=許可,false=禁止 です。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.extract">
				<td class="nowrap">output.pdf.encryption.permissions.<br />extract
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.3)
				</td>
				<td>障害のあるユーザーのために文書中のテキストや画像を抽出する権限です。<br />
					output.pdf.encryptionがv2のときだけ有効です<br /> true=許可,false=禁止 です。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.assemble">
				<td class="nowrap">output.pdf.encryption.permissions.<br />assemble
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.3)
				</td>
				<td>文書中に新しいページ、ブックマーク、サムネイル画像を追加する権限です。<br />
					output.pdf.encryptionがv2のときだけ有効です<br /> true=許可,false=禁止 です。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.print-high">
				<td class="nowrap">output.pdf.encryption.permissions.<br />print-high
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.3)
				</td>
				<td>文書を高画質で印刷する権限です。<br /> output.pdf.encryptionがv2のときだけ有効です<br />
					true=許可,false=禁止 です。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.length">
				<td>128</td>
				<td class="nowrap">2.0.0</td>
				<td class="nowrap">output.pdf.encryption.length</td>
				<td>暗号鍵の長さ(ビット数)です。<br />
					<span class="ioprop">output.pdf.encryption</span>がv2(Arcfour)のときに40〜128の範囲で指定します。
					v4・v5では無視されます(それぞれ128ビット・256ビット固定です)。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.v4.cfm">
				<td class="nowrap">output.pdf.encryption.v4.cfm</td>
				<td>V2</td>
				<td class="nowrap">3.0.0</td>
				<td><span class="ioprop">output.pdf.encryption</span>がv4のときの暗号方式です。
					`v2`はArcfour、`aesv2`はAES-128です。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.platform-encoding">
				<td class="nowrap">output.pdf.platform-encoding</td>
				<td>MS932</td>
				<td class="nowrap">2.0.0</td>
				<td>PDFの内部で名前(ファイル名など)を表すときに使うキャラクタ・エンコーディングです。<br />
					PDFを開く環境に合わせて指定します。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.file-id">
				<td class="nowrap">output.pdf.file-id</td>
				<td>ランダムに生成</td>
				<td class="nowrap">2.0.9</td>
				<td>PDFのファイルIDを設定します。32桁固定の16進数を使用してください。<br /> 例:<br />
					"000067A36902BF8D2A0617B9CD02BCFA"
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.fonts.policy">
				<td class="nowrap">output.pdf.fonts.policy</td>
				<td>cid-keyed</td>
				<td class="nowrap">1.1.0<br/>outlinesは3.1.1<br />(PDF 1.2)
				</td>
				<td>使用するフォントの種類です。cid-keyed, cid-identity, embedded, outlines をスペース区切りで優先順に指定します。-core でコアフォントを除外します。<br />
					Paged SVG出力(<span class="ioprop">output.type</span>=application/vnd.copper.paged-svg)、単一SVG出力(image/svg+xml、outline・keepとも)、
					ラスタ画像出力(image/png、image/jpeg など)では、
					このプロパティを指定しない場合の既定が<b>embedded</b>になります <span class="since">4.0.0</span>。
					SVGや画像にはCID-Keyedフォントに相当する仕組みが無く、cid-keyedのままでは
					SVGでは文字がすべてアウトライン(パス)として出力されて出力が大きくなり、
					画像では字形データを持たないフォントがサーバーのシステムフォント(別の書体)で描かれるためです。
					明示的に指定した場合はその指定に従います。<br />詳細は<b>フォントの種類</b>(pdfg2d の説明書)を参照してください。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.hyperlinks">
				<td class="nowrap">output.pdf.hyperlinks</td>
				<td>false</td>
				<td class="nowrap">1.0.0<br />(PDF 1.2)
				</td>
				<td>ハイパーリンク機能です。falseまたはtrueで指定します。<br />
					trueにすると、PDFからWWWなどへのハイパーリンクが有効になります。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.hyperlinks.href">
				<td class="nowrap">output.pdf.hyperlinks.href</td>
				<td>relative</td>
				<td class="nowrap">1.1.0<br />(PDF 1.2)
				</td>
				<td>ハイパーリンクのアドレスの記述方法です。relativeまたはabsoluteで指定します。<br />
					relativeでは相対アドレス指定となり、HTMLのa要素のhref属性がそのまま使われます。
					absoluteでは絶対URIに変換されてPDFに反映されます。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.hyperlinks.base">
				<td class="nowrap">output.pdf.hyperlinks.base</td>
				<td>ドキュメントのURI</td>
				<td class="nowrap">2.0.0<br />(PDF 1.2)
				</td>
				<td>output.pdf.hyperlinks.hrefにrelativeを指定した場合の、基準となるURIです。
					output.pdf.hyperlinks.hrefがabsoluteの場合は、このプロパティは無効です。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.hyperlinks.fragment">
				<td class="nowrap">output.pdf.hyperlinks.fragment</td>
				<td>true</td>
				<td class="nowrap">2.0.0<br />(PDF 1.2)
				</td>
				<td>trueを設定するとHTMLの&lt;a
					name～あるいはid属性によりドキュメントフラグメントが配置され、URLのフラグメント識別子によって
					ドキュメント内の特定の場所へリンクすることができるようになります。<br />
					falseを設定した場合はドキュメントフラグメントを配置しません。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.image.compression">
				<td class="nowrap">output.pdf.image.compression</td>
				<td>flate</td>
				<td class="nowrap">2.0.3</td>
				<td>PDFへ埋め込む画像の圧縮形式です。flate, jpeg, jpeg2000 のいずれかです。<br />詳細は<a href="#style-pdf-image" class="pageref">PDF中の画像の圧縮形式</a>を参照してください。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.image.compression.lossless">
				<td class="nowrap">output.pdf.image.compression.lossless</td>
				<td>200</td>
				<td class="nowrap">2.0.3</td>
				<td><span class="ioprop">output.pdf.image.compression</span>
					により非可逆圧縮(JPEG形式等)を使用する場合、非可逆圧縮を適用する画像サイズの閾値です。
					指定されたサイズ(縦のピクセル数と横のピクセル数を足したもの)より小さければ可逆圧縮(FlateDecode)を使用します。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.image.max-width">
				<td class="nowrap">output.pdf.image.max-width</td>
				<td>無制限</td>
				<td class="nowrap">3.0.0</td>
				<td>PDFで使用される画像の横方向の最大ピクセル数（整数）です。
					アスペクト比を維持して、画像の幅がこのピクセル数に収まるように自動的に縮小します。
					これは解像度を制限するためのもので、表示上の物理的な大きさは変わりません。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.blur-resolution">
				<td class="nowrap">output.pdf.blur-resolution</td>
				<td>150</td>
				<td class="nowrap">4.0.0</td>
				<td>PDF出力で<span class="cssprop">box-shadow</span>・<span class="cssprop">text-shadow</span>の
					ぼかしを描く解像度(dpi、72〜600)です<span class="since">4.0.0</span>。
					PDFにはぼかしの演算子が無いため、影だけを画素にして透明度付きの画像として置きます
					(文字や本文はベクタのまま。影は<i>artifact</i>なのでタグ付きPDFや文字抽出に影響しません)。
					透明を使えないプロファイル(PDF/A-1、PDF/X)では従来どおり段階塗りの近似になり、警告2822が出ます。
					影は低周波なので既定の150dpiで十分です。上げると画像が大きくなります。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.filter-resolution">
				<td class="nowrap">output.pdf.filter-resolution</td>
				<td>300</td>
				<td class="nowrap">4.0.0</td>
				<td>PDF出力で<span class="cssprop">filter</span>(色の変換・<tt>blur()</tt>・<tt>drop-shadow()</tt>)の
					付いた要素を画像にする解像度(dpi、72〜600)です<span class="since">4.0.0</span>。
					要素の文字が含まれ得るので影のぼかし(<span class="ioprop">output.pdf.blur-resolution</span>)より高い既定です。
					画素数が1,600万を超える要素は効果を掛けずにベクタのまま描き、警告2822(内容 filter-limit)で知らせます。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.image.max-height">
				<td class="nowrap">output.pdf.image.max-height</td>
				<td>無制限</td>
				<td class="nowrap">3.0.0</td>
				<td>PDFで使用される画像の縦方向の最大ピクセル数（整数）です。
					アスペクト比を維持して、画像の高さがこのピクセル数に収まるように自動的に縮小します。
					これは解像度を制限するためのもので、表示上の物理的な大きさは変わりません。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.jpeg-image">
				<td class="nowrap">output.pdf.jpeg-image</td>
				<td>raw</td>
				<td class="nowrap">1.1.0<br />(PDF 1.2)
				</td>
				<td>PDFへ埋め込むJPEG画像の扱いです。raw(そのまま埋め込む)、to-flate(可逆圧縮へ変換)、recompress(再圧縮)のいずれかです。<br />詳細は<a href="#style-pdf-image" class="pageref">PDF中の画像の圧縮形式</a>を参照してください。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.meta.creation-date">
				<td class="nowrap">output.pdf.meta.creation-date</td>
				<td>サーバーの現在時刻</td>
				<td class="nowrap">2.0.9</td>
				<td>PDFのメタ情報のCreationDateを設定します。<br /> 設定例:<br /> "2009-05-22
					21:10:14"<br /> "2009-06-04 15:53:02 +09:00" (タイムゾーンを明示する場合)<br />
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.meta.mod-date">
				<td class="nowrap">output.pdf.meta.mod-date</td>
				<td>output.pdf.meta.creation-dateの値</td>
				<td class="nowrap">2.0.9</td>
				<td>PDFのメタ情報のModDateを設定します。<br /> 時刻の形式は<span class="ioprop">output.pdf.meta.creation-date</span>と同じです。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.open-action.java-script">
				<td class="nowrap">output.pdf.open-action.java-script</td>
				<td>-</td>
				<td class="nowrap">3.0.2/2.1.11<br />(PDF 1.2)
				</td>
				<td>文書を開いた時に実行するJavaScriptを設定します。</td>
			</tr>
			<tr>
				<td class="nowrap">output.pdf.platform-encoding</td>
				<td>MS932</td>
				<td class="nowrap">1.2.0<br />(PDF 1.2)
				</td>
				<td>PDFを表示する環境のプラットフォームのキャラクタ・エンコーディングです。<br />
					PDF1.2以前ではフォント名が影響を受けます。PDF1.3以降ではユニコードが使われるため無関係です。<br />
					PDF1.6以前では添付ファイル名が影響を受けます。
					ファイル名にマルチバイト文字が使われている場合、このエンコーディングが表示するプラットフォームのものと一致しないと文字化けします。
					日本語の文書であればMS932(Windows版Shift_JIS)、韓国語であればEUC-KR、繁体字中国語ではBig5といった指定をしてください。<br />
					PDF1.7以降ではユニコードが使われるため無関係です(2.0.3)。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.version">
				<td class="nowrap">output.pdf.version</td>
				<td>1.5</td>
				<td class="nowrap">1.1.0</td>
				<td>出力するPDFのバージョン、または準拠プロファイル(PDF/A・PDF/X・PDF/UA)です。1.2〜1.7と2.0のほか、1.4A-1・1.7A-2・1.7A-2u・1.7A-2a・1.7A-3・1.7A-3a・2.0A-4・1.4X-1・1.6X-4・2.0X-6・1.7UA-1・2.0UA-2 を指定できます。指定したバージョンで使えない機能は警告が出て反映されません。<br />値の意味と選び方、プロファイルを指定したときに自動的に変わる設定は<a href="#style-pdf-version" class="pageref">PDFのバージョンと機能</a>を参照してください。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.hide-toolber">
				<td class="nowrap">output.pdf.viewer-preferences.<br />hide-toolber
				</td>
				<td>false</td>
				<td class="nowrap">3.0.2/2.1.11</td>
				<td>ビューワアプリケーションのツールバーの非表示、表示を設定します。<br /> trueを設定すると非表示となります。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.hide-menubar">
				<td class="nowrap">output.pdf.viewer-preferences.<br />hide-menubar
				</td>
				<td>false</td>
				<td class="nowrap">3.0.2/2.1.11</td>
				<td>ビューワアプリケーションのメニューバーの非表示、表示を設定します。<br /> trueを設定すると非表示となります。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.hide-windowUI">
				<td class="nowrap">output.pdf.viewer-preferences.<br />hide-windowUI
				</td>
				<td>false</td>
				<td class="nowrap">3.0.2/2.1.11</td>
				<td>ビューワアプリケーションのウィンドウ内UI(サムネール、添付など)の非表示、表示を設定します。<br />
					trueを設定すると非表示となります。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.fit-window">
				<td class="nowrap">output.pdf.viewer-preferences.<br />fit-window
				</td>
				<td>false</td>
				<td class="nowrap">3.0.2/2.1.11</td>
				<td>内容に合わせてビューワアプリケーションのウィンドウサイズをフィットさせるかどうかを設定します。<br />
					trueを設定すると非表示となります。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.center-window">
				<td class="nowrap">output.pdf.viewer-preferences.<br />center-window
				</td>
				<td>false</td>
				<td class="nowrap">3.0.2/2.1.11</td>
				<td>内容に合わせてビューワアプリケーションのウィンドウサイズをスクリーンに対して中央表示させるかどうかを設定します。<br />
					trueを設定すると中央表示となります。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.display-doc-title">
				<td class="nowrap">output.pdf.viewer-preferences.<br />display-doc-title
				</td>
				<td>false</td>
				<td class="nowrap">3.0.2/2.1.11<br />PDF 1.4
				</td>
				<td>ビューワアプリケーションのタイトルバーに文書のタイトルを表示させるかどうかを設定します。<br />
					trueを設定すると表示します。
				</td>
			</tr>
			<tr
				id="appx-ioprop-output.pdf.viewer-preferences.non-full-screen-page-mode">
				<td class="nowrap">output.pdf.viewer-preferences.<br />non-full-screen-page-mode
				</td>
				<td>use-none</td>
				<td class="nowrap">3.0.2/2.1.11</td>
				<td>ビューワアプリケーションのサイドパネルの表示内容を設定します。
					<dl>
						<dt>use-none</dt>
						<dd>しおりかサムネイルパネルを表示します。</dd>
						<dt>use-outlines</dt>
						<dd>しおりパネルを表示します。</dd>
						<dt>use-thumbs</dt>
						<dd>サムネイルパネルを表示します。</dd>
						<dt>use-oc</dt>
						<dd>レイヤーパネルを表示します。</dd>
					</dl>
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.print-scaling">
				<td class="nowrap">output.pdf.viewer-preferences.<br />print-scaling
				</td>
				<td>app-default</td>
				<td class="nowrap">3.0.2/2.1.11<br />PDF 1.6
				</td>
				<td>ビューワアプリケーションの印刷設定の拡大縮小を設定します。
					<dl>
						<dt>scaling-none</dt>
						<dd>拡大縮小をしません。</dd>
						<dt>app-default</dt>
						<dd>拡大縮小をビューワに任せます。</dd>
					</dl>
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.duplex">
				<td class="nowrap">output.pdf.viewer-preferences.<br />duplex
				</td>
				<td>none</td>
				<td class="nowrap">3.0.2/2.1.11<br />PDF 1.7
				</td>
				<td>ビューワアプリケーションの印刷設定の片面・両面印刷の方法を設定します。
					<dl>
						<dt>none</dt>
						<dd>ビューワのデフォルト設定のままです。</dd>
						<dt>simplex</dt>
						<dd>片面印刷をします。</dd>
						<dt>flip-short-edge</dt>
						<dd>短辺綴じで両面印刷をします。</dd>
						<dt>flip-long-edge</dt>
						<dd>長辺綴じで両面印刷をします。</dd>
					</dl>
				</td>
			</tr>
			<tr
				id="appx-ioprop-output.pdf.viewer-preferences.pick-tray-by-pdf-size">
				<td class="nowrap">output.pdf.viewer-preferences.<br />pick-tray-by-pdf-size
				</td>
				<td>false</td>
				<td class="nowrap">3.0.2/2.1.11<br />PDF 1.7
				</td>
				<td>ビューワアプリケーションの印刷設定の「PDFのページサイズに合わせて用紙を選択」のチェック状態を設定します。<br />
					trueを設定するとチェックした状態になります。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.print-page-range">
				<td class="nowrap">output.pdf.viewer-preferences.<br />print-page-range
				</td>
				<td>-</td>
				<td class="nowrap">3.0.2/2.1.11<br />PDF 1.7
				</td>
				<td>初期の印刷対象ページを設定します。<br /> ページはカンマ区切りで "1,2,3,5"のように設定します。
					範囲をしていするためにハイフンを使って"1-3,5"のように設定することもできます。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.num-copies">
				<td class="nowrap">output.pdf.viewer-preferences.<br />num-copies
				</td>
				<td>0</td>
				<td class="nowrap">3.0.2/2.1.11<br />PDF 1.7
				</td>
				<td>初期の印刷枚数を設定します。 0ではビューワのデフォルトで、その他は2から5が有効な値です。
					6以上の枚数を設定することができません。</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.watermark.uri">
				<td class="nowrap">output.pdf.watermark.uri</td>
				<td>-</td>
				<td class="nowrap">2.1.8<br />PDF 1.4
				</td>
				<td>すかし画像のアドレスを、絶対パスで設定してください。<br />
					すかしはPDFの前面または背面に繰り返しパターンとして描画されます。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.watermark.mode">
				<td class="nowrap">output.pdf.watermark.mode</td>
				<td>back</td>
				<td class="nowrap">2.1.8<br />PDF 1.4
				</td>
				<td>すかし画像の配置方法です。
					<dl>
						<dt>front</dt>
						<dd>前面に配置</dd>
						<dt>back</dt>
						<dd>背面に配置</dd>
					</dl>
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.watermark.opacity">
				<td class="nowrap">output.pdf.watermark.opacity</td>
				<td>1</td>
				<td class="nowrap">2.1.8<br />PDF 1.4
				</td>
				<td>すかし画像の不透明度です。<br />0～1までの小数で指定します。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.watermark.view">
				<td class="nowrap">output.pdf.watermark.view</td>
				<td>true</td>
				<td class="nowrap">2.1.8<br />PDF 1.4(説明参照)
				</td>
				<td>すかし画像が、画面表示の場合に見えるようにします。<br />
					すかしを背面に配置する場合、false(非表示)を設定できるのはPDF 1.5以降です。
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.watermark.print">
				<td class="nowrap">output.pdf.watermark.print</td>
				<td>true</td>
				<td class="nowrap">2.1.8<br />PDF 1.4(説明参照)
				</td>
				<td>すかし画像が、印刷時に見えるようにします。<br />
					すかしを背面に配置する場合、false(非表示)を設定できるのはPDF 1.5以降です。
				</td>
			</tr>
		</tbody>
	</table>

**その他のプロパティ**

| 名前 | デフォルト | バージョン | 説明 |
| --- | --- | --- | --- |
| <a id="appx-ioprop-processing.fail-on-fatal-error"></a>processing.fail-on-fatal-error | true | 4.0.0 | 回復できないエラーが起きたときに変換を中断するかどうかです。<br /> falseにすると、エラーが起きてもそこまでの内容で出力を試みます。 出力される内容は不完全になることがあります。 |
| <a id="appx-ioprop-processing.text-spill-budget"></a>processing.text-spill-budget | 8388608 | 4.0.0 | 改ページの再生のために保持するテキストを、メモリ上に置いておく上限 (バイト数)です。<br /> これを超えた分は一時ファイルへ書き出されます。 <b>出力される内容はこの値によって変わりません</b>。変わるのはメモリの使い方だけです。 非常に長い文書でメモリが不足する場合に小さくしてください。 |
| <a id="appx-ioprop-processing.retained-text-limit"></a>processing.retained-text-limit | 8388608 | 4.0.0 | 表・浮動体・inline-block・グリッド/フレックス・段組・絶対配置のように、寸法が決まるまで中身を溜めておく要素1つに入れられる文字量の上限(文字数×2バイト)です。<br />超えると変換は失敗します(メッセージ380F)。<b>出力される内容はこの値によって変わりません</b>。1件の変換が溜め込むメモリに天井を置くための設定で、メモリ量そのものの保証ではありません。脚注やページフロートの配置待ち、<span class="cssprop">orphans</span>/<span class="cssprop">widows</span>の先読みは数えません。0以下は無制限です。<br />目安: 溜めた文字1つにつき40〜50バイトのメモリを使います(既定の8MBで約200MB)。ヒープが小さい構成(256MB以下)では4MB程度に下げてください。 |
| <a id="appx-ioprop-processing.table-row-emission"></a>processing.table-row-emission | false | 4.0.0 | trueを設定すると、自動レイアウト(<span class="cssdecl">table-layout: auto</span>)の大きな表を、行がページに収まるごとに順に確定して出力します(横組み・本文が1グループ・キャプションなど無しの単純な表に限ります)。確定した行を保持し続けないので、数千行の表でメモリを抑えられます。<b>出力される内容は変わりません</b>。既定はfalseです(検証中の機能のため)。 |
| <a id="appx-ioprop-processing.middle-pass"></a>processing.middle-pass | false | 3.0.4 | trueを設定すると、実際は結果を生成しない中間の処理を実行します。 後でfalseを設定してドキュメントを処理すると、結果が生成されます。<br /> 詳細は<a href="#style-multipass">2パス以上の変換処理</a>を参照してください。 |
| <a id="appx-ioprop-processing.page-references"></a>processing.page-references | false | 2.0.0 | trueを設定すると、目次、ページ参照のための情報を収集します。 falseを設定すると、目次、ページ参照のための情報を収集しないため一部の機能が利用できなくなります。<br /> 詳細は<a href="#style-page-references">ページの参照</a>を参照してください。 |
| <a id="appx-ioprop-processing.pass-count"></a>processing.pass-count | 1 | 1.2.0 | 1回のフォーマット処理のために、文書を処理する回数です。<br />総ページ数・目次・ページの参照・一部のセレクタは2以上でないと働きません。<b>設定が足りなくても警告は出ません。</b><br />詳細は<a href="#style-multipass" class="pageref">2パス以上の変換処理</a>を参照してください。 |
| <a id="appx-ioprop-processing.concurrency"></a>processing.concurrency | 0 | 4.0.0 | 独立に組める単位を同時にいくつ組むかです。いま効くのはEPUBのspine項目をページ分割SVGへ出すときだけです。`0`(既定)は自動で、CPUコア数と4の小さいほう。`1`で逐次。**いくつにしても出力は同一**です——項目は互いに独立で、結果はspine順に解放されるためで、変わるのは所要時間とメモリ(同時に組む項目の数だけレイアウトを保持します)だけです。 |
| <a id="appx-ioprop-processing.time-limit"></a>processing.time-limit | 0 | 4.0.0 | 文書1件の変換に許す最大経過時間(ミリ秒)です。0以下は無制限です。複数パスでは全パスを合わせた時間を数えます。 |
| <a id="appx-ioprop-layout.bidi.paragraph"></a>layout.bidi.paragraph | true | 4.0.0 | 右横書き(ヘブライ語・アラビア語など)の並べ替えを段落単位のUnicode双方向アルゴリズムで行うかどうかです。<br />falseにすると旧来の行単位(常に左横書き基底)の並べ替えに戻ります。左横書きだけの文書では出力は変わりません。 |

### <a id="appx-ioprops-nopi">文書中で設定できないプロパティ</a>

以下のリストにあるプロパティは<span class="ioprop">input.property-pi</span>の設定とは関係なく、
jp.cssj.property処理命令による設定ができません。

- input.http.で始まるプロパティ
- input.default-encoding
- input.property-pi
- output.type
- processing.pass-count
