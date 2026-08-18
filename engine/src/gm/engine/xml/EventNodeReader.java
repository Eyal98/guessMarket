package gm.engine.xml;

import gm.engine.method.LmsrMethod;
import gm.engine.method.TradingMethod;
import gm.engine.model.Commission;
import gm.engine.model.CommissionType;
import gm.engine.model.Event;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Turns one {@code GM-event} element into an {@link Event}.
 * <p>
 * Every part of the element is examined even after something has already gone wrong, and each fault
 * is written to the shared problem list. One attempt at loading therefore tells the user everything
 * that is wrong with their file rather than revealing the faults one reload at a time.
 */
final class EventNodeReader {

    private static final String NAME_ATTRIBUTE = "name";
    private static final String ID_ELEMENT = "id";
    private static final String DESCRIPTION_ELEMENT = "description";
    /** Spelled with a single "s" in the file format. */
    private static final String COMMISSION_ELEMENT = "comision";
    private static final String COMMISSION_TYPE_ATTRIBUTE = "type";
    private static final String OPTIONS_ELEMENT = "GM-options";
    private static final String OPTION_ELEMENT = "GM-option";
    private static final String METHOD_ELEMENT = "GM-method";
    private static final String LMSR_ELEMENT = "GM-LMSR";
    private static final String ORDER_BOOK_ELEMENT = "GM-order-book";
    private static final String LIQUIDITY_ELEMENT = "b";

    private static final int REQUIRED_OPTIONS = 2;

    private final XmlNode node;
    private final List<String> problems;
    private final String name;
    private final String label;

    private OptionalInt id = OptionalInt.empty();

    EventNodeReader(XmlNode node, int position, List<String> problems) {
        this.node = node;
        this.problems = problems;
        this.name = node.attribute(NAME_ATTRIBUTE).orElse(null);
        this.label = label(position, name);
    }

    /** How an event is referred to in problem reports. */
    static String label(int position, String name) {
        return name == null || name.isBlank()
                ? "Event #" + position
                : "Event #" + position + " (\"" + name + "\")";
    }

    /** The event, or nothing at all if any part of it was faulty. */
    Optional<Event> read() {
        Optional<String> eventName = requiredName();
        id = requiredId();
        Optional<String> description = requiredText(DESCRIPTION_ELEMENT);
        Optional<Commission> commission = requiredCommission();
        Optional<List<String>> optionNames = requiredOptions();
        Optional<TradingMethod> method = requiredMethod();

        if (eventName.isEmpty() || id.isEmpty() || description.isEmpty()
                || commission.isEmpty() || optionNames.isEmpty() || method.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Event(id.getAsInt(), eventName.get(), description.get(),
                commission.get(), optionNames.get(), method.get()));
    }

    /**
     * The id this event declared, available after {@link #read()} even when the event as a whole was
     * rejected. That lets clashing ids be reported alongside whatever else is wrong with the file,
     * rather than only surfacing once every other fault has been fixed.
     */
    OptionalInt declaredId() {
        return id;
    }

    String label() {
        return label;
    }

    private Optional<String> requiredName() {
        if (name == null) {
            problems.add(label + ": it has no " + NAME_ATTRIBUTE + " attribute.");
            return Optional.empty();
        }
        if (name.isBlank()) {
            problems.add(label + ": its " + NAME_ATTRIBUTE + " attribute is empty.");
            return Optional.empty();
        }
        return Optional.of(name);
    }

