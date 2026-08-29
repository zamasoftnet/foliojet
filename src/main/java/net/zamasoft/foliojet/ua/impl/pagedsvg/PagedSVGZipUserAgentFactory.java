package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.util.Arrays;
import java.util.Iterator;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.UserAgentFactory;

/**
 * ページ分割SVGを<b>1本のZIP</b>で返す出力です(B-2、2026-08-29)。
 *
 * <p>
 * 中身は{@link PagedSVGUserAgentFactory}のバンドルと同じ——展開すれば
 * {@code pages/0001.svg}・{@code assets/…}・{@code manifest.json}が
 * ディレクトリ出力と同じ形で並ぶ。違いは<b>結果が1件になる</b>ことだけで、
 * セッションを使わない一発のREST({@code POST /transcode})でも受け取れる
 * (複数結果のバンドルはそこで4001になる)。利用者報告B-2。
 * </p>
 *
 * <p>
 * 中身は縮めない({@code output.paged-svg.compression}は無視)。ZIP自身が
 * 縮めるので二重になるし、展開した名前は{@code .svg}/{@code .json}で
 * あるべきだから。
 * </p>
 */
public class PagedSVGZipUserAgentFactory implements UserAgentFactory {
	/** ZIPで返すバンドルのメディア型。 */
	public static final String MIME_TYPE = "application/vnd.copper.paged-svg+zip";

	@Override
	public boolean match(final String key) {
		return MIME_TYPE.equals(key);
	}

	@Override
	public Iterator<Type> types() {
		return Arrays.asList(new Type[] { new Type("Paged SVG bundle (ZIP)", MIME_TYPE, "zip") }).iterator();
	}

	@Override
	public UserAgent createUserAgent() {
		return new PagedSVGUserAgent(true);
	}
}
