package com.fortuneavenue.server.models.board.db

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass

class DistrictValueProgression(id: EntityID<Uuid>) : UuidEntity(id) {
    companion object : UuidEntityClass<DistrictValueProgression>(DistrictValueProgressionsTable)

    var districtId by DistrictValueProgressionsTable.districtId
    var ownedShopCount by DistrictValueProgressionsTable.ownedShopCount
    var existingShopBoostPercentage by DistrictValueProgressionsTable.existingShopBoostPercentage
    var newShopBoostPercentage by DistrictValueProgressionsTable.newShopBoostPercentage
}
