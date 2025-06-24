package com.github.perelshtein.exchanges

import org.koin.core.component.KoinComponent
import org.slf4j.LoggerFactory
import java.util.PriorityQueue

data class Vertex(val name: String)
data class Edge(val source: Vertex, val target: Vertex, val id: Int)

class Dijkstra: KoinComponent {
    private var adjacencyList: MutableMap<Vertex, MutableList<Edge>> = mutableMapOf()

    fun addEdges(edges: List<Edge>) {
        val freshAdjacencyList: MutableMap<Vertex, MutableList<Edge>> = mutableMapOf()
        edges.forEach { edge ->
            freshAdjacencyList.computeIfAbsent(edge.source) { mutableListOf() }
                .add(edge)
        }
        // copy-on-write
        adjacencyList = freshAdjacencyList
    }

    fun shortestPath(start: Vertex, end: Vertex): List<Vertex>? {
        val distances = mutableMapOf<Vertex, Int>().withDefault { Int.MAX_VALUE }
        val previous = mutableMapOf<Vertex, Vertex?>()
        val queue = PriorityQueue<Vertex>(compareBy { distances[it] ?: Int.MAX_VALUE })

        distances[start] = 0
        queue.add(start)

        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: break

            for (neighbor in adjacencyList[current] ?: emptyList()) {
                val alt = distances.getValue(current) + 1
                if (alt < distances.getValue(neighbor.target)) {
                    distances[neighbor.target] = alt
                    previous[neighbor.target] = current
                    queue.remove(neighbor.target) // Update priority queue
                    queue.add(neighbor.target)
                }
            }
        }
        return reconstructPath(previous, start, end)
    }

    // length - длина пути, вершин
    fun findAllPathsWithLength(start: Vertex, end: Vertex, length: Int): List<List<Vertex>> {
        val log = LoggerFactory.getLogger("Dijkstra")
        log.info("start DFS..")
        require(length >= 2, {"Поиск вариантов курса: минимальная длина пути >= 2"})
        val result = mutableListOf<List<Vertex>>()
        val currentPath = mutableListOf<Vertex>()
        findPathsDFS(start, end, length - 1, currentPath, result)
        log.info("end DFS")
        return result
    }

    private fun findPathsDFS(
        current: Vertex,
        end: Vertex,
        remainingLength: Int,
        currentPath: MutableList<Vertex>,
        result: MutableList<List<Vertex>>
    ) {
        currentPath.add(current)

        if (remainingLength == 0) {
            if (current == end) {
                result.add(ArrayList(currentPath)) // Add a copy of the current path
            }
        } else {
            for (edge in adjacencyList[current] ?: emptyList()) {
                findPathsDFS(edge.target, end, remainingLength - 1, currentPath, result)
            }
        }

        currentPath.removeAt(currentPath.size - 1) // Backtrack
    }

    private fun reconstructPath(previous: Map<Vertex, Vertex?>, start: Vertex, end: Vertex): List<Vertex>? {
        if (previous[end] == null && end != start) return null
        val path = mutableListOf<Vertex>()
        var current: Vertex? = end
        while (current != null) {
            path.add(current)
            current = previous[current]
        }
        path.reverse()
        return path
    }
}
