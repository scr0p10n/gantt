/*
GanttProject is an opensource project management tool.
Copyright (C) 2004-2011 GanttProject Team

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
package net.sourceforge.ganttproject.task;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import net.sourceforge.ganttproject.GPLogger;
import net.sourceforge.ganttproject.GanttCalendar;
import net.sourceforge.ganttproject.GanttTaskRelationship;
import net.sourceforge.ganttproject.calendar.AlwaysWorkingTimeCalendarImpl;
import net.sourceforge.ganttproject.calendar.GPCalendar;
import net.sourceforge.ganttproject.document.AbstractURLDocument;
import net.sourceforge.ganttproject.document.Document;
import net.sourceforge.ganttproject.shape.ShapePaint;
import net.sourceforge.ganttproject.task.algorithm.AlgorithmCollection;
import net.sourceforge.ganttproject.task.dependency.TaskDependencyException;
import net.sourceforge.ganttproject.task.dependency.TaskDependencySlice;
import net.sourceforge.ganttproject.task.dependency.TaskDependencySliceAsDependant;
import net.sourceforge.ganttproject.task.dependency.TaskDependencySliceAsDependee;
import net.sourceforge.ganttproject.task.dependency.TaskDependencySliceImpl;
import net.sourceforge.ganttproject.task.hierarchy.TaskHierarchyItem;

/**
 * @author bard
 */
public class TaskImpl implements Task {
  private final int myID;

  private final TaskManagerImpl myManager;

  private String myName;

  private String myWebLink = "";

  private boolean isMilestone;

  boolean isProjectTask;

  private Priority myPriority;

  private GanttCalendar myStart;

  private GanttCalendar myEnd;

  private GanttCalendar myThird;

  private int myThirdDateConstraint;

  private int myCompletionPercentage;

  private TaskLength myLength;

  private final List<TaskActivity> myActivities = new ArrayList<TaskActivity>();

  private boolean bExpand;

  // private final TaskDependencyCollection myDependencies = new
  // TaskDependencyCollectionImpl();
  private final ResourceAssignmentCollectionImpl myAssignments;

  private final TaskDependencySlice myDependencySlice;

  private final TaskDependencySlice myDependencySliceAsDependant;

  private final TaskDependencySlice myDependencySliceAsDependee;

  private boolean myEventsEnabled;

  private final TaskHierarchyItem myTaskHierarchyItem;

  private ShapePaint myShape;

  private Color myColor;

  private String myNotes;

  private MutatorImpl myMutator;

  private final CustomColumnsValues customValues;

  private boolean critical;

  public final static int NONE = 0;

  public final static int EARLIESTBEGIN = 1;

  private static final GPCalendar RESTLESS_CALENDAR = new AlwaysWorkingTimeCalendarImpl();

  protected TaskImpl(TaskManagerImpl taskManager, int taskID) {
    myManager = taskManager;
    myID = taskID;

    myAssignments = new ResourceAssignmentCollectionImpl(this, myManager.getConfig().getResourceManager());
    myDependencySlice = new TaskDependencySliceImpl(this, myManager.getDependencyCollection());
    myDependencySliceAsDependant = new TaskDependencySliceAsDependant(this, myManager.getDependencyCollection());
    myDependencySliceAsDependee = new TaskDependencySliceAsDependee(this, myManager.getDependencyCollection());
    myPriority = DEFAULT_PRIORITY;
    myTaskHierarchyItem = myManager.getHierarchyManager().createItem(this);
    myNotes = "";
    bExpand = true;
    myColor = null;

    customValues = new CustomColumnsValues(myManager.getCustomPropertyManager());
  }

