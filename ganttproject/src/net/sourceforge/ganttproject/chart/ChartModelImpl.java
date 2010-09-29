/*
 * This code is provided under the terms of GPL version 2.
 * Please see LICENSE file for details
 * (C) Dmitry Barashev, GanttProject team, 2004-2008
 */
package net.sourceforge.ganttproject.chart;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.sourceforge.ganttproject.chart.GraphicPrimitiveContainer.Rectangle;
import net.sourceforge.ganttproject.chart.item.ChartItem;
import net.sourceforge.ganttproject.chart.item.TaskBoundaryChartItem;
import net.sourceforge.ganttproject.chart.item.TaskProgressChartItem;
import net.sourceforge.ganttproject.chart.item.TaskRegularAreaChartItem;
import net.sourceforge.ganttproject.gui.UIConfiguration;
import net.sourceforge.ganttproject.gui.options.OptionsPageBuilder;
import net.sourceforge.ganttproject.gui.options.model.ColorOption;
import net.sourceforge.ganttproject.gui.options.model.DefaultColorOption;
import net.sourceforge.ganttproject.gui.options.model.DefaultEnumerationOption;
import net.sourceforge.ganttproject.gui.options.model.EnumerationOption;
import net.sourceforge.ganttproject.gui.options.model.GP1XOptionConverter;
import net.sourceforge.ganttproject.gui.options.model.GPOption;
import net.sourceforge.ganttproject.gui.options.model.GPOptionGroup;
import net.sourceforge.ganttproject.task.Task;
import net.sourceforge.ganttproject.task.TaskActivity;
import net.sourceforge.ganttproject.task.TaskContainmentHierarchyFacade;
import net.sourceforge.ganttproject.task.TaskManager;
import net.sourceforge.ganttproject.time.TimeUnitStack;

/**
 * Controls painting of the Gantt chart
 */
public class ChartModelImpl extends ChartModelBase implements ChartModel {

    private java.util.List<Task> myVisibleTasks;

    private final TaskRendererImpl2 myTaskRendererImpl;

    private TaskContainmentHierarchyFacade myTaskContainment;

    private final TaskGridRendererImpl myTaskGridRendererImpl;

    //private final ResourcesRendererImpl myResourcesRendererImpl;

    // private final TaskProgressRendererImpl myTaskProgressRendererImpl;
    private TaskManager taskManager;

    private boolean isPreviousState = false;

    private int rowHeight = 20;

    private final EnumerationOption myDependencyHardnessOption;
    private final GPOptionGroup myDependencyOptions;

    private final ColorOption myTaskDefaultColorOption;

    private final ChartOptionGroup myDefaultColorOptions;

    private final ColorOption myTaskAheadOfScheduleColor;
    private final ColorOption myTaskBehindScheduleColor;
    private final ColorOption myTaskOnScheduleColor;
//
//
    private final ChartOptionGroup myStateDiffOptions;

    private Set myHiddenTasks;

    public static class TuningOptions {
        private final boolean renderProgress;

        private final boolean renderDependencies;

        public TuningOptions(boolean renderProgress, boolean renderDependencies) {
            this.renderProgress = renderProgress;
            this.renderDependencies = renderDependencies;
        }

        public static final TuningOptions DEFAULT = new TuningOptions(true,
                true);
    }

