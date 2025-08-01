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

package org.photonvision.vision.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.math.util.Units;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.photonvision.common.util.TestUtils;
import org.photonvision.vision.calibration.CameraCalibrationCoefficients;
import org.photonvision.vision.camera.QuirkyCamera;
import org.photonvision.vision.frame.Frame;
import org.photonvision.vision.frame.provider.FileFrameProvider;
import org.photonvision.vision.opencv.CVMat;
import org.photonvision.vision.opencv.ContourGroupingMode;
import org.photonvision.vision.opencv.ContourIntersectionDirection;
import org.photonvision.vision.pipe.impl.HSVPipe;
import org.photonvision.vision.pipeline.result.CVPipelineResult;
import org.photonvision.vision.target.TargetModel;

public class SolvePNPTest {
  private static final String LIFECAM_240P_CAL_FILE = "lifecam240p.json";
  private static final String LIFECAM_480P_CAL_FILE = "lifecam480p.json";

  @BeforeEach
  public void Init() {
    TestUtils.loadLibraries();
  }

  @Test
  public void loadCameraIntrinsics() {
    var lifecam240pCal = getCoeffs(LIFECAM_240P_CAL_FILE);
    var lifecam480pCal = getCoeffs(LIFECAM_480P_CAL_FILE);

    assertNotNull(lifecam240pCal);
    checkCameraCoefficients(lifecam240pCal);
    assertNotNull(lifecam480pCal);
    checkCameraCoefficients(lifecam480pCal);
  }

  private CameraCalibrationCoefficients getCoeffs(String filename) {
    var cameraCalibration = TestUtils.getCoeffs(filename, false);
    checkCameraCoefficients(cameraCalibration);
    return cameraCalibration;
  }

  private void checkCameraCoefficients(CameraCalibrationCoefficients cameraCalibration) {
    assertNotNull(cameraCalibration);
    assertEquals(3, cameraCalibration.cameraIntrinsics.rows);
    assertEquals(3, cameraCalibration.cameraIntrinsics.cols);
    assertEquals(3, cameraCalibration.cameraIntrinsics.getAsMatOfDouble().rows());
    assertEquals(3, cameraCalibration.cameraIntrinsics.getAsMatOfDouble().cols());
    assertEquals(3, cameraCalibration.getCameraIntrinsicsMat().rows());
    assertEquals(3, cameraCalibration.getCameraIntrinsicsMat().cols());
    assertEquals(1, cameraCalibration.distCoeffs.rows);
    assertEquals(5, cameraCalibration.distCoeffs.cols);
    assertEquals(1, cameraCalibration.distCoeffs.getAsMatOfDouble().rows());
    assertEquals(5, cameraCalibration.distCoeffs.getAsMatOfDouble().cols());
    assertEquals(1, cameraCalibration.getDistCoeffsMat().rows());
    assertEquals(5, cameraCalibration.getDistCoeffsMat().cols());
  }

  @Test
  public void test2019() {
    var pipeline = new ReflectivePipeline();

    pipeline.getSettings().hsvHue.set(60, 100);
    pipeline.getSettings().hsvSaturation.set(100, 255);
    pipeline.getSettings().hsvValue.set(190, 255);
    pipeline.getSettings().outputShouldDraw = true;
    pipeline.getSettings().outputShowMultipleTargets = true;
    pipeline.getSettings().solvePNPEnabled = true;
    pipeline.getSettings().contourGroupingMode = ContourGroupingMode.Dual;
    pipeline.getSettings().contourIntersection = ContourIntersectionDirection.Up;
    pipeline.getSettings().cornerDetectionUseConvexHulls = true;
    pipeline.getSettings().targetModel = TargetModel.k2019DualTarget;

    var frameProvider =
        new FileFrameProvider(
            TestUtils.getWPIImagePath(TestUtils.WPI2019Image.kCargoStraightDark48in, false),
            TestUtils.WPI2019Image.FOV,
            TestUtils.get2019LifeCamCoeffs(false));

    frameProvider.requestFrameThresholdType(pipeline.getThresholdType());
    var hsvParams =
        new HSVPipe.HSVParams(
            pipeline.getSettings().hsvHue,
            pipeline.getSettings().hsvSaturation,
            pipeline.getSettings().hsvValue,
            pipeline.getSettings().hueInverted);
    frameProvider.requestHsvSettings(hsvParams);

    CVPipelineResult pipelineResult = pipeline.run(frameProvider.get(), QuirkyCamera.DefaultCamera);
    TestUtils.printTestResultsWithLocation(pipelineResult);

    // Draw on input
    var outputPipe = new OutputStreamPipeline();
    outputPipe.process(
        pipelineResult.inputAndOutputFrame, pipeline.getSettings(), pipelineResult.targets);

    TestUtils.showImage(
        pipelineResult.inputAndOutputFrame.processedImage.getMat(), "Pipeline output", 999999);

    var pose = pipelineResult.targets.get(0).getBestCameraToTarget3d();
    // these numbers are not *accurate*, but they are known and expected
    var expectedTrl = new Translation3d(1.1, -0.05, -0.05);
    assertTrue(
        expectedTrl.getDistance(pose.getTranslation()) < 0.05,
        "SolvePNP translation estimation failed");
    // We expect the object axes to be in NWU, with the x-axis coming out of the tag
    // This target is facing the camera almost parallel, so in world space:
    // The object's X axis should be (-1, 0, 0)
    assertEquals(-1, new Translation3d(1, 0, 0).rotateBy(pose.getRotation()).getX(), 0.05);
    // The object's Y axis should be (0, -1, 0)
    assertEquals(-1, new Translation3d(0, 1, 0).rotateBy(pose.getRotation()).getY(), 0.05);
    // The object's Z axis should be (0, 0, 1)
    assertEquals(1, new Translation3d(0, 0, 1).rotateBy(pose.getRotation()).getZ(), 0.05);
  }

