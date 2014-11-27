package com.example.demohybridmymblorgnzr;

import static android.content.DialogInterface.BUTTON_NEUTRAL;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import com.karura.framework.plugins.utils.ContactAccessor;
import com.karura.framework.plugins.utils.ContactAccessorSdk5;

public class MainActivity extends Activity {

    private static final String JS_INTERFACE_OBJECT_NAME = "MyAndroid";
    private static final int MY_REQUEST_CODE             = 88;
    private static final String LOG_TAG                  = MainActivity.class.getSimpleName();
    private static final String PERSONAL                 = "personal";
    private static final String FILE_URL_INDEX_HTML      = "file:///android_asset/www/index.html";
    private static final String[] KEYS = new String[] {
            "category",
            "firstName", "lastName",
            "address1Type", "address1", "address2Type", "address2",
            "phone1Type", "phone1", "phone2Type", "phone2",
            "eMail"};

    private Context mContext;
    private WebView mWebView;
    private TextView mTextView;
    private Button mButtonFetchSay;
    private Button mButtonClear;
    private TextToSpeech mTextToSpeech;
    private AlertDialog alertDialog; // an alert dialog called by JavaScript from WebView
    private HandlerThread workerHandlerThread;
    private Handler handler;

    public class MyJsToJavaInterfaceObject {

