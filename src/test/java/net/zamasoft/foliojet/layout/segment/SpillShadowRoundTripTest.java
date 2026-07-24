package net.zamasoft.foliojet.layout.segment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.LayoutSourceTestHooks;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * E-6増分2(2026-07-24新設)のshadow round-tripテストです:
 * 代表文書のtranscode中に{@link LayoutSource}の全追記イベントを外から
 * 観測し、{@link LayoutSourceEventConverter}で{@link SegmentEvent}へ
 * 1:1変換 → {@link SegmentEventCodec}でencode → {@link SpillStore}へ
 * 書き込み → cursorで読み出し → decode → 元のSegmentEvent列と
 * ordinal単位で等値比較する。
 *
 * <p>
 * <b>等値基準</b>: {@code SegmentEvent}は全variantがrecordのため
 * {@code equals}(構造等値)で比較する。参照variant(BeginBox/
 * Replaced)はside table契約により同一インスタンス復元となるので、
 * 追加で{@code assertSame}(参照等値)も検証する。
 * </p>
 *
 * <p>
 * production経路(レイアウト処理)は一切変更しない——観測は
 * {@link LayoutSourceTestHooks}(テスト専用フック)のみで行う。
 * </p>
 */
public class SpillShadowRoundTripTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 代表文書: 表・段組・float・ルビをカバーする(display-list golden対象から)。 */
	private static final String[] DOCUMENTS = { //
			"0240-table/z-order.html", //
			"0400-column-count/nest.html", //
			"0120-float/auto-width.html", //
			"3060-RUBY/ruby-split-through.html", //
	};

	public void testShadowRoundTripOnRepresentativeDocuments() throws Exception {
		for (final String doc : DOCUMENTS) {
			this.roundTrip(doc);
		}
	}

	private void roundTrip(final String doc) throws Exception {
		// 1. transcode中の全append列を観測する(compactで破棄される前の
		// 全イベント。レイアウトが別スレッドで走る構成に備えsynchronized)
		final List<LayoutSource.Event> observed = Collections.synchronizedList(new ArrayList<>());
		LayoutSourceTestHooks.setAppendObserver(observed::add);
		try {
			this.transcode(new File("files/unittest/" + doc));
		} finally {
			LayoutSourceTestHooks.setAppendObserver(null);
		}
		assertFalse(doc + ": イベントが観測されていません", observed.isEmpty());

		// 2. 1:1変換(既存converterの契約: イベント数を維持する)
		final List<SegmentEvent> original = new ArrayList<>(observed.size());
		for (final LayoutSource.Event event : observed) {
			original.add(LayoutSourceEventConverter.convert(event));
		}
		assertEquals(doc + ": 変換は1:1のはず", observed.size(), original.size());

		// 3. encode → SpillStore → cursor → decode
		final SegmentEventCodec codec = new SegmentEventCodec();
		final List<SegmentEvent> decoded = new ArrayList<>(original.size());
		try (SpillStore store = SpillStore.create()) {
			for (final SegmentEvent event : original) {
				store.append(codec.encode(event));
			}
			assertEquals(doc, original.size(), store.recordCount());
			final SpillStore.Cursor cursor = store.cursor(0, store.recordCount());
			while (cursor.hasNext()) {
				decoded.add(codec.decode(cursor.next()));
			}
		}

		// 4. ordinal単位の等値比較
		assertEquals(doc + ": イベント数がround-tripで変わっています", original.size(), decoded.size());
		final Set<Class<?>> variants = new HashSet<>();
		for (int i = 0; i < original.size(); ++i) {
			final SegmentEvent expected = original.get(i);
			final SegmentEvent actual = decoded.get(i);
			assertEquals(doc + " ordinal=" + i, expected.getClass(), actual.getClass());
			assertEquals(doc + " ordinal=" + i, expected, actual);
			// side table契約: 参照variantは同一インスタンス復元
			if (expected instanceof SegmentEvent.BeginBox begin) {
				assertSame(doc + " ordinal=" + i, begin.recipe(), ((SegmentEvent.BeginBox) actual).recipe());
			} else if (expected instanceof SegmentEvent.Replaced replaced) {
				assertSame(doc + " ordinal=" + i, replaced.recipe(), ((SegmentEvent.Replaced) actual).recipe());
			}
			variants.add(expected.getClass());
		}
		// 観測が自明(単一variantのみ)でないことの確認
		assertTrue(doc + ": BeginBoxが観測されていません", variants.contains(SegmentEvent.BeginBox.class));
		assertTrue(doc + ": EndBoxが観測されていません", variants.contains(SegmentEvent.EndBox.class));
		assertTrue(doc + ": Textが観測されていません", variants.contains(SegmentEvent.Text.class));
	}

	/** DisplayListGoldenTestと同じ設定のtranscode(出力PDFは破棄)。 */
	private void transcode(final File source) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			session.property("input.property-pi", "true");
			CTISessionHelper.transcodeFile(session, source, "text/html", null);
		} finally {
			session.close();
		}
		assertTrue(source + ": PDFが出力されていません", out.size() > 0);
	}
}
