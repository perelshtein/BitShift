package com.github.perelshtein

import at.favre.lib.crypto.bcrypt.BCrypt
import com.github.perelshtein.database.DatabaseAccess
import com.github.perelshtein.database.OrdersManager
import com.github.perelshtein.database.OrdersManager.Orders
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.*
import org.ktorm.entity.*
import org.ktorm.entity.sequenceOf
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.use

class AccessControl: KoinComponent {
    object Users : Table<User>("users") {
        val id = int("id").primaryKey().bindTo { it.id }
        val date = datetime("date").bindTo { it.date }
        val name = varchar("name").bindTo { it.name }
        val password = varchar("password").bindTo { it.password }
        val mail = varchar("mail").bindTo { it.mail }
        val roleId = int("role_id").bindTo { it.roleId }
        val referralId = int("referral_id").bindTo { it.referralId }
        val cashbackPercent = double("cashback_percent").bindTo { it.cashbackPercent }
        val cashbackType = varchar("cashback_type").bindTo { it.cashbackType }
        val referralPercent = double("referral_percent").bindTo { it.referralPercent }
        var referralType = varchar("referral_type").bindTo { it.referralType }
    }

    object Roles : Table<Role>("roles") {
        val id = int("id").primaryKey().bindTo { it.id }
        val name = varchar("name").bindTo { it.name }
        val isEditNews = boolean("is_edit_news").bindTo { it.isEditNews }
        val isAdminPanel = boolean("is_admin_panel").bindTo { it.isAdminPanel }
        val isEditUserAndRole = boolean("is_edit_user_and_role").bindTo { it.isEditUserAndRole }
        val isEditOptions = boolean("is_edit_options").bindTo { it.isEditOptions }
        val isEditCurrency = boolean("is_edit_currency").bindTo { it.isEditCurrency }
        val isEditDirection = boolean("is_edit_direction").bindTo { it.isEditDirection }
        val isEditReserve = boolean("is_edit_reserve").bindTo { it.isEditReserve }
        val isEditNotify = boolean("is_edit_notify").bindTo { it.isEditNotify }
        val isEditReview = boolean("is_edit_review").bindTo { it.isEditReview }
        val isSendReferralPayouts = boolean("is_send_referral_payouts").bindTo { it.isSendReferralPayouts }
        val isSendCashbackPayouts = boolean("is_send_cashback_payouts").bindTo { it.isSendCashbackPayouts }
    }

    private val dbAccess: DatabaseAccess by inject()
    private val db = dbAccess.db
    val Database.users get() = this.sequenceOf(Users)
    val Database.roles get() = this.sequenceOf(Roles)
    val log = LoggerFactory.getLogger("AccessControl")

    init {
        // если в базе нет роли Админ, добавим ее
        var adminRole = db.roles.firstOrNull { it.isAdminPanel }
        if(adminRole == null) {
            adminRole = Role {
                name = "Admin"
                isAdminPanel = true
                isEditUserAndRole = true
                isEditNews = true
                isEditOptions = true
                isEditCurrency = true
                isEditDirection = true
                isEditReserve = true
                isEditNotify = true
                isEditReview = true
                isSendReferralPayouts = true
                isSendCashbackPayouts = true
            }
            addRole(adminRole)
        }

        var userRole = db.roles.firstOrNull { it.name eq "User" }
        if(userRole == null) {
            userRole = Role {
                name = "User"
            }
            addRole(userRole)
        }

        //если в базе нет админа, добавим его
        if(db.users.firstOrNull { it.roleId eq adminRole.id } == null) {
            val adminPassword = String().generateRandom()

            val newUser = User {
                name = "admin"
                password = adminPassword.generateHash()
                mail = "dev@null.com"
                roleId = adminRole.id
            }
            addUser(newUser)
            log.info("Добавлен админ по умолчанию - ${newUser.mail}, пароль - $adminPassword")
        }
    }

    // CRUD methods
    fun addUser(user: User): Int {
        val lowerMail = user.mail.trim().lowercase()
        if(db.users.any { it.mail eq lowerMail }) throw ValidationError("Пользователь с почтой ${lowerMail} уже существует")
        db.users.add(user)
        log.info("Добавлен пользователь ${user.name}")
        return getUserByName(user.name)!!.id
    }

    fun updateUser(user: User) {
        val lowerMail = user.mail.trim().lowercase()
        if(db.users.any { (it.mail eq lowerMail) and (it.id neq user.id) }) {
            throw ValidationError("Пользователь с почтой ${lowerMail} уже существует")
        }
        user.flushChanges()
    }

    fun getUserById(userId: Int): User? {
        return db.users.find { it.id eq userId }
    }

    fun getUserByName(name: String): User? {
        return db.users.find { it.name eq name }
    }

    fun getUserByMail(mail: String): User? {
        return db.users.find { it.mail eq mail }
    }

