package com.wtz.ffmpegapi;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.wtz.ffmpegapi.utils.LogUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AACEncoder implements PCMRecorder.Encoder {
    private static final String TAG = "AACEncoder";

    private MediaCodec mEncoder;
    private MediaFormat mFormat;
    private static final int AAC_BIT_RATE = 96000;
    private static final int AAC_PROFILE_LEVEL = MediaCodecInfo.CodecProfileLevel.AACObjectLC;

    private int mADTSHeaderLength;
    private int mADTSSampleRateIndex;
    private int mADTSChannelNums;
    // set protection to 1 if there is no CRC and 0 if there is CRC
    private static final int ADTS_PROTECTION = 1;
    private static final int ADTS_PROFILE = 2; // AAC LC

    private FileOutputStream mSaveStream;
    private MediaCodec.BufferInfo mBufferInfo;
    private byte[] mFrameBuffer = null;
    private int mLastFrameLength = 0;

    @Override
    public boolean start(int sampleRate, int channelNums, int bitsPerSample, int maxBytesPerCallback, File saveFile) {
        try {
            // create encoder
            mEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            if (mEncoder == null) {
                LogUtils.e(TAG, "MediaCodec create encoder failed!");
                return false;
            }

            // set format
            mFormat = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelNums);
            mFormat.setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE);
            mFormat.setInteger(
                    MediaFormat.KEY_AAC_PROFILE, AAC_PROFILE_LEVEL);
            mFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxBytesPerCallback);

            // start encoder
            mEncoder.configure(
                    mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mEncoder.start();

            // init other resource
            mSaveStream = new FileOutputStream(saveFile);
            mBufferInfo = new MediaCodec.BufferInfo();

            // protection_absent=0时, header length=9bytes
            // protection_absent=1时, header length=7bytes
            mADTSHeaderLength = (ADTS_PROTECTION == 1) ? 7 : 9;
            mADTSSampleRateIndex = getADTSSampleRateIndex(sampleRate);
            mADTSChannelNums = channelNums;

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void encode(byte[] pcmData, int size) {
        if (mEncoder == null) {
            LogUtils.e(TAG, "Call encode but mEncoder is null");
            return;
        }

        // 获取输入 buffer，不超时等待
        int inputBufferIndex = mEncoder.dequeueInputBuffer(0);
        if (inputBufferIndex < 0) {
            LogUtils.e(TAG, "mEncoder.dequeueInputBuffer failed");
            return;
        }

        // 成功获取输入 buffer后，填入要处理的数据
        ByteBuffer inputBuffer = mEncoder.getInputBuffers()[inputBufferIndex];
        inputBuffer.clear();
        inputBuffer.put(pcmData);
        // 填完输入数据后，释放输入 buffer
        mEncoder.queueInputBuffer(
                inputBufferIndex, 0, size, 0, 0);

        // 获取输出 buffer，不超时等待
        int ouputBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, 0);
        while (ouputBufferIndex >= 0) {// 可能一次获取不完，需要多次
            try {
                // 初始化 AACFrameBuffer
                int newAACFrameLength = mADTSHeaderLength + mBufferInfo.size;
                if (mFrameBuffer == null || newAACFrameLength > mLastFrameLength) {
                    mLastFrameLength = newAACFrameLength;
                    mFrameBuffer = null;// 先释放旧的 buffer
                    LogUtils.d(TAG, "new mFrameBuffer size: " + newAACFrameLength);
                    mFrameBuffer = new byte[newAACFrameLength];
                }

                // 添加 ADTS 头
                addADTSHeader(mFrameBuffer, newAACFrameLength);

                // 添加编码后的 AAC 数据
                ByteBuffer outputBuffer = mEncoder.getOutputBuffers()[ouputBufferIndex];
                outputBuffer.position(mBufferInfo.offset);
                outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);
                outputBuffer.get(mFrameBuffer, mADTSHeaderLength, mBufferInfo.size);
                outputBuffer.position(mBufferInfo.offset);

                // 保存到文件
                mSaveStream.write(mFrameBuffer, 0, newAACFrameLength);

                // 释放输出 buffer，并尝试获取下一个输出 buffer
                mEncoder.releaseOutputBuffer(ouputBufferIndex, false);
                ouputBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void stop() {
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }

        if (mSaveStream != null) {
            try {
                mSaveStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mSaveStream = null;
        }

        mFrameBuffer = null;
        mBufferInfo = null;
        mFormat = null;
    }

    private int getADTSSampleRateIndex(int samplerate) {
        int rate = 4;
        switch (samplerate) {
            case 96000:
                rate = 0;
                break;
            case 88200:
                rate = 1;
                break;
            case 64000:
                rate = 2;
                break;
            case 48000:
                rate = 3;
                break;
            case 44100:
                rate = 4;
                break;
            case 32000:
                rate = 5;
                break;
            case 24000:
                rate = 6;
                break;
            case 22050:
                rate = 7;
                break;
            case 16000:
                rate = 8;
                break;
            case 12000:
                rate = 9;
                break;
            case 11025:
                rate = 10;
                break;
            case 8000:
                rate = 11;
                break;
            case 7350:
                rate = 12;
                break;
        }
        return rate;
    }

    private void addADTSHeader(byte[] packet, int packetLen) {
        packet[0] = (byte) 0xFF; // 0xFFF(12bit) 这里只取了8位，所以还差4位放到下一个里面
        packet[1] = (byte) (0xF8 | ADTS_PROTECTION);
        packet[2] = (byte) (((ADTS_PROFILE - 1) << 6) + (mADTSSampleRateIndex << 2) + (mADTSChannelNums >> 2));
        packet[3] = (byte) (((mADTSChannelNums & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

}
