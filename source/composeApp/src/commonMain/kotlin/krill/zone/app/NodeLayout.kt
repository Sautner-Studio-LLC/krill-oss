package krill.zone.app

import androidx.compose.ui.geometry.*
import krill.zone.shared.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import kotlin.math.*

/**
 * Represents a targeting connection between nodes (source reads from or writes to target).
 * @param sourceId The node that is the source of the connection
 * @param targetId The node that is the target of the connection
 * @param isWriteToTarget If true, source writes to target (arrow points to target).
 *                        If false, source reads from target (arrow points to source).
 */
data class TargetingConnection(
    val sourceId: String,
    val targetId: String,
    val isWriteToTarget: Boolean
)

data class NodeLayout(
    val positions: Map<String, Offset>,
    val parentConnections: Map<String, String>,
    /**
     * Cached targeting connections derived from TargetingNodeMetaData.
     * Computed once during layout to avoid repeated metadata casting during recomposition.
     */
    val targetingConnections: List<TargetingConnection>,
    val width: Float,
    val height: Float,
    val top: Float
)

/**
 * Calculate the total number of descendants for each node in the tree.
 * This "weight" is used to determine spacing - nodes with larger subtrees
 * need more angular space around their parent.
 */
private fun calculateNodeWeights(
    childrenMap: Map<String, List<Node>>,
    nodeMap: Map<String, Node>
): Map<String, Int> {
    val weights = mutableMapOf<String, Int>()

    // Calculate weight recursively with memoization
    fun calculateWeight(nodeId: String, visited: MutableSet<String>): Int {
        // Return cached value if already computed
        weights[nodeId]?.let { return it }

        // Prevent infinite recursion (e.g., server nodes where parent == id)
        if (nodeId in visited) return 1
        visited.add(nodeId)

        val children = childrenMap[nodeId] ?: emptyList()
        // Filter out self-references
        val validChildren = children.filter { it.id != nodeId }
        val weight = 1 + validChildren.sumOf { child -> calculateWeight(child.id, visited) }
        weights[nodeId] = weight
        return weight
    }

    // Calculate weights for all nodes
    nodeMap.keys.forEach { nodeId ->
        calculateWeight(nodeId, mutableSetOf())
    }

    return weights
}

/**
 * Calculate the maximum depth of the subtree rooted at the given node.
 * Used to estimate how much radial space a subtree needs.
 */
private fun calculateSubtreeDepth(
    nodeId: String,
    childrenMap: Map<String, List<Node>>,
    memo: MutableMap<String, Int> = mutableMapOf(),
    visited: MutableSet<String> = mutableSetOf()
): Int {
    memo[nodeId]?.let { return it }

    // Prevent infinite recursion
    if (nodeId in visited) return 0
    visited.add(nodeId)

    val children = childrenMap[nodeId] ?: emptyList()
    // Filter out self-references
    val validChildren = children.filter { it.id != nodeId }
    val depth = if (validChildren.isEmpty()) {
        0
    } else {
        1 + validChildren.maxOf { child -> calculateSubtreeDepth(child.id, childrenMap, memo, visited) }
    }
    memo[nodeId] = depth
    return depth
}

/**
 * Layout children nodes for a server using BFS with weight-based angular distribution.
 *
 * Key improvements over standard radial layout:
 * 1. Children with larger subtrees get more angular space (proportional to subtree weight)
 * 2. Heavy children are distributed to maximize separation (alternating placement)
 * 3. Distance from parent increases for nodes with deep subtrees
 * 4. Uses subtree depth to calculate radius, preventing overlap of deep hierarchies
 */
