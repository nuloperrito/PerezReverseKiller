package com.perez.netdiag.Task;

import android.widget.TextView;

public abstract class BaseTask {

    String url;
    TextView resultTextView;

    String tag;

    public BaseTask(String url, TextView resultTextView) {
        this.url = url;
        this.resultTextView = resultTextView;
    }

    public void doTask(){
        resultTextView.setText("");
        tag = System.currentTimeMillis()+"";
        resultTextView.setTag(tag);
        
        if(this instanceof TraceTask){
            getExecRunnable().run();
        }else {
            new Thread(getExecRunnable()).start();
        }
    }

    public class updateResultRunnable implements Runnable{
        String resultString;

        public updateResultRunnable(String resultString){
            this.resultString = resultString;
        }

        @Override
        public void run() {
            if(resultTextView!=null && resultTextView.getTag().equals(tag)) {
                resultTextView.append(resultString);
                resultTextView.requestFocus();
            }
        }
    }

    public abstract Runnable getExecRunnable();
}
