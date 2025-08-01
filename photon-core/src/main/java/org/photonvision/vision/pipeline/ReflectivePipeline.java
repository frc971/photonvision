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

import java.util.List;
import org.photonvision.vision.frame.Frame;
import org.photonvision.vision.frame.FrameThresholdType;
import org.photonvision.vision.opencv.Contour;
import org.photonvision.vision.opencv.DualOffsetValues;
import org.photonvision.vision.pipe.CVPipe.CVPipeResult;
import org.photonvision.vision.pipe.impl.*;
import org.photonvision.vision.pipeline.result.CVPipelineResult;
import org.photonvision.vision.target.PotentialTarget;
import org.photonvision.vision.target.TargetOrientation;
import org.photonvision.vision.target.TrackedTarget;

/** Represents a pipeline for tracking retro-reflective targets. */
public class ReflectivePipeline extends CVPipeline<CVPipelineResult, ReflectivePipelineSettings> {
  private final FindContoursPipe findContoursPipe = new FindContoursPipe();
  private final SpeckleRejectPipe speckleRejectPipe = new SpeckleRejectPipe();
  private final FilterContoursPipe filterContoursPipe = new FilterContoursPipe();
  private final GroupContoursPipe groupContoursPipe = new GroupContoursPipe();
  private final SortContoursPipe sortContoursPipe = new SortContoursPipe();
  private final Collect2dTargetsPipe collect2dTargetsPipe = new Collect2dTargetsPipe();
  private final CornerDetectionPipe cornerDetectionPipe = new CornerDetectionPipe();
  private final SolvePNPPipe solvePNPPipe = new SolvePNPPipe();
  private final CalculateFPSPipe calculateFPSPipe = new CalculateFPSPipe();

  private final long[] pipeProfileNanos = new long[PipelineProfiler.ReflectivePipeCount];

  private static final FrameThresholdType PROCESSING_TYPE = FrameThresholdType.HSV;

  public ReflectivePipeline() {
    super(PROCESSING_TYPE);
    settings = new ReflectivePipelineSettings();
  }

  public ReflectivePipeline(ReflectivePipelineSettings settings) {
    super(PROCESSING_TYPE);
    this.settings = settings;
  }

  @Override
  protected void setPipeParamsImpl() {
    var dualOffsetValues =
        new DualOffsetValues(
            settings.offsetDualPointA,
            settings.offsetDualPointAArea,
            settings.offsetDualPointB,
            settings.offsetDualPointBArea);

    findContoursPipe.setParams(new FindContoursPipe.FindContoursParams());

    speckleRejectPipe.setParams(
        new SpeckleRejectPipe.SpeckleRejectParams(settings.contourSpecklePercentage));

    filterContoursPipe.setParams(
        new FilterContoursPipe.FilterContoursParams(
            settings.contourArea,
            settings.contourRatio,
            settings.contourFullness,
            frameStaticProperties,
            settings.contourFilterRangeX,
            settings.contourFilterRangeY,
            settings.contourTargetOrientation == TargetOrientation.Landscape));

    groupContoursPipe.setParams(
        new GroupContoursPipe.GroupContoursParams(
            settings.contourGroupingMode, settings.contourIntersection));

    sortContoursPipe.setParams(
        new SortContoursPipe.SortContoursParams(
            settings.contourSortMode,
            settings.outputShowMultipleTargets ? MAX_MULTI_TARGET_RESULTS : 1,
            frameStaticProperties));

    collect2dTargetsPipe.setParams(
        new Collect2dTargetsPipe.Collect2dTargetsParams(
            settings.offsetRobotOffsetMode,
            settings.offsetSinglePoint,
            dualOffsetValues,
            settings.contourTargetOffsetPointEdge,
            settings.contourTargetOrientation,
            frameStaticProperties));

    cornerDetectionPipe.setParams(
        new CornerDetectionPipe.CornerDetectionPipeParameters(
            settings.cornerDetectionStrategy,
            settings.cornerDetectionUseConvexHulls,
            settings.cornerDetectionExactSideCount,
            settings.cornerDetectionSideCount,
            settings.cornerDetectionAccuracyPercentage));

    solvePNPPipe.setParams(
        new SolvePNPPipe.SolvePNPPipeParams(
            frameStaticProperties.cameraCalibration, settings.targetModel));
  }

  @Override
  public CVPipelineResult process(Frame frame, ReflectivePipelineSettings settings) {
    long sumPipeNanosElapsed = 0L;

    CVPipeResult<List<Contour>> findContoursResult =
        findContoursPipe.run(frame.processedImage.getMat());
    sumPipeNanosElapsed += pipeProfileNanos[2] = findContoursResult.nanosElapsed;

    CVPipeResult<List<Contour>> speckleRejectResult =
        speckleRejectPipe.run(findContoursResult.output);
    sumPipeNanosElapsed += pipeProfileNanos[3] = speckleRejectResult.nanosElapsed;

    CVPipeResult<List<Contour>> filterContoursResult =
        filterContoursPipe.run(speckleRejectResult.output);
    sumPipeNanosElapsed += pipeProfileNanos[4] = filterContoursResult.nanosElapsed;

    CVPipeResult<List<PotentialTarget>> groupContoursResult =
        groupContoursPipe.run(filterContoursResult.output);
    sumPipeNanosElapsed += pipeProfileNanos[5] = groupContoursResult.nanosElapsed;

    CVPipeResult<List<PotentialTarget>> sortContoursResult =
        sortContoursPipe.run(groupContoursResult.output);
    sumPipeNanosElapsed += pipeProfileNanos[6] = sortContoursResult.nanosElapsed;

    CVPipeResult<List<TrackedTarget>> collect2dTargetsResult =
        collect2dTargetsPipe.run(sortContoursResult.output);
    sumPipeNanosElapsed += pipeProfileNanos[7] = collect2dTargetsResult.nanosElapsed;

    List<TrackedTarget> targetList;

    // 3d stuff
    if (settings.solvePNPEnabled) {
      var cornerDetectionResult = cornerDetectionPipe.run(collect2dTargetsResult.output);
      sumPipeNanosElapsed += pipeProfileNanos[8] = cornerDetectionResult.nanosElapsed;

      var solvePNPResult = solvePNPPipe.run(cornerDetectionResult.output);
      sumPipeNanosElapsed += pipeProfileNanos[9] = solvePNPResult.nanosElapsed;

      targetList = solvePNPResult.output;
    } else {
      pipeProfileNanos[8] = 0;
      pipeProfileNanos[9] = 0;
      targetList = collect2dTargetsResult.output;
    }

    var fpsResult = calculateFPSPipe.run(null);
    var fps = fpsResult.output;

    PipelineProfiler.printReflectiveProfile(pipeProfileNanos);

    return new CVPipelineResult(frame.sequenceID, sumPipeNanosElapsed, fps, targetList, frame);
  }
}
