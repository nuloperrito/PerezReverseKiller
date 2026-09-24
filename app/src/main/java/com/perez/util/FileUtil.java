package com.perez.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.FileHelper;
import android.os.FileUtils;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;

/**
 * File I/O utilities.
 */
public final class FileUtil {

	public static final String TAG = "FileUtil";

	private static final int BUFFER_SIZE = 1024 * 8;

	/**
	 * This class is uninstantiable.
	 */
	private FileUtil() {
		// This space intentionally left blank.
	}

	/**
	 * Reads the named file, translating {@link IOException} to a
	 * {@link RuntimeException} of some sort.
	 *
	 * @param fileName
	 *            non-null; name of the file to read
	 * @return non-null; contents of the file
	 */
	public static byte[] readFile(String fileName) throws IOException {
		File file = new File(fileName);
		return readFile(file);
	}

	/**
	 * Reads the given file, translating {@link IOException} to a
	 * {@link RuntimeException} of some sort.
	 *
	 * @param file
	 *            non-null; the file to read
	 * @return non-null; contents of the file
	 */
	public static byte[] readFile(File file) throws IOException {
		return readFile(file, 0, -1);
	}

	/**
	 * Reads the specified block from the given file, translating
	 * {@link IOException} to a {@link RuntimeException} of some sort.
	 *
	 * @param file
	 *            non-null; the file to read
	 * @param offset
	 *            the offset to begin reading
	 * @param length
	 *            the number of bytes to read, or -1 to read to the end of the
	 *            file
	 * @return non-null; contents of the file
	 */
	public static byte[] readFile(File file, int offset, int length)
	throws IOException {
		if(!file.exists())
			throw new RuntimeException(file + ": file not found");
		if(!file.isFile())
			throw new RuntimeException(file + ": not a file");
		if(!file.canRead())
			throw new RuntimeException(file + ": file not readable");
		long longLength = file.length();
		int fileLength = (int) longLength;
		if(fileLength != longLength)
			throw new RuntimeException(file + ": file too long");
		if(length == -1)
			length = fileLength - offset;
		if(offset + length > fileLength)
			throw new RuntimeException(file + ": file too short");
		FileInputStream in = new FileInputStream(file);
		int at = offset;
		while(at > 0) {
			long amt = in.skip(at);
			if(amt == -1)
				throw new RuntimeException(file + ": unexpected EOF");
			at -= amt;
		}
		byte[] result = readStream(in, length);
		in.close();
		return result;
	}

	public static byte[] readStream(InputStream in, int length) throws IOException {
		byte[] result = new byte[length];
		int at = 0;
		while(length > 0) {
			int amt = in.read(result, at, length);
			if(amt == -1)
				throw new RuntimeException("unexpected EOF");
			at += amt;
			length -= amt;
		}
		return result;
	}

	public static void copyFile(File source, File destination) throws Exception {
		byte[] buf = new byte[1024];
		InputStream input = new BufferedInputStream(new FileInputStream(source));
		OutputStream output = new BufferedOutputStream(new FileOutputStream(destination));
		int len;
		while((len = input.read(buf)) > 0)
			output.write(buf, 0, len);
		output.flush();
		output.close();
		input.close();
		int perms = getPermissions(source) & 0777;
		chmod(destination, perms);
		destination.setLastModified(source.lastModified());
	}


	public static void chmod(File path, int mode) throws ErrnoException {
		Os.chmod(path.getAbsolutePath(), mode);
		// Os.chmod(path.getAbsolutePath(), -1, -1)
	}

	public static int copy(InputStream input, OutputStream output) throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];
		BufferedInputStream in = new BufferedInputStream(input, BUFFER_SIZE);
		BufferedOutputStream out = new BufferedOutputStream(output, BUFFER_SIZE);
		int count = 0, n;
		try {
			while((n = in.read(buffer, 0, BUFFER_SIZE)) != -1) {
				out.write(buffer, 0, n);
				count += n;
			}
			out.flush();
		} finally {
			try {
				out.close();
				in.close();
			} catch(IOException e) {
				Log.e("IOUtils", e.getMessage());
			}
		}
		return count;
	}

	public static int getPermissions(File path) {
		int[] result = new int[1];
		FileHelper.getPermissions(path.getAbsolutePath(), result);
		return result[0];
	}

	public static boolean recursiveChmod(File root, int mode) {
		boolean success = true;
		try {
			chmod(root, mode);
		} catch (ErrnoException e) {
			success = false;
		}
		for(File path : root.listFiles()) {
			if(path.isDirectory())
				success = recursiveChmod(path, mode);
			try {
				chmod(path, mode);
			} catch (ErrnoException e) {
				success = false;
			}
		}
		return success;
	}

	public static boolean delete(File path) {
		boolean result = true;
		if(path.exists()) {
			if(path.isDirectory()) {
				for(File child : path.listFiles())
					result &= delete(child);
				result &= path.delete(); // Delete empty directory.
			}
			if(path.isFile())
				result &= path.delete();
			if(!result)
				Log.e(TAG, "Delete failed;");
			return result;
		} else {
			Log.e(TAG, "File does not exist.");
			return false;
		}
	}

	public static boolean makeDirectories(File directory, int mode) {
		File parent = directory;
		while(parent.getParentFile() != null && !parent.exists())
			parent = parent.getParentFile();
		if(!directory.exists()) {
			Log.v(TAG, "Creating directory: " + directory.getName());
			if(!directory.mkdirs()) {
				Log.e(TAG, "Failed to create directory.");
				return false;
			}
		}
		return recursiveChmod(parent, mode);
	}

	public static boolean rename(File file, String name) {
		return file.renameTo(new File(file.getParent(), name));
	}

	public static String readFromAssetsFile(Context context, String name)
			throws IOException {
		AssetManager am = context.getAssets();
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				am.open(name)));
		String line;
		StringBuilder builder = new StringBuilder();
		while((line = reader.readLine()) != null)
			builder.append(line);
		reader.close();
		return builder.toString();
	}

	public static void convertToJpg(String pngFilePath, String jpgFilePath) {
		Bitmap bitmap = BitmapFactory.decodeFile(pngFilePath);
		try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(jpgFilePath))) {
			if (bitmap.compress(Bitmap.CompressFormat.JPEG, 95, bos)) {
				bos.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void convertToPng(String jpgFilePath, String pngFilePath) {
		Bitmap bitmap = BitmapFactory.decodeFile(jpgFilePath);
		try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(pngFilePath))) {
			if (bitmap.compress(Bitmap.CompressFormat.PNG, 90, bos)) {
				bos.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
