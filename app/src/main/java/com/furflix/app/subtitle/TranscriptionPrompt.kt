package com.furflix.app.subtitle

import kotlinx.serialization.Serializable

/**
 * 风格预设：一段拼进系统提示词的"额外风格提示词"。
 *
 * [builtIn] 的内置预设名字按"预设标签"处理、不参与本地化（用户可另存为自定义预设
 * 再改名），与语言选择器里恒显母语名同理。
 */
@Serializable
data class StylePreset(
    val id: String,
    val name: String,
    val prompt: String,
    val builtIn: Boolean = false
)

/**
 * 字幕生成的提示词模板与变量注入。
 *
 * 模板里的三个占位符由调用方在发起请求前动态替换：额外风格提示词、作品标题、
 * 作品描述。纯函数，可直接单测。
 */
object TranscriptionPrompt {

    const val VAR_EXTRA_STYLE = "{@用户的额外风格提示词}"
    const val VAR_TITLE = "{@作品标题}"
    const val VAR_DESCRIPTION = "{@作品描述}"

    const val PRESET_ID_VORE = "builtin_vore"
    const val PRESET_ID_BILINGUAL = "builtin_bilingual"

    /** 基础系统提示词模板（占位符在发起请求时注入）。 */
    val DEFAULT_TEMPLATE: String = """
请对音频进行专业的转录, 确保生成带有精准时间戳的优质单行字幕 SRT file, 在任何情况下都确保精准识别所有台词。
提供精确的单词级时间戳, 准确逐字识别, 转录所有口语词，包括语气词、停顿、结巴等, 需要精确转录和分析语音。
强制精准转录所有模糊的声音和台词: Ensure the sounds of the predator or prey are distinct
转录始终忠于原始音频, 禁止凭空捏造台词和描述: 所有台词和描述仅基于音频的转录。
人声的台词用 "" 标注, 描述用 [] 标注
只有在无人声时, 详细添加优质引人入胜的 Scenario Hint: [ Briefly describe the likely activity, e.g., "GoPro recording of mountain biking," "Kitchen cooking tutorial narration," "Dialogue recorded in a crowded market" ]

# 上下文信息：
$VAR_EXTRA_STYLE

# 音频简介补充：
标题：$VAR_TITLE
描述：$VAR_DESCRIPTION

[TASK] 注意! 请你完全确保 srt 字幕的时间戳要使用逗号, 完全确保所有时间戳都使用 MM:SS,sss 格式! 输出符合标准的 srt 格式字幕，注意必须用 ``` 包裹，输出示例：
```srt
1
00:00,000 --> 00:02,908
[smile] "hey, in my belly..." **BURP**
```
""".trimIndent()

    /** 开箱即用的内置预设。 */
    val DEFAULT_PRESETS: List<StylePreset> = listOf(
        StylePreset(
            id = PRESET_ID_VORE,
            name = "Vore 风格",
            prompt = "重点刻画吞食场景的身体感受与声音细节：胃部蠕动声、吞咽声、饱腹闷响、" +
                "体内回声等都要尽量转录并标注；捕食者与被捕食者的声音必须明确区分。",
            builtIn = true
        ),
        StylePreset(
            id = PRESET_ID_BILINGUAL,
            name = "中文双语翻译",
            prompt = "每条字幕输出双语：第一行保留原始语言的转录，第二行给出对应的简体中文翻译；" +
                "两行属于同一条时间轴，不要拆成两条字幕。",
            builtIn = true
        )
    )

    /** 占位符替换。[extraStyle] 为空表示未启用任何风格预设。 */
    fun render(
        template: String,
        extraStyle: String,
        title: String,
        description: String
    ): String = template
        .replace(VAR_EXTRA_STYLE, extraStyle.ifBlank { "（无）" })
        .replace(VAR_TITLE, title.ifBlank { "（无）" })
        .replace(VAR_DESCRIPTION, description.ifBlank { "（无）" })

    /** 把启用中的预设拼成一段额外风格提示词（按 [presets] 的顺序）。 */
    fun combinePresets(presets: List<StylePreset>, activeIds: Set<String>): String =
        presets.filter { it.id in activeIds }
            .map { it.prompt.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
}
