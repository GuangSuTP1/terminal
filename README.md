
# terminal

一个适用于安卓的超小型伪终端（PTY）实现。

## 原理

Java 层通过 ProcessBuilder 启动预编译的原生 script 二进制文件，该文件负责创建 PTY 并绑定 /system/bin/sh。上层通过标准输入输出流进行命令交互，UI 层负责回显输出。

核心逻辑不在 NDK 层，而是封装在特定架构的 script 辅助程序中。

## 架构适配

支持以下四种 CPU 架构，通过 Gradle Product Flavors 分别打包：

- arm32
- arm64
- x86
- x86_64

每个架构的 assets 目录下包含对应的 script 可执行文件，打包时仅包含当前架构版本，以控制体积。

使用前请先确定好自己机器的架构,架构不匹配 会打不开软件!!!

script 文件是预先编译好的原生二进制，负责底层 PTY 的创建与维护。如需修改其行为，需自行编译对应架构的版本并替换。