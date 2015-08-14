package ru.ok.android.sdk.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class OkIOUtil {
	
	public static void closeSilently(final Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (Exception exc) {
				// Do nothing
			}
		}
	}
	
	public static final String inputStreamToString(final InputStream is) throws IOException {
		final char[] buffer = new char[0x10000];
		final StringBuilder sb = new StringBuilder();
		final Reader in = new InputStreamReader(is, "utf-8");
		try {
			int read;
			do {
				read = in.read(buffer, 0, buffer.length);
				if (read > 0) {
					sb.append(buffer, 0, read);
				}
			} while (read >= 0);
		} finally {
			closeSilently(in);
		}
		return sb.toString();
	}

}