private fun layoutServerChildren(
    queue: ArrayDeque<String>,
    visited: MutableSet<String>,
    positions: MutableMap<String, Offset>,
    parentConnections: MutableMap<String, String>,
    nodeMap: Map<String, Node>,
    childrenMap: Map<String, List<Node>>
) {
    val baseDistance = when (platform) {
        Platform.IOS -> 210.0
        Platform.ANDROID -> 210.0
        Platform.DESKTOP -> 135.0
        Platform.WASM -> 165.0
        else -> 135.0
    }

    val depthMap = mutableMapOf<String, Int>()
    queue.forEach { depthMap[it] = 0 }

    // Calculate weights and depths for smart distribution
    val nodeWeights = calculateNodeWeights(childrenMap, nodeMap)
    val subtreeDepthMemo = mutableMapOf<String, Int>()

    while (queue.isNotEmpty()) {
        val parentId = queue.removeFirst()
        val parentPosition = positions[parentId] ?: continue
        val parentNode = nodeMap[parentId] ?: continue
        val depth = depthMap[parentId] ?: 0

        val children = childrenMap[parentId] ?: emptyList()
        if (children.isEmpty()) continue

        val parentOfParentPosition = parentNode.parent.let { positions[it] }

        // A node is a "root" if its parent is itself (server nodes set parent = id)
        // In this case parentOfParentPosition == parentPosition
        val isRootNode = parentNode.parent == parentNode.id || parentOfParentPosition == parentPosition

        // Calculate the base angle - pointing away from the grandparent
        val baseAngle = if (!isRootNode && parentOfParentPosition != null) {
            atan2(
                (parentPosition.y - parentOfParentPosition.y).toDouble(),
                (parentPosition.x - parentOfParentPosition.x).toDouble()
            )
        } else {
            // Root node: start from top (north)
            -PI / 2
        }

        // Sweep angle depends on whether this is a root or has a parent
        val sweepAngle = if (!isRootNode && parentOfParentPosition != null) {
            PI * 1.5 // 270 degrees for non-root nodes
        } else {
            2 * PI // Full circle for root
        }

        // Calculate children weights and sort by weight (heaviest first)
        val childrenWithWeights = children
            .filter { it.id !in visited }
            .map { child ->
                val weight = nodeWeights[child.id] ?: 1
                val subtreeDepth = calculateSubtreeDepth(child.id, childrenMap, subtreeDepthMemo)
                Triple(child, weight, subtreeDepth)
            }
            .sortedByDescending { it.second } // Sort by weight, heaviest first

        if (childrenWithWeights.isEmpty()) continue

        // Total weight of all children for proportional distribution
        val totalChildrenWeight = childrenWithWeights.sumOf { it.second }

        // Distribute children using weight-proportional angles
        // Heavy children are interleaved to maximize separation
        val sortedChildren = distributeByWeight(childrenWithWeights)

        // Calculate cumulative angles based on weight
        var cumulativeAngle = 0.0
        val startOffset = if (!isRootNode && parentOfParentPosition != null) {
            baseAngle - sweepAngle / 2
        } else {
            baseAngle
        }

        sortedChildren.forEach { (child, weight, subtreeDepth) ->
            // Each child gets angular space proportional to its weight
            val angularShare = (weight.toDouble() / totalChildrenWeight) * sweepAngle

            // Position at the center of this child's angular slice
            // Special case: root node with single child should be placed directly to the right
            // for cleaner visual alignment (angle = 0)
            val angle = if (isRootNode && sortedChildren.size == 1) {
                0.0 // Place single child of root directly to the right
            } else {
                startOffset + cumulativeAngle + angularShare / 2
            }
            cumulativeAngle += angularShare

            // Distance increases with subtree depth — kept tight to reduce sprawl
            // Slight variation with depth for organic feel without excessive wobble
            val depthBonus = subtreeDepth * baseDistance * 0.15
            // Nodes with targeting relationships (executors, logic gates, serial devices)
            // get a 15% outward push so their arcs route around the outside of the swarm
            val targetingBias = if (child.meta is TargetingNodeMetaData) 1.15 else 1.0
            val distanceVariation = (baseDistance * (1.0 + (depth % 3) * 0.08) + depthBonus) * targetingBias

            val x = parentPosition.x.toDouble() + distanceVariation * cos(angle)
            val y = parentPosition.y.toDouble() + distanceVariation * sin(angle)

            positions[child.id] = Offset(x.toFloat(), y.toFloat())
            parentConnections[child.id] = parentId
            depthMap[child.id] = depth + 1
            visited.add(child.id)
            queue.add(child.id)
        }
    }
}