    public ChartModelImpl(TaskManager taskManager, TimeUnitStack timeUnitStack,
            final UIConfiguration projectConfig) {
        super(taskManager, timeUnitStack, projectConfig);
        this.taskManager = taskManager;
        myTaskRendererImpl = new TaskRendererImpl2(this);
        myTaskGridRendererImpl = new TaskGridRendererImpl(this);
        addRenderer(myTaskRendererImpl);
        //addRenderer(myTaskGridRendererImpl);
        //myResourcesRendererImpl = new ResourcesRendererImpl(this);
        // myTaskProgressRendererImpl = new TaskProgressRendererImpl(this);
        //myTimeUnitVisitors.add(myTaskGridRendererImpl);
        //myTimeUnitVisitors.add(myTaskRendererImpl);

        class NewTaskColorOption extends DefaultColorOption implements GP1XOptionConverter {
            private NewTaskColorOption() {
                super("newTaskDefaultColor");
            }
            public String getTagName() {
                return "colors";
            }

            public String getAttributeName() {
                return "tasks";
            }

            public void loadValue(String legacyValue) {
                lock();
                loadPersistentValue(legacyValue);
                commit();
            }
            public void commit() {
                super.commit();
                projectConfig.setTaskColor(getValue());
            }

        };
        myTaskDefaultColorOption = new NewTaskColorOption();
        myDependencyHardnessOption = new DefaultEnumerationOption("dependencyDefaultHardness", new String[] {
                "Strong", "Rubber"
             });
             myDependencyHardnessOption.lock();
             myDependencyHardnessOption.setValue("Strong");
             myDependencyHardnessOption.commit();
             myDependencyOptions = new GPOptionGroup("dependency", new GPOption[] {myDependencyHardnessOption});
             myDependencyOptions.setTitled(true);
             myDependencyOptions.setI18Nkey(
                     new OptionsPageBuilder.I18N().getCanonicalOptionGroupLabelKey(myDependencyOptions),
                     "link");
             myDependencyOptions.setI18Nkey(
                     new OptionsPageBuilder.I18N().getCanonicalOptionLabelKey(myDependencyHardnessOption),
                     "hardness");
             myDependencyOptions.setI18Nkey(
                     OptionsPageBuilder.I18N.getCanonicalOptionValueLabelKey("Strong"),
                     "hardness.strong");
             myDependencyOptions.setI18Nkey(
                     OptionsPageBuilder.I18N.getCanonicalOptionValueLabelKey("Rubber"),
                     "hardness.rubber");
        myDefaultColorOptions = new ChartOptionGroup("ganttChartDefaultColors", new GPOption[] {myTaskDefaultColorOption, projectConfig.getWeekendAlphaRenderingOption()}, getOptionEventDispatcher());
        {
            myTaskAheadOfScheduleColor = new DefaultColorOption(
                    "ganttChartStateDiffColors.taskAheadOfScheduleColor") {
                public void commit() {
                    super.commit();
                    projectConfig.setEarlierPreviousTaskColor(getValue());
                }
            };
            myTaskAheadOfScheduleColor.lock();
            myTaskAheadOfScheduleColor.setValue(new Color(50, 229, 50));
            myTaskAheadOfScheduleColor.commit();
            //
            myTaskBehindScheduleColor = new DefaultColorOption(
                    "ganttChartStateDiffColors.taskBehindScheduleColor") {
                public void commit() {
                    super.commit();
                    projectConfig.setLaterPreviousTaskColor(getValue());
                }
            };
            myTaskBehindScheduleColor.lock();
            myTaskBehindScheduleColor.setValue(new Color(229, 50, 50));
            myTaskBehindScheduleColor.commit();
            //
            myTaskOnScheduleColor = new DefaultColorOption(
                    "ganttChartStateDiffColors.taskOnScheduleColor") {
                public void commit() {
                    super.commit();
                    projectConfig.setPreviousTaskColor(getValue());
                }
            };
            myTaskOnScheduleColor.lock();
            myTaskOnScheduleColor.setValue(Color.LIGHT_GRAY);
            myTaskOnScheduleColor.commit();
            //
            myStateDiffOptions = new ChartOptionGroup(
                    "ganttChartStateDiffColors", new GPOption[] {
                            myTaskOnScheduleColor, myTaskAheadOfScheduleColor,
                            myTaskBehindScheduleColor },
                    getOptionEventDispatcher());
        }
        // myTimeUnitVisitors.add(myResourcesRendererImpl);
        // myTimeUnitVisitors.add(myTaskProgressRendererImpl);
    }

    public void setVisibleTasks(java.util.List/* <Task> */visibleTasks) {
        myVisibleTasks = visibleTasks;
    }

    public void setExplicitlyHiddenTasks(Set hiddenTasks) {
        myHiddenTasks = hiddenTasks;
    }

//    public Task findTaskWithCoordinates(int x, int y) {
//        y = y + getVerticalOffset();
//        GraphicPrimitiveContainer.GraphicPrimitive primitive = myTaskRendererImpl
//                .getPrimitiveContainer().getPrimitive(x,
//                        y - getChartUIConfiguration().getHeaderHeight());
//        if (primitive instanceof GraphicPrimitiveContainer.Rectangle) {
//            TaskActivity activity = (TaskActivity) primitive.getModelObject();
//            return activity == null ? null : activity.getTask();
//        }
//        return null;
//    }

    public ChartItem getChartItemWithCoordinates(int x, int y) {
        y = y + getVerticalOffset();
        ChartItem result = findTaskProgressItem(x, y);
        if (result == null) {
            result = findTaskBoundaryItem(x, y);
        }
        return result;
    }

    private ChartItem findTaskProgressItem(int x, int y) {
        ChartItem result = null;
        GraphicPrimitiveContainer.GraphicPrimitive primitive = myTaskRendererImpl
                .getPrimitiveContainer().getLayer(0).getPrimitive(x, 4,
                        y/* - getChartUIConfiguration().getHeaderHeight()*/, 0);
        if (primitive instanceof GraphicPrimitiveContainer.Rectangle) {
            GraphicPrimitiveContainer.Rectangle rect = (GraphicPrimitiveContainer.Rectangle) primitive;
            if ("task.progress.end".equals(primitive.getStyle())
                    && rect.getRightX() >= x - 4 && rect.getRightX() <= x + 4) {
                result = new TaskProgressChartItem(x, getBottomUnitWidth(),
                        getBottomUnit(), (Task) primitive.getModelObject());
            }
        }
        return result;
    }

