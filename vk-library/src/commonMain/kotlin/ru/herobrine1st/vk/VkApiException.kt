package ru.herobrine1st.vk

import kotlinx.io.IOException
import ru.herobrine1st.vk.model.VkError

public class VkApiException(public val error: VkError): IOException() {
    override val message: String by error::message
    public val code: Int by error::code
}