import com.github.perelshtein.database.DatabaseAccess
import com.github.perelshtein.Options
import com.github.perelshtein.startKoinModules
import com.github.perelshtein.OptionsManager
import com.github.perelshtein.toDB
import com.github.perelshtein.toDTO
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.ktorm.database.Database
import org.ktorm.dsl.insert
import org.ktorm.schema.Table
import org.ktorm.entity.*
import org.ktorm.schema.int
import kotlin.getValue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Test class
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OptionConversionTest: KoinComponent {
    private val optionsManager: OptionsManager by inject()

    @BeforeAll
    fun setup() {
        startKoinModules()
//        startKoin {
//            modules (
//                singleOf(::Options)
//                singleOf(::DatabaseAccess)
//                singleOf(::OptionsManager)
//            )
//        }
    }

    @AfterAll
    fun teardown() {
        stopKoin()
    }

    @Test
    fun `test toDTO and toDB conversion`() {
        val opt = optionsManager.getOptions()

        // Test toDTO conversion
        val dto = opt.toDTO()
        assertEquals(20, dto.sessionTimeoutMinutes, "toDTO() should correctly map sessionTimeoutMinutes")

        // Test toDB conversion
        val newEntity = dto.toDB()
        assertEquals(20, newEntity.sessionTimeoutMinutes, "toDB() should correctly map sessionTimeoutMinutes")
    }
}