/**
 * Distribute children to maximize separation between heavy nodes.
 *
 * Strategy: Interleave heavy and light nodes so that heavy subtrees
 * are spread around the parent rather than clustered together.
 *
 * For example, with children sorted by weight [A, B, C, D] (A heaviest):
 * - Place A at position 0 (12 o'clock area)
 * - Place B at position 2 (opposite side, 6 o'clock area)
 * - Place C at position 1 (3 o'clock area)
 * - Place D at position 3 (9 o'clock area)
 *
 * This ensures the two heaviest nodes are as far apart as possible.
 */
private fun <T> distributeByWeight(sortedByWeight: List<T>): List<T> {
    if (sortedByWeight.size <= 2) return sortedByWeight

    val result = MutableList<T?>(sortedByWeight.size) { null }
    val positions = generateInterleavedPositions(sortedByWeight.size)

    sortedByWeight.forEachIndexed { index, item ->
        result[positions[index]] = item
    }

    return result.filterNotNull()
}

/**
 * Generate interleaved position indices to maximize separation.
 * For n items, places the heaviest at 0, next heaviest at n/2, etc.
 */
private fun generateInterleavedPositions(count: Int): List<Int> {
    if (count <= 1) return listOf(0)
    if (count == 2) return listOf(0, 1)

    val positions = mutableListOf<Int>()
    val used = mutableSetOf<Int>()

    // Start with opposite positions: 0, n/2, n/4, 3n/4, etc.
    var step = count
    while (positions.size < count) {
        step = maxOf(1, step / 2)
        var pos = 0
        while (pos < count && positions.size < count) {
            if (pos !in used) {
                positions.add(pos)
                used.add(pos)
            }
            pos += step
        }
    }

    return positions
}

/**
 * Data class to hold swarm bounds information
 */
private data class SwarmBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val width: Float,
    val height: Float
)

/**
 * Calculate the effective radius of a swarm (half the diagonal of its bounding box)
 * Used for ring-based layout to prevent swarm overlaps
 */
private fun calculateSwarmRadius(bounds: SwarmBounds): Float {
    // Use the diagonal distance from center to corner as the effective radius
    // This ensures swarms don't overlap even with rectangular shapes
    val halfWidth = bounds.width / 2f
    val halfHeight = bounds.height / 2f
    return sqrt(halfWidth * halfWidth + halfHeight * halfHeight)
}

/**
 * Calculate the distance from the center of a swarm to its edge in a specific direction.
 * This allows tighter packing by considering the actual shape of the swarm.
 *
 * For a swarm with bounds, calculates how far the swarm extends from its center
 * in the direction specified by the angle (in radians).
 *
 * Uses the intersection of a ray from center at the given angle with the bounding box.
 */
private fun calculateDirectionalExtent(bounds: SwarmBounds, angleRadians: Double): Float {
    if (bounds.width == 0f && bounds.height == 0f) return 0f

    val halfWidth = bounds.width / 2f
    val halfHeight = bounds.height / 2f

    // Edge case: if either dimension is zero, return the other
    if (halfWidth == 0f) return halfHeight
    if (halfHeight == 0f) return halfWidth

    // Calculate direction vector
    val cosAngle = cos(angleRadians).toFloat()
    val sinAngle = sin(angleRadians).toFloat()

    // Find intersection with bounding box edges
    // The ray from center goes in direction (cosAngle, sinAngle)
    // We need to find where it intersects the rectangle [-halfWidth, halfWidth] x [-halfHeight, halfHeight]

    val abscos = abs(cosAngle)
    val abssin = abs(sinAngle)

    // Calculate the parameter t where the ray intersects the box
    val tX = if (abscos > 0.0001f) halfWidth / abscos else Float.MAX_VALUE
    val tY = if (abssin > 0.0001f) halfHeight / abssin else Float.MAX_VALUE

    // The actual intersection is at the smaller t value
    val t = minOf(tX, tY)

    // Return the distance to the intersection point
    return t
}

