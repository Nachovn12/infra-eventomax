package cl.duoc.eventomax.rabbitadmin.dto;
/** Observation is before declaration, never a claim of atomic create-if-absent. */
public record DeclarationResponse(String name, Outcome outcome) {
    public enum Outcome { DECLARED_AFTER_ABSENCE, EXISTING_COMPATIBLE }
}
