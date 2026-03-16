package com.boomaa.opends.display.tabs;

import com.boomaa.opends.display.elements.GBCPanelBuilder;
import com.boomaa.opends.display.frames.FrameBase;
import com.boomaa.opends.usb.Component;
import com.boomaa.opends.usb.ControlDevices;
import com.boomaa.opends.usb.HIDDevice;
import com.boomaa.opends.usb.VirtualController;
import com.boomaa.opends.util.Debug;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;

/**
 * A tab that provides on-screen arrow keys and gamepad buttons to simulate
 * controller/joystick inputs. The virtual gamepad is registered as a
 * controller device and its axes/buttons are sent to the robot just
 * like a real USB controller.
 *
 * Keyboard bindings:
 *   WASD              -> Left stick axes (X, Y)
 *   IJKL              -> Right stick axes (Z, RY)
 *   Q / U             -> Left / Right triggers (RZ, RX)
 *   Arrow Keys        -> A (down), B (right), X (left), Y (up)
 *   E / O             -> LB, RB bumpers
 *   7-8               -> Back, Start
 *   T / G / F / H    -> D-Pad Up / Down / Left / Right
 *
 * On-screen buttons mirror the same inputs with click/hold.
 */
public class VirtualControllerTab extends TabBase {
    private VirtualController virtualCtrl;
    private HIDDevice virtualDevice;
    private Map<Integer, Runnable> keyPressActions;
    private Map<Integer, Runnable> keyReleaseActions;

    // Axis state tracking for keyboard (multiple keys can be held)
    private boolean leftUp, leftDown, leftLeft, leftRight;
    private boolean rightUp, rightDown, rightLeft, rightRight;
    private boolean triggerLeft, triggerRight;

    // D-pad directional state (POV hat)
    private boolean dpadUp, dpadDown, dpadLeft, dpadRight;

    // UI labels for live axis values
    private JLabel lblLX, lblLY, lblRX, lblRY, lblLT, lblRT;

    // Maps key codes to their corresponding on-screen buttons for visual highlighting
    private Map<Integer, JButton> keyToButton;

    public VirtualControllerTab() {
        super(new Dimension(520, 360));
    }

