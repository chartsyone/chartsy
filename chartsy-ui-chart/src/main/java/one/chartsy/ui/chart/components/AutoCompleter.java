/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.ui.chart.internal.UpperCaseDocumentFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.TableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;

/**
 * The base class of all autocompleter popup windows generated by the
 * application. The autocompleter is a graphical component that can display
 * list of suggestions matching the text entered by a user in the input box.
 * 
 * @author Mariusz Bernacki
 * 
 */
public abstract class AutoCompleter<T> {
    /** The logger instance. */
    protected static final Logger logger = LogManager.getLogger(AutoCompleter.class);
    /** The timed trigger for showing suggestion popup. */
    protected Timer delayTimer;
    /** Indicates that the {@code delayTimer} was disabled. */
    protected volatile boolean timerStopped;
    /** The list of suggestions produced. */
    protected List<T> list = new ArrayList<>();
    /** The visual presentation of suggestions produced. */
    protected JTable table = new JTable();
    /** The popup menu with suggestions. */
    protected JPopupMenu popupMenu = new JPopupMenu();
    /** The input box. */
    protected JTextComponent component;
    /** The Autocompleter properties. */
    protected AutoCompleteProperties properties;


    private static final String AUTOCOMPLETER = "AUTOCOMPLETER";
    private static final String SHOW_POPUP_ACTION_KEY = "showPopup";
    private static final String HIDE_POPUP_ACTION_KEY = "hidePopup";
    private static final String DOWN_SELECTION_ACTION_KEY = "downSelection";
    private static final String UP_SELECTION_ACTION_KEY = "upSelection";

    /**
     * Constructs the autocompleter associated with the given text component (user input box).
     *
     * @param comp
     *            the text component
     */
    public AutoCompleter(JTextComponent comp) {
        this(comp, new AutoCompleteProperties());
    }