/**
 * Calculate positions for server nodes using a ring-based layout.
 *
 * Layout strategy:
 * - First server stays centered at origin
 * - Additional servers are arranged in a ring around the first server
 * - Distance for each server is calculated using directional extents
 * - Adjacent servers on the ring are spaced based on their actual shapes
 *
 * This approach provides a stable layout where the first server never moves,
 * eliminating jarring visual shifts when new servers are added.
 */
private fun calculateServerPositions(
    serverNodes: List<Node>,
    tempPositions: Map<String, Map<String, Offset>>
): List<Offset> {
    if (serverNodes.isEmpty()) return emptyList()

    // ===========================================
    // TUNING: Adjust this multiplier to control server spacing
    // 1.0 = calculated distance, <1.0 = closer, >1.0 = farther
    // ===========================================
    val distanceMultiplier = 1.0f

    // Calculate bounds for all server swarms
    val swarmBoundsMap = serverNodes.associate { serverNode ->
        serverNode.id to calculateSwarmBounds(tempPositions[serverNode.id] ?: emptyMap())
    }

    // Special case: single server - center it on screen
    if (serverNodes.size == 1) {
        val swarmBounds = swarmBoundsMap[serverNodes[0].id]!!
        val serverX = 0f  // Center horizontally
        val serverY = -swarmBounds.minY - swarmBounds.height / 2  // Center vertically
        return listOf(Offset(serverX, serverY))
    }

    val positions = mutableListOf<Offset>()

    // Margin between swarm boundaries
    val swarmMargin = when (platform) {
        Platform.IOS, Platform.ANDROID -> 60f
        else -> 40f
    }

    // First server: centered at origin
    val firstServerBounds = swarmBoundsMap[serverNodes[0].id]!!
    val firstServerX = 0f
    val firstServerY = -firstServerBounds.minY - firstServerBounds.height / 2
    positions.add(Offset(firstServerX, firstServerY))

    // Remaining servers: arranged around the first server
    val remainingServers = serverNodes.drop(1)
    if (remainingServers.isNotEmpty()) {
        // Calculate the angle step for even distribution
        val angleStep = 2 * PI / remainingServers.size

        // Start from the right side (0 radians) and go counter-clockwise
        val startAngle = 0.0

        // Calculate individual positions using directional extents
        remainingServers.forEachIndexed { index, serverNode ->
            val thisBounds = swarmBoundsMap[serverNode.id]!!

            // Calculate angle for this server (evenly distributed around the ring)
            val angle = startAngle + index * angleStep

            // Use directional extents for tighter packing
            // First server extends outward in direction of angle
            // Outer server extends inward in opposite direction
            val firstServerExtent = calculateDirectionalExtent(firstServerBounds, angle)
            val outerServerExtent = calculateDirectionalExtent(thisBounds, angle + PI)
            val baseDistance = (firstServerExtent + outerServerExtent + swarmMargin) * distanceMultiplier

            // For adjacent server overlap prevention:
            // Check if adjacent servers on the ring would overlap
            val adjustedDistance = if (remainingServers.size >= 2) {
                val halfAngle = angleStep / 2
                val sinHalfAngle = sin(halfAngle)

                // Use directional extent for angular constraint too
                val maxExtent = remainingServers.maxOf { srv ->
                    val bounds = swarmBoundsMap[srv.id]!!
                    maxOf(bounds.width, bounds.height) / 2f
                }

                val minAngularDistance = if (sinHalfAngle > 0.01) {
                    // Required ring radius so adjacent servers don't overlap
                    ((maxExtent + swarmMargin / 2) / sinHalfAngle.toFloat()) * distanceMultiplier
                } else {
                    baseDistance
                }

                maxOf(baseDistance, minAngularDistance)
            } else {
                baseDistance
            }

            // Position on the ring
            val serverX = firstServerX + (adjustedDistance * cos(angle)).toFloat()
            val serverY = firstServerY + (adjustedDistance * sin(angle)).toFloat() -
                          thisBounds.minY - thisBounds.height / 2 + firstServerBounds.height / 2

            positions.add(Offset(serverX, serverY))
        }
    }

    return positions
}

