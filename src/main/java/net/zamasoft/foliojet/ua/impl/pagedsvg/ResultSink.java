package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.results.Results;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.SequentialOutput;
import net.zamasoft.zstream.io.util.FragmentOutputAdapter;
import net.zamasoft.zstream.io.util.SequentialOutputAdapter;
import net.zamasoft.zstream.resolver.util.SimpleSourceMetadata;

/**
 * Paged SVGの結果1件を「開いて、書いて、閉じる」だけの受け口です(2026-09-02)。
 *
 * <p>
 * 結果の行き先はふつうの結果集合({@link ResultsSink})、1本のZIP
 * ({@link ZipSink})、中間パスの捨て場({@link NopSink})、EPUBの項目の
 * 解放段({@code DocumentRelease.Unit})の4つある。書き手(UA)はハッシュと
 * gzipを自分でやるので、行き先はストリームを1本返せばよい。
 * </p>
 */
interface ResultSink {
	/**
	 * 結果1件を開きます。返ったストリームを閉じると1件が確定します。
	 *
	 * @throws AbortException これ以上結果を受け取れないとき({@code ABORT_NORMAL})
	 */
	OutputStream open(String uri, String mimeType) throws IOException;

	/** 全結果の終わり。 */
	void end() throws IOException;

	/** 結果集合へ1件ずつ出す、ふつうの受け口。 */
	final class ResultsSink implements ResultSink {
		private final Results results;

		ResultsSink(final Results results) {
			this.results = results;
		}

		@Override
		public OutputStream open(final String uri, final String mimeType) throws IOException {
			if (!this.results.hasNext()) {
				throw new AbortException(CTISession.ABORT_NORMAL);
			}
			final var metadata = new SimpleSourceMetadata(URI.create(uri), mimeType, null, -1);
			final FragmentedOutput builder = this.results.nextBuilder(metadata);
			final OutputStream raw;
			if (builder instanceof SequentialOutput sequential) {
				raw = new SequentialOutputAdapter(sequential);
			} else {
				builder.addFragment();
				raw = new FragmentOutputAdapter(builder, 0);
			}
			return new FilterOutputStream(raw) {
				@Override
				public void write(final byte[] b, final int off, final int len) throws IOException {
					this.out.write(b, off, len);
				}

				@Override
				public void close() throws IOException {
					try {
						this.out.close();
					} finally {
						builder.close();
					}
				}
			};
		}

		@Override
		public void end() throws IOException {
			this.results.end();
		}
	}

	/**
	 * 1本のZIPにまとめて返す受け口(B-2、2026-08-29)。
	 *
	 * <p>
	 * 名前はふつうのバンドルと同じURI({@code pages/0001.svg}、
	 * {@code assets/fonts/font-0001.woff2}…)。展開すればディレクトリ出力と
	 * 同じ形になり、{@code manifest.json}の参照もそのまま解決する。
	 * SHA-256は書き手が<b>エントリの中身</b>(圧縮前)に対して取るので、
	 * 受け手は展開したファイルへそのまま当てられる。
	 * </p>
	 */
	final class ZipSink implements ResultSink {
		/** ZIPで返すときの結果URIとメディア型。 */
		static final String BUNDLE_URI = "paged-svg.zip";
		static final String BUNDLE_MEDIA_TYPE = "application/zip";

		private final Results results;
		private ZipOutputStream zip;
		private FragmentedOutput builder;

		ZipSink(final Results results) {
			this.results = results;
		}

		/** ZIPの結果を必要になった時点で1件だけ開きます。 */
		private ZipOutputStream requireZip() throws IOException {
			if (this.zip != null) {
				return this.zip;
			}
			if (!this.results.hasNext()) {
				throw new AbortException(CTISession.ABORT_NORMAL);
			}
			final var metadata = new SimpleSourceMetadata(URI.create(BUNDLE_URI), BUNDLE_MEDIA_TYPE, null, -1);
			this.builder = this.results.nextBuilder(metadata);
			final OutputStream raw;
			if (this.builder instanceof SequentialOutput sequential) {
				raw = new SequentialOutputAdapter(sequential);
			} else {
				this.builder.addFragment();
				raw = new FragmentOutputAdapter(this.builder, 0);
			}
			this.zip = new ZipOutputStream(raw);
			return this.zip;
		}

		@Override
		public OutputStream open(final String uri, final String mimeType) throws IOException {
			final ZipOutputStream zipOut = this.requireZip();
			zipOut.putNextEntry(new ZipEntry(uri));
			return new FilterOutputStream(zipOut) {
				@Override
				public void write(final byte[] b, final int off, final int len) throws IOException {
					this.out.write(b, off, len);
				}

				@Override
				public void close() throws IOException {
					zipOut.closeEntry();
				}
			};
		}

		@Override
		public void end() throws IOException {
			try {
				if (this.zip != null) {
					this.zip.finish();
					this.zip.close();
				}
			} finally {
				this.zip = null;
				if (this.builder != null) {
					this.builder.close();
					this.builder = null;
				}
			}
			this.results.end();
		}
	}

	/** 中間パスの捨て場。何も残さない。 */
	final class NopSink implements ResultSink {
		static final NopSink INSTANCE = new NopSink();

		@Override
		public OutputStream open(final String uri, final String mimeType) {
			return OutputStream.nullOutputStream();
		}

		@Override
		public void end() {
			// 何もしない
		}
	}
}
