package com.cb.cctvstreamer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements SignalingManager.SignalingListener {

    private static final String TAG = "MainActivity";

    private WebRTCManager webRTCManager;
    private PreviewView previewView;
    private SurfaceViewRenderer localRenderer;
    private Button startStreamingButton;
    private TextView streamStatus;
    private boolean isStreaming = false;
    private static final int REQUEST_CODE_PERMISSIONS = 10;

    private SignalingManager signalingManager;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        signalingManager = new SignalingManager(this);
        signalingManager.initializeSocket("http://127.0.0.1:3000");

        previewView = findViewById(R.id.previewView);
        startStreamingButton = findViewById(R.id.startStreamingButton);
        streamStatus = findViewById(R.id.streamStatus);

        // Initialize WebRTCManager with a local renderer (SurfaceViewRenderer)
        localRenderer = findViewById(R.id.localRenderer);
        webRTCManager = new WebRTCManager(this, localRenderer);


        startStreamingButton.setOnClickListener(v -> {
            if (isStreaming) {
                stopStreaming();
            } else {
                startStreaming();
            }
        });

        if (allPermissionsGranted()) {
            initializeAndStartCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void initializeAndStartCamera() {
        webRTCManager.initializeCameraCapturer(); // Add this line
        webRTCManager.startLocalMediaCapture();

        webRTCManager.createPeerConnection();
        webRTCManager.createOffer();
        startCamera();
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                // Handle the case where permissions are not granted
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
                // Handle any errors (including cancellation) here.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        cameraProvider.bindToLifecycle(this, cameraSelector, preview);
    }

    private void startStreaming() {
        // Implement your streaming logic here
        isStreaming = true;
        streamStatus.setText(R.string.status_streaming);
        startStreamingButton.setText(R.string.stop_streaming);
        webRTCManager.createOffer();
    }

    private void stopStreaming() {
        // Implement your stop streaming logic here
        isStreaming = false;
        streamStatus.setText(R.string.status_idle);
        startStreamingButton.setText(R.string.start_streaming);
    }

    // Handle offer from the signaling server

    // Handle answer from the signaling server
    @Override
    public void onAnswerReceived(SessionDescription answer) {
        Log.d(TAG, "Received answer: " + answer.description);
        // Set remote description
        webRTCManager.setRemoteDescription(answer);
    }



    // Handle ICE candidate from the signaling server
    @Override
    public void onIceCandidateReceived(IceCandidate iceCandidate) {
        Log.d(TAG, "Received ICE candidate: " + iceCandidate.sdp);
        // Add ICE candidate
        webRTCManager.addIceCandidate(iceCandidate);
    }

    @Override
    public void onOfferReceived(SessionDescription offer) {
        webRTCManager.setRemoteDescription(offer);
        webRTCManager.createAnswer();

    }

    // Handle WebSocket disconnection
    @Override
    public void onDisconnected() {
        Log.d(TAG, "Disconnected from signaling server");
    }

    // Handle errors
    @Override
    public void onError(String error) {
        Log.e(TAG, "Error: " + error);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up WebRTC resources
        webRTCManager.cleanup();
        signalingManager.disconnect();
    }
}