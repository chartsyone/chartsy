/*
 * @(#)TristateCheckBoxEx.java 5/20/2011
 *
 * Copyright 2002 - 2011 JIDE Software Inc. All rights reserved.
 */
package one.chartsy.ui.swing;

import java.awt.*;

import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JCheckBox;

/**
 * TristateCheckBox is a check box with three states - selected, unselected and
 * mixed (a.k.a partial selected state). Internally it uses a new class called
 * {@link TristateButtonModel} to store the 3rd mixed state information.
 * <p/>
 * The mixed state uses a different check icon. Instead of a checked sign in the
 * selected state as in a regular check box, we use a square sign to indicate
 * the mixed state. On different L&Fs, it might look different. TristateCheckBox
 * supports most of the standard L&Fs such as Windows L&F, Metal L&F, Motif L&F,
 * Nimbus L&F, Aqua L&F etc. For most L&Fs, we use a new UIDefault
 * "TristateCheckBox.icon" to paint in three different states. However for Aqua
 * L&F, we actually leveraged a client property provided by Apple to display the
 * icon for the mixed state (refer to Radar #8930094 at
 * http://developer.apple.com
 * /library/mac/#releasenotes/Java/JavaSnowLeopardUpdate4LeopardUpdate9RN
 * /ResolvedIssues/ResolvedIssues.html). To make it extensible for other L&Fs
 * who might provide a built-in mixed state for check box, we support two types
 * of customizations.
 * 
 * <pre>
 * <ul>
 *     <li>using client property as Aqua. You can define your own client properties and use UIDefaults to tell us how
 * to set it. For example: </li>
 * "TristateCheckBox.icon", null,
 * "TristateCheckBox.setMixed.clientProperty", new Object[]{"JButton.selectedState", "indeterminate"},
 * "TristateCheckBox.clearMixed.clientProperty", new Object[]{"JButton.selectedState", null},
 * </ul>using component name. Some Synth-based L&Fs use component name to define style. If so, you can use the
 * following two UIDefaults. For example: </li>
 * "TristateCheckBox.setMixed.componentName", "HalfSelected",
 * "TristateCheckBox.clearMixed.componentName", "",
 * </pre>
 * 
 * The correct listener for state change is ActionListener. It will be fired
 * when the state is changed. The ItemListener is only fired when changing from
 * selected state to unselected state or vice versa. Only ActionListener will be
 * fired for all three states.
 */
public class TristateCheckBox extends JCheckBox {
    public static final int STATE_UNSELECTED = 0;
    public static final int STATE_SELECTED = 1;
    public static final int STATE_MIXED = 2;
    
    public TristateCheckBox(String text, Icon icon) {
        super(text, icon);
    }
    
    public TristateCheckBox(String text) {
        this(text, null);
    }
    
    public TristateCheckBox() {
        this(null);
    }
    
    @Override
    protected void init(String text, Icon icon) {
        model = createButtonModel();
        setModel(model);
        super.init(text, icon);
    }
    
    /**
     * Creates the button model. In this case, it is always a
     * TristateButtonModel.
     * 
     * @return TristateButtonModel
     */
    protected ButtonModel createButtonModel() {
        return new TristateButtonModel();
    }
    
    //	@Override
    //	public void updateUI() {
    //		super.updateUI();
    //		if (isMixed()) {
    //			adjustMixedIcon();
    //		} else {
    //			restoreMixedIcon();
    //		}
    //	}
    //
    //	protected void adjustMixedIcon() {
    //		setIcon(UIManager.getIcon("TristateCheckBox.icon"));
    //	}
    //
    //	protected void restoreMixedIcon() {
    //		setIcon(null);
    //	}
    
    /**
     * Checks if the check box is in mixed selection state.
     * 
     * @return true or false.
     */
    public boolean isMixed() {
        return getState() == STATE_MIXED;
    }
    
    /**
     * Sets the check box to mixed selection state.
     * 
     * @param b
     *            true or false. True means mixed state. False means unselected
     *            state.
     */
    public void setMixed(boolean b) {
        if (b) {
            setState(STATE_MIXED);
        } else {
            setState(STATE_UNSELECTED);
        }
    }
    
    /**
     * Gets the selection state. It could be one of the three states as defined
     * - {@link #STATE_SELECTED}, {@link #STATE_UNSELECTED} and
     * {@link #STATE_MIXED}.
     * 
     * @return one of the three selection states.
     */
    public int getState() {
        if (model instanceof TristateButtonModel)
            return ((TristateButtonModel) model).getState();
        else {
            throw new IllegalStateException(
                    "TristateButtonModel is required for TristateCheckBox");
        }
    }
    
    /**
     * Sets the selection state. It could be one of the three states as defined
     * - {@link #STATE_SELECTED}, {@link #STATE_UNSELECTED} and
     * {@link #STATE_MIXED}.
     * 
     * @param state
     *            one of the three selection states.
     */
    public void setState(int state) {
        if (model instanceof TristateButtonModel) {
            int old = ((TristateButtonModel) model).getState();
            if (old != state)
                ((TristateButtonModel) model).setState(state);
            //			stateUpdated(state);
        } else {
            throw new IllegalStateException(
                    "TristateButtonModel is required for TristateCheckBox");
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        // Workaround for missing borders in checkbox appearance on Windows-based LAF
        ((Graphics2D) g).translate(-0.04, -0.04);

        if (isMixed()) {
            Graphics2D g2 = (Graphics2D) g;
            Composite old = g2.getComposite();
            try {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                super.paintComponent(g2);
            } finally {
                g2.setComposite(old);
            }
        } else {
            super.paintComponent(g);
        }
    }
    
    //	/**
    //	 * This method is called when the selection state changes.
    //	 * 
    //	 * @param state
    //	 *            the new selection state.
    //	 */
    //	protected void stateUpdated(int state) {
    //		if (state == STATE_MIXED) {
    //			adjustMixedIcon();
    //			// Object cp =
    //			// UIDefaultsLookup.get("TristateCheckBox.setMixed.clientProperty");
    //			// if (cp != null) {
    //			// putClientProperty(((Object[]) cp)[0], ((Object[]) cp)[1]); // for
    //			// Aqua L&F
    //			// }
    //			// String name =
    //			// UIDefaultsLookup.getString("TristateCheckBox.setMixed.componentName");
    //			// if (name != null) {
    //			// setName(name); // for Synthetica
    //			// }
    //		} else {
    //			restoreMixedIcon();
    //			// Object cp =
    //			// UIDefaultsLookup.get("TristateCheckBox.clearMixed.clientProperty");
    //			// if (cp != null) {
    //			// putClientProperty(((Object[]) cp)[0], ((Object[]) cp)[1]); // for
    //			// Aqua L&F
    //			// }
    //			// String name =
    //			// UIDefaultsLookup.getString("TristateCheckBox.clearMixed.componentName");
    //			// if (name != null) {
    //			// setName(name); // for Synthetica
    //			// }
    //		}
    //	}
}
