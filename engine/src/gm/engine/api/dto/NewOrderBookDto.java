package gm.engine.api.dto;

/**
 * An event to be traded between people through two books.
 *
 * @param initialInvestment what the creator will pay to stock the market when they open it
 * @param baseValue         what a whole pair is worth, called d in the course material
 * @param allowMint         whether two opposing buyers may between them create new shares
 */
public record NewOrderBookDto(int initialInvestment, int baseValue, boolean allowMint)
        implements NewMethodDto {
}
