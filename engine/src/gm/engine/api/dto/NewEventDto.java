package gm.engine.api.dto;

import java.util.List;

/**
 * An event somebody has filled in on the screen and wants brought into being.
 * <p>
 * This is the one DTO that travels inwards. It carries nothing but plain values, exactly like the
 * ones travelling out, so the screen still cannot reach the model — it can only describe what it
 * would like and let the engine decide whether that describes a real event.
 *
 * @param name              what the event is called
 * @param description       what is being guessed at
 * @param commissionPercent a whole percentage
 * @param commissionTiming  "on-purchase" or "on-close", the same words that travel outwards
 * @param optionNames       the outcomes, of which there must be at least two
 * @param method            how it is to be traded
 */
public record NewEventDto(String name, String description, int commissionPercent,
                          String commissionTiming, List<String> optionNames, NewMethodDto method) {

    public NewEventDto {
        optionNames = optionNames == null ? List.of()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(optionNames));
    }
}
