package com.example.chatgptapplication;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private SpeechRecognizer speechRecognizer;
    private TextView tvResult;
    private TextView tvResponse;
    private static final int REQUEST_CODE_SPEECH_INPUT = 1;

    //OpenAI API 키 설정
    private static final String API_KEY = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button btnSpeak = findViewById(R.id.btnSpeak);
        tvResult = findViewById(R.id.tvResult);
        tvResponse = findViewById(R.id.tvResponse);

        // 버튼 클릭 시 음성 인식 시작
        btnSpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSpeechToText();
            }
        });
    }

    // 음성 인식 시작
    private void startSpeechToText() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN); // 한국어 설정
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀하세요"); // 사용자에게 표시될 프롬프트 텍스트

        try {
            // 음성 인식 액티비티 시작
            //startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
            // 음성 인식된 텍스트를 GPT에 전송
            sendTextToGPT("안녕하십니까");
        } catch (Exception e) {
            e.printStackTrace();
            tvResult.setText("음성 인식을 사용할 수 없습니다.");
        }
    }

    // 음성 인식 결과 처리
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {    // 결과가 NULL 이나 비어있지 않는다면
                String spokenText = results.get(0); // 인식된 텍스트 가져오기
                tvResult.setText(spokenText); // 텍스트뷰에 표시

                // 음성 인식된 텍스트를 GPT에 전송
                sendTextToGPT(spokenText);
            } else {
                tvResult.setText("음성 인식에 실패했습니다.");
            }
        }
    }

    //GPT에 텍스트 전송 및 응답 받기
    private void sendTextToGPT(String text){
        OkHttpClient client = new OkHttpClient();

        // 요청할 JSON 데이터 만들기
        JSONObject jsonObject = new JSONObject();
        try{
            jsonObject.put("model", "gpt-3.5-turbo");

            // messages 배열 생성
            JSONArray messages = new JSONArray();

            // 시스템 메시지: 모델의 페르소나 설정
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "나는 면접 보러 온 사람이고, 너는 회사의 침착하고 분석적인 면접관이야");
            messages.put(systemMessage);

            // 사용자 메세지
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", text);  // 질문 텍스트
            messages.put(userMessage);

            jsonObject.put("messages", messages);  // messages 배열 추가
            jsonObject.put("max_tokens", 100);      // 응답 길이 설정
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
                runOnUiThread(()-> tvResponse.setText("응답을 받을 수 없습니다."));
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

                        //GPT 응답을 UI에 표시
                        runOnUiThread(()->tvResponse.setText(gptResponse));
                    }catch (JSONException e){
                        e.printStackTrace();
                        runOnUiThread(()->tvResponse.setText("응답 파싱 실패"));
                    }
                }else {
                    Log.d("GPT", "onResponse fail: "+response.body().string());
                    runOnUiThread(()->tvResponse.setText("GPT 응답 실패"));
                }
            }
        });
    }
}