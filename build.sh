#!/bin/bash

export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

SOURCE_DIR="源代码"

print_msg() {
    echo -e "${2}${1}${NC}"
}

print_header() {
    echo -e "\n${BOLD}${CYAN}========================================${NC}"
    echo -e "${BOLD}${CYAN}  $1${NC}"
    echo -e "${BOLD}${CYAN}========================================${NC}\n"
}

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

install_jdk() {
    print_header "检查 Java 开发工具包 (JDK)"
    
    if command_exists java && command_exists javac; then
        JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
        print_msg "✓ JDK 已安装 (版本: $JAVA_VERSION)" "$GREEN"
    else
        print_msg "✗ 未找到 JDK，正在安装..." "$YELLOW"
        
        if command_exists apt-get; then
            sudo apt-get update
            sudo apt-get install -y openjdk-17-jdk
        elif command_exists yum; then
            sudo yum install -y java-17-openjdk-devel
        elif command_exists dnf; then
            sudo dnf install -y java-17-openjdk-devel
        else
            print_msg "✗ 不支持的包管理器，请手动安装 JDK" "$RED"
            exit 1
        fi
        
        if [ $? -eq 0 ]; then
            print_msg "✓ JDK 安装成功" "$GREEN"
        else
            print_msg "✗ JDK 安装失败" "$RED"
            exit 1
        fi
    fi
    
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
    print_msg "JAVA_HOME 设置为: $JAVA_HOME" "$BLUE"
}

install_android_sdk() {
    print_header "检查 Android SDK"
    
    ANDROID_HOME="$HOME/Android/Sdk"
    
    if [ -d "$ANDROID_HOME" ] && [ -f "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
        print_msg "✓ Android SDK 已安装" "$GREEN"
        
        print_msg "正在检查并安装所需的 SDK 包..." "$CYAN"
        yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1
        "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-34" "build-tools;34.0.0" "platforms;android-36" "build-tools;35.0.0"
        print_msg "✓ SDK 包已更新" "$GREEN"
    else
        print_msg "✗ 未找到 Android SDK，正在安装..." "$YELLOW"
        
        CURRENT_DIR=$(pwd)
        mkdir -p "$ANDROID_HOME/cmdline-tools"
        cd "$ANDROID_HOME/cmdline-tools"
        
        SDK_URL="https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip"
        print_msg "正在下载 Android SDK 命令行工具..." "$CYAN"
        wget -q --show-progress "$SDK_URL" -O commandlinetools.zip
        
        if [ $? -ne 0 ]; then
            print_msg "✗ Android SDK 下载失败" "$RED"
            cd "$CURRENT_DIR"
            exit 1
        fi
        
        unzip -q commandlinetools.zip
        mv cmdline-tools latest
        rm commandlinetools.zip
        
        print_msg "✓ Android SDK 命令行工具已安装" "$GREEN"
        
        print_msg "正在接受 SDK 许可证..." "$CYAN"
        yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1
        
        print_msg "正在安装 SDK 包..." "$CYAN"
        "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-34" "build-tools;34.0.0" "platforms;android-36" "build-tools;35.0.0"
        
        cd "$CURRENT_DIR"
        print_msg "✓ Android SDK 包已安装" "$GREEN"
    fi
    
    export ANDROID_HOME
    export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
    print_msg "ANDROID_HOME 设置为: $ANDROID_HOME" "$BLUE"
}

install_gradle() {
    print_header "检查 Gradle"
    
    if command_exists gradle; then
        GRADLE_VERSION=$(gradle -v | grep "Gradle" | awk '{print $2}')
        print_msg "✓ Gradle 已安装 (版本: $GRADLE_VERSION)" "$GREEN"
    else
        print_msg "✗ 未找到 Gradle，正在安装..." "$YELLOW"
        
        GRADLE_VERSION="8.5"
        GRADLE_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
        
        CURRENT_DIR=$(pwd)
        cd /tmp
        print_msg "正在下载 Gradle ${GRADLE_VERSION}..." "$CYAN"
        wget -q --show-progress "$GRADLE_URL" -O gradle.zip
        
        if [ $? -ne 0 ]; then
            print_msg "✗ Gradle 下载失败" "$RED"
            cd "$CURRENT_DIR"
            exit 1
        fi
        
        sudo mkdir -p /opt/gradle
        sudo unzip -q gradle.zip -d /opt/gradle
        sudo ln -sf /opt/gradle/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle
        rm gradle.zip
        
        cd "$CURRENT_DIR"
        print_msg "✓ Gradle 安装成功" "$GREEN"
    fi
}

build_apk() {
    print_header "构建 APK"
    
    if [ ! -d "$SOURCE_DIR" ]; then
        print_msg "✗ 未找到源代码目录 '$SOURCE_DIR'！" "$RED"
        exit 1
    fi
    
    cd "$SOURCE_DIR"
    print_msg "从目录构建: $(pwd)" "$BLUE"
    
    if [ -f "gradlew" ]; then
        chmod +x gradlew
        print_msg "✓ 已设置 gradlew 为可执行" "$GREEN"
    else
        print_msg "✗ 未找到 gradlew！" "$RED"
        exit 1
    fi
    
    print_msg "\n正在运行 Gradle 清理..." "$CYAN"
    ./gradlew clean
    
    print_msg "\n正在构建 APK（可能需要一些时间）..." "$MAGENTA"
    ./gradlew assembleDebug --stacktrace
    
    BUILD_RESULT=$?
    
    if [ $BUILD_RESULT -eq 0 ]; then
        print_msg "\n✓ APK 构建成功！" "$GREEN"
        
        APK_PATH=$(find . -name "*.apk" -type f 2>/dev/null)
        if [ -n "$APK_PATH" ]; then
            print_msg "\n${BOLD}📦 APK 文件:${NC}" "$CYAN"
            for apk in $APK_PATH; do
                APK_SIZE=$(du -h "$apk" | cut -f1)
                print_msg "  → $apk ($APK_SIZE)" "$GREEN"
            done
        fi
    else
        print_msg "\n✗ APK 构建失败！" "$RED"
        print_msg "请检查上面的错误信息。" "$YELLOW"
        print_msg "\n您也可以尝试:" "$YELLOW"
        print_msg "  cd $SOURCE_DIR && ./gradlew assembleDebug --info" "$BLUE"
        exit 1
    fi
}

main() {
    print_header "Android APK 构建脚本"
    print_msg "开始构建流程..." "$MAGENTA"
    
    install_jdk
    install_android_sdk
    install_gradle
    build_apk
    
    print_header "构建完成！🎉"
    print_msg "所有任务已成功完成！" "$GREEN"
}

main
