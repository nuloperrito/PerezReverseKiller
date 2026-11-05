package com.perez.revkiller;

import java.util.Date;

public class Features {
    public static class StatusMsg {
        private boolean success;
        private String staMsg;

        public StatusMsg(boolean success, String staMsg) {
            this.success = success;
            this.staMsg = staMsg;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getStaMsg() {
            return staMsg;
        }

        public void setStaMsg(String staMsg) {
            this.staMsg = staMsg;
        }
    }

    public static native int ExtractAllRAR(String f, String d);

    public static native boolean Oat2Dex(String f);

    public static native boolean Odex2Dex(String file, String dest);

    public static native boolean isValidElf(String elf);

    public static native long ELFHash(String strUri);

    public static native int dumpDex(int apiLevel, String appEntry);

    public native static String AStyleMain(String text, String opt);

    public native static int openFd(String path);

    public native static StatusMsg modifyTimestamp(int fd, Date date);

}
