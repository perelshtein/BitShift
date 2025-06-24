package com.github.perelshtein.routes

import com.github.perelshtein.database.Formula
import com.github.perelshtein.database.Formula.Companion.invoke
import com.github.perelshtein.database.FormulaManager
import com.github.perelshtein.database.toDTO
import com.github.perelshtein.exchanges.CrossCourses
import com.github.perelshtein.withUser
import io.ktor.server.response.respondText
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory

class ConvertPrice: KoinComponent {
    val formulaMgr: FormulaManager = KoinJavaComponent.get(FormulaManager::class.java)
    val cross: CrossCourses = KoinJavaComponent.get(CrossCourses::class.java)
    val log = LoggerFactory.getLogger("ConvertPrice")

    suspend fun convertAmount(amount: Double, fromCurrency: String, toCurrency: String): Double {
        if(fromCurrency == toCurrency) return amount
        var formulaDTO = formulaMgr.getFormula(fromCurrency, toCurrency)

        // ищем новую формулу
        if (formulaDTO == null) {
            log.info("Ищем новую формулу для $fromCurrency -> $toCurrency...")
            val courses = cross.findCrossCourses(fromCurrency, toCurrency)
            if (courses.list.isEmpty()) {
                return 0.0 // без вариантов
            }

            val formula = Formula {
                this.from = fromCurrency
                this.to = toCurrency
                this.tag = courses.list.first().tag
                this.reserve = "auto"
                this.isEnabled = true
                this.spread = courses.list.first().spread
                this.price = courses.list.first().price
            }
            formulaMgr.upsertFormula(formula)
            formulaDTO = formula.toDTO()
        }

        return formulaDTO?.price?.let { price ->
            amount * price
        } ?: 0.0
    }
}