package com.cjv.sistemacjv.dto;

import java.time.LocalDate;

/**
 * El semáforo de UN día en el calendario del Jefe.
 *
 * Un renglón por día CON cortes. Los días sin ningún corte no vienen en
 * la lista: el navegador los pinta grises por ausencia, y así el mes no
 * viaja lleno de renglones vacíos.
 *
 * LOS COLORES (la regla vive en el servidor, no en el navegador):
 *   VERDE    -> todos los cortes del día están AUTORIZADOS. Día cerrado.
 *   AMARILLO -> algunos autorizados y otros no, o hay alguno REABIERTO.
 *               O sea: el Jefe ya metió mano pero queda pendiente.
 *   ROJO     -> hay cortes y ninguno está autorizado. Sin revisar.
 *
 * Lo que este semáforo NO dice: si alguien FALTÓ por entregar. El sistema
 * no sabe quién trabajó cada día — no hay registro de asistencia — así
 * que un día con dos cortes se ve igual haya trabajado dos personas o
 * tres. Eso se decidió a propósito para no inventar un módulo de turnos.
 */
public class EstadoDelDiaDTO {

    public static final String VERDE = "VERDE";
    public static final String AMARILLO = "AMARILLO";
    public static final String ROJO = "ROJO";

    private LocalDate fecha;
    private String color;

    /** Cuántos cortes se entregaron ese día. Para el tooltip del cuadrito. */
    private int totalCortes;

    /** De esos, cuántos ya firmó el Jefe. */
    private int autorizados;

    /** ¿Hay alguno devuelto para corregir? Cuenta como pendiente. */
    private boolean hayReabiertos;

    public EstadoDelDiaDTO() {
    }

    public EstadoDelDiaDTO(LocalDate fecha,
                           String color,
                           int totalCortes,
                           int autorizados,
                           boolean hayReabiertos) {
        this.fecha = fecha;
        this.color = color;
        this.totalCortes = totalCortes;
        this.autorizados = autorizados;
        this.hayReabiertos = hayReabiertos;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getTotalCortes() {
        return totalCortes;
    }

    public void setTotalCortes(int totalCortes) {
        this.totalCortes = totalCortes;
    }

    public int getAutorizados() {
        return autorizados;
    }

    public void setAutorizados(int autorizados) {
        this.autorizados = autorizados;
    }

    public boolean isHayReabiertos() {
        return hayReabiertos;
    }

    public void setHayReabiertos(boolean hayReabiertos) {
        this.hayReabiertos = hayReabiertos;
    }
}