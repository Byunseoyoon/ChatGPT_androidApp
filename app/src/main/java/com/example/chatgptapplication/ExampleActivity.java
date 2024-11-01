package com.example.chatgptapplication;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Locale;

public class ExampleActivity extends AppCompatActivity {
    private SpeechRecognizer speechRecognizer;
    private TextView tvResult;
    private TextView tvResponse;
    private static final int REQUEST_CODE_SPEECH_INPUT = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_example);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button btnSpeak = findViewById(R.id.btn_Speak);
        tvResult = findViewById(R.id.tv_Result);
        tvResponse = findViewById(R.id.tv_Response);

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


    }







}