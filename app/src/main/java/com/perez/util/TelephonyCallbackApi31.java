package com.perez.util;

import android.os.Build;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.S)
public class TelephonyCallbackApi31 {
    private static CallForwardingRealtimeCallback sRealtimeCallback;

    public interface IndicatorCallback {
        void onIndicator(boolean enabled);
    }

    private static class CallForwardingRealtimeCallback extends TelephonyCallback
            implements TelephonyCallback.CallForwardingIndicatorListener {
        private final IndicatorCallback callback;

        CallForwardingRealtimeCallback(IndicatorCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onCallForwardingIndicatorChanged(boolean cfi) {
            if(callback != null) {
                callback.onIndicator(cfi);
            }
        }
    }

    private static class CallForwardingOneShotCallback extends TelephonyCallback
            implements TelephonyCallback.CallForwardingIndicatorListener {
        private final TelephonyManager tm;
        private final IndicatorCallback callback;
        private boolean consumed = false;

        CallForwardingOneShotCallback(TelephonyManager tm, IndicatorCallback callback) {
            this.tm = tm;
            this.callback = callback;
        }

        @Override
        public void onCallForwardingIndicatorChanged(boolean cfi) {
            if(!consumed) {
                consumed = true;
                try {
                    tm.unregisterTelephonyCallback(this);
                } catch(Exception ignored) {}
                if(callback != null) {
                    callback.onIndicator(cfi);
                }
            }
        }
    }

    public static void registerRealtime(TelephonyManager tm, java.util.concurrent.Executor executor, IndicatorCallback callback) {
        unregisterRealtime(tm);
        sRealtimeCallback = new CallForwardingRealtimeCallback(callback);
        tm.registerTelephonyCallback(executor, sRealtimeCallback);
    }

    public static void unregisterRealtime(TelephonyManager tm) {
        if(sRealtimeCallback != null && tm != null) {
            try {
                tm.unregisterTelephonyCallback(sRealtimeCallback);
            } catch(Exception ignored) {}
            sRealtimeCallback = null;
        }
    }

    public static void queryAndExecute(TelephonyManager tm, java.util.concurrent.Executor executor, IndicatorCallback callback) {
        CallForwardingOneShotCallback oneShot = new CallForwardingOneShotCallback(tm, callback);
        tm.registerTelephonyCallback(executor, oneShot);
    }
}