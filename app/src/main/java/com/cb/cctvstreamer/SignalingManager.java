package com.cb.cctvstreamer;

import io.socket.client.IO;
import io.socket.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URI;
import java.net.URISyntaxException;

public class SignalingManager {

    private static final String TAG = "SignalingManager";

    private Socket socket;
    private SignalingListener signalingListener;

    // Constructor to initialize the signaling server URL and listener
    public SignalingManager(SignalingListener listener) {
        this.signalingListener = listener;
    }

    // Initialize the WebSocket connection
    public void initializeSocket(String serverUrl) {
        try {
            socket = IO.socket(new URI(serverUrl));
            socket.connect();

            socket.on("offer", args -> {
                // Handle offer
                SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER, args[0].toString());
                signalingListener.onOfferReceived(sdp);
            });

            socket.on("answer", args -> {
                // Handle answer
                SessionDescription sdp = new SessionDescription(SessionDescription.Type.ANSWER, args[0].toString());
                signalingListener.onAnswerReceived(sdp);
            });

            socket.on("icecandidate", args -> {
                // Handle ICE candidates
                IceCandidate iceCandidate = new IceCandidate(args[0].toString(), (Integer) args[1], args[2].toString());
                signalingListener.onIceCandidateReceived(iceCandidate);
            });

            socket.on("disconnect", args -> {
                signalingListener.onDisconnected();
            });

        } catch (URISyntaxException e) {
            e.printStackTrace();
            signalingListener.onError(e.getMessage());
        }
    }

    // Send the offer to the signaling server
    public void sendOffer(SessionDescription sdp) throws JSONException {
        JSONObject offerMessage = new JSONObject();
        offerMessage.put("offer", sdp.description);
        socket.emit("offer", offerMessage);
    }

    // Send the answer to the signaling server
    public void sendAnswer(SessionDescription sdp) throws JSONException {
        JSONObject answerMessage = new JSONObject();
        answerMessage.put("answer", sdp.description);
        socket.emit("answer", answerMessage);
    }

    // Send ICE candidate to the signaling server
    public void sendIceCandidate(IceCandidate iceCandidate) throws JSONException {
        JSONObject candidateMessage = new JSONObject();
        candidateMessage.put("candidate", iceCandidate.sdp);
        candidateMessage.put("sdpMid", iceCandidate.sdpMid);
        candidateMessage.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
        socket.emit("icecandidate", candidateMessage);
    }

    // Disconnect the socket when done
    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
        }
    }

    // Interface to handle signaling events
    public interface SignalingListener {
        void onOfferReceived(SessionDescription offer);
        void onAnswerReceived(SessionDescription answer);
        void onIceCandidateReceived(IceCandidate iceCandidate);
        void onDisconnected();
        void onError(String error);
    }
}