    fun getUsers(from: Int = 0, count: Int = 10, role: Int? = null, query: String? = null): Pair<Int, List<User>> {
        val query = db.from(Users)
            .select()
            .whereWithConditions {
                role?.let { role ->
                    it += Users.roleId eq role
                }
                query?.let { query ->
                    it += (Users.mail like "%${query.trim().lowercase()}%") or (Users.name like "%${query.trim().lowercase()}%")
                }
            }

        val countFound = query.totalRecordsInAllPages
        if (countFound == 0) return 0 to emptyList()

        val result = query
            .limit(from, count)
            .map {
                Users.createEntity(it)
            }
        return countFound to result
    }

    fun getRoles(): List<Role> {
        return db.roles.toList()
    }

    fun getRoleByName(name: String): Role? {
        return db.roles.find { it.name eq name }
    }

    fun deleteUser(userId: Int): Int {
        db.users.find { it.id eq userId }?.let { user ->
            //считаем, сколько всего админов
            val adminCount = db.from(Users)
                .innerJoin(Roles, on = Users.roleId eq Roles.id)
                .select()
                .where { Roles.isAdminPanel eq true }
                .totalRecordsInAllPages
            if(adminCount == 1 && getRoleById(user.roleId)!!.isAdminPanel) {
                throw ValidationError("Нельзя удалять последнего админа!")
            }
            log.info("Удаляю пользователя ${user.name}")
        }
        return db.delete(Users) { it.id eq userId }
    }

    fun addRole(role: Role): Int {
        if(db.roles.any { it.name eq role.name }) throw ValidationError("Роль ${role.name} уже существует")
        log.info("Добавлена роль ${role.name}")
        db.roles.add(role)
        return getRoleByName(role.name)!!.id
    }

    fun updateRole(role: Role) {
        if(!role.isAdminPanel && db.roles.count { it.isAdminPanel } == 1 && getRoleById(role.id)!!.isAdminPanel) {
            throw ValidationError("Нельзя отбирать последние права у админа!")
        }
        if(!role.isEditUserAndRole && db.roles.count { it.isEditUserAndRole } == 1 && getRoleById(role.id)!!.isEditUserAndRole) {
            throw ValidationError("Нельзя выстрелить себе в ногу! Никто больше не сможет редактировать роли!")
        }
        if(db.roles.any { (it.name eq role.name) and (it.id neq role.id) }) throw ValidationError("Роль ${role.name} уже существует")
        role.flushChanges()
    }

    fun getRoleById(roleId: Int): Role? {
        return db.roles.find { it.id eq roleId }
    }

    fun deleteRole(roleId: Int): Int {
        db.roles.find { it.id eq roleId }?.let { role ->
            if(role.isAdminPanel && db.roles.count { it.isAdminPanel } == 1) {
                throw ValidationError("Нельзя удалить единственную роль админа!")
            }
            log.info("Удаляю роль ${role.name}")
        }
        return db.delete(Roles) { it.id eq roleId }
    }

    fun fakeMethod() {
        println("fake method")
    }

    fun getUsersCount(role: Int? = null, query: String = ""): Int {
        return db.useConnection { conn ->
            val baseQuery = """
            SELECT COUNT(*)
            FROM users
            WHERE 1=1
        """.trimIndent()

            // Составим запрос динамически в зависимости от фильтров
            val filters = StringBuilder()
            val params = mutableListOf<Any>()

            if (role != null) {
                filters.append(" AND role_id = ?")
                params.add(role)
            }
            if (query.isNotEmpty()) {
                filters.append(" AND (mail LIKE ? OR name LIKE ?)")
                params.add("%${query.trim()}%")
                params.add("%${query.trim()}%")
            }
            conn.prepareStatement("${baseQuery} ${filters}").use { statement ->
                params.forEachIndexed { index, param ->
                    when (param) {
                        is Int -> statement.setInt(index + 1, param)
                        is String -> statement.setString(index + 1, param)
                    }
                }
                val resultSet  = statement.executeQuery()
                if (resultSet.next()) {
                    return resultSet.getInt(1)
                }
                return 0
            }
        }
    }
}

interface User: Entity<User> {
    companion object : Entity.Factory<User>()
    var id: Int
    var date: LocalDateTime
    var name: String
    var password: String
    var mail: String
    var roleId: Int
    var referralId: Int?
    var cashbackPercent: Double?
    var cashbackType: String
    var referralPercent: Double?
    var referralType: String
}

interface Role: Entity<Role> {
    companion object : Entity.Factory<Role>()
    var id: Int
    var name: String
    var isAdminPanel: Boolean
    var isEditUserAndRole: Boolean
    var isEditNews: Boolean
    var isEditOptions: Boolean
    var isEditCurrency: Boolean
    var isEditDirection: Boolean
    var isEditReserve: Boolean
    var isEditNotify: Boolean
    var isEditReview: Boolean
    var isSendReferralPayouts: Boolean
    var isSendCashbackPayouts: Boolean
}

