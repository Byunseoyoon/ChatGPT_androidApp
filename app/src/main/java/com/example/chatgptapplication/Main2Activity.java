package com.example.chatgptapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Main2Activity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private TextView recognitionTextView;
    private StringBuilder recognizedTextBuilder = new StringBuilder();
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "SpeechRecognition";
    private static final String API_KEY = "";

    // 음성 출력이 끝났을 때만 음성 인식을 다시 시작하도록
    private boolean isSpeaking = false;

    // 시스템 메시지: 모델의 페르소나 설정
    JSONObject systemMessage = new JSONObject();
    // 대화 내역을 저장 할 리스트
    private List<JSONObject> conversationHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        EdgeToEdge.enable(this);
//        setContentView(R.layout.activity_example);
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });

        // 시스템 메세지, 초기 페르 소나 설정
        try{
            systemMessage.put("role", "system");
            systemMessage.put("content", "나는 면접 보러 온 사람이고, 너는 회사의 침착하고 분석적인 면접관이야 대신 답변또는 피드백을 요점만 정확히 간결하게 말해줘 ");
            conversationHistory.add(systemMessage);
            Log.d("conversationHistory", "onCreate: "+ systemMessage);
        }catch (JSONException e){
            e.printStackTrace();
        }

        //UI 설정
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

        //TTS 초기화
        textToSpeech = new TextToSpeech(this, this);

        // 음성 인식 권한 확인 및 요청
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
                    //new SendQuestionTask().execute(recognizedText);
                    sendTextToGPT(recognizedText);                }
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

        // textToSpeech 리스너 설정
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                isSpeaking = true;
            }

            @Override
            public void onDone(String utteranceId) {
                isSpeaking = false;

                // 일정시간 지연 후 음성인식 시작
                new Handler(getMainLooper()).postDelayed(()->startListening(), 1000);
            }

            @Override
            public void onError(String utteranceId) {
                isSpeaking = false;
                new Handler(getMainLooper()).postDelayed(()-> startListening(), 1000);
            }
        });

    }

    private void startListening() {
        if(!isSpeaking) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            speechRecognizer.startListening(intent);
            updateRecognitionText("음성 인식 시작...");
        }
    }

    private void updateRecognitionText(String text) {
        recognitionTextView.setText(text + "\n\n" + recognizedTextBuilder.toString());
    }

    private void addRecognizedText(String text) {
        recognizedTextBuilder.insert(0, "You: " + text + "\n\n");
        updateRecognitionText("최근 인식된 텍스트:");
    }

//    private class SendQuestionTask extends AsyncTask<String, Void, String> {
//        @Override
//        protected String doInBackground(String... params) {
//            String question = params[0];
//            String response = "";
//            try {
//                URL url = new URL(API_URL + question);
//                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//                conn.setRequestMethod("GET");
//
//                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//                StringBuilder stringBuilder = new StringBuilder();
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    stringBuilder.append(line);
//                }
//                reader.close();
//                response = stringBuilder.toString();
//            } catch (Exception e) {
//                Log.e(TAG, "Error sending question to API", e);
//            }
//            return response;
//        }
//
//        @Override
//        protected void onPostExecute(String result) {
//            try {
//                JSONObject jsonObject = new JSONObject(result);
//                String apiResponse = jsonObject.getString("response");
//                recognizedTextBuilder.insert(0, "API: " + apiResponse + "\n\n");
//                updateRecognitionText("API 응답:");
//                speakText(apiResponse);
//            } catch (JSONException e) {
//                Log.e(TAG, "Error parsing JSON", e);
//                updateRecognitionText("JSON 파싱 오류: " + e.getMessage());
//            }
//        }
//    }

    //GPT에 텍스트 전송 및 응답 받기
    private void sendTextToGPT(String text){
        OkHttpClient client = new OkHttpClient();

        // 사용자의 메시지를 대화 내역(history)에 추가
        try {
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", text);
            conversationHistory.add(userMessage); // 사용자 메시지 추가
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 최근 N개의 메시지만 유지하고 시스템 메시지는 항상 첫 번째로 유지
        limitConversationHistory(10);

        // 요청할 JSON 데이터 만들기
        JSONObject jsonObject = new JSONObject();
        try{
            jsonObject.put("model", "gpt-3.5-turbo");

            // messages 배열 생성
            JSONArray messages = new JSONArray(conversationHistory);
            jsonObject.put("messages", messages);  // 대화내역을 포함한 messages 배열 추가
            jsonObject.put("max_tokens", 150);      // 응답 길이 설정
        }catch (JSONException e){
            e.printStackTrace();
        }

        //JSON 데이터를 RequestBody로 설정
        RequestBody body = RequestBody.create(
                jsonObject.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        // OpenAI API에 요청 보낼 준비
        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")  // 엔드 포인트
                .header("Authorization", "Bearer "+API_KEY)
                .post(body)
                .build();

        // 요청 실행 및 응답 처리
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                updateRecognitionText("GPT onFailure: " + e.getMessage());
                Log.d("GPT ERROR", "onResponse: 응답을 받을 수 없습니다.");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if(response.isSuccessful() && response.body() !=null){
                    String responseData = response.body().string();
                    Log.d("GPT", "onResponse: "+responseData);
                    try {
                        // 응답 JSON에서 텍스트 부분 추출
                        JSONObject jsonResponse = new JSONObject(responseData);
                        String gptResponse = jsonResponse
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                                .trim();

                        // 모델의 응답을 대화 내역에 추가
                        JSONObject assistantMessage = new JSONObject();
                        assistantMessage.put("role", "assistant");
                        assistantMessage.put("content", gptResponse);
                        conversationHistory.add(assistantMessage); // 모델 응답 추가

                        //GPT 응답을 UI에 표시
                        updateRecognitionText("API 응답:");
                        speakText(gptResponse);

                    }catch (JSONException e){
                        e.printStackTrace();
                        updateRecognitionText("JSON 파싱 오류: " + e.getMessage());
                    }
                }else {
                    Log.d("GPT", "onResponse fail: "+response.body().string());
                    updateRecognitionText("GPT 응답 실패");
                }
            }
        });
    }

    // 대화 내역 제한 함수
    private void limitConversationHistory(int limit){
        // 시스템 메시지가 있는 경우 첫 번째로 유지 하고 나머지 최근 N 개의 메세지만 유지
        if(conversationHistory.size() >limit+1){
            conversationHistory = new ArrayList<>(conversationHistory.subList(conversationHistory.size() - limit, conversationHistory.size()));

            // 시스템 메세지를 첫 번째로 추가
            conversationHistory.add(0, systemMessage);
        }
    }

    private void speakText(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TTS_OUTPUT");
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