  protected TaskImpl(TaskImpl copy, boolean isUnplugged) {
    myManager = copy.myManager;
    // Use a new (unique) ID for the cloned task
    myID = myManager.getAndIncrementId();

    if (!isUnplugged) {
      myTaskHierarchyItem = myManager.getHierarchyManager().createItem(this);
    } else {
      myTaskHierarchyItem = null;
    }
    myAssignments = new ResourceAssignmentCollectionImpl(this, myManager.getConfig().getResourceManager());
    myAssignments.importData(copy.getAssignmentCollection());
    myName = copy.myName;
    myWebLink = copy.myWebLink;
    isMilestone = copy.isMilestone;
    isProjectTask = copy.isProjectTask;
    myPriority = copy.myPriority;
    myStart = copy.myStart;
    myEnd = copy.myEnd;
    myThird = copy.myThird;
    myThirdDateConstraint = copy.myThirdDateConstraint;
    myCompletionPercentage = copy.myCompletionPercentage;
    myLength = copy.myLength;
    myShape = copy.myShape;
    myColor = copy.myColor;
    myNotes = copy.myNotes;
    bExpand = copy.bExpand;

    myDependencySlice = new TaskDependencySliceImpl(this, myManager.getDependencyCollection());
    myDependencySliceAsDependant = new TaskDependencySliceAsDependant(this, myManager.getDependencyCollection());
    myDependencySliceAsDependee = new TaskDependencySliceAsDependee(this, myManager.getDependencyCollection());

    customValues = (CustomColumnsValues) copy.getCustomValues().clone();

    recalculateActivities();
  }

  @Override
  public Task unpluggedClone() {
    TaskImpl result = new TaskImpl(this, true) {
      @Override
      public boolean isSupertask() {
        return false;
      }
    };
    return result;
  }

  class MutatorException extends RuntimeException {
    public MutatorException(String msg) {
      super(msg);
    }
  }

  @Override
  public TaskMutator createMutator() {
    if (myMutator != null) {
      throw new MutatorException("Two mutators have been requested for task=" + getName());
    }
    myMutator = new MutatorImpl();
    return myMutator;
  }

  @Override
  public TaskMutator createMutatorFixingDuration() {
    if (myMutator != null) {
      throw new MutatorException("Two mutators have been requested for task=" + getName());
    }
    myMutator = new MutatorImpl() {
      @Override
      public void setStart(GanttCalendar start) {
        super.setStart(start);
        TaskImpl.this.myEnd = null;
      }
    };
    return myMutator;
  }

  // main properties
  @Override
  public int getTaskID() {
    return myID;
  }

  @Override
  public String getName() {
    return myName;
  }

  public String getWebLink() {
    return myWebLink;
  }

  @Override
  public List<Document> getAttachments() {
    if (getWebLink() != null && !"".equals(getWebLink())) {
      return Collections.singletonList((Document) new AbstractURLDocument() {
        @Override
        public boolean canRead() {
          return true;
        }

        @Override
        public IStatus canWrite() {
          return Status.CANCEL_STATUS;
        }

        @Override
        public String getFileName() {
          return null;
        }

        @Override
        public InputStream getInputStream() throws IOException {
          return null;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
          return null;
        }

        @Override
        public String getPath() {
          return null;
        }

        @Override
        public URI getURI() {
          try {
            return new URI(new URL(getWebLink()).toString());
          } catch (URISyntaxException e) {
            // Do nothing
          } catch (MalformedURLException e) {
            File f = new File(getWebLink());
            if (f.exists()) {
              return f.toURI();
            }
          }
          try {
            URL context = myManager.getProjectDocument();
            if (context == null) {
              return null;
            }
            URL relative = new URL(context, getWebLink());
            return new URI(URLEncoder.encode(relative.toString(), "utf-8"));
          } catch (URISyntaxException e) {
            // Do nothing
          } catch (MalformedURLException e) {
            // Do nothing
          } catch (UnsupportedEncodingException e) {
            // Do nothing
          }
          return null;
        }

        @Override
        public boolean isLocal() {
          return false;
        }

        @Override
        public boolean isValidForMRU() {
          return false;
        }

        @Override
        public void write() throws IOException {
        }
      });
    }
    return Collections.emptyList();
  }

  @Override
  public boolean isMilestone() {
    return isMilestone;
  }

  @Override
  public Priority getPriority() {
    return myPriority;
  }

  @Override
  public GanttCalendar getStart() {
    if (myMutator != null && myMutator.myIsolationLevel == TaskMutator.READ_UNCOMMITED) {
      return myMutator.getStart();
    }
    return myStart;
  }

  @Override
  public GanttCalendar getEnd() {
    GanttCalendar result = null;
    if (myMutator != null && myMutator.myIsolationLevel == TaskMutator.READ_UNCOMMITED) {
      result = myMutator.getEnd();
    }
    if (result == null) {
      if (myEnd == null) {
        myEnd = calculateEnd();
      }
      result = myEnd;
    }
    return result;
  }

