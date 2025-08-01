/*
 * Copyright (C) Photon Vision.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.photonvision.vision.processes;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.photonvision.common.configuration.CameraConfiguration;
import org.photonvision.common.configuration.ConfigManager;
import org.photonvision.common.dataflow.DataChangeService;
import org.photonvision.common.dataflow.events.OutgoingUIEvent;
import org.photonvision.common.dataflow.websocket.UIPhotonConfiguration;
import org.photonvision.common.logging.LogGroup;
import org.photonvision.common.logging.Logger;
import org.photonvision.vision.pipeline.*;

@SuppressWarnings({"rawtypes", "unused"})
public class PipelineManager {
  private static final Logger logger = new Logger(PipelineManager.class, LogGroup.VisionModule);

  public static final int DRIVERMODE_INDEX = -1;
  public static final int CAL_3D_INDEX = -2;

  protected final List<CVPipelineSettings> userPipelineSettings;
  protected final Calibrate3dPipeline calibration3dPipeline;
  protected final DriverModePipeline driverModePipeline = new DriverModePipeline();

  /** Index of the currently active pipeline. Defaults to 0. */
  private int currentPipelineIndex = DRIVERMODE_INDEX;

  /** The currently active pipeline. */
  private CVPipeline currentUserPipeline = driverModePipeline;

  /**
   * Index of the last active user-created pipeline. <br>
   * <br>
   * Used only when switching from any of the built-in pipelines back to a user-created pipeline.
   */
  private int lastUserPipelineIdx;

  /**
   * Creates a PipelineManager with a DriverModePipeline, a Calibration3dPipeline, and all provided
   * pipelines.
   */
  PipelineManager(
      DriverModePipelineSettings driverSettings,
      List<CVPipelineSettings> userPipelines,
      int defaultIndex) {
    this.userPipelineSettings = new ArrayList<>(userPipelines);
    // This is to respect the default res idx for vendor cameras

    this.driverModePipeline.setSettings(driverSettings);

    if (userPipelines.isEmpty()) addPipeline(PipelineType.AprilTag);

    calibration3dPipeline = new Calibrate3dPipeline();

    // We know that at this stage, VisionRunner hasn't yet started so we're good to
    // do this from
    // this thread
    this.setIndex(defaultIndex);
    updatePipelineFromRequested();
  }

  public PipelineManager(CameraConfiguration config) {
    this(config.driveModeSettings, config.pipelineSettings, config.currentPipelineIndex);
  }

  /**
   * Get the settings for a pipeline by index.
   *
   * @param index Index of pipeline whose settings need getting.
   * @return The gotten settings of the pipeline whose index was provided.
   */
  public CVPipelineSettings getPipelineSettings(int index) {
    return switch (index) {
      case DRIVERMODE_INDEX -> driverModePipeline.getSettings();
      case CAL_3D_INDEX -> calibration3dPipeline.getSettings();
      default -> {
        for (var setting : userPipelineSettings) {
          if (setting.pipelineIndex == index) yield setting;
        }
        yield null;
      }
    };
  }

  /**
   * Get the settings for a pipeline by index.
   *
   * @param index Index of pipeline whose nickname needs getting.
   * @return the nickname of the pipeline whose index was provided.
   */
  public String getPipelineNickname(int index) {
    return switch (index) {
      case DRIVERMODE_INDEX -> driverModePipeline.getSettings().pipelineNickname;
      case CAL_3D_INDEX -> calibration3dPipeline.getSettings().pipelineNickname;
      default -> {
        for (var setting : userPipelineSettings) {
          if (setting.pipelineIndex == index) yield setting.pipelineNickname;
        }
        yield null;
      }
    };
  }

  /**
   * Gets a list of nicknames for all user pipelines
   *
   * @return The list of nicknames for all user pipelines
   */
  public List<String> getPipelineNicknames() {
    List<String> ret = new ArrayList<>();
    for (var p : userPipelineSettings) {
      ret.add(p.pipelineNickname);
    }
    return ret;
  }

  /**
   * Gets the index of the currently active pipeline
   *
   * @return The index of the currently active pipeline
   */
  public int getCurrentPipelineIndex() {
    return currentPipelineIndex;
  }

  /**
   * Get the currently active pipeline.
   *
   * @return The currently active pipeline.
   */
  public CVPipeline getCurrentPipeline() {
    updatePipelineFromRequested();
    return switch (currentPipelineIndex) {
      case CAL_3D_INDEX -> calibration3dPipeline;
      case DRIVERMODE_INDEX -> driverModePipeline;
        // Just return the current user pipeline, we're not on a built-in one
      default -> currentUserPipeline;
    };
  }

  /**
   * Get the currently active pipelines settings
   *
   * @return The currently active pipelines settings
   */
  public CVPipelineSettings getCurrentPipelineSettings() {
    return getPipelineSettings(currentPipelineIndex);
  }

  private volatile int requestedIndex = 0;

  /**
   * Grab the currently requested pipeline index. The VisionRunner may not have changed over to this
   * pipeline yet.
   */
  public int getRequestedIndex() {
    return requestedIndex;
  }

  /**
   * Internal method for setting the active pipeline. <br>
   * <br>
   * All externally accessible methods that intend to change the active pipeline MUST go through
   * here to ensure all proper steps are taken.
   *
   * @param newIndex Index of pipeline to be active
   */
  private void setPipelineInternal(int newIndex) {
    requestedIndex = newIndex;
  }

  /**
   * Based on a requested pipeline index, create/destroy pipelines as necessary. We do this as a
   * side effect of the main thread that calls getCurrentPipeline to avoid race conditions between
   * server threads and the VisionRunner TODO: this should be refactored. Shame Java doesn't have
   * RAII
   */
  private void updatePipelineFromRequested() {
    int newIndex = requestedIndex;
    if (newIndex == currentPipelineIndex) {
      // nothing to do, probably no change -- give up
      return;
    }

    if (newIndex < 0 && currentPipelineIndex >= 0) {
      // Transitioning to a built-in pipe, save off the current user one
      lastUserPipelineIdx = currentPipelineIndex;
    }

    if (userPipelineSettings.size() - 1 < newIndex) {
      logger.warn("User attempted to set index to non-existent pipeline!");
      return;
    }

    currentPipelineIndex = newIndex;

    if (newIndex >= 0) {
      recreateUserPipeline();
    }

    DataChangeService.getInstance()
        .publishEvent(
            new OutgoingUIEvent<>(
                "fullsettings",
                UIPhotonConfiguration.programStateToUi(ConfigManager.getInstance().getConfig())));
  }

  /**
   * Recreate the current user pipeline with the current pipeline index. Useful to force a
   * recreation after changing pipeline type
   */
  private void recreateUserPipeline() {
    // Cleanup potential old native resources before swapping over from a user
    // pipeline
    if (currentUserPipeline != null && !(currentPipelineIndex < 0)) {
      currentUserPipeline.release();
    }

    var desiredPipelineSettings = userPipelineSettings.get(currentPipelineIndex);
    switch (desiredPipelineSettings.pipelineType) {
      case Reflective -> {
        logger.debug("Creating Reflective pipeline");
        currentUserPipeline =
            new ReflectivePipeline((ReflectivePipelineSettings) desiredPipelineSettings);
      }
      case ColoredShape -> {
        logger.debug("Creating ColoredShape pipeline");
        currentUserPipeline =
            new ColoredShapePipeline((ColoredShapePipelineSettings) desiredPipelineSettings);
      }
      case AprilTag -> {
        logger.debug("Creating AprilTag pipeline");
        currentUserPipeline =
            new AprilTagPipeline((AprilTagPipelineSettings) desiredPipelineSettings);
      }
      case AprilTagCuda -> {
        logger.debug("Creating AprilTagCuda pipeline");
        currentUserPipeline =
            new AprilTagCudaPipeline((AprilTagCudaPipelineSettings) desiredPipelineSettings);
      }
      case Aruco -> {
        logger.debug("Creating Aruco Pipeline");
        currentUserPipeline = new ArucoPipeline((ArucoPipelineSettings) desiredPipelineSettings);
      }
      case ObjectDetection -> {
        logger.debug("Creating ObjectDetection Pipeline");
        currentUserPipeline =
            new ObjectDetectionPipeline((ObjectDetectionPipelineSettings) desiredPipelineSettings);
      }
      case Calib3d, DriverMode -> {}
    }
  }

  /**
   * Enters or exits calibration mode based on the parameter. <br>
   * <br>
   * Exiting returns to the last used user pipeline.
   *
   * @param wantsCalibration True to enter calibration mode, false to exit calibration mode.
   */
  public void setCalibrationMode(boolean wantsCalibration) {
    if (!wantsCalibration) calibration3dPipeline.finishCalibration();
    setPipelineInternal(wantsCalibration ? CAL_3D_INDEX : lastUserPipelineIdx);
  }

  /**
   * Enters or exits driver mode based on the parameter. <br>
   * <br>
   * Exiting returns to the last used user pipeline.
   *
   * @param state True to enter driver mode, false to exit driver mode.
   */
  public void setDriverMode(boolean state) {
    setPipelineInternal(state ? DRIVERMODE_INDEX : lastUserPipelineIdx);
  }

  /**
   * Returns whether driver mode is active.
   *
   * @return Whether driver mode is active.
   */
  public boolean getDriverMode() {
    return currentPipelineIndex == DRIVERMODE_INDEX;
  }

  public static final Comparator<CVPipelineSettings> PipelineSettingsIndexComparator =
      Comparator.comparingInt(o -> o.pipelineIndex);

  /**
   * Sorts the pipeline list by index, and reassigns their indexes to match the new order. <br>
   * <br>
   * I don't like this, but I have no other ideas, and it works so
   */
  private void reassignIndexes() {
    userPipelineSettings.sort(PipelineSettingsIndexComparator);
    for (int i = 0; i < userPipelineSettings.size(); i++) {
      userPipelineSettings.get(i).pipelineIndex = i;
    }
  }

  public CVPipelineSettings addPipeline(PipelineType type) {
    return addPipeline(type, "New Pipeline");
  }

  public CVPipelineSettings addPipeline(PipelineType type, String nickname) {
    var added = createSettingsForType(type, nickname);
    if (added == null) {
      logger.error("Cannot add null pipeline!");
      return null;
    }
    addPipelineInternal(added);
    reassignIndexes();
    return added;
  }

  private CVPipelineSettings createSettingsForType(PipelineType type, String nickname) {
    CVPipelineSettings settings =
        switch (type) {
          case Reflective -> new ReflectivePipelineSettings();
          case ColoredShape -> new ColoredShapePipelineSettings();
          case AprilTag -> new AprilTagPipelineSettings();
          case AprilTagCuda -> new AprilTagCudaPipelineSettings();
          case Aruco -> new ArucoPipelineSettings();
          case ObjectDetection -> new ObjectDetectionPipelineSettings();
          case Calib3d, DriverMode -> {
            logger.error("Got invalid pipeline type: " + type);
            yield null;
          }
        };
    if (settings != null) {
      settings.pipelineNickname = nickname;
    }
    return settings;
  }

  private void addPipelineInternal(CVPipelineSettings settings) {
    settings.pipelineIndex = userPipelineSettings.size();
    userPipelineSettings.add(settings);
    reassignIndexes();
  }

  /**
   * Remove a pipeline settings at the given index and return the new current index
   *
   * @param index The idx to remove
   */
  private int removePipelineInternal(int index) {
    userPipelineSettings.remove(index);
    currentPipelineIndex = Math.min(index, userPipelineSettings.size() - 1);
    reassignIndexes();
    return currentPipelineIndex;
  }

  public void setIndex(int index) {
    this.setPipelineInternal(index);
  }

  public int removePipeline(int index) {
    if (index < 0) {
      return currentPipelineIndex;
    }
    // TODO should we block/lock on a mutex?
    return removePipelineInternal(index);
  }

  public void renameCurrentPipeline(String newName) {
    getCurrentPipelineSettings().pipelineNickname = newName;
  }

  /**
   * Duplicate a pipeline at a given index
   *
   * @param index the index of the target pipeline
   * @return The new index
   */
  public int duplicatePipeline(int index) {
    var settings = userPipelineSettings.get(index);
    var newSettings = settings.clone();
    newSettings.pipelineNickname =
        createUniqueName(settings.pipelineNickname, userPipelineSettings);
    newSettings.pipelineIndex = Integer.MAX_VALUE;
    logger.debug("Duplicating pipe " + index + " to " + newSettings.pipelineNickname);
    userPipelineSettings.add(newSettings);
    reassignIndexes();

    // Now we look for the index of the new pipeline and return it
    return userPipelineSettings.indexOf(newSettings);
  }

  private static String createUniqueName(
      String nickname, List<CVPipelineSettings> existingSettings) {
    StringBuilder uniqueName = new StringBuilder(nickname);
    while (true) {
      String finalUniqueName = uniqueName.toString(); // To get around lambda capture
      var conflictingName =
          existingSettings.stream().anyMatch(it -> it.pipelineNickname.equals(finalUniqueName));

      if (!conflictingName) {
        // If no conflict, we're done
        return uniqueName.toString();
      } else {
        // Otherwise, we need to add a suffix to the name
        // If the string doesn't already end in "([0-9]*)", we'll add it
        // If it does, we'll increment the number in the suffix

        if (uniqueName.toString().matches(".*\\([0-9]*\\)")) {
          // Because java strings are immutable, we have to do this curstedness
          // This is like doing "New pipeline (" + 2 + ")"

          var parenStart = uniqueName.toString().lastIndexOf('(');
          var parenEnd = uniqueName.length() - 1;
          var number = Integer.parseInt(uniqueName.substring(parenStart + 1, parenEnd)) + 1;

          uniqueName = new StringBuilder(uniqueName.substring(0, parenStart + 1) + number + ")");
        } else {
          uniqueName.append(" (1)");
        }
      }
    }
  }

  private static List<Field> getAllFields(Class base) {
    List<Field> ret = new ArrayList<>();
    ret.addAll(List.of(base.getDeclaredFields()));
    var superclazz = base.getSuperclass();
    if (superclazz != null) {
      ret.addAll(getAllFields(superclazz));
    }

    return ret;
  }

  public void changePipelineType(int newType) {
    // Find the PipelineType proposed
    // To do this we look at all the PipelineType entries and look for one with
    // matching
    // base indexes
    PipelineType type =
        Arrays.stream(PipelineType.values())
            .filter(it -> it.baseIndex == newType)
            .findAny()
            .orElse(null);
    if (type == null) {
      logger.error("Could not match type " + newType + " to a PipelineType!");
      return;
    }

    if (type.baseIndex == getCurrentPipelineSettings().pipelineType.baseIndex) {
      logger.debug(
          "Not changing settings as "
              + type
              + " and "
              + getCurrentPipelineSettings().pipelineType
              + " are identical!");
      return;
    }

    var idx = currentPipelineIndex;
    if (idx < 0) {
      logger.error("Cannot replace non-user pipeline!");
      return;
    }

    // The settings we used to have
    var oldSettings = userPipelineSettings.get(idx);

    var name = getCurrentPipelineSettings().pipelineNickname;
    // Dummy settings to copy common fields over
    var newSettings = createSettingsForType(type, name);

    // Copy all fields from AdvancedPipelineSettings/its superclasses from old to
    // new
    try {
      for (Field field : getAllFields(AdvancedPipelineSettings.class)) {
        if (field.isAnnotationPresent(SuppressSettingCopy.class)) {
          // Skip fields that are annotated with SuppressSettingCopy
          continue;
        }
        Object value = field.get(oldSettings);
        logger.debug("setting " + field.getName() + " to " + value);
        field.set(newSettings, value);
      }
    } catch (Exception e) {
      logger.error("Couldn't copy old settings", e);
    }

    logger.info("Adding new pipe of type " + type + " at idx " + idx);

    userPipelineSettings.set(idx, newSettings);

    setPipelineInternal(idx);
    reassignIndexes();
    recreateUserPipeline();
  }
}
