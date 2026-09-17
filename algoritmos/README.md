# Algoritmos: integración ALNS y Tabu Search

Esta versión conserva el ALNS de `algorithms` (`50f317c`) e incorpora Tabu Search y sus dependencias de `dev/yaser` (`6864d6a`). La integración es selectiva: no reemplaza el ALNS con el ALNS estricto de la otra rama. Los dos motores se compilan y ejecutan por separado, con JDK 17 o superior y sin Maven.

## Construcción y verificación

Desde la raíz del repositorio, en PowerShell o CMD:

```bat
algoritmos\compilar.bat -Pruebas
```

Compila ambos motores, ejecuta 15 grupos de regresión de TS y verifica ALNS con datos de enero. Un fallo devuelve un código distinto de cero. Para compilar solamente uno:

```bat
algoritmos\compilar.bat -Algoritmo alns
algoritmos\compilar.bat -Algoritmo tabu
```

Los resultados de compilación están en `alns/out` y `tabu/out`, ignorados por Git. No deben mezclarse los classpaths ni utilizar antiguos JAR o carpetas `target`. El script requiere Windows PowerShell; en otros sistemas se puede utilizar `javac --release 17` sobre las mismas carpetas de fuentes.

## Ejecutar ALNS

Prueba corta con tres ciclos y datos de enero:

```bat
algoritmos\ejecutar-alns.bat algoritmos/alns/data/ventas.v20260909/ventas.202601.txt algoritmos/alns/data/bloqueos.v20260909/bloqueo.2601.txt algoritmos/alns/data/mant.preventivo.09.10.txt --dia 1 --hora 1 --ciclos 3 --iteraciones 20 --semilla 20262
```

La consola presenta pedidos, rutas, costo, distancia, tiempo de búsqueda y resumen de entregas; puede detenerse por colapso. Para lanzar el arnés mensual existente:

```bat
algoritmos\alns\simular.bat 202609
```

Guarda la salida en `algoritmos/alns/resultados/sim_202609.txt`. Ejecuta hasta el colapso o el límite de ciclos. Es un arnés simplificado: marca como ejecutadas las rutas completas de cada ciclo; no sustituye una simulación de eventos que avance cada vehículo físicamente. El valor predeterminado de 4464 ciclos representa 31 días con saltos de 10 minutos; para septiembre, de 30 días, usar `--ciclos 4320`. Cada ejecución del mismo mes sobrescribe su informe.

## Ejecutar Tabu Search

Prueba de septiembre a las 08:00:

```bat
algoritmos\ejecutar-tabu.bat algoritmos/alns/data/ventas.v20260909/ventas.202609.txt algoritmos/alns/data/bloqueos.v20260909/bloqueo.2609.txt algoritmos/alns/data/mant.preventivo.09.10.txt 2026-09-01T08:00 20 20262
```

Los últimos dos argumentos son iteraciones y semilla; si se omiten, se usan 100 y 20262. Muestra pedidos leídos y considerados, cobertura, paquetes pendientes, costo, kilómetros, utilización, tiempo y motivo de parada. La salida se vuelve a evaluar con una instancia nueva del evaluador antes de imprimirse.

Este ejecutable evalúa una fotografía de la operación, no un mes completo. El cargador excluye pedidos registrados después del instante indicado y aquellos cuyo plazo vence después de las siguientes 24 horas; limita la selección a 400 pedidos. No descuenta entregas de ejecuciones anteriores. Para modificar estos filtros hay que configurar el adaptador; no son parámetros de la búsqueda tabú.

## Arquitectura y alcance

| Carpeta | Contenido |
| --- | --- |
| `alns/src` | ALNS y su dominio, evaluador, mapa, lectores y arnés de ciclos de `algorithms` |
| `tabu/src/main/java` | Motor TS de `dev/yaser`, vecindarios, aspiración y memoria tabú |
| `comun/src/main/java/pe/pucp/paqrap/estricto` | Dominio, lectores, caminos, constructor y evaluador utilizados actualmente por TS |
| `experimentacion/src/main/java` | Lanzador de TS, carga y resumen de consola |
| `experimentacion/src/test/java` | Regresiones de TS sin dependencias externas |
| `alns/data` | Datos de ventas, bloqueos y mantenimiento utilizados por ambos lanzadores |

Aunque la carpeta se llama `comun`, el ALNS conservado no utiliza aún este núcleo. Los motores tienen contratos y reglas diferentes; estos comandos permiten probar cada versión, pero no constituyen una comparación experimental homogénea.

Consultar [metadatos y reglas de comparación](METADATOS-PRUEBAS.md), [ALNS](alns/README.md) y [Tabu Search](tabu/README.md).

## Documentación y procedencia

Se reemplazaron los tres documentos `DISENO-ALGORITMOS.md` por estas guías del código efectivo, evitando conservar pseudocódigos y rutas de ejecución de versiones anteriores como instrucciones vigentes. La documentación anterior sigue disponible en el historial Git. El anterior TS `pe.logistica`, su POM, pruebas JUnit y reportes publicados fueron sustituidos por la versión de `dev/yaser`; sus resultados no representan esta integración.

Se conservaron `CARACTERISTICAS-GUI.md` y `Estandares_GUI_PaqRap_en_Ruta.docx`. Las fuentes Java de ALNS y el prototipo gráfico permanecen iguales a `algorithms`. No se incorporaron las mejoras propuestas en el chat ni se afirma que el código implemente ya todos los pseudocódigos revisados.

También se conservan `tabu/datos/inventario.json`, el mantenimiento y el Excel de referencias como insumos de procedencia. Los lanzadores actuales utilizan los TXT de `alns/data`; el inventario histórico no certifica automáticamente esos archivos ni configura las reglas del motor.

Si se reintegra Maven, crear módulos para el núcleo estricto y TS, y un módulo independiente para ALNS, con nivel Java 17. Mantener los classpaths separados hasta unificar modelos; adaptar las pruebas ejecutables sin borrar sus comprobaciones. Actualmente no se necesita descargar dependencias.
