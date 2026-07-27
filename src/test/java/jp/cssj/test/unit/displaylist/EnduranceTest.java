package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.builder.impl.TableBuildStats;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.LayoutSourceTestHooks;
import net.zamasoft.foliojet.layout.fragment.TextSpillException;
import net.zamasoft.foliojet.layout.rescue.RescueStats;
import net.zamasoft.foliojet.layout.segment.TextSpill;
import net.zamasoft.foliojet.layout.segment.TextSpillTestHooks;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * E-6(spillableテープ基盤)の耐久試験です(2026-07-24。
 * docs/history/2026-07-24-e6-remaining-design-decision.md の
 * 「耐久試験(案Aの合格条件)」——適応裁定でスコープ調整済み)。
 *
 * <p>
 * 常時CIで走る縮小版(このクラスの大半のテスト)と、
 * {@code -Dfoliojet.perf}ゲートの本格版({@code -Xmx128m}別JVM完走試験。
 * {@code ./gradlew test --tests "*.EnduranceTest" -Dfoliojet.perf= })から
 * 成る。保証できる項目はassertで固定し、保証できない既知の残存
 * (完成TableBoxの全行box木保持——行単位親コミットはIncremental統合の
 * 将来増分)は実測値の報告に留める——無理なassertで赤いテストを作らない。
 * </p>
 *
 * <ul>
 * <li>テキストspill上界: 同一CSSで本文量8倍でも
 * {@code LIVE_TEXT_PAYLOAD_BYTES}高水位は「予算+最大1record」以内
 * (本文量非比例)。spill量は本文量比例。</li>
 * <li>spill障害の型付き失敗: write/read失敗は{@link TextSpillException}、
 * 一時ファイル残存ゼロ、後続変換は正常。</li>
 * <li>header/footer進捗: 反復ヘッダがページより高い極端fixtureでも
 * 無限ループしない(既存保護: {@code TableCutter.keepOrMoveAll}——
 * ヘッダ+フッタが収まらないときページ先頭ならKEEP(はみ出し確定)、
 * そうでなければMOVE(次ページ先頭では必ずKEEP)で常に有限)。</li>
 * <li>-Xmx128m別JVM(perfゲート): 巨大単一セルauto表の完走+kill switch
 * (全legacy)対照、10万行短セルauto表の完走規模の実測。</li>
 * <li>救済分割(2026-07-25、増分8): 20,000pt浮動体+20,000pt書字方向
 * 不一致ブロック+3,000pt行の同居fixtureで、クラッシュ・無限ループ・
 * 停滞がないこと、ページ数が有限で妥当なこと、意図しない白紙ページが
 * できないことを固定する(常時CI)。</li>
 * </ul>
 */
