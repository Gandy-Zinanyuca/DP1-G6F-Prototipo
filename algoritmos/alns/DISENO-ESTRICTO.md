# PaqRap — ALNS con contrato estricto

Esta variante vive en pe.pucp.paqrap.alns.estricto y se usa para comparar con Tabu Search.
El motor ALNS histórico y su documentación se conservan para reproducir experimentos previos.

## 1. Problema, estructuras y restricciones

Utiliza exactamente EstadoOperacion, ParametrosOperacion, partes, solución inicial,
EvaluadorFactibilidad y ResultadoPlanificacion descritos en el
[diseño común/TS](../tabu/DISENO-ALGORITMOS.md), secciones 1–4.
No se aceptan plazos blandos ni salida del turno. Las partes imposibles se reportan pendientes.

## 2. Operadores y aceptación

Destrucción: selección aleatoria de partes, o partes relacionadas por distancia a un pivote.
Ambas omiten rutas en curso comprometidas. El número retirado está acotado por configuración.

Reparación: inserción por plazo/ID o en orden aleatorio, probando vehículo, almacén y posición.
Solo se aplican inserciones que pasan el evaluador común.

Se reutilizan SelectorAdaptativo y CriterioAceptacion de la implementación original.
Las ruletas de destrucción y reparación se actualizan por segmentos; recompensas
8 por nueva mejor, 4 por mejora actual, 1 por aceptación. Una candidata inviable no se acepta,
aunque la temperatura sea alta. Se conserva siempre la mejor solución.

## 3. Pseudocódigo

```text
actual = mejor = constructor común
repetir hasta parada:
    seleccionar destructor y reparador por ruleta adaptativa
    retirar partes de una copia inmutable
    reinsertar con factibilidad estricta
    evaluar el candidato
    actualizar mejor si mejora
    aceptar mejora/empeoramiento según temperatura, solo si factible
    premiar operadores, enfriar y actualizar pesos por segmento
auditar y devolver mejor + métricas comunes
```

## 4. Configuración

ConfiguracionALNS define maxIteraciones, sinMejoraMax, destruccionMax, segmento,
reaccion, aceptacionInicial, presupuestoMs y semilla.
El criterio de parada por tiempo es cooperativo entre iteraciones; no interrumpe una
construcción o reparación a mitad. Para regresiones, presupuestoMs=0.

La variante no reproduce todos los operadores de la cartera histórica. La comparación
actual identifica explícitamente ALNS-estricto, con dos destructores y dos reparaciones.
La referencia histórica permanece en DISENO-ALGORITMOS.md.

## 5. Compilación y ejecución

Usar los scripts comunes de algoritmos. CompararAlgoritmos ejecuta ambos motores sobre
el mismo estado: [comandos](../tabu/README.md), [resultados](../experimentacion/COMPARACION-ESTRICTA.md).
Para ejecutar solo ALNS y ver el resumen por consola, usar `ejecutar-alns.bat` o
`ejecutar-alns.sh`; consulte la [guía de arquitectura y ejecución](../ARQUITECTURA-EJECUCION.md).
Desde Java:

```java
ResultadoPlanificacion r = new ALNSPlanner(
    ConfiguracionALNS.porDefecto()).planificar(estado, ParametrosOperacion.porDefecto());
```

Importar pe.pucp.paqrap.alns.estricto.* y pe.pucp.paqrap.estricto.modelo.*.
Métricas: mismas definiciones y formato CSV que TS. Los candidatos incluyen las evaluaciones
de inserción de la reparación; las inserciones iniciales van en una columna separada.

## 6. Validación

Las regresiones comparan solución inicial idéntica, no empeoramiento, reproducibilidad y
factibilidad común. Las doce ejecuciones reales (seis por motor) están auditadas.
Factible significa rutas válidas; completa exige además cero cantidades pendientes.
