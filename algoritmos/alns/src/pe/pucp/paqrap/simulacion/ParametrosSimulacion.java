package pe.pucp.paqrap.simulacion;

import java.nio.file.Path;

/** Parámetros del avance temporal de la simulación, independientes del algoritmo. */
public class ParametrosSimulacion {

    /** Día (del mes inicial) y hora en que arranca el reloj. */
    public int diaInicial = 1;
    public int horaInicial = 0;

    /** Sa: salto de planificación, en minutos (cada cuánto se ejecuta el planificador). */
    public int saMinutos = 10;

    /** Sc = Sa × K: amplitud de la ventana de pedidos considerados, en minutos. */
    public int scMinutos = 70;

    /**
     * Número máximo de ciclos de planificación; 0 significa sin límite, que es el modo
     * "hasta el colapso": la simulación sigue, encadenando meses, hasta que colapsa o se
     * acaban los datos.
     */
    public int maxCiclos = 0;

    /** Imprime una línea por ciclo (y el resumen del planificador) en lugar de una por día. */
    public boolean detalle = false;

    /** Archivo CSV con una fila por ciclo de planificación, o {@code null}. */
    public Path csvCiclos;

    /** Archivo CSV al que se agrega una fila con el resumen de la corrida, o {@code null}. */
    public Path csvResumen;
}
