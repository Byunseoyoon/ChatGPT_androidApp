package com.example.chatgptapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;
import com.google.mlkit.vision.pose.PoseLandmark;
import java.util.concurrent.ExecutionException;

public class SquatCountActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private PreviewView previewView;
    private TextView squatCountTextView;
    private int squatCount = 0;
    private boolean isSquatting = false;
    private PoseDetector poseDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_squat_count);

        // PreviewView 및 TextView 설정
        previewView = findViewById(R.id.previewView);
        squatCountTextView = findViewById(R.id.squatCountTextView);

        // 포즈 인식을 위한 ML Kit 설정
        AccuratePoseDetectorOptions options = new AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        // 카메라 권한 확인 및 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                // 권한 거부 시 처리
                Log.e("CameraPermission", "Camera permission denied");
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
            processImageProxy(imageProxy);
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        if (imageProxy.getImage() != null) {
            InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
            poseDetector.process(image)
                    .addOnSuccessListener(pose -> {
                        checkSquatPose(pose);
                    })
                    .addOnFailureListener(e -> Log.e("PoseDetection", "Failed to detect pose: " + e))
                    .addOnCompleteListener(task -> {
                        imageProxy.close();
                    });
        } else {
            imageProxy.close();
        }
    }

    private void checkSquatPose(Pose pose) {
        // 포즈에서 필요한 포인트 가져오기
        PoseLandmark leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE);
        PoseLandmark leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE);

        if (leftHip != null && leftKnee != null && leftAnkle != null) {
            // 무릎과 엉덩이의 각도를 계산해 스쿼트 동작을 감지
            double angle = getAngle(leftHip, leftKnee, leftAnkle);
            if (angle < 90) {
                // 무릎이 굽혀졌을 때 스쿼트 중으로 간주
                isSquatting = true;
            } else if (isSquatting && angle > 160) {
                // 무릎이 펴졌을 때 스쿼트 완료로 간주하고 카운트 증가
                squatCount++;
                isSquatting = false;
                updateSquatCountOnScreen();
            }
        }
    }

    private double getAngle(PoseLandmark firstPoint, PoseLandmark middlePoint, PoseLandmark lastPoint) {
        double result = Math.toDegrees(
                Math.atan2(lastPoint.getPosition().y - middlePoint.getPosition().y,
                        lastPoint.getPosition().x - middlePoint.getPosition().x) -
                        Math.atan2(firstPoint.getPosition().y - middlePoint.getPosition().y,
                                firstPoint.getPosition().x - middlePoint.getPosition().x));
        result = Math.abs(result);
        if (result > 180) {
            result = 360.0 - result;
        }
        return result;
    }

    private void updateSquatCountOnScreen() {
        squatCountTextView.setText("스쿼트 횟수: " + squatCount);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        poseDetector.close();
    }
}
