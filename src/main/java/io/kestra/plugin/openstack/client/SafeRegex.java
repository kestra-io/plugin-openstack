package io.kestra.plugin.openstack.client;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compiles and runs user supplied name filters with bounded cost: limited pattern length,
 * no quantified groups that already contain a quantifier, and a matching time budget.
 */
public final class SafeRegex {

    public static final int MAX_PATTERN_LENGTH = 256;
    private static final Duration MATCH_TIMEOUT = Duration.ofSeconds(2);

    private SafeRegex() {
    }

    public static Pattern compile(String regex) {
        if (regex.length() > MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException(
                "Invalid name regex: pattern length " + regex.length() + " exceeds the maximum of " + MAX_PATTERN_LENGTH + " characters."
            );
        }
        if (hasNestedQuantifier(regex)) {
            throw new IllegalArgumentException(
                "Invalid name regex: quantified groups that contain another quantifier are not supported."
            );
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid name regex '" + regex + "': " + e.getDescription(), e);
        }
    }

    public static boolean matches(Pattern pattern, String input) {
        var matcher = pattern.matcher(new DeadlineCharSequence(input, System.nanoTime() + MATCH_TIMEOUT.toNanos()));
        return matcher.matches();
    }

    static boolean hasNestedQuantifier(String regex) {
        var groups = new ArrayDeque<Boolean>();
        boolean inClass = false;
        boolean lastWasQuantified = false;
        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);
            if (c == '\\') {
                i++;
                lastWasQuantified = false;
                continue;
            }
            if (inClass) {
                if (c == ']') {
                    inClass = false;
                }
                continue;
            }
            switch (c) {
                case '[' -> inClass = true;
                case '(' -> groups.push(false);
                case ')' -> {
                    var innerQuantified = !groups.isEmpty() && groups.pop();
                    if (innerQuantified && isQuantifierAt(regex, i + 1)) {
                        return true;
                    }
                    // a quantified inner group counts as a quantifier for the enclosing group
                    if (innerQuantified && !groups.isEmpty()) {
                        groups.pop();
                        groups.push(true);
                    }
                }
                case '*', '+' -> markQuantifier(groups);
                case '{' -> {
                    if (isQuantifierAt(regex, i)) {
                        markQuantifier(groups);
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    private static void markQuantifier(Deque<Boolean> groups) {
        if (!groups.isEmpty()) {
            groups.pop();
            groups.push(true);
        }
    }

    private static boolean isQuantifierAt(String regex, int index) {
        if (index >= regex.length()) {
            return false;
        }
        char c = regex.charAt(index);
        if (c == '*' || c == '+') {
            return true;
        }
        return c == '{' && regex.startsWith("{", index) && regex.substring(index).matches("(?s)\\{\\d+(,\\d*)?\\}.*");
    }

    private record DeadlineCharSequence(CharSequence delegate, long deadlineNanos) implements CharSequence {
        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public char charAt(int index) {
            if (System.nanoTime() > deadlineNanos) {
                throw new IllegalStateException("Name regex evaluation exceeded the allowed time.");
            }
            return delegate.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new DeadlineCharSequence(delegate.subSequence(start, end), deadlineNanos);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
