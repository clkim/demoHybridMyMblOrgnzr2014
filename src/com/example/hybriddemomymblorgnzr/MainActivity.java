package com.example.hybriddemomymblorgnzr;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.karura.framework.plugins.utils.ContactAccessor;
import com.karura.framework.plugins.utils.ContactAccessorSdk5;

public class MainActivity extends Activity {


    private static final String FILE_SCHEME = "file";
    private static final int MY_REQUEST_CODE = 88;
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final String PERSONAL = "personal";
    private static final  String[] KEYS = new String[] {"category",
            "firstName", "lastName",
            "address1Type", "address1", "address2Type", "address2",
            "phone1Type", "phone1", "phone2Type", "phone2",
            "eMail"};

    private Context mContext;
    private WebView mWebView;
    private TextToSpeech mTextToSpeech;

    public class MyJsToJavaInterfaceObject {

        /** Fetch contacts from native address book */
        @JavascriptInterface
        public void fetchContacts(String handleIdForDeferredObject) {
            Log.d("** debugging", "handleIdForDeferredObject is: " + handleIdForDeferredObject);

            JSONArray entriesInContacts = getEntriesInContacts();
            //JSONArray entriesInContacts = null; //test error path

            // form javascript
            StringBuilder sb = new StringBuilder();
            sb.append("var hybrid = window.exports.hybrid; ");
            // check for successful call above
            if (entriesInContacts != null) {
                // get a string in JSON notation (key in quotes)
                String json = entriesInContacts.toString();
                sb.append("hybrid.deferredMap["+ handleIdForDeferredObject +"].resolve("+ json +"); ");
            } else {
                sb.append("hybrid.deferredMap["+ handleIdForDeferredObject +"].reject('Android code threw an exception, null returned.'); ");
            }
            // remove deferred object to avoid memory leak
            sb.append("hybrid.deferredMap["+ handleIdForDeferredObject +"]= null; ");
            final String jsCode = String.format("javascript:%s", sb.toString());
            Log.d("** debugging", "jsCode is: " + jsCode);

            // evaluateJavascript() must be run in UI thread
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // the callback object could be replaced with null since we don't need to do anything in this case;
                    // besides, the jQuery resolve() or reject() methods return a Deferred object and not a string
                    mWebView.evaluateJavascript(jsCode, null);
                    /*
                    mWebView.evaluateJavascript(jsCode, new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String s) {}
                    });
                    */
                }
            });
        }

        /** Show a alert dialog from the web page */
        @JavascriptInterface
        public void showAlertDialog(String toast) {
            new AlertDialog.Builder(mContext)
                    .setTitle("Alert")
                    .setMessage(toast)
                    .setNeutralButton("OK", null)
                    .show();
        }
    }

    public class MyTtsOnInitListener implements TextToSpeech.OnInitListener {
        @Override
        public void onInit(int i) {
            if (i == TextToSpeech.SUCCESS) {
                mTextToSpeech.setLanguage(Locale.US);
            } else {
                Log.e(MyTtsOnInitListener.class.getSimpleName(), "Snap! Text-to-speech onInit returned FAIL");
            }
        }
    }

    private JSONArray getEntriesInContacts() {
        ContactAccessor contactAccessor = new ContactAccessorSdk5();
        contactAccessor.setContext(mContext);
        JSONArray jsonArray = new JSONArray();
        try {
            Map<String, String> hashmap = null;

            JSONArray result = contactAccessor.search(new JSONArray(new String[] {"*"}), null, 0, 10);
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
                // address
                jo = getFirstJSONObjectIn(jsonObject, ContactAccessor.FORMATTED_ADDRESS);
                String addressType   = (jo == null ? "" : jo.optString("type"))
                        .replace("custom", "other");
                String streetAddress = (jo == null ? "" : jo.optString("formatted"));

                // phone
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

                // email
                jo = getFirstJSONObjectIn(jsonObject, ContactAccessor.EMAIL);
                String email = (jo == null ? "" : jo.optString("value"));

                // build map for JSONObject
                // the properties and values are needed by the web app
                String[] values = new String[] {PERSONAL,
                        firstName, lastName,
                        addressType, streetAddress, "", "",
                        phoneType, phoneNumber, "", "",
                        email};
                hashmap = new HashMap<String, String>();
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;

        mWebView = (WebView) findViewById(R.id.activity_main_webview);
        // inject a javascript-java interface object, available to JavaScript at next page (re)load
        //  javascript interacts with java object on a private background thread of the webview, so need to watch for thread safety
        //  only java object's methods annotated with JavascriptInterface are accessible for targetSdkVersion to 17 (JELLY_BEAN_MR1) or higher
        mWebView.addJavascriptInterface(new MyJsToJavaInterfaceObject(), "MyAndroid");

        // Enable Javascript
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        // JavaScript running in the context of a file scheme URL should be allowed to access content from other file scheme URLs
        webSettings.setAllowFileAccessFromFileURLs(true);
        // JavaScript running in the context of a file scheme URL should be allowed to access content from any origin
        //webSettings.setAllowUniversalAccessFromFileURLs(true);

        // allow window.localStorage in webview
        webSettings.setDomStorageEnabled(true);

        // viewport

        // zoom
        //webSettings.setDisplayZoomControls(true); // default is false
        //webSettings.setBuiltInZoomControls(true); // default true?

        // Stop local file links and redirects from opening in browser instead of WebView
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                String urlScheme = Uri.parse(url).getScheme();
                if(urlScheme!=null && urlScheme.equalsIgnoreCase(FILE_SCHEME)) {
                    // tells the platform not to override the URL, but to load it in the WebView
                    return false;
                }

                // open a browser for non-local web pages
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                view.getContext().startActivity(intent);
                return true; // prevents URL from being loaded into the WebView
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // do a test of tts by fetching first entry in notes
                if (url.endsWith("index.html")) {
                    testTts();
                }
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


        // webview methods must be called on UI thread, but need to be nonblocking
        mWebView.loadUrl("file:///android_asset/index.html");


        // Text to Speech

        // check there is a default tts engine installed
        Intent checkTtsIntent = new Intent();
        checkTtsIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkTtsIntent, MY_REQUEST_CODE);

        // in webviewclient we do a test of tts whenever vebview finish loading the index.html page in onPageFinished()
    }
    private void testTts() {
        // do test of tts
        // by demoing how android can run javascript in webview: 1) fetch notes data, then 2) activate tts function in native app
        StringBuilder sb = new StringBuilder();
        sb.append("var jqXHR = $.ajax({ url: ajaxURLPrefix+'/note', async: false });"); // route is note
        sb.append("var resp = jqXHR.responseText;");
        sb.append("JSON.parse(resp)[0].text;"); // javascript object after parsing response is an array of size 1; field name is text
        String jsStatements = sb.toString();
        String js = String.format("javascript:%s", jsStatements);

        // onReceiveValue is always {"readyState":1} in logcat if ajax call is async
        mWebView.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String s) {
                //Log.i("*** Webview", s);
                mTextToSpeech.speak(s, TextToSpeech.QUEUE_ADD, null);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == MY_REQUEST_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // success, create the TTS instance
                mTextToSpeech = new TextToSpeech(mContext, new MyTtsOnInitListener());
            } else {
                // missing tts engine, go to store to get one to install
                Intent installIntent = new Intent();
                installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
    }

    @Override
    public void onBackPressed() {
        // prevent the back button from incorrectly exiting the application
        //  if there are pages in the WebView history to navigate to previous page
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
    public void onDestroy() {
        super.onDestroy();
        if (mTextToSpeech != null) {
            mTextToSpeech.stop();
            // release resources
            mTextToSpeech.shutdown();
        }
    }
}
