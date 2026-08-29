package gm.engine.api.dto;

/**
 * How an event being created is to be traded.
 * <p>
 * Sealed over the two possibilities so the engine can be handed exactly the numbers that kind of
 * market needs and no others — rather than one wide record whose order book fields are meaningless
 * when the answer is a formula, and whose liquidity is meaningless when it is a book.
 */
public sealed interface NewMethodDto permits NewLmsrDto, NewOrderBookDto {
}