/**
 * Calculate the bounding box of a server's swarm from temporary positions
 */
private fun calculateSwarmBounds(positions: Map<String, Offset>): SwarmBounds {
    if (positions.isEmpty()) {
        return SwarmBounds(0f, 0f, 0f, 0f, 0f, 0f)
    }

    val minX = positions.values.minOf { it.x }
    val maxX = positions.values.maxOf { it.x }
    val minY = positions.values.minOf { it.y }
    val maxY = positions.values.maxOf { it.y }

    return SwarmBounds(
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
        width = maxX - minX,
        height = maxY - minY
    )
}


fun computeNodePositions(swarm: Set<String>, nodeManager: ClientNodeManager): NodeLayout {

    val positions = mutableMapOf<String, Offset>()
    val parentConnections = mutableMapOf<String, String>()
    val nodes = mutableListOf<Node>()



    swarm.forEach { nodeId ->
        try {
            val node = nodeManager.readNodeState(nodeId).value
            nodes.add(node)


        } catch (_: Exception) {
            // Node not found - skip it
        }
    }

    val nodeMap = nodes.associateBy { it.id }
    val childrenMap = nodes.groupBy { it.parent }

    // Find all Server nodes - these are our independent roots
    val serverNodes = nodes.filter { it.type is KrillApp.Server }

    if (serverNodes.isEmpty()) {
        // No servers, return empty layout
        return NodeLayout(emptyMap(), emptyMap(), emptyList(), 0f, 0f, 0f)
    }

    // First pass: layout each server's children at origin to calculate their bounds
    val tempPositions = mutableMapOf<String, Map<String, Offset>>()
    serverNodes.forEach { serverNode ->
        val tempServerPositions = mutableMapOf<String, Offset>()
        val tempParentConnections = mutableMapOf<String, String>()
        val visited = mutableSetOf<String>()

        // Position server at origin for this temporary layout
        tempServerPositions[serverNode.id] = Offset.Zero
        visited.add(serverNode.id)

        val queue = ArrayDeque<String>()
        queue.add(serverNode.id)

        layoutServerChildren(
            queue = queue,
            visited = visited,
            positions = tempServerPositions,
            parentConnections = tempParentConnections,
            nodeMap = nodeMap,
            childrenMap = childrenMap
        )

        tempPositions[serverNode.id] = tempServerPositions
    }

    // Second pass: calculate actual server positions based on swarm bounds
    val serverPositions = calculateServerPositions(serverNodes, tempPositions)

    // Third pass: layout all servers with their calculated positions
    val visited = mutableSetOf<String>()
    serverNodes.forEachIndexed { index, serverNode ->
        val serverPosition = serverPositions[index]
        positions[serverNode.id] = serverPosition
        visited.add(serverNode.id)

        val queue = ArrayDeque<String>()
        queue.add(serverNode.id)

        layoutServerChildren(
            queue = queue,
            visited = visited,
            positions = positions,
            parentConnections = parentConnections,
            nodeMap = nodeMap,
            childrenMap = childrenMap
        )
    }

    // Targeting affinity pass: pull arc-connected nodes closer together
    // before force simulation, so source/target pairs start near each other
    nodes.forEach { node ->
        val meta = node.meta as? TargetingNodeMetaData ?: return@forEach
        val nodePos = positions[node.id] ?: return@forEach

        // Pull toward each target
        meta.targets.filter { it.nodeId.isNotBlank() }.forEach { target ->
            val targetPos = positions[target.nodeId] ?: return@forEach
            val midX = (nodePos.x + targetPos.x) / 2f
            val midY = (nodePos.y + targetPos.y) / 2f
            // Move each 20% toward their midpoint
            positions[node.id] = Offset(
                nodePos.x + (midX - nodePos.x) * 0.2f,
                nodePos.y + (midY - nodePos.y) * 0.2f
            )
            positions[target.nodeId] = Offset(
                targetPos.x + (midX - targetPos.x) * 0.2f,
                targetPos.y + (midY - targetPos.y) * 0.2f
            )
        }

        // Pull toward each source
        meta.sources.filter { it.nodeId.isNotBlank() }.forEach { source ->
            val sourcePos = positions[source.nodeId] ?: return@forEach
            val currentPos = positions[node.id] ?: return@forEach
            val midX = (currentPos.x + sourcePos.x) / 2f
            val midY = (currentPos.y + sourcePos.y) / 2f
            positions[node.id] = Offset(
                currentPos.x + (midX - currentPos.x) * 0.2f,
                currentPos.y + (midY - currentPos.y) * 0.2f
            )
            positions[source.nodeId] = Offset(
                sourcePos.x + (midX - sourcePos.x) * 0.2f,
                sourcePos.y + (midY - sourcePos.y) * 0.2f
            )
        }
    }

    var minX = 0f
    var maxX = 0f
    var minY = 0f
    var maxY = 0f

    if (positions.isNotEmpty()) {
        minX = positions.values.minOf { it.x }
        maxX = positions.values.maxOf { it.x }
        minY = positions.values.minOf { it.y }
        maxY = positions.values.maxOf { it.y }
    }

    val width = maxX - minX
    val height = maxY - minY

    // Compute targeting connections from TargetingNodeMetaData
    // This is cached here to avoid repeated metadata casting during recomposition
    val targetingConnections = buildList {
        nodes.forEach { node ->
            val meta = node.meta as? TargetingNodeMetaData
            if (meta != null) {
                // Node writes to each target (arrow points to target)
                meta.targets.filter { it.nodeId.isNotBlank() && positions.containsKey(it.nodeId) }.forEach { target ->
                    add(TargetingConnection(sourceId = node.id, targetId = target.nodeId, isWriteToTarget = true))
                }
                // Node reads from each source (arrow points to node)
                meta.sources.filter { it.nodeId.isNotBlank() && positions.containsKey(it.nodeId) }.forEach { source ->
                    add(TargetingConnection(sourceId = source.nodeId, targetId = node.id, isWriteToTarget = false))
                }
            }
        }
    }

    val layout = NodeLayout(positions, parentConnections, targetingConnections, width, height, minY)
    return optimizedNodeLayout(layout)
}

