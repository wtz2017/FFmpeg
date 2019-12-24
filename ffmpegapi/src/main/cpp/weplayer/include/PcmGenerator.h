//
// Created by WTZ on 2019/11/25.
//

#ifndef FFMPEG_PCMGENERATOR_H
#define FFMPEG_PCMGENERATOR_H


class PcmGenerator {

public:
    PcmGenerator() {}

    virtual ~PcmGenerator() {}

    /**
     * 提供 PCM 数据
     *
     * @param buf 外部调用者用来接收 PCM 数据的 buffer
     * @return 实际返回的数据字节大小
     */
    virtual int getPcmData(void **buffer) = 0;

    /**
     * @return 声道数量
     */
    virtual int getChannelNums() = 0;

    /**
     * @return 采样率
     */
    virtual int getSampleRate() = 0;

    /**
     * @return 每个声道每次采样比特位数
     */
    virtual int getBitsPerSample() = 0;

};


#endif //FFMPEG_PCMGENERATOR_H
