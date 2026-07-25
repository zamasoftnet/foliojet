package net.zamasoft.foliojet.layout.fragment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 改ページ・改段の再開操作をテキストとしてダンプします(回帰検証用)。
 *
 * <p>
 * Continuation 移行(ARCHITECTURE §5.7)の各スライスは「再開の操作列が
 * 変わらない(または意図的に変わる)」ことを機械検証する必要があります。
 * 本トレースは restyle 走行(箱の再演)とソース再駆動の分岐・順序を
 * 記録し、golden 比較(ResumeTraceGoldenTest)で意味保存を固定します。
 * </p>
 *
 * <p>
 * システムプロパティ {@value #DIR_PROPERTY} に出力先ディレクトリを
 * 設定すると有効になります。無効時のコストはプロパティ参照1回です。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ResumeTrace {
	private static final Logger LOG = Logger.getLogger(ResumeTrace.class.getName());

	public static final String DIR_PROPERTY = "net.zamasoft.foliojet.debug.resume-trace.dir";

	/**
	 * 再開トレースのバッファスタックです。再生した内容が新ページを
	 * 溢れさせると再開の中で破断が入れ子で起きるため、単一バッファでは
	 * 内側の begin が外側の記録を上書きする(外部レビュー指摘)。
	 * 入れ子の破断は「nested resume」行として外側にも痕跡を残し、
	 * 自身は完了時に独立ファイルとして書き出される(完了順の連番)。
	 */
	private static final ThreadLocal<java.util.ArrayDeque<StringBuilder>> buffers = ThreadLocal
			.withInitial(java.util.ArrayDeque::new);

	private static final java.util.concurrent.atomic.AtomicInteger breakCount = new java.util.concurrent.atomic.AtomicInteger();

	private ResumeTrace() {
		// utility
	}

	private static boolean enabled() {
		return System.getProperty(DIR_PROPERTY) != null;
	}

	/**
	 * 破断(改ページ・改段)の再開の開始を記録します。
	 *
	 * @param kind PAGE / COLUMN
	 */
	public static void begin(final String kind) {
		if (!enabled()) {
			return;
		}
		final StringBuilder outer = buffers.get().peek();
		if (outer != null) {
			outer.append("  nested resume ").append(kind).append('\n');
		}
		final StringBuilder buffer = new StringBuilder();
		buffer.append("resume ").append(kind).append('\n');
		buffers.get().push(buffer);
	}

	/**
	 * 再開中の操作を記録します。呼び出し側は enabled 判定を気にせず
	 * 呼んでよい(無効時は無視)。
	 *
	 * @param depth 祖先チェーン上の深さ(不明は -1)
	 * @param op    操作名(replay-subtree / restyle-box / text-tail 等)
	 * @param what  対象の要約(ボックス種別・serial 等)
	 */
	public static void op(final int depth, final String op, final String what) {
		final StringBuilder sb = buffers.get().peek();
		if (sb == null) {
			return;
		}
		sb.append("  ");
		for (int i = 0; i < Math.max(0, depth); ++i) {
			sb.append(' ');
		}
		sb.append(op).append(' ').append(what).append('\n');
	}

	/**
	 * 再開の終了を記録し、有効ならファイルへ書き出します。
	 */
	public static void end() {
		final StringBuilder sb = buffers.get().poll();
		if (sb == null) {
			return;
		}
		final String dir = System.getProperty(DIR_PROPERTY);
		if (dir == null) {
			return;
		}
		try {
			final File d = new File(dir);
			d.mkdirs();
			Files.writeString(
					new File(d, String.format(Locale.ROOT, "break-%04d.txt", breakCount.incrementAndGet())).toPath(),
					sb.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.log(Level.WARNING, "再開トレースをダンプできませんでした", e);
		}
	}

	/**
	 * テスト用: 連番をリセットします。
	 */
	public static void reset() {
		breakCount.set(0);
		buffers.get().clear();
	}
}
