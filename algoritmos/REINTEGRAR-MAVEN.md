# Reintegrar Maven

## Cambio realizado

Se retiraron los cinco archivos pom.xml (raíz, comun, alns, tabu y experimentacion)
para que la construcción y las pruebas solo necesiten el JDK 17+.
Los scripts usan javac y java y generan las clases en out/.
IntegracionALNSTest conserva las tres comprobaciones anteriores con un main y
AssertionError, sin JUnit. No se modificó la lógica del algoritmo.

Se mantiene la distribución src/main/java y src/test/java para facilitar una futura
migración. La compilación básica reúne las fuentes; la separación de dependencias
es una convención hasta reactivar la construcción por módulos.

## Pasos para restaurar la construcción por módulos

1. Crear algoritmos/pom.xml como agregador con packaging pom, coordenadas
   pe.pucp.paqrap:algoritmos:1.0-SNAPSHOT y módulos comun, alns, tabu, experimentacion.
   Configurar maven.compiler.release=17 y project.build.sourceEncoding=UTF-8.
2. Crear un pom.xml por módulo con ese padre (relativePath ../pom.xml) y su nombre
   como artifactId. Declarar dependencias con la misma versión:
   alns → comun; tabu → comun; experimentacion → comun, alns, tabu.
   comun no debe depender de las metaheurísticas.
3. Configurar maven-compiler-plugin. La configuración retirada usaba 3.13.0.
   Para pruebas JUnit, la configuración anterior usaba junit-jupiter 5.11.0
   con scope test y maven-surefire-plugin 3.5.2.
   Son referencias de la configuración retirada; verificar compatibilidad al reintegrarlas.
4. Si se desea detección automática con JUnit, quitar los tres auxiliares locales
   assertTrue/assertFalse/assertEquals, importar Assertions y anotar con @Test
   los tres métodos de comprobación de IntegracionALNSTest. Retirar su main.
   Maven no ejecutará automáticamente el main actual como una prueba JUnit.
5. Ejecutar desde la raíz del repositorio:

   ```sh
   mvn -f algoritmos/pom.xml clean verify
   ```

   Confirmar que el informe registra tres pruebas ejecutadas, no solamente una
   compilación correcta. Comprobar también que comun compila sin ALNS o Tabu.
6. Si Maven pasa a ser el mecanismo principal, actualizar compilar.bat,
   compilar.sh, y los comandos del README. Los lanzadores de alns
   ya delegan a los scripts comunes.

## Ejecución después de la migración

Maven genera target/classes por módulo. En Windows el classpath para la demo será:

```bat
java -cp "algoritmos/comun/target/classes;algoritmos/alns/target/classes;algoritmos/tabu/target/classes;algoritmos/experimentacion/target/classes" pe.pucp.paqrap.DemoPlanificador ventas.txt
```

En Linux/macOS, sustituir los separadores de classpath por dos puntos.
No mezclar las clases antiguas de out/ con las nuevas de target/.
Ambas carpetas están ignoradas por Git. La reintegración no requiere mover las
fuentes ni modificar el contrato Planificador.
