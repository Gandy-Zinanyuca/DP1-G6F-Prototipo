# ALNS y Tabu Search comparables

Los lanzadores principales ejecutan TS y ALNS sobre el mismo `EstadoOperacion`, constructor inicial, `EvaluadorFactibilidad`, caminos y parámetros operativos. Se compilan con JDK 17, sin Maven.

## Compilar y probar

Desde la raíz del repositorio en Windows:

```bat
algoritmos\compilar.bat -Pruebas
```

La salida es `algoritmos/out`. Se compilan ambos motores juntos para verificar el contrato común; se ejecutan por separado. Los valores antiguos `-Algoritmo alns` y `-Algoritmo tabu` se aceptan por compatibilidad, pero la construcción sigue siendo conjunta.

## Ejecutar cada algoritmo

Ambos comandos tienen exactamente los mismos argumentos:

```bat
algoritmos\ejecutar-alns.bat algoritmos/alns/data/ventas.v20260909/ventas.202609.txt algoritmos/alns/data/bloqueos.v20260909/bloqueo.2609.txt algoritmos/alns/data/mant.preventivo.09.10.txt 2026-09-01T08:00 20 20262
algoritmos\ejecutar-tabu.bat algoritmos/alns/data/ventas.v20260909/ventas.202609.txt algoritmos/alns/data/bloqueos.v20260909/bloqueo.2609.txt algoritmos/alns/data/mant.preventivo.09.10.txt 2026-09-01T08:00 20 20262
```

Argumentos: ventas, bloqueos, mantenimiento, instante ISO, [iteraciones], [semilla], [presupuesto-ms].
Predeterminados: 100 iteraciones, semilla 20262, presupuesto 0 (sin reloj). Un presupuesto positivo se aplica a ambos motores, incluyendo su construcción inicial. Es un límite cooperativo: una operación en curso puede terminar después del plazo.

El reporte final incluye pedidos leídos/considerados/completos, paquetes pendientes, cobertura, vehículos, utilización, costo operativo, distancia, objetivo, tiempo, iteraciones, candidatos, factibilidad y motivo de parada. La salida se audita con una nueva instancia del evaluador común.

## Experimentación conjunta

Para comprobar reproducibilidad con 20 iteraciones y tres semillas:

```bat
java -cp algoritmos/out pe.pucp.paqrap.CompararAlgoritmos algoritmos/alns/data/ventas.v20260909/ventas.202609.txt algoritmos/alns/data/bloqueos.v20260909/bloqueo.2609.txt algoritmos/alns/data/mant.preventivo.09.10.txt 2026-09-01T08:00 algoritmos/experimentacion/resultados/nueva-prueba 20 20262,20263,20264
```

Para comparar calidad bajo el mismo presupuesto de tiempo, usar un límite de iteraciones alto y añadir, por ejemplo, 1000 milisegundos:

```bat
java -cp algoritmos/out pe.pucp.paqrap.CompararAlgoritmos algoritmos/alns/data/ventas.v20260909/ventas.202609.txt algoritmos/alns/data/bloqueos.v20260909/bloqueo.2609.txt algoritmos/alns/data/mant.preventivo.09.10.txt 2026-09-01T08:00 algoritmos/experimentacion/resultados/nueva-prueba-tiempo 100000 20262,20263,20264 1000
```

Usar una carpeta nueva o vacía. Se guardan `metricas.csv`, un README de metadatos y un informe por motor/semilla con rutas, horarios y pendientes. Se realizan dos iteraciones de calentamiento por motor y se alterna el orden entre semillas. El comparador audita factibilidad, objetivo y no empeoramiento respecto de la inicial común.

## Arquitectura y alcance

| Componente | Responsabilidad |
| --- | --- |
| `comun/src/main/java/.../estricto` | Dominio, carga, constructor, evaluación, métricas y caminos utilizados por ambos |
| `alns/src/main/java/.../estricto` | ALNS adaptado al núcleo común |
| `tabu/src/main/java` | Tabu Search |
| `experimentacion/src/main/java` | Lanzadores individuales, reporte y comparación |
| `experimentacion/src/test/java` | Pruebas de reglas, reproducibilidad e igualdad de la inicial |
| `alns/src/pe` | ALNS histórico de algorithms, conservado fuera del experimento común |

El ALNS comparable reutiliza la selección adaptativa y el criterio de aceptación originales, con operadores adaptados a `PartePedido`. No es una medición de la cartera completa del ALNS histórico. El ALNS histórico se compila mediante `algoritmos/alns/compilar.bat` y su arnés `simular.bat` permanece disponible; no mezclar sus resultados con los del experimento común.

Las pruebas actuales son fotografías de la operación: seleccionan pedidos registrados hasta T, cuyo plazo vence como máximo en T+24 horas, con límite 400. No reconstruyen entregas anteriores ni simulan todo el mes. Ambos reciben exactamente la misma selección. La gestión del lote está fuera de los motores.

Consultar [metadatos y resultados](METADATOS-PRUEBAS.md), [ALNS](alns/README.md) y [TS](tabu/README.md). La documentación anterior y los archivos de GUI permanecen recuperables en Git. No se necesitan dependencias externas; una futura integración de Maven puede organizar módulos común, ALNS, TS y experimentación sin cambiar estos contratos.
