## <a id="appx-ioprops">I/O property list</a>

The following is a list of properties you can set when accessing this layout engine from various programming languages. For details on setting properties, see the **Developer's guide** (server product manual).

**Input properties**

| Name | Default | Version | Description |
| --- | --- | --- | --- |
| <a id="appx-ioprop-input.default-encoding"></a>input.default-encoding | JISAutoDetect<br /> JISUniAutoDetect is the default in 3.0.1 | 1.0.0 | The character encoding name to use when the encoding cannot be determined from the HTML META element.<br /> The custom encoding name JISUniAutoDetect automatically detects ISO-2022-JP, UTF-8, Windows-31J, and EUC_JP_Solaris. Unlike JISAutoDetect, it recognizes UTF-8 and reduces garbled platform-dependent characters.<span class="since">3.0.1</span> |
| <a id="appx-ioprop-input.include"></a>input.include | - | 3.0.0 | URI patterns for resources that may be loaded. Once you set this property, resources (stylesheets, images, and so on) that do not match a pattern are no longer loaded. '*' matches any string except '/', and '**' matches any string including '/'.<br /> You can set this property any number of times. Rules are evaluated in the order you set them, and <b>the first match applies</b>. When you combine allow and deny rules, <b>write the deny rules first</b>.<br /> <span class="since">3.5.0</span>These restrictions also apply before a custom resource retrieval mechanism (source resolver) that you configure programmatically. Previously, local files (file:) were resolved by that mechanism without passing through the restrictions.<br /> <span class="since">4.0.0</span>These restrictions also apply to retrieval from within SVG (CSS <tt>@import</tt> and <tt>&lt;?xml-stylesheet?&gt;</tt>, color profiles, and external documents). Previously, only the SVG processor's own check applied (allowing retrieval from the same host as the document). If the document itself was served over HTTP, resources could be retrieved from any path on that host.<br /> <span class="since">4.0.0</span>They also apply to XSLT <tt>unparsed-text()</tt>. This function <b>previously failed with an internal error</b>; it has been made usable at the same time.<br /> <span class="since">4.0.0</span>They <b>also apply to HTTP redirect destinations</b>. Retrieval stops if a redirect leads outside the allowed range. Redirects that downgrade HTTPS to HTTP are not followed either. |
| <a id="appx-ioprop-input.exclude"></a>input.exclude | - | 3.0.0 | URI patterns for resources that must not be loaded. The pattern syntax is the same as for <span class="ioprop">input.include</span>. |
| <a id="appx-ioprop-input.size-limit"></a>input.size-limit | - | 4.0.0 | The maximum input size of a single main document, in bytes. There is no limit by default. For EPUB, the entire EPUB file is counted. For details, see <a href="#prog-input-size-limit" class="pageref">Input size and external resource limits</a>. |
| <a id="appx-ioprop-input.resource-size-limit"></a>input.resource-size-limit | - | 4.0.0 | The maximum cumulative input size, in bytes, of external resources resolved from the main document. There is no limit by default. |
| <a id="appx-ioprop-input.resource-count-limit"></a>input.resource-count-limit | - | 4.0.0 | The maximum number of distinct external resource URIs resolved from the main document. There is no limit by default. |
| <a id="appx-ioprop-input.image-pixel-limit"></a>input.image-pixel-limit | - | 4.0.0 | The maximum number of pixels (width × height) in a single image to load. There is no limit by default. The dimensions in the image header are checked <b>before</b> decoding the pixels. Images that exceed the limit are treated as unreadable images (the stage in message 2811 is <tt>too-large 12000x12000 &gt; 40000000</tt>, and <tt>&lt;img&gt;</tt> follows <span class="ioprop">output.broken-image</span>). If you set a limit, images whose dimensions cannot be read are also rejected. For GIF, the larger of the frame and screen dimensions is used. A small file can expand into a huge image, which <span class="ioprop">input.resource-size-limit</span> (bytes read) cannot stop. Set this limit as well on a shared server. |
| <a id="appx-ioprop-input.default-stylesheet"></a>input.default-stylesheet | - | 1.0.0 | The URI of the default CSS stylesheet. If you specify this property, the default stylesheet is loaded first. |
| <a id="appx-ioprop-input.image-metrics"></a>input.image-metrics | - | 4.0.0 | The URI of a JSON (or XML) file containing image dimensions recorded in advance. The dimensions use output units (pt) and depend on <span class="ioprop">output.resolution</span>. If a dimension table was based on a different resolution, it is discarded and the images are measured again. Passes that need only dimensions (all except the final pass in multi-pass processing) can avoid opening image resources, eliminating the retrieval round trips for remote resources. You can pass the `metrics.json` produced by page-split SVG output directly. The XML format produced during development of 4.0.0 can also be read. Since 4.0.0, `metrics.json` also records the identity of resources already output (content hash, MIME type, and pixel count), so reconversion with <span class="ioprop">output.paged-svg.resources</span>=omit **never opens an image, even in the rendering pass**. If the file cannot be read, a warning is issued and actual measurement is used again. For details, see <a href="#style-output-paged-svg" class="pageref">Page-split SVG output</a>. |
| <a id="appx-ioprop-input.epub.spine"></a>input.epub.spine | - | 4.0.0 | Selects which EPUB spine items to lay out. An empty value (the default) selects all items. The value is a sequence separated by whitespace or `,`. Each entry can be an OPF `idref`, an item path (`OEBPS/ch3.xhtml` or `ch3.xhtml`), a number starting at 1, or a range of numbers (`3-5`). An entry that matches none of these is ignored with a warning. This lets an e-book reader **lay out only the chapter currently being read again** when the font size changes. Items are laid out independently, so laying out one chapter produces the same result as that chapter in a full conversion. In page-split SVG output, item numbers are fixed by their position in the spine (`items/0003/`), so partial output can be overlaid directly on the full output. |
| <a id="appx-ioprop-input.viewport"></a>input.viewport | false | 3.1.0 | When true, the size specified by HTML &lt;meta name="viewport" ...&gt; is treated as the page size.<br /> This takes precedence over settings such as <span class="ioprop">output.page-width</span>. |
| <a id="appx-ioprop-input.filters"></a>input.filters | xslt<br />default-to-xhtml<br />loose-html | 1.0.0 | A space-separated list of preprocessing filters to apply to the input document, in application order. You can specify xslt, default-to-xhtml, and loose-html.<br />For details, see <a href="#style-input-filters" class="pageref">Input filters</a>. |
| <a id="appx-ioprop-input.normalize-text"></a>input.normalize-text | false | 3.2.15 | When set to "true", normalizes all text to NFC (Normalization Form C). |
| <a id="appx-ioprop-input.property-pi"></a>input.property-pi | false | 2.0.0 | When true, enables the jp.cssj.property processing instruction in documents. |
| <a id="appx-ioprop-input.stylesheet.titles"></a>input.stylesheet.titles | - | 1.0.0 | A list of CSS stylesheet titles to apply, separated by spaces or commas. For stylesheets associated through a link element or an xml-stylesheet processing instruction, all non-alternate stylesheets are applied by default. You can use this property to select the stylesheets to apply. Stylesheets without a title attribute are always applied regardless of this setting; those with a title are applied only if the name matches exactly. |
| <a id="appx-ioprop-input.xslt.default-stylesheet"></a>input.xslt.default-stylesheet | - | 1.2.0 | The URI of the default XSLT stylesheet. If you specify this property, the default stylesheet is loaded first. This is effective only when input.filters includes the xslt filter. |

**HTTP access properties**

