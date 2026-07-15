package com.susnatacodes.audiorouter;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class PcmWaveRecorder {
    private static final String TAG = "PcmWaveRecorder";
    private static final int CHANNELS = 2;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int HEADER_BYTES = 44;

    private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(96);
    private volatile boolean running;
    private Thread writerThread;
    private RandomAccessFile output;
    private long pcmBytesWritten;
    private File currentFile;

    public synchronized void start(Context context) {
        if (running) {
            return;
        }
        try {
            File directory = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            if (directory == null) {
                directory = context.getFilesDir();
            }
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Unable to create recording directory");
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            currentFile = new File(directory, "susnatacodes-mix-" + stamp + ".wav");
            output = new RandomAccessFile(currentFile, "rw");
            output.setLength(0);
            writeHeader(output, 0);
            pcmBytesWritten = 0;
            queue.clear();
            running = true;
            writerThread = new Thread(this::writerLoop, "router-wav-recorder");
            writerThread.start();
        } catch (IOException e) {
            Log.e(TAG, "Unable to start WAV recording", e);
            closeQuietly();
        }
    }

    public void write(short[] stereoPcm, int frames) {
        if (!running || frames <= 0) {
            return;
        }
        int samples = frames * CHANNELS;
        byte[] bytes = new byte[samples * 2];
        int byteIndex = 0;
        for (int index = 0; index < samples && index < stereoPcm.length; index++) {
            short sample = stereoPcm[index];
            bytes[byteIndex++] = (byte) (sample & 0xff);
            bytes[byteIndex++] = (byte) ((sample >> 8) & 0xff);
        }
        queue.offer(bytes);
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        Thread localThread = writerThread;
        writerThread = null;
        if (localThread != null) {
            try {
                localThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            if (output != null) {
                output.seek(0);
                writeHeader(output, pcmBytesWritten);
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to finalize WAV recording", e);
        } finally {
            closeQuietly();
        }
    }

    public synchronized boolean isRecording() {
        return running;
    }

    public synchronized File getCurrentFile() {
        return currentFile;
    }

    private void writerLoop() {
        while (running || !queue.isEmpty()) {
            try {
                byte[] chunk = queue.poll(100, TimeUnit.MILLISECONDS);
                if (chunk != null && output != null) {
                    output.write(chunk);
                    pcmBytesWritten += chunk.length;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                Log.e(TAG, "Recording write failed", e);
                break;
            }
        }
    }

    private void closeQuietly() {
        try {
            if (output != null) {
                output.close();
            }
        } catch (IOException ignored) {
            // Nothing useful to do while shutting down recording.
        }
        output = null;
        running = false;
    }

    private static void writeHeader(RandomAccessFile file, long pcmBytes) throws IOException {
        long totalDataLen = pcmBytes + 36;
        long byteRate = (long) AudioEngine.SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8;

        file.writeBytes("RIFF");
        writeInt(file, (int) totalDataLen);
        file.writeBytes("WAVE");
        file.writeBytes("fmt ");
        writeInt(file, 16);
        writeShort(file, (short) 1);
        writeShort(file, (short) CHANNELS);
        writeInt(file, AudioEngine.SAMPLE_RATE);
        writeInt(file, (int) byteRate);
        writeShort(file, (short) (CHANNELS * BITS_PER_SAMPLE / 8));
        writeShort(file, (short) BITS_PER_SAMPLE);
        file.writeBytes("data");
        writeInt(file, (int) pcmBytes);
    }

    private static void writeInt(RandomAccessFile file, int value) throws IOException {
        file.write(value & 0xff);
        file.write((value >> 8) & 0xff);
        file.write((value >> 16) & 0xff);
        file.write((value >> 24) & 0xff);
    }

    private static void writeShort(RandomAccessFile file, short value) throws IOException {
        file.write(value & 0xff);
        file.write((value >> 8) & 0xff);
    }
}