// используем DTO вместо Entity, чтобы не было проблем с сериализацией,
// и чтобы отделить структуру базы от backend API
@Serializable
data class UserDTO(
    val id: Int = 0,
    @Serializable(with = LocalDateTimeSerializer::class)
    val date: LocalDateTime = LocalDateTime.now(),
    var name: String = "",
    var password: String = "",
    var mail: String = "",
    var roleId: Int = 0,
    var referralId: Int?,
    var cashbackPercent: Double?,
    var cashbackType: BONUS_TYPE,
    var referralPercent: Double?,
    var referralType: BONUS_TYPE,
    var ordersCount: Int? = null, // для страницы Пользователи в админке
    var referralName: String?,
    var referralMail: String?
)

fun UserDTO.toDB(): User {
    return User {
        id = this@toDB.id
        date = this@toDB.date
        name = this@toDB.name
        password = this@toDB.password
        mail = this@toDB.mail
        roleId = this@toDB.roleId
        referralId = this@toDB.referralId
        cashbackPercent = this@toDB.cashbackPercent
        cashbackType = this@toDB.cashbackType.value
        referralPercent = this@toDB.referralPercent
        referralType = this@toDB.referralType.value
    }
}


fun User.toDto(): UserDTO {
    val accessControl: AccessControl by inject(AccessControl::class.java)
    return UserDTO(
        id = this.id,
        date = this.date,
        name = this.name,
        password = "******",
        mail = this.mail,
        roleId = this.roleId,
        referralId = this.referralId,
        cashbackPercent = this.cashbackPercent,
        cashbackType = BONUS_TYPE.fromValue(this.cashbackType),
        referralPercent = this.referralPercent,
        referralType = BONUS_TYPE.fromValue(this.referralType),
        referralName = referralId?.let {accessControl.getUserById(it)?.name },
        referralMail = referralId?.let { accessControl.getUserById(it)?.mail }
    )
}

@Serializable
data class RoleDTO(
    var id: Int = 0,
    var name: String = "",
    var isAdminPanel: Boolean = false,
    var isEditUserAndRole: Boolean = false,
    var isEditOptions: Boolean = false,
    var isEditCurrency: Boolean = false,
    var isEditNews: Boolean = false,
    var isEditDirection: Boolean = false,
    var isEditReserve: Boolean = false,
    var isEditNotify: Boolean = false,
    var isEditReview: Boolean = false,
    var isSendReferralPayouts: Boolean = false

//    var isChangeOrderStatus: Boolean = false,
//    var isEditOrder: Boolean = false,
//    var isMakePayments: Boolean = false,
//    var isSetRate: Boolean = false,
//    var isEditReviews: Boolean = false,
)

fun RoleDTO.toDB(): Role {
    return Role {
        id = this@toDB.id
        name = this@toDB.name
        isAdminPanel = this@toDB.isAdminPanel
        isEditUserAndRole = this@toDB.isEditUserAndRole
        isEditOptions = this@toDB.isEditOptions
        isEditCurrency = this@toDB.isEditCurrency
        isEditNews = this@toDB.isEditNews
        isEditDirection = this@toDB.isEditDirection
        isEditReserve = this@toDB.isEditReserve
        isEditNotify = this@toDB.isEditNotify
        isEditReview = this@toDB.isEditReview
        isSendReferralPayouts = this@toDB.isSendReferralPayouts

//        isChangeOrderStatus = this@toDB.isChangeOrderStatus
//        isEditOrder = this@toDB.isEditOrder
//        isMakePayments = this@toDB.isMakePayments
//        isSetRate = this@toDB.isSetRate
//        isSetReserve = this@toDB.isSetReserve
//        isEditDirections = this@toDB.isEditDirections
//        isEditReviews = this@toDB.isEditReviews
//        isEditNews = this@toDB.isEditNews
    }
}

fun Role.toDto(): RoleDTO {
    return RoleDTO(
        id = this.id,
        name = this.name,
        isAdminPanel = this.isAdminPanel,
        isEditUserAndRole = this.isEditUserAndRole,
        isEditOptions = this.isEditOptions,
        isEditCurrency = this.isEditCurrency,
        isEditNews = this.isEditNews,
        isEditDirection = this.isEditDirection,
        isEditReserve = this.isEditReserve,
        isEditNotify = this.isEditNotify,
        isEditReview = this.isEditReview,
        isSendReferralPayouts = this.isSendReferralPayouts
    )
}

@Serializable
data class UpdatedTimeDTO (
    @Serializable (with = LocalDateTimeSerializer::class)
    val lastUpdated: LocalDateTime
)

fun LocalDateTime.toDto(): UpdatedTimeDTO {
    return UpdatedTimeDTO(lastUpdated = this)
}

enum class BONUS_TYPE(val value: String) {
    FROM_SUM("from_sum"),
    FROM_PROFIT("from_profit");

    companion object {
        fun fromValue(value: String): BONUS_TYPE =
            values().find { it.value == value }
                ?: throw IllegalArgumentException("Invalid bonus type value: $value")
    }
}