package com.charles.ollama.client.domain.prompt

/**
 * A system-prompt preset shown in the prompt library. [builtIn] presets ship
 * with the app and cannot be edited/deleted; custom presets are user-created
 * and backed by the prompt_presets table (their [id] is "custom_<rowId>").
 */
data class PromptPreset(
    val id: String,
    val title: String,
    val text: String,
    val builtIn: Boolean
)

/** Curated personas shipped with the app. */
object BuiltInPrompts {
    val all: List<PromptPreset> = listOf(
        PromptPreset(
            id = "builtin_coding",
            title = "Coding Assistant",
            text = "You are an expert software engineer. Give correct, concise answers with " +
                "minimal but complete code. Prefer modern idioms, call out edge cases, and " +
                "explain only what isn't obvious from the code.",
            builtIn = true
        ),
        PromptPreset(
            id = "builtin_concise",
            title = "Concise",
            text = "Answer as briefly as possible. Lead with the direct answer, then add at most " +
                "a sentence or two of context. No filler, no restating the question.",
            builtIn = true
        ),
        PromptPreset(
            id = "builtin_brainstorm",
            title = "Brainstormer",
            text = "You are a creative brainstorming partner. Offer a wide range of distinct ideas, " +
                "think laterally, and build on the user's direction. Favor quantity and variety, " +
                "then highlight the most promising options.",
            builtIn = true
        ),
        PromptPreset(
            id = "builtin_tutor",
            title = "Socratic Tutor",
            text = "You are a patient tutor. Explain step by step, check understanding with short " +
                "questions, and use simple examples. Encourage the learner and avoid giving the " +
                "full answer before they've had a chance to reason.",
            builtIn = true
        ),
        PromptPreset(
            id = "builtin_translator",
            title = "Translator",
            text = "You are a precise translator. Detect the input language and translate naturally " +
                "into the requested target language, preserving tone and meaning. Provide only the " +
                "translation unless asked to explain.",
            builtIn = true
        ),
        PromptPreset(
            id = "builtin_writer",
            title = "Editor",
            text = "You are a sharp writing editor. Improve clarity, flow, and grammar while keeping " +
                "the author's voice. Return the revised text first, then a short bullet list of the " +
                "key changes you made.",
            builtIn = true
        )
    )
}
