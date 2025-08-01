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

import edu.wpi.first.cscore.VideoMode;
import java.util.HashMap;
import org.photonvision.common.configuration.CameraConfiguration;
import org.photonvision.common.logging.LogGroup;
import org.photonvision.common.logging.Logger;
import org.photonvision.vision.calibration.CameraCalibrationCoefficients;
import org.photonvision.vision.frame.FrameStaticProperties;

public abstract class VisionSourceSettables {
  protected Logger logger;

  private final CameraConfiguration configuration;

  protected VisionSourceSettables(CameraConfiguration configuration) {
    this.configuration = configuration;
    this.logger =
        new Logger(VisionSourceSettables.class, configuration.nickname, LogGroup.VisionModule);
  }

  protected FrameStaticProperties frameStaticProperties = null;
  protected HashMap<Integer, VideoMode> videoModes = new HashMap<>();

  public CameraConfiguration getConfiguration() {
    return configuration;
  }

  // If the device has been connected at least once, and we cached properties
  protected boolean cameraPropertiesCached = false;

  /**
   * Runs exactly once the first time that the underlying device goes from disconnected to connected
   */
  public void onCameraConnected() {
    cameraPropertiesCached = true;
  }

  public abstract void setExposureRaw(double exposureRaw);

  public abstract double getMinExposureRaw();

  public abstract double getMaxExposureRaw();

  public abstract void setAutoExposure(boolean cameraAutoExposure);

  public abstract void setWhiteBalanceTemp(double temp);

  public abstract void setAutoWhiteBalance(boolean autowb);

  public abstract void setBrightness(int brightness);

  public abstract void setGain(int gain);

  // Pretty uncommon so instead of abstract this is just a no-op by default
  // Overridden by cameras with AWB gain support
  public void setRedGain(int red) {}

  public void setBlueGain(int blue) {}

  public abstract VideoMode getCurrentVideoMode();

  public void setVideoModeInternal(int index) {
    if (!getAllVideoModes().isEmpty()) setVideoMode(getAllVideoModes().get(index));
  }

  public void setVideoMode(VideoMode mode) {
    logger.info(
        "Setting video mode to "
            + "FPS: "
            + mode.fps
            + " Width: "
            + mode.width
            + " Height: "
            + mode.height
            + " Pixel Format: "
            + mode.pixelFormat);
    setVideoModeInternal(mode);
    calculateFrameStaticProps();
  }

  protected abstract void setVideoModeInternal(VideoMode videoMode);

  @SuppressWarnings("unused")
  public void setVideoModeIndex(int index) {
    setVideoMode(videoModes.get(index));
  }

  public abstract HashMap<Integer, VideoMode> getAllVideoModes();

  public double getFOV() {
    return configuration.FOV;
  }

  public void setFOV(double fov) {
    logger.info("Setting FOV to " + fov);
    configuration.FOV = fov;
    calculateFrameStaticProps();
  }

  public void addCalibration(CameraCalibrationCoefficients calibrationCoefficients) {
    configuration.addCalibration(calibrationCoefficients);
    calculateFrameStaticProps();
  }

  protected void calculateFrameStaticProps() {
    var videoMode = getCurrentVideoMode();
    this.frameStaticProperties =
        new FrameStaticProperties(
            videoMode,
            getFOV(),
            configuration.calibrations.stream()
                .filter(
                    it ->
                        it.unrotatedImageSize.width == videoMode.width
                            && it.unrotatedImageSize.height == videoMode.height)
                .findFirst()
                .orElse(null));
  }

  public FrameStaticProperties getFrameStaticProperties() {
    return frameStaticProperties;
  }

  public abstract double getMinWhiteBalanceTemp();

  public abstract double getMaxWhiteBalanceTemp();
}
