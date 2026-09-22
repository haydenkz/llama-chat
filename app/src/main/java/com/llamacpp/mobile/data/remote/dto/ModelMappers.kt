package com.llamacpp.mobile.data.remote.dto

import com.llamacpp.mobile.domain.model.LlamaModel
import com.llamacpp.mobile.domain.model.ModelMeta

internal fun ModelDto.toDomain(): LlamaModel = LlamaModel(
    id = id,
    aliases = aliases,
    tags = tags,
    ownedBy = ownedBy,
    created = created,
    status = status?.value,
    statusArgs = status?.args.orEmpty(),
    presetText = status?.preset,
    source = source,
    canRemove = canRemove,
    meta = meta?.toDomain(),
    inputModalities = architecture?.inputModalities.orEmpty(),
    outputModalities = architecture?.outputModalities.orEmpty(),
)

private fun ModelMetaDto.toDomain(): ModelMeta = ModelMeta(
    nVocab = nVocab,
    nCtx = nCtx,
    nCtxTrain = nCtxTrain,
    nEmbd = nEmbd,
    nParams = nParams,
    sizeBytes = size,
    ftype = ftype,
)