    private OptionalInt requiredId() {
        Optional<String> text = requiredText(ID_ELEMENT);
        if (text.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(text.get()));
        } catch (NumberFormatException e) {
            problems.add(label + ": its id is \"" + text.get() + "\", which is not a whole number.");
            return OptionalInt.empty();
        }
    }

    private Optional<Commission> requiredCommission() {
        Optional<XmlNode> commissionNode = node.child(COMMISSION_ELEMENT);
        if (commissionNode.isEmpty()) {
            problems.add(label + ": it has no <" + COMMISSION_ELEMENT + "> element.");
            return Optional.empty();
        }
        OptionalInt percent = commissionPercent(commissionNode.get());
        Optional<CommissionType> type = commissionType(commissionNode.get());
        if (percent.isEmpty() || type.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Commission(percent.getAsInt(), type.get()));
    }

    private OptionalInt commissionPercent(XmlNode commissionNode) {
        String text = commissionNode.text().orElse("");
        if (text.isBlank()) {
            problems.add(label + ": its <" + COMMISSION_ELEMENT + "> element is empty. It must hold a whole"
                    + " number between " + Commission.MINIMUM_PERCENT + " and " + Commission.MAXIMUM_PERCENT + ".");
            return OptionalInt.empty();
        }
        int percent;
        try {
            percent = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            problems.add(label + ": its commission is \"" + text + "\", which is not a whole number.");
            return OptionalInt.empty();
        }
        if (!Commission.isValidPercent(percent)) {
            problems.add(label + ": its commission is " + percent + ", but a commission must be between "
                    + Commission.MINIMUM_PERCENT + " and " + Commission.MAXIMUM_PERCENT + ".");
            return OptionalInt.empty();
        }
        return OptionalInt.of(percent);
    }

    private Optional<CommissionType> commissionType(XmlNode commissionNode) {
        Optional<String> declared = commissionNode.attribute(COMMISSION_TYPE_ATTRIBUTE);
        if (declared.isEmpty()) {
            problems.add(label + ": its <" + COMMISSION_ELEMENT + "> element has no "
                    + COMMISSION_TYPE_ATTRIBUTE + " attribute. It must be " + CommissionType.allowedFileValues() + ".");
            return Optional.empty();
        }
        Optional<CommissionType> type = CommissionType.fromFileValue(declared.get());
        if (type.isEmpty()) {
            problems.add(label + ": its commission type is \"" + declared.get() + "\", but it must be "
                    + CommissionType.allowedFileValues() + ".");
        }
        return type;
    }

    private Optional<List<String>> requiredOptions() {
        Optional<XmlNode> optionsNode = node.child(OPTIONS_ELEMENT);
        if (optionsNode.isEmpty()) {
            problems.add(label + ": it has no <" + OPTIONS_ELEMENT + "> element.");
            return Optional.empty();
        }
        List<String> names = optionsNode.get().children(OPTION_ELEMENT).stream()
                .map(option -> option.text().orElse(""))
                .toList();
        if (names.size() != REQUIRED_OPTIONS) {
            problems.add(label + ": it has " + names.size() + " options, but every event must have exactly "
                    + REQUIRED_OPTIONS + ".");
            return Optional.empty();
        }
        if (names.stream().anyMatch(String::isBlank)) {
            problems.add(label + ": one of its options has no name.");
            return Optional.empty();
        }
        if (names.get(0).equalsIgnoreCase(names.get(1))) {
            problems.add(label + ": both of its options are called \"" + names.get(0)
                    + "\". The two options must be different, otherwise there is nothing to choose between.");
            return Optional.empty();
        }
        return Optional.of(names);
    }

    private Optional<TradingMethod> requiredMethod() {
        Optional<XmlNode> methodNode = node.child(METHOD_ELEMENT);
        if (methodNode.isEmpty()) {
            problems.add(label + ": it has no <" + METHOD_ELEMENT + "> element.");
            return Optional.empty();
        }
        Optional<XmlNode> lmsrNode = methodNode.get().child(LMSR_ELEMENT);
        if (lmsrNode.isEmpty()) {
            problems.add(unsupportedMethodProblem(methodNode.get()));
            return Optional.empty();
        }
        OptionalInt liquidity = liquidity(lmsrNode.get());
        return liquidity.isEmpty()
                ? Optional.empty()
                : Optional.of(new LmsrMethod(liquidity.getAsInt()));
    }

    private String unsupportedMethodProblem(XmlNode methodNode) {
        if (methodNode.child(ORDER_BOOK_ELEMENT).isPresent()) {
            return label + ": it trades through an order book, which this version of the system does not"
                    + " support yet. Only <" + LMSR_ELEMENT + "> events can be loaded.";
        }
        return label + ": its <" + METHOD_ELEMENT + "> element does not name a trading method."
                + " It must contain <" + LMSR_ELEMENT + ">.";
    }

    private OptionalInt liquidity(XmlNode lmsrNode) {
        Optional<String> text = lmsrNode.childText(LIQUIDITY_ELEMENT);
        if (text.isEmpty() || text.get().isBlank()) {
            problems.add(label + ": its <" + LMSR_ELEMENT + "> element has no <" + LIQUIDITY_ELEMENT
                    + "> value. The liquidity index must be a positive whole number.");
            return OptionalInt.empty();
        }
        int liquidity;
        try {
            liquidity = Integer.parseInt(text.get());
        } catch (NumberFormatException e) {
            problems.add(label + ": its liquidity index (" + LIQUIDITY_ELEMENT + ") is \"" + text.get()
                    + "\", which is not a whole number.");
            return OptionalInt.empty();
        }
        if (liquidity <= 0) {
            problems.add(label + ": its liquidity index (" + LIQUIDITY_ELEMENT + ") is " + liquidity
                    + ", but it must be a positive whole number.");
            return OptionalInt.empty();
        }
        return OptionalInt.of(liquidity);
    }

    private Optional<String> requiredText(String elementName) {
        Optional<String> text = node.childText(elementName);
        if (text.isEmpty()) {
            problems.add(label + ": it has no <" + elementName + "> element.");
            return Optional.empty();
        }
        if (text.get().isBlank()) {
            problems.add(label + ": its <" + elementName + "> element is empty.");
            return Optional.empty();
        }
        return text;
    }
}
