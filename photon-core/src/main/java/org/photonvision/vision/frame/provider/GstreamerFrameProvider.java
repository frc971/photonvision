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

package org.photonvision.vision.frame.provider;

import java.util.concurrent.TimeUnit;
import jni.Gstreamer; // TODO (charlie) refactor?
import org.opencv.core.Mat;
import org.photonvision.common.util.math.MathUtils;
import org.photonvision.vision.camera.csi.GstreamerSettables;
import org.photonvision.vision.frame.Frame;
import org.photonvision.vision.frame.FrameProvider;
import org.photonvision.vision.frame.FrameThresholdType;
import org.photonvision.vision.opencv.CVMat;
import org.photonvision.vision.opencv.ImageRotationMode;
import org.photonvision.vision.pipe.impl.HSVPipe.HSVParams;

class releaseCapThread extends Thread {
  private long cap;

  public releaseCapThread(long cap) {
    this.cap = cap;
  }

  public void run() {
    Gstreamer.releaseCam(cap);
    System.out.println("Succesfully shutdown camera\n");
  }
}


public class GstreamerFrameProvider extends FrameProvider {
  private final GstreamerSettables settables;
  private long cap;
  private Mat readMat = new Mat();
  private long start;
  private long end;

  public GstreamerFrameProvider(GstreamerSettables visionSettables, String pipeline) {
    this.settables = visionSettables;
    var vidMode = settables.getCurrentVideoMode();
    settables.setVideoMode(vidMode);

    Runtime current = Runtime.getRuntime();

    cap = Gstreamer.initCam(pipeline);
    Gstreamer.releaseCam(cap);

    cap = Gstreamer.initCam(pipeline);
    current.addShutdownHook(new releaseCapThread(cap));

    onCameraConnected();
  }

  @Override
  public String getName() {
    return "GstreammerCamera";
  }

  int badFrameCounter = 0;

  @Override
  public Frame get() {
    start = MathUtils.wpiNanoTime();
    long pipeline_latency = (start - end);

    Mat raw = new Mat();
    Gstreamer.readMat(cap, raw.nativeObj);

    Mat processed = new Mat();
    Gstreamer.getGrayScale(raw.nativeObj, processed.nativeObj);

    CVMat frame = new CVMat(raw);
    CVMat gray = new CVMat(processed);

    end = MathUtils.wpiNanoTime();
    long latency = (end - start);
    // System.out.println("Camera latency " + latency / 1000_000);
    //
    // System.out.println("Pipeline latency " + pipeline_latency / 1000_000);

    ++sequenceID;

    return new Frame(
        sequenceID,
        frame,
        gray,
        FrameThresholdType.GREYSCALE,
        MathUtils.wpiNanoTime() - latency,
        settables.getFrameStaticProperties());
  }

  @Override
  public void requestFrameThresholdType(FrameThresholdType type) {}

  @Override
  public void requestFrameRotation(ImageRotationMode rotationMode) {}

  @Override
  public void requestHsvSettings(HSVParams params) {}

  @Override
  public void requestFrameCopies(boolean copyInput, boolean copyOutput) {}

  @Override
  public void release() {
    Gstreamer.releaseCam(cap);
  }

  @Override
  public boolean checkCameraConnected() {
    return true;
  }

  @Override
  public boolean isConnected() {
    return true;
  }
}