public class EnduranceTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final File WORK_DIR = new File("local/unittest/endurance");

	/**
	 * 画像の<b>絶対URI</b>。相対パスにするとフィクスチャを動かした瞬間に
	 * 画像が黙って消え、別の文書になる(2026-07-27)。
	 */
	private static final String RED_PNG_URI = new File("files/unittest/red.png").getAbsoluteFile().toURI()
			.toString();

	// ------------------------------------------------------------------
	// 1. テキストspill上界(常時CI)
	// ------------------------------------------------------------------

	/**
	 * 同じCSSで本文量を8倍にした2文書を極小予算(4KB)でtranscodeし、
	 * (1) inline保持高水位が両文書とも「予算+最大1record」以内
	 * (本文量に比例しない——E-6の中心保証)、
	 * (2) spill済みbytesは本文量にほぼ比例して増える(8倍文書で4〜12倍)、
	 * を固定する。
	 */
	public void testTextPayloadHighWaterBoundedByBudgetPlusOneRecord() throws Exception {
		final long budget = 4096;
		final File doc1 = generateProse("spill-bound-1x", 160);
		final File doc8 = generateProse("spill-bound-8x", 160 * 8);
		try {
			final long[] r1 = this.measureSpill(doc1, "spill-bound-1x", budget);
			final long[] r8 = this.measureSpill(doc8, "spill-bound-8x", budget);
			final long live1 = r1[0], spilled1 = r1[1], maxRecord1 = r1[2];
			final long live8 = r8[0], spilled8 = r8[1], maxRecord8 = r8[2];
			System.err.println("[E-6 endurance spill-bound] budget=" + budget + " live1x=" + live1 + " spilled1x="
					+ spilled1 + " maxRecord1x=" + maxRecord1 + " live8x=" + live8 + " spilled8x=" + spilled8
					+ " maxRecord8x=" + maxRecord8);

			assertTrue("1x文書でspillが発火していません(fixtureが小さすぎます): " + spilled1, spilled1 > 0);
			assertTrue("8x文書でspillが発火していません: " + spilled8, spilled8 > 0);
			// 保証の中心: inline保持高水位は本文量に依存せず「予算+最大1record」以内
			assertTrue("1x: inline保持高水位が予算+1recordを超えています: " + live1 + " > " + budget + "+" + maxRecord1,
					live1 <= budget + maxRecord1);
			assertTrue("8x: inline保持高水位が予算+1recordを超えています(本文量比例の疑い): " + live8 + " > " + budget
					+ "+" + maxRecord8, live8 <= budget + maxRecord8);
			// spill量は本文量比例(8倍文書で4〜12倍——決定的な予算判定のもとで
			// inline share分だけ厳密な8倍からずれうる)
			assertTrue("spill量が本文量に比例していません: 1x=" + spilled1 + ", 8x=" + spilled8,
					spilled8 >= spilled1 * 4 && spilled8 <= spilled1 * 12);
		} finally {
			doc1.delete();
			doc8.delete();
		}
	}

	/** 極小予算でtranscodeし {live高水位, spilled bytes, 最大Chars record bytes} を返す。 */
	private long[] measureSpill(final File doc, final String name, final long budget) throws Exception {
		ContinuationStats.reset();
		final AtomicLong maxRecordBytes = new AtomicLong();
		LayoutSourceTestHooks.setAppendObserver(event -> {
			if (event instanceof LayoutSource.Chars chars) {
				maxRecordBytes.accumulateAndGet(chars.payload().utf16Length() * 2L, Math::max);
			}
		});
		try {
			this.transcode(doc, name, String.valueOf(budget));
		} finally {
			LayoutSourceTestHooks.setAppendObserver(null);
		}
		return new long[] { ContinuationStats.LIVE_TEXT_PAYLOAD_BYTES.get(), ContinuationStats.SPILLED_TEXT_BYTES.get(),
				maxRecordBytes.get() };
	}

	// ------------------------------------------------------------------
	// 3. spill障害の型付き失敗(常時CI)
	// ------------------------------------------------------------------

	/**
	 * spill write/read失敗が{@link TextSpillException}の型で失敗すること
	 * (黙殺・裸のIOException・非決定的フォールバックのいずれでもない)を
	 * LayoutSource単体で固定する。close後の一時ファイル残存ゼロも確認。
	 */
	public void testSpillIoFailureIsTypedTextSpillException() throws Exception {
		// (a) write失敗
		try (LayoutSource source = new LayoutSource(0)) {
			TextSpillTestHooks.setFaultInjector(() -> {
				throw new IOException("injected write failure");
			}, null);
			try {
				source.appendChars(0, "endurance".toCharArray(), 0, 9, false);
				fail("spill書き込み失敗はTextSpillExceptionになるはずです");
			} catch (final TextSpillException e) {
				assertTrue("原因IOExceptionが失われています", e.getCause() instanceof IOException);
			} finally {
				TextSpillTestHooks.clearFaultInjector();
			}
			final TextSpill spill = source.textSpillForTest();
			assertNotNull("spillストアが開かれているはずです(openは注入対象外)", spill);
			source.close();
			assertTrue("close後にspill一時ファイルが残っています", spill.tempFilesDeletedForTest());
		}

		// (b) read失敗(書き込みは成功済み)
		try (LayoutSource source = new LayoutSource(0)) {
			final long id = source.appendChars(0, "endurance".toCharArray(), 0, 9, false);
			final LayoutSource.Chars chars = (LayoutSource.Chars) source.get(id);
			assertTrue("予算0では必ずSpilledになるはずです",
					chars.payload() instanceof LayoutSource.TextPayload.Spilled);
			TextSpillTestHooks.setFaultInjector(null, () -> {
				throw new IOException("injected read failure");
			});
			try {
				chars.payload().freshChars();
				fail("spill読み出し失敗はTextSpillExceptionになるはずです");
			} catch (final TextSpillException e) {
				assertTrue("原因IOExceptionが失われています", e.getCause() instanceof IOException);
			} finally {
				TextSpillTestHooks.clearFaultInjector();
			}
			// 障害解除後は正常に読める(障害はストアを壊さない)
			assertEquals("endurance", new String(chars.payload().freshChars()));
		}
	}

	/**
	 * transcode中のspill書き込み失敗が(1)変換の失敗として伝播し(黙殺
	 * しない)、その根本原因が{@link TextSpillException}であること、
	 * (2)spill一時ファイルが残らないこと、(3)後続変換が正常動作する
	 * ことを固定する。読み出し失敗(改ページ再生時のdecode)も同様。
	 */
	public void testSpillFailureDuringTranscodeFailsCleanlyAndRecovers() throws Exception {
		final File writeDoc = new File("files/unittest/0460-segment-restyle/mid-paragraph.html");
		// 読み出しは改ページ再生でspill済みpayloadを実際にdecodeする文書で
		// 注入する(実測: moved-blocksは予算0で8 record読む。mid-paragraphの
		// 尾部再生は配達済み終端ゲートでdecodeに至らないことがある)
		final File readDoc = new File("files/unittest/0460-segment-restyle/moved-blocks.html");

		// (a) write失敗(3件spillした後の4件目で注入)
		final AtomicInteger appends = new AtomicInteger();
		this.checkTranscodeSpillFailure(writeDoc, "spill-write-fail", () -> {
			if (appends.incrementAndGet() > 3) {
				throw new IOException("injected write failure");
			}
		}, null);

		// (b) read失敗(spill済みpayloadの改ページ再生decodeで注入)
		this.checkTranscodeSpillFailure(readDoc, "spill-read-fail", null, () -> {
			throw new IOException("injected read failure");
		});

		// (c) 後続変換の正常動作(注入なし・同一文書・極小予算——spillの
		// 書き込みとdecode再生の両方が通ることを確認)
		ContinuationStats.reset();
		this.transcode(readDoc, "spill-recovered", "0");
		assertTrue("後続変換でspillが正常動作していません", ContinuationStats.SPILLED_TEXT_RECORDS.get() > 0);
	}

	private void checkTranscodeSpillFailure(final File doc, final String name,
			final TextSpillTestHooks.IOAction beforeAppend, final TextSpillTestHooks.IOAction beforeRead)
			throws Exception {
		ContinuationStats.reset();
		final List<TextSpill> spills = Collections.synchronizedList(new ArrayList<>());
		LayoutSourceTestHooks.setAppendObserver(event -> {
			if (event instanceof LayoutSource.Chars chars
					&& chars.payload() instanceof LayoutSource.TextPayload.Spilled spilled
					&& !spills.contains(spilled.spill())) {
				spills.add(spilled.spill());
			}
		});
		// DirectSessionは予期しない失敗をTranscoderException(FATAL_UNEXPECTED)へ
		// 変換する際にcause連鎖を持たないため、型はSEVEREログのthrownで検証する
		final Throwable[] layoutFailure = new Throwable[1];
		final Logger sessionLog = Logger.getLogger(DirectSession.class.getName());
		final Handler capture = new Handler() {
			@Override
			public void publish(final LogRecord record) {
				if (record.getThrown() != null && layoutFailure[0] == null) {
					layoutFailure[0] = record.getThrown();
				}
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() {
			}
		};
		sessionLog.addHandler(capture);
		TextSpillTestHooks.setFaultInjector(beforeAppend, beforeRead);
		try {
			this.transcode(doc, name, "0");
			fail(name + ": spill障害注入下の変換は失敗するはずです");
		} catch (final TranscoderException expected) {
			// DirectSession.transcodeのcatch ThrowableがFATAL_UNEXPECTEDへ変換
		} finally {
			TextSpillTestHooks.clearFaultInjector();
			LayoutSourceTestHooks.setAppendObserver(null);
			sessionLog.removeHandler(capture);
		}
		assertNotNull(name + ": レイアウト失敗がSEVEREログに現れていません", layoutFailure[0]);
		assertTrue(name + ": 失敗の型がTextSpillExceptionではありません: " + layoutFailure[0].getClass(),
				layoutFailure[0] instanceof TextSpillException);
		// 失敗経路でもspill一時ファイルは残らない(formatterのfinally→
		// LayoutSource.close()の清算)
		assertFalse(name + ": spillストアが観測されていません", spills.isEmpty());
		for (final TextSpill spill : spills) {
			assertTrue(name + ": 失敗後にspill一時ファイルが残っています", spill.tempFilesDeletedForTest());
		}
	}

	// ------------------------------------------------------------------
	// 4. header/footer進捗条件(常時CI)
	// ------------------------------------------------------------------

	/**
	 * 反復ヘッダ(+フッタ)が1ページに収まらない極端fixtureで無限ループに
	 * ならないことを固定する。
	 *
	 * <p>
	 * 既存保護の調査結果(2026-07-24): {@code TableBox.splitPageAxis}は
	 * ヘッダ+フッタ+フレームを差し引いた切断線が正になる場合のみ行分割を
	 * 試み、収まらない場合は{@code TableCutter.keepOrMoveAll}へ縮退する
	 * ——ページ先頭(FLAGS_FIRST)ならKEEP(全体をこのページに置き、
	 * はみ出しを許容=強制進捗)、そうでなければMOVE(次ページでは必ず
	 * ページ先頭になるためKEEPで確定)。よって反復は高々1回で、専用の
	 * 進捗カウンタなしに有限性が構造的に成立している。このテストは
	 * その挙動(完走・有限ページ数)をwatchdog付きで固定する。
	 * </p>
	 */
	public void testRepeatedHeaderTallerThanPageDoesNotLoop() throws Exception {
		// (a) 極端: ヘッダ500pt > ページ400pt(フッタも巨大)
		final File extreme = generateTallHeaderTable("tall-header-extreme", 500, 450, 40);
		final int extremePages = this.transcodeWithWatchdogCountingPages(extreme, "tall-header-extreme", 120_000);
		assertTrue("極端fixtureでページが出力されていません", extremePages > 0);
		// ページ先頭KEEPのはみ出し確定により、ページ数は行数に比例しない
		// 有限小(表全体は分割不能のため1〜2ページ)に留まるはず
		assertTrue("極端fixtureのページ数が異常です(進捗せずヘッダだけ反復した疑い): " + extremePages,
				extremePages <= 5);

		// (b) 対照: ヘッダ300pt+行10ptはページ400ptで毎ページ反復しつつ
		// body行が進捗する(反復ヘッダの正常系が壊れていないことの確認)
		final File tall = generateTallHeaderTable("tall-header-progress", 300, -1, 40);
		final int tallPages = this.transcodeWithWatchdogCountingPages(tall, "tall-header-progress", 120_000);
		assertTrue("反復ヘッダの正常系が複数ページに進捗していません: " + tallPages, tallPages > 1);
		assertTrue("反復ヘッダ行数high-waterが観測されていません",
				TableBuildStats.RETAINED_REPEATED_HEADER_ROW_HIGH_WATER.get() >= 1);
		System.err.println("[E-6 endurance header] extremePages=" + extremePages + " progressPages=" + tallPages);
	}

	// ------------------------------------------------------------------
	// 5. 救済分割の耐久試験(常時CI。2026-07-25、増分8)
	// ------------------------------------------------------------------

	/**
	 * 救済分割(visual rescue split)を極端な規模で連続発火させても、
	 * (1)クラッシュ・無限ループ・停滞がない、(2)ページ数が有限で妥当、
	 * (3)意図しない白紙ページができない、を固定する
	 * ({@link net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner})。
	 *
	 * <p>
	 * fixtureは200×200ptのページに、救済の3経路——浮動体(置換要素)
	 * 20,000pt・書字方向不一致ブロック20,000pt・巨大な行3,000pt——を
	 * <b>同居</b>させたものです。単独ではなく同居させるのは、浮動体の
	 * 排除域が本文側の利用可能量を削り、極小断片ページ(sliver)の下限判定
	 * ({@code MIN_RESCUE_SLICE}/{@code MIN_RESCUE_FRACTION})が実際に
	 * 効く配置を作るためです。
	 * </p>
	 *
	 * <p>
	 * ページ数はexactではなく範囲で固定します。厳密値は段組・排除域の
	 * 相互作用に依存し、goldenとしての価値より脆さが勝るためです。
	 * 「行数に比例して増えない」「1ページも進まない(=停滞)にならない」
	 * という有限性の性質だけを見ます。
	 * </p>
	 */
	public void testRescueSplitEnduranceIsFiniteAndLeavesNoBlankPage() throws Exception {
		final int floatPt = 20_000, orthogonalPt = 20_000, linePt = 3_000;
		final File doc = generateRescueSplitStress("rescue-stress", floatPt, orthogonalPt, linePt);
		RescueStats.reset();
		final List<String> pages = this.transcodeWithWatchdogDumpingPages(doc, "rescue-stress", 300_000);
		final long slices = RescueStats.ENABLED_SLICES.get();
		System.err.println("[rescue endurance] pages=" + pages.size() + " candidates=" + RescueStats.CANDIDATES.get()
				+ " slices=" + RescueStats.SLICES.get() + " enabledSlices=" + slices);

		// (1) 完走した(watchdogがfailしていない)ことは戻り値到達で確定。
		// 救済が実際に発火していること——fixtureが黙って通常経路へ落ちて
		// いたら、この耐久試験は何も試していない
		assertTrue("救済分割が一度も発火していません(fixtureが通常経路へ落ちた疑い)", slices > 0);

		// (2) ページ数が有限で妥当。
		// 下限: いちばん背の高い分割不能要素(20,000pt)だけでも200ptずつ
		// 100断片は要る——これを下回るのは内容が失われている(=停滞して
		// 打ち切った)ということ。
		// 上限: 3要素が1ページも共有しない最悪でも総量43,000pt/200pt=215。
		// 「1ptずつ切って延々とページを作る」なら万単位になる。
		// 実測(2026-07-25): 160ページ、救済断片156個。
		final int tallestPages = Math.max(floatPt, orthogonalPt) / 200;
		final int disjointPages = (floatPt + orthogonalPt + linePt) / 200;
		assertTrue("ページ数が少なすぎます(停滞して内容を捨てた疑い): " + pages.size(), pages.size() >= tallestPages);
		assertTrue("ページ数が過大です(極小断片で切り刻んだ疑い): " + pages.size(), pages.size() <= disjointPages * 2);

		// (3) 意図しない白紙ページがない(全ページに描画命令がある)
		for (int i = 0; i < pages.size(); ++i) {
			assertTrue("ページ" + (i + 1) + "の表示リストが空です(意図しない白紙):\n" + pages.get(i),
					pages.get(i).contains("  x="));
		}
	}

	/**
	 * 救済の3経路を同居させた耐久fixture(ページは200×200pt)。
	 *
	 * @param floatPt      分割できない浮動体(置換要素)の高さ
	 * @param orthogonalPt 書字方向が幹と食い違うブロックの高さ
	 * @param linePt       巨大フォントによる1行の高さ
	 */
	private static File generateRescueSplitStress(final String name, final int floatPt, final int orthogonalPt,
			final int linePt) throws IOException {
		WORK_DIR.mkdirs();
		final File file = new File(WORK_DIR, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"200pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{margin:0;font:normal 10pt/1 serif}p{margin:0}"
					+ "img#f{float:left;width:60pt;height:" + floatPt + "pt}"
					+ "div#o{writing-mode:vertical-rl;width:100pt;height:" + orthogonalPt + "pt;background:#dddddd}"
					+ "p#huge{font:normal " + linePt + "pt/1 serif}</style>\n");
			w.write("</head><body>\n");
			// **絶対URIで書く**(2026-07-27)。以前は WORK_DIR
			// (local/unittest/endurance)からの相対 `../../../` を決め打ち
			// していたが、この形は**フィクスチャを別の場所へ動かすと画像が
			// 黙って消え、別の文書になる**。RandomDocumentFuzzTest の同型の
			// 脆さに1時間費やした——再現を縮小しようとコピーしたら再現せず、
			// 「入力パスで挙動が変わる」という誤った結論に達した。
			w.write("<img src=\"" + RED_PNG_URI + "\" id=\"f\" />\n");
			w.write("<div id=\"o\">O</div>\n");
			w.write("<p id=\"huge\">A</p>\n");
			w.write("<p id=\"after\">after</p>\n");
			w.write("</body></html>\n");
		}
		return file;
	}

	/** watchdog(別daemonスレッド+join timeout)付きtranscode。display listのページ数を返す。 */
	private int transcodeWithWatchdogCountingPages(final File doc, final String name, final long timeoutMs)
			throws Exception {
		return this.transcodeWithWatchdogDumpingPages(doc, name, timeoutMs).size();
	}

	/** watchdog付きtranscode。display listのページごとのダンプを返す。 */
	private List<String> transcodeWithWatchdogDumpingPages(final File doc, final String name, final long timeoutMs)
			throws Exception {
		final File dumpDir = new File(WORK_DIR, name + "-dump");
		deleteRecursively(dumpDir);
		dumpDir.mkdirs();
		System.setProperty(DisplayListDumper.DIR_PROPERTY, dumpDir.getPath());
		final Throwable[] failure = new Throwable[1];
		try {
			final Thread worker = new Thread(() -> {
				try {
					this.transcode(doc, name, null);
				} catch (final Throwable t) {
					failure[0] = t;
				}
			}, "endurance-header-watchdog");
			worker.setDaemon(true);
			worker.start();
			worker.join(timeoutMs);
			if (worker.isAlive()) {
				fail(name + ": " + timeoutMs + "msで完了しません(無限ループの疑い——header/footer進捗保護の重大発見)");
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が失敗しました", failure[0]);
		}
		final File[] files = dumpDir.listFiles((d, n) -> n.endsWith(".txt"));
		if (files == null) {
			return Collections.emptyList();
		}
		java.util.Arrays.sort(files);
		final List<String> pages = new ArrayList<>(files.length);
		for (final File f : files) {
			pages.add(Files.readString(f.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}

	// ------------------------------------------------------------------
	// 2. -Xmx128m制限の別JVM完走(perfゲート)
	// ------------------------------------------------------------------

	/**
	 * {@code -Xmx128m}の別JVMで(a)巨大単一セルauto表、(b)大量行の短セル
	 * auto表をtranscodeし、E-6で達成された保証を実測で特徴付ける
	 * ({@code ./gradlew test --tests "*.EnduranceTest" -Dfoliojet.perf= })。
	 *
	 * <p>
	 * assertするのは「保証」の下限のみ: (a)はE-6経路でセル本文4MB
	 * (payload換算、UTF-16)の完走、(b)はE-6経路で6,000行の完走。それ以上の到達規模と、
	 * kill switch(全legacy: {@code -Dfoliojet.noTwoPassRangeBind=true}+
	 * spill予算実質無効)との対比は実測レポート(stderr)に出力する——
	 * (b)の上限は完成TableBoxの全行box木保持(既知の残存——行単位親
	 * コミットはIncremental統合の将来増分)が支配するため、assertでは
	 * 固定しない。
	 * </p>
	 */
	public void testConstrainedHeapEndurance() throws Exception {
		if (System.getProperty("foliojet.perf") == null) {
			System.out.println("EnduranceTest.testConstrainedHeapEndurance: skipped (-Dfoliojet.perf not set)");
			return;
		}
		WORK_DIR.mkdirs();
		final StringBuilder report = new StringBuilder("[E-6 endurance -Xmx128m]\n");
		try {
			// ---- (a) 巨大単一セルauto表(セル本文payload bytes = chars×2) ----
			// 2026-07-24実測: 4MBはOK・5MBでOOM(上限はspillではなくclose前の
			// capture records——range-first capture(4c)見送りの既知の残存)
			final long[] cellPayloadLadder = { 4L << 20, 5L << 20, 6L << 20, 8L << 20 };
			long maxCellPayload = -1;
			ChildResult firstCell = null;
			for (final long payload : cellPayloadLadder) {
				final File doc = generateBigCellTable("big-cell-" + (payload >> 20) + "m", payload / 2);
				final ChildResult result = this.runChild(doc, "bigcell-new-" + (payload >> 20) + "m", false, null,
						420_000);
				doc.delete();
				report.append("  bigcell new payload=").append(payload >> 20).append("MB -> ").append(result)
						.append('\n');
				if (firstCell == null) {
					firstCell = result;
				}
				if (!result.ok) {
					break;
				}
				maxCellPayload = payload;
			}
			assertNotNull(firstCell);
			assertTrue("E-6経路がセル本文4MB(payload)の単一セルauto表を-Xmx128mで完走できません: " + firstCell,
					firstCell.ok);
			// kill switch(全legacy)の対照実験: E-6経路の完走最大サイズを
			// legacy(range bindなし+spill実質無効)でtranscode
			if (maxCellPayload > 0) {
				final File doc = generateBigCellTable("big-cell-legacy", maxCellPayload / 2);
				final ChildResult legacy = this.runChild(doc, "bigcell-legacy-" + (maxCellPayload >> 20) + "m", true,
						String.valueOf(Long.MAX_VALUE), 420_000);
				doc.delete();
				report.append("  bigcell legacy payload=").append(maxCellPayload >> 20).append("MB -> ").append(legacy)
						.append('\n');
			}

			// ---- (b) 大量行の短セルauto表(降順ladder、最初の完走が到達規模) ----
			// 2026-07-24実測: E-6経路は8,000行OK・9,000行OOM、legacyは7,000行OK・
			// 8,000行OOM(上限は完成TableBoxの全行box木保持が支配——既知の残存)
			final int[] rowsLadder = { 100_000, 50_000, 25_000, 12_000, 9_000, 8_000, 7_000, 6_000 };
			int maxRowsNew = -1;
			for (final int rows : rowsLadder) {
				final File doc = generateManyRowsTable("many-rows-" + rows, rows);
				final ChildResult result = this.runChild(doc, "rows-new-" + rows, false, null, 300_000);
				doc.delete();
				report.append("  rows new rows=").append(rows).append(" -> ").append(result).append('\n');
				if (result.ok) {
					maxRowsNew = rows;
					break;
				}
			}
			assertTrue("E-6経路が6,000行の短セルauto表を-Xmx128mで完走できません", maxRowsNew > 0);
			// legacy対照(同じ降順ladderをE-6経路の到達点から)
			int maxRowsLegacy = -1;
			for (final int rows : rowsLadder) {
				if (rows > maxRowsNew) {
					continue;
				}
				final File doc = generateManyRowsTable("many-rows-legacy-" + rows, rows);
				final ChildResult result = this.runChild(doc, "rows-legacy-" + rows, true,
						String.valueOf(Long.MAX_VALUE), 300_000);
				doc.delete();
				report.append("  rows legacy rows=").append(rows).append(" -> ").append(result).append('\n');
				if (result.ok) {
					maxRowsLegacy = rows;
					break;
				}
			}
			report.append("  SUMMARY maxCellPayloadMB(new)=").append(maxCellPayload > 0 ? (maxCellPayload >> 20) : -1)
					.append(" maxRows(new)=").append(maxRowsNew).append(" maxRows(legacy)=").append(maxRowsLegacy)
					.append('\n');
		} finally {
			System.err.print(report);
			deleteRecursively(new File(WORK_DIR, "pdf"));
		}
	}

	/** 別JVM実行の結果です(okは正常終了=完走)。 */
	private static final class ChildResult {
		final boolean ok;
		final boolean timedOut;
		final int exitCode;
		final long millis;
		final String stats;

		ChildResult(final boolean ok, final boolean timedOut, final int exitCode, final long millis,
				final String stats) {
			this.ok = ok;
			this.timedOut = timedOut;
			this.exitCode = exitCode;
			this.millis = millis;
			this.stats = stats;
		}

		@Override
		public String toString() {
			return (this.ok ? "OK" : this.timedOut ? "TIMEOUT" : "FAIL(exit=" + this.exitCode + ")") + " "
					+ this.millis + "ms" + (this.stats.isEmpty() ? "" : " " + this.stats);
		}
	}

	/**
	 * 現テストJVMのclasspath・設定を引き継いだ別JVM({@code -Xmx128m})で
	 * {@link Child}を実行する。
	 */
	private ChildResult runChild(final File doc, final String name, final boolean legacy, final String budget,
			final long timeoutMs) throws Exception {
		final File pdfDir = new File(WORK_DIR, "pdf");
		pdfDir.mkdirs();
		final File pdf = new File(pdfDir, name + ".pdf");
		final File log = new File(pdfDir, name + ".log");
		final String javaExe = new File(new File(System.getProperty("java.home"), "bin"),
				System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").getPath();
		final List<String> command = new ArrayList<>();
		command.add(javaExe);
		command.add("-Xmx128m");
		command.add("-XX:+ExitOnOutOfMemoryError");
		command.add("-Djava.awt.headless=true");
		command.add("-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"));
		command.add("-Djp.cssj.copper.config=" + System.getProperty("jp.cssj.copper.config"));
		command.add("-Djp.cssj.driver.default=" + System.getProperty("jp.cssj.driver.default"));
		if (legacy) {
			command.add("-Dfoliojet.noTwoPassRangeBind=true");
		}
		command.add("-cp");
		command.add(System.getProperty("java.class.path"));
		command.add(Child.class.getName());
		command.add(doc.getPath());
		command.add(pdf.getPath());
		command.add(budget == null ? "-" : budget);
		final ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(new File(System.getProperty("user.dir")));
		pb.redirectErrorStream(true);
		pb.redirectOutput(log);
		final long t0 = System.nanoTime();
		final Process process = pb.start();
		final boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
		if (!finished) {
			process.destroyForcibly();
			process.waitFor(30, TimeUnit.SECONDS);
		}
		final long millis = (System.nanoTime() - t0) / 1_000_000;
		String stats = "";
		if (log.exists()) {
			for (final String line : Files.readAllLines(log.toPath(), StandardCharsets.UTF_8)) {
				if (line.startsWith("ENDURANCE-OK ")) {
					stats = line.substring("ENDURANCE-OK ".length());
				} else if (line.contains("OutOfMemoryError")) {
					stats = "OutOfMemoryError";
				}
			}
		}
		final int exit = finished ? process.exitValue() : -1;
		pdf.delete();
		return new ChildResult(finished && exit == 0, !finished, exit, millis, stats);
	}

	/**
	 * 別JVMのエントリポイントです(引数: 文書パス、PDF出力パス、spill予算
	 * ({@code -}なら既定8MB))。完走時は{@code ENDURANCE-OK}+主要カウンタを
	 * stdoutへ出力してexit 0、失敗時は非0。
	 */
	public static final class Child {
		public static void main(final String[] args) {
			try {
				final File doc = new File(args[0]);
				final File pdf = new File(args[1]);
				final String budget = args[2];
				try (OutputStream out = new FileOutputStream(pdf)) {
					final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
					try {
						session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
						session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
						session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
						session.property("input.include", "**");
						session.property("input.property-pi", "true");
						if (!"-".equals(budget)) {
							session.property("processing.text-spill-budget", budget);
						}
						CTISessionHelper.transcodeFile(session, doc, "text/html", null);
					} finally {
						session.close();
					}
				}
				System.out.println("ENDURANCE-OK maxMemMB=" + (Runtime.getRuntime().maxMemory() >> 20)
						+ " spilledBytes=" + ContinuationStats.SPILLED_TEXT_BYTES.get() + " spilledRecords="
						+ ContinuationStats.SPILLED_TEXT_RECORDS.get() + " livePayloadHW="
						+ ContinuationStats.LIVE_TEXT_PAYLOAD_BYTES.get() + " sourceEventHW="
						+ ContinuationStats.SOURCE_EVENT_HIGH_WATER.get() + " retainedRowHW="
						+ TableBuildStats.RETAINED_ROW_HIGH_WATER.get() + " twoPassRecordHW="
						+ TableBuildStats.TWO_PASS_RECORD_HIGH_WATER.get() + " twoPassGlyphHW="
						+ TableBuildStats.TWO_PASS_GLYPH_HIGH_WATER.get() + " cellRangeSeals="
						+ ContinuationStats.CELL_RANGE_SEALS.get() + " cellLegacyBinds="
						+ ContinuationStats.CELL_LEGACY_BINDS.get() + " passCTables="
						+ ContinuationStats.TABLE_PASS_C_TABLES.get() + " legacyBindRows="
						+ ContinuationStats.TABLE_LEGACY_BINDROWS.get());
				System.exit(0);
			} catch (final Throwable t) {
				t.printStackTrace();
				System.exit(3);
			}
		}
	}

	// ------------------------------------------------------------------
	// fixture生成・共通transcode
	// ------------------------------------------------------------------

	private static final String[] SENTENCES = { //
			"The quick brown fox jumps over the lazy dog while the sun sets slowly behind distant mountains. ", //
			"Typography is the art and technique of arranging type to make written language legible and readable. ", //
			"Paragraph composition with total-fit line breaking minimizes the sum of squared badness over lines. ", //
			"Endurance testing characterizes the achieved guarantees of the spillable tape infrastructure. ", //
	};

	/** 同一CSSの散文文書(A4相当ページ、段落数のみ可変)。 */
	private static File generateProse(final String name, final int paragraphs) throws IOException {
		WORK_DIR.mkdirs();
		final File file = new File(WORK_DIR, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"595pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"842pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:24pt}body{font:normal 8pt/1.3 serif}p{margin:0 0 4pt 0}</style>\n");
			w.write("</head><body>\n");
			for (int i = 0; i < paragraphs; ++i) {
				w.write("<p>");
				for (int j = 0; j < 4; ++j) {
					w.write(SENTENCES[(i + j) % SENTENCES.length]);
				}
				w.write("</p>\n");
			}
			w.write("</body></html>\n");
		}
		return file;
	}

	/** 単一セルのauto表(セル本文はおよそ{@code totalChars}文字の段落列)。 */
	private static File generateBigCellTable(final String name, final long totalChars) throws IOException {
		WORK_DIR.mkdirs();
		final File file = new File(WORK_DIR, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"595pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"842pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:24pt}body{font:normal 8pt/1.3 serif}td{border:1pt solid black}"
					+ "p{margin:0 0 4pt 0}</style>\n");
			w.write("</head><body><table><tbody><tr><td>\n");
			long written = 0;
			int i = 0;
			while (written < totalChars) {
				w.write("<p>");
				for (int j = 0; j < 4; ++j) {
					final String s = SENTENCES[(i + j) % SENTENCES.length];
					w.write(s);
					written += s.length();
				}
				w.write("</p>\n");
				++i;
			}
			w.write("</td></tr></tbody></table></body></html>\n");
		}
		return file;
	}

	/** 短セル({@code r{i}c{j}})×3列のauto表(thead 1行つき)。 */
	private static File generateManyRowsTable(final String name, final int rows) throws IOException {
		WORK_DIR.mkdirs();
		final File file = new File(WORK_DIR, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"595pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"842pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:24pt}body{font:normal 8pt/1 serif}td,th{border:1pt solid black}</style>\n");
			w.write("</head><body><table>\n");
			w.write("<thead><tr><th>h0</th><th>h1</th><th>h2</th></tr></thead>\n");
			w.write("<tbody>\n");
			for (int i = 0; i < rows; ++i) {
				w.write("<tr><td>r" + i + "c0</td><td>r" + i + "c1</td><td>r" + i + "c2</td></tr>\n");
			}
			w.write("</tbody></table></body></html>\n");
		}
		return file;
	}

	/**
	 * 反復ヘッダ(と任意でフッタ)の行が{@code headerPt}の高さを持つauto表
	 * (ページは400×400pt)。{@code footerPt}が負ならフッタなし。
	 */
	private static File generateTallHeaderTable(final String name, final int headerPt, final int footerPt,
			final int bodyRows) throws IOException {
		WORK_DIR.mkdirs();
		final File file = new File(WORK_DIR, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"400pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif}td,th{border:1pt solid black}</style>\n");
			w.write("</head><body><table>\n");
			w.write("<thead><tr><th><div style=\"height:" + headerPt + "pt\">H</div></th></tr></thead>\n");
			if (footerPt >= 0) {
				w.write("<tfoot><tr><td><div style=\"height:" + footerPt + "pt\">F</div></td></tr></tfoot>\n");
			}
			w.write("<tbody>\n");
			for (int i = 0; i < bodyRows; ++i) {
				w.write("<tr><td>row" + i + "</td></tr>\n");
			}
			w.write("</tbody></table></body></html>\n");
		}
		return file;
	}

	private void transcode(final File source, final String name, final String budget) throws Exception {
		final File pdf = new File(WORK_DIR, name + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				if (budget != null) {
					session.property("processing.text-spill-budget", budget);
				}
				CTISessionHelper.transcodeFile(session, source, "text/html", null);
			} finally {
				session.close();
			}
		}
	}

	private static void deleteRecursively(final File file) {
		final File[] children = file.listFiles();
		if (children != null) {
			for (final File child : children) {
				deleteRecursively(child);
			}
		}
		file.delete();
	}
}
