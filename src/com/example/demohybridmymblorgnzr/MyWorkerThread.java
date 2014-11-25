package com.example.demohybridmymblorgnzr;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/**
 * Not used anymore in main activity; instead, using HandlerThread since it already has a Looper.
 * (Not deleting for now; leave as example of adding a Looper to a worker thread.)
 *
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
      // handles msgs/runnables received on msg queue, this will start a loop that listens
      Looper.loop();
    }

    public Handler getHandlerToMsgQueue() {
      return workerHandler;
    }

    // clk: trying to make sure thread does not leak
    public void cleanup() {
        workerHandler = null;
        //Looper.myLooper().quitSafely(); // crashes app; seems not needed despite what JavaDocs for Looper.loop() says; see http://stackoverflow.com/questions/17617731/where-quit-the-looper
    }
}
