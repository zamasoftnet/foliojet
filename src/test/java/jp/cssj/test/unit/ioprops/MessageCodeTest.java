package jp.cssj.test.unit.ioprops;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * メッセージハンドラへ通知されるコードの契約です(2026-08-02新設)。
 *
 * <p>
 * <b>この層のテストが1つも無かった。</b> 説明書(5200_messages.md)は
 * ページ番号・見出し・パス・タイトルや各種警告のコードを定めているが、
 * 実際に通知されるかは確かめられていなかった。メッセージは<b>利用者が
 * 変換の進行と異常を知る唯一の口</b>で、止まっていても出力は出るため
 * 気づきにくい。
 * </p>
 */
public class MessageCodeTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 見出しとタイトルを持ち、2ページになる文書。 */
	private static final File HEADINGS = new File("files/unittest/ioprops/headings.html");

	private static final File WITH_IMAGE = new File("files/unittest/ioprops/link-and-image.html");

	/** 存在しない画像を参照する文書。 */
	private static final File MISSING_IMAGE = new File("files/unittest/ioprops/missing-image.html");

	private final List<Short> codes = new ArrayList<>();

	private final List<String> args0 = new ArrayList<>();
	private final List<String[]> messageArgs = new ArrayList<>();

	/** 1801: ページの開始が通知されること。 */
	public void testPageNumber() throws Exception {
		this.convert(HEADINGS, props());
		assertTrue("ページ番号(1801)が通知されること: " + this.codes,
				this.codes.contains((short) 0x1801));
	}

	/**
	 * 1802: 見出しが通知されること。
	 *
	 * <p>
	 * <b>しおりかページ参照が有効なときだけ通知される</b>(2026-08-02に
	 * 実装で確認。見出しの走査自体がその条件下でしか行われない)。
	 * </p>
	 */
	public void testHeading() throws Exception {
		this.convert(HEADINGS, props("output.pdf.bookmarks", "true"));
		assertTrue("見出し(1802)が通知されること", this.codes.contains((short) 0x1802));
		assertTrue("見出しの文字列が渡ること: " + this.args0,
				this.args0.stream().anyMatch(a -> a != null && a.contains("PROBE-HEADING")));
	}

	/** 1805: タイトルが通知されること。 */
	public void testTitle() throws Exception {
		this.convert(HEADINGS, props());
		assertTrue("タイトル(1805)が通知されること", this.codes.contains((short) 0x1805));
	}

	/** 1803: 複数パスならパス番号が通知されること。 */
	public void testPassCount() throws Exception {
		this.convert(HEADINGS, props("processing.pass-count", "2", "processing.page-references", "true"));
		assertTrue("パス番号(1803)が通知されること: " + this.codes,
				this.codes.contains((short) 0x1803));
	}

	/**
	 * 参照先が存在しない資源があれば警告が出ること。
	 *
	 * <p>
	 * ACLで除外しても<b>警告にはならない</b>——{@code file:}資源は
	 * 差し込まれたリゾルバが解決してACLを通らないため(PLAN §3の
	 * 判断待ち項目)。ここでは実際に存在しないURIを参照する。
	 * </p>
	 */
	public void testBrokenResourceWarning() throws Exception {
		this.convert(MISSING_IMAGE, props());
		boolean found = false;
		for (int i = 0; i < this.codes.size(); ++i) {
			if (this.codes.get(i).shortValue() != MessageCodes.WARN_MISSING_IMAGE) {
				continue;
			}
			found = true;
			final String[] args = this.messageArgs.get(i);
			assertNotNull("2811に引数があること", args);
			assertEquals("2811の引数はURIと段階の2個であること", 2, args.length);
			assertNotNull("2811に失敗段階があること", args[1]);
			assertTrue("2811の段階が固定語であること: " + args[1],
					"resolve".equals(args[1]) || "fetch".equals(args[1]) || "decode".equals(args[1])
							|| "decode: unsupported or corrupt image".equals(args[1])
							|| args[1].matches("fetch: HTTP [1-5][0-9][0-9]"));
		}
		assertTrue("画像警告(2811)が通知されること: " + this.codes, found);
	}

	/** 不正な入出力プロパティは警告になること(値が壊れていても変換は続く)。 */
	public void testBadPropertyWarning() throws Exception {
		this.convert(HEADINGS, props("output.page-width", "not-a-length"));
		assertFalse("不正な値が警告として通知されること: " + this.codes, warnings().isEmpty());
	}

	private List<Short> warnings() {
		final List<Short> list = new ArrayList<>();
		for (final Short code : this.codes) {
			if ((code & 0xF000) == 0x2000) {
				list.add(code);
			}
		}
		return list;
	}

	private static Map<String, String> props(final String... kv) {
		final Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	private void convert(final File document, final Map<String, String> properties) throws Exception {
		final File out = new File("local/unittest/pdf/" + this.getClass().getName() + ".pdf");
		out.getParentFile().mkdirs();
		this.codes.clear();
		this.args0.clear();
		this.messageArgs.clear();
		try (OutputStream stream = new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setMessageHandler((code, args, mes) -> {
					this.codes.add(code);
					this.args0.add(args != null && args.length > 0 ? args[0] : null);
					this.messageArgs.add(args == null ? null : args.clone());
				});
				session.setResults(new SingleResult(new StreamFragmentedOutput(stream)));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				if (!properties.containsKey("input.include") && !properties.containsKey("input.exclude")) {
					session.property("input.include", "**");
				}
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, document, "text/html", null);
			} finally {
				session.close();
			}
		}
	}
}
