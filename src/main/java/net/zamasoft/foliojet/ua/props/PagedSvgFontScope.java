package net.zamasoft.foliojet.ua.props;

/**
 * Whether to create one font subset for the whole document or one per page.
 *
 * <p>
 * A tradeoff between total size and time until the first page can be rendered.
 * Measurements with a Japanese book (Natsume Soseki's "Kokoro", A5, 350 pages):
 * </p>
 *
 * <table border="1">
 * <caption>Measurements (2026-09-02)</caption>
 * <tr><th></th><th>{@code document}</th><th>{@code page}</th></tr>
 * <tr><td>Total fonts</td><td>0.25 MB</td><td>7.1 MB (28 times)</td></tr>
 * <tr><td>Total output</td><td>11.8 MB</td><td>18.6 MB (1.6 times)</td></tr>
 * <tr><td>Read only 3 pages</td><td>About 340 KB</td><td>About 150 KB</td></tr>
 * <tr><td>Read the whole book</td><td>11.8 MB</td><td>18.6 MB</td></tr>
 * </table>
 *
 * <p>
 * One page uses 104 distinct characters, about one-tenth of the document's 1,102, but its subset
 * is <b>only 1/12 the size</b>, not 1/28, because of WOFF2's fixed overhead and cmap/hmtx.
 * Summing distinct character counts across all pages gives 33-fold duplication.
 * The download-size crossover occurs around 12–13 pages.
 * For documents with only Latin text, the difference is negligible.
 * </p>
 */
public enum PagedSvgFontScope implements PropCode {
	/**
	 * Creates one subset for the whole document (default). Minimizes total size.
	 *
	 * <p>
	 * However, <b>the required glyphs are unknown until all pages have been laid out</b>,
	 * so subsets can only be emitted at the end. Body text uses private-use characters,
	 * with glyphs available only in the fonts, so the consumer cannot draw even one character
	 * until conversion finishes.
	 * </p>
	 */
	DOCUMENT,

	/**
	 * Creates subsets per page and <b>emits that page's subsets whenever the page closes</b>.
	 *
	 * <p>
	 * Rendering can begin when the first page and its fonts arrive. Readers that fetch only
	 * pages near the visible page also download less. Self-contained pages are the simplest
	 * to handle. The cost is total size.
	 * </p>
	 */
	PAGE;
}
