package com.charles.ollama.client.ui.models

import com.charles.ollama.client.data.litert.LocalModelCatalog
import com.charles.ollama.client.domain.model.Model

fun Model.displayTitle(): String =
    LocalModelCatalog.fromThreadModelName(name)?.displayName ?: name
