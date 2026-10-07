<h2 id="prog-limit">Operational limits</h2>

When you **convert documents submitted by arbitrary users**, you need settings
that protect the server. This chapter describes those features.

There are two things to protect.

<dl>

<dt>Prevent access to resources that must not be read</dt>
<dd>Prevent the server from following references in a document and reading
	files or internal URLs that it should not expose.
	→ [Restricting resource access](#prog-input-restriction)</dd>
<dt>Prevent excessive use</dt>
<dd>Extremely large main documents, numerous or large external resources, results with many pages,
	and large outputs can put a heavy load on the server, whether intentionally or not.
	→ [Limiting input size and external resources](#prog-input-size-limit),
	[Limiting the page count](#prog-page-limit), [Limiting data size](#prog-size-limit)</dd>
<dt>Terminate processing that does not finish</dt>
<dd>You can limit the elapsed time for one document in milliseconds with
	<span class="ioprop">processing.time-limit</span>. For multiple passes, this is the total time for all passes.
	The default value of 0 means unlimited.<br />
	During development, to detect only a state in which no pages are progressing,
	set the JVM system property <tt>-Dfoliojet.noProgressSeconds=seconds</tt>.
	<b>The default is unlimited (no detection)</b>, because legitimate conversions
	of production documents can take time. This setting is intended to detect hangs
	in development and test environments. <b>This limit applies to a single conversion</b>,
	and differs from the connection timeout (provided separately by the server product).</dd>

</dl>

When an output limit takes effect, the driver throws an exception,
or the document conversion function returns an error.
The driver's error handler is notified of an error (rather than a warning or fatal error).

### <a id="prog-input-restriction">Restricting resource access</a>

Documents can contain **references to external resources**, such as stylesheets and images.
The layout engine retrieves these resources.

This is dangerous when you convert HTML submitted by users.
A reference to `file:///etc/passwd` or a URL accessible only from the internal network
can make **the server retrieve it on the user's behalf**.

For this reason, **no external resources are read by default**.

<div class="note">

**If images or stylesheets do not appear, check this first.**
Without permission settings, references are ignored without being loaded.

</div>

#### Configuring permissions and restrictions

Set URI patterns to allow with <span class="ioprop">input.include</span>,
and URI patterns to deny with <span class="ioprop">input.exclude</span>.
You can set both **as many times as needed**, and **they are evaluated in the order set**.

Patterns support two types of wildcards.

| Notation | Meaning |
| --- | --- |
| `*` | Any string that does not contain `/` |
| `**` | Any string, including `/` |

Evaluation proceeds as follows.

1. Patterns are checked in the order set, and **the first match determines the result**
	(an include match allows access; an exclude match denies access)
2. If no pattern matches, access is **denied**

```java
// Allow only resources under your company's site
session.property("input.include", "https://www.example.com/**");

// Exclude the administration pages (set this first so it is evaluated before include)
session.property("input.exclude", "https://www.example.com/admin/**");
session.property("input.include", "https://www.example.com/**");
```

**Order changes the result.** If you set `input.include` first in the example above,
`/admin/` also matches include first, and the restriction has no effect.

To allow all resources, use the following setting.
Use it only when you process trusted documents exclusively.

```java
session.property("input.include", "**");
```

#### data: URIs are always loaded

URIs beginning with `data:` (with the content embedded directly in the URI)
are always loaded, regardless of the settings.
Because the URI itself contains the resource,
it cannot be used to make the server retrieve something.

#### Measures that prevent bypasses

URIs are **normalized before** matching.
For http/https URIs, `%xx` escapes are also decoded before matching.
You cannot bypass the patterns by hiding `../` with notation such as `%2e%2e/`,
or by using a different representation of the same resource.

#### Passing resources directly from the application

You can also **pass the content from the application**, without having the server retrieve it.
URI permissions and restrictions, as well as the resource count and size limits below, still apply.
See the source resolver in **API access overview** (server product manual).

### <a id="prog-input-size-limit">Limiting input size and external resources</a>

For a service that accepts documents from arbitrary users, set limits on the amount
of data read as well as the permitted URI range.

| Property | Unit | What it limits |
| --- | --- | --- |
| <span class="ioprop">input.size-limit</span> | Bytes | One main document. For EPUB, the entire EPUB file |
| <span class="ioprop">input.resource-size-limit</span> | Bytes | The cumulative amount read from external resources resolved from the main document |
| <span class="ioprop">input.resource-count-limit</span> | Number of URIs | Distinct external resources resolved from the main document |
| <span class="ioprop">input.image-pixel-limit</span> | Pixels | Width × height of each input image (checked before decoding) |
| <span class="ioprop">output.image-pixel-limit</span> | Pixels | Each generated raster image (such as the type area for image output) |
| <span class="ioprop">processing.time-limit</span> | Milliseconds | The entire conversion of one document. Multiple passes are added together |

All are unlimited by default. When you use size limits, pass input as a `Source`
that returns a byte stream. A custom `Source` that returns only a character `Reader`
fails safely because the character count does not equal the encoded byte count.

An external resource counts as one resource even if the same normalized URI is referenced more than once.
The cumulative size counts bytes actually read, so retrieving the same resource again adds to the total.
`data:` URIs are also input content and are subject to size limits.

Even if a compressed image file is small, decoding it uses width × height × 4 bytes of memory (about 400 MB for 10,000 × 10,000 pixels). A limit on the number of bytes read does not prevent this, so limit the pixel count with <span class="ioprop">input.image-pixel-limit</span>. An image that exceeds the limit is not decoded and is treated as an unreadable image (message 2811).

```java
session.property("input.size-limit", "10485760");           // Main document: 10 MiB
session.property("input.resource-size-limit", "52428800"); // External resources: 50 MiB total
session.property("input.resource-count-limit", "200");
session.property("input.image-pixel-limit", "25000000");   // 25 million pixels per image
session.property("processing.time-limit", "30000");        // 30 seconds
```

#### <a id="prog-operator-limits">Operator limits (limits that cannot be relaxed)</a>

<span class="since">4.0.0</span>Even if you specify the limits above in a profile (<tt>jp.cssj.driver.default</tt>),
client settings or processing instructions in the document can relax them. If you use the system property <tt>jp.cssj.driver.limits</tt>
to point to a limits file (a properties file), the **smaller value** is used when comparing its values with settings from any other source.
Stricter settings take effect; more permissive settings are capped at the limit (warning 2825). The supported properties are
<span class="ioprop">input.size-limit</span>, <span class="ioprop">input.resource-size-limit</span>,
<span class="ioprop">input.resource-count-limit</span>, <span class="ioprop">input.image-pixel-limit</span>,
<span class="ioprop">output.image-pixel-limit</span>, <span class="ioprop">output.size-limit</span>,
<span class="ioprop">output.page-limit</span>, <span class="ioprop">processing.time-limit</span>,
<span class="ioprop">processing.retained-text-limit</span>, and <span class="ioprop">processing.concurrency</span>.
If the file contains any other names or nonnumeric values, a session cannot be created.

### <a id="prog-page-limit">Limiting the page count</a>

This feature forcibly interrupts processing when the output reaches a specified number of pages.

Set the maximum number of output pages with <span class="ioprop">output.page-limit</span>.
By default, the page count is unlimited.

When the page count limit is exceeded, you can choose to output the pages produced so far or discard the result.

Set <span class="ioprop">output.page-limit.abort</span> to "normal" to output the pages produced so far.
Set it to "force" to discard the result. The default is "force".

However, when you use [two or more passes](#style-multipass), the result is discarded if the page limit is reached on any pass except the final one.
Also, when you output the result as images (rather than PDF), pages produced so far are output in either case (on the final pass).

### <a id="prog-size-limit">Limiting data size</a>

This feature forcibly interrupts processing when the output reaches a specified physical size.

Set the maximum data size in bytes with <span class="ioprop">output.size-limit</span>.
By default, data size is unlimited.
