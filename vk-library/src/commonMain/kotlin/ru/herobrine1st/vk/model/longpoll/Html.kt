package ru.herobrine1st.vk.model.longpoll

private val regex = Regex("&(?:amp|lt|gt|quot);|<br>")
private val mapping = mapOf(
    "&amp;" to "&",
    "&lt;" to "<",
    "&gt;" to ">",
    "&quot;" to "\"",
    "<br>" to "\n"
)

internal fun String.unescapeHtml() = replace(regex) { mapping[it.value] ?: it.value }