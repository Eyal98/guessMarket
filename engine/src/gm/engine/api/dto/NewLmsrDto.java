package gm.engine.api.dto;

/**
 * An event to be priced by the formula.
 *
 * @param liquidity the index called b in the course material, which must be above nothing
 */
public record NewLmsrDto(int liquidity) implements NewMethodDto {
}
