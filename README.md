terminal

一个适用于Android(含AndroidTV)的超小型伪终端(PTY)实现

原理

Java层通过ProcessBuilder启动预编译的原生script二进制文件,该文件负责创建PTY(伪终端)并将/system/bin/sh绑定到其中.上层通过标准输入输出流与Shell交互,UI层负责实时回显输出.

核心PTY逻辑由script辅助程序封装,不涉及NDK编译

架构适配

支持以下四种CPU架构,通过GradleProductFlavors分别打包为独立APK：

·arm32
·arm64
·x86
·x86_64

每个架构的assets目录下包含对应架构的script可执行文件.打包时仅包含当前架构版本,以控制体积.

⚠️使用前请先确定好自己机器的架构,架构不匹配会打不开软件！

编译

1.准备好各架构的script二进制文件,放入对应flavor的assets目录.
2.执行./gradlewassembleRelease编译所有架构release版本.
3.也可单独编译某架构,如./gradlewassembleArm32Release.

script二进制来源

script二进制文件提取自Termux它负责创建PTY并启动交互式Shell.

如需修改行为或适配其他架构,请自行编译对应架构的版本并替换.

许可证

本项目代码部分可自由修改使用.script二进制文件源自util-linux,遵循其原有许可证(GPL).请遵守相关开源协议.