package com.uncraftbar.easyautocycler.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class SuggestingEditBox extends EditBox {
    private static final int SUGGESTIONS_PER_PAGE = 6;
    private static final int SUGGESTION_ROW_HEIGHT = 12;

    private final Font font;
    private List<String> suggestions;
    private List<String> matches = List.of();
    private int selectedSuggestion;
    private int suggestionPage;
    private int dropdownX;
    private int dropdownY;
    private int dropdownWidth;
    private int dropdownHeight;
    private boolean commaSeparatedValues;
    private Consumer<String> changeListener = value -> {};

    public SuggestingEditBox(Font font, int x, int y, int width, int height, Component message,
                             List<String> validSuggestions) {
        super(font, x, y, width, height, message);
        this.font = font;
        this.suggestions = validSuggestions == null ? new ArrayList<>() : new ArrayList<>(validSuggestions);
        this.setResponder(value -> {
            updateSuggestions(value);
            changeListener.accept(value);
        });
        updateSuggestions("");
    }

    public void setChangeListener(Consumer<String> listener) {
        this.changeListener = listener == null ? value -> {} : listener;
    }

    public void setSuggestions(List<String> newSuggestions) {
        this.suggestions = newSuggestions == null ? new ArrayList<>() : new ArrayList<>(newSuggestions);
        updateSuggestions(this.getValue());
    }

    public void setCommaSeparatedValues(boolean commaSeparatedValues) {
        this.commaSeparatedValues = commaSeparatedValues;
        updateSuggestions(this.getValue());
    }

    private void updateSuggestions(String currentText) {
        String query = activeValue(currentText).trim().toLowerCase(Locale.ROOT);
        selectedSuggestion = 0;
        suggestionPage = 0;
        if (query.isEmpty()) {
            matches = List.copyOf(suggestions);
            setSuggestion(null);
            return;
        }

        boolean namespaced = query.indexOf(':') >= 0;
        matches = suggestions.stream()
                .filter(id -> matches(id, query, namespaced))
                .filter(id -> !id.equalsIgnoreCase(query))
                .toList();

        if (matches.isEmpty()) setSuggestion(null);
        else updateInlineSuggestion();
    }

    private static boolean matches(String id, String query, boolean namespaced) {
        String lowerId = id.toLowerCase(Locale.ROOT);
        if (namespaced) {
            return lowerId.startsWith(query) || lowerId.contains("(" + query);
        }
        String normalizedQuery = normalizeForSearch(query);
        return path(lowerId).startsWith(query) || lowerId.startsWith(query)
                || lowerId.contains(query)
                || (!normalizedQuery.isEmpty()
                && normalizeForSearch(lowerId).contains(normalizedQuery));
    }

    private static String normalizeForSearch(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private static String path(String id) {
        int separator = id.indexOf(':');
        return separator < 0 ? id : id.substring(separator + 1);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isFocused() && !matches.isEmpty()) {
            if (event.key() == GLFW.GLFW_KEY_DOWN) {
                selectedSuggestion = (selectedSuggestion + 1) % matches.size();
                suggestionPage = selectedSuggestion / SUGGESTIONS_PER_PAGE;
                updateInlineSuggestion();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_UP) {
                selectedSuggestion = (selectedSuggestion - 1 + matches.size()) % matches.size();
                suggestionPage = selectedSuggestion / SUGGESTIONS_PER_PAGE;
                updateInlineSuggestion();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
                changePage(1);
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_PAGE_UP) {
                changePage(-1);
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_TAB || event.key() == GLFW.GLFW_KEY_ENTER
                    || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                acceptSuggestion(selectedSuggestion);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void updateInlineSuggestion() {
        String query = activeValue(getValue()).trim();
        String selected = matches.get(selectedSuggestion);
        String comparison = selected.regionMatches(true, 0, query, 0, query.length())
                ? selected : path(selected);
        setSuggestion(comparison.regionMatches(true, 0, query, 0, query.length())
                ? comparison.substring(query.length()) : null);
    }

    private void acceptSuggestion(int index) {
        if (index < 0 || index >= matches.size()) return;
        String current = getValue();
        int start = commaSeparatedValues ? activeValueStart(current) : 0;
        int end = commaSeparatedValues ? activeValueEnd(current) : current.length();
        setValue(current.substring(0, start) + matches.get(index) + current.substring(end));
        moveCursorToEnd(false);
        setHighlightPos(getCursorPosition());
    }

    private String activeValue(String value) {
        return commaSeparatedValues
                ? value.substring(activeValueStart(value), activeValueEnd(value)) : value;
    }

    private int activeValueStart(String value) {
        int cursor = Math.max(0, Math.min(getCursorPosition(), value.length()));
        int separator = Math.max(value.lastIndexOf(',', Math.max(0, cursor - 1)),
                value.lastIndexOf('，', Math.max(0, cursor - 1)));
        int start = separator + 1;
        while (start < value.length() && Character.isWhitespace(value.charAt(start))) start++;
        return start;
    }

    private int activeValueEnd(String value) {
        int cursor = Math.max(0, Math.min(getCursorPosition(), value.length()));
        int comma = value.indexOf(',', cursor);
        int chineseComma = value.indexOf('，', cursor);
        if (comma < 0) return chineseComma < 0 ? value.length() : chineseComma;
        if (chineseComma < 0) return comma;
        return Math.min(comma, chineseComma);
    }

    private int pageCount() {
        return Math.max(1, (matches.size() + SUGGESTIONS_PER_PAGE - 1) / SUGGESTIONS_PER_PAGE);
    }

    private int pageStart() {
        return suggestionPage * SUGGESTIONS_PER_PAGE;
    }

    private void changePage(int delta) {
        int pages = pageCount();
        suggestionPage = (suggestionPage + delta + pages) % pages;
        selectedSuggestion = Math.min(matches.size() - 1, pageStart());
        updateInlineSuggestion();
    }

    public void extractSuggestionList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!isFocused() || matches.isEmpty()) {
            dropdownHeight = 0;
            return;
        }

        dropdownWidth = getWidth();
        int start = pageStart();
        int end = Math.min(matches.size(), start + SUGGESTIONS_PER_PAGE);
        for (int index = start; index < end; index++) {
            dropdownWidth = Math.max(dropdownWidth, font.width(matches.get(index)) + 10);
        }
        dropdownWidth = Math.min(dropdownWidth, graphics.guiWidth() - getX() - 4);
        int visibleCount = end - start;
        int footerHeight = pageCount() > 1 ? SUGGESTION_ROW_HEIGHT : 0;
        dropdownHeight = visibleCount * SUGGESTION_ROW_HEIGHT + footerHeight + 2;
        dropdownX = getX();
        dropdownY = getY() + getHeight() + 2;
        if (dropdownY + dropdownHeight > graphics.guiHeight() - 4) {
            dropdownY = getY() - dropdownHeight - 2;
        }

        graphics.fill(dropdownX - 1, dropdownY - 1, dropdownX + dropdownWidth + 1,
                dropdownY + dropdownHeight + 1, 0xFF050607);
        graphics.fill(dropdownX, dropdownY, dropdownX + dropdownWidth,
                dropdownY + dropdownHeight, 0xF01A1D24);

        for (int index = start; index < end; index++) {
            int rowY = dropdownY + 1 + (index - start) * SUGGESTION_ROW_HEIGHT;
            boolean hovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth
                    && mouseY >= rowY && mouseY < rowY + SUGGESTION_ROW_HEIGHT;
            if (index == selectedSuggestion || hovered) {
                graphics.fill(dropdownX + 1, rowY, dropdownX + dropdownWidth - 1,
                        rowY + SUGGESTION_ROW_HEIGHT, 0xFF3B4250);
            }
            graphics.text(font, matches.get(index), dropdownX + 4, rowY + 2,
                    index == selectedSuggestion ? 0xFFFFFFFF : 0xFFD5DAE2, false);
        }

        if (pageCount() > 1) {
            int footerY = dropdownY + 1 + visibleCount * SUGGESTION_ROW_HEIGHT;
            graphics.fill(dropdownX + 1, footerY, dropdownX + dropdownWidth - 1,
                    footerY + SUGGESTION_ROW_HEIGHT, 0xFF252A33);
            String pageText = "<  " + (suggestionPage + 1) + "/" + pageCount() + "  >";
            graphics.text(font, Component.literal(pageText),
                    dropdownX + (dropdownWidth - font.width(pageText)) / 2, footerY + 2,
                    0xFFD5DAE2, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (clickSuggestion(event)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Handles clicks in the popup, which lies outside the edit box's normal hit
     * rectangle and therefore is not reached by newer Screen event routing.
     */
    public boolean clickSuggestion(MouseButtonEvent event) {
        if (event.button() == 0 && isFocused() && dropdownHeight > 0
                && event.x() >= dropdownX && event.x() < dropdownX + dropdownWidth
                && event.y() >= dropdownY && event.y() < dropdownY + dropdownHeight) {
            int index = ((int) event.y() - dropdownY - 1) / SUGGESTION_ROW_HEIGHT;
            int visibleCount = Math.min(SUGGESTIONS_PER_PAGE, matches.size() - pageStart());
            if (index >= visibleCount && pageCount() > 1) {
                changePage(event.x() < dropdownX + dropdownWidth / 2.0 ? -1 : 1);
                return true;
            }
            acceptSuggestion(Math.max(0, Math.min(matches.size() - 1, pageStart() + index)));
            return true;
        }
        return false;
    }
}