  GanttCalendar calculateEnd() {
    GanttCalendar result = getStart().clone();
    Date newEnd = shiftDate(result.getTime(), getDuration());
    result.setTime(newEnd);
    return result;
  }

  @Override
  public GanttCalendar getThird() {
    if (myMutator != null && myMutator.myIsolationLevel == TaskMutator.READ_UNCOMMITED) {
      return myMutator.getThird();
    }
    return myThird;
  }

  @Override
  public int getThirdDateConstraint() {
    return myThirdDateConstraint;
  }

  @Override
  public TaskActivity[] getActivities() {
    List<TaskActivity> activities = myMutator == null ? null : myMutator.getActivities();
    if (activities == null) {
      activities = myActivities;
    }
    return activities.toArray(new TaskActivity[activities.size()]);
  }

  @Override
  public TaskLength getDuration() {
    return (myMutator != null && myMutator.myIsolationLevel == TaskMutator.READ_UNCOMMITED) ? myMutator.getDuration()
        : myLength;
  }

  @Override
  public int getCompletionPercentage() {
    return (myMutator != null && myMutator.myIsolationLevel == TaskMutator.READ_UNCOMMITED) ? myMutator.getCompletionPercentage()
        : myCompletionPercentage;
  }

  @Override
  public boolean getExpand() {
    return bExpand;
  }

  @Override
  public ShapePaint getShape() {
    return myShape;
  }

  @Override
  public Color getColor() {
    Color result = myColor;
    if (result == null) {
      if (isMilestone() || getNestedTasks().length > 0) {
        result = Color.BLACK;
      } else {
        result = myManager.getConfig().getDefaultColor();
      }
    }
    return result;
  }

  @Override
  public String getNotes() {
    return myNotes;
  }

  @Override
  public GanttTaskRelationship[] getPredecessors() {
    return new GanttTaskRelationship[0];
  }

  @Override
  public GanttTaskRelationship[] getSuccessors() {
    return new GanttTaskRelationship[0];
  }

  @Override
  public ResourceAssignment[] getAssignments() {
    return myAssignments.getAssignments();
  }

  @Override
  public ResourceAssignmentCollection getAssignmentCollection() {
    return myAssignments;
  }

  @Override
  public Task getSupertask() {
    TaskHierarchyItem container = myTaskHierarchyItem.getContainerItem();
    return container.getTask();
  }

  @Override
  public Task[] getNestedTasks() {
    TaskHierarchyItem[] nestedItems = myTaskHierarchyItem.getNestedItems();
    Task[] result = new Task[nestedItems.length];
    for (int i = 0; i < nestedItems.length; i++) {
      result[i] = nestedItems[i].getTask();
    }
    return result;
  }

  @Override
  public void move(Task targetSupertask) {
    TaskImpl supertaskImpl = (TaskImpl) targetSupertask;
    TaskHierarchyItem targetItem = supertaskImpl.myTaskHierarchyItem;
    myTaskHierarchyItem.delete();
    targetItem.addNestedItem(myTaskHierarchyItem);
    myManager.onTaskMoved(this);
  }

  @Override
  public void delete() {
    getDependencies().clear();
    getAssignmentCollection().clear();
  }

  @Override
  public TaskDependencySlice getDependencies() {
    return myDependencySlice;
  }

  @Override
  public TaskDependencySlice getDependenciesAsDependant() {
    return myDependencySliceAsDependant;
  }

  @Override
  public TaskDependencySlice getDependenciesAsDependee() {
    return myDependencySliceAsDependee;
  }

  @Override
  public TaskManager getManager() {
    return myManager;
  }

  private static interface EventSender {
    void enable();

    void fireEvent();
  }

  private class ProgressEventSender implements EventSender {
    private boolean myEnabled;

    @Override
    public void fireEvent() {
      if (myEnabled) {
        myManager.fireTaskProgressChanged(TaskImpl.this);
      }
      myEnabled = false;
    }

    @Override
    public void enable() {
      myEnabled = true;
    }
  }

  private class PropertiesEventSender implements EventSender {
    private boolean myEnabled;

    @Override
    public void fireEvent() {
      if (myEnabled) {
        myManager.fireTaskPropertiesChanged(TaskImpl.this);
      }
      myEnabled = false;
    }

