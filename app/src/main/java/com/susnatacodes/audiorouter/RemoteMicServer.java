package com.susnatacodes.audiorouter;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class RemoteMicServer {
    private static final String TAG = "RemoteMicServer";
    private static final int MAX_PACKET_BYTES = 1500;

    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong packetCount = new AtomicLong(0);
    private final AtomicLong lastPacketAt = new AtomicLong(0);
    private DatagramSocket socket;
    private Thread thread;

    public RemoteMicServer(int port) {
        this.port = port;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        thread = new Thread(this::receiveLoop, "remote-mic-udp");
        thread.start();
    }

    public void stop() {
        running.set(false);
        if (socket != null) {
            socket.close();
            socket = null;
        }
        Thread localThread = thread;
        thread = null;
        if (localThread != null) {
            try {
                localThread.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public long getPacketCount() {
        return packetCount.get();
    }

    public long getLastPacketAt() {
        return lastPacketAt.get();
    }

    private void receiveLoop() {
        try {
            socket = new DatagramSocket(port);
            socket.setReceiveBufferSize(64 * 1024);
            byte[] buffer = new byte[MAX_PACKET_BYTES];
            while (running.get()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                packetCount.incrementAndGet();
                lastPacketAt.set(System.currentTimeMillis());

                // Expected future payload: small header + Opus frame or PCM frame.
                // Decode and pass the resulting PCM into AudioEngine for mixing.
            }
        } catch (SocketException e) {
            if (running.get()) {
                Log.e(TAG, "Remote mic socket failed", e);
            }
        } catch (IOException e) {
            Log.e(TAG, "Remote mic receive failed", e);
        } finally {
            running.set(false);
            if (socket != null) {
                socket.close();
                socket = null;
            }
        }
    }
}
