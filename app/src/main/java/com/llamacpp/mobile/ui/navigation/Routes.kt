package com.llamacpp.mobile.ui.navigation

object Routes {
    const val CHAT = "chat"
    const val COMPLETION = "completion"
    const val SERVER_INFO = "server_info"
    const val SERVERS = "servers"
    const val SERVER_EDIT = "server_edit"
    const val SETTINGS = "settings"
    const val TOOLS = "tools"

    const val ARG_SERVER_ID = "serverId"

    fun serverEdit(serverId: String?): String =
        "$SERVER_EDIT?$ARG_SERVER_ID=${serverId.orEmpty()}"
}