    @Override
    public void enable() {
      myEnabled = true;
    }
  }

  private static class FieldChange {
    Object myFieldValue;
    Object myOldValue;

    EventSender myEventSender;

    void setValue(Object newValue) {
      myFieldValue = newValue;
      myEventSender.enable();
    }

    public void setOldValue(Object oldValue) {
      myOldValue = oldValue;
    }
  }

  private class MutatorImpl implements TaskMutator {
    private EventSender myPropertiesEventSender = new PropertiesEventSender();

    private EventSender myProgressEventSender = new ProgressEventSender();

    private FieldChange myCompletionPercentageChange;

    private FieldChange myStartChange;

    private FieldChange myEndChange;

    private FieldChange myThirdChange;

    private FieldChange myDurationChange;

    private List<TaskActivity> myActivities;

    private final List<Runnable> myCommands = new ArrayList<Runnable>();

    private int myIsolationLevel;

    @Override
    public void commit() {
      try {
        if (myStartChange != null) {
          GanttCalendar start = getStart();
          TaskImpl.this.setStart(start);
        }
        if (myDurationChange != null) {
          TaskLength duration = getDuration();
          TaskImpl.this.setDuration(duration);
          myEndChange = null;
        }
        if (myCompletionPercentageChange != null) {
          int newValue = getCompletionPercentage();
          TaskImpl.this.setCompletionPercentage(newValue);
        }
        if (myEndChange != null) {
          GanttCalendar end = getEnd();
          if (end.getTime().compareTo(TaskImpl.this.getStart().getTime()) > 0) {
            TaskImpl.this.setEnd(end);
          }
        }
        if (myThirdChange != null) {
          GanttCalendar third = getThird();
          TaskImpl.this.setThirdDate(third);
        }
        for (Runnable command : myCommands) {
          command.run();
        }
        myCommands.clear();
        myPropertiesEventSender.fireEvent();
        myProgressEventSender.fireEvent();
      } finally {
        TaskImpl.this.myMutator = null;
      }
      if (myStartChange != null && TaskImpl.this.isSupertask()) {
        TaskImpl.this.adjustNestedTasks();
      }
      if ((myStartChange != null || myEndChange != null || myDurationChange != null) && areEventsEnabled()) {
        GanttCalendar oldStart = (GanttCalendar) (myStartChange == null ? TaskImpl.this.getStart()
            : myStartChange.myOldValue);
        GanttCalendar oldEnd = (GanttCalendar) (myEndChange == null ? TaskImpl.this.getEnd() : myEndChange.myOldValue);
        myManager.fireTaskScheduleChanged(TaskImpl.this, oldStart, oldEnd);
      }
    }

    public GanttCalendar getThird() {
      return myThirdChange == null ? TaskImpl.this.myThird : (GanttCalendar) myThirdChange.myFieldValue;
    }

    public List<TaskActivity> getActivities() {
      if (myActivities == null && (myStartChange != null) || (myDurationChange != null)) {
        myActivities = new ArrayList<TaskActivity>();
        TaskImpl.recalculateActivities(myManager.getConfig().getCalendar(), TaskImpl.this, myActivities,
            getStart().getTime(), TaskImpl.this.getEnd().getTime());
      }
      return myActivities;
    }

    @Override
    public void setName(final String name) {
      myCommands.add(new Runnable() {
        @Override
        public void run() {
          TaskImpl.this.setName(name);
        }
      });
    }

    @Override
    public void setProjectTask(final boolean projectTask) {
      myCommands.add(new Runnable() {
        @Override
        public void run() {
          TaskImpl.this.setProjectTask(projectTask);
        }
      });
    }

    @Override
    public void setMilestone(final boolean milestone) {
      myCommands.add(new Runnable() {
        @Override
        public void run() {
          TaskImpl.this.setMilestone(milestone);
        }
      });
    }

    @Override
    public void setPriority(final Priority priority) {
      myCommands.add(new Runnable() {
        @Override
        public void run() {
          TaskImpl.this.setPriority(priority);
        }
      });
    }