    /**
     * Constructs the autocompleter associated with the given text component (user input box).
     * 
     * @param comp
     *            the text component
     * @param  properties
     *            the autocompleter properties
     */
    public AutoCompleter(JTextComponent comp, AutoCompleteProperties properties) {
        setProperties(properties);
        int delay = getProperties().getTriggerDelay();
        delayTimer = new Timer(delay, (ActionEvent e) -> {
            timerStopped = false;
            try {
                showPopup();
            } catch (Exception x) {
                x.printStackTrace();
            }
        });
        delayTimer.setRepeats(false);
        
        component = comp;
        component.putClientProperty(AUTOCOMPLETER, this);
        if (!getProperties().isCaseSensitive())
            UpperCaseDocumentFilter.decorate(component);
        configure(table);

        JScrollPane pane = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setBorder(BorderFactory.createEmptyBorder());

        pane.getVerticalScrollBar().setFocusable(false);
        pane.getHorizontalScrollBar().setFocusable(false);
        popupMenu.add(pane);
        popupMenu.setFocusable(false);

        component.getActionMap().put(SHOW_POPUP_ACTION_KEY, showAction);
        component.getActionMap().put(HIDE_POPUP_ACTION_KEY, hidePopupAction);
        component.getActionMap().put(DOWN_SELECTION_ACTION_KEY, downAction);
        component.getActionMap().put(UP_SELECTION_ACTION_KEY, upAction);
        if (component instanceof JTextField) {
            component.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), SHOW_POPUP_ACTION_KEY);
        } else {
            component.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, KeyEvent.CTRL_DOWN_MASK), SHOW_POPUP_ACTION_KEY);
        }
        component.getDocument().addDocumentListener(documentListener);

        component.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), HIDE_POPUP_ACTION_KEY);

        popupMenu.addPopupMenuListener(new PopupMenuListener() {
            
            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                component.getInputMap().remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
                component.getInputMap().remove(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
                component.getInputMap().remove(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
                SymbolChanger changer = getSymbolChanger();
                if (changer != null) {
                    component.registerKeyboardAction(changer.submit,
                            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
                }
            }
            
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                component.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), DOWN_SELECTION_ACTION_KEY);
                component.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), UP_SELECTION_ACTION_KEY);
            }
            
            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == getProperties().getAcceptClickCount()) {
                    ActionEvent v = new ActionEvent(component, ActionEvent.ACTION_PERFORMED, "Accept");
                    acceptAction.actionPerformed(v);
                    SymbolChanger changer = getSymbolChanger();
                    if (changer != null)
                        changer.submit.actionPerformed(v);
                }
            }
        });
        table.setRequestFocusEnabled(false);
        //SelectionAwareListCellRenderer.decorate(list);
    }

    protected void setProperties(AutoCompleteProperties properties) {
        Objects.requireNonNull(properties, "properties");
        this.properties = properties;
    }

    public final AutoCompleteProperties getProperties() {
        return properties;
    }

    public void resizeColumnWidth(JTable table) {
        var columnModel = table.getColumnModel();
        for (int column = 0; column < table.getColumnCount(); column++) {
            int width = 15; // Min width
            for (int row = 0; row < table.getRowCount(); row++) {
                var renderer = table.getCellRenderer(row, column);
                var comp = table.prepareRenderer(renderer, row, column);
                width = Math.max(comp.getPreferredSize().width + 2, width);
            }
            if (width > 300)
                width = 300;
            columnModel.getColumn(column).setPreferredWidth(width);
        }
    }

    protected void configure(JTable table) {
        table.setModel(createTableModel(list));
        table.setFocusable(true);
        table.setRequestFocusEnabled(false);
        table.setTableHeader(null);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0,0));
        table.setBorder(BorderFactory.createEmptyBorder());
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    }

    public interface SelectionStateTransformable<T> {

        T getAsSelected();
    }

    private static final class SelectionAwareListCellRenderer<E> implements ListCellRenderer<E> {

        public static <E> void decorate(JList<E> list) {
            ListCellRenderer<? super E> origin = list.getCellRenderer();
            list.setCellRenderer(new SelectionAwareListCellRenderer<>(origin));
        }

        private final ListCellRenderer<E> origin;

        private SelectionAwareListCellRenderer(ListCellRenderer<E> origin) {
            this.origin = origin;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Component getListCellRendererComponent(JList<? extends E> list, E value, int index, boolean isSelected, boolean cellHasFocus) {
            if (isSelected && value instanceof SelectionStateTransformable)
                value = ((SelectionStateTransformable<E>) value).getAsSelected();

            return origin.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
    }

    /**
     * If the direct parent of the text {@code component} is an instance of {@link SymbolChanger}
     * returns that instance. Otherwise {@code null} is returned.
     * 
     * @return the symbol changer
     */
    protected SymbolChanger getSymbolChanger() {
        Component parent = component.getParent();
        return (parent instanceof SymbolChanger)? (SymbolChanger) parent : null;
    }
    
    public void enableTimer() {
        timerStopped = false;
    }
    
    public void disableTimer() {
        delayTimer.stop();
        timerStopped = true;
    }

    public T getSelectedValue() {
        int rowIndex = table.getSelectedRow();
        return (rowIndex < 0)? null: list.get(rowIndex);
    }

    static Action acceptAction = new AbstractAction() {
        /** The serial version UID */
        private static final long serialVersionUID = -6492024454591122704L;
        
        @Override
        public void actionPerformed(ActionEvent e) {
            JComponent comp = (JComponent) e.getSource();
            AutoCompleter<?> completer = (AutoCompleter<?>) comp.getClientProperty(AUTOCOMPLETER);
            completer.popupMenu.setVisible(false);
            completer.disableTimer();
            completer.acceptedListItem(completer.getSelectedValue());
        }
    };
    
    DocumentListener documentListener = new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
            doLaunchTimer();
        }
        
        @Override
        public void removeUpdate(DocumentEvent e) {
            doLaunchTimer();
        }
        
        private void doLaunchTimer() {
            if (!timerStopped) {
                if (delayTimer.isRunning())
                    delayTimer.restart();
                else if (!component.isFocusable() || component.isFocusOwner())
                    delayTimer.start();
            }
        }
        
        @Override
        public void changedUpdate(DocumentEvent e) {
        }
    };
    
    private void showPopup() throws IOException, InterruptedException {
        popupMenu.setVisible(false);
        int rowCount;
        if (component.isEnabled() && updateListData() && (rowCount = table.getModel().getRowCount()) != 0) {
            if (!(component instanceof JTextField))
                component.getDocument().addDocumentListener(documentListener);
            component.registerKeyboardAction(acceptAction,
                    KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                    JComponent.WHEN_FOCUSED);

            resizeColumnWidth(table);
            table.setPreferredScrollableViewportSize(
                    new Dimension(
                            table.getPreferredSize().width,
                            table.getRowHeight() * Math.min(rowCount, getProperties().getVisibleRowCount())));
            Rectangle cellRect = table.getCellRect(0, 0, true);
            table.scrollRectToVisible(cellRect);

            int x = 0, y = component.getHeight();
            try {
                int caretDot = component.getCaret().getDot();
                int caretMark = component.getCaret().getMark();
                int pos = Math.min(caretDot, caretMark);
                Rectangle2D rect = component.getUI().modelToView2D(component, pos, Position.Bias.Forward);
                x = (int) rect.getX();
                if (!(component instanceof JTextField))
                    y = (int)(rect.getY() + rect.getHeight());
            } catch (BadLocationException e) {
                logger.fatal(e);
            }
            popupMenu.show(component, x, y);
        }
    }
    
    static Action showAction = new AbstractAction() {
        @Serial
        private static final long serialVersionUID = -3239063033929574976L;
        
        @Override
        public void actionPerformed(ActionEvent e) {
            JComponent tf = (JComponent) e.getSource();
            AutoCompleter<?> completer = (AutoCompleter<?>) tf.getClientProperty(AUTOCOMPLETER);
            if (tf.isEnabled()) {
                if (completer.popupMenu.isVisible())
                    completer.selectNextPossibleValue();
                else {
                    try {
                        completer.showPopup();
                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                }
            }
        }
    };
    
    static Action upAction = new AbstractAction() {
        /** Serial version UID. */
        private static final long serialVersionUID = -2319631777295762823L;
        
        @Override
        public void actionPerformed(ActionEvent e) {
            JComponent tf = (JComponent) e.getSource();
            AutoCompleter<?> completer = (AutoCompleter<?>) tf.getClientProperty(AUTOCOMPLETER);
            if (tf.isEnabled()) {
                if (completer.popupMenu.isVisible())
                    completer.selectPreviousPossibleValue();
            }
        }
    };

    static Action downAction = new AbstractAction() {
        /** Serial version UID. */
        private static final long serialVersionUID = -2319631777295762823L;

        @Override
        public void actionPerformed(ActionEvent e) {
            JComponent tf = (JComponent) e.getSource();
            AutoCompleter<?> completer = (AutoCompleter<?>) tf.getClientProperty(AUTOCOMPLETER);
            if (tf.isEnabled()) {
                if (completer.popupMenu.isVisible())
                    completer.selectNextPossibleValue();
            }
        }
    };

    static Action hidePopupAction = new AbstractAction() {
        /** Serial version UID. */
        private static final long serialVersionUID = 6296293698682328185L;
        
        @Override
        public void actionPerformed(ActionEvent e) {
            JComponent tf = (JComponent) e.getSource();
            AutoCompleter<?> completer = (AutoCompleter<?>) tf.getClientProperty(AUTOCOMPLETER);
            if (tf.isEnabled())
                completer.popupMenu.setVisible(false);
        }
    };
    
    /**
     * Selects the next item in the list. It won't change the selection if the
     * currently selected item is already the last item.
     */
    protected void selectNextPossibleValue() {
        int si = table.getSelectedRow();
        if (si < table.getRowCount() - 1) {
            table.setRowSelectionInterval(si + 1, si + 1);
            table.scrollRectToVisible(table.getCellRect(si + 1, 0, true));
        }
    }
    
    /**
     * Selects the previous item in the list. It won't change the selection if
     * the currently selected item is already the first item.
     */
    protected void selectPreviousPossibleValue() {
        int si = table.getSelectedRow();
        if (si > 0) {
            table.setRowSelectionInterval(si - 1, si - 1);
            table.scrollRectToVisible(table.getCellRect(si - 1, 0, true));
        }
    }
    
    // update list model depending on the data in textfield
    protected abstract boolean updateListData() throws IOException, InterruptedException;
    
    // user has selected some item in the list. update textfield accordingly...
    protected abstract void acceptedListItem(Object selected);

    // create table model used by autocompleter popup
    protected abstract TableModel createTableModel(List<T> values);
}
