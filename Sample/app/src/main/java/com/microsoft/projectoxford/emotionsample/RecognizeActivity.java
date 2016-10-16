//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license.
//
// Microsoft Cognitive Services (formerly Project Oxford): https://www.microsoft.com/cognitive-services
//
// Microsoft Cognitive Services (formerly Project Oxford) GitHub:
// https://github.com/Microsoft/Cognitive-Emotion-Android
//
// Copyright (c) Microsoft Corporation
// All rights reserved.
//
// MIT License:
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to
// permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
//
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED ""AS IS"", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
package com.microsoft.projectoxford.emotionsample;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;
import com.microsoft.projectoxford.emotion.EmotionServiceClient;
import com.microsoft.projectoxford.emotion.EmotionServiceRestClient;
import com.microsoft.projectoxford.emotion.contract.RecognizeResult;
import com.microsoft.projectoxford.emotion.rest.EmotionServiceException;
import com.microsoft.projectoxford.emotionsample.helper.ImageHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import static com.microsoft.projectoxford.emotionsample.R.id.result;

public class RecognizeActivity extends ActionBarActivity {

    // Flag to indicate which task is to be performed.
    private static final int REQUEST_SELECT_IMAGE = 0;

    // The button to select an image
    private Button mButtonSelectImage;

    // The URI of the image selected to detect.
    private Uri mImageUri;

    // The image selected to detect.
    private Bitmap mBitmap;

    // The edit to show status and result.
    private TextView mTextViewResults;

    private EmotionServiceClient client;


    // OSC Variables
    private String myIP = "192.168.1.173";
    private int myPort = 7400;

    // This is used to send messages
    private OSCPortOut oscPortOut;
    // Successful detection of emotion for an image
    private int successfulRequests = 0;
    private int oscMessagesSent = 0;
    private List<RecognizeResult> mRecognizeResult;

