package com.example.hybriddemomymblorgnzr;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/**
 * Following http://androidshortnotes.blogspot.com/2013/02/thread-concept-in-android.html?view=sidebar
 *
 */
@SuppressLint("HandlerLeak") // the warning is for a Handler using looper of main thread
public class MyWorkerThread extends Thread {

    private Handler workerHandler;

    @Override
    public void run() {
      // Thread by default doesn't have a msg queue, so attach a msg queue to this thread
      Looper.prepare();
      // this will bind the Handler to the msg queue
      workerHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // not processing messages passed into handler, just runnables
        }
      };
      // handles msgs/runnables receive to msgqueue, this will start a loop that listens
      Looper.loop();
    }

    public Handler getHandlerToMsgQueue() {
      return workerHandler;
    }

    // clk: trying to make sure thread does not leak
    public void cleanup() {
        workerHandler = null;
    }
}