| Name | Default | Version | Description |
| --- | --- | --- | --- |
| <a id="appx-ioprop-input.html.change-default-namespace"></a>input.html.change-default-namespace | false | 3.2.12 | When false, the document's default namespace is forced to XHTML. That is, xmlns="～" declarations are ignored. When true, xmlns="～" declarations take effect. |
| <a id="appx-ioprop-input.http.referer"></a>input.http.referer | true | 1.0.1 | Specifies whether to send a Referer header when retrieving server-side data over HTTP. Specify true or false.<br /> If you specify false, resources cannot be accessed on sites that use Referer to restrict direct access to images and other resources. |
| <a id="appx-ioprop-input.http.proxy.host"></a>input.http.proxy.host | - | 1.2.6 | The proxy host name.<br /> Setting this enables the proxy for HTTP communication. |
| <a id="appx-ioprop-input.http.proxy.port"></a>input.http.proxy.port | 8080 | 1.2.6 | The port number to use for the proxy.<br /> This setting is effective only when input.http.proxy.host is set. |
| <a id="appx-ioprop-input.http.proxy.authentication.user"></a>input.http.proxy.authentication.user<br /> input.http.proxy.authentication.password<br /> | - | 1.2.6 | The credentials (user,password) for a proxy server that requires authentication. This setting is effective only when <span class="ioprop">input.http.proxy.host</span> is set. |
| <a id="appx-ioprop-input.http.header."></a>input.http.header.<i>n</i>.name<br /> input.http.header.<i>n</i>.value<br /> | - | 2.0.0 | Headers to send over HTTP connections.<br /> n is a sequential number starting at 0. Two properties with the same n form a pair. name is the header name, and value is the header value.<br /> Numbers are counted from 0. Once required information (name) is missing, all subsequent parameters are ignored. |
| <a id="appx-ioprop-input.http.authentication.preemptive"></a>input.http.authentication.preemptive | false | 1.2.6 | Specifies whether to send credentials from the start when using HTTP authentication. Specify true or false.<br /> If you specify true, credentials such as the Authorization header are sent on the first connection.<br /> If you specify false, a 401 response is first received from the server to obtain information such as the realm and authentication scheme.<br /> If you specify true, authentication will not work with Digest authentication or on servers with multiple realms. |
| <a id="appx-ioprop-input.http.proxy.authentication.password"></a>input.http.proxy.authentication.password | - | 2.0.0 | The password to use for proxy authentication. Specify it together with <span class="ioprop">input.http.proxy.authentication.user</span>. |
| <a id="appx-ioprop-input.http.authentication."></a>input.http.authentication.<i>n</i>.host<br /> input.http.authentication.<i>n</i>.user<br /> input.http.authentication.<i>n</i>.password<br /> input.http.authentication.<i>n</i>.port<br /> input.http.authentication.<i>n</i>.realm<br /> input.http.authentication.<i>n</i>.schema<br /> | - | 1.2.6 | HTTP authentication settings. <i>n</i> is a sequential number starting at 0. Specify <tt>.host</tt> (required), <tt>.port</tt>, <tt>.user</tt> (required), and <tt>.password</tt>.<br />For details, see <b>BASIC or Digest authentication</b> (server product manual). |
| <a id="appx-ioprop-input.http.cookie."></a>input.http.cookie.<i>n</i>.domain<br /> input.http.cookie.<i>n</i>.name<br /> input.http.cookie.<i>n</i>.value<br /> input.http.cookie.<i>n</i>.path<br /> | - | 1.2.6 | Cookies to send. n is a sequential number, and four properties with the same n form a set. domain, name, and value are the cookie's domain, name, and value, respectively. path is the cookie path; if omitted, it defaults to the root (/).<br /> Numbers are counted from 0. Once required information (domain and name) is missing, all subsequent parameters are ignored. |
| <a id="appx-ioprop-input.http.connection.timeout"></a>input.http.connection.timeout<br /> | 60000 | 2.0.7 | The connection timeout for HTTP connections, in milliseconds. If a connection is not established within the specified time, a connection error occurs. 0 means no timeout. In 4.0.0, the default changed from 0 (no timeout) to 60000 (60 seconds). |
| <a id="appx-ioprop-input.http.cache"></a>input.http.cache | true | 4.0.0 | Specifies whether to cache HTTP responses in memory across conversions. Specify true or false.<br /> Retrievals that include credentials or Cookie are not cached. For details, see <b>Response cache</b> (server product manual). |
| <a id="appx-ioprop-input.http.cache.ttl"></a>input.http.cache.ttl | 600 | 4.0.0 | The retention period for the HTTP response cache, in seconds. If the response's Cache-Control header contains max-age, the shorter period is used. Specify 0 to disable caching. |
| <a id="appx-ioprop-input.http.socket.timeout"></a>input.http.socket.timeout<br /> | 60000 | 2.0.7 | The socket communication timeout for HTTP connections, in milliseconds. A communication error occurs if the response does not start, or reading stops, for the specified time or longer. 0 means no timeout. In 4.0.0, the default changed from 0 (no timeout) to 60000 (60 seconds). |
| <a id="appx-ioprop-input.prefetch"></a>input.prefetch | true | 4.0.0 | Specifies whether to asynchronously prefetch external resources (stylesheets and images) discovered in the main document. Specify true or false.<br /> In normal conversion, resources are retrieved sequentially as needed, so HTTP waiting times accumulate when converting a web page with many resources. This is enabled by default. Resources are retrieved in parallel while the main document is read, greatly reducing conversion time (similar to browser prefetching). If false, resources are retrieved one at a time as needed, as before.<br /> Only http/https resources that pass the <span class="ioprop">input.include</span>/<span class="ioprop">input.exclude</span> restrictions are prefetched. Requests that send credentials are not prefetched. For details, see <b>Resource prefetching</b> (server product manual). |

**Output properties**

