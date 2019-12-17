package com.wtz.ffmpegapi;

import com.wtz.ffmpegapi.utils.LogUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WAVSaver implements PCMRecorder.Encoder {

    private static final String TAG = "WAVSaver";

    private Header mHeader;
    private File mSaveFile;
    private FileOutputStream mSaveStream;
    private int mPcmDataSize;

    @Override
    public boolean start(int sampleRate, int channelNums, int bitsPerSample, int maxBytesPerCallback, File saveFile) {
        LogUtils.d(TAG, "start sampleRate=" + sampleRate + ",channelNums=" + channelNums
                + ",bitsPerSample=" + bitsPerSample + ",saveFile=" + saveFile);
        try {
            mSaveStream = new FileOutputStream(saveFile);
            mSaveFile = saveFile;
            mHeader = new Header(sampleRate, channelNums, bitsPerSample);
            mHeader.writeHeader(mSaveStream);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    @Override
    public void encode(byte[] pcmData, int size) {
        if (mSaveStream == null) {
            LogUtils.e(TAG, "Call encode but mSaveStream is null");
            return;
        }

        try {
            mSaveStream.write(pcmData, 0, size);
            mPcmDataSize += size;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        LogUtils.d(TAG, "stop...");
        if (mSaveStream != null) {
            try {
                mSaveStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mSaveStream = null;
        }

        if (mHeader != null && mSaveFile != null) {
            mHeader.updateDataSize(mSaveFile, mPcmDataSize);
            mHeader = null;
            mSaveFile = null;
        }
    }

    class Header {

        public static final int HEADER_SIZE = 44;// chunk + subchunk1 + subchunk2

        public static final int RIFF_CHUNKSIZE_OFFSET = 4;
        public static final int FMT_SUBCHUNKSIZE1_OFFSET = 16;
        public static final int DATA_SUBCHUNKSIZE2_OFFSET = 40;

        // ----------------- header start -------------------
        // RIFF chunk
        public static final String CHUNK_ID = "RIFF";
        public int mChunkSize = 0;// headerSize + dataSize - 8 (整个文件的长度减去本区块 ID 和 Size 的长度)
        public static final String FORMAT = "WAVE";

        // fmt subchunk1
        public static final String SUB_CHUNK1_ID = "fmt ";// 注意这里末尾有一个空格
        public static final int SUB_CHUNK1_SIZE = 16;// 该区块数据不包含 ID 和 Size 的长度
        public static final short AUDIO_FORMAT = 1;// 1: PCM 类型
        public short mNumChannel = 1;// 声道数
        public int mSampleRate = 8000;// 采样率
        public int mByteRate = 0;// 每秒数据字节数 = SampleRate * NumChannels * BitsPerSample / 8
        public short mBlockAlign = 0;// 数据块的对齐数 = 一次采样所有声道所需的字节数 = NumChannels * BitsPerSample / 8
        public short mBitsPerSample = 8;// 每个声道每个采样存储的 bit 数

        // data subchunk2
        public static final String SUB_CHUNK2_ID = "data";
        public int mSubChunk2Size = 0;// ByteRate * seconds
        // ----------------- header end -------------------

        public Header(int sampleRateInHz, int channels, int bitsPerSample) {
            mSampleRate = sampleRateInHz;
            mNumChannel = (short) channels;
            mBitsPerSample = (short) bitsPerSample;
            mByteRate = mSampleRate * mNumChannel * mBitsPerSample / 8;
            mBlockAlign = (short) (mNumChannel * mBitsPerSample / 8);
        }

        public void writeHeader(FileOutputStream outputStream) {
            try {
                // 顺序不可动
                outputStream.write(CHUNK_ID.getBytes("UTF-8"));
                outputStream.write(intToByteArray(mChunkSize), 0, 4);
                outputStream.write(FORMAT.getBytes("UTF-8"));
                outputStream.write(SUB_CHUNK1_ID.getBytes("UTF-8"));
                outputStream.write(intToByteArray(SUB_CHUNK1_SIZE), 0, 4);
                outputStream.write(shortToByteArray(AUDIO_FORMAT), 0, 2);
                outputStream.write(shortToByteArray(mNumChannel), 0, 2);
                outputStream.write(intToByteArray(mSampleRate), 0, 4);
                outputStream.write(intToByteArray(mByteRate), 0, 4);
                outputStream.write(shortToByteArray(mBlockAlign), 0, 2);
                outputStream.write(shortToByteArray(mBitsPerSample), 0, 2);
                outputStream.write(SUB_CHUNK2_ID.getBytes("UTF-8"));
                outputStream.write(intToByteArray(mSubChunk2Size), 0, 4);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void updateDataSize(File waveFile, int dataSize) {
            try {
                RandomAccessFile wavFile = new RandomAccessFile(waveFile, "rw");
                wavFile.seek(RIFF_CHUNKSIZE_OFFSET);
                wavFile.write(intToByteArray((HEADER_SIZE + dataSize - 8)), 0, 4);
                wavFile.seek(DATA_SUBCHUNKSIZE2_OFFSET);
                wavFile.write(intToByteArray((dataSize)), 0, 4);
                wavFile.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private byte[] intToByteArray(int data) {
            return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data).array();
        }

        private byte[] shortToByteArray(short data) {
            return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(data).array();
        }

    }

}