    @Override
    public void setStart(final GanttCalendar start) {
      assert start != null;
      GanttCalendar currentStart = getStart();
      if (currentStart != null && start.equals(currentStart)) {
        return;
      }
      if (myStartChange == null) {
        myStartChange = new FieldChange();
        myStartChange.myEventSender = myPropertiesEventSender;
      }
      myStartChange.setOldValue(TaskImpl.this.myStart);
      myStartChange.setValue(start);
      myActivities = null;
    }

    @Override
    public void setEnd(final GanttCalendar end) {
      if (myEndChange == null) {
        myEndChange = new FieldChange();
        myEndChange.myEventSender = myPropertiesEventSender;
      }
      myEndChange.setOldValue(TaskImpl.this.myEnd);
      myEndChange.setValue(end);
      myActivities = null;
    }

    @Override
    public void setThird(final GanttCalendar third, final int thirdDateConstraint) {
      myCommands.add(new Runnable() {
        @Override
        public void run() {
          TaskImpl.this.setThirdDateConstraint(thirdDateConstraint);
        }
      });
      if (myThirdChange == null) {
        myThirdChange = new FieldChange();
        myThirdChange.myEventSender = myPropertiesEventSender;
      }
      myThirdChange.setValue(third);
      myActivities = null;
    }

    @Override
    public void setDuration(final TaskLength length) {
      // If duration of task was set to 0 or less do not change it
      if (length.getLength() <= 0) {
        return;
      }

      if (myDurationChange == null) {
        myDurationChange = new FieldChange();
        myDurationChange.myEventSender = myPropertiesEventSender;
        myDurationChange.setValue(length);
      } else {
        TaskLength currentLength = (TaskLength) myDurationChange.myFieldValue;
        if (currentLength.getLength() - length.getLength() == 0) {
          return;
        }
      }

      myDurationChange.setValue(length);
      Date shifted = TaskImpl.this.shiftDate(getStart().getTime(), length);
      GanttCalendar newEnd = new GanttCalendar(shifted);
      setEnd(newEnd);
      myActivities = null;
    }

    @Override
    public void setExpand(final boolean expand) {
      myCommands.add(new Runnable() {
        @Override
        public void run() {
          TaskImpl.this.setExpand(expand);
        }
      });
    }

    @Override
    public void setCompletionPercentage(final int percentage) {
      if (myCompletionPercentageChange == null) {
        myCompletionPercentageChange = new FieldChange();
        myCompletionPercentageChange.myEventSender = myProgressEventSender;
      }
      myCompletionPercentageChange.setValue(new Integer(percentage));
    }

    @Override
    public void setCritical(final boolean critical) {
      myCommands.add(new Runnable() {
        @Override
        public void run() {
          TaskImpl.this.setCritical(critical);
        }
      });
    }

    @Override
    public void setShape(final ShapePaint shape) {
      myCommands.add(new Runnable() {
        @Override
        public void run() {
          TaskImpl.this.setShape(shape);
        }
      });
    }

    @Override
    public void setColor(final Color color) {
      myCommands.add(new Runnable() {
        @Override
        public void run() {
          TaskImpl.this.setColor(color);
        }
      });
    }

    @Override
    public void setNotes(final String notes) {
      myCommands.add(new Runnable() {
        @Override
        public void run() {
          TaskImpl.this.setNotes(notes);
        }
      });
    }

    @Override
    public void addNotes(final String notes) {
      myCommands.add(new Runnable() {
        @Override
        public void run() {
          TaskImpl.this.addNotes(notes);
        }
      });
    }

    @Override
    public int getCompletionPercentage() {
      return myCompletionPercentageChange == null ? TaskImpl.this.myCompletionPercentage
          : ((Integer) myCompletionPercentageChange.myFieldValue).intValue();
    }

    GanttCalendar getStart() {
      return myStartChange == null ? TaskImpl.this.myStart : (GanttCalendar) myStartChange.myFieldValue;
    }

    GanttCalendar getEnd() {
      return myEndChange == null ? null : (GanttCalendar) myEndChange.myFieldValue;
    }

    TaskLength getDuration() {
      return myDurationChange == null ? TaskImpl.this.myLength : (TaskLength) myDurationChange.myFieldValue;
    }

    @Override
    public void shift(float unitCount) {
      Task result = getPrecomputedShift(unitCount);
      if (result == null) {
        result = TaskImpl.this.shift(unitCount);
        cachePrecomputedShift(result, unitCount);
      }

      setStart(result.getStart());
      setDuration(result.getDuration());
      setEnd(result.getEnd());
    }