    private ChartItem findTaskBoundaryItem(int x, int y) {
        ChartItem result = null;
        GraphicPrimitiveContainer.GraphicPrimitive primitive = myTaskRendererImpl
                .getPrimitiveContainer().getPrimitive(x, y);
                        //y - getChartUIConfiguration().getHeaderHeight());
        if (primitive==null) {
            primitive = myTaskRendererImpl.getPrimitiveContainer().getLayer(1).getPrimitive(x, y/*-getChartUIConfiguration().getHeaderHeight()*/);
        }
        if (primitive instanceof GraphicPrimitiveContainer.Rectangle) {
            GraphicPrimitiveContainer.Rectangle rect = (Rectangle) primitive;
            TaskActivity activity = (TaskActivity) primitive.getModelObject();
            if (activity != null) {
                if (activity.isFirst() && rect.myLeftX - 2 <= x
                        && rect.myLeftX + 2 >= x) {
                    result = new TaskBoundaryChartItem(activity.getTask(), true);
                }
                if (result == null && activity.isLast()
                        && rect.myLeftX + rect.myWidth - 2 <= x
                        && rect.myLeftX + rect.myWidth + 2 >= x) {
                    result = new TaskBoundaryChartItem(activity.getTask(),
                            false);
                }
                if (result == null) {
                    result = new TaskRegularAreaChartItem(activity.getTask());
                }
            }
        }
        return result;
    }

    public java.awt.Rectangle getBoundingRectangle(Task task) {
        java.awt.Rectangle result = null;
        TaskActivity[] activities = task.getActivities();
        for (int i = 0; i < activities.length; i++) {
            GraphicPrimitiveContainer.Rectangle nextRectangle = (GraphicPrimitiveContainer.Rectangle) myTaskRendererImpl
                    .getPrimitive(activities[i]);
            if (nextRectangle != null) {
                java.awt.Rectangle nextAwtRectangle = new java.awt.Rectangle(
                        nextRectangle.myLeftX, nextRectangle.myTopY,
                        nextRectangle.myWidth, nextRectangle.myHeight);
                if (result == null) {
                    result = nextAwtRectangle;
                } else {
                    result = result.union(nextAwtRectangle);
                }
            }
        }
        return result;
    }

    GraphicPrimitiveContainer.Rectangle[] getTaskActivityRectangles(Task task) {
        List result = new ArrayList();
        TaskActivity[] activities = task.getActivities();
        for (int i = 0; i < activities.length; i++) {
            GraphicPrimitiveContainer.Rectangle nextRectangle = (GraphicPrimitiveContainer.Rectangle) myTaskRendererImpl
                    .getPrimitive(activities[i]);
            if (nextRectangle!=null) {
                result.add(nextRectangle);
            }
        }
        return (Rectangle[]) result.toArray(new GraphicPrimitiveContainer.Rectangle[0]);
    }

    java.util.List<Task> getVisibleTasks() {
        return myVisibleTasks==null ? Collections.EMPTY_LIST : myVisibleTasks;
    }

    /*
     * (non-Javadoc)
     *
     * @see net.sourceforge.ganttproject.chart.ChartModel#setTaskContainment(net.sourceforge.ganttproject.task.TaskContainmentHierarchyFacade)
     */
    public void setTaskContainment(
            TaskContainmentHierarchyFacade taskContainment) {
        myTaskContainment = taskContainment;
    }

    TaskContainmentHierarchyFacade getTaskContainment() {
        return myTaskManager.getTaskHierarchy();
    }

    public int calculateRowHeight() {
        rowHeight = myTaskRendererImpl.calculateRowHeight();
        if (isPreviousState) {
            rowHeight = rowHeight + 8;
        }
        return rowHeight;
    }

    @Override
    protected int getRowCount() {
        return getTaskManager().getTaskCount();
    }

    public boolean isSelected(int index) {
        return myTaskRendererImpl.isVisible(index);
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public int getRowHeight() {
        return rowHeight;
        //return getChartUIConfiguration().getRowHeight();
    }

    public GPOptionGroup[] getChartOptionGroups() {
        GPOptionGroup[] superGroups = super.getChartOptionGroups();
        GPOptionGroup[] rendererGroups = myTaskRendererImpl.getOptionGroups();
        List result = new ArrayList();
        result.addAll(Arrays.asList(superGroups));
        result.addAll(Arrays.asList(rendererGroups));
        result.add(myDependencyOptions);
        result.add(myDefaultColorOptions);
        result.add(myStateDiffOptions);
        return (GPOptionGroup[]) result.toArray(new GPOptionGroup[result.size()]);
    }


    public int setPreviousStateTasks(ArrayList tasks) {
        if (tasks == null)
            isPreviousState = false;
        else
            isPreviousState = true;
        //myTaskRendererImpl.setPreviousStateTasks(tasks);
        return (calculateRowHeight());
    }

    public boolean isPrevious() {
        return isPreviousState;
    }

    public ChartModelBase createCopy() {
        ChartModelBase result = new ChartModelImpl(getTaskManager(), getTimeUnitStack(), getProjectConfig());
        super.setupCopy(result);
        result.setVisibleTasks(getVisibleTasks());
        return result;
    }

    public boolean isExplicitlyHidden(Task task) {
        return myHiddenTasks==null ? false : myHiddenTasks.contains(task);
    }

    public EnumerationOption getDependencyHardnessOption() {
        return myDependencyHardnessOption;
    }

}