    @Override
    public void config() {
        keyPressActions = new HashMap<>();
        keyReleaseActions = new HashMap<>();
        keyToButton = new HashMap<>();

        virtualCtrl = new VirtualController();
        virtualDevice = new HIDDevice(virtualCtrl);
        ControlDevices.getAll().put(virtualDevice.getIdx(), virtualDevice);
        JoystickTab.EmbeddedJDEC.LIST_MODEL.addElement(virtualDevice);
        Debug.println("Virtual gamepad controller registered at index " + virtualDevice.getIdx());

        setLayout(new GridBagLayout());
        GBCPanelBuilder base = new GBCPanelBuilder(this)
            .setFill(GridBagConstraints.BOTH)
            .setAnchor(GridBagConstraints.CENTER)
            .setInsets(new Insets(4, 4, 4, 4));

        // --- Enable checkbox ---
        JCheckBox enableCb = new JCheckBox("Enable Virtual Gamepad", true);
        enableCb.addItemListener(e -> {
            boolean sel = enableCb.isSelected();
            virtualDevice.setDisabled(!sel);
            Debug.println("Virtual gamepad " + (sel ? "enabled" : "disabled"));
        });
        base.clone().setPos(0, 0, 4, 1).setFill(GridBagConstraints.NONE).build(enableCb);

        // --- Left Stick (WASD) ---
        JPanel leftStickPanel = createStickPanel("Left Stick (WASD)",
            Component.Axis.X, Component.Axis.Y, true, true);
        base.clone().setPos(0, 1, 2, 3).build(leftStickPanel);

        // --- Right Stick (IJKL) ---
        JPanel rightStickPanel = createStickPanel("Right Stick (IJKL)",
            Component.Axis.Z, Component.Axis.RY, false, true);
        base.clone().setPos(2, 1, 2, 3).build(rightStickPanel);

        // --- Triggers ---
        JPanel triggerPanel = new JPanel(new GridBagLayout());
        triggerPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Triggers",
            TitledBorder.CENTER, TitledBorder.TOP));
        GBCPanelBuilder tBase = new GBCPanelBuilder(triggerPanel)
            .setInsets(new Insets(2, 4, 2, 4))
            .setFill(GridBagConstraints.HORIZONTAL);

        JButton ltBtn = makeHoldButton("LT (Q)");
        JButton rtBtn = makeHoldButton("RT (U)");
        lblLT = new JLabel("0.00", SwingConstants.CENTER);
        lblRT = new JLabel("0.00", SwingConstants.CENTER);

        setupHold(ltBtn, () -> { triggerLeft = true; updateTriggers(); },
                          () -> { triggerLeft = false; updateTriggers(); });
        setupHold(rtBtn, () -> { triggerRight = true; updateTriggers(); },
                          () -> { triggerRight = false; updateTriggers(); });

        keyToButton.put(KeyEvent.VK_Q, ltBtn);
        keyToButton.put(KeyEvent.VK_U, rtBtn);

        tBase.clone().setPos(0, 0, 1, 1).build(ltBtn);
        tBase.clone().setPos(1, 0, 1, 1).build(rtBtn);
        tBase.clone().setPos(0, 1, 1, 1).setFill(GridBagConstraints.NONE).build(lblLT);
        tBase.clone().setPos(1, 1, 1, 1).setFill(GridBagConstraints.NONE).build(lblRT);

        base.clone().setPos(0, 4, 2, 1).build(triggerPanel);

        // --- Gamepad Buttons ---
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        buttonPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Buttons",
            TitledBorder.CENTER, TitledBorder.TOP));
        GBCPanelBuilder bBase = new GBCPanelBuilder(buttonPanel)
            .setInsets(new Insets(2, 3, 2, 3));

        String[] btnLabels = {"A(\u2193)", "B(\u2192)", "X(\u2190)", "Y(\u2191)",
            "LB(E)", "RB(O)", "Back(7)", "Start(8)"};
        int[] btnKeys = {KeyEvent.VK_DOWN, KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT, KeyEvent.VK_UP,
            KeyEvent.VK_E, KeyEvent.VK_O, KeyEvent.VK_7, KeyEvent.VK_8};
        Color[] btnColors = {
            new Color(0x4CAF50), new Color(0xF44336), new Color(0x2196F3), new Color(0xFFEB3B),
            null, null, null, null
        };
        for (int i = 0; i < btnLabels.length; i++) {
            JButton btn = makeHoldButton(btnLabels[i]);
            if (btnColors[i] != null) {
                btn.setForeground(btnColors[i]);
            }
            final int idx = i;
            setupHold(btn,
                () -> { virtualCtrl.setButton(idx, true); },
                () -> { virtualCtrl.setButton(idx, false); });
            keyToButton.put(btnKeys[i], btn);
            bBase.clone().setPos(i % 4, i / 4, 1, 1).build(btn);
        }

        // Buttons panel spans rows 4-5 on the right side
        base.clone().setPos(2, 4, 2, 2).build(buttonPanel);

        // --- D-Pad ---
        JPanel dpadPanel = createDpadPanel();
        base.clone().setPos(0, 5, 2, 1).build(dpadPanel);

        // --- Keyboard bindings ---
        setupKeyBindings();
        installKeyBindings();

        Debug.println("VirtualControllerTab configured");
    }

    private JPanel createStickPanel(String title, Component.Axis xAxis, Component.Axis yAxis, boolean isLeft, boolean showLabels) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), title,
            TitledBorder.CENTER, TitledBorder.TOP));

        GBCPanelBuilder gb = new GBCPanelBuilder(panel)
            .setInsets(new Insets(2, 2, 2, 2))
            .setFill(GridBagConstraints.BOTH);

        JButton upBtn = makeHoldButton("\u25B2");
        JButton downBtn = makeHoldButton("\u25BC");
        JButton leftBtn = makeHoldButton("\u25C0");
        JButton rightBtn = makeHoldButton("\u25B6");

        JLabel xLabel = new JLabel("X: 0.00", SwingConstants.CENTER);
        JLabel yLabel = new JLabel("Y: 0.00", SwingConstants.CENTER);

        if (isLeft) {
            lblLX = xLabel;
            lblLY = yLabel;
            setupHold(upBtn,    () -> { leftUp = true; updateLeftStick(); },
                                () -> { leftUp = false; updateLeftStick(); });
            setupHold(downBtn,  () -> { leftDown = true; updateLeftStick(); },
                                () -> { leftDown = false; updateLeftStick(); });
            setupHold(leftBtn,  () -> { leftLeft = true; updateLeftStick(); },
                                () -> { leftLeft = false; updateLeftStick(); });
            setupHold(rightBtn, () -> { leftRight = true; updateLeftStick(); },
                                () -> { leftRight = false; updateLeftStick(); });
        } else {
            lblRX = xLabel;
            lblRY = yLabel;
            setupHold(upBtn,    () -> { rightUp = true; updateRightStick(); },
                                () -> { rightUp = false; updateRightStick(); });
            setupHold(downBtn,  () -> { rightDown = true; updateRightStick(); },
                                () -> { rightDown = false; updateRightStick(); });
            setupHold(leftBtn,  () -> { rightLeft = true; updateRightStick(); },
                                () -> { rightLeft = false; updateRightStick(); });
            setupHold(rightBtn, () -> { rightRight = true; updateRightStick(); },
                                () -> { rightRight = false; updateRightStick(); });
        }

        // Layout:
        //        [up]
        // [left][down][right]
        //   X: ...   Y: ...
        gb.clone().setPos(1, 0, 1, 1).build(upBtn);
        gb.clone().setPos(0, 1, 1, 1).build(leftBtn);
        gb.clone().setPos(1, 1, 1, 1).build(downBtn);
        gb.clone().setPos(2, 1, 1, 1).build(rightBtn);
        if (showLabels) {
            gb.clone().setPos(0, 2, 1, 1).setFill(GridBagConstraints.NONE).build(xLabel);
            gb.clone().setPos(2, 2, 1, 1).setFill(GridBagConstraints.NONE).build(yLabel);
        }

        // Register arrow buttons for keyboard highlighting
        if (isLeft) {
            keyToButton.put(KeyEvent.VK_W, upBtn);
            keyToButton.put(KeyEvent.VK_S, downBtn);
            keyToButton.put(KeyEvent.VK_A, leftBtn);
            keyToButton.put(KeyEvent.VK_D, rightBtn);
        } else {
            keyToButton.put(KeyEvent.VK_I, upBtn);
            keyToButton.put(KeyEvent.VK_K, downBtn);
            keyToButton.put(KeyEvent.VK_J, leftBtn);
            keyToButton.put(KeyEvent.VK_L, rightBtn);
        }

        return panel;
    }

    private JPanel createDpadPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "D-Pad (T/G/F/H)",
            TitledBorder.CENTER, TitledBorder.TOP));

        GBCPanelBuilder gb = new GBCPanelBuilder(panel)
            .setInsets(new Insets(2, 2, 2, 2))
            .setFill(GridBagConstraints.BOTH);

        JButton upBtn    = makeHoldButton("\u25B2");
        JButton downBtn  = makeHoldButton("\u25BC");
        JButton leftBtn  = makeHoldButton("\u25C0");
        JButton rightBtn = makeHoldButton("\u25B6");

        setupHold(upBtn,
            () -> { dpadUp = true;    updateDpad(); },
            () -> { dpadUp = false;   updateDpad(); });
        setupHold(downBtn,
            () -> { dpadDown = true;  updateDpad(); },
            () -> { dpadDown = false; updateDpad(); });
        setupHold(leftBtn,
            () -> { dpadLeft = true;  updateDpad(); },
            () -> { dpadLeft = false; updateDpad(); });
        setupHold(rightBtn,
            () -> { dpadRight = true;  updateDpad(); },
            () -> { dpadRight = false; updateDpad(); });

        keyToButton.put(KeyEvent.VK_T, upBtn);
        keyToButton.put(KeyEvent.VK_G, downBtn);
        keyToButton.put(KeyEvent.VK_F, leftBtn);
        keyToButton.put(KeyEvent.VK_H, rightBtn);

        // Layout:
        //        [up]
        // [left][down][right]
        gb.clone().setPos(1, 0, 1, 1).build(upBtn);
        gb.clone().setPos(0, 1, 1, 1).build(leftBtn);
        gb.clone().setPos(1, 1, 1, 1).build(downBtn);
        gb.clone().setPos(2, 1, 1, 1).build(rightBtn);

        return panel;
    }

    private void setupKeyBindings() {
        // Left stick: WASD
        bindKey(KeyEvent.VK_W,     () -> { leftUp = true; updateLeftStick(); },
                                   () -> { leftUp = false; updateLeftStick(); });
        bindKey(KeyEvent.VK_S,     () -> { leftDown = true; updateLeftStick(); },
                                   () -> { leftDown = false; updateLeftStick(); });
        bindKey(KeyEvent.VK_A,     () -> { leftLeft = true; updateLeftStick(); },
                                   () -> { leftLeft = false; updateLeftStick(); });
        bindKey(KeyEvent.VK_D,     () -> { leftRight = true; updateLeftStick(); },
                                   () -> { leftRight = false; updateLeftStick(); });

        // Right stick: IJKL
        bindKey(KeyEvent.VK_I, () -> { rightUp = true; updateRightStick(); },
                               () -> { rightUp = false; updateRightStick(); });
        bindKey(KeyEvent.VK_K, () -> { rightDown = true; updateRightStick(); },
                               () -> { rightDown = false; updateRightStick(); });
        bindKey(KeyEvent.VK_J, () -> { rightLeft = true; updateRightStick(); },
                               () -> { rightLeft = false; updateRightStick(); });
        bindKey(KeyEvent.VK_L, () -> { rightRight = true; updateRightStick(); },
                               () -> { rightRight = false; updateRightStick(); });

        // Triggers: Q (left), U (right)
        bindKey(KeyEvent.VK_Q, () -> { triggerLeft = true; updateTriggers(); },
                               () -> { triggerLeft = false; updateTriggers(); });
        bindKey(KeyEvent.VK_U, () -> { triggerRight = true; updateTriggers(); },
                               () -> { triggerRight = false; updateTriggers(); });

        // Buttons: arrow keys for ABXY, E/O for bumpers, 7-8 for Bk/St
        bindKey(KeyEvent.VK_DOWN,
            () -> virtualCtrl.setButton(0, true),
            () -> virtualCtrl.setButton(0, false));
        bindKey(KeyEvent.VK_RIGHT,
            () -> virtualCtrl.setButton(1, true),
            () -> virtualCtrl.setButton(1, false));
        bindKey(KeyEvent.VK_LEFT,
            () -> virtualCtrl.setButton(2, true),
            () -> virtualCtrl.setButton(2, false));
        bindKey(KeyEvent.VK_UP,
            () -> virtualCtrl.setButton(3, true),
            () -> virtualCtrl.setButton(3, false));
        bindKey(KeyEvent.VK_E,
            () -> virtualCtrl.setButton(4, true),
            () -> virtualCtrl.setButton(4, false));
        bindKey(KeyEvent.VK_O,
            () -> virtualCtrl.setButton(5, true),
            () -> virtualCtrl.setButton(5, false));
        bindKey(KeyEvent.VK_7,
            () -> virtualCtrl.setButton(6, true),
            () -> virtualCtrl.setButton(6, false));
        bindKey(KeyEvent.VK_8,
            () -> virtualCtrl.setButton(7, true),
            () -> virtualCtrl.setButton(7, false));

        // D-Pad POV hat: T (up), G (down), F (left), H (right)
        bindKey(KeyEvent.VK_T,
            () -> { dpadUp = true;    updateDpad(); },
            () -> { dpadUp = false;   updateDpad(); });
        bindKey(KeyEvent.VK_G,
            () -> { dpadDown = true;  updateDpad(); },
            () -> { dpadDown = false; updateDpad(); });
        bindKey(KeyEvent.VK_F,
            () -> { dpadLeft = true;  updateDpad(); },
            () -> { dpadLeft = false; updateDpad(); });
        bindKey(KeyEvent.VK_H,
            () -> { dpadRight = true;  updateDpad(); },
            () -> { dpadRight = false; updateDpad(); });
    }

    private void bindKey(int keyCode, Runnable onPress, Runnable onRelease) {
        keyPressActions.put(keyCode, onPress);
        keyReleaseActions.put(keyCode, onRelease);
    }

    /**
     * Installs all key bindings into the InputMap/ActionMap using
     * WHEN_IN_FOCUSED_WINDOW so they fire regardless of which child
     * component currently has focus. Actions are guarded to only
     * execute when this tab is the visible tab.
     */
    private void installKeyBindings() {
        javax.swing.InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        javax.swing.ActionMap actionMap = getActionMap();

        for (Map.Entry<Integer, Runnable> entry : keyPressActions.entrySet()) {
            int code = entry.getKey();
            Runnable action = entry.getValue();
            String name = "vgp_pressed_" + code;
            inputMap.put(KeyStroke.getKeyStroke(code, 0, false), name);
            actionMap.put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (TabBase.isVisible(VirtualControllerTab.class)) {
                        action.run();
                        JButton btn = keyToButton.get(code);
                        if (btn != null) {
                            btn.getModel().setArmed(true);
                            btn.getModel().setPressed(true);
                        }
                    }
                }
            });
        }

        for (Map.Entry<Integer, Runnable> entry : keyReleaseActions.entrySet()) {
            int code = entry.getKey();
            Runnable action = entry.getValue();
            String name = "vgp_released_" + code;
            inputMap.put(KeyStroke.getKeyStroke(code, 0, true), name);
            actionMap.put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (TabBase.isVisible(VirtualControllerTab.class)) {
                        action.run();
                        JButton btn = keyToButton.get(code);
                        if (btn != null) {
                            btn.getModel().setPressed(false);
                            btn.getModel().setArmed(false);
                        }
                    }
                }
            });
        }
    }

    private void updateLeftStick() {
        double x = (leftRight ? 1.0 : 0.0) - (leftLeft ? 1.0 : 0.0);
        double y = (leftDown ? 1.0 : 0.0) - (leftUp ? 1.0 : 0.0);
        virtualCtrl.setAxis(Component.Axis.X, x);
        virtualCtrl.setAxis(Component.Axis.Y, y);
        if (lblLX != null) {
            lblLX.setText(String.format("X: %.2f", x));
        }
        if (lblLY != null) {
            lblLY.setText(String.format("Y: %.2f", y));
        }
    }

    private void updateRightStick() {
        double x = (rightRight ? 1.0 : 0.0) - (rightLeft ? 1.0 : 0.0);
        double y = (rightDown ? 1.0 : 0.0) - (rightUp ? 1.0 : 0.0);
        virtualCtrl.setAxis(Component.Axis.Z, x);
        virtualCtrl.setAxis(Component.Axis.RY, y);
        if (lblRX != null) {
            lblRX.setText(String.format("X: %.2f", x));
        }
        if (lblRY != null) {
            lblRY.setText(String.format("Y: %.2f", y));
        }
    }

    private void updateDpad() {
        int angle;
        if (dpadUp && dpadRight)        angle = 45;
        else if (dpadDown && dpadRight) angle = 135;
        else if (dpadDown && dpadLeft)  angle = 225;
        else if (dpadUp && dpadLeft)    angle = 315;
        else if (dpadUp)                angle = 0;
        else if (dpadRight)             angle = 90;
        else if (dpadDown)              angle = 180;
        else if (dpadLeft)              angle = 270;
        else                            angle = -1;
        virtualCtrl.setPov(angle);
    }

    private void updateTriggers() {
        double lt = triggerLeft ? 1.0 : 0.0;
        double rt = triggerRight ? 1.0 : 0.0;
        virtualCtrl.setAxis(Component.Axis.RZ, lt);
        virtualCtrl.setAxis(Component.Axis.RX, rt);
        if (lblLT != null) {
            lblLT.setText(String.format("%.2f", lt));
        }
        if (lblRT != null) {
            lblRT.setText(String.format("%.2f", rt));
        }
    }

    private JButton makeHoldButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusable(false);
        btn.setMargin(new Insets(4, 8, 4, 8));
        return btn;
    }

    private JButton makeCompactButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusable(false);
        btn.setMargin(new Insets(1, 2, 1, 2));
        return btn;
    }

    private void setupHold(JButton btn, Runnable onPress, Runnable onRelease) {
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                onPress.run();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                onRelease.run();
            }
        });
    }

    public HIDDevice getVirtualDevice() {
        return virtualDevice;
    }

    /**
     * Switch between the full vertical layout (compact=false, normal tab view)
     * and a compact horizontal layout (compact=true) suitable for a ~185 px tall
     * combined control strip. All five sections (Left Stick, Right Stick,
     * Triggers, Buttons, D-Pad) are placed in a single row.
     */
    public void setCompact(boolean compact) {
        removeAll();
        keyToButton = new HashMap<>();
        // Axis/button state flags are preserved across layout changes.

        setLayout(new GridBagLayout());
        if (compact) {
            setPreferredSize(null);
            GBCPanelBuilder base = new GBCPanelBuilder(this)
                .setFill(GridBagConstraints.BOTH)
                .setAnchor(GridBagConstraints.CENTER)
                .setInsets(new Insets(0, 1, 0, 1));

            // --- D-Pad (leftmost) ---
            JPanel dpadPanel = createDpadPanel();

            // --- Bumpers + Triggers + Sticks ---
            // LB / RB buttons
            JButton lbBtn = makeCompactButton("LB(E)");
            JButton rbBtn = makeCompactButton("RB(O)");
            setupHold(lbBtn,
                () -> virtualCtrl.setButton(4, true),
                () -> virtualCtrl.setButton(4, false));
            setupHold(rbBtn,
                () -> virtualCtrl.setButton(5, true),
                () -> virtualCtrl.setButton(5, false));
            keyToButton.put(KeyEvent.VK_E, lbBtn);
            keyToButton.put(KeyEvent.VK_O, rbBtn);

            // LT / RT trigger buttons (small)
            JButton ltBtn = makeCompactButton("LT(Q)");
            JButton rtBtn = makeCompactButton("RT(U)");
            lblLT = new JLabel("", SwingConstants.CENTER);
            lblRT = new JLabel("", SwingConstants.CENTER);
            setupHold(ltBtn, () -> {
                triggerLeft = true;
                updateTriggers();
            }, () -> {
                triggerLeft = false;
                updateTriggers();
            });
            setupHold(rtBtn, () -> {
                triggerRight = true;
                updateTriggers();
            }, () -> {
                triggerRight = false;
                updateTriggers();
            });
            keyToButton.put(KeyEvent.VK_Q, ltBtn);
            keyToButton.put(KeyEvent.VK_U, rtBtn);

            // Left stick cross-pad
            JPanel leftStickPanel = createStickPanel("L Stick (WASD)",
                Component.Axis.X, Component.Axis.Y, true, false);
            // Right stick cross-pad
            JPanel rightStickPanel = createStickPanel("R Stick (IJKL)",
                Component.Axis.Z, Component.Axis.RY, false, false);

            // Left column: LB -> LT -> Left Stick (vertical)
            JPanel leftCol = new JPanel(new GridBagLayout());
            GBCPanelBuilder lc = new GBCPanelBuilder(leftCol)
                .setFill(GridBagConstraints.BOTH)
                .setInsets(new Insets(0, 0, 0, 0));
            lc.clone().setPos(0, 0, 1, 1).setWeightX(1.0).setWeightY(0.0).build(lbBtn);
            lc.clone().setPos(0, 1, 1, 1).setWeightX(1.0).setWeightY(0.0).build(ltBtn);
            lc.clone().setPos(0, 2, 1, 1).setWeightX(1.0).setWeightY(1.0).build(leftStickPanel);

            // Right column: RB -> RT -> Right Stick (vertical)
            JPanel rightCol = new JPanel(new GridBagLayout());
            GBCPanelBuilder rc = new GBCPanelBuilder(rightCol)
                .setFill(GridBagConstraints.BOTH)
                .setInsets(new Insets(0, 0, 0, 0));
            rc.clone().setPos(0, 0, 1, 1).setWeightX(1.0).setWeightY(0.0).build(rbBtn);
            rc.clone().setPos(0, 1, 1, 1).setWeightX(1.0).setWeightY(0.0).build(rtBtn);
            rc.clone().setPos(0, 2, 1, 1).setWeightX(1.0).setWeightY(1.0).build(rightStickPanel);

            // --- Face buttons: ABXY diamond + Bk/St ---
            JPanel facePanel = new JPanel(new GridBagLayout());
            GBCPanelBuilder fb = new GBCPanelBuilder(facePanel)
                .setFill(GridBagConstraints.NONE)
                .setAnchor(GridBagConstraints.CENTER)
                .setInsets(new Insets(0, 0, 0, 0));

            // ABXY in diamond: Y top, X left, B right, A bottom
            JButton btnY = makeCompactButton("Y(\u2191)");
            btnY.setForeground(new Color(0xFFEB3B));
            JButton btnX = makeCompactButton("X(\u2190)");
            btnX.setForeground(new Color(0x2196F3));
            JButton btnB = makeCompactButton("B(\u2192)");
            btnB.setForeground(new Color(0xF44336));
            JButton btnA = makeCompactButton("A(\u2193)");
            btnA.setForeground(new Color(0x4CAF50));
            setupHold(btnA,
                () -> virtualCtrl.setButton(0, true),
                () -> virtualCtrl.setButton(0, false));
            setupHold(btnB,
                () -> virtualCtrl.setButton(1, true),
                () -> virtualCtrl.setButton(1, false));
            setupHold(btnX,
                () -> virtualCtrl.setButton(2, true),
                () -> virtualCtrl.setButton(2, false));
            setupHold(btnY,
                () -> virtualCtrl.setButton(3, true),
                () -> virtualCtrl.setButton(3, false));
            keyToButton.put(KeyEvent.VK_DOWN, btnA);
            keyToButton.put(KeyEvent.VK_RIGHT, btnB);
            keyToButton.put(KeyEvent.VK_LEFT, btnX);
            keyToButton.put(KeyEvent.VK_UP, btnY);

            //       [Y]
            //  [X]       [B]
            //       [A]
            fb.clone().setPos(1, 0, 1, 1).build(btnY);
            fb.clone().setPos(0, 1, 1, 1).build(btnX);
            fb.clone().setPos(2, 1, 1, 1).build(btnB);
            fb.clone().setPos(1, 2, 1, 1).build(btnA);

            // Bk / St below diamond
            JButton bkBtn = makeCompactButton("Bk(7)");
            JButton stBtn = makeCompactButton("St(8)");
            setupHold(bkBtn,
                () -> virtualCtrl.setButton(6, true),
                () -> virtualCtrl.setButton(6, false));
            setupHold(stBtn,
                () -> virtualCtrl.setButton(7, true),
                () -> virtualCtrl.setButton(7, false));
            keyToButton.put(KeyEvent.VK_7, bkBtn);
            keyToButton.put(KeyEvent.VK_8, stBtn);
            fb.clone().setPos(0, 3, 1, 1).build(bkBtn);
            fb.clone().setPos(2, 3, 1, 1).build(stBtn);

            // Top-level columns: DPad | Left | Right | Face
            base.clone().setPos(0, 0, 1, 1).setWeightX(0.8).setWeightY(1.0)
                .setFill(GridBagConstraints.HORIZONTAL)
                .setAnchor(GridBagConstraints.NORTH).build(dpadPanel);
            base.clone().setPos(1, 0, 1, 1).setWeightX(1.0).setWeightY(1.0)
                .setFill(GridBagConstraints.HORIZONTAL)
                .setAnchor(GridBagConstraints.NORTH).build(leftCol);
            base.clone().setPos(2, 0, 1, 1).setWeightX(1.0).setWeightY(1.0)
                .setFill(GridBagConstraints.HORIZONTAL)
                .setAnchor(GridBagConstraints.NORTH).build(rightCol);
            base.clone().setPos(3, 0, 1, 1).setWeightX(2).setWeightY(1.0)
                .setFill(GridBagConstraints.HORIZONTAL)
                .setAnchor(GridBagConstraints.NORTH).build(facePanel);
        } else {
            Dimension fullSize = new Dimension(520, 360);
            FrameBase.applyNonWindowsScaling(fullSize);
            setPreferredSize(fullSize);
            GBCPanelBuilder base = new GBCPanelBuilder(this)
                .setFill(GridBagConstraints.BOTH)
                .setAnchor(GridBagConstraints.CENTER)
                .setInsets(new Insets(4, 4, 4, 4));

            JCheckBox enableCb = new JCheckBox("Enable Virtual Gamepad", true);
            enableCb.addItemListener(e -> {
                boolean sel = enableCb.isSelected();
                virtualDevice.setDisabled(!sel);
                Debug.println("Virtual gamepad " + (sel ? "enabled" : "disabled"));
            });
            base.clone().setPos(0, 0, 4, 1).setFill(GridBagConstraints.NONE).build(enableCb);

            JPanel leftStickPanel = createStickPanel("Left Stick (WASD)",
                Component.Axis.X, Component.Axis.Y, true, true);
            base.clone().setPos(0, 1, 2, 3).build(leftStickPanel);

            JPanel rightStickPanel = createStickPanel("Right Stick (IJKL)",
                Component.Axis.Z, Component.Axis.RY, false, true);
            base.clone().setPos(2, 1, 2, 3).build(rightStickPanel);

            JPanel triggerPanel = new JPanel(new GridBagLayout());
            triggerPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Triggers",
                TitledBorder.CENTER, TitledBorder.TOP));
            GBCPanelBuilder tBase = new GBCPanelBuilder(triggerPanel)
                .setInsets(new Insets(2, 4, 2, 4))
                .setFill(GridBagConstraints.HORIZONTAL);
            JButton ltBtn = makeHoldButton("LT (Q)");
            JButton rtBtn = makeHoldButton("RT (U)");
            lblLT = new JLabel("0.00", SwingConstants.CENTER);
            lblRT = new JLabel("0.00", SwingConstants.CENTER);
            setupHold(ltBtn, () -> {
                triggerLeft = true;
                updateTriggers();
            }, () -> {
                triggerLeft = false;
                updateTriggers();
            });
            setupHold(rtBtn, () -> {
                triggerRight = true;
                updateTriggers();
            }, () -> {
                triggerRight = false;
                updateTriggers();
            });
            keyToButton.put(KeyEvent.VK_Q, ltBtn);
            keyToButton.put(KeyEvent.VK_U, rtBtn);
            tBase.clone().setPos(0, 0, 1, 1).build(ltBtn);
            tBase.clone().setPos(1, 0, 1, 1).build(rtBtn);
            tBase.clone().setPos(0, 1, 1, 1).setFill(GridBagConstraints.NONE).build(lblLT);
            tBase.clone().setPos(1, 1, 1, 1).setFill(GridBagConstraints.NONE).build(lblRT);
            base.clone().setPos(0, 4, 2, 1).build(triggerPanel);

            JPanel buttonPanel = new JPanel(new GridBagLayout());
            buttonPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Buttons",
                TitledBorder.CENTER, TitledBorder.TOP));
            GBCPanelBuilder bBase = new GBCPanelBuilder(buttonPanel)
                .setInsets(new Insets(2, 3, 2, 3));
            String[] btnLabels2 = {"A(\u2193)", "B(\u2192)", "X(\u2190)", "Y(\u2191)",
                "LB(E)", "RB(O)", "Back(7)", "Start(8)"};
            int[] btnKeys2 = {KeyEvent.VK_DOWN, KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT, KeyEvent.VK_UP,
                KeyEvent.VK_E, KeyEvent.VK_O, KeyEvent.VK_7, KeyEvent.VK_8};
            Color[] btnColors2 = {
                new Color(0x4CAF50), new Color(0xF44336), new Color(0x2196F3), new Color(0xFFEB3B),
                null, null, null, null
            };
            for (int i = 0; i < btnLabels2.length; i++) {
                JButton btn = makeHoldButton(btnLabels2[i]);
                if (btnColors2[i] != null) {
                    btn.setForeground(btnColors2[i]);
                }
                final int idx = i;
                setupHold(btn,
                    () -> virtualCtrl.setButton(idx, true),
                    () -> virtualCtrl.setButton(idx, false));
                keyToButton.put(btnKeys2[i], btn);
                bBase.clone().setPos(i % 4, i / 4, 1, 1).build(btn);
            }
            base.clone().setPos(2, 4, 2, 2).build(buttonPanel);

            JPanel dpadPanel = createDpadPanel();
            base.clone().setPos(0, 5, 2, 1).build(dpadPanel);
        }

        setupKeyBindings();
        installKeyBindings();
        revalidate();
        repaint();
    }
}