  @Test
  public void test2020() {
    var pipeline = new ReflectivePipeline();

    pipeline.getSettings().hsvHue.set(60, 100);
    pipeline.getSettings().hsvSaturation.set(100, 255);
    pipeline.getSettings().hsvValue.set(60, 255);
    pipeline.getSettings().outputShouldDraw = true;
    pipeline.getSettings().solvePNPEnabled = true;
    pipeline.getSettings().cornerDetectionAccuracyPercentage = 4;
    pipeline.getSettings().cornerDetectionUseConvexHulls = true;
    pipeline.getSettings().targetModel = TargetModel.k2020HighGoalOuter;

    var frameProvider =
        new FileFrameProvider(
            TestUtils.getWPIImagePath(TestUtils.WPI2020Image.kBlueGoal_224in_Left, false),
            TestUtils.WPI2020Image.FOV,
            TestUtils.get2020LifeCamCoeffs(false));

    frameProvider.requestFrameThresholdType(pipeline.getThresholdType());
    var hsvParams =
        new HSVPipe.HSVParams(
            pipeline.getSettings().hsvHue,
            pipeline.getSettings().hsvSaturation,
            pipeline.getSettings().hsvValue,
            pipeline.getSettings().hueInverted);
    frameProvider.requestHsvSettings(hsvParams);

    CVPipelineResult pipelineResult = pipeline.run(frameProvider.get(), QuirkyCamera.DefaultCamera);
    TestUtils.printTestResultsWithLocation(pipelineResult);

    // Draw on input
    var outputPipe = new OutputStreamPipeline();
    outputPipe.process(
        pipelineResult.inputAndOutputFrame, pipeline.getSettings(), pipelineResult.targets);

    TestUtils.showImage(
        pipelineResult.inputAndOutputFrame.processedImage.getMat(), "Pipeline output", 999999);

    var pose = pipelineResult.targets.get(0).getBestCameraToTarget3d();
    // these numbers are not *accurate*, but they are known and expected
    var expectedTrl =
        new Translation3d(
            Units.inchesToMeters(236), Units.inchesToMeters(36), Units.inchesToMeters(-53));
    assertTrue(
        expectedTrl.getDistance(pose.getTranslation()) < 0.05,
        "SolvePNP translation estimation failed");
    // We expect the object axes to be in NWU, with the x-axis coming out of the tag
    // Rotation around Z axis (yaw) should be mostly facing us
    var xAxis = new Translation3d(1, 0, 0);
    var yAxis = new Translation3d(0, 1, 0);
    var zAxis = new Translation3d(0, 0, 1);
    var expectedRot =
        new Rotation3d(Math.toRadians(-20), Math.toRadians(-20), Math.toRadians(-120));
    assertTrue(xAxis.rotateBy(expectedRot).getDistance(xAxis.rotateBy(pose.getRotation())) < 0.1);
    assertTrue(yAxis.rotateBy(expectedRot).getDistance(yAxis.rotateBy(pose.getRotation())) < 0.1);
    assertTrue(zAxis.rotateBy(expectedRot).getDistance(zAxis.rotateBy(pose.getRotation())) < 0.1);
  }

  private static void continuouslyRunPipeline(Frame frame, ReflectivePipelineSettings settings) {
    var pipeline = new ReflectivePipeline();
    pipeline.settings = settings;

    while (true) {
      CVPipelineResult pipelineResult = pipeline.run(frame, QuirkyCamera.DefaultCamera);
      TestUtils.printTestResultsWithLocation(pipelineResult);
      int preRelease = CVMat.getMatCount();
      pipelineResult.release();
      int postRelease = CVMat.getMatCount();

      System.out.printf("Pre: %d, Post: %d\n", preRelease, postRelease);
    }
  }

  // used to run VisualVM for profiling, which won't run on unit tests.
  public static void main(String[] args) {
    TestUtils.loadLibraries();
    var frameProvider =
        new FileFrameProvider(
            TestUtils.getWPIImagePath(TestUtils.WPI2019Image.kCargoStraightDark72in_HighRes, false),
            TestUtils.WPI2019Image.FOV);

    var settings = new ReflectivePipelineSettings();
    settings.hsvHue.set(60, 100);
    settings.hsvSaturation.set(100, 255);
    settings.hsvValue.set(190, 255);
    settings.outputShouldDraw = true;
    settings.outputShowMultipleTargets = true;
    settings.contourGroupingMode = ContourGroupingMode.Dual;
    settings.contourIntersection = ContourIntersectionDirection.Up;

    continuouslyRunPipeline(frameProvider.get(), settings);
  }
}