fun optimizedNodeLayout(original: NodeLayout): NodeLayout {
    if (original.positions.isEmpty()) return original

    val positions = original.positions.toMutableMap()
    val nodeRadius = when (platform) {
        Platform.IOS -> 40f
        Platform.ANDROID -> 40f
        Platform.DESKTOP -> 25f
        Platform.WASM -> 25f
        else -> 25f
    }

    val minDistance = nodeRadius * 3.0f
    val iterations = 80
    val dampening = 0.9f
    val maxRadius = 1500f

    // Build list of edges for crossing detection
    val edges = original.parentConnections.map { (childId, parentId) ->
        childId to parentId
    }

    // Force simulation to optimize layout — stops early if energy is low
    for (iteration in 0 until iterations) {
        val forces = mutableMapOf<String, Offset>()
        positions.keys.forEach { nodeId -> forces[nodeId] = Offset.Zero }

        // Apply repulsive forces between all nodes
        applyNodeRepulsion(positions, forces, minDistance)

        // Apply spring forces for parent-child connections
        applySpringForces(original.parentConnections, positions, forces)

        // Apply weak spring forces for targeting connections (source↔target proximity)
        applyTargetingSpringForces(original.targetingConnections, positions, forces)

        // Apply forces to push nodes away from non-adjacent edges (crossing prevention)
        applyEdgeCrossingForces(positions, forces, edges, nodeRadius)

        // Apply boundary forces
        applyBoundaryForces(positions, forces, maxRadius)

        // Early termination: if total energy is negligible, stop
        val totalEnergy = forces.values.sumOf { sqrt((it.x * it.x + it.y * it.y).toDouble()) }
        if (totalEnergy < 1.0 && iteration > 10) break

        // Apply forces with dampening
        applyForcesWithDampening(positions, forces, iteration, iterations, dampening, maxRadius)
    }

    return recalculateBounds(positions, original.parentConnections, original.targetingConnections)
}

