package gm.client.http;

import com.google.gson.Gson;
import gm.dto.ChatDto;
import gm.dto.ChatLineDto;
import gm.dto.ErrorDto;
import gm.dto.EventInfoDto;
import gm.dto.LedgerDto;
import gm.dto.LedgerLineDto;
import gm.dto.MarketStateDto;
import gm.dto.OptionHoldingDto;
import gm.dto.OptionMarketDto;
import gm.dto.OptionStateDto;
import gm.dto.OrderBookStateDto;
import gm.dto.OrderDto;
import gm.dto.ParticipantDto;
import gm.dto.ParticipationDto;
import gm.dto.PurchaseResultDto;
import gm.dto.TradeDto;
import gm.dto.UploadResultDto;
import gm.dto.UserDetailDto;
import gm.dto.UserDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the server sends is exactly what the client gets back.
 * <p>
 * Every answer crosses the network as JSON: the server turns a data object into text with Gson, and the
 * client turns the text back into the same data object. That only works for objects that are nothing
 * but data — no cycles, no interface a parser would have to guess an implementation for, and nothing
 * of any screen. These tests hold every data object to that, so a field added carelessly later is
 * caught here rather than on the checker's computer.
 */
class DtoRoundTripTest {

    private static final List<Class<?>> EVERY_DTO = List.of(ChatDto.class, ChatLineDto.class, ErrorDto.class,
            EventInfoDto.class, LedgerDto.class, LedgerLineDto.class, MarketStateDto.class,
            OptionHoldingDto.class, OptionMarketDto.class, OptionStateDto.class, OrderBookStateDto.class,
            OrderDto.class, ParticipantDto.class, ParticipationDto.class, PurchaseResultDto.class,
            TradeDto.class, UploadResultDto.class, UserDetailDto.class, UserDto.class);

    /** Plain values a data object may hold, besides other data objects and lists of them. */
    private static final Set<Type> PLAIN = Set.of(int.class, long.class, double.class, boolean.class,
            String.class, Double.class);

    private final Gson gson = new Gson();

    private static final EventInfoDto CUP = new EventInfoDto(2, "World Cap Winner", "Who wins?", 15,
            "on-close", "charged from the winners when the event closes", List.of("Argentina", "Spain"),
            "Active", "Order book (d=1, initial=100, mint allowed)", "Order book", "Avrum", 100.0, 100.0,
            null);

    private <T> void survives(T sent, Class<T> type) {
        T received = gson.fromJson(gson.toJson(sent), type);
        assertEquals(sent, received, type.getSimpleName() + " changed on its way across");
    }

    @Test
    @DisplayName("An order book, with its absent prices, comes back exactly as it was sent")
    void anOrderBookSurvives() {
        OptionHoldingDto neverTraded = new OptionHoldingDto(2, "Spain", 100, 50.0, null);
        OrderBookStateDto book = new OrderBookStateDto(CUP,
                List.of(new OptionMarketDto(1, "Argentina", List.of(new OrderDto("Tikva", "Buy", 20, 0.6)),
                                List.of(), 0.58, 0.6, null, null, null, 110),
                        new OptionMarketDto(2, "Spain", List.of(), List.of(), null, null, null, null, null, 100)),
                100.0, 0.0,
                List.of(new ParticipantDto("Avrum", List.of(
                        new OptionHoldingDto(1, "Argentina", 80, 38.0, 46.4), neverTraded), false)),
                1, true, 0.99);

        survives(book, OrderBookStateDto.class);
    }

    @Test
    @DisplayName("An LMSR event, a purchase, and a user's whole standing come back exactly as sent")
    void theRestSurvive() {
        List<TradeDto> history = List.of(new TradeDto("Yes", 100, 62.0115, 3.1, 65.1115));
        MarketStateDto lmsr = new MarketStateDto(CUP, List.of(new OptionStateDto(1, "Yes", 0.7311, 100)),
                131.33, 3.1, 930.0, history, false, null, 0, 0.0, 1.0);

        survives(lmsr, MarketStateDto.class);
        survives(new PurchaseResultDto("Yes", 100, 62.0115, 3.1, 65.1115, false, lmsr), PurchaseResultDto.class);
        survives(new UserDetailDto("Menash", 12.5, true, List.of("Rain"),
                List.of(new ParticipationDto(CUP, List.of(new OptionHoldingDto(1, "Argentina", 20, 12.0, null)),
                        0.0, -12.0, history))), UserDetailDto.class);
        survives(new UserDto("Tikva", 995.0, false, true), UserDto.class);
        survives(new LedgerDto(3, List.of(new LedgerLineDto(4, "Deposit", 50.0, 1045.0))), LedgerDto.class);
        survives(new ChatDto(0, List.of(new ChatLineDto(1, "Avrum", "Café?", 1_790_000_000_000L))), ChatDto.class);
        survives(new ErrorDto("Refused", List.of("one", "two")), ErrorDto.class);
        survives(new UploadResultDto("multiple.xml", List.of("A", "B")), UploadResultDto.class);
    }

    @Test
    @DisplayName("Every data object holds only plain values, lists, and other data objects")
    void everyDtoIsPlainData() {
        for (Class<?> dto : EVERY_DTO) {
            assertTrue(dto.isRecord(), dto.getSimpleName() + " should be a record");
            for (RecordComponent component : dto.getRecordComponents()) {
                assertTrue(isPlainData(component.getGenericType()),
                        dto.getSimpleName() + "." + component.getName() + " is " + component.getGenericType()
                                + ", which is not plain data");
            }
        }
    }

    @Test
    @DisplayName("No data object carries a method of its own, only what a record gives it")
    void noDtoHasLogic() {
        for (Class<?> dto : EVERY_DTO) {
            Set<String> accessors = Set.copyOf(Arrays.stream(dto.getRecordComponents())
                    .map(RecordComponent::getName).toList());
            Arrays.stream(dto.getDeclaredMethods())
                    .filter(method -> !method.isSynthetic())
                    .map(java.lang.reflect.Method::getName)
                    .filter(name -> !accessors.contains(name))
                    .filter(name -> !Set.of("equals", "hashCode", "toString").contains(name))
                    .findFirst()
                    .ifPresent(name -> {
                        throw new AssertionError(dto.getSimpleName() + " has a method of its own: " + name);
                    });
        }
    }

    private static boolean isPlainData(Type type) {
        if (PLAIN.contains(type) || EVERY_DTO.contains(type)) {
            return true;
        }
        return type instanceof ParameterizedType list && list.getRawType() == List.class
                && isPlainData(list.getActualTypeArguments()[0]);
    }
}
