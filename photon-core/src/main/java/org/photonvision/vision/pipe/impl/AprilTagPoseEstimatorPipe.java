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

package org.photonvision.vision.pipe.impl;

import edu.wpi.first.apriltag.AprilTagDetection;
import edu.wpi.first.apriltag.AprilTagPoseEstimate;
import edu.wpi.first.apriltag.AprilTagPoseEstimator;
import edu.wpi.first.apriltag.AprilTagPoseEstimator.Config;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.photonvision.vision.calibration.CameraCalibrationCoefficients;
import org.photonvision.vision.opencv.Releasable;
import org.photonvision.vision.pipe.CVPipe;

public class AprilTagPoseEstimatorPipe
    extends CVPipe<
        AprilTagDetection,
        AprilTagPoseEstimate,
        AprilTagPoseEstimatorPipe.AprilTagPoseEstimatorPipeParams>
    implements Releasable {
  private final AprilTagPoseEstimator m_poseEstimator =
      new AprilTagPoseEstimator(new AprilTagPoseEstimator.Config(0, 0, 0, 0, 0));

  public AprilTagPoseEstimatorPipe() {
    super();
  }

  MatOfPoint2f temp = new MatOfPoint2f();

  @Override
  protected AprilTagPoseEstimate process(AprilTagDetection in) {
    // System.out.println("asdfasf:");
    // System.exit(0);
    // Save the corner points of our detection to an array
    Point[] corners = new Point[4];
    for (int i = 0; i < 4; i++) {
      corners[i] = new Point(in.getCornerX(i), in.getCornerY(i));
    }
    // And shove into our matofpoints
    temp.fromArray(corners);

    // Probably overwrites what was in temp before. I hope
    System.out.println("Undistort 1");
    Calib3d.undistortImagePoints(
        temp,
        temp,
        params.calibration().getCameraIntrinsicsMat(),
        params.calibration().getDistCoeffsMat());

    // Save out undistorted corners
    corners = temp.toArray();

    // Apriltagdetection expects an array in form [x1 y1 x2 y2 ...]
    var fixedCorners = new double[8];
    for (int i = 0; i < 4; i++) {
      fixedCorners[i * 2] = corners[i].x;
      fixedCorners[i * 2 + 1] = corners[i].y;
    }

    // Create a new Detection with the fixed corners
    var corrected =
        new AprilTagDetection(
            in.getFamily(),
            in.getId(),
            in.getHamming(),
            in.getDecisionMargin(),
            in.getHomography(),
            in.getCenterX(),
            in.getCenterY(),
            fixedCorners);

    return m_poseEstimator.estimateOrthogonalIteration(corrected, params.nIters());
  }

  @Override
  public void setParams(AprilTagPoseEstimatorPipe.AprilTagPoseEstimatorPipeParams newParams) {
    if (this.params == null || !this.params.config().equals(newParams.config())) {
      m_poseEstimator.setConfig(newParams.config());
    }

    super.setParams(newParams);
  }

  @Override
  public void release() {
    temp.release();
  }

  public static record AprilTagPoseEstimatorPipeParams(
      Config config, CameraCalibrationCoefficients calibration, int nIters) {}
}
