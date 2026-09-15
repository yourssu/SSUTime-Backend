package com.ssutime.notification.domain

import com.ssutime.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(name = "boards", uniqueConstraints = [UniqueConstraint(name = "uk_boards_board_id", columnNames = ["board_id"])])
class Board(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "board_id", nullable = false)
    val boardId: Long,
) : BaseEntity()
