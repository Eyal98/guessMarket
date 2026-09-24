package gm.server.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Reads the parameters of a request, whether they came in the address or in a form body, and refuses
 * with a clear message when one is missing or is not what it should be.
 */
public final class Parameters {

    private Parameters() {
    }

    public static String text(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null || value.isBlank()) {
            throw new BadRequestException("The request has no \"" + name + "\" parameter.");
        }
        return value.trim();
    }

    public static int wholeNumber(HttpServletRequest request, String name) {
        String value = text(request, name);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException("The \"" + name + "\" parameter is \"" + value
                    + "\", which is not a whole number.");
        }
    }

    public static long count(HttpServletRequest request, String name) {
        String value = text(request, name);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException("The \"" + name + "\" parameter is \"" + value
                    + "\", which is not a whole number.");
        }
    }

    public static double amount(HttpServletRequest request, String name) {
        String value = text(request, name);
        try {
            double amount = Double.parseDouble(value);
            if (!Double.isFinite(amount)) {
                throw new NumberFormatException();
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new BadRequestException("The \"" + name + "\" parameter is \"" + value
                    + "\", which is not a number.");
        }
    }

    /** A whole number that may be left out, standing for nothing yet held. */
    public static int countAlreadyHeld(HttpServletRequest request, String name) {
        return request.getParameter(name) == null ? 0 : wholeNumber(request, name);
    }
}
