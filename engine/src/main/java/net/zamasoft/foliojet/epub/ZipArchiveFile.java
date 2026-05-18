package net.zamasoft.foliojet.epub;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipArchiveFile implements ArchiveFile {
	private final File file;
	private final ZipFile zip;

	public ZipArchiveFile(File file) throws IOException {
		this.file = file;
		this.zip = new ZipFile(file);
	}

	public ZipArchiveFile(File file, ZipFile zip) {
		this.file = file;
		this.zip = zip;
	}

	public boolean exists(String path) {
		try {
			return this.zip.getEntry(path) != null;
		} catch (IllegalStateException e) {
			try {
				try (ZipFile zip = new ZipFile(this.file)) {
					return zip.getEntry(path) != null;
				}
			} catch (Exception e1) {
				return false;
			}
		}
	}

	public InputStream getInputStream(String path) throws FileNotFoundException, IOException {
		path = URLDecoder.decode(URI.create(URLEncoder.encode(path, "UTF-8")).normalize().toString(), "UTF-8");
		try {
			ZipEntry entry = this.zip.getEntry(path);
			if (entry == null) {
				throw new FileNotFoundException(path);
			}
			return this.zip.getInputStream(entry);
		} catch (IllegalStateException e) {
			final ZipFile zip = new ZipFile(this.file);
			ZipEntry entry = zip.getEntry(path);
			if (entry == null) {
				throw new FileNotFoundException(path);
			}
			return new FilterInputStream(zip.getInputStream(entry)) {
				public void close() throws IOException {
					super.close();
					zip.close();
				}
			};
		}
	}

	public void close() throws IOException {
		this.zip.close();
	}
}
