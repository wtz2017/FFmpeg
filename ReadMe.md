# FFmpeg

FFmpeg study and Audio/Video Player base on FFmpeg + OpenSL ES + OpenGL ES + GLSurfaceView + MediaCodec.

此项目是在学习 CSDN 学院[杨万理](https://edu.csdn.net/lecturer/1846)的音视频课程基础上，加上自己的理解、查阅相关专业理论资料和一步步软件构思设计调试而成的，在此记录学习成果，也同时感谢杨老师的知识传播！

## ffmpegapi 模块

实现音视频解码播放的核心功能库，Java 层提供的主要接口类有：WePlayer、WeVideoView、WeEditor。

###  WePlayer

- 参考 MediaPlayer 生命周期基本实现了同系列播放接口；
- 音频采用 FFmpeg 软解得到 PCM 数据、OpenSL ES 播放 PCM 数据；
- 视频采用 FFmpeg 解封装、FFmpeg 软解码得到 YUV 数据 或 MediaCodec 硬解码 AVPacket 数据、OpenGL ES 和 GLSurfaceView 渲染纹理；
- 支持播放时声道（左声道、右声道、立体声道）、音量控制；
- 支持播放时声音分贝数的获取；
- 支持声音播放时变调或变速，原理是采用第三方库 SoundTouch 实现；
- 支持边播放边录制声音功能，录制音频可保存为 AAC、WAV 格式；
- 支持 http/https、HLS/RTMP/RTSP 播放；
- 支持 'armeabi','armeabi-v7a','arm64-v8a', 'x86','x86_64' 主流 ABI；

### WeVideoView

- 用于播放视频的 View，封装了 WePlayer；
- 原理是采用 OpenGL ES 和 GLSurfaceView 渲染 WePlayer 解码的视频图像纹理；

### WeEditor

- 用于编辑音视频；
- 目前实现了音频的异步裁减功能，可保存为 AAC 或 WAV 格式；

## app 模块

- 用于测试验证 ffmpegapi  提供的各个功能接口的 Demo；
- 效果图如下：
  - ![测试目录](https://github.com/wtz2017/FFmpeg/raw/master/images/ffmpeg-app-1.png)
  - ![音频播放](https://github.com/wtz2017/FFmpeg/raw/master/images/ffmpeg-app-2.png)
  - ![音频编辑](https://github.com/wtz2017/FFmpeg/raw/master/images/ffmpeg-app-3.png)
  - ![视频播放](https://github.com/wtz2017/FFmpeg/raw/master/images/ffmpeg-app-4.png)

## liveplay 模块

- 基于 ffmpegapi  模块实现的广播电台和电视直播小应用；
- 效果图如下：
  - ![广播电台列表](https://github.com/wtz2017/FFmpeg/raw/master/images/ffmpeg-liveplay-1.png)
  - ![电视直播列表](https://github.com/wtz2017/FFmpeg/raw/master/images/ffmpeg-liveplay-2.png)
  - ![广播电台播放-竖屏](https://github.com/wtz2017/FFmpeg/raw/master/images/ffmpeg-liveplay-3.png)
  - ![广播电台播放-横屏](https://github.com/wtz2017/FFmpeg/raw/master/images/ffmpeg-liveplay-4.png)
  - ![广播电台播放服务通知](https://github.com/wtz2017/FFmpeg/raw/master/images/ffmpeg-liveplay-5.png)
  - ![电视直播-1](https://github.com/wtz2017/FFmpeg/raw/master/images/ffmpeg-liveplay-6.png)
  - ![电视直播-2](https://github.com/wtz2017/FFmpeg/raw/master/images/ffmpeg-liveplay-7.png)
