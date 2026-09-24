package com.stayfocused.app.data.local.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.Relation

@Entity(
    tableName = "profile_blocked_packages",
    primaryKeys = ["profileId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = FocusProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"]), Index(value = ["packageName"])]
)
data class ProfileBlockedPackageEntity(
    val profileId: Long,
    val packageName: String
)

@Entity(
    tableName = "profile_blocked_domains",
    primaryKeys = ["profileId", "domain"],
    foreignKeys = [
        ForeignKey(
            entity = FocusProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"]), Index(value = ["domain"])]
)
data class ProfileBlockedDomainEntity(
    val profileId: Long,
    val domain: String
)

data class FocusProfileWithRules(
    @Embedded val profile: FocusProfileEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "profileId"
    )
    val blockedPackages: List<ProfileBlockedPackageEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "profileId"
    )
    val blockedDomains: List<ProfileBlockedDomainEntity>
)
