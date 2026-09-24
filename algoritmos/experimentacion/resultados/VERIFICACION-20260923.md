# Verificacion funcional del simulador compartido

Base Git: `7373a8e` (merge de `alns/simulacion-colapso`, origen `a5875fd`). Las corridas usan el nuevo ejecutor del mismo commit que incorpora este documento, sobre esa base. No son campañas estadisticas finales: solo dos iteraciones y una semilla, sin calentamiento dedicado de la JVM.

- `verificacion-simulacion-20260923`: limite de 12 ciclos; TS y ALNS terminaron por limite a las 02:00, con una ruta comprometida y ninguna entrega completada todavia.
- `verificacion-8h-20260923`: resultado anterior a la correccion de espera entre turnos. Ambos encontraron un falso colapso a las 05:00 porque la ruta no cabia antes de las 07:00 y el evaluador no probaba el siguiente turno.
- `verificacion-turnos-20260923`: repeticion de 48 ciclos despues de la correccion. TS y ALNS llegan a las 08:00 por limite de ciclos sin colapsar; el pedido de las 04:55 queda planificado para salir a partir de las 07:00.

Los metadatos y CSV conservan parametros y mediciones. Los tiempos son observaciones de estas ejecuciones, no una conclusion sobre superioridad de un algoritmo. La primera corrida precede un cambio puramente visual que sustituye NaN por N/D en consola; los CSV usan celdas vacias para metricas ausentes.

Validacion: compilacion Java 17, 15 grupos de restricciones comunes, pruebas de experimentacion y pruebas de simulacion compartida aprobadas.