| Name | Default | Version | Description |
| --- | --- | --- | --- |
| <a id="appx-ioprop-output.auto-height"></a>output.auto-height | false | 1.0.0 | Controls automatic height. Specify false or true.<br /> When true, automatic page breaks are disabled and the page height matches the height of the document content. In this case, the <span class="ioprop">output.page-height</span> property (<span class="ioprop">output.page-width</span> for vertical writing) has no effect.<br /> <a href="#style-page-layout">There are limits on the page sizes that can be output.</a> |
| <a id="appx-ioprop-output.auto-rotate"></a>output.auto-rotate | none | 2.1.9 | Controls automatic rotation when the paper and content orientations do not match. Specify none (no rotation), content (rotate the content), or paper (swap the paper orientation).<br />For details, see <a href="#style-auto-rotate" class="pageref">When portrait and landscape orientations do not match</a>. |
| <a id="appx-ioprop-output.broken-image"></a>output.broken-image | none | 1.2.2<br />none since 2.0.0<br />annotation since 2.1.2 | Controls what is displayed when an image cannot be loaded. Specify none, hidden, cross, or annotation.<br />For details, see <a href="#style-image-broken" class="pageref">When an image cannot be loaded</a>. |
| <a id="appx-ioprop-output.clip"></a>output.clip | true | 2.0.3 | When true, nothing outside the print area (outside the bleed area within the crop marks, or outside the page) is drawn. When false, content outside the print area is drawn. |
| <a id="appx-ioprop-output.color"></a>output.color | rgb | 1.2.1<br />cmyk since 3.1.0 | The color type of the output. Specify rgb, cmyk, or gray. rgb outputs colors as specified. cmyk converts all colors to CMYK (since 4.0.0, using the output intent's ICC profile; achromatic solid colors use K only, and every pixel in an image is converted). gray converts everything to grayscale. |
| <a id="appx-ioprop-output.default-font-family"></a>output.default-font-family | serif | 2.0.0 | The default font family. This font is used when the document does not specify a font or when the specified font cannot be found.<br /> You can specify multiple fonts in the same format as the CSS <span class="cssprop">font-family</span> property. Enclose font names that contain spaces in quotes (' or "). |
| <a id="appx-ioprop-output.expand-with-content"></a>output.expand-with-content | false | 3.2.1 | If content does not fit on the page, expands the page by the amount that does not fit.<br />For details, see <a href="#style-expand-with-content" class="pageref">Expanding the paper to fit the content</a>. |
| <a id="appx-ioprop-output.fit-to-paper"></a>output.fit-to-paper | false | 2.0.0<br /> preserve-aspect-ratio since 2.1.9 | Controls placement when the paper and print area differ in size. When true, the print area is fitted to fill the paper. When false, it is centered. preserve-aspect-ratio fits it to the paper while preserving its aspect ratio.<br /> <b>The scale factor is "paper size ÷ print area size," so a larger print area is reduced.</b> For the procedure to fit a page that is wider than the paper, see <a href="#style-fit-wide-page" class="pageref">Fitting a page wider than the paper onto a sheet</a>. |
| <a id="appx-ioprop-output.marks"></a>output.marks | none | 1.0.0<br />hidden since 1.2.1 | Controls the display of crop marks and trim allowance. Specify none, crop, cross, both, or hidden.<br /> These mean, respectively: no crop marks or trim allowance, corner crop marks, center crop marks, both types of crop marks, or trim allowance only. |
| <a id="appx-ioprop-output.media_types"></a>output.media_types | all print paged visual bitmap static | 2.0.0 | The media types of the stylesheets to apply. |
| <a id="appx-ioprop-output.meta."></a>output.meta.<i>n</i>.name<br /> output.meta.<i>n</i>.value<br /> | - | 2.0.3 | Sets document information in advance. <i>n</i> is a sequential number starting at 0. Two properties with the same <i>n</i> form a pair. <br /> Document information is overridden by <tt>&lt;meta name="name" content="value"&gt;</tt> elements in the document. For details, see <a href="#style-xml-meta" class="pageref">Document information</a>. |
| <a id="appx-ioprop-output.no-page-break"></a>output.no-page-break | false | 2.0.3 | When true, disables all page breaks. Unlike setting <span class="ioprop">output.auto-height</span> to true, it does not expand the page height to fit the content. |
| <a id="appx-ioprop-output.page-height"></a>output.page-height | 297mm | 1.0.0 | The page height. The default is the height of A4.<br /> Use CSS length units (mm,cm,in,pt,pc,px).<br /> <a href="#style-page-layout">There are limits on the page sizes that can be output.</a> |
| <a id="appx-ioprop-output.page-limit"></a>output.page-limit | - | 1.2.0 | The maximum number of pages. Processing is interrupted when the page count reaches the limit. There is no limit by default. For details, see <a href="#prog-page-limit" class="pageref">Limiting the number of pages</a>.<br />A negative value means no limit. |
| <a id="appx-ioprop-output.page-limit.abort"></a>output.page-limit.abort | force | 3.0.11 | When force, the result is discarded if the page limit is reached. When normal, the partially completed file is output as far as possible. For details, see <a href="#prog-page-limit" class="pageref">Limiting the number of pages</a>. |
| <a id="appx-ioprop-output.page-margins"></a>output.page-margins | 12.7mm | 2.0.0 | The page margins. Use the same format as the CSS <span class="cssprop">margin</span> property. The available length units are (mm,cm,in,pt,pc,px). You can override this setting in an @page rule in the document. |
| <a id="appx-ioprop-output.type"></a>output.type | application/pdf | 1.0.0<br />images in 2.0.3 | The MIME type of the output file format.<br /> Specify "application/pdf" for PDF, "image/jpeg" or "image/png" for images, "application/vnd.copper.paged-svg" for page-split SVG, or "application/vnd.copper.paged-svg+zip" to bundle it into a single ZIP. For details, see <a href="#style-output" class="pageref">Output file formats</a>.<br /> "application/pdf" (PDF files) is always available. Image output depends on Java Image I/O and can use the image formats supported by the Java runtime (such as "image/png"). You can also add available image formats by installing plugins such as <a href="#style-image-jai">JAI-ImageI/O</a> in the Java runtime.<br /> Normal image output outputs only the last page. To output an SVG for each page and shared resources, specify "application/vnd.copper.paged-svg". The core 14 fonts and CID-keyed fonts defined by the cid-keyed-font element in the font configuration file cannot be rendered accurately in normal image output. |
| <a id="appx-ioprop-output.page-width"></a>output.page-width | 210mm | 1.0.0 | The page width. The default is the width of A4.<br /> Use CSS length units (mm,cm,in,pt,pc,px).<br /> <a href="#style-page-layout">There are limits on the page sizes that can be output.</a> |
| <a id="appx-ioprop-output.paper-height"></a>output.paper-height | Value of output.page-height | 2.0.0 | The paper height. The default is the page height. When the paper and page differ in size, the behavior depends on <span class="ioprop">output.fit-to-paper</span>.<br /> Use CSS length units (mm,cm,in,pt,pc,px).<br /> <a href="#style-page-layout">There are limits on the page sizes that can be output.</a> |
| <a id="appx-ioprop-output.paper-width"></a>output.paper-width | Value of output.page-width | 2.0.0 | The paper width. The default is the page width.<br /> When the paper and page differ in size, the behavior depends on <span class="ioprop">output.fit-to-paper</span>.<br /> Use CSS length units (mm,cm,in,pt,pc,px).<br /> <a href="#style-page-layout">There are limits on the page sizes that can be output.</a> |
| <a id="appx-ioprop-output.n-up"></a>output.n-up | 1 | 4.0.0 | The number of logical pages to impose on one sheet of paper. 1 disables imposition. The specified number of pages is arranged on one sheet.<br />You can specify 1–256. An out-of-range value produces a warning and is treated as 1 (no imposition). |
| <a id="appx-ioprop-output.n-up.order"></a>output.n-up.order | horizontal | 4.0.0 | The order of imposed pages. Specify horizontal (row order), vertical (column order), horizontal-reverse, or vertical-reverse. Adding reverse reverses the order. |
| <a id="appx-ioprop-output.marks.spine-width"></a>output.marks.spine-width | - | 4.0.0 | The width of the spine, specified as a length. When set, lines indicating the spine position are added to the crop marks. |
| <a id="appx-ioprop-output.print-mode"></a>output.print-mode | double-side | 2.0.0<br /> left-side, right-side since 3.0.0 | The print mode. Specify single-side, double-side, left-side, or right-side.<br /> single-side uses single-sided printing, and the :left and :right pseudo-classes in @page rules no longer apply.<br /> left-side and right-side fix the binding direction regardless of whether the document uses horizontal or vertical writing. |
| <a id="appx-ioprop-output.resolution"></a>output.resolution | 96 | 2.0.0 | The reference resolution for the px unit.<br /> Specify it in ppi (pixels per inch).<br /> Typical browsers use 96. Specifying 72 makes 1 pt (the basic PDF unit) and 1 px the same length.<br />You can specify 1–10000. An out-of-range value produces a warning and is treated as 96. |
| <a id="appx-ioprop-output.size-limit"></a>output.size-limit | - | 1.2.0 | The maximum output data size, in bytes. Processing is interrupted when the size reaches the limit. There is no limit by default.<br />A negative value means no limit. |
| <a id="appx-ioprop-output.htrim"></a>output.htrim | 1cm | 2.0.0 | The widths of the left and right trim allowances.<br /> Use CSS length units (mm,cm,in,pt,pc,px). |
| <a id="appx-ioprop-output.vtrim"></a>output.vtrim | 1cm | 2.0.0 | The widths of the top and bottom trim allowances.<br /> Use CSS length units (mm,cm,in,pt,pc,px). |
| <a id="appx-ioprop-output.text-size"></a>output.text-size | 1.0 | 2.1.9 | The font size scale factor (a real number).<br /> For example, 0.5 makes the font size half the normal size, and 2.0 doubles it.<br />You can specify 0.01–100. An out-of-range value produces a warning and is treated as 1.0. |
| <a id="appx-ioprop-output.svg.text"></a>output.svg.text | outline | 4.0.0 | Controls how text is written in single SVG output (`image/svg+xml`).<br/>`outline` (the default) converts glyphs to outlines (path). `keep` retains them as `&lt;text&gt;` and embeds subsetted WOFF2 fonts and images in the SVG using `data:`. |
| <a id="appx-ioprop-output.trim-inset"></a>output.trim-inset | - | 4.0.0 | The <b>width of the band treated as bleed</b> along the perimeter of the print area (the trim line is considered to be inset from the perimeter by this width).<br/>Use this to output existing content that already includes bleed at the correct trim size without rewriting the CSS. The available length units are (mm,cm,in,pt,pc,px). |
| <a id="appx-ioprop-output.trims"></a>output.trims | 1cm | 3.1.6 | The widths of the trim allowances.<br/>Use the same format as the CSS <span class="cssprop">margin</span> property. The available length units are (mm,cm,in,pt,pc,px). |

**Image output properties**

| Name | Default | Version | Description |
| --- | --- | --- | --- |
| <a id="appx-ioprop-output.image.resolution"></a>output.image.resolution | 96 | 2.0.4 | The resolution (dpi) for raster image output selected by <span class="ioprop">output.type</span>.<br /> <span class="notice">In 2.0.8 and earlier, the default was 72, and a bug prevented the resolution from being applied correctly. In 2.0.9 and later, set the value converted as (previous setting × <span class="ioprop">output.resolution</span> / 72). </span><br />You can specify 1–10000. An out-of-range value produces a warning and is treated as 96. If the type area exceeds Java's image limit (approximately 2.1 billion pixels), conversion fails with message 3812. |
| <a id="appx-ioprop-output.image-pixel-limit"></a>output.image-pixel-limit | - | 4.0.0 | The maximum number of pixels (width × height) in a single generated raster image. There is no limit by default. If the type area for image output (page size × <span class="ioprop">output.image.resolution</span>) exceeds the limit, conversion fails (message 3812). Images rasterized for page-split SVG output (such as SVG images) are rendered at a reduced scale until they fit within the limit. |
| <a id="appx-ioprop-output.image.antialias"></a>output.image.antialias | true | 3.0.1 | Controls antialiasing for raster image output. true enables antialiasing, and false disables it. |
| <a id="appx-ioprop-output.image.transparent"></a>output.image.transparent | false | 4.0.0 | Specifies whether image output is drawn without painting the background. When true, areas where nothing is drawn remain transparent. <b>This is effective only for formats that can preserve transparency (PNG, GIF, TIFF).</b> For formats that cannot (JPEG, BMP, WBMP), the background remains white and message <a href="#appx-messages" class="pageref">2824</a> is issued. |
| <a id="appx-ioprop-output.use-meta-info"></a>output.use-meta-info | true | 3.1.8 | Sets document information from the HTML META and TITLE elements. "false" disables this feature. |
| <a id="appx-ioprop-output.paged-svg.font-scope"></a>output.paged-svg.font-scope | document | 4.0.0 | Specifies whether to create one font subset for the whole document or a subset for each page. For EPUB, `document` creates one subset **per spine item (the XHTML it contains)**, output when layout of that item finishes. The default, `document`, produces the smallest total size, but the required glyphs are not known until all pages have been laid out, so **subsets can only be output at the end, and the recipient cannot render a single character until conversion finishes**. `page` creates subsets for each page and outputs them **before that page's SVG**, so the first page can be rendered as soon as it arrives. Measurements (350 pages of Japanese text): total output 9.2 MB → 16.1 MB (1.75 times), conversion time +14%. However, if only three pages are read, the result reverses: 340 KB → 150 KB (the break-even point is 12–13 pages). |
| <a id="appx-ioprop-output.paged-svg.base-uri"></a>output.paged-svg.base-uri | ../ | 4.0.0 | The prefix used when page SVGs refer to shared resources (font subsets and images). The default, `../`, is relative to `pages/` and resolves when each page is opened as a separate document. **In a reader that imports multiple page SVGs into one HTML document, the base changes and resolution fails. Because the body text uses private-use characters, entire pages appear blank.** An absolute URL prefix (`https://example.com/book/`) resolves regardless of where the SVGs are imported. A trailing `/` is added if missing. The same prefix is applied to both fonts and images. |
| <a id="appx-ioprop-output.paged-svg.compression"></a>output.paged-svg.compression | gzip | 4.0.0 | Specifies whether to return page SVGs and page JSON compressed with gzip. With the default, gzip, page SVGs are named `.svgz` and page JSON `.json.gz`. Measurements for a 314-page book in vertical writing show a 56% reduction in total output, from 15.0 MB to 6.6 MB. Conversion time changes very little. Shared WOFF2 and PNG/JPEG files are already compressed and are left unchanged. `manifest.json` is the entry point and is left uncompressed. The manifest's `sha256` values are calculated from **the bytes after compression**. |
| <a id="appx-ioprop-output.paged-svg.resources"></a>output.paged-svg.resources | reference | 4.0.0 | Controls how shared resources (font subsets and images) are delivered in page-split SVG output. **Fonts and images are controlled together.** reference outputs separate files referenced with `../assets/…`, so only one copy of each image is needed. embed embeds images using `data:` and does not output separate files. It is intended for delivery methods that cannot preserve relative URIs; the total size increases because the same image is duplicated on each page. Fonts remain references to shared WOFF2 files even with embed. omit writes references only and does not return the resources themselves. Entries remain in manifest.json, so the recipient can reuse resources from the previous conversion when laying out the same book again. source **references the original URLs of web images without copying them**<span class="since">4.0.0</span>. It is intended for converting web content to SVG and displaying it on the same web. For raster images originating from `http:`/`https:`/`file:`, the page SVG writes `<image href="source URL">`, and `source` is added to `images[]` in manifest.json. Images without a source (`data:`, generated graphics, and rasterized SVG) and fonts are output as shared resources, as with reference. Because these are direct references to the original server, the reader cannot retrieve private URLs or resources that require authentication. **This setting reduces network traffic and storage, not conversion time**. Measurements for a 314-page book (one pass) show a difference of only 121 ms (6%), while output decreases by 27%, from 15.0 MB to 10.9 MB. |
| <a id="appx-ioprop-output.paged-svg.image.compression"></a>output.paged-svg.image.compression | none | 4.0.0 | The compression policy for shared images (`assets/images/`) in page-split SVG output. The default, `none`, outputs retrieved images as they are (JPEG remains JPEG; other formats become PNG). `jpeg` recompresses raster images without transparency as JPEG (quality 0.8). Images that are already JPEG are left unchanged unless resized. Small images (at or below the <span class="ioprop">output.paged-svg.image.compression.lossless</span> threshold) and images with transparency remain lossless (PNG). SVG images remain vectors and are not affected. Measurements (a Wikipedia article, 215 pages with Wikimedia images, 76 images): because the source images were all JPEG thumbnails (up to 500 px), `jpeg` alone reduced the total image size from 2.09 MB to 2.01 MB (4%). Combined with width and height limits of 250 px, it reduced the size to 1.24 MB (41%). Shared image URIs are the SHA-256 of **the output bytes**, so changing the policy also changes the URIs. |
| <a id="appx-ioprop-output.paged-svg.image.compression.lossless"></a>output.paged-svg.image.compression.lossless | 200 | 4.0.0 | The image size threshold for lossy compression when <span class="ioprop">output.paged-svg.image.compression</span>=jpeg. Images at or below the specified size (the sum of vertical and horizontal pixel counts) remain lossless (PNG). This has the same meaning as <span class="ioprop">output.pdf.image.compression.lossless</span>. |
| <a id="appx-ioprop-output.paged-svg.image.max-width"></a>output.paged-svg.image.max-width | Unlimited | 4.0.0 | The maximum horizontal pixel count (an integer) of shared images in page-split SVG output. Images are reduced to fit this width while preserving their aspect ratio. This limits resolution without changing the displayed size. Resized images are output as JPEG if the source was JPEG, or PNG otherwise. This has the same meaning as <span class="ioprop">output.pdf.image.max-width</span>. |
| <a id="appx-ioprop-output.paged-svg.image.max-height"></a>output.paged-svg.image.max-height | Unlimited | 4.0.0 | The maximum vertical pixel count (an integer) of shared images in page-split SVG output. This works like <span class="ioprop">output.paged-svg.image.max-width</span>. |
| <a id="appx-ioprop-output.paged-svg.page-checksums"></a>output.paged-svg.page-checksums | true | 4.0.0 | Specifies whether to write each page's SHA-256 (`svgSha256` and `dataSha256`) to `pages[]` in `manifest.json`. If the recipient does not use them to check integrity or detect changed pages, setting `false` makes `manifest.json` smaller (two 64-digit hashes per page ≈ 160 bytes; in a measured 215-page book the manifest shrinks from 88 KB to 54 KB, 39% smaller). The `sha256` of shared resources (fonts and images) is always written because it is the key for their URIs and identity. |
| <a id="appx-ioprop-output.paged-svg.pdf"></a>output.paged-svg.pdf | false | 4.0.0 | Specifies whether to **also output a PDF from the same layout** as the page-split SVG. When `true`, `document.pdf` is added to the result set (inside the ZIP when returned as a ZIP), and `pdf` is added to `manifest.json`. Layout runs once, with each page rendered to both page SVG and PDF, so it is faster than converting twice separately and the pagination always matches. PDF output follows `output.pdf.*` (such as <span class="ioprop">output.pdf.fonts.policy</span>). If no font policy is specified, fonts are embedded as in the page SVG (`core,embedded`), and the PDF writes them as text (not outlines). The PDF is output as a single result at the end of conversion (page SVGs are still output incrementally). This has no effect for EPUB (bundles per item). |

<table class="spec">
		<caption>PDF output properties</caption>
		<thead>
			<tr>
				<th>Name</th>
				<th>Default</th>
				<th>Version</th>
				<th>Description</th>
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
				<td>None</td>
				<td class="nowrap">1.2.0<br />(PDF 1.4)
				</td>
				<td>Settings for files to attach to the PDF. <i>n</i> is a sequential number starting at 0. Specify <tt>.uri</tt> (required), <tt>.name</tt>, <tt>.mime-type</tt>, and <tt>.description</tt>.<br /><tt>.relationship</tt> is the relationship between the attachment and the document (AFRelationship in PDF/A-3). Specify alternative, data, source, supplement, or unspecified. Use alternative for invoice XML in an electronic invoice.<br />For details, see <a href="#style-pdf-attachments" class="pageref">File attachments</a>.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.facturx">
				<td class="nowrap"><span id="appx-ioprop-output.pdf.facturx.conformance-level">output.pdf.facturx.conformance-level</span><br />
					<span id="appx-ioprop-output.pdf.facturx.document-type">output.pdf.facturx.document-type</span><br />
					<span id="appx-ioprop-output.pdf.facturx.document-file-name">output.pdf.facturx.document-file-name</span><br />
					<span id="appx-ioprop-output.pdf.facturx.version">output.pdf.facturx.version</span><br />
				</td>
				<td>None<br />INVOICE<br />factur-x.xml<br />1.0</td>
				<td class="nowrap">4.0.0<br />(PDF/A-3)
				</td>
				<td>Metadata settings for electronic invoices (Factur-X / ZUGFeRD). Setting <tt>.conformance-level</tt> (MINIMUM, BASIC WL, BASIC, EN 16931, or EXTENDED) outputs the XMP extension schema required by electronic invoice validators.<br />Attach the invoice XML itself using <tt>output.pdf.attachments.<i>n</i>.*</tt>, with <tt>relationship=alternative</tt> and <tt>name</tt> set to the same name as <tt>.document-file-name</tt>. The recommended value for <tt>output.pdf.version</tt> is 1.7A-3 (PDF/A-3).</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.output-intent">
				<td class="nowrap"><span id="appx-ioprop-output.pdf.output-intent.identifier">output.pdf.output-intent.identifier</span><br />
					<span id="appx-ioprop-output.pdf.output-intent.condition">output.pdf.output-intent.condition</span><br />
					<span id="appx-ioprop-output.pdf.output-intent.registry">output.pdf.output-intent.registry</span><br />
					<span id="appx-ioprop-output.pdf.output-intent.info">output.pdf.output-intent.info</span><br />
					<span id="appx-ioprop-output.pdf.output-intent.icc-profile">output.pdf.output-intent.icc-profile</span><br />
				</td>
				<td>None<br />None<br />http://www.color.org<br />None<br />None</td>
				<td class="nowrap">4.0.0<br />(PDF 1.4)
				</td>
				<td>Settings for the output intent (/OutputIntents—the intended printing conditions). It is output when you set <tt>.identifier</tt> (a characterization identifier in the ICC registry, such as JC200103 or FOGRA39). Follow your print shop's requirements.<br />If you specify an ICC profile URI in <tt>.icc-profile</tt>, it is embedded as DestOutputProfile (the number of color components is detected automatically from the profile header). Specifying <tt>.info</tt> is recommended for unregistered printing conditions.<br />If unspecified, the bundled ISO Coated v2 300% (ECI) (FOGRA39, CMYK) is used for PDF/X (all versions) and output.color=cmyk; sRGB is used otherwise. For PDF/X, the ICC profile is fully validated. Error 380E occurs if it cannot be read, is not an output profile (prtr), is not CMYK, or the identifier is empty (a warning for formats other than PDF/X). For PDF/X, 380E also occurs if only an identifier is specified without <tt>.icc-profile</tt>, or if the identifier or registry name contains characters other than printable ASCII. If you omit <tt>.info</tt> for PDF/X, <tt>.condition</tt> (or the identifier if absent) is written to Info, because Info is required for unregistered printing conditions. This profile is also used for RGB → CMYK conversion.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.rendering-intent">
				<td class="nowrap">output.pdf.rendering-intent</td>
				<td>None</td>
				<td class="nowrap">4.0.0<br />(PDF 1.4)
				</td>
				<td>The default rendering intent. If you specify perceptual, relative-colorimetric, saturation, or absolute-colorimetric, it is output as an ri operator at the beginning of each page's content stream. RGB → CMYK conversion for output.color=cmyk and PDF/X-1a always uses perceptual and is not affected by this setting.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.bookmarks">
				<td class="nowrap">output.pdf.bookmarks</td>
				<td>true<br />(false before 4.0.0; false by default for PDF/UA-2)</td>
				<td class="nowrap">1.0.0<br />(PDF 1.2)
				</td>
				<td>Controls bookmarks. Specify false or true.<br />
					When true, generates bookmarks (PDF outline) from H1–H6 elements.<br />
					You can use the CSS <span class="cssprop">bookmark-level</span> property (<span class="cssdecl">auto</span>, <span class="cssdecl">none</span>, or an integer of 1 or greater) to change levels, exclude headings, or create bookmarks from elements that are not headings. <span class="cssdecl">auto</span> (the default) follows the H1–H6 levels. You can change bookmark text with <span class="cssprop">bookmark-label</span> (a sequence of strings, <span class="cssdecl">content()</span>, and <span class="cssdecl">attr()</span>). <span class="cssdecl">counter()</span> is not supported (numbers written in ::before are included in bookmark text).<span class="since">4.0.0</span>
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.compression">
				<td class="nowrap">output.pdf.compression</td>
				<td>binary</td>
				<td class="nowrap">1.0.0<br />(PDF 1.2)
				</td>
				<td>The compression method. Specify none, ascii, or binary.<br /> Compression efficiency increases in this order.
					none leaves everything except images uncompressed. ascii also compresses non-image content, but the generated PDF is a text file.
					binary produces a compressed PDF in binary format.<br />
					However, if you use encryption, the result is always binary.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption">
				<td class="nowrap">output.pdf.encryption</td>
				<td>none</td>
				<td class="nowrap">1.2.0<br /> (PDF 1.2)<br /> (v2 requires PDF 1.3)
				</td>
				<td>The encryption method. Specify none, v1, v2, v4, or v5. For new documents, choose v5 (AES-256, PDF 1.7 or later).<br />For details, see <a href="#style-pdf-encryption" class="pageref">Encryption</a>.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.tagged">
				<td class="nowrap">output.pdf.tagged</td>
				<td>false</td>
				<td class="nowrap">4.0.0</td>
				<td>Specifies whether to output tagged PDF (logical structure).<br />
					true enables it, adding structure and marked content to text, images, and graphics (the foundation for accessibility).<br />
					It is enabled automatically for PDF/A level A (1.7A-2a/1.7A-3a) and PDF/UA (1.7UA-1/2.0UA-2).
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.tagged.lang">
				<td class="nowrap">output.pdf.tagged.lang</td>
				<td>(None)</td>
				<td class="nowrap">4.0.0</td>
				<td>The document language for tagged PDF / PDF/UA (BCP 47, for example "ja").<br />
					A language is required for PDF/UA (1.7UA-1/2.0UA-2).
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.bidi.actual-text">
				<td class="nowrap">output.pdf.bidi.actual-text</td>
				<td>false</td>
				<td class="nowrap">4.0.0</td>
				<td>Specifies whether to add logical-order strings as ActualText to reordered lines containing right-to-left horizontal text, and to use separate CIDs with ToUnicode mappings to the logical characters for mirrored brackets.<br />
					These are not added by default (Chrome and Edge PDF viewers restore the logical order more accurately without them).
					This setting is intended for extractors such as Acrobat that actively honor ActualText, and is effective only for PDF 1.5 or later.<br />
					For details, see <a href="#style-pdf-bidi" class="pageref">Bidirectional text extraction</a>.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.forms">
				<td class="nowrap">output.pdf.forms</td>
				<td>false</td>
				<td class="nowrap">4.0.0</td>
				<td>Specifies whether to output HTML form controls as form fields (AcroForm) that you can fill in within the PDF.<br />For details, see <a href="#style-pdf-forms" class="pageref">Fillable PDF forms</a>.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.length">
				<td class="nowrap">output.pdf.encryption.length</td>
				<td>128</td>
				<td class="nowrap">1.2.0<br />(PDF 1.3)
				</td>
				<td>The encryption key length, in bits.<br /> With output.pdf.encryption=v1, it is fixed at 40.
					With v2 and v4, you can specify 40 to 128 in 8-bit increments. It is ignored with v5 (fixed at 256 bits).
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.user-password">
				<td class="nowrap">output.pdf.encryption.user-password</td>
				<td>Empty</td>
				<td class="nowrap">1.2.0<br />(PDF 1.2)
				</td>
				<td>The password for opening the document.
					When you view the document with this password, the permissions set on the document restrict what you can do.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.owner-password">
				<td class="nowrap">output.pdf.encryption.owner-password</td>
				<td>User password</td>
				<td class="nowrap">1.2.0<br />(PDF 1.2)
				</td>
				<td>The password for changing document permissions (the master password). It allows all operations on the document.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.print">
				<td class="nowrap">output.pdf.encryption.permissions.<br />print
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.2)
				</td>
				<td>Permission to print the document.<br /> true=allowed, false=prohibited.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.modify">
				<td class="nowrap">output.pdf.encryption.permissions.<br />modify
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.2)
				</td>
				<td>Permission to modify the document content.<br /> true=allowed, false=prohibited.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.copy">
				<td class="nowrap">output.pdf.encryption.permissions.<br />copy
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.2)
				</td>
				<td>Permission to copy text and images from the document.<br /> true=allowed, false=prohibited.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.add">
				<td class="nowrap">output.pdf.encryption.permissions.<br />add
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.2)
				</td>
				<td>Permission to add or modify annotations, or fill in forms.
					If output.pdf.encryption.permissions.modify=true, adding and modifying forms is also allowed.<br />
					true=allowed, false=prohibited.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.fill">
				<td class="nowrap">output.pdf.encryption.permissions.<br />fill
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.3)
				</td>
				<td>Permission to fill in forms.<br /> Effective only when output.pdf.encryption is v2.<br />
					true=allowed, false=prohibited.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.extract">
				<td class="nowrap">output.pdf.encryption.permissions.<br />extract
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.3)
				</td>
				<td>Permission to extract text and images from the document for users with disabilities.<br />
					Effective only when output.pdf.encryption is v2.<br /> true=allowed, false=prohibited.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.assemble">
				<td class="nowrap">output.pdf.encryption.permissions.<br />assemble
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.3)
				</td>
				<td>Permission to add new pages, bookmarks, and thumbnail images to the document.<br />
					Effective only when output.pdf.encryption is v2.<br /> true=allowed, false=prohibited.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.permissions.print-high">
				<td class="nowrap">output.pdf.encryption.permissions.<br />print-high
				</td>
				<td>true</td>
				<td class="nowrap">1.2.0<br />(PDF 1.3)
				</td>
				<td>Permission to print the document at high quality.<br /> Effective only when output.pdf.encryption is v2.<br />
					true=allowed, false=prohibited.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.encryption.v4.cfm">
				<td class="nowrap">output.pdf.encryption.v4.cfm</td>
				<td>V2</td>
				<td class="nowrap">3.0.0</td>
				<td>The encryption method when <span class="ioprop">output.pdf.encryption</span> is v4.
					`v2` is Arcfour, and `aesv2` is AES-128.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.file-id">
				<td class="nowrap">output.pdf.file-id</td>
				<td>Randomly generated</td>
				<td class="nowrap">2.0.9</td>
				<td>Sets the PDF file ID. Use exactly 32 hexadecimal digits.<br /> Example:<br />
					"000067A36902BF8D2A0617B9CD02BCFA"
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.fonts.policy">
				<td class="nowrap">output.pdf.fonts.policy</td>
				<td>cid-keyed</td>
				<td class="nowrap">1.1.0<br/>outlines since 3.1.1<br />(PDF 1.2)
				</td>
				<td>The types of fonts to use. Specify cid-keyed, cid-identity, embedded, and outlines in priority order, separated by spaces. -core excludes core fonts.<br />
					For page-split SVG output (<span class="ioprop">output.type</span>=application/vnd.copper.paged-svg), single SVG output (image/svg+xml, both outline and keep),
					and raster image output (image/png, image/jpeg, and so on),
					the default when this property is not specified is <b>embedded</b> <span class="since">4.0.0</span>.
					SVG and images have no mechanism equivalent to CID-keyed fonts. Leaving the policy as cid-keyed
					causes all SVG text to be output as outlines (paths), increasing output size,
					and causes fonts without glyph data in images to be rendered with the server's system fonts (different typefaces).
					An explicit setting takes precedence.<br />For details, see <b>Font types</b> (pdfg2d manual).</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.hyperlinks">
				<td class="nowrap">output.pdf.hyperlinks</td>
				<td>true<br />(false before 4.0.0; false by default for PDF/UA-2)</td>
				<td class="nowrap">1.0.0<br />(PDF 1.2)
				</td>
				<td>Controls hyperlinks. Specify false or true.<br />
					When true, enables hyperlinks from the PDF to the WWW and other destinations.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.hyperlinks.href">
				<td class="nowrap">output.pdf.hyperlinks.href</td>
				<td>relative</td>
				<td class="nowrap">1.1.0<br />(PDF 1.2)
				</td>
				<td>How hyperlink addresses are written. Specify relative or absolute.<br />
					relative uses relative addresses, retaining the href attribute of the HTML a element as is.
					absolute converts the address to an absolute URI before writing it to the PDF.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.hyperlinks.base">
				<td class="nowrap">output.pdf.hyperlinks.base</td>
				<td>Document URI</td>
				<td class="nowrap">2.0.0<br />(PDF 1.2)
				</td>
				<td>The base URI when output.pdf.hyperlinks.href is relative.
					This property has no effect when output.pdf.hyperlinks.href is absolute.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.hyperlinks.fragment">
				<td class="nowrap">output.pdf.hyperlinks.fragment</td>
				<td>true</td>
				<td class="nowrap">2.0.0<br />(PDF 1.2)
				</td>
				<td>When true, document fragments are created from HTML &lt;a
					name～ or id attributes, allowing URL fragment identifiers
					to link to specific locations within the document.<br />
					When false, no document fragments are created.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.image.compression">
				<td class="nowrap">output.pdf.image.compression</td>
				<td>flate</td>
				<td class="nowrap">2.0.3</td>
				<td>The compression format for images embedded in the PDF. Specify flate, jpeg, or jpeg2000.<br />For details, see <a href="#style-pdf-image" class="pageref">Compression formats for images in PDFs</a>.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.image.compression.lossless">
				<td class="nowrap">output.pdf.image.compression.lossless</td>
				<td>200</td>
				<td class="nowrap">2.0.3</td>
				<td>When <span class="ioprop">output.pdf.image.compression</span>
					selects lossy compression (such as JPEG), this is the image size threshold for applying lossy compression.
					If an image is at or below the specified size (the sum of vertical and horizontal pixel counts), lossless compression (FlateDecode) is used.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.image.max-width">
				<td class="nowrap">output.pdf.image.max-width</td>
				<td>Unlimited</td>
				<td class="nowrap">3.0.0</td>
				<td>The maximum horizontal pixel count (an integer) of images used in the PDF.
					Images are automatically reduced to fit this width while preserving their aspect ratio.
					This limits resolution without changing the physical display size.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.blur-resolution">
				<td class="nowrap">output.pdf.blur-resolution</td>
				<td>150</td>
				<td class="nowrap">4.0.0</td>
				<td>The resolution for rendering blurred <span class="cssprop">box-shadow</span> and <span class="cssprop">text-shadow</span> effects
					in PDF output (dpi, 72–600)<span class="since">4.0.0</span>.
					PDF has no blur operator, so only the shadow is rasterized and placed as an image with transparency
					(text and body content remain vectors. Shadows are <i>artifact</i> content, so they do not affect tagged PDF or text extraction).
					For output that cannot use transparency (PDF/A-1, PDF/X-1a, and so on; PDF/X-4 and PDF/X-6 can use transparency), the existing approximation with stepped fills is used, and warning 2822 is issued.
					Shadows have low spatial frequencies, so the default 150 dpi is sufficient. Increasing it makes images larger.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.filter-resolution">
				<td class="nowrap">output.pdf.filter-resolution</td>
				<td>300</td>
				<td class="nowrap">4.0.0</td>
				<td>The resolution for rasterizing elements with <span class="cssprop">filter</span> (color conversion, <tt>blur()</tt>, or <tt>drop-shadow()</tt>)
					in PDF output (dpi, 72–600)<span class="since">4.0.0</span>.
					The default is higher than for shadow blur (<span class="ioprop">output.pdf.blur-resolution</span>) because the element may contain text.
					Elements exceeding 16 million pixels are drawn as vectors without the effect, and warning 2822 (content: filter-limit) is issued.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.image.max-height">
				<td class="nowrap">output.pdf.image.max-height</td>
				<td>Unlimited</td>
				<td class="nowrap">3.0.0</td>
				<td>The maximum vertical pixel count (an integer) of images used in the PDF.
					Images are automatically reduced to fit this height while preserving their aspect ratio.
					This limits resolution without changing the physical display size.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.jpeg-image">
				<td class="nowrap">output.pdf.jpeg-image</td>
				<td>raw</td>
				<td class="nowrap">1.1.0<br />(PDF 1.2)
				</td>
				<td>How JPEG images embedded in the PDF are handled. Specify raw (embed as is), to-flate (convert to lossless compression), or recompress (recompress).<br />For details, see <a href="#style-pdf-image" class="pageref">Compression formats for images in PDFs</a>.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.meta.creation-date">
				<td class="nowrap">output.pdf.meta.creation-date</td>
				<td>Current server time</td>
				<td class="nowrap">2.0.9</td>
				<td>Sets CreationDate in the PDF metadata.<br /> Examples:<br /> "2009-05-22
					21:10:14"<br /> "2009-06-04 15:53:02 +09:00" (with an explicit time zone)<br />
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.meta.mod-date">
				<td class="nowrap">output.pdf.meta.mod-date</td>
				<td>Value of output.pdf.meta.creation-date</td>
				<td class="nowrap">2.0.9</td>
				<td>Sets ModDate in the PDF metadata.<br /> The time format is the same as for <span class="ioprop">output.pdf.meta.creation-date</span>.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.open-action.java-script">
				<td class="nowrap">output.pdf.open-action.java-script</td>
				<td>-</td>
				<td class="nowrap">3.0.2/2.1.11<br />(PDF 1.2)
				</td>
				<td>Sets the JavaScript to run when the document is opened.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.platform-encoding">
				<td class="nowrap">output.pdf.platform-encoding</td>
				<td>MS932</td>
				<td class="nowrap">1.2.0<br />(PDF 1.2)
				</td>
				<td>The platform character encoding of the environment where the PDF is viewed. It is used to represent names (such as file names) inside the PDF.<br />
					It affects font names in PDF 1.2 and earlier. It has no effect in PDF 1.3 and later, which use Unicode.<br />
					It affects attachment file names in PDF 1.6 and earlier.
					If file names contain multibyte characters, they become garbled unless this encoding matches that of the viewing platform.
					Specify MS932 (the Windows version of Shift_JIS) for Japanese documents, EUC-KR for Korean, or Big5 for Traditional Chinese.<br />
					It has no effect in PDF 1.7 and later, which use Unicode (2.0.3).<br />
					Only encodings that represent ASCII characters with the same bytes (such as MS932 and UTF-8)
					are allowed. An unknown name or an encoding such as UTF-16 produces a warning and is treated as the default, MS932.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.version">
				<td class="nowrap">output.pdf.version</td>
				<td>1.5</td>
				<td class="nowrap">1.1.0</td>
				<td>The PDF version or conformance profile (PDF/A, PDF/X, PDF/UA) to output. In addition to 1.2–1.7 and 2.0, you can specify 1.4A-1, 1.7A-2, 1.7A-2u, 1.7A-2a, 1.7A-3, 1.7A-3a, 2.0A-4, 1.4X-1, 1.6X-4, 2.0X-6, 1.7UA-1, and 2.0UA-2. Features unavailable in the specified version produce warnings and are not applied.<br />For the meaning of each value, how to choose one, and the settings that change automatically when you select a profile, see <a href="#style-pdf-version" class="pageref">PDF versions and features</a>.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.hide-toolbar">
				<td class="nowrap">output.pdf.viewer-preferences.<br />hide-toolbar
				</td>
				<td>false</td>
				<td class="nowrap">3.0.2/2.1.11</td>
				<td>Controls whether the viewer application's toolbar is hidden or shown.<br /> true hides it.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.hide-menubar">
				<td class="nowrap">output.pdf.viewer-preferences.<br />hide-menubar
				</td>
				<td>false</td>
				<td class="nowrap">3.0.2/2.1.11</td>
				<td>Controls whether the viewer application's menu bar is hidden or shown.<br /> true hides it.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.hide-windowUI">
				<td class="nowrap">output.pdf.viewer-preferences.<br />hide-windowUI
				</td>
				<td>false</td>
				<td class="nowrap">3.0.2/2.1.11</td>
				<td>Controls whether the viewer application's in-window UI (thumbnails, attachments, and so on) is hidden or shown.<br />
					true hides it.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.fit-window">
				<td class="nowrap">output.pdf.viewer-preferences.<br />fit-window
				</td>
				<td>false</td>
				<td class="nowrap">3.0.2/2.1.11</td>
				<td>Specifies whether to fit the viewer application's window size to the content.<br />
					true fits it.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.center-window">
				<td class="nowrap">output.pdf.viewer-preferences.<br />center-window
				</td>
				<td>false</td>
				<td class="nowrap">3.0.2/2.1.11</td>
				<td>Specifies whether to center the viewer application's window on the screen according to the content.<br />
					true centers it.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.display-doc-title">
				<td class="nowrap">output.pdf.viewer-preferences.<br />display-doc-title
				</td>
				<td>false</td>
				<td class="nowrap">3.0.2/2.1.11<br />PDF 1.4
				</td>
				<td>Specifies whether to display the document title in the viewer application's title bar.<br />
					true displays it.
				</td>
			</tr>
			<tr
				id="appx-ioprop-output.pdf.viewer-preferences.non-full-screen-page-mode">
				<td class="nowrap">output.pdf.viewer-preferences.<br />non-full-screen-page-mode
				</td>
				<td>use-none</td>
				<td class="nowrap">3.0.2/2.1.11</td>
				<td>Sets the content displayed in the viewer application's side panel.
					<dl>
						<dt>use-none</dt>
						<dd>Displays neither the bookmarks panel nor the thumbnails panel.</dd>
						<dt>use-outlines</dt>
						<dd>Displays the bookmarks panel.</dd>
						<dt>use-thumbs</dt>
						<dd>Displays the thumbnails panel.</dd>
						<dt>use-oc</dt>
						<dd>Displays the layers panel.</dd>
					</dl>
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.print-scaling">
				<td class="nowrap">output.pdf.viewer-preferences.<br />print-scaling
				</td>
				<td>app-default</td>
				<td class="nowrap">3.0.2/2.1.11<br />PDF 1.6
				</td>
				<td>Sets scaling in the viewer application's print settings.
					<dl>
						<dt>scaling-none</dt>
						<dd>Does not scale.</dd>
						<dt>app-default</dt>
						<dd>Leaves scaling to the viewer.</dd>
					</dl>
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.duplex">
				<td class="nowrap">output.pdf.viewer-preferences.<br />duplex
				</td>
				<td>none</td>
				<td class="nowrap">3.0.2/2.1.11<br />PDF 1.7
				</td>
				<td>Sets single-sided or double-sided printing in the viewer application's print settings.
					<dl>
						<dt>none</dt>
						<dd>Uses the viewer's default setting.</dd>
						<dt>simplex</dt>
						<dd>Prints single-sided.</dd>
						<dt>flip-short-edge</dt>
						<dd>Prints double-sided with short-edge binding.</dd>
						<dt>flip-long-edge</dt>
						<dd>Prints double-sided with long-edge binding.</dd>
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
				<td>Sets the checked state of "Choose paper source by PDF page size" in the viewer application's print settings.<br />
					true selects the checkbox.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.print-page-range">
				<td class="nowrap">output.pdf.viewer-preferences.<br />print-page-range
				</td>
				<td>-</td>
				<td class="nowrap">3.0.2/2.1.11<br />PDF 1.7
				</td>
				<td>Sets the initial pages to print.<br /> Specify pages separated by commas, such as "1,2,3,5".
					You can also use hyphens for ranges, such as "1-3,5".
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.viewer-preferences.num-copies">
				<td class="nowrap">output.pdf.viewer-preferences.<br />num-copies
				</td>
				<td>0</td>
				<td class="nowrap">3.0.2/2.1.11<br />PDF 1.7
				</td>
				<td>Sets the initial number of copies to print. 0 uses the viewer's default; other valid values are 2 to 5.
					You cannot specify 6 or more copies.</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.watermark.uri">
				<td class="nowrap">output.pdf.watermark.uri</td>
				<td>-</td>
				<td class="nowrap">2.1.8<br />PDF 1.4
				</td>
				<td>Specify the watermark image address as an absolute path.<br />
					The watermark is drawn as a repeating pattern in front of or behind the PDF content.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.watermark.mode">
				<td class="nowrap">output.pdf.watermark.mode</td>
				<td>back</td>
				<td class="nowrap">2.1.8<br />PDF 1.4
				</td>
				<td>How the watermark image is placed.
					<dl>
						<dt>front</dt>
						<dd>Place in front</dd>
						<dt>back</dt>
						<dd>Place behind</dd>
					</dl>
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.watermark.opacity">
				<td class="nowrap">output.pdf.watermark.opacity</td>
				<td>1</td>
				<td class="nowrap">2.1.8<br />PDF 1.4
				</td>
				<td>The opacity of the watermark image.<br />Specify a decimal value from 0 to 1.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.watermark.view">
				<td class="nowrap">output.pdf.watermark.view</td>
				<td>true</td>
				<td class="nowrap">2.1.8<br />PDF 1.4 (see description)
				</td>
				<td>Makes the watermark image visible on screen.<br />
					When the watermark is placed behind the content, false (hidden) is available only in PDF 1.5 and later.
				</td>
			</tr>
			<tr id="appx-ioprop-output.pdf.watermark.print">
				<td class="nowrap">output.pdf.watermark.print</td>
				<td>true</td>
				<td class="nowrap">2.1.8<br />PDF 1.4 (see description)
				</td>
				<td>Makes the watermark image visible when printed.<br />
					When the watermark is placed behind the content, false (hidden) is available only in PDF 1.5 and later.
				</td>
			</tr>
		</tbody>
	</table>

