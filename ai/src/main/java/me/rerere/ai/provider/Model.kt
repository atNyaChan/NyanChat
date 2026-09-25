package me.rerere.ai.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Model(
    val modelId: String = "",
    val displayName: String = "",
    val id: Uuid = Uuid.random(),
    val type: ModelType = ModelType.CHAT,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val inputModalities: List<Modality> = listOf(Modality.TEXT),
    val outputModalities: List<Modality> = listOf(Modality.TEXT),
    val abilities: List<ModelAbility> = emptyList(),
    val tools: Set<BuiltInTools> = emptySet(),
    val providerOverwrite: ProviderSetting? = null,
    val price: ModelPrice? = null,
)

@Serializable
data class ModelPrice(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cacheRead: Double = 0.0,
    val cacheWrite: Double = 0.0,
)

@Serializable
enum class ModelType {
    CHAT,
    IMAGE,
    EMBEDDING,
}

@Serializable
enum class Modality {
    TEXT,
    IMAGE,
}

@Serializable
enum class ModelAbility {
    TOOL,
    REASONING,
}

// 模型(提供商)提供的内置工具选项
@Serializable
sealed class BuiltInTools {
    // https://ai.google.dev/gemini-api/docs/google-search?hl=zh-cn
    @Serializable
    @SerialName("search")
    data object Search : BuiltInTools()

    // https://ai.google.dev/gemini-api/docs/url-context?hl=zh-cn
    @Serializable
    @SerialName("url_context")
    data object UrlContext : BuiltInTools()

    @Serializable
    @SerialName("image_generation")
    data object ImageGeneration : BuiltInTools()
}

/**
 * 当前提供商 API 实际支持（会在请求中序列化）的内置工具，与各提供商的请求构建逻辑保持同步：
 *   - Claude：仅 [BuiltInTools.Search]
 *   - Google：[BuiltInTools.Search]、[BuiltInTools.UrlContext]
 *   - OpenAI：仅使用 Responses API 时支持 [BuiltInTools.Search]、[BuiltInTools.ImageGeneration]；
 *     使用 Chat Completions 时不支持任何内置工具
 */
fun ProviderSetting.supportedBuiltInTools(): Set<BuiltInTools> = when (this) {
    is ProviderSetting.OpenAI ->
        if (useResponseApi) {
            setOf(BuiltInTools.Search, BuiltInTools.ImageGeneration)
        } else {
            emptySet()
        }

    is ProviderSetting.Google -> setOf(BuiltInTools.Search, BuiltInTools.UrlContext)
    is ProviderSetting.Claude -> setOf(BuiltInTools.Search)
}

/**
 * 文本生成请求中实际会传给模型的内置工具。
 *
 * - 未开启“模型内置搜索”时移除 [BuiltInTools.Search]（与请求构建一致，见 GenerationHandler）
 * - 只保留当前提供商实际支持的内置工具，见 [supportedBuiltInTools]
 *
 * 需要与各提供商的请求构建逻辑保持同步。
 */
fun chatRequestBuiltInTools(
    model: Model,
    provider: ProviderSetting,
    useBuiltInSearch: Boolean,
): Set<BuiltInTools> {
    val candidate = if (useBuiltInSearch) model.tools else model.tools - BuiltInTools.Search
    return candidate.intersect(provider.supportedBuiltInTools())
}