    @Override
    public void shift(TaskLength shift) {
      TaskImpl.this.shift(shift);
    }

    @Override
    public void setIsolationLevel(int level) {
      myIsolationLevel = level;
    }

    private void cachePrecomputedShift(Task result, float unitCount) {
      // TODO Implement cache
    }

    private Task getPrecomputedShift(float unitCount) {
      // TODO Use cache to grab value
      return null;
    }

    @Override
    public void setTaskInfo(TaskInfo taskInfo) {
      myTaskInfo = taskInfo;
    }
  }

  @Override
  public void setName(String name) {
    myName = (name == null ? null : name.trim());
  }

  public void setWebLink(String webLink) {
    myWebLink = webLink;
  }

  @Override
  public void setMilestone(boolean milestone) {
    if (milestone) {
      setEnd(getStart().newAdd(Calendar.DATE, 1));
    }
    isMilestone = milestone;
  }

  @Override
  public void setPriority(Priority priority) {
    myPriority = priority;
  }

  @Override
  public void setStart(GanttCalendar start) {
    Date closestWorkingStart = myManager.findClosestWorkingTime(start.getTime());
    start.setTime(closestWorkingStart);
    myStart = start;
    recalculateActivities();
  }

  private void adjustNestedTasks() {
    assert myManager != null;
    try {
      AlgorithmCollection algorithmCollection = myManager.getAlgorithmCollection();
      if (algorithmCollection != null) {
        algorithmCollection.getAdjustTaskBoundsAlgorithm().adjustNestedTasks(this);
      }
    } catch (TaskDependencyException e) {
      if (!GPLogger.log(e)) {
        e.printStackTrace(System.err);
      }
    }
  }

  @Override
  public boolean isSupertask() {
    return myManager.getTaskHierarchy().hasNestedTasks(this);
  }

  @Override
  public void setEnd(GanttCalendar end) {
    myEnd = end;
    recalculateActivities();
  }

  @Override
  public void setThirdDate(GanttCalendar third) {
    myThird = third;
    // recalculateActivities();
  }

  @Override
  public void setThirdDateConstraint(int thirdDateConstraint) {
    myThirdDateConstraint = thirdDateConstraint;
    // recalculateActivities();
  }

  @Override
  public void shift(TaskLength shift) {
    float unitCount = shift.getLength(myLength.getTimeUnit());
    if (unitCount != 0f) {
      Task resultTask = shift(unitCount);
      GanttCalendar oldStart = myStart;
      GanttCalendar oldEnd = myEnd;
      myStart = resultTask.getStart();
      myLength = resultTask.getDuration();
      myEnd = resultTask.getEnd();
      if (areEventsEnabled()) {
        myManager.fireTaskScheduleChanged(this, oldStart, oldEnd);
      }
      recalculateActivities();
    }
  }

  public Task shift(float unitCount) {
    Task clone = unpluggedClone();
    if (unitCount != 0) {
      Date newStart;
      if (unitCount > 0) {
        TaskLength length = myManager.createLength(myLength.getTimeUnit(), unitCount);
        // clone.setDuration(length);
        newStart = RESTLESS_CALENDAR.shiftDate(myStart.getTime(), length);
      } else {
        newStart = shiftDate(clone.getStart().getTime(),
            getManager().createLength(clone.getDuration().getTimeUnit(), (long) unitCount));
      }
      clone.setStart(new GanttCalendar(newStart));
      clone.setDuration(myLength);
    }
    return clone;
  }

  @Override
  public void setDuration(TaskLength length) {
    assert length.getLength() > 0;

    myLength = length;
    myEnd = null;
    recalculateActivities();
  }

  private Date shiftDate(Date input, TaskLength duration) {
    return myManager.getConfig().getCalendar().shiftDate(input, duration);
  }

  @Override
  public TaskLength translateDuration(TaskLength duration) {
    return myManager.createLength(myLength.getTimeUnit(), translateDurationValue(duration));
  }

