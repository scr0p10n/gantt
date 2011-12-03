package net.sourceforge.ganttproject.gui;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;

import net.sourceforge.ganttproject.action.GPAction;
import net.sourceforge.ganttproject.language.GanttLanguage;

class NotificationComponent implements NotificationChannel.Listener {
    private final JPanel myComponent;
    private final Action[] myActions;
    int myPosition;
    private Action myBackwardAction;
    private Action myForwardAction;
    private final Set<NotificationItem> myNotifications = new HashSet<NotificationItem>();
    private final NotificationChannel myChannel;
    private Action myClearAction;
    private final NotificationSlider mySlider;

    NotificationComponent(NotificationChannel channel, NotificationSlider slider) {
        mySlider = slider;
        myComponent = new JPanel(new CardLayout());
        List<Action> actions = new ArrayList<Action>();
        myBackwardAction = createBackwardAction();
        myForwardAction = createForwardAction();
        myClearAction = createClearAction();
        actions.add(myBackwardAction);
        actions.add(myForwardAction);
        actions.add(myClearAction);
        myActions = actions.toArray(new Action[0]);
        myChannel = channel;
        myChannel.addListener(this);
        processItems();
    }

    private void processItems() {
        if (myChannel.getItems().isEmpty() && myChannel.getDefaultNotification() != null) {
            addNotification(myChannel.getDefaultNotification(), myChannel);
        }
        for (NotificationItem notification : myChannel.getItems()) {
            addNotification(notification, myChannel);
        }
        if (!myNotifications.isEmpty()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    updateEnabled();
                }
            });
        }
    }

    void addNotification(NotificationItem item, NotificationChannel channel) {
        if (!myNotifications.contains(item)) {
            addNotification(item.myTitle, item.myBody, item.myHyperlinkListener, channel);
            myNotifications.add(item);
        }
    }

    void addNotification(String title, String body, HyperlinkListener hyperlinkListener, NotificationChannel channel) {
        JComponent htmlPane = createHtmlPane(
            GanttLanguage.getInstance().formatText("error.channel.text", title, body), hyperlinkListener);
        UIUtil.setBackgroundTree(htmlPane, channel.getColor());
        htmlPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(channel.getColor().darker()),
            BorderFactory.createEmptyBorder(3, 3, 3, 3)));

        myComponent.add(htmlPane, String.valueOf(myComponent.getComponentCount()));
    }

    private Action createBackwardAction() {
        return new GPAction("updateRss.backwardItem") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                if (myPosition > 0) {
                    ((CardLayout)myComponent.getLayout()).show(myComponent, String.valueOf(--myPosition));
                    updateEnabled();
                }
            }
        };

    }

    private Action createClearAction() {
        return new GPAction("updateRss.clear") {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                myChannel.clear();
                mySlider.hide();
            }
        };
    }


    private Action createForwardAction() {
        return new GPAction("updateRss.forwardItem") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                if (myPosition < myComponent.getComponentCount()-1) {
                    ((CardLayout)myComponent.getLayout()).show(myComponent, String.valueOf(++myPosition));
                    updateEnabled();
                }
            }
        };
    }

    private void updateEnabled() {
        assert myBackwardAction != null && myForwardAction != null;
        myBackwardAction.setEnabled(myPosition > 0);
        myForwardAction.setEnabled(myPosition < myComponent.getComponentCount() - 1);
        if (!myChannel.getItems().isEmpty()) {
            myChannel.setRead(myPosition);
        }
    }

    JComponent getComponent() {
        return myComponent;
    }

    Action[] getActions() {
        return myActions;
    }

    static JComponent createHtmlPane(String html, HyperlinkListener hyperlinkListener) {
        JEditorPane htmlPane = new JEditorPane();
        htmlPane.setEditorKit(new HTMLEditorKit());
        htmlPane.setEditable(false);
        htmlPane.setPreferredSize(new Dimension(300, 150));
        htmlPane.addHyperlinkListener(hyperlinkListener);
        htmlPane.setBackground(Color.YELLOW);
        htmlPane.setText(html);
        htmlPane.setBorder(BorderFactory.createEmptyBorder());
        JScrollPane scrollPane = new JScrollPane(htmlPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;

    }

    @Override
    public void notificationAdded() {
        processItems();
    }

    @Override
    public void notificationRead(NotificationItem item) {
        // Do nothing
    }

    @Override
    public void channelCleared() {
        myNotifications.clear();
        myComponent.removeAll();
        processItems();
    }
}
