package com.grimfox.triangle

class Configuration(
        var predicates: () -> Predicates = { Predicates.default },
        var trianglePool: () -> TrianglePool = { TrianglePool() })
