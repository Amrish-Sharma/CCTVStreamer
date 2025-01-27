package com.cb.cctvstreamer;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.webrtc.*;

import java.util.ArrayList;
import java.util.List;

public class WebRTCManager {

    private final Context context;
    private static final String TAG = "WebRTCManager";
    private PeerConnectionFactory peerConnectionFactory;
    private EglBase rootEglBase;
    private SurfaceViewRenderer localRenderer;
    private VideoTrack localVideoTrack;
    private PeerConnection peerConnection;
    private VideoCapturer videoCapturer;

    // ICE Servers (you can replace with your own STUN/TURN servers)
    private List<PeerConnection.IceServer> iceServers;

    private SignalingManager signalingManager;

    public WebRTCManager(Context context, SurfaceViewRenderer localRenderer) {
        this.context = context;
        this.localRenderer = localRenderer;

        // Initialize the WebRTC components
        initializeWebRTC(context);
        initializeIceServers();
//        initializeCameraCapturer();
    }

    // Initialize the WebRTC components: PeerConnectionFactory and EglBase
    private void initializeWebRTC(Context context) {
        // Initialize PeerConnectionFactory
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions());

        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        // Initialize EglBase for rendering the camera preview
        rootEglBase = EglBase.create();

        // Set up the local video renderer
        localRenderer.init(rootEglBase.getEglBaseContext(), null);
    }

    // Initialize ICE servers (STUN/TURN servers)
    private void initializeIceServers() {
        iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        // You can add TURN servers here if needed
    }

    // Initialize Camera Capturer
    void initializeCameraCapturer() {
        videoCapturer = createCameraCapturer(new Camera2Enumerator(context));
        if (videoCapturer == null) {
            throw new RuntimeException("Failed to initialize CameraCapturer");
        }
    }

    // Create VideoTrack and start capturing local media
    public void startLocalMediaCapture() {
        if (videoCapturer == null) {
            throw new RuntimeException("CameraCapturer must be initialized before calling startCapture");
        }

        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        // Add local video track to the renderer
        localVideoTrack.addSink(localRenderer);

        // Start the camera capture
        videoCapturer.startCapture(1280, 720, 30);
    }

    // Create Camera Capturer
    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        String[] deviceNames = enumerator.getDeviceNames();
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            }
        }
        // Fallback to a back-facing camera if no front camera is found
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            }
        }
        return null;
    }

    // Create and configure PeerConnection
    public void createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        // Create PeerConnection
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                // Handle ICE candidate (send to remote peer)
                Log.d(TAG, "onIceCandidate: " + iceCandidate.toString());
                try {
                    signalingManager.sendIceCandidate(iceCandidate);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to send ICE candidate: " + e.getMessage());
                }
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

            @Override
            public void onAddStream(MediaStream mediaStream) {
                // Handle remote stream if required
                Log.d(TAG, "onAddStream: " + mediaStream.toString());
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
                // Handle removed stream
                Log.d(TAG, "onRemoveStream: " + mediaStream.toString());
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {}

            @Override
            public void onRenegotiationNeeded() {}

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
                // Handle ICE connection state changes
                Log.d(TAG, "onIceConnectionChange: " + newState);
            }

            @Override
            public void onIceConnectionReceivingChange(boolean receiving) {}

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}

            @Override
            public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {}

            @Override
            public void onRemoveTrack(RtpReceiver receiver) {}

            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange: " + signalingState);
            }
        });

        // Add local video track to the peer connection
        peerConnection.addTrack(localVideoTrack);
    }

    // Create SDP Offer (signaling will be handled here)
    public void createOffer() {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection is not initialized.");
            return;
        }

        // Create an SDP offer
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                Log.d(TAG, "Offer created: " + sdp);
                peerConnection.setLocalDescription(this, sdp);
                try {
                    // Send offer SDP to the remote peer (via signaling)
                    signalingManager.sendOffer(sdp);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to send offer: " + e.getMessage());
                }
            }

            @Override
            public void onSetSuccess() {}

            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Offer creation failed: " + error);
            }

            @Override
            public void onSetFailure(String error) {
                Log.e(TAG, "Set SDP failed: " + error);
            }
        }, new MediaConstraints());
    }

    // Set the remote SDP (offer/answer)
    public void setRemoteDescription(SessionDescription sdp) {
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {}

            @Override
            public void onSetSuccess() {
                Log.d(TAG, "Remote SDP set successfully.");
            }

            @Override
            public void onCreateFailure(String error) {}

            @Override
            public void onSetFailure(String error) {
                Log.e(TAG, "Setting remote SDP failed: " + error);

            }
        }, sdp);
    }

    // Add ICE Candidate to the peer connection
    public void addIceCandidate(IceCandidate iceCandidate) {
        peerConnection.addIceCandidate(iceCandidate);
    }

    // Cleanup
    public void cleanup() {
        if (peerConnection != null) {
            peerConnection.close();
        }
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
        }
    }

    public void createAnswer() {
        if (peerConnection == null) {
            Log.e(TAG, "PeerConnection is not initialized.");
            return;
        }

        // Create an SDP answer
        peerConnection.createAnswer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                Log.d(TAG, "Answer created: " + sdp);
                peerConnection.setLocalDescription(this, sdp);
                try {
                    // Send answer SDP to the remote peer (via signaling)
                    signalingManager.sendAnswer(sdp);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to send answer: " + e.getMessage());
                }
            }

            @Override
            public void onSetSuccess() {}

            @Override
            public void onCreateFailure(String error) {
                Log.e(TAG, "Answer creation failed: " + error);
            }

            @Override
            public void onSetFailure(String error) {
                Log.e(TAG, "Set SDP failed: " + error);
            }
        }, new MediaConstraints());
    }
}