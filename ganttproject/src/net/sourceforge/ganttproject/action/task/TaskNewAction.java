/*
GanttProject is an opensource project management tool.
Copyright (C) 2011 GanttProject team

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 3
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package net.sourceforge.ganttproject.action.task;

import java.awt.event.ActionEvent;

import net.sourceforge.ganttproject.IGanttProject;
import net.sourceforge.ganttproject.action.GPAction;
import net.sourceforge.ganttproject.undo.GPUndoManager;

public class TaskNewAction extends GPAction {
  private final IGanttProject myProject;

  private final GPUndoManager myUndoManager;

  public TaskNewAction(IGanttProject project, GPUndoManager undoManager) {
    this(project, undoManager, IconSize.MENU);
  }

  private TaskNewAction(IGanttProject project, GPUndoManager undoManager, IconSize size) {
    super("task.new", size.asString());
    myProject = project;
    myUndoManager = undoManager;
  }

  @Override
  public GPAction withIcon(IconSize size) {
    return new TaskNewAction(myProject, myUndoManager, size);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    myUndoManager.undoableEdit(getLocalizedDescription(), new Runnable() {
      @Override
      public void run() {
        // TODO all actions have their actual action inside, so move newTask
        // code to here
        myProject.newTask();
      }
    });
  }
}