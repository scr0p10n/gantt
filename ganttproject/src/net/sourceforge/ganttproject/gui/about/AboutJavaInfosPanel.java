/*
GanttProject is an opensource project management tool.
Copyright (C) 2004-2011 Thomas Alexandre, GanttProject Team

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package net.sourceforge.ganttproject.gui.about;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.security.AccessControlException;
import java.util.Enumeration;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import net.sourceforge.ganttproject.gui.options.GeneralOptionPanel;
import net.sourceforge.ganttproject.language.GanttLanguage;

/**
 * About the java information panel.
 * 
 * @author athomas
 */
public class AboutJavaInfosPanel extends GeneralOptionPanel {

  public AboutJavaInfosPanel() {
    super(GanttLanguage.getInstance().getText("jinfos"), GanttLanguage.getInstance().getText("settingsJavaInfos"));

    JTable jTableProperties = new JTable();
    AboutFieldTableModel modelproperties = new AboutFieldTableModel();
    jTableProperties.setModel(modelproperties);

    try {
      Enumeration<?> props = System.getProperties().propertyNames();
      SortedSet<String> s = new TreeSet<String>();
      while (props.hasMoreElements()) {
        s.add((String) props.nextElement());
      }
      for (String prop : s) {
        modelproperties.addField(new SystemInfo(prop, System.getProperty(prop)));
      }
    } catch (AccessControlException e) {
      // This can happen when running in a sandbox (Java WebStart)
      System.err.println(e + ": " + e.getMessage());
    }

    JPanel infosPanel = new JPanel(new BorderLayout());
    infosPanel.add(new JScrollPane(jTableProperties), BorderLayout.CENTER);
    infosPanel.setPreferredSize(new Dimension(400, 350));
    vb.add(infosPanel);

    applyComponentOrientation(language.getComponentOrientation());
  }

  @Override
  public boolean applyChanges(boolean askForApply) {
    return false;
  }

  @Override
  public void initialize() {
  }

  class SystemInfo {
    private String name;

    private String value;

    public SystemInfo(String name, String value) {
      this.name = name;
      this.value = value;
    }

    public String getName() {
      return name;
    }

    public String getValue() {
      return value;
    }
  }

  class AboutFieldTableModel extends AbstractTableModel {
    private final GanttLanguage language = GanttLanguage.getInstance();

    private final String[] columnNames = { language.getText("name"), language.getText("value") };

    private final Class<?>[] columnClasses = { String.class, String.class };

    private final Vector<SystemInfo> data = new Vector<SystemInfo>();

    public void addField(SystemInfo w) {
      data.addElement(w);
      fireTableRowsInserted(data.size() - 1, data.size() - 1);
    }

    @Override
    public int getColumnCount() {
      return columnNames.length;
    }

    @Override
    public int getRowCount() {
      return data.size();
    }

    @Override
    public String getColumnName(int col) {
      return columnNames[col];
    }

    @Override
    public Class<?> getColumnClass(int c) {
      return columnClasses[c];
    }

    @Override
    public Object getValueAt(int row, int col) {
      SystemInfo info = data.elementAt(row);
      if (col == 0) {
        return info.getName();
      } else if (col == 1) {
        return info.getValue();
      } else {
        return null;
      }
    }

    @Override
    public boolean isCellEditable(int row, int col) {
      return false;
    }
  }
}
