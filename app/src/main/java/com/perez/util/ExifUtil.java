package com.perez.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.ImageWriteException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.IImageMetadata;
import org.apache.sanselan.formats.jpeg.exifRewrite.ExifRewriter;

public class ExifUtil {

	private static final String TEMP_DIR = "temp";
	private static final String FILE_SEPARATOR = System.getProperty("file.separator");

	public static String showExif(String pth) throws IOException, ImageReadException {
		File file = new File(pth);
		return getExifData(file);
	}

	public static void deleteEXIF(String pth, String pth0) throws IOException,
			ImageReadException, ImageWriteException {
		File source = new File(pth);
		// If no destination folder given, assign source folder to dest
		File dest = new File(pth0);

		if (source.isDirectory()) {
			deleteEXIFFromDir(source, dest);
		} else {
			deleteEXIFForSingleFile(source, dest);
		}
	}

	/**
	 * Removes EXIF data from all files in a given source directory and saves
	 * new images into the destination directory. Destination directory is
	 * created if it doesn't exist.
	 *
	 * @param source source folder that contains the image files
	 * @param dest   destination folder where the new images are saved to
	 */
	private static boolean deleteEXIFFromDir(File source, File dest) {
		assert source.isDirectory();
		if (!dest.isDirectory()) {
			if (dest.exists()) {
				System.err.println("destination file is no directory");
				return false;
			} else {
				dest.mkdir();
			}
		}
		// Create temp folder if destination folder equals source folder
		if (dest.equals(source)) {
			return removeAllEXIFFromFolder(source, new File(dest.getAbsolutePath()
					+ FILE_SEPARATOR + TEMP_DIR));
		} else {
			return removeAllEXIFFromFolder(source, dest);
		}
	}

	/**
	 * Removes EXIF data from a single image file which is the source file.
	 * Resulting images is saved into the destination file which may be the same
	 * as the source file (in that case the image is overwritten).
	 *
	 * @param source source folder that contains the image files
	 * @param dest   destination folder where the new images are saved to
	 * @throws IOException
	 * @throws ImageReadException
	 * @throws ImageWriteException
	 */
	private static void deleteEXIFForSingleFile(File source, File dest)
			throws IOException, ImageReadException, ImageWriteException {
		removeEXIFData(source, dest);
	}

	private static boolean removeAllEXIFFromFolder(File srcFolder, File destFolder) {
		assert srcFolder.isDirectory() && destFolder.isDirectory();
		File[] files = srcFolder.listFiles();
		if (files == null) {
			return false;
		}

		if (!destFolder.exists()) {
			destFolder.mkdir();
		}
		for (File file : files) {
			if (file.isFile() && (file.getName().endsWith(".jpg") || file.getName().endsWith(".jpeg"))) {
				try {
					File destFile = new File(destFolder.getAbsolutePath()
							+ FILE_SEPARATOR + file.getName());
					removeEXIFData(file, destFile);
				} catch (IOException e) {
					System.err.println("Can not convert or read file "
							+ file.getAbsolutePath());
					return false;
				} catch (ImageReadException e) {
					System.err.println("Unable to remove EXIF data from "
							+ file.getAbsolutePath());
					return false;
				} catch (ImageWriteException e) {
					System.err.println("Unable to remove EXIF data from "
							+ file.getAbsolutePath());
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Reads EXIF metadata from the target image file.
	 *
	 * @param image source image file
	 * @return string representation of EXIF metadata, or fallback string if absent
	 * @throws ImageReadException
	 * @throws IOException
	 */
	private static String getExifData(File image) throws ImageReadException, IOException {
		byte[] imageBytes = FileUtil.readAllFileBytes(image);
		IImageMetadata metadata = Sanselan.getMetadata(imageBytes);
		if (metadata != null) {
			return metadata.toString();
		}
		return "no metadata found";
	}

	/**
	 * Strips EXIF metadata from source image and writes result to destination file.
	 *
	 * @param source source image file
	 * @param dest   target destination file
	 * @throws ImageReadException
	 * @throws ImageWriteException
	 * @throws IOException
	 */
	private static void removeEXIFData(File source, File dest)
			throws ImageReadException, ImageWriteException, IOException {
		byte[] imageBytes = FileUtil.readAllFileBytes(source);
		OutputStream os = null;
		try {
			os = new FileOutputStream(dest);
			ExifRewriter exifWriter = new ExifRewriter();
			exifWriter.removeExifMetadata(imageBytes, os);
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}