**Other properties**

| Name | Default | Version | Description |
| --- | --- | --- | --- |
| <a id="appx-ioprop-processing.fail-on-fatal-error"></a>processing.fail-on-fatal-error | true | 4.0.0 | Specifies whether to interrupt conversion when an unrecoverable error occurs.<br /> When false, output is attempted using the content processed up to that point, even if an error occurs. The output may be incomplete. |
| <a id="appx-ioprop-processing.text-spill-budget"></a>processing.text-spill-budget | 8388608 | 4.0.0 | The maximum amount of text retained for replay at page breaks that is kept in memory, in bytes.<br /> Text beyond this limit is written to temporary files. <b>This value does not change the output content</b>. It only changes memory use. Reduce it if memory runs short with very long documents. |
| <a id="appx-ioprop-processing.retained-text-limit"></a>processing.retained-text-limit | 16777216 | 4.0.0 | The maximum amount of text (character count × 2 bytes) in a single element that buffers its content until its dimensions are determined, such as a table, float, inline-block, grid/flex layout, multi-column layout, or absolutely positioned element.<br />Exceeding it causes conversion to fail (message 380F). <b>This value does not change the output content</b>. It places a ceiling on the memory retained by a single conversion, but does not guarantee the actual amount of memory used. Footnotes and page floats waiting for placement, and lookahead for <span class="cssprop">orphans</span>/<span class="cssprop">widows</span>, are not counted. 0 or less means no limit.<br />As a guide, each buffered character uses 40–50 bytes of memory (about 400 MB at the default 16 MB limit). For a small heap, reduce it (around 4 MB for 256 MB or less, 8 MB for 1 GB or less). |
| <a id="appx-ioprop-processing.table-row-emission"></a>processing.table-row-emission | false | 4.0.0 | When true, rows of large tables using automatic layout (<span class="cssdecl">table-layout: auto</span>) are finalized and output sequentially as they fit on a page (limited to simple tables in horizontal writing, with one body group and no captions or similar content). Finalized rows are not retained, reducing memory use for tables with thousands of rows. <b>The output content does not change</b>. The default is false because this feature is still under validation. |
| <a id="appx-ioprop-processing.middle-pass"></a>processing.middle-pass | false | 3.0.4 | When true, runs an intermediate pass that does not actually generate a result. Processing the document later with false generates the result.<br /> For details, see <a href="#style-multipass">Conversion with two or more passes</a>.<br />Intermediate passes do not output results. The session that continues the conversion performs the final layout pass. |
| <a id="appx-ioprop-processing.page-references"></a>processing.page-references | false | 2.0.0 | When true, collects information for the table of contents and `target-text()` (headings and the content of reference targets). When false, this information is not collected, so the table of contents and `target-text()` are unavailable. Page number references (`target-counter()` and others) display numbers even when false<span class="since">4.0.0</span>.<br /> For details, see <a href="#style-page-references">Page references</a>. |
| <a id="appx-ioprop-processing.target-counter.digits"></a>processing.target-counter.digits | 3 | 4.0.0 | The number of digits (1–9) to reserve for `target-counter()` numbers in single-pass PDF and page-split SVG output. The number field is laid out at this width first, and page numbers for later pages are filled in afterward. Numbers are right-aligned in the field; longer numbers overflow to the left (with a warning).<br />For details, see <a href="#style-page-references">Page references</a>. |
| <a id="appx-ioprop-processing.pass-count"></a>processing.pass-count | 1 | 1.2.0 | The number of times to process the document for one formatting operation.<br />The total page count, table of contents, page references, and some selectors require 2 or more passes (except decimal `target-counter()` in single-pass PDF and page-split SVG output). <b>No warning is issued if the setting is too low.</b><br />For details, see <a href="#style-multipass" class="pageref">Conversion with two or more passes</a>.<br />You can specify 1–10. An out-of-range value produces a warning and is treated as 1. |
| <a id="appx-ioprop-processing.concurrency"></a>processing.concurrency | 0 | 4.0.0 | The number of independent layout units to process concurrently. Currently effective only when outputting EPUB spine items as page-split SVG. `0` (the default) selects automatically: the smaller of the CPU core count and 4. `1` processes items sequentially. **The output is identical for any value** because items are independent and results are released in spine order. Only elapsed time and memory use change (layouts are retained for as many items as are processed concurrently). |
| <a id="appx-ioprop-processing.time-limit"></a>processing.time-limit | 0 | 4.0.0 | The maximum elapsed time allowed for converting one document, in milliseconds. 0 or less means no limit. For multiple passes, the total time for all passes is counted. |

### <a id="appx-ioprops-nopi">Properties that cannot be set in a document</a>

Regardless of the <span class="ioprop">input.property-pi</span> setting, the properties listed below
cannot be set with the jp.cssj.property processing instruction.

- Properties starting with input.http.
- input.default-encoding
- input.property-pi
- output.type
- processing.pass-count
