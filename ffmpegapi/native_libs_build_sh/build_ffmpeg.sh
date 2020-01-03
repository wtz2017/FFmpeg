#!/bin/bash

# 以下在 ffmpeg-3.4.6 测试通过
# 在 ~/ffmpeg/ffmpeg-<version>/ 目录下创建脚本文件：build_android.sh，内容即本文件内容
# 编译步骤：1. 执行 ./configure  2. 执行 chmod 777 build_android.sh  3. 执行 ./build_android.sh

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
    --disable-ffserver \
    --cross-prefix=$CROSS_COMPILE \
    --enable-cross-compile \
    --sysroot=$SYSROOT \
    --enable-small \
    --extra-cflags="-Os -fpic $ADDI_CFLAGS" \
    --extra-ldflags="$ADDI_LDFLAGS" \
    $ADDITIONAL_CONFIGURE_FLAG
    make clean
    make
    make install
    echo "build ffmpeg for $ARCH finished"
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