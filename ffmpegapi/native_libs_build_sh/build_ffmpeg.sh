#!/bin/bash

# 本脚本在 ffmpeg-3.4.6、4.0.5 测试通过
# 编译：chmod 777 build_ffmpeg.sh && sudo ./build_ffmpeg.sh
# 建议执行脚本时切换到 root 用户或加 sudo，否则在编译时报权限错误
# 适配 ffmpeg-4.0.5 版本时，去除 --disable-ffserver 选项，否则 configure 失败，无法生成自定义安装目录 android

export NDK_HOME=/home/ubuntu/ndk/android-ndk-r14b/
export PLATFORM_VERSION=android-9

function build
{
    echo "start build ffmpeg for $ARCH"
    ./configure --target-os=android \
    --prefix=$PREFIX --arch=$ARCH \
    --disable-doc \
    --enable-shared \
    --disable-static \
    --disable-yasm \
    --disable-asm \
    --disable-symver \
    --enable-gpl \
    --disable-ffmpeg \
    --disable-ffplay \
    --disable-ffprobe \
    --cross-prefix=$CROSS_COMPILE \
    --enable-cross-compile \
    --sysroot=$SYSROOT \
    --enable-small \
    --extra-cflags="-Os -fpic $ADDI_CFLAGS" \
    --extra-ldflags="$ADDI_LDFLAGS" \
    $ADDITIONAL_CONFIGURE_FLAG
    echo -e "====== configure finished ======\n"

    make clean
    echo -e "====== clean finished ======\n"

    make -j4
    echo -e "====== make finished ======\n"

    make install
    echo -e "====== install finished ======\n"
    echo -e "build ffmpeg for $ARCH finished"
}

#arm
ARCH=arm
CPU=arm
PREFIX=$(pwd)/android/$ARCH
TOOLCHAIN=$NDK_HOME/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
CROSS_COMPILE=$TOOLCHAIN/bin/arm-linux-androideabi-
ADDI_CFLAGS="-marm"
SYSROOT=$NDK_HOME/platforms/$PLATFORM_VERSION/arch-$ARCH/
build

#x86
ARCH=x86
CPU=x86
PREFIX=$(pwd)/android/$ARCH
TOOLCHAIN=$NDK_HOME/toolchains/x86-4.9/prebuilt/linux-x86_64
CROSS_COMPILE=$TOOLCHAIN/bin/i686-linux-android-
ADDI_CFLAGS="-march=i686 -mtune=intel -mssse3 -mfpmath=sse -m32"
SYSROOT=$NDK_HOME/platforms/$PLATFORM_VERSION/arch-$ARCH/
build