    // This thread will contain all the code that pertains to OSC
    private Thread oscThread = new Thread() {
        @Override
        public void run() {
            Log.d("MESSAGE", "running thread??");

            try {
                // Connect to some IP address and port
                oscPortOut = new OSCPortOut(InetAddress.getByName(myIP), myPort);
            } catch(UnknownHostException e) {
                // Error handling when your IP isn't found
                return;
            } catch(Exception e) {
                // Error handling for any other errors
                return;
            }

            while (true) {
                if (oscPortOut != null && successfulRequests != oscMessagesSent) {
                    oscMessagesSent += 1;

                    Object args[] = new Object[2];
                    RecognizeResult r = mRecognizeResult.get(0);
                    if (r.scores.happiness > r.scores.sadness) {
                        args[0] = 0;
                    } else {
                        args[0] = 1;
                    }


                    OSCMessage message = new OSCMessage("/emotion", Arrays.asList(args));


                    try {
                        // Send the messages
                        oscPortOut.send(message);
                    } catch (Exception e) {
                        Log.e("ERROR: oscPortOut.send", e.toString());
                        // Error handling for some error
                    }
                }

                try {
                    sleep(1000);
                } catch (Exception e) {
                    Log.e("ERROR: sleep", e.toString());
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognize);

        if (client == null) {
            client = new EmotionServiceRestClient(getString(R.string.subscription_key));
        }

        mButtonSelectImage = (Button) findViewById(R.id.buttonSelectImage);
        mTextViewResults = (TextView) findViewById(result);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_recognize, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void doRecognize() {
        mTextViewResults.setText("Processing request...");
        mButtonSelectImage.setEnabled(false);

        // Do emotion detection using auto-detected faces.
        try {
            new doRequest().execute();
        } catch (Exception e) {
            mTextViewResults.append("Error encountered. Exception is: " + e.toString());
        }
    }

    // Called when the "Select Image" button is clicked.
    public void selectImage(View view) {
        mTextViewResults.setText("");

        Intent intent;
        intent = new Intent(RecognizeActivity.this, com.microsoft.projectoxford.emotionsample.helper.SelectImageActivity.class);
        startActivityForResult(intent, REQUEST_SELECT_IMAGE);
    }

    // Called when image selection is done.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("RecognizeActivity", "onActivityResult");
        switch (requestCode) {
            case REQUEST_SELECT_IMAGE:
                if (resultCode == RESULT_OK) {
                    // If image is selected successfully, set the image URI and bitmap.
                    mImageUri = data.getData();

                    mBitmap = ImageHelper.loadSizeLimitedBitmapFromUri(
                            mImageUri, getContentResolver());
                    if (mBitmap != null) {
                        // Show the image on screen.
                        ImageView imageView = (ImageView) findViewById(R.id.selectedImage);
                        imageView.setImageBitmap(mBitmap);

                        // Add detection log.
                        Log.d("RecognizeActivity", "Image: " + mImageUri + " resized to " + mBitmap.getWidth()
                                + "x" + mBitmap.getHeight());

                        doRecognize();
                    }
                }
                break;
            default:
                break;
        }
    }


    private List<RecognizeResult> processWithAutoFaceDetection() throws EmotionServiceException, IOException {
        Log.d("emotion", "Start emotion detection with auto-face detection");

        Gson gson = new Gson();

        // Put the image into an input stream for detection.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        long startTime = System.currentTimeMillis();
        // -----------------------------------------------------------------------
        // KEY SAMPLE CODE STARTS HERE
        // -----------------------------------------------------------------------

        List<RecognizeResult> result = null;
        //
        // Detect emotion by auto-detecting faces in the image.
        //
        result = this.client.recognizeImage(inputStream);

        String json = gson.toJson(result);
        Log.d("result", json);

        Log.d("emotion", String.format("Detection done. Elapsed time: %d ms", (System.currentTimeMillis() - startTime)));
        // -----------------------------------------------------------------------
        // KEY SAMPLE CODE ENDS HERE
        // -----------------------------------------------------------------------
        return result;
    }

    private class doRequest extends AsyncTask<String, String, List<RecognizeResult>> {
        // Store error message
        private Exception e = null;

        @Override
        protected List<RecognizeResult> doInBackground(String... args) {
            try {
                return processWithAutoFaceDetection();
            } catch (Exception e) {
                this.e = e;    // Store error
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<RecognizeResult> result) {
            super.onPostExecute(result);
            // Display based on error existence

            mRecognizeResult = result;

            mTextViewResults.append("\n\nRecognizing emotions with auto-detected face rectangles...\n");
            if (e != null) {
                mTextViewResults.setText("Error: " + e.getMessage());
                this.e = null;
            } else {
                if (result.size() == 0) {
                    mTextViewResults.append("No emotion detected :(");
                } else {
                    Integer count = 0; // count of how many faces detected

                    // Covert bitmap to a mutable bitmap by copying it
                    Bitmap bitmapCopy = mBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    Canvas faceCanvas = new Canvas(bitmapCopy);
                    faceCanvas.drawBitmap(mBitmap, 0, 0, null);
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(5);
                    paint.setColor(Color.RED);

                    for (RecognizeResult r : result) {
                        mTextViewResults.append(String.format("\nFace #%1$d \n", count));
                        mTextViewResults.append(String.format("\t anger: %1$.5f\n", r.scores.anger));
                        mTextViewResults.append(String.format("\t contempt: %1$.5f\n", r.scores.contempt));
                        mTextViewResults.append(String.format("\t disgust: %1$.5f\n", r.scores.disgust));
                        mTextViewResults.append(String.format("\t fear: %1$.5f\n", r.scores.fear));
                        mTextViewResults.append(String.format("\t happiness: %1$.5f\n", r.scores.happiness));
                        mTextViewResults.append(String.format("\t neutral: %1$.5f\n", r.scores.neutral));
                        mTextViewResults.append(String.format("\t sadness: %1$.5f\n", r.scores.sadness));
                        mTextViewResults.append(String.format("\t surprise: %1$.5f\n", r.scores.surprise));
                        mTextViewResults.append(String.format("\t face rectangle: %d, %d, %d, %d", r.faceRectangle.left, r.faceRectangle.top, r.faceRectangle.width, r.faceRectangle.height));
                        faceCanvas.drawRect(r.faceRectangle.left,
                                r.faceRectangle.top,
                                r.faceRectangle.left + r.faceRectangle.width,
                                r.faceRectangle.top + r.faceRectangle.height,
                                paint);
                        count++;
                    }

                    successfulRequests += 1;
                    ImageView imageView = (ImageView) findViewById(R.id.selectedImage);
                    imageView.setImageDrawable(new BitmapDrawable(getResources(), mBitmap));
                }



                // start the oscThread
                if (!oscThread.isAlive()){
                    oscThread.start();
                }
            }

            mButtonSelectImage.setEnabled(true);
        }
    }
}
