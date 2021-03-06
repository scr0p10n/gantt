/*
GanttProject is an opensource project management tool.
Copyright (C) 2005-2011 GanttProject Team

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
package net.sourceforge.ganttproject;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import net.sourceforge.ganttproject.task.Task;
import net.sourceforge.ganttproject.task.TaskContainmentHierarchyFacade;
import net.sourceforge.ganttproject.task.TaskManager;
import net.sourceforge.ganttproject.task.TaskNode;
import net.sourceforge.ganttproject.task.dependency.TaskDependencyException;

class TaskContainmentHierarchyFacadeImpl implements TaskContainmentHierarchyFacade {
  private Map<Task, DefaultMutableTreeNode> myTask2treeNode = new HashMap<Task, DefaultMutableTreeNode>();
  private Map<Task, Integer> myTask2index = new LinkedHashMap<Task, Integer>();
  private Task myRootTask;

  private List<Task> myPathBuffer = new ArrayList<Task>();

  private GanttTree2 myTree;

  public TaskContainmentHierarchyFacadeImpl(GanttTree2 tree) {
    ArrayList<TaskNode> allTasks = tree.getAllTasks();
    for (int i = 0; i < allTasks.size(); i++) {
      TaskNode treeNode = allTasks.get(i);
      Task task = (Task) treeNode.getUserObject();
      if (treeNode.isRoot()) {
        myRootTask = task;
      }
      myTask2treeNode.put(task, treeNode);
      myTask2index.put(task, new Integer(i));
    }
    myTree = tree;
  }

  @Override
  public Task[] getNestedTasks(Task container) {
    Task[] result = null;
    DefaultMutableTreeNode treeNode = myTask2treeNode.get(container);
    if (treeNode != null) {
      ArrayList<Task> list = new ArrayList<Task>();
      for (Enumeration children = treeNode.children(); children.hasMoreElements();) {
        DefaultMutableTreeNode next = (DefaultMutableTreeNode) children.nextElement();
        if (next instanceof TaskNode) {
          list.add((Task) next.getUserObject());
        }
      }
      result = list.toArray(new Task[0]);
    }
    return result == null ? new Task[0] : result;
  }

  @Override
  public Task[] getDeepNestedTasks(Task container) {
    ArrayList<Task> result = new ArrayList<Task>();
    DefaultMutableTreeNode treeNodes = myTask2treeNode.get(container);
    if (treeNodes != null) {
      for (Enumeration subtree = treeNodes.preorderEnumeration(); subtree.hasMoreElements();) {
        DefaultMutableTreeNode curNode = (DefaultMutableTreeNode) subtree.nextElement();
        assert curNode.getUserObject() instanceof Task;
        result.add((Task) curNode.getUserObject());
      }

      // We remove the first task which is == container
      assert result.size() > 0;
      result.remove(0);
    }
    return result.toArray(new Task[result.size()]);
  }

  /**
   * Purpose: Returns true if the container Task has any nested tasks. This
   * should be a quicker check than using getNestedTasks().
   * 
   * @param container
   *          The Task on which to check for children.
   */
  @Override
  public boolean hasNestedTasks(Task container) {
    DefaultMutableTreeNode treeNode = myTask2treeNode.get(container);
    if (treeNode != null) {
      if (treeNode.children().hasMoreElements()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Task getRootTask() {
    return myRootTask;
  }

  @Override
  public Task getContainer(Task nestedTask) {
    DefaultMutableTreeNode treeNode = myTask2treeNode.get(nestedTask);
    if (treeNode == null) {
      return null;
    }
    DefaultMutableTreeNode containerNode = (DefaultMutableTreeNode) treeNode.getParent();
    return containerNode == null ? null : (Task) containerNode.getUserObject();
  }

  @Override
  public Task getPreviousSibling(Task nestedTask) {
    DefaultMutableTreeNode treeNode = myTask2treeNode.get(nestedTask);
    assert treeNode != null : "TreeNode of " + nestedTask + " not found. Please inform GanttProject developers";
    DefaultMutableTreeNode siblingNode = treeNode.getPreviousSibling();
    return siblingNode == null ? null : (Task) siblingNode.getUserObject();
  }

  @Override
  public Task getNextSibling(Task nestedTask) {
    DefaultMutableTreeNode treeNode = myTask2treeNode.get(nestedTask);
    assert treeNode != null : "TreeNode of " + nestedTask + " not found. Please inform GanttProject developers";
    DefaultMutableTreeNode siblingNode = treeNode.getNextSibling();
    return siblingNode == null ? null : (Task) siblingNode.getUserObject();
  }

  @Override
  public int getTaskIndex(Task nestedTask) {
    DefaultMutableTreeNode treeNode = myTask2treeNode.get(nestedTask);
    assert treeNode != null : "TreeNode of " + nestedTask + " not found. Please inform GanttProject developers";
    DefaultMutableTreeNode containerNode = (DefaultMutableTreeNode) treeNode.getParent();
    return containerNode.getIndex(treeNode);
  }

  @Override
  public boolean areUnrelated(Task first, Task second) {
    myPathBuffer.clear();
    for (Task container = getContainer(first); container != null; container = getContainer(container)) {
      myPathBuffer.add(container);
    }
    if (myPathBuffer.contains(second)) {
      return false;
    }
    myPathBuffer.clear();
    for (Task container = getContainer(second); container != null; container = getContainer(container)) {
      myPathBuffer.add(container);
    }
    if (myPathBuffer.contains(first)) {
      return false;
    }
    return true;
  }

  @Override
  public void move(Task whatMove, Task whereMove) {
    DefaultMutableTreeNode targetNode = myTask2treeNode.get(whereMove);
    assert targetNode != null : "Failed to find tree node for task=" + whereMove;
    move(whatMove, whereMove, targetNode.getChildCount());
  }

  @Override
  public void move(Task whatMove, Task whereMove, int index) {
    DefaultMutableTreeNode targetNode = myTask2treeNode.get(whereMove);
    DefaultMutableTreeNode movedNode = myTask2treeNode.get(whatMove);
    if (movedNode != null) {
      TreePath movedPath = new TreePath(movedNode.getPath());
      boolean wasSelected = (myTree.getJTree().getSelectionModel().isPathSelected(movedPath));
      if (wasSelected) {
        myTree.getJTree().getSelectionModel().removeSelectionPath(movedPath);
      }
      myTree.getModel().removeNodeFromParent(movedNode);
      myTree.getModel().insertNodeInto(movedNode, targetNode, index);
      if (wasSelected) {
        movedPath = new TreePath(movedNode.getPath());
        myTree.getJTree().getSelectionModel().addSelectionPath(movedPath);
      }
    } else {
      myTree.addObjectWithExpand(whatMove, targetNode);
    }
    getTaskManager().getAlgorithmCollection().getAdjustTaskBoundsAlgorithm().run(whatMove);
    try {
      getTaskManager().getAlgorithmCollection().getRecalculateTaskScheduleAlgorithm().run();
    } catch (TaskDependencyException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private TaskManager getTaskManager() {
    return myRootTask.getManager();
  }

  @Override
  public int getDepth(Task task) {
    DefaultMutableTreeNode treeNode = myTask2treeNode.get(task);
    return treeNode.getLevel();
  }

  @Override
  public int compareDocumentOrder(Task task1, Task task2) {
    Integer index1 = myTask2index.get(task1);
    Integer index2 = myTask2index.get(task2);
    return index1.intValue() - index2.intValue();
  }

  @Override
  public boolean contains(Task task) {
    return myTask2treeNode.containsKey(task);
  }

  @Override
  public List<Task> getTasksInDocumentOrder() {
    List<Task> result = new ArrayList<Task>();
    DefaultMutableTreeNode rootNode = myTask2treeNode.get(getRootTask());
    Enumeration<TreeNode> nodes = rootNode.preorderEnumeration();
    if (nodes.hasMoreElements()) {
      nodes.nextElement();
    }
    for (; nodes.hasMoreElements();) {
      DefaultMutableTreeNode nextNode = (DefaultMutableTreeNode) nodes.nextElement();
      result.add((Task) nextNode.getUserObject());
    }
    return result;
  }
}