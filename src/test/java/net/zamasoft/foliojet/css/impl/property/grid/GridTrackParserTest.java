package net.zamasoft.foliojet.css.impl.property.grid;

import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.GridLineValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Grid G0の解析テストです(consult-codex-2026-07-31-grid.txt §2/G0)。
 * track list(固定長・auto・fr・整数repeat展開・上限4096)と
 * grid-line(auto・整数線番号・span)の受理/拒否を固定する。
 * computed(絶対化)はstyle文脈が要るため、ここではRaw中間形の受理までを
 * 対象とし、絶対化はGridBox系の統合テストで固定する。
 */
public class GridTrackParserTest extends TestCase {

	private static TokenStream tokens(final String declaration) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList decls = CSSReaderDeclarationList.readFromString(declaration, settings);
		assertNotNull("宣言のパースに失敗: " + declaration, decls);
		final List<CSSDeclaration> all = decls.getAllDeclarations();
		assertEquals(1, all.size());
		final List<CssToken> ts = Tokens.fromExpression(all.get(0).getExpression());
		return new TokenStream(ts);
	}

	private static UserAgent ua() {
		return (UserAgent) java.lang.reflect.Proxy.newProxyInstance(GridTrackParserTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					if ("getPixelsPerInch".equals(method.getName())) {
						return 96.0;
					}
					if ("toString".equals(method.getName())) {
						return "GridTrackParserTest.UserAgent";
					}
					if ("hashCode".equals(method.getName())) {
						return System.identityHashCode(proxy);
					}
					if ("equals".equals(method.getName())) {
						return proxy == args[0];
					}
					throw new UnsupportedOperationException(method.toString());
				});
	}

	private static Value parseTracks(final String value) throws PropertyException {
		return ((GridTemplateTracks) GridTemplateTracks.COLUMNS)
				.parseValue(tokens("grid-template-columns: " + value), ua(), null);
	}

	private static void assertTracksRejected(final String value) {
		try {
			parseTracks(value);
			fail("拒否されるべきtrack list: " + value);
		} catch (PropertyException e) {
			// expected
		}
	}

	public void testTrackListAccepted() throws Exception {
		assertNotNull(parseTracks("100pt auto 1fr"));
		assertNotNull(parseTracks("repeat(3, 50pt 1fr)"));
		assertNotNull(parseTracks("2em 0.5fr"));
		assertNotNull(parseTracks("none"));
		// minmax()/max()/min()は仕様外の近似対応(2026-08-06、
		// GridTemplateTracks.javaのクラスjavadoc参照)。真のtrack sizing
		// algorithmは実装せず、minmax()は最大値だけを採用し最小値は捨てる。
		// yahoo.co.jpの実物CSSで発覚した未対応を埋める
		assertNotNull(parseTracks("minmax(50pt, 1fr)"));
		assertNotNull(parseTracks("minmax(0, 1fr)")); // Tailwindのgrid-cols-Nが常に使う形
		assertNotNull(parseTracks("repeat(9, minmax(30pt, auto))")); // yahoo.co.jpの実物
		assertNotNull(parseTracks("max(44px, 4.4rem)")); // yahoo.co.jpの実物
		assertNotNull(parseTracks("min(44px, 4.4rem)"));
	}

	public void testTrackListRejected() {
		assertTracksRejected("-1fr"); // 負のfr
		assertTracksRejected("50%"); // %トラックはサブセット外
		assertTracksRejected("repeat(0, 50pt)"); // 0回
		assertTracksRejected("repeat(2, repeat(2, 50pt))"); // 入れ子repeat
		assertTracksRejected("repeat(5000, 50pt)"); // 展開上限4096超
		assertTracksRejected("minmax(50pt)"); // 引数不足(2引数必須)
		assertTracksRejected("minmax(50pt, 50%)"); // 最大値が%は依然サブセット外
		assertTracksRejected("max(50%, 1fr)"); // %引数は依然サブセット外
		// named line("[a] 100pt")はトークン化層が角括弧を落とすため
		// このプロパティ層には届かない(=100ptとして受理される)。
		// 拒否したい場合はトークン層の対応が要る——現状は容認(記録)
	}

	public void testGridLine() throws Exception {
		final GridPlacement info = (GridPlacement) GridPlacement.COLUMN_START;
		assertTrue(((GridLineValue) info.parseValue(tokens("grid-column-start: auto"), ua(), null)).isAuto());
		final GridLineValue line = (GridLineValue) info.parseValue(tokens("grid-column-start: -2"), ua(), null);
		assertFalse(line.isAuto());
		assertFalse(line.isSpan());
		assertEquals(-2, line.getNumber());
		final GridLineValue span = (GridLineValue) info.parseValue(tokens("grid-column-start: span 3"), ua(), null);
		assertTrue(span.isSpan());
		assertEquals(3, span.getNumber());
	}

	public void testGridLineRejected() {
		final GridPlacement info = (GridPlacement) GridPlacement.ROW_START;
		for (final String bad : new String[] { "0", "span 0", "span -1", "1.5", "a" }) {
			try {
				info.parseValue(tokens("grid-row-start: " + bad), ua(), null);
				fail("拒否されるべきgrid-line: " + bad);
			} catch (PropertyException e) {
				// expected
			}
		}
	}
}
