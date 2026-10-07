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
 * A sink that only opens, writes, and closes one Paged SVG result (2026-09-02).
 *
 * <p>
 * There are four result destinations: a normal result set ({@link ResultsSink}),
 * one ZIP ({@link ZipSink}), a discard sink for intermediate passes ({@link NopSink}),
 * and the EPUB item release stage ({@code DocumentRelease.Unit}).
 * The writer (UA) handles hashing and gzip itself, so the destination only needs to return one stream.
 * </p>
 */
interface ResultSink {
	/**
	 * Opens one result. Closing the returned stream finalizes the result.
	 *
	 * @throws AbortException if no more results can be accepted ({@code ABORT_NORMAL})
	 */
	OutputStream open(String uri, String mimeType) throws IOException;

	/** End of all results. */
	void end() throws IOException;

	/** The normal sink, emitting one result at a time to a result set. */
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
	 * A sink returning everything in one ZIP (B-2, 2026-08-29).
	 *
	 * <p>
	 * Names use the same URIs as normal bundles ({@code pages/0001.svg},
	 * {@code assets/fonts/font-0001.woff2}, ...). Extraction reproduces the directory output
	 * structure, and {@code manifest.json} references resolve unchanged.
	 * The writer computes SHA-256 over <b>entry contents</b> (before compression),
	 * so the consumer can check it directly against extracted files.
	 * </p>
	 */
	final class ZipSink implements ResultSink {
		/** Result URI and media type when returning a ZIP. */
		static final String BUNDLE_URI = "paged-svg.zip";
		static final String BUNDLE_MEDIA_TYPE = "application/zip";

		private final Results results;
		private ZipOutputStream zip;
		private FragmentedOutput builder;

		ZipSink(final Results results) {
			this.results = results;
		}

		/** Opens exactly one ZIP result when needed. */
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

	/** Discard sink for intermediate passes. Retains nothing. */
	final class NopSink implements ResultSink {
		static final NopSink INSTANCE = new NopSink();

		@Override
		public OutputStream open(final String uri, final String mimeType) {
			return OutputStream.nullOutputStream();
		}

		@Override
		public void end() {
			// Do nothing.
		}
	}
}
