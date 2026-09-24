package com.perez.revkiller.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.perez.revkiller.PerezReverseKillerMain;
import com.perez.revkiller.R;
import com.perez.revkiller.ZipManagerMain;
import com.perez.util.AsyncImageLoader;
import com.perez.util.FileUtil;
import com.perez.util.RealFuncUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;

public class FileListAdapter extends BaseAdapter {
    public static final int S_IFMT = 0170000;
    public static final int S_IFLNK = 0120000;
    public static final int S_IFREG = 0100000;
    public static final int S_IFBLK = 0060000;
    public static final int S_IFDIR = 0040000;
    public static final int S_IFCHR = 0020000;
    public static final int S_IFIFO = 0010000;

    protected final Context mContext;
    protected final LayoutInflater mInflater;
    private final SimpleDateFormat format = new SimpleDateFormat("yy-MM-dd HH:mm:ss");
    protected AsyncImageLoader asyn;
    private final boolean m_isOutsideArchive;

    public FileListAdapter(Context context, boolean isOutsideArchive) {
        mContext = context;
        m_isOutsideArchive = isOutsideArchive;
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (m_isOutsideArchive) {
            asyn = new AsyncImageLoader(mContext);
        }
    }

    public FileListAdapter(Context context) {
        this(context, true);
    }

    @Override
    public int getCount() {
        if (m_isOutsideArchive) {
            return getFileList().size();
        } else {
            return ((ZipManagerMain) mContext).fileList.size();
        }
    }

    @Override
    public Object getItem(int position) {
        if (m_isOutsideArchive) {
            return getFileList().get(position);
        } else {
            return ((ZipManagerMain) mContext).fileList.get(position);
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private String permRwx(int perm) {
        return ((perm & 04) != 0 ? "r" : "-") + ((perm & 02) != 0 ? "w" : "-") + ((perm & 1) != 0 ? "x" : "-");
    }

    private String permFileType(int perm) {
        switch (perm & S_IFMT) {
            case S_IFLNK:
                return "l";
            case S_IFREG:
                return "-";
            case S_IFBLK:
                return "b";
            case S_IFDIR:
                return "d";
            case S_IFCHR:
                return "c";
            case S_IFIFO:
                return "p";
            default:
                return "?";
        }
    }

    public String permString(int perms) {
        return permFileType(perms) + permRwx(perms >> 6) + permRwx(perms >> 3) + permRwx(perms);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        int layoutRes = m_isOutsideArchive ? R.layout.list_item_details : R.layout.zip_list_item;

        if (convertView == null) {
            convertView = mInflater.inflate(layoutRes, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.container = (RelativeLayout) convertView;
            viewHolder.icon = convertView.findViewById(R.id.icon);
            viewHolder.text = convertView.findViewById(R.id.text);
            viewHolder.perm = convertView.findViewById(R.id.permissions);
            viewHolder.time = convertView.findViewById(R.id.times);
            viewHolder.size = convertView.findViewById(R.id.size);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        if (m_isOutsideArchive) {
            // Mode: Regular file system
            final File file = getFileList().get(position);
            String name = file.getName();
            setIcon(file.isDirectory(), name, viewHolder.icon, file);
            viewHolder.text.setText(name);

            String perms;
            try {
                perms = permString(FileUtil.getPermissions(file));
            } catch (Exception e) {
                perms = "????";
            }
            viewHolder.perm.setText(perms);

            Date date = new Date(file.lastModified());
            viewHolder.time.setText(format.format(date));

            if (file.isDirectory()) {
                viewHolder.size.setText("");
            } else {
                viewHolder.size.setText(RealFuncUtil.convertBytesLength(file.length()));
            }
        } else {
            // Mode: Inside archive (ZipManagerMain)
            ZipManagerMain zipManager = (ZipManagerMain) mContext;
            String fileName = zipManager.fileList.get(position);
            boolean isDir = zipManager.tree.isDirectory(fileName);

            setIcon(isDir, fileName, viewHolder.icon, null);
            viewHolder.text.setText(fileName);
            viewHolder.perm.setText("");

            if (!isDir) {
                ZipEntry zipEntry = zipManager.getEntry(fileName);
                Date date = new Date(zipEntry.getTime());
                viewHolder.time.setText(format.format(date));
                viewHolder.size.setText(RealFuncUtil.convertBytesLength(zipEntry.getSize()));
            } else {
                viewHolder.time.setText("");
                viewHolder.size.setText("");
            }
        }

        return convertView;
    }

    private void setIcon(boolean isDirectory, String fileName, final ImageView icon, final File file) {
        if (isDirectory) {
            icon.setImageResource(R.drawable.folder);
            return;
        }

        String ext = RealFuncUtil.getFileExtension(fileName);
        switch (ext) {
            case "apk":
                if (file != null && asyn != null) {
                    Drawable drawable = asyn.loadDrawable(file.getAbsolutePath(), icon,
                            (drawable1, imageView) -> icon.setImageDrawable(drawable1));
                    icon.setImageDrawable(drawable);
                } else {
                    icon.setImageResource(R.drawable.android);
                }
                break;
            case "png":
            case "jpg":
            case "bmp":
            case "gif":
            case "webp":
                icon.setImageResource(R.drawable.image);
                break;
            case "zip":
            case "rar":
            case "7z":
                icon.setImageResource(R.drawable.zip);
                break;
            case "jar":
                icon.setImageResource(R.drawable.jar);
                break;
            case "so":
                icon.setImageResource(R.drawable.sharedlib);
                break;
            case "dex":
            case "odex":
            case "oat":
                icon.setImageResource(R.drawable.dex);
                break;
            case "rc":
            case "sh":
                icon.setImageResource(R.drawable.script);
                break;
            case "xml":
                icon.setImageResource(R.drawable.xml);
                break;
            case "txt":
            case "log":
            case "c":
            case "cpp":
            case "h":
            case "hpp":
            case "java":
            case "kt":
            case "py":
            case "cs":
            case "smali":
            case "js":
            case "json":
            case "html":
            case "rs":
                icon.setImageResource(R.drawable.text);
                break;
            case "arsc":
                icon.setImageResource(R.drawable.arsc);
                break;
            case "pdf":
                icon.setImageResource(R.drawable.pdf);
                break;
            case "xls":
            case "xlsx":
                icon.setImageResource(R.drawable.excel);
                break;
            case "ppt":
            case "pptx":
            case "pps":
            case "ppsx":
                icon.setImageResource(R.drawable.ppt);
                break;
            case "doc":
            case "docx":
            case "dot":
            case "dotx":
                icon.setImageResource(R.drawable.word);
                break;
            case "mp4":
            case "3gp":
            case "avi":
            case "wmv":
            case "vob":
            case "ts":
            case "flv":
            case "rm":
            case "rmvb":
            case "f4v":
            case "mov":
            case "webm":
            case "mpg":
            case "asf":
            case "mkv":
                icon.setImageResource(R.drawable.video);
                break;
            case "mp3":
            case "aac":
            case "mp2":
            case "wav":
            case "wma":
            case "ogg":
            case "ape":
            case "amr":
            case "m4a":
            case "flac":
                icon.setImageResource(R.drawable.audio);
                break;
            default:
                icon.setImageResource(R.drawable.file);
                break;
        }
    }

    protected List<File> getFileList() {
        return PerezReverseKillerMain.mFileList;
    }

    static class ViewHolder {
        RelativeLayout container;
        ImageView icon;
        TextView text;
        TextView perm;
        TextView time;
        TextView size;
    }
}