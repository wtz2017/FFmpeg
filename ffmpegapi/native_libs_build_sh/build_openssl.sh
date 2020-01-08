#!/bin/bash

# 以下脚本只适用于 ndk-r14b 和 openssl-1.1.1c release on 2019-May-28
#
# 编译：chmod 777 build_openssl.sh && sudo ./build_openssl.sh ABI
# 建议执行脚本时切换到 root 用户或加 sudo，否则在编译时报权限错误
#
# openssl-1.1.0g 的 os/compiler 针对 android 的支持平台有：
# android-armeabi android64-aarch64
# android-x86 android-mips
# android android64
#
# openssl-1.1.1c 的 os/compiler 针对 android 的支持平台有：
# android-arm android-armeabi android-arm64 android64-aarch64
# android-x86 android-x86_64 android64-x86_64
# android-mips android-mips64 android64-mips64
# android64

if [ $# -ne 1 ];
  then echo "illegal number of parameters"
  echo "usage: sudo ./build_openssl.sh ABI"
  echo "ABI can be armeabi/armeabi-v7a/arm64-v8a/x86/x86_64/all"
  exit 1
fi

export ABI=$1
echo -e "====== ABI is ${ABI} ======\n"

export NDK_HOME=/home/ubuntu/ndk/android-ndk-r14b

LIB_TYPE_SHARED=shared
LIB_TYPE_STATIC=no-shared
# 由于目前动态库生成的是带版本号尾缀的 libcrypto.so.1.1 和 libssl.so.1.1，不适合 android 直接使用
# 目前没有找到更改去除版本号尾缀的方法，暂时使用静态库
LIB_TYPE=${LIB_TYPE_STATIC}

function set_armeabi {
    OS_COMPILER=android-arm
    ARCH=arm
    ABI=armeabi
    PLATFORM_VERSION=14
    SYSROOT=${NDK_HOME}/platforms/android-${PLATFORM_VERSION}/arch-${ARCH}/
    PREFIX=$(pwd)/android/${ABI}
    TOOLCHAIN=${NDK_HOME}/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
    CROSS_COMPILE=${TOOLCHAIN}/bin/arm-linux-androideabi-
    ADDI_CFLAGS="--sysroot=${SYSROOT} -marm"
    ADDI_LDFLAGS=""
}

function set_armeabi_v7a {
    OS_COMPILER=android-arm
    ARCH=arm
    ABI=armeabi-v7a
    PLATFORM_VERSION=14
    SYSROOT=${NDK_HOME}/platforms/android-${PLATFORM_VERSION}/arch-${ARCH}/
    PREFIX=$(pwd)/android/${ABI}
    TOOLCHAIN=${NDK_HOME}/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
    CROSS_COMPILE=${TOOLCHAIN}/bin/arm-linux-androideabi-
    ADDI_CFLAGS="--sysroot=${SYSROOT} -march=armv7-a -mfpu=neon -mfloat-abi=softfp -mthumb"
    ADDI_LDFLAGS="-march=armv7-a -Wl,--fix-cortex-a8"
}

function set_arm64_v8a {
    OS_COMPILER=android-arm64
    ARCH=arm64
    ABI=arm64-v8a
    PLATFORM_VERSION=21
    SYSROOT=${NDK_HOME}/platforms/android-${PLATFORM_VERSION}/arch-${ARCH}/
    PREFIX=$(pwd)/android/${ABI}
    TOOLCHAIN=${NDK_HOME}/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64
    CROSS_COMPILE=${TOOLCHAIN}/bin/aarch64-linux-android-
    ADDI_CFLAGS="--sysroot=${SYSROOT} "
    ADDI_LDFLAGS=""
}

function set_x86 {
    OS_COMPILER=android-x86
    ARCH=x86
    ABI=x86
    PLATFORM_VERSION=14
    SYSROOT=${NDK_HOME}/platforms/android-${PLATFORM_VERSION}/arch-${ARCH}/
    PREFIX=$(pwd)/android/${ABI}
    TOOLCHAIN=${NDK_HOME}/toolchains/x86-4.9/prebuilt/linux-x86_64
    CROSS_COMPILE=${TOOLCHAIN}/bin/i686-linux-android-
    ADDI_CFLAGS="--sysroot=${SYSROOT} -march=i686 -mtune=intel -mssse3 -mfpmath=sse -m32"
    ADDI_LDFLAGS=""
}

function set_x86_64 {
    OS_COMPILER=android64-x86_64
    ARCH=x86_64
    ABI=x86_64
    PLATFORM_VERSION=21
    SYSROOT=${NDK_HOME}/platforms/android-${PLATFORM_VERSION}/arch-${ARCH}/
    PREFIX=$(pwd)/android/${ABI}
    TOOLCHAIN=${NDK_HOME}/toolchains/x86_64-4.9/prebuilt/linux-x86_64
    CROSS_COMPILE=${TOOLCHAIN}/bin/x86_64-linux-android-
    ADDI_CFLAGS="--sysroot=${SYSROOT} -march=x86-64 -mtune=intel -msse4.2 -mpopcnt -m64"
    ADDI_LDFLAGS=""
}

function terminate {
  err="Unknown error!"
  test "$1" && err=$1
  echo "$err"
  exit -1
}

function build {
    # 以下注释的部分在新版 openssl 里是不需要的，配置了 OS_COMPILER 后会自动判断使用 NDK
#    export CC=${CROSS_COMPILE}gcc
#    export CXX=${CROSS_COMPILE}g++
#    export LINK=${CXX}
#    export LD=${CROSS_COMPILE}ld
#    export AR=${CROSS_COMPILE}ar
#    export NM=${CROSS_COMPILE}nm
#    export RANLIB=${CROSS_COMPILE}ranlib
#    export STRIP=${CROSS_COMPILE}strip
    export ANDROID_NDK_HOME=${NDK_HOME}
    export PATH="${PATH}:${TOOLCHAIN}/bin"
    export CPPFLAGS=" ${ADDI_CFLAGS} -fpic -ffunction-sections -funwind-tables -fstack-protector -fno-strict-aliasing -finline-limit=64 "
    export CXXFLAGS=" ${ADDI_CFLAGS} -fpic -ffunction-sections -funwind-tables -fstack-protector -fno-strict-aliasing -finline-limit=64 -frtti -fexceptions "
    export CFLAGS=" ${ADDI_CFLAGS} -fpic -ffunction-sections -funwind-tables -fstack-protector -fno-strict-aliasing -finline-limit=64 "
    export LDFLAGS=" ${ADDI_LDFLAGS} "

    echo "start build openssl for ${ABI}"
    # no-asm 编译过程中不使用汇编代码加快编译过程
    # no-dso 仅在 no-shared 的前提下可用
    # no-hw 禁用硬件支持，移动设备iOS/android要加上
    # no-engine 禁用引擎支持，ENGINE是 OPENSSL 预留的用以加载第三方加密库引擎，主要包括了动态库加载的代码和加密函数指针管理的一系列接口
    # no-dtls 在 OpenSSL 1.1.0 和以上版本禁用 dtls
    # no-dtls1 在 OpenSSL 1.0.2 和以下版本禁用 dtls1
    # 剩下的裁减参数用于只保留 AES、MD5、RSA、SHA 四种加密算法
    ./Configure ${OS_COMPILER} ${LIB_TYPE} \
    -D__ANDROID_API__=${PLATFORM_VERSION} \
    no-asm no-dso no-hw no-engine no-dtls no-async \
    no-md2 no-md4 no-mdc2 no-poly1305 no-blake2 no-siphash \
    no-sm3 no-rc2 no-rc4 no-rc5 no-idea no-aria no-bf no-cast \
    no-camellia no-seed no-sm4 no-chacha no-ec no-dsa no-sm2 \
    no-err no-comp no-ocsp no-cms no-ts no-srp no-cmac no-ct \
    --prefix=${PREFIX} || terminate "Configure openssl for ${ABI} failed!"
    echo -e "====== configure finished ======\n"

    make clean
    echo -e "====== clean finished ======\n"

    make -j4 || terminate "Make openssl for ${ABI} failed!"
    echo -e "====== make finished ======\n"

    make install
    echo -e "====== install finished ======\n"
    echo -e "build openssl for ${ABI} finished, the end time is $(date "+%Y-%m-%d %H:%M:%S")\n"
}

start_tm=`date +%s%N`

if [ ${ABI} == "armeabi" ]
then
    set_armeabi
    build
elif [ ${ABI} == "armeabi-v7a" ]
then
    set_armeabi_v7a
    build
elif [ ${ABI} == "arm64-v8a" ]
then
    set_arm64_v8a
    build
elif [ ${ABI} == "x86" ]
then
    set_x86
    build
elif [ ${ABI} == "x86_64" ]
then
    set_x86_64
    build
elif [ ${ABI} == "all" ]
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
    echo -e "====== invalid ABI: ${ABI} ======\n"
fi

end_tm=`date +%s%N`
use_tm=`echo ${end_tm} ${start_tm} | awk '{ print ($1 - $2) / 1000000000}'`
echo "Total use time $use_tm seconds"