        /** Fetch contacts from native address book for use in WebView
         *
         *  Runs in separate thread associated with WebView JavaScript/Java binding.
         */
        @JavascriptInterface //mandatory if targetSdkVersion 17+ & running Android 4.2+
        public void fetchContacts(final String handleIdForDeferredObject) {
            Log.d("** debugging", "handleIdForDeferredObject is: " + handleIdForDeferredObject);

            // Note that the worker HandlerThread here is different from the separate thread (not UI thread)
            //  that this WebView JavaScript/Java method runs in
            handler = new Handler(workerHandlerThread.getLooper()); // set to null in onDestroy()
            if (handler != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        getContactsAndResolveJSDeferredObj(handleIdForDeferredObject);
                    }
                });
            }
        }

        /** Show an alert dialog called from the WebView - a 'hacky' example
         *
         *  Might be better to avoid, because there does not seem to be easy and robust
         *  way to avoid "leaked window" error if alert dialog is open when orientation
         *  change occurs (and Activity is destroyed). See comment notes below.
         *
         *  Runs in separate thread associated with WebView JavaScript/Java binding.
         */
        @JavascriptInterface //mandatory if targetSdkVersion 17+ & running Android 4.2+
        public void showAlertDialog(final String toast) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    alertDialog = buildAlertDialog(toast);
                    alertDialog.show();
                }
            });

            // Limited successful attempt to dismiss the alert dialog after a time interval e.g. 2s.
            // Could see "leaked window" LogCat error log message if orientation is changed
            //  many times in quick succession, analogous to comments in onPause(), but
            //  for somewhat different reason as explained below.
            // We null out alertDialog in onDestroy() to try to avoid a leak.
            Handler handler = new Handler(); // handler works with looper in this separate thread; don't need a worker thread for what we're doing here
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Multiple orientation changes in quick succession might cause FATAL EXCEPTION;
                            //  we see IllegalArgumentException: PhoneWindow$DecorView not attached to a window manager.
                            // The if-condition check seems to prevent crash, but could still result in:
                            //  WindowLeaked: Activity... has leaked window ...PhoneWindow$DecorView
                            //  if the alertDialog were not from most recent orientation change and so
                            //  the if-condition prevents it from being dismissed;
                            //  the if-condition is not documented and was obtained by trial-error from
                            //  noticing the objects mentioned in the "leaked window" LogCat error message,
                            //  so is quite hackish and might not work in future Android versions.
                            if (alertDialog != null &&
                                    alertDialog.getWindow().getDecorView().getWindowVisibility() != View.GONE) {
                                alertDialog.dismiss();
                            }
                        }
                    });
                }
            }, 2000);
        }
    }

    private AlertDialog buildAlertDialog(String toast) {
        AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();
        alertDialog.setTitle("Alert");
        alertDialog.setMessage(toast);
        alertDialog.setButton(BUTTON_NEUTRAL, "OK", new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // Probably should explicitly dismiss or cancel dialog, for reason
                //  that is documented in onPause() method below; touching OK button
                //  even without any code here also seems to produce similar behavior.
                // Also should dismiss dialog before exiting Activity e.g. in onPause()
                dialog.dismiss();
            }
        });
        return alertDialog;
    }

    private void getContactsAndResolveJSDeferredObj(String handleIdForDeferredObject) {
        JSONArray entriesInContacts = getEntriesInContacts();
        //JSONArray entriesInContacts = null; //test error path

        // form javascript
        StringBuilder sb = new StringBuilder();
        sb.append("var hybrid = window.hybrid; ");
        // check for successful call above
        if (entriesInContacts != null) {
            // get a string in JSON notation (key in quotes)
            // works here as a javascript object literal because it allows string or numeric literal for name of property
            // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Values,_variables,_and_literals#Object_literals
            String json = entriesInContacts.toString();
            sb.append("hybrid.deferredMap["+ handleIdForDeferredObject +"].resolve("+ json +"); ");
        } else {
            sb.append("hybrid.deferredMap["+ handleIdForDeferredObject +"].reject('Android code threw an exception, null returned.'); ");
        }
        // remove reference to deferred object to avoid memory leak
        sb.append("delete hybrid.deferredMap["+ handleIdForDeferredObject +"]; ");
        final String jsCode = String.format("javascript:%s", sb.toString());
        //Log.d("** debugging", "jsCode is: " + jsCode);

        // evaluateJavascript() must be run in UI thread
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // the callback object could be replaced with null since we don't need to do anything in this case;
                // besides, the jQuery resolve() or reject() methods return a Deferred object and not a string
                mWebView.evaluateJavascript(jsCode, null);
            }
        });
    }

    private JSONArray getEntriesInContacts() {
        ContactAccessor contactAccessor = new ContactAccessorSdk5();
        contactAccessor.setContext(mContext);
        JSONArray jsonArray = new JSONArray(); // array of values to be returned
        try {
            JSONArray result = contactAccessor.search(
                    new JSONArray(new String[] {"*"}), null, 0, 10);
            for (int i=0; i<result.length(); i++) {
                JSONObject jsonObject = result.getJSONObject(i);

                // name
                String js = jsonObject.optString(ContactAccessor.DISPLAY_NAME);
                String[] displayName = (js == null ? new String[]{} : js.split(" "));
                String firstName = "";
                String lastName  = "";
                if (displayName.length == 1) lastName = displayName[0];
                if (displayName.length >= 2) {
                    firstName = displayName[0];
                    lastName  = displayName[displayName.length - 1];
                }

                JSONObject jo;
                // address, for demo just get first one
                jo = getFirstJSONObjectIn(jsonObject, ContactAccessor.FORMATTED_ADDRESS);
                String addressType   = (jo == null ? "" : jo.optString("type"))
                        .replace("custom", "other");
                String streetAddress = (jo == null ? "" : jo.optString("formatted"));

                // phone, for demo just get first one
                jo = getFirstJSONObjectIn(jsonObject, ContactAccessor.PHONE_NUMBER);
                String phoneType   = (jo == null ? "" : jo.optString("type"))
                        .replace("mobile", "cell")
                        .replace("home fax", "fax")
                        .replace("work fax", "fax")
                        .replace("other fax", "fax");
                if (!"home".equals(phoneType) && !"work".equals(phoneType)
                        && !"cell".equals(phoneType) && !"fax".equals(phoneType))
                    phoneType = "other";
                String phoneNumber = (jo == null ? "" : jo.optString("value"));

                // email, for demo just get first one
                //   ref ContactAccessorSdk5.emailQuery() for “value” key to use
                jo = getFirstJSONObjectIn(jsonObject, ContactAccessor.EMAIL);
                String email = (jo == null ? "" : jo.optString("value"));

                // build map for JSONObject
                // the values (for demo, just set category to string PERSONAL)
                //   and properties (in string array KEYS) needed by the web app
                String[] values = new String[] {PERSONAL,
                        firstName, lastName,
                        addressType, streetAddress, "", "",
                        phoneType, phoneNumber, "", "",
                        email};
                Map<String, String> hashmap = new HashMap<String, String>();
                for (int j = 0; j<KEYS.length; j++) {
                        hashmap.put(KEYS[j], values[j]);
                }

                jsonArray.put(new JSONObject(hashmap));
            }

            return jsonArray;

        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get first JSON object in {code jsonObject} with {code key}
     * @param jsonObject
     * @param key
     * @return JSON object or null if none exists
     */
    private JSONObject getFirstJSONObjectIn(JSONObject jsonObject, String key) throws JSONException {
        JSONArray ja = jsonObject.optJSONArray(key);
        JSONObject jo = (ja != null && ja.length()>=1) ? ja.getJSONObject(0) : null;
        return  jo;
    }


    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;

        mTextView = (TextView) findViewById(R.id.activity_main_textview);

        mButtonFetchSay = (Button) findViewById(R.id.activity_main_button_fetch);
        mButtonFetchSay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchAndSayFirstNoteEntryInWebApp();
            }
        });
        mButtonClear = (Button) findViewById(R.id.activity_main_button_clear);
        mButtonClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTextView.setText("");
            }
        });

        mWebView = (WebView) findViewById(R.id.activity_main_webview);
        // inject a javascript-java-interface object, available to JavaScript at next page (re)load
        //  javascript interacts with java object on a private background thread of the webview, so need to watch for thread safety
        //  only java object's methods annotated with JavascriptInterface are accessible for targetSdkVersion to 17 (JELLY_BEAN_MR1) or higher
        mWebView.addJavascriptInterface(new MyJsToJavaInterfaceObject(), JS_INTERFACE_OBJECT_NAME);

        // set webview background to avoid 'flash' when app loads up with a WebView
        // set to be same as WebView home page background color (in demo app, it is just an image with white? background)
        //  http://developer.android.com/reference/android/R.color.html
        mWebView.setBackgroundColor(
                getResources().getColor(android.R.color.background_light)); // this is #ffffff
        //  OR use color hex value, but good practice to define color in application's values/colors.xml file and use above syntax
        //  https://github.com/android/platform_frameworks_base/blob/master/core/res/res/values/colors.xml
        //  http://developer.android.com/guide/topics/resources/more-resources.html#Color
        //mWebView.setBackgroundColor(Color.parseColor("#f3f3f3")); // this is "background_holo_light"; there is no android.R.color.background_holo_light

        // Enable Javascript
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        // JavaScript running in the context of a file scheme URL should be allowed to access content from other file scheme URLs
        webSettings.setAllowFileAccessFromFileURLs(true);
        // JavaScript running in the context of a file scheme URL should be allowed to access content from any origin
        //webSettings.setAllowUniversalAccessFromFileURLs(true);

        // allow window.localStorage in webview
        webSettings.setDomStorageEnabled(true);

        // viewport set in index.html meta tag

        // zoom seen in Chromium WebView Samples https://github.com/GoogleChrome/chromium-webview-samples
        // WebRTC sample app
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
            // Hide the zoom controls for HONEYCOMB+
            webSettings.setDisplayZoomControls(false); // default is true
        }
        webSettings.setBuiltInZoomControls(true); // only see pinch to zoom effect for dialog box; default false

        // Open local webpages in WebView instead of in mobile browser
        // default implementation in a myWebViewClient = new WebViewClient() always returns false
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if(Uri.parse(url).getHost().length() == 0) {
                    // don't override URL loading, load it in the WebView
                    return false;
                }
                // override the URL loading, request system to open the URL
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                view.getContext().startActivity(intent);
                return true;
            }
        });

        // Configure WebView for debugging
        // https://developer.chrome.com/devtools/docs/remote-debugging#configure-webview
        //  typo in getApplcationInfo()
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if ( 0 != ( getApplicationInfo().flags &= ApplicationInfo.FLAG_DEBUGGABLE ) ) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        }

        // Display webview DOM's window.console.log messages in logcat
        // http://developer.android.com/guide/webapps/debugging.html
        mWebView.setWebChromeClient(new WebChromeClient() {
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(LOG_TAG, cm.message() + " --- From line "
                        + cm.lineNumber() + " of "
                        + cm.sourceId() );
                return true;
            }
        });


        // webview methods must be called on UI thread; and non-blocking
        mWebView.loadUrl(FILE_URL_INDEX_HTML);


        // Text to Speech

        // check there is data for language installed, for TTS
        Intent checkTtsIntent = new Intent();
        checkTtsIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkTtsIntent, MY_REQUEST_CODE);
        // if onActivityResult() runs ok, we're ready for click listener of mButtonFetchSay
        // to demo TTS as well as demonstrate accessing data from web app


        // start WorkerThread
        workerHandlerThread = new HandlerThread("Worker HandlerThread"); // set to null in onDestroy()
        workerHandlerThread.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == MY_REQUEST_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // success, create the TTS instance
                //  seems need to use getApplicationContext() instead of mContext to avoid Error log: ...MainActivity has leaked ServiceConnection android.speech.tts.TextToSpeech$Connection@41e93920 that was originally bound here
                //  http://stackoverflow.com/questions/19653223/texttospeech-and-memory-leak
                mTextToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int i) {
                        if (i == TextToSpeech.SUCCESS) {
                            mTextToSpeech.setLanguage(Locale.getDefault());
                        } else {
                            Log.e(TextToSpeech.OnInitListener.class.getSimpleName(), "Snap! Text-to-speech onInit returned FAIL");
                        }
                    }
                });

            } else {
                // missing data for language, so install it
                Intent installIntent = new Intent();
                installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
    }

    private void fetchAndSayFirstNoteEntryInWebApp() {
        // demo how android can run javascript in webview: 1) fetch web app 'note' data, then 2) activate native app tts function
        StringBuilder sb = new StringBuilder();
        // ajaxURLPrefix is http://127.0.0.1:8080, path is /note
        // ajax call should wait for response, then fire off ValueCallback with result obtained
        sb.append("var jqXHR = $.ajax({ url: ajaxURLPrefix+'/note', async: false }); ");
        sb.append("var resp = jqXHR.responseText; ");
        // convert json text response to a JavaScript object as return value
        sb.append("JSON.parse(resp); ");
        String jsStatements = sb.toString();
        String js = String.format("javascript:%s", jsStatements);

        // onReceiveValue is always {"readyState":1} in logcat if ajax call is async
        mWebView.evaluateJavascript(js, new ValueCallback<String>() {

            @SuppressWarnings("deprecation") // TextToSpeech.speak(String, int, HashMap) deprecated in API 21
            @Override public void onReceiveValue(String s) {
                // not applicable for current implementation so for reference only:
                //  single string json value is wrapped in quotes http://stackoverflow.com/questions/19788294/how-does-evaluatejavascript-work
                //Log.i("*** Webview", s);
                String text;
                try {
                    // web app stores notes as array of json notation object literals
                    JSONArray allNotes = new JSONArray(s);
                    JSONObject firstNote = (JSONObject) allNotes.get(0);
                    // property name for noteText value is 'text'
                    text = firstNote.getString("text");
                } catch (JSONException e) {
                    text = "";
                }
                mTextToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null); // deprecated in API 21 but replacement crashes when run on 4.4.4

                mTextView.setText(text);
                mTextView.setEllipsize(TruncateAt.MARQUEE);
                mTextView.setSingleLine(true);
                mTextView.setMarqueeRepeatLimit(-1); // same as"marquee_forever" in xml
                mTextView.setSelected(true); // seems needed for marquee scrolling
            }
        });
    }

    @Override
    public void onBackPressed() {
        // prevent the back button from incorrectly exiting the app
        //  if there are pages in the WebView history to navigate back
        if(mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            // Let the system handle the back button
            super.onBackPressed();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Need to dismiss alertDialog before exiting Activity in order to avoid
        //  seeing LogCat error log message (though app does not crash):
        //  E/WindowManager(<pid>): android.view.WindowLeaked: Activity... has leaked window ...PhoneWindow$DecorView
        // But, will still see the "leaked window" LogCat error log message on third time
        //  if orientation is changed in quick succession because there are actually
        //  two alertDialogs after two orientation changes, but first alertDialog seems
        //  unreachable to be dismissed when orientation is changed a third time.
        // We null out alertDialog in onDestroy() to try to avoid a leak.
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mTextToSpeech != null) {
            mTextToSpeech.stop();
            // release resources
            mTextToSpeech.shutdown();
        }
        // set to null to try to avoid leaks
        handler = null;
        workerHandlerThread = null; // Thread.stop() is unsafe and deprecated
        alertDialog = null;
    }
}
