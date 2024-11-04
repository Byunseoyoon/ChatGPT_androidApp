package com.example.chatgptapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class Example extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private TextView recognitionTextView;
    private StringBuilder recognizedTextBuilder = new StringBuilder();
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "SpeechRecognition";
    private static final String API_URL = "http://211.37.178.59:5000/ask?question=";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        recognitionTextView = new TextView(this);
        recognitionTextView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        recognitionTextView.setText("음성 인식 준비 중...");

        scrollView.addView(recognitionTextView);
        mainLayout.addView(scrollView);
        setContentView(mainLayout);

        textToSpeech = new TextToSpeech(this, this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
        } else {
            initializeSpeechRecognizer();
        }
    }

    private void initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                updateRecognitionText("음성 인식 준비 완료");
            }

            @Override
            public void onBeginningOfSpeech() {
                updateRecognitionText("음성 입력 시작");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                updateRecognitionText("음성 입력 종료");
            }

            @Override
            public void onError(int error) {
                updateRecognitionText("오류 발생: " + error);
                Log.e(TAG, "Error: " + error);
                startListening();
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    addRecognizedText(recognizedText);
                    Log.d(TAG, "Final Result: " + recognizedText);
                    new SendQuestionTask().execute(recognizedText);
                }
                startListening();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    updateRecognitionText("부분 인식: " + recognizedText);
                    Log.d(TAG, "Partial Result: " + recognizedText);
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });

        startListening();
    }

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizer.startListening(intent);
        updateRecognitionText("음성 인식 시작...");
    }

    private void updateRecognitionText(String text) {
        recognitionTextView.setText(text + "\n\n" + recognizedTextBuilder.toString());
    }

    private void addRecognizedText(String text) {
        recognizedTextBuilder.insert(0, "You: " + text + "\n\n");
        updateRecognitionText("최근 인식된 텍스트:");
    }

    private class SendQuestionTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String question = params[0];
            String response = "";
            try {
                URL url = new URL(API_URL + question);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                reader.close();
                response = stringBuilder.toString();
            } catch (Exception e) {
                Log.e(TAG, "Error sending question to API", e);
            }
            return response;
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                JSONObject jsonObject = new JSONObject(result);
                String apiResponse = jsonObject.getString("response");
                recognizedTextBuilder.insert(0, "API: " + apiResponse + "\n\n");
                updateRecognitionText("API 응답:");
                speakText(apiResponse);
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing JSON", e);
                updateRecognitionText("JSON 파싱 오류: " + e.getMessage());
            }
        }
    }

    private void speakText(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.getDefault());
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported");
            }
        } else {
            Log.e(TAG, "TTS Initialization failed");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeSpeechRecognizer();
        } else {
            updateRecognitionText("음성 인식 권한이 필요합니다.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}
