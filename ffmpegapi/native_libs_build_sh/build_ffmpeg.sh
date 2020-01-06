#!/bin/bash

# 本脚本在 ffmpeg-3.4.6、4.0.5 测试通过
# 编译：chmod 777 build_ffmpeg.sh && sudo ./build_ffmpeg.sh ABI
# 建议执行脚本时切换到 root 用户或加 sudo，否则在编译时报权限错误
# 适配 ffmpeg-4.0.5 版本时，去除 --disable-ffserver 选项，否则 configure 失败，无法生成自定义安装目录 android

if [ $# -ne 1 ];
  then echo "illegal number of parameters"
  echo "usage: sudo ./build_ffmpeg.sh ABI"
  echo "ABI can be armeabi/armeabi-v7a/arm64-v8a/x86/x86_64/all"
  exit 1
fi

export ABI=$1
echo -e "====== ABI is $ABI ======\n"

export NDK_HOME=/home/ubuntu/ndk/android-ndk-r14b

function set_armeabi {
    ARCH=arm
    ABI=armeabi
    PLATFORM_VERSION=android-9
    PREFIX=$(pwd)/android/$ABI
    TOOLCHAIN=$NDK_HOME/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
    CROSS_COMPILE=$TOOLCHAIN/bin/arm-linux-androideabi-
    ADDI_CFLAGS="-marm"
    ADDI_LDFLAGS=""
    SYSROOT=$NDK_HOME/platforms/$PLATFORM_VERSION/arch-$ARCH/
}

function set_armeabi_v7a {
    ARCH=arm
    ABI=armeabi-v7a
    PLATFORM_VERSION=android-9
    PREFIX=$(pwd)/android/$ABI
    TOOLCHAIN=$NDK_HOME/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
    CROSS_COMPILE=$TOOLCHAIN/bin/arm-linux-androideabi-
    ADDI_CFLAGS="-march=armv7-a -mfpu=neon -mfloat-abi=softfp -mthumb"
    ADDI_LDFLAGS="-march=armv7-a -Wl,--fix-cortex-a8"
    SYSROOT=$NDK_HOME/platforms/$PLATFORM_VERSION/arch-$ARCH/
}

function set_arm64_v8a {
    ARCH=arm64
    ABI=arm64-v8a
    PLATFORM_VERSION=android-21
    PREFIX=$(pwd)/android/$ABI
    TOOLCHAIN=$NDK_HOME/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64
    CROSS_COMPILE=$TOOLCHAIN/bin/aarch64-linux-android-
    ADDI_CFLAGS=""
    ADDI_LDFLAGS=""
    SYSROOT=$NDK_HOME/platforms/$PLATFORM_VERSION/arch-$ARCH/
}

function set_x86 {
    ARCH=x86
    ABI=x86
    PLATFORM_VERSION=android-9
    PREFIX=$(pwd)/android/$ABI
    TOOLCHAIN=$NDK_HOME/toolchains/x86-4.9/prebuilt/linux-x86_64
    CROSS_COMPILE=$TOOLCHAIN/bin/i686-linux-android-
    ADDI_CFLAGS="-march=i686 -mtune=intel -mssse3 -mfpmath=sse -m32"
    ADDI_LDFLAGS=""
    SYSROOT=$NDK_HOME/platforms/$PLATFORM_VERSION/arch-$ARCH/
}

function set_x86_64 {
    ARCH=x86_64
    ABI=x86_64
    PLATFORM_VERSION=android-21
    PREFIX=$(pwd)/android/$ABI
    TOOLCHAIN=$NDK_HOME/toolchains/x86_64-4.9/prebuilt/linux-x86_64
    CROSS_COMPILE=$TOOLCHAIN/bin/x86_64-linux-android-
    ADDI_CFLAGS="-march=x86-64 -mtune=intel -msse4.2 -mpopcnt -m64"
    ADDI_LDFLAGS=""
    SYSROOT=$NDK_HOME/platforms/$PLATFORM_VERSION/arch-$ARCH/
}

function terminate {
  err="Unknown error!"
  test "$1" && err=$1
  echo "$err"
  exit -1
}

function build {
    echo "start build ffmpeg for $ABI"
    ./configure --target-os=android \
    --arch=$ARCH \
    --cross-prefix=$CROSS_COMPILE \
    --sysroot=$SYSROOT \
    --prefix=$PREFIX \
    --extra-cflags="-Os -fpic $ADDI_CFLAGS" \
    --extra-ldflags="$ADDI_LDFLAGS" \
    --enable-gpl \
    --enable-cross-compile \
    --enable-small \
    --enable-shared \
    --disable-static \
    --disable-avdevice \
    --disable-yasm \
    --disable-asm \
    --disable-symver \
    --disable-programs \
    --disable-doc \
    $ADDITIONAL_CONFIGURE_FLAG || terminate "Configure ffmpeg failed!"
    echo -e "====== configure finished ======\n"

    make clean
    echo -e "====== clean finished ======\n"

    make -j4
    echo -e "====== make finished ======\n"

    make install
    echo -e "====== install finished ======\n"
    echo -e "build ffmpeg for $ABI finished, the end time is $(date "+%Y-%m-%d %H:%M:%S")\n"
}

start_tm=`date +%s%N`

if [ $ABI == "armeabi" ]
then
    set_armeabi
    build
elif [ $ABI == "armeabi-v7a" ]
then
    set_armeabi_v7a
    build
elif [ $ABI == "arm64-v8a" ]
then
    set_arm64_v8a
    build
elif [ $ABI == "x86" ]
then
    set_x86
    build
elif [ $ABI == "x86_64" ]
then
    set_x86_64
    build
elif [ $ABI == "all" ]
then
    set_armeabi
    build
    set_armeabi_v7a
    build
    set_arm64_v8a
    build
    set_x86
    build
    set_x86_64
    build
else
    echo -e "====== invalid ABI: $ABI ======\n"
fi

end_tm=`date +%s%N`
use_tm=`echo $end_tm $start_tm | awk '{ print ($1 - $2) / 1000000000}'`
echo "Total use time $use_tm seconds"