private fun applyNodeRepulsion(
    positions: Map<String, Offset>,
    forces: MutableMap<String, Offset>,
    minDistance: Float
) {
    positions.keys.forEach { nodeId1 ->
        positions.keys.forEach { nodeId2 ->
            if (nodeId1 != nodeId2) {
                val pos1 = positions[nodeId1]!!
                val pos2 = positions[nodeId2]!!
                val dx = pos1.x - pos2.x
                val dy = pos1.y - pos2.y
                val distance = sqrt(dx * dx + dy * dy)

                if (distance > 0 && distance < minDistance * 4) {
                    val force = (minDistance * minDistance * 2f) / (distance * distance + 1f)
                    val forceX = (dx / distance) * force
                    val forceY = (dy / distance) * force
                    forces[nodeId1] = forces[nodeId1]!! + Offset(forceX, forceY)
                }
            }
        }
    }
}

private fun applySpringForces(
    parentConnections: Map<String, String>,
    positions: Map<String, Offset>,
    forces: MutableMap<String, Offset>
) {
    // Spring ideal distance matches tighter base distances
    val idealDistance = when (platform) {
        Platform.IOS -> 170f
        Platform.ANDROID -> 170f
        else -> 110f
    }

    parentConnections.forEach { (childId, parentId) ->
        val childPos = positions[childId]
        val parentPos = positions[parentId]

        if (childPos != null && parentPos != null) {
            val dx = parentPos.x - childPos.x
            val dy = parentPos.y - childPos.y
            val distance = sqrt(dx * dx + dy * dy)

            if (distance > 0) {
                val springForce = (distance - idealDistance) * 0.15f
                val forceX = (dx / distance) * springForce
                val forceY = (dy / distance) * springForce

                forces[childId] = forces[childId]!! + Offset(forceX, forceY)
                forces[parentId] = forces[parentId]!! - Offset(forceX * 0.3f, forceY * 0.3f)
            }
        }
    }
}

/**
 * Weak spring forces pulling source↔target connected nodes closer together.
 * Weaker than parent-child springs (0.05 vs 0.15) to avoid disrupting the tree structure.
 */
private fun applyTargetingSpringForces(
    targetingConnections: List<TargetingConnection>,
    positions: Map<String, Offset>,
    forces: MutableMap<String, Offset>
) {
    targetingConnections.forEach { conn ->
        val sourcePos = positions[conn.sourceId] ?: return@forEach
        val targetPos = positions[conn.targetId] ?: return@forEach

        val dx = targetPos.x - sourcePos.x
        val dy = targetPos.y - sourcePos.y
        val distance = sqrt(dx * dx + dy * dy)

        if (distance > 0) {
            // Pull them together with a weak spring
            val springForce = distance * 0.05f
            val forceX = (dx / distance) * springForce
            val forceY = (dy / distance) * springForce

            forces[conn.sourceId]?.let { forces[conn.sourceId] = it + Offset(forceX, forceY) }
            forces[conn.targetId]?.let { forces[conn.targetId] = it - Offset(forceX, forceY) }
        }
    }
}

/**
 * Push nodes away from edges they're too close to (but aren't connected to).
 * This helps prevent edge crossings by keeping nodes away from non-adjacent edges.
 */
