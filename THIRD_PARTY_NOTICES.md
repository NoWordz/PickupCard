# 第三方组件声明 / Third-party notices

本 mod 的 jar 里**内嵌**了下面这些第三方产物（不是玩家需要另装的前置）。为什么选择内嵌：
Forge 的开发与发布两条类加载路径不一样，内嵌是唯一两边都验过的做法；而 HUD mod 的
native 加载失败意味着 HUD 整个不画。

这份文件会随产物一起进 jar（`/THIRD_PARTY_NOTICES.md`）—— 宽松许可的条件就是把声明带上。

## LWJGL（Java 侧绑定）

- 文件：`META-INF/jarjar/lwjgl-3.3.1.jar` 与 `META-INF/jarjar/lwjgl-nanovg-3.3.1.jar`
  （由 JarJar 嵌套供应。主 jar 里**不再**摊平 `org/lwjgl/nanovg/**`：两个 mod 导出同一个
  包会让 Forge 的 JPMS 直接拒绝启动，详见 [docs/architecture.md](docs/architecture.md) 的
  「打包：nanovg 走 JarJar 嵌套」一节）
- 版本：3.3.1 —— 与 Minecraft 1.20.1 实际加载的 LWJGL 同版本，区间钉死为 `[3.3.1]`
  （开区间会解析到 3.3.6，错版本 = 首次调用 `UnsatisfiedLinkError`）
- 许可：BSD-3-Clause — <https://www.lwjgl.org/license>

## NanoVG / NanoSVG（C 侧，已编译进 native）

- 文件：`windows/x64/…/lwjgl_nanovg.dll`、`linux/x64/…/liblwjgl_nanovg.so`、
  `macos/x64/…` 与 `macos/arm64/…` 的 `liblwjgl_nanovg.dylib`
  （四平台 native 留在**主 jar**：它们是资源不是类，不产生 JPMS 包导出，LWJGL 按资源路径自取）
- 上游：NanoVG 与 NanoSVG，作者 Mikko Mononen
- 许可：zlib — <https://github.com/memononen/nanovg/blob/master/LICENSE.txt>

两个许可都是宽松许可，允许以二进制形式随本 mod 分发，条件是保留上述声明。
