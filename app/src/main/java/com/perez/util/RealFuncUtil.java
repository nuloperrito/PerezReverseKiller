package com.perez.util;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.MediaDrm;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;

import com.googlecode.d2j.dex.Dex2jar;
import com.perez.revkiller.Features;
import com.perez.revkiller.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class RealFuncUtil {

    private static boolean reuseReg = true;
    private static boolean debugInfo = false;
    private static boolean printIR = false;
    private static boolean optmizeSynchronized = true;
    private static boolean skipExceptions = true;
    private static boolean noCode = false;

    public static String joinStr(Collection<String> collection, String delimiter) {
        StringBuffer buffer = new StringBuffer();
        Iterator<String> iter = collection.iterator();
        while(iter.hasNext()) {
            buffer.append(iter.next());
            if(iter.hasNext())
                buffer.append(delimiter);
        }
        return buffer.toString();
    }

    public static boolean isStandardJAR(String zip) {
        boolean value = false;
        try {
            ZipFile zipFile = new ZipFile(zip);
            Enumeration<ZipEntry> enu = (Enumeration<ZipEntry>) zipFile.entries();
            while (enu.hasMoreElements()) {
                ZipEntry zipElement = enu.nextElement();
                zipFile.getInputStream(zipElement);
                String fileName = zipElement.getName();
                if (fileName.endsWith(".class")) {
                    value = true;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    public static void installProcess(File apk, Activity act) {
        boolean haveInstallPermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            haveInstallPermission = act.getPackageManager().canRequestPackageInstalls();
            if (!haveInstallPermission) {
                Log.d("PerezReverseKiller", "Did not have installing permissions");
                AlertDialog.Builder builder = new AlertDialog.Builder(act);
                builder.setTitle(act.getString(R.string.prompt));
                builder.setMessage(R.string.need_perm_tips);
                builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startInstallPermissionSettingActivity(act);
                    }
                });
                builder.show();
                return;
            }
        }

        installApk(act, apk);
    }

    public static String extractFileNameFromUri(Context ctx, Uri uri) {
        if (uri != null) {
            String scheme = uri.getScheme();

            // Content URI: Query MediaStore/DocumentProvider
            if ("content".equalsIgnoreCase(scheme)) {
                Cursor cursor = null;
                try {
                    cursor = ctx.getContentResolver().query(
                            uri,
                            new String[]{MediaStore.MediaColumns.DISPLAY_NAME},
                            null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            String name = cursor.getString(nameIndex);
                            if (!TextUtils.isEmpty(name)) {
                                return name;
                            }
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    if (cursor != null) cursor.close();
                }
            }

            // File URI or raw path
            String path = uri.getPath();
            if (TextUtils.isEmpty(path)) {
                path = uri.toString();
            }

            if (!TextUtils.isEmpty(path)) {
                try {
                    String decodedPath = Uri.decode(path);
                    File file = new File(decodedPath);
                    String name = file.getName();
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                } catch (Exception ignored) {
                }
            }

            // Fallback
            String lastSegment = uri.getLastPathSegment();
            if (!TextUtils.isEmpty(lastSegment)) {
                return Uri.decode(lastSegment);
            }
        }

        return "Unknown File";
    }

    public static boolean hasPermissions(Context ctx, String[] perms) {
        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static void DexTrans(File in, File out) throws IOException {
        byte[] dexContents = FileUtil.readAllFileBytes(new File(in.getAbsolutePath()));
        Dex2jar.from(dexContents)
                .reUseReg(reuseReg)
                .topoLogicalSort()
                .skipDebug(!debugInfo)
                .optimizeSynchronized(optmizeSynchronized)
                .printIR(printIR)
                .noCode(noCode)
                .skipExceptions(skipExceptions)
                .to(out);
    }

    public static String readCodeFromFile(Context ctx, String filePath) {
        File file = new File(filePath);
        int fileLength = (int) file.length();

        // Use file length if valid, otherwise fallback to default buffer size
        int capacity = fileLength > 0 ? fileLength : 8192;
        byte[] buffer = new byte[capacity];

        try (FileInputStream fis = new FileInputStream(file)) {
            int totalBytesRead = 0;
            int bytesRead;
            while (totalBytesRead < buffer.length && (bytesRead = fis.read(buffer, totalBytesRead, buffer.length - totalBytesRead)) != -1) {
                totalBytesRead += bytesRead;
            }
            return new String(buffer, 0, totalBytesRead, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Toast.makeText(ctx, "Error accessing or reading file " + filePath, Toast.LENGTH_LONG).show();
            return "";
        }
    }

    public static boolean writeCodeToFile(Context ctx, String textOut, String filePath) {
        File sourceFile = new File(filePath);
        String origFilePath = filePath + ".orig";
        File backupFile = new File(origFilePath);

        // If a backup file already exists, delete it first to ensure rename succeeds
        if (backupFile.exists() && !backupFile.delete()) {
            Toast.makeText(ctx, "Cannot create backup file " + origFilePath, Toast.LENGTH_LONG).show();
            return false;
        }

        // Atomic move fallback: File.renameTo
        if (!sourceFile.renameTo(backupFile)) {
            Toast.makeText(ctx, "Cannot create backup file " + origFilePath, Toast.LENGTH_LONG).show();
            return false;
        }

        // StandardOpenOption.CREATE + TRUNCATE_EXISTING equals default FileOutputStream(file, false)
        try (FileOutputStream fos = new FileOutputStream(sourceFile, false)) {
            fos.write(textOut.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (IOException e) {
            Toast.makeText(ctx, "Cannot write to output " + filePath, Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    public static boolean formatCode(Context ctx, String fn_path) {
        String textIn = readCodeFromFile(ctx, fn_path);
        String options = "-style=java";
        String textOut = Features.AStyleMain(textIn, options);
        if (textIn.isEmpty())
            return false;
        if (!writeCodeToFile(ctx, textOut, fn_path))
            return false;
        return true;
    }

    public static String convertBytesLength(long size) {
        DecimalFormat formater = new DecimalFormat("####.00");
        if (size < 1024)
            return size + "B";
        else if (size < 1024 * 1024) {
            float kbsize = size / 1024f;
            return formater.format(kbsize) + "KB";
        } else if (size < 1024 * 1024 * 1024) {
            float mbsize = size / 1024f / 1024f;
            return formater.format(mbsize) + "MB";
        } else {
            float gbsize = size / 1024f / 1024f / 1024f;
            return formater.format(gbsize) + "GB";
        }
    }

    /**
     * Reads the first 4 bytes of the file to verify the ZIP signature.
     */
    private static boolean hasZipHeader(File file) {
        byte[] signature = new byte[4];
        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead = fis.read(signature);
            if (bytesRead < 4) {
                return false;
            }

            // Standard Local File Header: "PK\003\004" (0x50, 0x4B, 0x03, 0x04)
            boolean isLocalHeader = signature[0] == 0x50 && signature[1] == 0x4B
                    && signature[2] == 0x03 && signature[3] == 0x04;

            // Empty ZIP archive (End of Central Directory): "PK\005\006" (0x50, 0x4B, 0x05, 0x06)
            boolean isEmptyZip = signature[0] == 0x50 && signature[1] == 0x4B
                    && signature[2] == 0x05 && signature[3] == 0x06;

            // Spanned archive marker: "PK\007\008" (0x50, 0x4B, 0x07, 0x08)
            boolean isSpannedZip = signature[0] == 0x50 && signature[1] == 0x4B
                    && signature[2] == 0x07 && signature[3] == 0x08;

            return isLocalHeader || isEmptyZip || isSpannedZip;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isZip(File file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() < 4) {
            return false;
        }

        // Fast path: Check the 4-byte magic number first to avoid expensive parsing.
        if (!hasZipHeader(file)) {
            return false;
        }

        // Strict verification: Verify the central directory structure.
        try (ZipFile ignored = new ZipFile(file)) {
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void startInstallPermissionSettingActivity(Activity ctx) {
        Uri packageURI = Uri.parse("package:" + ctx.getPackageName());

        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageURI);
        ctx.startActivityForResult(intent, 10086);
    }

    public static void installApk(Activity ctx, File apk) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            intent.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
        } else {
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getApplicationContext().getPackageName() + ".provider", apk);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.getBaseContext().startActivity(intent);
    }

    public static String getWidevineUniqueID() {
        UUID wvu = new UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L);
        try {
            MediaDrm mediaDrm = new MediaDrm(wvu);
            byte[] deviceUniqueId = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID);
            mediaDrm.release();
            StringBuilder sb = new StringBuilder();
            for(byte ch : deviceUniqueId) sb.append(String.format("%02x", ch));
            return sb.toString();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    public static String getIMEI(Context context) {
        try {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager == null) {
                return null;
            }
            // Unless you guide the user to manually execute:
            // "adb shell pm grant com.perez.revkiller android.permission.READ_PRIVILEGED_PHONE_STATE"
            // in the terminal, you cannot obtain the IMEI, MEID, and S/N since Android 10 (API 29).
            if(Build.VERSION.SDK_INT >= 26) {
                return Build.getSerial() + " (S/N) / " + telephonyManager.getImei() + " (IMEI) / " + telephonyManager.getMeid() + " (MEID)";
            } else return Build.SERIAL + " (S/N) / " + telephonyManager.getDeviceId() + " (IMEI/MEID)";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    public static List<String> getSelfInstallSource(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        String pn = ctx.getPackageName();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                List<String> arr = new ArrayList<>();
                InstallSourceInfo isi = pm.getInstallSourceInfo(pn);
                arr.add(isi.getInstallingPackageName());
                arr.add(isi.getInitiatingPackageName());
                arr.add(isi.getOriginatingPackageName());
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    arr.add(isi.getUpdateOwnerPackageName());
                return arr;
            } catch (PackageManager.NameNotFoundException e) {
                return new ArrayList<>();
            }
        } else {
            try {
                return Collections.singletonList(pm.getInstallerPackageName(pn));
            } catch (IllegalArgumentException e) {
                return new ArrayList<>();
            }
        }
    }

    public static byte[] getSvcSig(Context ctx) {
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.adoptFd(Features.openFd(ctx.getPackageResourcePath()));
             ZipInputStream zis = new ZipInputStream(new FileInputStream(fd.getFileDescriptor()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().matches("(META-INF/.*)\\.(RSA|DSA|EC)")) {
                    CertificateFactory certFactory = CertificateFactory.getInstance("X509");
                    return certFactory.generateCertificate(zis).getEncoded();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] getZipSig(Context ctx) {
        try (ZipFile zipFile = new ZipFile(ctx.getPackageResourcePath())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().matches("(META-INF/.*)\\.(RSA|DSA|EC)")) {
                    InputStream is = zipFile.getInputStream(entry);
                    CertificateFactory certFactory = CertificateFactory.getInstance("X509");
                    return certFactory.generateCertificate(is).getEncoded();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Drawable showApkIcon(Context ctx, String apkPath) {
        PackageManager pm = ctx.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES);
        if(info != null && info.applicationInfo != null) {
            info.applicationInfo.sourceDir = apkPath;
            info.applicationInfo.publicSourceDir = apkPath;
            try {
                return info.applicationInfo.loadIcon(pm);
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
            }
        }
        return ResourcesCompat.getDrawable(ctx.getResources(), R.drawable.android, null);
    }

    public static String getSystemInfo(Context ctx) {
        StringBuilder info = new StringBuilder();
        String androidId = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        List<String> sis = getSelfInstallSource(ctx);
        info.append("Phone model: ").append(Build.MODEL).append("\n");
        info.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        info.append("Android version: ").append(Build.VERSION.RELEASE).append("\n");
        info.append("Android SDK code: ").append(Build.VERSION.SDK_INT).append("\n");
        info.append("CPU variant: ").append(Build.CPU_ABI).append(" / ").append(Build.CPU_ABI2).append("\n");
        info.append("Hardware name: ").append(Build.HARDWARE).append("\n");
        info.append("Baseband version: ").append(Build.getRadioVersion()).append("\n");
        info.append("BootLoader version: ").append(Build.BOOTLOADER).append("\n");
        info.append("Device Modem Identifier: ").append(getIMEI(ctx)).append("\n");
        info.append("System ID: ").append(androidId).append("\n");
        info.append("Widevine UUID: ").append(getWidevineUniqueID()).append("\n");
        info.append("App ZIP signature: ").append(HashUtil.md5(getZipSig(ctx))).append("\n");
        info.append("App SVC signature: ").append(HashUtil.md5(getSvcSig(ctx))).append("\n");
        if(!sis.isEmpty()) {
            info.append("Installation Info:").append("\n");
            info.append("\t").append("Who installed me: ").append(sis.get(0)).append("\n");
            if(sis.size() > 1) {
                info.append("\t").append("Who initiated my installation: ").append(sis.get(1)).append("\n");
                info.append("\t").append("My package origin: ").append(sis.get(2)).append("\n");
                if(sis.size() > 3) info.append("\t").append("Update owner package: ").append(sis.get(3)).append("\n");
            }
        }
        return info.toString();
    }

    public static String getFullException(Throwable th) {
        StringBuilder initial = new StringBuilder(th.toString() + "\n");
        for(StackTraceElement ste : th.getStackTrace()) {
            initial.append("\t").append("at ").append(ste.toString()).append("\n");
        }
        return initial.toString();
    }

    public static void showDlgMsg(Context context, String title, String message, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if(!title.isEmpty()) builder.setTitle(title);
        builder.setMessage(message);
        if(listener != null) {
            builder.setPositiveButton(android.R.string.ok, listener);
            builder.setNegativeButton(android.R.string.cancel, listener);
        } else builder.setNeutralButton(android.R.string.ok, null);
        AlertDialog dialog = builder.create();
        dialog.show();
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextIsSelectable(true);
        }
    }
}