private fun applyEdgeCrossingForces(
    positions: Map<String, Offset>,
    forces: MutableMap<String, Offset>,
    edges: List<Pair<String, String>>,
    nodeRadius: Float
) {
    val edgeAvoidanceDistance = nodeRadius * 4f

    positions.forEach { (nodeId, nodePos) ->
        edges.forEach { (edgeStart, edgeEnd) ->
            // Skip if this node is an endpoint of the edge
            if (nodeId == edgeStart || nodeId == edgeEnd) return@forEach

            val startPos = positions[edgeStart] ?: return@forEach
            val endPos = positions[edgeEnd] ?: return@forEach

            // Calculate distance from node to the edge line segment
            val closestPoint = closestPointOnSegment(nodePos, startPos, endPos)
            val dx = nodePos.x - closestPoint.x
            val dy = nodePos.y - closestPoint.y
            val distance = sqrt(dx * dx + dy * dy)

            if (distance > 0 && distance < edgeAvoidanceDistance) {
                // Push node away from edge
                val force = (edgeAvoidanceDistance - distance) * 0.5f
                val forceX = (dx / distance) * force
                val forceY = (dy / distance) * force
                forces[nodeId] = forces[nodeId]!! + Offset(forceX, forceY)
            }
        }
    }
}

/**
 * Find the closest point on a line segment to a given point.
 */
private fun closestPointOnSegment(point: Offset, segStart: Offset, segEnd: Offset): Offset {
    val segmentX = segEnd.x - segStart.x
    val segmentY = segEnd.y - segStart.y
    val segmentLengthSq = segmentX * segmentX + segmentY * segmentY

    if (segmentLengthSq == 0f) return segStart // Segment is a point

    // Project point onto line, clamping to segment
    val t = maxOf(0f, minOf(1f,
        ((point.x - segStart.x) * segmentX + (point.y - segStart.y) * segmentY) / segmentLengthSq
    ))

    return Offset(
        segStart.x + t * segmentX,
        segStart.y + t * segmentY
    )
}

private fun applyBoundaryForces(
    positions: Map<String, Offset>,
    forces: MutableMap<String, Offset>,
    maxRadius: Float
) {
    positions.forEach { (nodeId, pos) ->
        val distanceFromCenter = sqrt(pos.x * pos.x + pos.y * pos.y)
        if (distanceFromCenter > maxRadius * 0.8f) {
            val boundaryForce = (distanceFromCenter - maxRadius * 0.8f) * 0.2f
            val forceX = -(pos.x / distanceFromCenter) * boundaryForce
            val forceY = -(pos.y / distanceFromCenter) * boundaryForce
            forces[nodeId] = forces[nodeId]!! + Offset(forceX, forceY)
        }
    }
}

private fun applyForcesWithDampening(
    positions: MutableMap<String, Offset>,
    forces: Map<String, Offset>,
    iteration: Int,
    totalIterations: Int,
    dampening: Float,
    maxRadius: Float
) {
    val currentDampening = dampening * (1f - iteration.toFloat() / totalIterations)
    val maxForce = 25f

    forces.forEach { (nodeId, force) ->
        val currentPos = positions[nodeId]!!
        val forceMagnitude = sqrt(force.x * force.x + force.y * force.y)

        val limitedForce = if (forceMagnitude > maxForce) {
            Offset((force.x / forceMagnitude) * maxForce, (force.y / forceMagnitude) * maxForce)
        } else {
            force
        }

        val newPos = Offset(
            currentPos.x + limitedForce.x * currentDampening,
            currentPos.y + limitedForce.y * currentDampening
        )

        val distFromCenter = sqrt(newPos.x * newPos.x + newPos.y * newPos.y)
        positions[nodeId] = if (distFromCenter > maxRadius) {
            Offset((newPos.x / distFromCenter) * maxRadius, (newPos.y / distFromCenter) * maxRadius)
        } else {
            newPos
        }
    }
}

private fun recalculateBounds(
    positions: Map<String, Offset>,
    parentConnections: Map<String, String>,
    targetingConnections: List<TargetingConnection>
): NodeLayout {
    if (positions.isEmpty()) return NodeLayout(positions, parentConnections, emptyList(), 0f, 0f, 0f)

    val minX = positions.values.minOf { it.x }
    val maxX = positions.values.maxOf { it.x }
    val minY = positions.values.minOf { it.y }
    val maxY = positions.values.maxOf { it.y }

    return NodeLayout(positions, parentConnections, targetingConnections, maxX - minX, maxY - minY, minY)
}

