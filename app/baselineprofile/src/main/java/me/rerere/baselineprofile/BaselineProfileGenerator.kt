package me.rerere.baselineprofile

import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val REPLY_TIMEOUT_MS = 30_000L
private const val WAIT_POLL_MS = 100L
private const val SWIPE_STEPS = 20

/**
 * This test class generates a basic startup baseline profile for the target package.
 *
 * We recommend you start with this but add important user flows to the profile to improve their performance.
 * Refer to the [baseline profile documentation](https://d.android.com/topic/performance/baselineprofiles)
 * for more information.
 *
 * You can run the generator with the "Generate Baseline Profile" run configuration in Android Studio or
 * the equivalent `generateBaselineProfile` gradle task:
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 *
 * 生成前先 `git apply app/baselineprofile/mock-provider.patch`，让全新安装的 app 默认模型指向 [MockOpenAIServer]；
 * 生成完 `git apply -R app/baselineprofile/mock-provider.patch` 撤掉，只提交生成的 profile 文件。
 * 生成器每次运行前都会清空目标 app 的数据，避免上一次运行留下的会话把这一轮的回复挤到屏幕外。
 * The run configuration runs the Gradle task and applies filtering to run only the generators.
 *
 * Check [documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args)
 * for more information about available instrumentation arguments.
 *
 * After you run the generator, you can verify the improvements running the [StartupBenchmarks] benchmark.
 *
 * When using this class to generate a baseline profile, only API 33+ or rooted API 28+ are supported.
 *
 * The minimum required version of androidx.benchmark to generate a baseline profile is 1.2.0.
 **/
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        // benchmark 的 resetAndCompile 只重置 ART profile，不清 app 数据；上一轮留下的会话会让这一轮的
        // 回复落到很靠下的位置，而且会话里的旧标记会让“等回复出现”变成误判。每次运行都从干净的数据开始。
        clearTargetAppData()

        // 需要先应用 app/baselineprofile/mock-provider.patch，app 的默认模型才会指向这个假接口
        val server = MockOpenAIServer().apply { start() }
        try {
            collect(server)
        } finally {
            server.stop()
        }
    }

    /** 在 [BaselineProfileRule.collect] 之前调用：`pm clear` 会杀掉 app，不能放在生成过程中 */
    private fun clearTargetAppData() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm clear ${targetAppId()}")
            .let { ParcelFileDescriptor.AutoCloseInputStream(it) }
            .use { it.readBytes() }
    }

    /** The application id for the running build variant is read from the instrumentation arguments. */
    private fun targetAppId(): String =
        InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: throw Exception("targetAppId not passed as instrumentation runner arg")

    private fun collect(server: MockOpenAIServer) {
        rule.collect(
            packageName = targetAppId(),

            // See: https://d.android.com/topic/performance/baselineprofiles/dex-layout-optimizations
            includeInStartupProfile = true
        ) {
            // This block defines the app's critical user journey. Here we are interested in
            // optimizing for app startup. But you can also navigate and scroll through your most important UI.

            // Start default activity for your app
            pressHome()
            startActivityAndWait()

            // 等待聊天输入框出现（testTag 通过 testTagsAsResourceId 暴露为 resource-id）
            val input = checkNotNull(device.wait(Until.findObject(By.res("chat_input")), 5_000)) {
                "chat input not found"
            }

            // 点击输入框并输入一条 Markdown 文本（覆盖 Markdown / 代码块渲染路径）
            input.click()
            input.text = """
                # Hello NyanChat

                这是一段 **Markdown** 文本，包含 *斜体*、`行内代码` 和列表：

                - 第一项
                - 第二项
                - [链接](https://github.com)

                > 引用块示例

                ```kotlin
                fun main() {
                    println("Hello, world!")
                }
                ```
            """.trimIndent()
            device.waitForIdle()

            // 发送前先记下 mock 接口已经写完的回复数量，再用它判断这一轮回复何时送达
            val deliveredReplies = server.deliveredReplyCount
            checkNotNull(device.wait(Until.findObject(By.res("chat_send_button")), 3_000)) {
                "send button not found"
            }.click()
            awaitDelivered(server, deliveredReplies)
            device.waitForIdle()

            // 再确认回复的结尾标记真的渲染出来了（覆盖生成、思考块、Markdown、表格、公式和代码高亮路径）
            awaitReply(device, "${MockOpenAIServer.REPLY_MARKER} ${server.lastReplyIndex}")
            device.waitForIdle()

            // 在消息列表上来回滑动，覆盖长列表滚动
            scrollMessageList(device, towardEnd = false)
            scrollMessageList(device, towardEnd = true)
        }
    }

    /** 等 mock 接口把这一轮的流式回复完整写完 */
    private fun awaitDelivered(server: MockOpenAIServer, deliveredBefore: Int) {
        val deadline = System.currentTimeMillis() + REPLY_TIMEOUT_MS
        while (server.deliveredReplyCount <= deliveredBefore) {
            check(System.currentTimeMillis() < deadline) {
                "no streamed reply from the mock provider, check that mock-provider.patch is applied"
            }
            SystemClock.sleep(WAIT_POLL_MS)
        }
    }

    /**
     * 等待回复的结尾标记渲染出来，期间持续把消息列表翻到最新一条。
     *
     * 回复比屏幕高时，聊天列表的自动跟随会在气泡底边离开视口后停止，结尾标记会停在屏幕外。
     */
    private fun awaitReply(device: UiDevice, marker: String) {
        val deadline = System.currentTimeMillis() + REPLY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (device.findObject(By.textContains(marker)) != null) return
            scrollMessageList(device, towardEnd = true)
        }
        checkNotNull(device.findObject(By.textContains(marker))) {
            "reply \"$marker\" not rendered, check that mock-provider.patch is applied"
        }
    }

    /**
     * 翻一屏消息列表：`towardEnd` 为 true 时翻向最新的消息，否则翻向更早的消息。
     *
     * 不直接 `fling`：它在滑动前会把方向反过来（`fling(UP)` 的手势是从上往下划），而且会等 5 秒的
     * `scrollFinished` 事件——本 app 的列表常常等不到，一次翻页要白等满 5 秒。这里直接注入滑动手势。
     */
    private fun scrollMessageList(device: UiDevice, towardEnd: Boolean) {
        // 聊天建议等其它可滚动区域可能比消息列表先被命中，按高度取最大的那个
        val bounds = runCatching {
            device.findObjects(By.scrollable(true))
                .maxByOrNull { it.visibleBounds.height() }
                ?.visibleBounds
        }.getOrNull() ?: Rect(0, 0, device.displayWidth, device.displayHeight)
        val x = bounds.centerX()
        val top = bounds.top + bounds.height() / 5
        val bottom = bounds.top + bounds.height() * 4 / 5
        device.swipe(
            x,
            if (towardEnd) bottom else top,
            x,
            if (towardEnd) top else bottom,
            SWIPE_STEPS,
        )
        device.waitForIdle()
    }
}
