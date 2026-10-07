## <a id="style-performance">Speed and memory</a>

The engine is designed to keep producing large volumes of reports reliably.
This chapter explains **where layout itself uses memory and time**.
For settings when you run it as a server,
see <b>Server speed and memory</b> (server product manual).

### <a id="admin-perf-memory">Memory use does not depend on document size</a>

Instead of reading the entire document before laying it out,
the engine **lays out incoming content on pages and discards it as it goes**.

As a result, the memory requirement depends on
<a href="#style-page-layout" class="pageref">the size of the page being laid out</a>, rather than the document's total page count.
A report with 100,000 pages can be processed with about the same amount of memory as a one-page report.

This lets you estimate requirements as follows.

<dl>

<dt>Memory per conversion is nearly constant</dt>
<dd>
	You can estimate the total by multiplying the measured memory for one conversion by the number of concurrent conversions.
	This design reduces the likelihood of failures that occur only with large documents.
</dd>

<dt>Adding memory does not make conversion faster</dt>
<dd>
	Adding more memory has no effect if the available memory is not being used up.
	To improve speed, see <b>Concurrency</b> (server product manual).
</dd>

</dl>

However, the following cases increase the amount of content held for one page, and therefore increase memory use.

- An extremely large number of elements on one page (such as a table with tens of thousands of rows squeezed onto one page)
- Huge images placed at their original size
- Many columns in a multi-column layout, requiring all columns to be retained for balancing


### <a id="admin-perf-passes">The pass count directly affects processing time</a>

When you specify <a href="#style-multipass" class="pageref">two or more passes</a>,
**the document is read that many times**. Two passes take roughly twice as long.

The default for <span class="ioprop">processing.pass-count</span> is 1.
If a document does not use
<a href="#style-multipass-required" class="pageref">features that require two passes</a>,
such as page numbers in the table of contents or the total page count, setting it to 2 only doubles the time without changing the result.

**Specify it only for documents that need it.**


### <a id="admin-perf-resources">Retrieving external resources</a>

Images, stylesheets, and fonts referenced by a document
are retrieved for each conversion. **Slow retrieval makes the entire conversion wait.**

In particular, if the document references an external site that does not respond,
conversion stalls until the connection closes.

<dl>

<dt>Limit retrieval destinations</dt>
<dd>
	Use <span class="ioprop">input.include</span> /
	<span class="ioprop">input.exclude</span>
	to limit the URIs that may be retrieved.
	→ <a href="#prog-input-restriction" class="pageref">Restricting resource access</a>
</dd>

<dt>Keep resources locally</dt>
<dd>
	Store frequently used images and fonts locally on the server,
	instead of retrieving them from external sources.
</dd>

</dl>


### <a id="admin-perf-images">Images</a>

Images affect both PDF size and processing time.

<dl>

<dt>Reduce images with excessively large original dimensions</dt>
<dd>
	When you set <span class="ioprop">output.pdf.image.max-width</span> /
	<span class="ioprop">output.pdf.image.max-height</span>,
	images exceeding those limits are reduced before embedding.
	An image that occupies only a few centimeters on the page does not need original dimensions of several thousand pixels.
</dd>

<dt>Choose the compression method</dt>
<dd>
	The default for <span class="ioprop">output.pdf.image.compression</span> is
	lossless compression (flate). For documents with many photos, jpeg produces smaller PDFs.
	For diagrams and screenshots, jpeg can reduce quality and increase size.
</dd>

<dt>Do not recompress images that are already JPEG</dt>
<dd>
	With the default (raw) for <span class="ioprop">output.pdf.jpeg-image</span>,
	JPEG images are embedded as they are, without decoding.
	Using recompress reduces image quality and slows conversion. **You should normally leave this setting unchanged.**
</dd>

</dl>


### <a id="style-perf-where">What to check first when conversion is slow</a>

1. **Pass count**. Is <span class="ioprop">processing.pass-count</span> set to 2 or more?
   Limit this to documents that need it.
2. **External resources**. Does the document reference a site that does not respond?
   Does the log show <a href="#appx-messages" class="pageref">2811 (image could not be read)</a> or
   2814 (retrieval was denied)?
3. **Images**. Does the document contain many images with large original dimensions?

If you still cannot identify the cause,
also see
<a href="#prog-troubleshooting" class="pageref">Troubleshooting</a>.
