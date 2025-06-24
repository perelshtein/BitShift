package com.github.perelshtein.database

import com.github.perelshtein.Options
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.ktorm.database.Database
import org.slf4j.LoggerFactory

class DatabaseAccess: KoinComponent {
    private val options: Options by inject()
    private val config = HikariConfig().apply {
        jdbcUrl = "jdbc:mysql://${options.dbAddress}:${options.dbPort}/${options.dbName}"
        username = options.dbUser
        password = options.dbPass
        maximumPoolSize = 10 // Adjust based on your application's needs
    }
    private val dataSource = HikariDataSource(config)
    val db = Database.connect(dataSource)

    init {
        // создаем таблицы, если их нет
        db.useConnection { conn ->
            conn.createStatement().use { statement ->
                statement.execute(
                    """
                CREATE TABLE IF NOT EXISTS roles (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(255) NOT NULL,                    
                    is_admin_panel BOOLEAN NOT NULL DEFAULT FALSE,
                    is_edit_user_and_role BOOLEAN NOT NULL DEFAULT FALSE,
                    is_edit_options BOOLEAN NOT NULL DEFAULT FALSE,
                    is_edit_currency BOOLEAN NOT NULL DEFAULT FALSE,
                    is_edit_news BOOLEAN NOT NULL DEFAULT FALSE,
                    is_edit_direction BOOLEAN NOT NULL DEFAULT FALSE,
                    is_edit_reserve BOOLEAN NOT NULL DEFAULT FALSE,
                    is_edit_notify BOOLEAN NOT NULL DEFAULT FALSE,
                    is_edit_review BOOLEAN NOT NULL DEFAULT FALSE,
                    is_send_referral_payouts BOOLEAN NOT NULL DEFAULT FALSE,
                    is_send_cashback_payouts BOOLEAN NOT NULL DEFAULT FALSE
                );
                """.trimIndent()
                )
                statement.execute(
                    """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    name VARCHAR(64) NOT NULL,
                    password VARCHAR(128) NOT NULL,
                    mail VARCHAR(64) NOT NULL,
                    role_id INTEGER NOT NULL,
                    cashback_percent DOUBLE DEFAULT NULL,                    
                    cashback_type ENUM('from_sum', 'from_profit') DEFAULT 'from_sum',
                    referral_percent DOUBLE DEFAULT NULL,                     
                    referral_type ENUM('from_sum', 'from_profit') DEFAULT 'from_sum',
                    referral_id INTEGER DEFAULT NULL,
                    FOREIGN KEY (role_id) REFERENCES roles(id)
                );
                """.trimIndent()
                )
                statement.execute(
                    """CREATE TABLE IF NOT EXISTS sessions (
                    token VARCHAR(255) PRIMARY KEY,
                    user_id INTEGER NOT NULL,
                    last_activity DATETIME NOT NULL,
                    CONSTRAINT fk_users_sessions FOREIGN KEY (user_id)
                        REFERENCES users(id) ON DELETE CASCADE
                    );                                        
                    """.trimIndent()
                )
                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS options (
                        id INTEGER PRIMARY KEY AUTO_INCREMENT,
                        session_timeout_minutes INTEGER DEFAULT 30,
                        max_requests_serious INTEGER DEFAULT 30,
                        max_requests INTEGER DEFAULT 120,
                        is_random_cookie BOOLEAN DEFAULT TRUE,
                        random_cookie_interval INTEGER DEFAULT 15,
                        random_cookie_attempts INTEGER DEFAULT 10,
                        random_cookie_name VARCHAR(36) DEFAULT "random_name",
                        random_cookie_updated DATETIME DEFAULT NULL,
                        smtp_server VARCHAR(64) DEFAULT "",
                        smtp_port INTEGER DEFAULT 25,
                        smtp_login VARCHAR(64) DEFAULT "",
                        smtp_password VARCHAR(128) DEFAULT "",
                        is_export_courses BOOLEAN DEFAULT TRUE,
                        is_maintenance BOOLEAN DEFAULT FALSE,
                        telegram_bot_token VARCHAR(64) DEFAULT "",
                        telegram_bot_name VARCHAR(32) DEFAULT "",
                        telegram_group_id BIGINT DEFAULT NULL,
                        admin_email varchar(64) DEFAULT "",
                        cashback_percent DOUBLE DEFAULT 0.0,                    
                        cashback_type ENUM('from_sum', 'from_profit') DEFAULT 'from_sum',
                        referral_percent DOUBLE DEFAULT 0.0,                     
                        referral_type ENUM('from_sum', 'from_profit') DEFAULT 'from_sum'                       
                        );
                    """.trimIndent()
                )
                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS cookie_bans (                        
                        ip_address VARCHAR(45) NOT NULL PRIMARY KEY,
                        tokens INTEGER NOT NULL,
                        last_refill DATETIME NOT NULL,
                        ban_expiry DATETIME DEFAULT NULL
                        );
                    """.trimIndent()
                )
                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS reviews (                        
                        id INTEGER PRIMARY KEY AUTO_INCREMENT,
                        date DATETIME NOT NULL,
                        mail VARCHAR(64) NOT NULL,
                        caption VARCHAR(64) NOT NULL,
                        text VARCHAR(2048) NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        rating INTEGER NOT NULL
                        );
                    """.trimIndent()
                )
                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS stop_words (
                            text VARCHAR(255) PRIMARY KEY
                        );
                    """.trimIndent()
                )
                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS notify_users(
                        field_name VARCHAR(64) PRIMARY KEY,
                        subject VARCHAR(64) DEFAULT "",
                        text VARCHAR(2048) DEFAULT ""
                        );
                    """.trimIndent()
                )
                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS notify_admin(
                        field_name VARCHAR(64) PRIMARY KEY,
                        subject VARCHAR(64) DEFAULT "",
                        text VARCHAR(2048) DEFAULT ""
                        );
                    """.trimIndent()
                )
                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS news (
                        id INTEGER PRIMARY KEY AUTO_INCREMENT,
                        date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        caption VARCHAR(255) NOT NULL,
                        text TEXT NOT NULL
                        );                        
                    """.trimIndent()
                )
                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS currencies (
                        id INTEGER PRIMARY KEY AUTO_INCREMENT,
                        name VARCHAR(128) NOT NULL,
                        code VARCHAR(32) NOT NULL,
                        xml_code VARCHAR(32) NOT NULL,
                        fidelity INTEGER NOT NULL,
                        acct_validator VARCHAR(64) DEFAULT "",
                        acct_chain VARCHAR(64) DEFAULT "",
                        is_enabled BOOLEAN DEFAULT FALSE,
                        payin VARCHAR(32) DEFAULT "manual",
                        payin_code VARCHAR(32) DEFAULT "",
                        payin_chain VARCHAR(32) DEFAULT "",
                        payout VARCHAR(32) DEFAULT "manual",
                        payout_code VARCHAR(32) DEFAULT "",
                        payout_chain VARCHAR(32) DEFAULT ""
                        );
                    """.trimIndent()
                )
                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS currency_fields (
                        id INTEGER PRIMARY KEY AUTO_INCREMENT,
                        name VARCHAR(128) NOT NULL,
                        is_required BOOLEAN NOT NULL,
                        hint_account_from VARCHAR(512) NOT NULL,
                        hint_account_to VARCHAR(512) NOT NULL
                        );
                    """.trimIndent()
                )
                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS currency_give_fields (
                        id INTEGER PRIMARY KEY AUTO_INCREMENT,
                        currency_id INTEGER NOT NULL,
                        field_id INTEGER NOT NULL,
                        FOREIGN KEY (currency_id) REFERENCES currencies(id),
                        FOREIGN KEY (field_id) REFERENCES currency_fields(id)
                        );
                    """.trimIndent()
                )
                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS currency_get_fields (
                        id INTEGER PRIMARY KEY AUTO_INCREMENT,
                        currency_id INTEGER NOT NULL,
                        field_id INTEGER NOT NULL,
                        FOREIGN KEY (currency_id) REFERENCES currencies(id),
                        FOREIGN KEY (field_id) REFERENCES currency_fields(id)
                        );
                    """.trimIndent()
                )

                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS exchanges (
                        id INTEGER PRIMARY KEY AUTO_INCREMENT,
                        name VARCHAR(128) NOT NULL,
                        update_period INTEGER DEFAULT 1,
                        max_fail_count INTEGER DEFAULT 3,
                        last_update DATETIME NOT NULL,                        
                        is_enabled BOOLEAN DEFAULT TRUE,
                        url VARCHAR(255) NOT NULL,
                        blacklist VARCHAR(255) DEFAULT "[]"
                        );
                    """.trimIndent()
                )

                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS courses (
                        id INTEGER PRIMARY KEY AUTO_INCREMENT,
                        currency_from VARCHAR(64) NOT NULL,
                        currency_to VARCHAR(64) NOT NULL,
                        exchange_id INTEGER NOT NULL,
                        price DOUBLE DEFAULT 0.0,
                        buy DOUBLE DEFAULT 0.0,
                        sell DOUBLE DEFAULT 0.0,
                        tag VARCHAR(255) NOT NULL,
                        CONSTRAINT fk_exchange_courses FOREIGN KEY (exchange_id)
                            REFERENCES exchanges(id) ON DELETE CASCADE
                        );
                    """.trimIndent()
                )

                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS trade_currencies (
                            id INTEGER PRIMARY KEY AUTO_INCREMENT,
                            exchange_id INTEGER NOT NULL,
                            name VARCHAR(64) NOT NULL,                            
                            CONSTRAINT fk_exchange_trade_currencies FOREIGN KEY (exchange_id)
                                REFERENCES exchanges(id) ON DELETE CASCADE
                        )
                    """.trimIndent()
                )

                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS cmc_currencies (
                            id INTEGER PRIMARY KEY AUTO_INCREMENT,
                            cmc_id INTEGER NOT NULL,
                            name VARCHAR(64) NOT NULL
                        );              
                    """.trimIndent()
                )

                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS api_keys (
                            id INTEGER PRIMARY KEY AUTO_INCREMENT,
                            exchange_id INTEGER NOT NULL,
                            api_key VARCHAR(255) NOT NULL DEFAULT '',
                            secret_key VARCHAR(255) NOT NULL DEFAULT '',
                            CONSTRAINT fk_exchange_api_keys FOREIGN KEY (exchange_id)
                                REFERENCES exchanges(id) ON DELETE CASCADE
                        );              
                    """.trimIndent()
                )

                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS directions(
                            id INTEGER PRIMARY KEY AUTO_INCREMENT,
                            currency_from_id INTEGER NOT NULL,
                            currency_to_id INTEGER NOT NULL,
                            is_active BOOLEAN NOT NULL,
                            is_export BOOLEAN NOT NULL,
                            min_sum DOUBLE NOT NULL,
                            min_sum_currency_id INTEGER NOT NULL,
                            max_sum DOUBLE NOT NULL,                            
                            max_sum_currency_id INTEGER NOT NULL,
                            profit DOUBLE NOT NULL,
                            formula_id INTEGER NOT NULL,
                            status_id INTEGER NOT NULL
                        );
                    """.trimIndent()
                )

                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS status_templates(
                            id INTEGER PRIMARY KEY AUTO_INCREMENT,
                            caption VARCHAR(128) NOT NULL,
                            last_updated DATETIME NOT NULL
                        )
                    """.trimIndent()
                )

                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS statuses(
                            id INTEGER PRIMARY KEY AUTO_INCREMENT,
                            template_id INTEGER NOT NULL,                            
                            status_type VARCHAR(32) NOT NULL,
                            text VARCHAR(4096) NOT NULL,
                            CONSTRAINT fk_template FOREIGN KEY (template_id) 
                                REFERENCES status_templates(id) ON DELETE CASCADE
                        );
                    """.trimIndent()
                )

                statement.execute(
                    """
                        CREATE TABLE IF NOT EXISTS formulas(
                            id INTEGER PRIMARY KEY AUTO_INCREMENT,
                            currency_from VARCHAR(64) NOT NULL,
                            currency_to VARCHAR(64) NOT NULL,
                            tag VARCHAR(255) NOT NULL,
                            reserve VARCHAR(64) NOT NULL,
                            last_updated DATETIME NOT NULL,
                            is_enabled BOOLEAN NOT NULL,
                            spread DOUBLE NOT NULL,
                            price DOUBLE NOT NULL
                        );
                    """.trimIndent()
                )

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS reserves(
                        id INTEGER PRIMARY KEY AUTO_INCREMENT,                        
                        currency VARCHAR(16) NOT NULL, 
                        reserve_currency VARCHAR(16) DEFAULT "", 
                        reserve_type VARCHAR(32) NOT NULL,
                        value DOUBLE DEFAULT 0.0,
                        last_updated DATETIME NOT NULL,
                        exchange_name VARCHAR(32) DEFAULT ""
                    );
                """.trimIndent())

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS payout_requests (
                        id INTEGER AUTO_INCREMENT PRIMARY KEY,
                        ref_id INTEGER NOT NULL,
                        date_created DATETIME NOT NULL,
                        amount DOUBLE NOT NULL,
                        status ENUM('pending', 'finished') DEFAULT 'pending',
                        CONSTRAINT fk_users_payout_requests FOREIGN KEY (ref_id)
                            REFERENCES users(id) ON DELETE CASCADE
                    );
                """.trimIndent())

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS referral_earnings (
                        id INTEGER AUTO_INCREMENT PRIMARY KEY,
                        ref_id INTEGER NOT NULL,
                        order_id INTEGER NOT NULL,
                        earnings DOUBLE NOT NULL,
                        is_paid_out BOOLEAN DEFAULT FALSE,
                        date_created DATETIME NOT NULL,
                        payout_request_id INTEGER DEFAULT NULL,
                        CONSTRAINT fk_payout_requests FOREIGN KEY (payout_request_id)
                            REFERENCES payout_requests(id) ON DELETE SET NULL,
                        CONSTRAINT fk_users_referral_earnings FOREIGN KEY (ref_id)
                            REFERENCES users(id) ON DELETE CASCADE,         
                        UNIQUE (order_id)
                    );
                """.trimIndent())

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS referral_sessions (
                        uuid varchar(36) PRIMARY KEY,
                        ref_id INTEGER NOT NULL,
                        date_created DATETIME NOT NULL                        
                    );
                """.trimIndent())

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS cashback_requests (
                        id INTEGER AUTO_INCREMENT PRIMARY KEY,
                        user_id INTEGER NOT NULL,
                        date_created DATETIME NOT NULL,
                        amount DOUBLE NOT NULL,
                        status ENUM('pending', 'finished') DEFAULT 'pending',
                        CONSTRAINT fk_users_cashback_requests FOREIGN KEY (user_id)
                            REFERENCES users(id) ON DELETE CASCADE
                    );
                """.trimIndent())

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS cashback_earnings (
                        id INTEGER AUTO_INCREMENT PRIMARY KEY,
                        user_id INTEGER NOT NULL,
                        order_id INTEGER NOT NULL,
                        earnings DOUBLE NOT NULL,
                        is_paid_out BOOLEAN DEFAULT FALSE,
                        date_created DATETIME NOT NULL,
                        cashback_request_id INTEGER DEFAULT NULL,
                        CONSTRAINT fk_cashback_requests FOREIGN KEY (cashback_request_id)
                            REFERENCES cashback_requests(id) ON DELETE SET NULL,
                        CONSTRAINT fk_cashback_users FOREIGN KEY (user_id)
                            REFERENCES users(id) ON DELETE CASCADE,         
                        UNIQUE (order_id)
                    );
                """.trimIndent())



                statement.execute("""
                    CREATE TABLE IF NOT EXISTS orders(
                        id INTEGER PRIMARY KEY AUTO_INCREMENT,
                        user_id INTEGER NOT NULL,
                        date_created DATETIME NOT NULL,
                        date_updated DATETIME NOT NULL,
                        wallet_from VARCHAR(64) DEFAULT "",
                        wallet_to VARCHAR(64) NOT NULL,
                        requisites VARCHAR(255) DEFAULT "",
                        amount_from DOUBLE NOT NULL,
                        amount_to DOUBLE NOT NULL,
                        profit DOUBLE DEFAULT 0.0,
                        status VARCHAR(32) NOT NULL,
                        is_active BOOLEAN DEFAULT TRUE,
                        from_name VARCHAR(128) NOT NULL,
                        from_code VARCHAR(32) NOT NULL,
                        from_xml_code VARCHAR(32) NOT NULL,
                        to_name VARCHAR(128) NOT NULL,
                        to_code VARCHAR(32) NOT NULL,
                        to_xml_code VARCHAR(32) NOT NULL,                        
                        fields_give VARCHAR(2048) DEFAULT "{}",
                        fields_get VARCHAR(2048) DEFAULT "{}",
                        is_manual_receive BOOLEAN DEFAULT FALSE,
                        is_manual_send BOOLEAN DEFAULT FALSE,
                        rate_from DOUBLE DEFAULT NULL,
                        rate_to DOUBLE DEFAULT NULL,              
                        payin_fee DOUBLE DEFAULT 0.0,
                        payout_fee DOUBLE DEFAULT 0.0,
                        is_needs_tx_id BOOLEAN DEFAULT FALSE,
                        date_status_updated DATETIME NOT NULL,
                        status_history varchar(2048) default "[]",
                        ref_id INTEGER NULL,
                        order_value DOUBLE DEFAULT NULL
                    );
                """.trimIndent())

                // ускоренный поиск для заявок
                val indexOrdersDate = statement.executeQuery(
                    """
                    SHOW INDEX FROM orders WHERE Key_name = 'idx_orders_date_created';
                    """.trimIndent()
                )
                if(!indexOrdersDate.next()) {
                    statement.execute(
                        """
                        CREATE INDEX idx_orders_date_created ON orders (date_created);
                        """.trimIndent()
                    )
                }

                val indexOrderStatus = statement.executeQuery(
                    """
                    SHOW INDEX FROM orders WHERE Key_name = 'idx_orders_status';
                    """.trimIndent()
                )
                if(!indexOrderStatus.next()) {
                    statement.execute(
                        """
                        CREATE INDEX idx_orders_status ON orders (status);
                        """.trimIndent()
                    )
                }

                // ускоренный поиск для курсов
                val indexFromTo = statement.executeQuery(
                    """
                    SHOW INDEX FROM courses WHERE Key_name = 'idx_exchange_from_to';
                    """.trimIndent()
                )

                if (!indexFromTo.next()) {
                    statement.execute(
                        """
                        CREATE INDEX idx_exchange_from_to ON courses (exchange_id, currency_from, currency_to);
                        """.trimIndent()
                    )
                }

                val indexTag = statement.executeQuery(
                    """
                    SHOW INDEX FROM courses WHERE Key_name = 'idx_tag';
                    """.trimIndent()
                )

                if (!indexTag.next()) {
                    statement.execute(
                        """
                        CREATE INDEX idx_tag ON courses (tag);
                        """.trimIndent()
                    )
                }

                val isConstraintExists =
                    conn.prepareStatement(
                        """
                        SELECT CONSTRAINT_NAME
                        FROM information_schema.TABLE_CONSTRAINTS
                        WHERE TABLE_NAME = 'options'
                          AND CONSTRAINT_TYPE = 'CHECK'
                          AND CONSTRAINT_NAME = 'session_timeout_minutes';
                        """
                    ).executeQuery().next()
                if(!isConstraintExists) statement.execute(
                    """
                        ALTER TABLE options ADD CONSTRAINT session_timeout_minutes
                        CHECK (session_timeout_minutes BETWEEN 1 AND 60*24*7);
                    """.trimIndent()
                )
            }
        }
    }

    fun updateDeleteOldSessionsEvent(sessionTimeoutMinutes: Int) {
        val log = LoggerFactory.getLogger("DatabaseAccess")
        log.debug("updateDeleteOldSessionsEvent()")
        db.useConnection { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP EVENT IF EXISTS delete_old_sessions")

                val createEventQuery = """
                    CREATE EVENT delete_old_sessions
                    ON SCHEDULE EVERY 1 DAY
                    DO
                    DELETE FROM sessions WHERE last_activity < NOW() - INTERVAL $sessionTimeoutMinutes MINUTE;
                """.trimIndent()

                statement.execute(createEventQuery)
            }
        }
    }
}