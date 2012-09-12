package org.copywaste.breakzradio;

import android.util.Log;

public class Logger {
	public static final boolean DEV = true;
	public static final String  LOGGER_TAG = "breakzradio";
	
	public static void log(String message) {
        if (DEV) {
            Log.d(LOGGER_TAG, message);
        }
    }

    public static void log(Throwable e) {
        if (DEV) {
            if (e != null) {
                String msg = e.getMessage();
                if (msg == null ) {
                    msg = "Exception.Message was Null";
                }
                Log.e(LOGGER_TAG, msg);

                for (StackTraceElement ste : e.getStackTrace()) {
                    Log.e(LOGGER_TAG, ste.getClassName() + " - " + ste.getMethodName() + " [ " + ste.getFileName() + " : " + ste.getLineNumber() + " ]");
                }
            } else {
                Log.e(LOGGER_TAG, "Exception was Null");
            }
        }
    }    
}
