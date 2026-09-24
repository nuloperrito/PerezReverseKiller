package com.perez.netdiag.Task;

import android.widget.TextView;


import com.perez.netdiag.Utils.NetInfo.NetBasicInfo;
import com.perez.netdiag.Utils.NetInfo.SystemBasicInfo;

public class InfoTask extends BaseTask {
    String url;

    TextView resultTextView;

    public InfoTask(String url, TextView resultTextView) {
        super(url, resultTextView);
        this.url = url;
        this.resultTextView = resultTextView;
    }

    @Override
    public Runnable getExecRunnable() {
        return execRunnable;
    }

    public Runnable execRunnable = new Runnable() {
        @Override
        public void run() {
            NetBasicInfo mNetBasicInfo = NetBasicInfo.getInstance(resultTextView.getContext());

            resultTextView.post(new updateResultRunnable(mNetBasicInfo.getApnInfo()
                    + "\r\nMac address : \r\n"
                    + "wlan0 :\t" + mNetBasicInfo.getMacAddress("wlan0")
                    + "\np2p0 :\t " + mNetBasicInfo.getMacAddress("p2p0")
                    + "\n\n" + SystemBasicInfo.getBuildInfo()
                    + "\n"));
        }
    };
}
