# Verificacion funcional del simulador compartido

Base Git: `7373a8e` (merge de `alns/simulacion-colapso`, origen `a5875fd`). Las corridas usan el nuevo ejecutor del mismo commit que incorpora este documento, sobre esa base. No son campañas estadisticas finales: solo dos iteraciones y una semilla, sin calentamiento dedicado de la JVM.

- `verificacion-simulacion-20260923`: limite de 12 ciclos; TS y ALNS terminaron por limite a las 02:00, con una ruta comprometida y ninguna entrega completada todavia.
- `verificacion-8h-20260923`: limite de 48 ciclos; ambos encontraron colapso de planificacion a las 05:00 del 01/01/2026. Cada uno habia entregado un pedido de dos paquetes. El ciclo del colapso contiene un pedido de seis paquetes sin plan completo. Esto requiere diagnostico operativo antes de una campaña larga; no demuestra inviabilidad matematica.

Los metadatos y CSV conservan parametros y mediciones. Los tiempos son observaciones de estas ejecuciones, no una conclusion sobre superioridad de un algoritmo. La primera corrida precede un cambio puramente visual que sustituye NaN por N/D en consola; los CSV usan celdas vacias para metricas ausentes.

Validacion: compilacion Java 17, 15 grupos de restricciones comunes, pruebas de experimentacion y pruebas de simulacion compartida aprobadas.