  private float translateDurationValue(TaskLength duration) {
    if (myLength.getTimeUnit().equals(duration.getTimeUnit())) {
      return duration.getValue();
    }
    if (myLength.getTimeUnit().isConstructedFrom(duration.getTimeUnit())) {
      return duration.getValue() / myLength.getTimeUnit().getAtomCount(duration.getTimeUnit());
    }
    if (duration.getTimeUnit().isConstructedFrom(myLength.getTimeUnit())) {
      return duration.getValue() * duration.getTimeUnit().getAtomCount(myLength.getTimeUnit());
    }
    throw new RuntimeException("Can't translate duration=" + duration + " into units=" + myLength.getTimeUnit());
  }

  private void recalculateActivities() {
    if (myLength == null || myManager == null) {
      return;
    }
    recalculateActivities(myManager.getConfig().getCalendar(), this, myActivities, myStart.getTime(),
        getEnd().getTime());
    int length = 0;
    for (TaskActivity activity : myActivities) {
      if (activity.getIntensity() > 0) {
        length += activity.getDuration().getLength(getDuration().getTimeUnit());
      }
    }
    myLength = getManager().createLength(myLength.getTimeUnit(), length);
  }

  private static void recalculateActivities(GPCalendar calendar, Task task, List<TaskActivity> output, Date startDate,
      Date endDate) {
    TaskActivitiesAlgorithm alg = new TaskActivitiesAlgorithm(calendar);
    alg.recalculateActivities(task, output, startDate, endDate);
  }

  @Override
  public void setCompletionPercentage(int percentage) {
    if (percentage != myCompletionPercentage) {
      myCompletionPercentage = percentage;
      EventSender progressEventSender = new ProgressEventSender();
      progressEventSender.enable();
      progressEventSender.fireEvent();
    }
  }

  @Override
  public void setShape(ShapePaint shape) {
    myShape = shape;
  }

  @Override
  public void setColor(Color color) {
    myColor = color;
  }

  @Override
  public void setNotes(String notes) {
    myNotes = notes;
  }

  @Override
  public void setExpand(boolean expand) {
    bExpand = expand;
  }

  @Override
  public void addNotes(String notes) {
    myNotes += notes;
  }

  protected void enableEvents(boolean enabled) {
    myEventsEnabled = enabled;
  }

  protected boolean areEventsEnabled() {
    return myEventsEnabled;
  }

  /**
   * Determines whether a special shape is defined for this task.
   * 
   * @return true, if this task has its own shape defined.
   */
  public boolean shapeDefined() {
    return (myShape != null);
  }

  /**
   * Determines whether a special color is defined for this task.
   * 
   * @return true, if this task has its own color defined.
   */
  public boolean colorDefined() {
    return (myColor != null);
  }

  @Override
  public String toString() {
    return getName();
  }

  public boolean isUnplugged() {
    return myTaskHierarchyItem == null;
  }

  /** @return The CustomColumnValues. */
  @Override
  public CustomColumnsValues getCustomValues() {
    return customValues;
  }

  @Override
  public void setCritical(boolean critical) {
    this.critical = critical;
  }

  @Override
  public boolean isCritical() {
    return this.critical;
  }

  // TODO: implementation of this method has no correlation with algorithms
  // recalculating schedules,
  // doesn't affect subtasks and supertasks. It is necessary to call this
  // method explicitly from other
  // parts of code to be sure that constraint fulfills
  //
  // Method GanttCalendar.newAdd() assumes that time unit is day
  @Override
  public void applyThirdDateConstraint() {
    TaskLength length = getDuration();
    if (getThird() != null)
      switch (getThirdDateConstraint()) {
      case EARLIESTBEGIN:
        // TODO: TIME UNIT (assumption about days)
        if (getThird().after(getStart())) {
          int difference = getThird().diff(getStart());
          GanttCalendar _start = getStart().newAdd(Calendar.DATE, difference);
          GanttCalendar _end = getEnd().newAdd(Calendar.DATE, difference);
          setEnd(_end);
          setStart(_start);
          setDuration(length);
        }
        break;
      }
  }

  private TaskInfo myTaskInfo;

  @Override
  public TaskInfo getTaskInfo() {
    return myTaskInfo;
  }

  @Override
  public void setTaskInfo(TaskInfo taskInfo) {
    myTaskInfo = taskInfo;
  }

  public boolean isProjectTask() {
    return isProjectTask;
  }

  public void setProjectTask(boolean projectTask) {
    isProjectTask = projectTask;
  }
}
