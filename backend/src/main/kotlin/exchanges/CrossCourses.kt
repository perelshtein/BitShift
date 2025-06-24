package com.github.perelshtein.exchanges

import com.github.perelshtein.ValidationError
import com.github.perelshtein.database.CourseManager
import com.github.perelshtein.database.Exchange
import com.github.perelshtein.database.ExchangeManager
import com.github.perelshtein.database.FormulaManager
import com.github.perelshtein.database.PriceSearchResult
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class CrossCourses: KoinComponent {
    private val mgr: ExchangeManager by inject()
    private val courseMgr: CourseManager by inject()
    private val dijkstra: Dijkstra by inject()
    private val log = LoggerFactory.getLogger("CrossCourses")

    // Обновим граф для поиска кросс-курсов,
    // Учитываем только включенные биржи
    fun update() {
        log.info("Строим граф...")
        val exchanges = mgr.getExchanges()
            .filter { it.isEnabled }
            .map { it to courseMgr.getCoursesMap(it.id) }

        val edges = mutableListOf<Edge>()
        exchanges.forEach { exch ->
            exch.second.forEach {
                edges.add(
                    Edge(source = Vertex(it.key.first), target = Vertex(it.key.second), id = it.value.id)
                )
            }
        }
        dijkstra.addEdges(edges)
        log.info("Граф построен")
    }

//    fun findCachedCourse(from: String, to: String): CrossCourseResult? {
//        val formulaMgr: FormulaManager by inject()
//        formulaMgr.getFormula(from, to)?.let {
//            log.info("Найдена формула в базе для $from -> $to")
//            return CrossCourseResult(it.formula.tag.replace("*", "->"), it.price, it.formula.tag, it.spread)
//        }
//        return null
//    }

    suspend fun findCrossCourses(from: String, to: String): FormulasGenerateAnswer {
        log.info("Ищем курс для $from -> $to...")

        val coinMarketCap: CoinMarketCap by inject()
        val shortestPath = dijkstra.shortestPath(Vertex(from), Vertex(to))
        if(shortestPath == null) {
            val msg = "Курс для $from -> $to не найден"
            log.info(msg)
            throw ValidationError(msg)
        }

        val f = (dijkstra.findAllPathsWithLength(
            Vertex(from),
            Vertex(to),
            minOf(shortestPath.size + 1, 5)
        ) + listOf(shortestPath))
        log.info("f calculated")

        val found = f
            .map { it.joinToString(" -> ") { node -> node.name } to decodePath(it) }
            .sortedBy { it.second.spread }
            .distinctBy { it.second.tag }

        // сравним курс с CoinMarketCap и выберем наименьшее отклонение,
        // если сервис недоступен - возьмем среднее значение
        log.info("Курс найден, ${found.size} вариантов")
        log.info("Ищем референтную цену..")
        val cmcPrice = coinMarketCap.getReferencePrice(from, to)
        val referent =
            if (cmcPrice != null) {
                log.info("Используем среднюю цену с CoinMarketCap: $cmcPrice")
                cmcPrice to "CoinMarketCap"
            } else {
                val average = found.map { it.second.price }.average()
                log.info("CoinMarketCap недоступен. Берем среднее арифметическое для проверки: ${average}")
                average to "average"
            }

        val list = found.map {
            Triple(it.first, it.second, Math.abs(it.second.price - referent.first))
        }
        .sortedBy { it.third }
        .map { CrossCourseResult(it.first, it.second.price, it.second.tag, it.second.spread) }
        log.info("Референтная цена найдена")
        return FormulasGenerateAnswer(referent.first, referent.second, list)
    }

    private fun decodePath(p: List<Vertex>): PriceSearchResult {
        var formula = mutableListOf<String>()
        var sum = 1.0
        var spread = 0.0
        p.windowed(2, 1)
            .forEach {
                val brick = courseMgr.getPriceAndTag(it[0].name, it[1].name) ?:
                    throw ValidationError("Курс в базе для ${it[0].name} -> ${it[1].name} не найден")
                formula += brick.tag
                sum *= brick.price
                spread += brick.spread
            }
        return PriceSearchResult(spread, sum, formula.joinToString(" * "))
    }
}

@Serializable
data class CrossCourseResult(
    val path: String,
    val price: Double,
    val tag: String,
    val spread: Double
)

@Serializable
data class FormulasGenerateAnswer(
    val referentPrice: Double,
    val referentSource: String,
    val list: List<CrossCourseResult>,
)