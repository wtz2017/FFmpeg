package com.wtz.ffmpegapi;

import android.util.Log;

import com.wtz.libmp3util.WeMp3Encoder;

import java.io.File;

public class MP3Encoder implements PCMRecorder.Encoder {
    private static final String TAG = MP3Encoder.class.getSimpleName();

    private WeMp3Encoder mWeMp3Encoder;
    private short[] shortBuffer = null;

    public MP3Encoder() {
        this.mWeMp3Encoder = new WeMp3Encoder();
    }

    @Override
    public boolean start(int sampleRate, int channelNums, int bitsPerSample, int maxBytesPerCallback, File saveFile) {
        Log.w(TAG, "start sampleRate=" + sampleRate + " channelNums=" + channelNums
        + " bitsPerSample=" + bitsPerSample + " saveFile=" + saveFile);
        return mWeMp3Encoder.startEncodePCMBuffer(sampleRate, channelNums, bitsPerSample, saveFile.getAbsolutePath());
    }

    @Override
    public void encode(byte[] pcmData, int size) {
        int shortSize = size / 2;
        if (shortBuffer == null || shortBuffer.length < shortSize) {
            shortBuffer = new short[shortSize];
        }
        for (int i = 0; i < shortSize; i++) {
            shortBuffer[i] = (short) ((pcmData[i * 2] & 0xff) | (pcmData[i * 2 + 1] & 0xff) << 8);
        }
        mWeMp3Encoder.encodeFromPCMBuffer(shortBuffer, shortSize);
    }

    @Override
    public void stop() {
        Log.w(TAG, "stop");
        mWeMp3Encoder.stopEncodePCMBuffer();
    }

}
