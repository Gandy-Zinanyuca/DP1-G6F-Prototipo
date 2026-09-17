# Algoritmos de planificación

Solo se requiere un JDK 17 o superior con `javac` y `java` en PATH. Se conservan el ALNS histórico y los motores nuevos TS/ALNS con contrato estricto.

| Módulo | Responsabilidad |
|---|---|
| comun | Modelos, carga de datos, mapa, contrato Planificador, rutas, evaluación y constructor inicial |
| alns | Motor ALNS, configuración, selección adaptativa, aceptación y operadores |
| tabu | Tabu Search estricto: vecindarios, memoria, aspiración y parada |
| experimentacion | Demo, verificación con datasets y pruebas Java con datos sintéticos |

Dependencias: alns → comun; tabu → comun; experimentacion → comun + alns + tabu.
No se permite que comun dependa de una metaheurística.

## Construcción y pruebas

Desde la raíz del repositorio, en Windows:

```bat
algoritmos\compilar.bat
```

En Linux/macOS:

```sh
bash algoritmos/compilar.sh
```

Los scripts compilan con `javac --release 17 -encoding UTF-8` y ejecutan las tres
pruebas de integración históricas y 15 grupos de pruebas estrictas. Cualquier fallo devuelve un código distinto de cero.
El lanzador Windows usa comandos de cmd; el de Linux/macOS utiliza Bash.
No se descargan dependencias. Las comprobaciones no requieren activar `-ea`.
Las fuentes conservan `src/main/java` y `src/test/java`; las clases se generan en
`algoritmos/out/`. Los scripts antiguos de alns redirigen a la construcción completa.

Después de compilar (los mismos comandos funcionan en todos los sistemas):

```sh
java -cp algoritmos/out pe.pucp.paqrap.DemoPlanificador ventas.txt
java -cp algoritmos/out pe.pucp.paqrap.PruebaPlanificador ventas.txt
java -cp algoritmos/out pe.pucp.paqrap.IntegracionALNSTest
```

La demo conserva sus argumentos; consulte su código para opciones adicionales.
`PruebaPlanificador` requiere datos reales; las pruebas de integración son sintéticas.
Para repetir experimentos, conservar el dataset, parámetros, semilla y presupuesto;
usar iteraciones fijas si se necesita reproducibilidad exacta.

Se retiraron los POM y la dependencia de JUnit. La organización lógica permanece,
aunque la compilación básica compila todas las carpetas juntas y no impone las fronteras
entre módulos. Los `target/` de la construcción anterior no se usan.
Véase [cómo reintegrar Maven](REINTEGRAR-MAVEN.md).

## Contrato estricto y comparación

Consultar [Tabu Search](tabu/README.md), [ALNS estricto](alns/DISENO-ESTRICTO.md),
[verificación del cuadro](VERIFICACION-TABU.md) y [experimentación comparativa](experimentacion/COMPARACION-ESTRICTA.md).
Las rutas asignadas cumplen restricciones duras; los pedidos pendientes se informan como cobertura incompleta.

La guía [Arquitectura y ejecución](ARQUITECTURA-EJECUCION.md) explica cómo ejecutar
cada algoritmo por separado, sus clases, métodos principales y diagramas de secuencia.

## Entrada histórica y evolución

Para los datasets proporcionados de septiembre/octubre de 2026, usar
PruebaDatosReales: [comandos, alcance y resultados](experimentacion/resultados/README.md).
Este ejecutable toma el período del nombre del archivo de ventas y comprueba que
el archivo de bloqueos corresponde al mismo mes.

`Instancia.construir(archivos)` → cargadores → `Instancia` →
`ContextoPlanificacion.construir(...)` → `Planificador.planificar(contexto, planPrevio)` → `Solucion`.

ALNS no abre archivos. El backend puede construir los objetos directamente.
El contrato nuevo `EstadoOperacion + ParametrosOperacion → ResultadoPlanificacion` está implementado en `comun/estricto` y lo comparten TS y ALNS estrictos.
El diagnóstico histórico se conserva en la
[verificación requisito por requisito](VERIFICACION-ALNS.md).

Se conservaron los nombres públicos existentes, salvo `ConstructorInicial` y
`EvaluadorInsercion`, que pasan de `pe.pucp.paqrap.alns` a `pe.pucp.paqrap.servicios`.
Ambos algoritmos podrán reutilizarlos sin depender de ALNS.
El archivo anterior `alns/sources.txt` se conserva porque tenía cambios locales;
ya no es una entrada de compilación. Los antiguos `alns/out` tampoco se utilizan.

[Tabu Search: diseño y ejecución](tabu/README.md) · [Diseño histórico](DISENO-ALGORITMOS.md).
El diseño histórico explica las decisiones previas; no constituye evidencia de cumplimiento.
