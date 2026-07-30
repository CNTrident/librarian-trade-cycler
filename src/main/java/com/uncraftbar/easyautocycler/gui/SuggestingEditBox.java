package com.uncraftbar.easyautocycler.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SuggestingEditBox extends EditBox {
    private static final int MAX_VISIBLE_SUGGESTIONS = 6;
    private static final int SUGGESTION_ROW_HEIGHT = 12;

    private final Font font;
    private List<String> suggestions;
    private List<String> matches = List.of();
    private int selectedSuggestion;
    private int dropdownX;
    private int dropdownY;
    private int dropdownWidth;
    private int dropdownHeight;

    public SuggestingEditBox(Font font, int x, int y, int width, int height, Component message,
                             List<String> validSuggestions) {
        super(font, x, y, width, height, message);
        this.font = font;
        this.suggestions = validSuggestions == null ? new ArrayList<>() : new ArrayList<>(validSuggestions);
        this.setResponder(this::updateSuggestions);
    }

    public void setSuggestions(List<String> newSuggestions) {
        this.suggestions = newSuggestions == null ? new ArrayList<>() : new ArrayList<>(newSuggestions);
        updateSuggestions(this.getValue());
    }

    private void updateSuggestions(String currentText) {
        String query = currentText.trim().toLowerCase(Locale.ROOT);
        selectedSuggestion = 0;
        if (query.isEmpty()) {
            matches = List.of();
            setSuggestion(null);
            return;
        }

        boolean namespaced = query.indexOf(':') >= 0;
        matches = suggestions.stream()
                .filter(id -> matches(id, query, namespaced))
                .filter(id -> !id.equalsIgnoreCase(query))
                .limit(MAX_VISIBLE_SUGGESTIONS)
                .toList();

        if (matches.isEmpty()) setSuggestion(null);
        else updateInlineSuggestion();
    }

    private static boolean matches(String id, String query, boolean namespaced) {
        String lowerId = id.toLowerCase(Locale.ROOT);
        return namespaced ? lowerId.startsWith(query) || lowerId.contains("(" + query)
                : path(lowerId).startsWith(query) || lowerId.startsWith(query);
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
                updateInlineSuggestion();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_UP) {
                selectedSuggestion = (selectedSuggestion - 1 + matches.size()) % matches.size();
                updateInlineSuggestion();
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
        String query = getValue().trim();
        String selected = matches.get(selectedSuggestion);
        String comparison = selected.regionMatches(true, 0, query, 0, query.length())
                ? selected : path(selected);
        setSuggestion(comparison.regionMatches(true, 0, query, 0, query.length())
                ? comparison.substring(query.length()) : null);
    }

    private void acceptSuggestion(int index) {
        if (index < 0 || index >= matches.size()) return;
        setValue(matches.get(index));
        moveCursorToEnd(false);
        setHighlightPos(getCursorPosition());
    }

    public void extractSuggestionList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!isFocused() || matches.isEmpty()) {
            dropdownHeight = 0;
            return;
        }

        dropdownWidth = getWidth();
        for (String match : matches) dropdownWidth = Math.max(dropdownWidth, font.width(match) + 10);
        dropdownWidth = Math.min(dropdownWidth, graphics.guiWidth() - getX() - 4);
        dropdownHeight = matches.size() * SUGGESTION_ROW_HEIGHT + 2;
        dropdownX = getX();
        dropdownY = getY() + getHeight() + 2;
        if (dropdownY + dropdownHeight > graphics.guiHeight() - 4) {
            dropdownY = getY() - dropdownHeight - 2;
        }

        graphics.fill(dropdownX - 1, dropdownY - 1, dropdownX + dropdownWidth + 1,
                dropdownY + dropdownHeight + 1, 0xFF050607);
        graphics.fill(dropdownX, dropdownY, dropdownX + dropdownWidth,
                dropdownY + dropdownHeight, 0xF01A1D24);

        for (int index = 0; index < matches.size(); index++) {
            int rowY = dropdownY + 1 + index * SUGGESTION_ROW_HEIGHT;
            boolean hovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth
                    && mouseY >= rowY && mouseY < rowY + SUGGESTION_ROW_HEIGHT;
            if (index == selectedSuggestion || hovered) {
                graphics.fill(dropdownX + 1, rowY, dropdownX + dropdownWidth - 1,
                        rowY + SUGGESTION_ROW_HEIGHT, 0xFF3B4250);
            }
            graphics.text(font, matches.get(index), dropdownX + 4, rowY + 2,
                    index == selectedSuggestion ? 0xFFFFFFFF : 0xFFD5DAE2, false);
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
            acceptSuggestion(Math.max(0, Math.min(matches.size() - 1, index)));
            return true;
        }
        return false;
    }
}
